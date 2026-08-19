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
// WS-B(메시지 처리량)·Kafka consumer 시험용: 세션당 전송 간격(ms). 낮출수록 전체 msg/s가 올라간다.
// 전체 유입률 ≈ VUS / (MSG_INTERVAL_MS/1000) msg/s. 예: 200 VU × 1000ms = 200 msg/s.
const MSG_INTERVAL_MS = Number(__ENV.MSG_INTERVAL_MS || 3000);
// 다중 방 분산(파티션 hot-spot 대조군): 방을 N개로 나누되 각 VU가 전 방을 구독한다 -
// 메시지당 팬아웃(수신자 수)은 단일 방과 동일하게 유지하고 Kafka 파티션 키(roomId)만 분산된다.
// 전송은 자기 방(`{ROOM_ID}-{__VU % N}`)으로만. 기본 1 = 기존 단일 방 동작.
const ROOM_COUNT = Number(__ENV.ROOM_COUNT || 1);
const roomName = (i) => (ROOM_COUNT === 1 ? ROOM_ID : `${ROOM_ID}-${i}`);

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
        for (let i = 0; i < ROOM_COUNT; i++) socket.send(subscribeFrame(`sub-${__VU}-${i}`, roomName(i)));
        const myRoom = roomName(__VU % ROOM_COUNT);
        socket.setInterval(() => socket.send(chatSendFrame(myRoom, 'hello', ENTER_ROOM)), MSG_INTERVAL_MS);
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
