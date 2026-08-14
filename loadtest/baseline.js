// T1. 기준선 - 평상시 트래픽 형태를 10분 고정 부하로 흘려 SLO 기준선을 만든다.
// 이 숫자가 있어야 A~F 의 "몇 배 느려졌다"가 성립한다. 극한 시나리오보다 먼저 돌린다.
// 세 서비스를 동시에 태우되 VU 는 실제 비율에 가깝게: 읽기 >> 로그인 > 채팅.
//   k6 run loadtest/baseline.js
import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { CONTENT, CHAT, CHAT_WS, account, login, tokenPool, bearer, pick, feedIds } from './lib/common.js';
import { connectFrame, subscribeFrame, chatSendFrame } from './lib/stomp.js';

const DUR = __ENV.DURATION || '10m';
const ROOM_ID = __ENV.CHAT_ROOM_ID || 'loadtest-room';

export const options = {
  scenarios: {
    read: { executor: 'constant-vus', vus: 30, duration: DUR, exec: 'read' },
    auth: { executor: 'constant-vus', vus: 5, duration: DUR, exec: 'auth' },
    chat: { executor: 'constant-vus', vus: 20, duration: DUR, exec: 'chat' },
  },
  thresholds: {
    // 기준선이므로 넉넉하지 않게 잡는다 - 여기서 이미 깨지면 극한 시나리오는 의미가 없다
    'http_req_duration{name:GET /feeds/scroll}': ['p(95)<300', 'p(99)<500'],
    'http_req_duration{name:GET /feeds/{id}}': ['p(95)<200'],
    'http_req_duration{name:POST /login}': ['p(95)<400'],
    'http_req_duration{name:GET /chat/rooms}': ['p(95)<300'],
    http_req_failed: ['rate<0.005'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  return { tokens: tokenPool(20), feeds: feedIds(100) };
}

export function read(data) {
  const h = { headers: bearer(pick(data.tokens, __VU)) };
  check(http.get(`${CONTENT}/feeds/scroll?size=20`, { ...h, tags: { name: 'GET /feeds/scroll' } }), {
    'scroll 200': (r) => r.status === 200,
  });
  if (data.feeds.length) {
    const id = pick(data.feeds, __VU + __ITER);
    check(http.get(`${CONTENT}/feeds/${id}`, { ...h, tags: { name: 'GET /feeds/{id}' } }), {
      'detail 200': (r) => r.status === 200,
    });
  }
  sleep(1); // think time - 기준선은 최대 처리량이 아니라 평상시 형태를 재는 것
}

export function auth() {
  login(account(__VU));
  sleep(3);
}

export function chat(data) {
  const token = pick(data.tokens, __VU);
  check(
    http.get(`${CHAT}/v1/chat/rooms`, {
      headers: bearer(token),
      tags: { name: 'GET /chat/rooms' },
    }),
    { 'rooms 200': (r) => r.status === 200 }
  );

  ws.connect(CHAT_WS, {}, (socket) => {
    socket.on('open', () => socket.send(connectFrame(token, `k6-base-${__VU}`)));
    socket.on('message', (m) => {
      if (m.startsWith('CONNECTED')) {
        socket.send(subscribeFrame(`sub-${__VU}`, ROOM_ID));
        socket.setInterval(() => socket.send(chatSendFrame(ROOM_ID, 'baseline')), 5000);
      }
    });
    socket.setTimeout(() => socket.close(), 30000);
  });
}
