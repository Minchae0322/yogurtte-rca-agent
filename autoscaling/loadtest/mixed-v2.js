// 혼합 Breakpoint v2 - 로그인 비중을 15% → 10%로 재보정한 종합 부하테스트.
//   k6 run autoscaling/loadtest/mixed-v2.js -e VUS_MAX=1200
// 근거: 커뮤니티에서 로그인은 세션당 1회이고 조회는 여러 번이다 - journey-v2 실측에서도
//   세션 흐름 기반 자연 비율이 로그인 ~11%로 나왔다. v1의 15%는 인증 축을 과대 대표.
// 단일 변수 원칙: v1(mixed-breakpoint) 대비 바뀐 것은 비율 하나다 - 읽기 80→85(로그인의
//   5%p를 흡수) · 로그인 15→10 · 챗 5 유지. 계단·abort 기준·sleep·경로는 v1과 동일.
//   쓰기 축(작성·좋아요) 추가는 비율 델타와 섞이므로 이 버전에 넣지 않는다.
// 경로: v1과 같은 CloudFront 경유로 돈다 - 직결 전환도 변수라서. (직결은 별도 회차)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { AUTH, CONTENT, CHAT, PASSWORD, account, tokenPool, pick, bearer, feedIds } from './lib/common.js';

const VUS_MAX = Number(__ENV.VUS_MAX || 1000);
const TOKEN_POOL = Number(__ENV.TOKEN_POOL || 50);

export const options = {
  stages: [
    { duration: '2m', target: 100 },
    { duration: '2m', target: 300 },
    { duration: '2m', target: 600 },
    { duration: '2m', target: Math.min(1000, VUS_MAX) },
    { duration: '2m', target: VUS_MAX },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    // 로그인(BCrypt) 꼬리가 p99를 지배하므로 abort는 읽기 경로 p99 + 전체 실패율로 판정
    // (v1 1차 실행이 38 VU에서 조기 중단된 교훈 - 기준 동일 유지).
    'http_req_duration{name:GET /feeds/scroll}': [{ threshold: 'p(99)<3000', abortOnFail: true, delayAbortEval: '120s' }],
    'http_req_duration{name:GET /feeds/{id}}': [{ threshold: 'p(99)<3000', abortOnFail: true, delayAbortEval: '120s' }],
    http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '120s' }],
    // 로그인 p99는 참고용으로 기록만 (abort 안 함)
    'http_req_duration{name:POST /login}': ['p(99)<10000'],
  },
};

export function setup() {
  return { tokens: tokenPool(TOKEN_POOL), feeds: feedIds(100) };
}

export default function (data) {
  const dice = Math.random() * 100;
  const h = { headers: bearer(pick(data.tokens, __VU)) };
  if (dice < 85) {
    // 읽기 85%: 스크롤과 상세를 반반
    if (dice < 42.5) {
      check(http.get(`${CONTENT}/feeds/scroll?size=20`, { ...h, tags: { name: 'GET /feeds/scroll' } }),
        { 'scroll 200': (r) => r.status === 200 });
    } else if (data.feeds.length) {
      const id = pick(data.feeds, __VU + __ITER);
      check(http.get(`${CONTENT}/feeds/${id}`, { ...h, tags: { name: 'GET /feeds/{id}' } }),
        { 'detail 200': (r) => r.status === 200 });
    }
  } else if (dice < 95) {
    // 로그인 10%
    const res = http.post(`${AUTH}/login`,
      JSON.stringify({ email: account(__VU), password: PASSWORD }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /login' } });
    check(res, { 'login 200': (r) => r.status === 200 });
  } else {
    // 챗 목록 5% (WS 연결 유지 부하는 WS-A가 따로 잰다 - 한 시험 한 자원)
    check(http.get(`${CHAT}/v1/chat/rooms`, { ...h, tags: { name: 'GET /chat/rooms' } }),
      { 'rooms 200': (r) => r.status === 200 });
  }
  sleep(0.3); // 연결 폭주 방지 최소 간격 - 처리량 축은 arrival rate가 아니라 VU 계단
}
