// T0. 스모크 - 부하가 아니라 "경로가 살아있나" 확인. 다른 모든 시나리오의 선행 조건.
// 1 VU 1회. 하나라도 깨지면 뒤 시나리오 결과는 볼 필요가 없다.
//   k6 run autoscaling/loadtest/smoke.js
import http from 'k6/http';
import ws from 'k6/ws';
import { check } from 'k6';
import { AUTH, CONTENT, CHAT, CHAT_WS, account, login, bearer } from './lib/common.js';
import { connectFrame } from './lib/stomp.js';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ['rate==1.00'] }, // 스모크는 100% 아니면 실패
};

export default function () {
  // 1) auth - 로그인 (시드 계정 존재 확인 겸)
  const token = login(account(1));
  check(token, { 'accessToken 발급': (t) => !!t });
  const h = { headers: bearer(token) };

  // 2) content - 읽기 3종
  check(http.get(`${CONTENT}/feeds/scroll?size=5`, h), { 'feeds/scroll 200': (r) => r.status === 200 });
  check(http.get(`${CONTENT}/feeds/hot?page=0&size=5`, h), { 'feeds/hot 200': (r) => r.status === 200 });
  check(http.get(`${CONTENT}/battles?page=0&size=5`, h), { 'battles 200': (r) => r.status === 200 });

  // 3) auth - content 가 쓰는 내부 API 경로가 뚫려 있는지 (X-API-Key 필요)
  if (__ENV.INTERNAL_API_KEY) {
    const res = http.get(`${AUTH}/external/users?userIds=1`, {
      headers: { 'X-API-Key': __ENV.INTERNAL_API_KEY },
    });
    check(res, { 'external/users 200': (r) => r.status === 200 });
  }

  // 4) chat - REST + WS 핸드셰이크/STOMP CONNECT
  check(http.get(`${CHAT}/v1/chat/rooms`, h), { 'chat rooms 200': (r) => r.status === 200 });

  let connected = false;
  const wsRes = ws.connect(CHAT_WS, {}, (socket) => {
    socket.on('open', () => socket.send(connectFrame(token, 'k6-smoke')));
    socket.on('message', (m) => {
      if (m.startsWith('CONNECTED')) connected = true;
      socket.close();
    });
    socket.setTimeout(() => socket.close(), 5000);
  });
  check(wsRes, { 'ws 101': (r) => r && r.status === 101 });
  check(connected, { 'STOMP CONNECTED': (c) => c === true });
}
