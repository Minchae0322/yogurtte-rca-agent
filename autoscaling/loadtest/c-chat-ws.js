// 시나리오 C - 채팅 동시접속 (chat-service, WebSocket)
// 흐름: CONNECT(Bearer + X-Device-Id) → SUBSCRIBE /topic/chatroom/{roomId} → 3초마다 SEND /app/chat/send
// 부하 지점: 동시 세션 레지스트리(로컬 + Redis presence) · 메시지당 Mongo 저장 ·
//            DirectChatMessageSender 가 senderId 조회로 auth-service 를 호출하는 구간(캐시 miss 시 부하 전파)
//   k6 run autoscaling/loadtest/c-chat-ws.js -e CHAT_WS_URL=wss://<INGRESS>/api/ws/websocket -e VUS=200
import ws from 'k6/ws';
import { check } from 'k6';
import { CHAT_WS, tokenPool, pick } from './lib/common.js';
import { connectFrame, subscribeFrame, chatSendFrame, frame } from './lib/stomp.js';

const VUS = Number(__ENV.VUS || 200);
const TOKEN_POOL = Number(__ENV.TOKEN_POOL || 50); // 로그인 비용을 측정에서 빼려고 setup 에서 미리 받는다
const ROOM_ID = __ENV.CHAT_ROOM_ID || 'loadtest-room';
const ENTER_ROOM = __ENV.CHAT_ROOM_ENTER === '1';

export const options = {
  stages: [
    { duration: '2m', target: VUS }, // 동시 WS 연결
    { duration: '3m', target: VUS },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    ws_connecting: ['p(99)<1000'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  return { tokens: tokenPool(TOKEN_POOL) };
}

export default function (data) {
  const token = pick(data.tokens, __VU);
  let connected = false;

  const res = ws.connect(CHAT_WS, {}, (socket) => {
    socket.on('open', () => socket.send(connectFrame(token, `k6-${__VU}`)));

    socket.on('message', (msg) => {
      if (!connected && msg.startsWith('CONNECTED')) {
        connected = true;
        socket.send(subscribeFrame(`sub-${__VU}`, ROOM_ID));
        socket.setInterval(() => socket.send(chatSendFrame(ROOM_ID, 'hello', ENTER_ROOM)), 3000);
      }
    });

    socket.setTimeout(() => {
      socket.send(frame('DISCONNECT', {}));
      socket.close();
    }, 60000);
  });

  check(res, { 'ws status 101': (r) => r && r.status === 101 });
  check(connected, { 'STOMP CONNECTED': (c) => c === true });
}
