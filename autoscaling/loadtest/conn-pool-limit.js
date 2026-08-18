// Connection Pool 한계 곡선 - Hikari pending이 0을 벗어나는 VU 지점을 찾는다 (설계 1 · 3-3)
// 경로: 분산 key 리액션 쓰기 (트랜잭션마다 커넥션 점유, 락 직렬화는 배제해 풀 자체를 잰다)
// 서버측은 k6가 못 보므로 같은 시각 Mimir에서 hikaricp_connections_{active,pending}과
// hikaricp_connections_acquire_seconds를 함께 본다. 판정 분기(설계 1):
//   pending↑ + DB 여유 → Pool 상향 / DB 포화 → 쿼리·인덱스·캐시 / 쿼리 자체 느림 → SQL
//   k6 run autoscaling/loadtest/conn-pool-limit.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { CONTENT, tokenPool, pick, bearer, feedIds } from './lib/common.js';

const TOKEN_POOL = Number(__ENV.TOKEN_POOL || 50);

export const options = {
  // 계단마다 2분 - Mimir 15s 해상도에서 단계별 정점이 분리돼 보이도록
  stages: [
    { duration: '2m', target: 20 },
    { duration: '2m', target: 50 },
    { duration: '2m', target: 100 },
    { duration: '2m', target: 200 },
    { duration: '2m', target: 400 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    'http_req_duration{name:POST /feeds/{id}/reactions}': ['p(99)<3000'],
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {
  return { tokens: tokenPool(TOKEN_POOL), feeds: feedIds(100) };
}

export default function (data) {
  if (!data.feeds.length) return;
  const id = pick(data.feeds, __VU + __ITER); // 분산 key - 락이 아니라 풀을 잰다
  const res = http.post(`${CONTENT}/feeds/${id}/reactions?reactionType=LIKE`, null, {
    headers: bearer(pick(data.tokens, __VU)),
    tags: { name: 'POST /feeds/{id}/reactions' },
  });
  check(res, { 'reaction 2xx': (r) => r.status >= 200 && r.status < 300 });
  sleep(0.2);
}
