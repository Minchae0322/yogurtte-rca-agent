// T1.5 사용자 여정 - 실제 사용자가 평소처럼 쓰는 상황
//
// baseline.js 는 경로별로 따로 때린다(읽기 30VU · 로그인 5VU · 채팅 20VU). 실제 트래픽은 그렇게 오지 않는다.
// 여기서는 반복 1회 = 사용자 세션 1개다: 로그인 → 피드 훑기 → 상세 → 반응 → 채팅 확인 → 이탈.
//
// 두 가지가 다른 시나리오들과 다르다.
//   ① think time - 사람은 응답이 오자마자 다음 요청을 보내지 않는다. 이게 빠지면 VU 수와
//      실제 동시 사용자 수가 완전히 다른 값이 되고, 커넥션 재사용 패턴도 현실과 달라진다.
//   ② 도착률 모델 - VU 고정이 아니라 "초당 N명이 새로 들어온다"로 건다. 세션이 길어지면
//      동시 사용자가 저절로 늘어나는데, 그게 실제 서비스에서 일어나는 일이다.
//
// 사용자 구성은 실제 비율에 맞춘다(수정하려면 MIX 조정): 대부분은 읽기만 하고 일부만 쓴다.
//
//   k6 run loadtest/journey.js                      # 초당 2세션 (~100 동시)
//   k6 run loadtest/journey.js -e RATE=6            # 성수기 가정
//   k6 run loadtest/journey.js -e DURATION=30m
import http from 'k6/http';
import ws from 'k6/ws';
import { check, group, sleep } from 'k6';
import { CONTENT, CHAT, CHAT_WS, account, login, tokenPool, bearer, pick, feedIds } from './lib/common.js';
import { connectFrame, subscribeFrame } from './lib/stomp.js';

const RATE = Number(__ENV.RATE || 2); // 초당 새 세션 수
const DURATION = __ENV.DURATION || '15m';
const ROOM_ID = __ENV.CHAT_ROOM_ID || 'loadtest-room';

// 사용자 구성 - 합 100
const MIX = { lurker: 70, reactor: 20, chatter: 10 };

// 사람이 화면을 보는 시간. 고정값이면 요청이 한 박자로 몰려 실제와 다른 그래프가 나온다.
const think = (min, max) => sleep(min + Math.random() * (max - min));

export const options = {
  scenarios: {
    sessions: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 800,
      stages: [
        { duration: '2m', target: RATE }, // 완만한 진입 - 실제 트래픽은 계단으로 오지 않는다
        { duration: DURATION, target: RATE },
        { duration: '2m', target: 0 },
      ],
    },
  },
  thresholds: {
    // 평상시 기준이므로 넉넉하지 않게. 여기서 깨지면 극한 시나리오는 볼 필요가 없다.
    'http_req_duration{name:GET /feeds/scroll}': ['p(95)<300', 'p(99)<600'],
    'http_req_duration{name:GET /feeds/{id}}': ['p(95)<200'],
    'http_req_duration{name:POST /login}': ['p(95)<400'],
    'http_req_duration{name:POST /feeds/{id}/reactions}': ['p(95)<400'],
    'http_req_duration{name:GET /chat/rooms}': ['p(95)<300'],
    http_req_failed: ['rate<0.005'],
    checks: ['rate>0.99'],
    // 세션 하나가 끝나는 데 걸리는 시간. think time 포함이라 절대값보다 추세를 본다.
    iteration_duration: ['p(95)<90000'],
  },
};

export function setup() {
  return { tokens: tokenPool(30), feeds: feedIds(100) };
}

function role() {
  const r = Math.random() * 100;
  if (r < MIX.lurker) return 'lurker';
  if (r < MIX.lurker + MIX.reactor) return 'reactor';
  return 'chatter';
}

export default function (data) {
  const kind = role();

  // 1) 앱 진입 - 대부분 토큰이 살아있어 매번 로그인하지는 않는다. 10%만 실제 로그인.
  let token;
  if (Math.random() < 0.1) {
    token = login(account(__VU));
    think(1, 3);
  } else {
    token = pick(data.tokens, __VU);
  }
  const h = { headers: bearer(token) };

  // 2) 피드 훑기 - 커서 페이징 2~3장. 실제 무한스크롤이 이 모양이다.
  let cursor = null;
  group('feed scroll', () => {
    const pages = 2 + Math.floor(Math.random() * 2);
    for (let i = 0; i < pages; i++) {
      const url = `${CONTENT}/feeds/scroll?size=20` + (cursor ? `&cursor=${cursor}` : '');
      const res = http.get(url, { ...h, tags: { name: 'GET /feeds/scroll' } });
      check(res, { 'scroll 200': (r) => r.status === 200 });
      const items = (res.json('data.items') || res.json('data.content') || []);
      if (items.length) cursor = items[items.length - 1].feedId || items[items.length - 1].id;
      think(3, 8); // 스크롤하며 읽는 시간
    }
  });

  // 3) 눈에 띈 것 한두 개 열어본다
  if (data.feeds.length) {
    const id = pick(data.feeds, __VU + __ITER);
    check(http.get(`${CONTENT}/feeds/${id}`, { ...h, tags: { name: 'GET /feeds/{id}' } }), {
      'detail 200': (r) => r.status === 200,
    });
    think(4, 10); // 상세를 읽는 시간이 가장 길다

    // 4) 일부만 반응한다
    if (kind !== 'lurker') {
      check(
        http.post(`${CONTENT}/feeds/${id}/reactions?reactionType=LIKE`, null, {
          ...h,
          tags: { name: 'POST /feeds/{id}/reactions' },
        }),
        { 'reaction 200': (r) => r.status === 200 }
      );
      think(1, 3);
    }
  }

  // 5) 배틀 목록을 둘러본다 (읽기)
  check(http.get(`${CONTENT}/battles?page=0&size=10`, { ...h, tags: { name: 'GET /battles' } }), {
    'battles 200': (r) => r.status === 200,
  });
  think(2, 6);

  // 6) 채팅 - 소수만. 목록을 보고 방에 잠깐 붙어 있다 나간다.
  if (kind === 'chatter') {
    check(http.get(`${CHAT}/v1/chat/rooms`, { ...h, tags: { name: 'GET /chat/rooms' } }), {
      'rooms 200': (r) => r.status === 200,
    });
    ws.connect(CHAT_WS, {}, (socket) => {
      socket.on('open', () => socket.send(connectFrame(token, `k6-journey-${__VU}`)));
      socket.on('message', (m) => {
        if (m.startsWith('CONNECTED')) socket.send(subscribeFrame(`sub-${__VU}`, ROOM_ID));
      });
      // 붙어만 있고 대부분 읽기만 한다 - 실제 채팅방 체류가 이렇다
      socket.setTimeout(() => socket.close(), 20000 + Math.random() * 20000);
    });
  }

  think(2, 5); // 이탈 전 잠깐
}
