// F. 서비스 간 캐스케이드 - auth 가 느려질 때 content 가 같이 죽는가
//
// content 는 작성자 정보를 auth 에서 가져온다(ExternalUserApiClient, WebClient).
//   - 타임아웃 3초. 초과/에러면 예외 대신 "사용자{id}" 익명으로 폴백하고 user.fallback 카운터를 올린다.
//   - 캐시(UserCacheStore) 히트면 호출 자체가 없다. 그래서 캐시가 식어 있어야 이 경로가 드러난다.
//
// 설계: auth 를 로그인 폭주로 먼저 포화시키고(0~), 30초 뒤 content 읽기를 얹는다.
// 세 가지가 동시에 관측돼야 인과가 성립한다:
//   ① auth p99 상승  ② content 의 auth 호출 지연(3초 타임아웃 근처)  ③ user_fallback_total 증가
// ③ 없이 ①②만 오르면 폴백이 작동하지 않은 것이고, ③만 오르면 auth 가 아니라 캐시/네트워크 문제다.
//
// 볼 것(서버측): user_fallback_total · content→auth client span 지속시간(Tempo) ·
//               "[user-fallback]" 로그(Loki) · auth 의 tomcat_threads_busy
//   k6 run loadtest/f-cascade.js
import http from 'k6/http';
import { check } from 'k6';
import { CONTENT, AUTH, account, PASSWORD, tokenPool, bearer, pick, feedIds } from './lib/common.js';

const AUTH_VUS = Number(__ENV.AUTH_VUS || 300);
const READ_VUS = Number(__ENV.READ_VUS || 50);

export const options = {
  scenarios: {
    authBurst: {
      executor: 'ramping-vus',
      exec: 'authBurst',
      startVUs: 0,
      stages: [
        { duration: '30s', target: AUTH_VUS },
        { duration: '3m', target: AUTH_VUS },
        { duration: '30s', target: 0 },
      ],
    },
    contentRead: {
      executor: 'constant-vus',
      exec: 'contentRead',
      vus: READ_VUS,
      duration: '3m',
      startTime: '30s', // auth 가 이미 포화된 뒤에 얹는다
    },
  },
  thresholds: {
    // content 는 auth 가 죽어도 폴백으로 버텨야 한다. 이 임계값이 깨지면 격리가 안 된 것이다.
    'http_req_duration{name:GET /feeds/scroll}': ['p(99)<3000'],
    'http_req_failed{name:GET /feeds/scroll}': ['rate<0.01'],
  },
};

export function setup() {
  return { tokens: tokenPool(20), feeds: feedIds(100) };
}

export function authBurst() {
  http.post(`${AUTH}/login`, JSON.stringify({ email: account(__VU), password: PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'POST /login' },
  });
}

export function contentRead(data) {
  const h = { headers: bearer(pick(data.tokens, __VU)), tags: { name: 'GET /feeds/scroll' } };
  // scroll 은 작성자 정보를 여러 건 묶어 조회한다 → auth 로의 팬아웃이 가장 큰 읽기 경로
  const res = http.get(`${CONTENT}/feeds/scroll?size=20`, h);
  check(res, {
    'scroll 200 (폴백 포함)': (r) => r.status === 200,
    '3초 타임아웃 미만': (r) => r.timings.duration < 3000,
  });
}
