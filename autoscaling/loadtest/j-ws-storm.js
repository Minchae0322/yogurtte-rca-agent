// J. WS 연결 폭주 - 메시지가 아니라 "연결 수" 자체가 한계인 지점
//
// C 는 메시지 처리 부하, 이건 연결 유지 부하다. 둘은 다른 자원에서 깨진다.
//   연결 1개 = STOMP 세션 + LocalSessionRegistry 엔트리 + Redis presence 키 + 소켓 FD.
//   메시지를 하나도 안 보내도 이것들이 쌓인다.
// 재접속 폭풍(reconnect storm)도 여기서 만든다: -e RECONNECT=1 이면 짧게 붙었다 끊기를 반복해
// CONNECT/DISCONNECT 핸들러와 presence 등록/해제 경로를 때린다. 서버 재시작 직후 클라이언트가
// 일제히 재접속하는 상황이 이 모양이다.
//
//   k6 run autoscaling/loadtest/j-ws-storm.js -e VUS=1000               (연결 유지)
//   k6 run autoscaling/loadtest/j-ws-storm.js -e VUS=500 -e RECONNECT=1 (재접속 폭풍)
//
// 볼 것: chat 의 열린 소켓 수 · Redis 키 증가와 TTL 회수 여부 · DISCONNECT 후 정리 누락(세션 누수) ·
//        JVM 스레드 수. OS 의 파일 디스크립터 한계(ulimit -n)에 k6 쪽이 먼저 걸릴 수 있으니 확인할 것.
import ws from 'k6/ws';
import { check } from 'k6';
import { CHAT_WS, tokenPool, pick } from './lib/common.js';
import { connectFrame, frame } from './lib/stomp.js';

const VUS = Number(__ENV.VUS || 1000);
const RECONNECT = __ENV.RECONNECT === '1';
const HOLD_MS = RECONNECT ? Number(__ENV.HOLD_MS || 2000) : Number(__ENV.HOLD_MS || 180000);

export const options = {
  stages: [
    { duration: '2m', target: VUS }, // 연결 증가
    { duration: '3m', target: VUS }, // 유지 (RECONNECT=1 이면 이 구간이 재접속 반복)
    { duration: '1m', target: 0 }, // 일제 해제 - 정리 경로도 부하다
  ],
  thresholds: {
    ws_connecting: ['p(95)<2000'],
    checks: ['rate>0.95'],
  },
};

export function setup() {
  return { tokens: tokenPool(Number(__ENV.TOKEN_POOL || 50)) };
}

export default function (data) {
  let connected = false;
  const res = ws.connect(CHAT_WS, {}, (socket) => {
    socket.on('open', () => socket.send(connectFrame(pick(data.tokens, __VU), `k6-storm-${__VU}`)));
    socket.on('message', (m) => {
      if (m.startsWith('CONNECTED')) connected = true;
    });
    // 메시지는 보내지 않는다. 연결 유지 비용만 본다.
    socket.setTimeout(() => {
      socket.send(frame('DISCONNECT', {}));
      socket.close();
    }, HOLD_MS);
  });

  check(res, { 'ws 101': (r) => r && r.status === 101 });
  check(connected, { 'STOMP CONNECTED': (c) => c === true });
}
