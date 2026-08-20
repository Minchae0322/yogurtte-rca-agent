// T1.5-v2 사용자 여정 v2 - 실제 화면 흐름 기반 (2026-08-20 실트래픽 확인 후 개편)
//
// v1(journey.js)과 다른 점 - v1은 보존한다 (1회차 결과가 그 스크립트로 채록됨):
//   ① 진입 화면이 대시보드다 - battles/hot(7) · products(20) · feeds/hot(6) 3종 동시 호출.
//      실서비스 최다 호출 경로인데 v1에는 battles/hot·products 축이 아예 없었다.
//      셋 다 사용자 무관 동일 응답(정렬·페이지 고정)이라 캐시 실험(설계 3-4)의 Before 측정 지점.
//   ② 메뉴 구조 반영 - 피드 메뉴(categories/list + 무한스크롤 → 상세 → 좋아요·댓글) ·
//      배틀 메뉴(battles 최신순 + battles/hot?size=5)
//   ③ 댓글 경로 추가 - 상세에서 댓글 목록 조회, reactor는 좋아요 + 댓글 작성
//      (POST /feeds/{id}/comments {content} - FeedCommentController 실경로 확인)
// 유지: 세션 단위 · 도착률 모델 · think time · MIX(읽기 70/반응 20/채팅 10)
//
//   k6 run autoscaling/loadtest/journey-v2.js                    # 초당 2세션 (~100 동시)
//   k6 run autoscaling/loadtest/journey-v2.js -e RATE=6          # 성수기 가정
import http from 'k6/http';
import ws from 'k6/ws';
import { check, group, sleep } from 'k6';
import { CONTENT, CHAT, CHAT_WS, account, login, tokenPool, bearer, pick, feedIds } from './lib/common.js';
import { connectFrame, subscribeFrame } from './lib/stomp.js';

const RATE = Number(__ENV.RATE || 2);
const DURATION = __ENV.DURATION || '15m';
const ROOM_ID = __ENV.CHAT_ROOM_ID || 'loadtest-room';

// 사용자 구성 - 합 100 (v1과 동일)
const MIX = { lurker: 70, reactor: 20, chatter: 10 };
// 메뉴 진입률 - 대시보드는 전원, 그 뒤 분기 (실측 없음 - 가정값이며 회차에 기록할 것)
const FEED_MENU_P = Number(__ENV.FEED_MENU_P || 0.8);
const BATTLE_MENU_P = Number(__ENV.BATTLE_MENU_P || 0.4);

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
        { duration: '2m', target: RATE },
        { duration: DURATION, target: RATE },
        { duration: '2m', target: 0 },
      ],
    },
  },
  thresholds: {
    // 대시보드 3종 - 최다 호출 경로라 여기가 제일 엄격해야 한다
    'http_req_duration{name:GET /battles/hot}': ['p(95)<300', 'p(99)<600'],
    'http_req_duration{name:GET /products}': ['p(95)<300', 'p(99)<600'],
    'http_req_duration{name:GET /feeds/hot}': ['p(95)<300', 'p(99)<600'],
    'http_req_duration{name:GET /feeds/scroll}': ['p(95)<300', 'p(99)<600'],
    'http_req_duration{name:GET /feeds/{id}}': ['p(95)<200'],
    'http_req_duration{name:POST /login}': ['p(95)<400'],
    'http_req_duration{name:POST /feeds/{id}/reactions}': ['p(95)<400'],
    'http_req_duration{name:POST /feeds/{id}/comments}': ['p(95)<400'],
    'http_req_duration{name:GET /chat/rooms}': ['p(95)<300'],
    http_req_failed: ['rate<0.005'],
    checks: ['rate>0.99'],
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

  // 1) 앱 진입 - 10%만 실제 로그인 (v1과 동일)
  let token;
  if (Math.random() < 0.1) {
    token = login(account(__VU));
    think(1, 3);
  } else {
    token = pick(data.tokens, __VU);
  }
  const h = { headers: bearer(token) };

  // 2) 대시보드 - 화면 하나가 3종을 동시에 부른다. 실 URL 그대로 (sort 인코딩 포함)
  group('dashboard', () => {
    const res = http.batch([
      ['GET', `${CONTENT}/battles/hot?page=0&size=7&sort=hotScore%2Cdesc`, null, { ...h, tags: { name: 'GET /battles/hot' } }],
      ['GET', `${CONTENT}/products?status=APPROVED&page=0&size=20&sort=popularityScore%2CDESC`, null, { ...h, tags: { name: 'GET /products' } }],
      ['GET', `${CONTENT}/feeds/hot?page=0&size=6&sort=hotScore%2Cdesc`, null, { ...h, tags: { name: 'GET /feeds/hot' } }],
    ]);
    check(res[0], { 'dash battles/hot 200': (r) => r.status === 200 });
    check(res[1], { 'dash products 200': (r) => r.status === 200 });
    check(res[2], { 'dash feeds/hot 200': (r) => r.status === 200 });
  });
  think(2, 6); // 대시보드를 훑는 시간

  // 3) 피드 메뉴 - 카테고리 탭 로드 + 무한스크롤 → 상세 → (일부) 좋아요·댓글
  if (Math.random() < FEED_MENU_P) {
    group('feed menu', () => {
      check(http.get(`${CONTENT}/categories/list?categoryType=FEED&isActive=true`,
        { ...h, tags: { name: 'GET /categories/list' } }), { 'categories 200': (r) => r.status === 200 });
      think(1, 3);

      let cursor = null;
      const pages = 2 + Math.floor(Math.random() * 2);
      for (let i = 0; i < pages; i++) {
        const url = `${CONTENT}/feeds/scroll?size=20` + (cursor ? `&cursor=${cursor}` : '');
        const res = http.get(url, { ...h, tags: { name: 'GET /feeds/scroll' } });
        check(res, { 'scroll 200': (r) => r.status === 200 });
        const items = (res.json('data.items') || res.json('data.content') || []);
        if (items.length) cursor = items[items.length - 1].feedId || items[items.length - 1].id;
        think(3, 8);
      }

      if (data.feeds.length) {
        const id = pick(data.feeds, __VU + __ITER);
        check(http.get(`${CONTENT}/feeds/${id}`, { ...h, tags: { name: 'GET /feeds/{id}' } }),
          { 'detail 200': (r) => r.status === 200 });
        // 상세 화면이 댓글 목록을 같이 부른다
        check(http.get(`${CONTENT}/feeds/${id}/comments?page=0&size=10`,
          { ...h, tags: { name: 'GET /feeds/{id}/comments' } }), { 'comments 200': (r) => r.status === 200 });
        think(4, 10);

        if (kind !== 'lurker') {
          check(http.post(`${CONTENT}/feeds/${id}/reactions?reactionType=LIKE`, null,
            { ...h, tags: { name: 'POST /feeds/{id}/reactions' } }), { 'reaction 200': (r) => r.status === 200 });
          think(1, 3);
          // 댓글은 리액션한 사람의 절반만. 내용은 짧게 (varchar 255 - AP-1의 교훈)
          if (Math.random() < 0.5) {
            check(http.post(`${CONTENT}/feeds/${id}/comments`,
              JSON.stringify({ content: `journey-v2 comment ${__VU}-${__ITER}` }),
              { headers: { ...h.headers, 'Content-Type': 'application/json' }, tags: { name: 'POST /feeds/{id}/comments' } }),
              { 'comment 200': (r) => r.status === 200 });
            think(1, 3);
          }
        }
      }
    });
  }

  // 4) 배틀 메뉴 - 최신순 목록 + 핫 리스트 (실 URL 그대로)
  if (Math.random() < BATTLE_MENU_P) {
    group('battle menu', () => {
      const res = http.batch([
        ['GET', `${CONTENT}/battles?isActive=true&page=0&size=20&sort=createdAt%2CDESC`, null, { ...h, tags: { name: 'GET /battles' } }],
        ['GET', `${CONTENT}/battles/hot?page=0&size=5&sort=hotScore%2Cdesc`, null, { ...h, tags: { name: 'GET /battles/hot' } }],
      ]);
      check(res[0], { 'battles 200': (r) => r.status === 200 });
      check(res[1], { 'battles/hot 200': (r) => r.status === 200 });
    });
    think(2, 6);
  }

  // 5) 채팅 - 소수만 (v1과 동일)
  if (kind === 'chatter') {
    check(http.get(`${CHAT}/v1/chat/rooms`, { ...h, tags: { name: 'GET /chat/rooms' } }),
      { 'rooms 200': (r) => r.status === 200 });
    ws.connect(CHAT_WS, {}, (socket) => {
      socket.on('open', () => socket.send(connectFrame(token, `k6-journey-${__VU}`)));
      socket.on('message', (m) => {
        if (m.startsWith('CONNECTED')) socket.send(subscribeFrame(`sub-${__VU}`, ROOM_ID));
      });
      socket.setTimeout(() => socket.close(), 20000 + Math.random() * 20000);
    });
  }

  think(2, 5);
}
