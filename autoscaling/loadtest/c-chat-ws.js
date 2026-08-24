// 시나리오 C - 채팅 동시접속 (chat-service, WebSocket)
// 흐름: CONNECT(Bearer + X-Device-Id) → SUBSCRIBE /topic/chatroom/{roomId} → 3초마다 SEND /app/chat/send
// 부하 지점: 동시 세션 레지스트리(로컬 + Redis presence) · 메시지당 Mongo 저장 ·
//            DirectChatMessageSender 가 senderId 조회로 auth-service 를 호출하는 구간(캐시 miss 시 부하 전파)
//   k6 run autoscaling/loadtest/c-chat-ws.js -e CHAT_WS_URL=wss://<INGRESS>/api/ws/websocket -e VUS=200
// 실행 모드:
//   기본                                    단일 방 팬아웃 스트레스 (프로덕션엔 없는 상태 - SUB_ALL 주석 참조)
//   -e ROOM_COUNT=100 -e SUB_ALL=0          DM 현실 모델: 1:1 방 100개 × 2세션, 팬아웃 2
//   -e ROOM_COUNT=20                        파티션 hot-spot 대조군: 팬아웃 고정, 파티션만 분산
import ws from 'k6/ws';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import { CHAT_WS, tokenPool, pick } from './lib/common.js';
import { connectFrame, subscribeFrame, chatSendFrame, frame } from './lib/stomp.js';

const VUS = Number(__ENV.VUS || 200);
const TOKEN_POOL = Number(__ENV.TOKEN_POOL || 50); // 로그인 비용을 측정에서 빼려고 setup 에서 미리 받는다
const ROOM_ID = __ENV.CHAT_ROOM_ID || 'loadtest-room';
const ENTER_ROOM = __ENV.CHAT_ROOM_ENTER === '1';
// WS-B(메시지 처리량)·Kafka consumer 시험용: 세션당 전송 간격(ms). 낮출수록 전체 msg/s가 올라간다.
// 전체 유입률 ≈ VUS / (MSG_INTERVAL_MS/1000) msg/s. 예: 200 VU × 1000ms = 200 msg/s.
const MSG_INTERVAL_MS = Number(__ENV.MSG_INTERVAL_MS || 3000);
// 방 개수. 전송은 언제나 자기 방(`{ROOM_ID}-{__VU % N}`)으로만. 기본 1 = 단일 방.
const ROOM_COUNT = Number(__ENV.ROOM_COUNT || 1);
// 구독 범위 - 이 한 줄이 무엇을 재는 시험인지를 바꾼다.
//   1 (기본) = 전 방 구독. 팬아웃(수신자 수)을 통제 변수로 고정하고 Kafka 파티션 분산만 바꾼다
//              → 파티션 hot-spot 대조군 (WS-B 08-19)
//   0        = 자기 방만 구독. 방당 세션 2개 = 실제 DM 트래픽 모델
//              → ROOM_COUNT=100 · VUS=200 이면 1:1 방 100개 × 2세션, 팬아웃 2, 파티션 자연 분산
// 주의: 기본값(전 방 구독 + NORMAL 방)은 프로덕션에 존재할 수 없는 상태다 -
//       NORMAL 방은 1:1만 허용인데 WS 멤버십 검증이 없어서 성립하는 것뿐이다.
const SUB_ALL = __ENV.SUB_ALL !== '0';
const roomName = (i) => (ROOM_COUNT === 1 ? ROOM_ID : `${ROOM_ID}-${i}`);

// 배달 지연 - SEND 한 시각을 본문에 실어 보내고, 다른 세션이 그 MESSAGE 를 받은 시각과의 차.
// 사용자가 체감하는 유일한 값이라 이것을 합격선으로 삼는다(배달률이 아니라).
// 전 VU 가 한 k6 프로세스라 시계가 같으므로 송신자-수신자가 달라도 성립한다.
// ponytail: k6 수신 큐가 밀리면 그 대기시간도 여기 섞인다 - 서버 지연의 상한값으로 읽을 것.
//           서버측 순수 지연이 필요해지면 chat 에 브로드캐스트 직전 타임스탬프를 심어 분리한다.
const deliveryLatency = new Trend('ws_delivery_latency', true);

export const options = {
  stages: [
    { duration: '2m', target: VUS }, // 램프 - JVM/커넥션풀 예열 구간
    { duration: '3m', target: VUS }, // 유지 - scrape 15s 기준 표본 12개
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    ws_connecting: ['p(99)<1000'],
    checks: ['rate>0.99'],
    ws_delivery_latency: ['p(99)<1000'],
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
        const myRoom = roomName(__VU % ROOM_COUNT);
        if (SUB_ALL) {
          for (let i = 0; i < ROOM_COUNT; i++) socket.send(subscribeFrame(`sub-${__VU}-${i}`, roomName(i)));
        } else {
          socket.send(subscribeFrame(`sub-${__VU}`, myRoom));
        }
        socket.setInterval(
          () => socket.send(chatSendFrame(myRoom, `hello t=${Date.now()}`, ENTER_ROOM)),
          MSG_INTERVAL_MS
        );
      } else if (msg.startsWith('MESSAGE')) {
        // 브로드캐스트 DTO 가 content 를 그대로 되돌려준다 (WebSocketMessageResponse.ChatMessageDto)
        const sentAt = /t=(\d+)/.exec(msg);
        if (sentAt) deliveryLatency.add(Date.now() - Number(sentAt[1]));
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
