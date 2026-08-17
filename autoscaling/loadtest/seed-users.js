// 부하테스트용 계정 생성. 회원가입(/user/auth/signup)은 이메일 인증코드가 필요해서 못 쓰고,
// POST /api/user (UserController.createUser) 는 인증·인증코드 없이 바로 만들어진다.
//   k6 run autoscaling/loadtest/seed-users.js -e USER_COUNT=300
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { AUTH, USER_COUNT, PASSWORD, account } from './lib/common.js';

export const options = {
  scenarios: {
    seed: { executor: 'shared-iterations', vus: 10, iterations: USER_COUNT, maxDuration: '10m' },
  },
};

export default function () {
  const id = account(exec.scenario.iterationInTest + 1);
  const res = http.post(
    `${AUTH}/user`,
    JSON.stringify({ username: id, email: id, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  // 재실행 시 중복은 정상 (4xx)
  check(res, { 'created or already exists': (r) => r.status === 200 || r.status >= 400 });
}
