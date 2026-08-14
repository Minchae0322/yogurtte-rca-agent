import http from 'k6/http';
import { fail } from 'k6';

// context-path 는 세 서비스 모두 /api (auth 8081 · content 8082 · chat 8084)
export const AUTH = __ENV.AUTH_URL || 'http://localhost:8081/api';
export const CONTENT = __ENV.CONTENT_URL || 'http://localhost:8082/api';
export const CHAT = __ENV.CHAT_URL || 'http://localhost:8084/api';
export const CHAT_WS = __ENV.CHAT_WS_URL || 'ws://localhost:8084/api/ws/websocket';

export const USER_COUNT = Number(__ENV.USER_COUNT || 200);
export const PASSWORD = __ENV.PASSWORD || 'test1234!';

// 로그인 계정 풀. seed-users.js 가 만든 것과 같은 규칙이어야 한다.
export const account = (n) => `load${((n - 1) % USER_COUNT) + 1}@test.com`;

// LoginRequestDto 의 필드명은 email 이지만 실제 인증은 username 으로 조회한다
// (CustomUserDetailsService → findByUsernameAndProvider(username, COMMON)).
// 시드에서 username == email 로 맞춰 두었기 때문에 그대로 통한다.
export function login(id) {
  const res = http.post(`${AUTH}/login`, JSON.stringify({ email: id, password: PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'POST /login' },
  });
  if (res.status !== 200) fail(`login failed: ${res.status} ${res.body}`);
  return res.json('accessToken');
}

export function tokenPool(n) {
  const tokens = [];
  for (let i = 1; i <= n; i++) tokens.push(login(account(i)));
  return tokens;
}

export function bearer(token) {
  return { Authorization: `Bearer ${token}` };
}

// 비로그인 투표자는 gid 쿠키로 식별된다 (CurrentVoterIdArgumentResolver).
// VU 마다 다른 값을 주면 서로 다른 게스트로 집계된다.
export function guestCookie(id) {
  return { Cookie: `gid=k6-guest-${id}` };
}

export const pick = (arr, n) => arr[n % arr.length];

// ApiResponse<CursorResponse<...>> 형태가 버전마다 items/content 로 갈려서 둘 다 본다.
export function feedIds(limit = 100) {
  const res = http.get(`${CONTENT}/feeds/scroll?size=${limit}`);
  if (res.status !== 200) return [];
  const items = res.json('data.items') || res.json('data.content') || [];
  return items.map((f) => f.feedId || f.id).filter(Boolean);
}

// 스와이프 대상. 배틀 목록 → 각 배틀의 next 로 활성 아이템 ID 를 얻는다.
export function battleTargets(maxBattles = 3) {
  const res = http.get(`${CONTENT}/battles?page=0&size=${maxBattles}`);
  if (res.status !== 200) return [];
  const battles = res.json('data.content') || [];
  const targets = [];
  for (const b of battles) {
    const id = b.battleId || b.id;
    if (!id) continue;
    const next = http.get(`${CONTENT}/battles/${id}/swipe/next?size=20`);
    if (next.status !== 200) continue;
    const items = next.json('data.items') || next.json('data.nextItems') || [];
    for (const it of items) {
      const itemId = it.itemId || it.id;
      if (itemId) targets.push({ battleId: id, itemId });
    }
  }
  return targets;
}
