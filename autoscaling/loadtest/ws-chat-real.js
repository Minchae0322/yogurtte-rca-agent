// WS 발화 모델 정렬 시험 - "진짜 방"에서의 배달 지연 (부하모델-chat.md §2)
//
// 배경: chat 재배포 후 ChatRelayConsumer 가 방 참여자를 DB 에서 조회해
//   비어 있으면 배달을 스킵한다(roomMemberIds.isEmpty() → return). 따라서 c-chat-ws 의
//   가상 방(loadtest-room-N)으로는 배달이 0건이 된다 - 2026-08-30 실측으로 확인.
//   이 스크립트는 setup 에서 REST 로 실제 1:1 방을 만들어(참여자 등록 포함) 그 위에서 잰다.
//   부수 효과로 저장(Mongo)·참여자 조회·발신자 정보 조회까지 실경로 전체를 태운다.
//
// 모델 정렬 (부하모델-chat.md):
//   평시:    PAIRS=30  (60세션)  × MSG_INTERVAL_MS=30000 → 인입 2 msg/s
//   스파이크: PAIRS=150 (300세션) × MSG_INTERVAL_MS=30000 → 인입 10 msg/s
//
//   k6 run autoscaling/loadtest/ws-chat-real.js -e PAIRS=30 -e MSG_INTERVAL_MS=30000 \
//     -e CHAT_WS_URL=ws://<HOST>/api/chat/ws/websocket -e AUTH_URL=http://<HOST>/api/auth \
//     -e CHAT_URL=http://<HOST>/api/chat
//
// 배달 지연: 본문에 발신 시각(t=)과 발신 VU(v=)를 실어 보내고, "상대" 세션이 받은 시각과의
//   차를 잰다 (자기 에코는 제외 - 순수 상대방 배달 지연만).
import ws from 'k6/ws';
import http from 'k6/http';
import { check, fail } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { b64decode } from 'k6/encoding';
import { CHAT, CHAT_WS, account, login, bearer } from './lib/common.js';
import { connectFrame, subscribeFrame, chatSendFrame, frame } from './lib/stomp.js';

const PAIRS = Number(__ENV.PAIRS || 30);
const MSG_INTERVAL_MS = Number(__ENV.MSG_INTERVAL_MS || 30000);
const VUS = PAIRS * 2;

const deliveryLatency = new Trend('ws_delivery_latency', true);
const delivered = new Counter('ws_delivered');

export const options = {
  // 방 PAIRS개 생성 + 로그인 2×PAIRS회. 서버가 부하 회복 중이면 기본 60s로는 부족하다(실측).
  setupTimeout: '5m',
  stages: [
    { duration: '2m', target: VUS },
    { duration: '3m', target: VUS },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    ws_connecting: ['p(99)<1000'],
    checks: ['rate>0.99'],
    ws_delivery_latency: ['p(99)<1000'], // SLO: 배달 p99 < 1s (부하모델-chat.md §4)
  },
};

function userIdOf(token) {
  // JWT payload 의 userId 클레임. base64url 패딩 보정.
  let p = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  while (p.length % 4) p += '=';
  return JSON.parse(b64decode(p, 'std', 's')).userId;
}

export function setup() {
  const rooms = [];
  for (let p = 0; p < PAIRS; p++) {
    const tokA = login(account(2 * p + 1));
    const tokB = login(account(2 * p + 2));
    const idA = userIdOf(tokA);
    const idB = userIdOf(tokB);
    const res = http.post(
      `${CHAT}/v1/chat/rooms/chat`,
      JSON.stringify({ chatContext: 'GENERAL', participantIds: [idA, idB], deviceId: `k6-setup-${p}` }),
      { headers: { ...bearer(tokA), 'Content-Type': 'application/json' }, tags: { name: 'POST /rooms/chat' } }
    );
    const roomId = res.json('data.roomId');
    if (!roomId) fail(`room create failed (pair ${p}): ${res.status} ${String(res.body).slice(0, 200)}`);
    rooms.push({ roomId, tokA, tokB });
  }
  console.log(`setup: ${rooms.length}개 1:1 방 준비 완료`);
  return { rooms };
}

export default function (data) {
  const pairIdx = Math.floor((__VU - 1) / 2) % data.rooms.length;
  const side = (__VU - 1) % 2; // 0=A, 1=B
  const room = data.rooms[pairIdx];
  const token = side ? room.tokB : room.tokA;
  let connected = false;

  const res = ws.connect(CHAT_WS, {}, (socket) => {
    socket.on('open', () => socket.send(connectFrame(token, `k6-${__VU}`)));

    socket.on('message', (msg) => {
      if (!connected && msg.startsWith('CONNECTED')) {
        connected = true;
        socket.send(subscribeFrame(`sub-${__VU}`, room.roomId));
        socket.setInterval(
          () => socket.send(chatSendFrame(room.roomId, `hello v=${__VU} t=${Date.now()}`, false)),
          MSG_INTERVAL_MS
        );
      } else if (msg.startsWith('MESSAGE')) {
        const m = /v=(\d+) t=(\d+)/.exec(msg);
        if (m && Number(m[1]) !== __VU) {
          deliveryLatency.add(Date.now() - Number(m[2]));
          delivered.add(1);
        }
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
