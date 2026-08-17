// 시나리오 A - 로그인 폭주 (auth-service)
// 부하 지점: 요청 1건마다 BCrypt 검증 + JWT 서명 + refresh_token 테이블 upsert.
// BCrypt 는 CPU 를 의도적으로 태우므로 300VU 에서 p99<500ms 는 통과보다 실패가 정상적인 기준선이다.
//   k6 run autoscaling/loadtest/a-login-burst.js -e AUTH_URL=https://<INGRESS>/api
import http from 'k6/http';
import { check } from 'k6';
import { AUTH, PASSWORD, account } from './lib/common.js';

export const options = {
  stages: [
    { duration: '1m', target: 50 },
    { duration: '2m', target: 300 }, // 스파이크
    { duration: '2m', target: 300 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(99)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.post(
    `${AUTH}/login`,
    JSON.stringify({ email: account(__VU), password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /login' } }
  );
  check(res, {
    'status 200': (r) => r.status === 200,
    'accessToken 발급': (r) => r.status === 200 && !!r.json('accessToken'),
  });
}
