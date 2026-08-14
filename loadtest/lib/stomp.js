// 서버는 raw WS 가 아니라 SockJS + STOMP 다 (WebSocketConfig: addEndpoint("/ws").withSockJS()).
// - 엔드포인트: context-path 포함 /api/ws/websocket  ← SockJS 의 raw WS transport
// - CONNECT 에 Authorization(Bearer) + X-Device-Id 가 없으면 StompConnectHandler 가 끊는다
// - 임의 JSON 을 보내면 브로커가 버린다. 아래 프레임 형식이어야 한다.
const NUL = '\u0000'; // STOMP 프레임 종료 문자

export function frame(command, headers, body = '') {
  const head = Object.entries(headers)
    .map(([k, v]) => `${k}:${v}`)
    .join('\n');
  return `${command}\n${head}\n\n${body}${NUL}`;
}

export function connectFrame(token, deviceId) {
  return frame('CONNECT', {
    'accept-version': '1.2',
    'heart-beat': '0,0',
    Authorization: `Bearer ${token}`,
    'X-Device-Id': deviceId,
    'X-Device-Type': 'WEB',
  });
}

export function subscribeFrame(id, roomId) {
  return frame('SUBSCRIBE', { id, destination: `/topic/chatroom/${roomId}` });
}

// enterRoom=true 면 Chat-Room-Id 헤더가 붙어 입장 + 읽음처리 경로까지 탄다(실재하는 방일 때만).
export function chatSendFrame(roomId, text, enterRoom = false) {
  const headers = { destination: '/app/chat/send', 'content-type': 'application/json' };
  if (enterRoom) headers['Chat-Room-Id'] = roomId;
  return frame(
    'SEND',
    headers,
    JSON.stringify({ roomId, actionType: 'TEXT', messageContent: text })
  );
}
