// G/H/I. 한계 탐색 - 같은 요청을 세 가지 부하 모양으로 때린다. 모양만 다르고 대상은 같아야
// "어느 모양에서 깨지는가"를 비교할 수 있어서 한 파일로 묶었다.
//
//   breakpoint  RPS 를 계단식으로 올리다 임계값이 깨지면 중단 → 한계 처리량을 숫자로 얻는다
//   spike       0 → 극대 → 0. 순간 폭주와 회복 시간(오토스케일/커넥션풀 복구)을 본다
//   soak        저부하 1시간. 누수(힙·커넥션·FD)를 본다. 짧은 테스트로는 절대 안 보이는 것
//
//   k6 run autoscaling/loadtest/stress.js -e PROFILE=breakpoint -e TARGET=feeds-scroll -e RPS_MAX=2000
//   k6 run autoscaling/loadtest/stress.js -e PROFILE=spike      -e TARGET=login
//   k6 run autoscaling/loadtest/stress.js -e PROFILE=soak       -e TARGET=feeds-scroll -e DURATION=1h
//
// 볼 것: breakpoint 는 중단 시점의 RPS, spike 는 임계값 복귀까지 걸린 시간,
//        soak 는 jvm_memory_used_bytes 기울기와 hikaricp_connections_active 의 우상향 여부.
import http from 'k6/http';
import { check } from 'k6';
import { CONTENT, AUTH, account, PASSWORD, tokenPool, bearer, pick, feedIds } from './lib/common.js';

const PROFILE = __ENV.PROFILE || 'breakpoint';
const TARGET = __ENV.TARGET || 'feeds-scroll';
const RPS_MAX = Number(__ENV.RPS_MAX || 1000);
const RPS_SOAK = Number(__ENV.RPS_SOAK || 50);
const DURATION = __ENV.DURATION || '1h';

const PROFILES = {
  // 계단식 상승 + abortOnFail: 깨지는 지점에서 스스로 멈춘다
  breakpoint: {
    executor: 'ramping-arrival-rate',
    startRate: 10,
    timeUnit: '1s',
    preAllocatedVUs: 100,
    maxVUs: 2000,
    stages: [
      { duration: '2m', target: Math.round(RPS_MAX * 0.1) },
      { duration: '2m', target: Math.round(RPS_MAX * 0.25) },
      { duration: '2m', target: Math.round(RPS_MAX * 0.5) },
      { duration: '2m', target: Math.round(RPS_MAX * 0.75) },
      { duration: '4m', target: RPS_MAX },
    ],
  },
  // 30초 폭주 후 즉시 0. 이후 저부하로 회복 시간을 잰다
  spike: {
    executor: 'ramping-arrival-rate',
    startRate: 10,
    timeUnit: '1s',
    preAllocatedVUs: 200,
    maxVUs: 3000,
    stages: [
      { duration: '1m', target: 20 },
      { duration: '10s', target: RPS_MAX }, // 급상승
      { duration: '30s', target: RPS_MAX },
      { duration: '10s', target: 20 }, // 급하강
      { duration: '3m', target: 20 }, // 회복 관측 구간
    ],
  },
  soak: {
    executor: 'constant-arrival-rate',
    rate: RPS_SOAK,
    timeUnit: '1s',
    duration: DURATION,
    preAllocatedVUs: 100,
    maxVUs: 500,
  },
};

export const options = {
  scenarios: { [PROFILE]: PROFILES[PROFILE] },
  thresholds: {
    // breakpoint 만 중단시킨다. spike/soak 는 끝까지 돌려야 회복·누수가 보인다.
    http_req_duration: [
      { threshold: 'p(99)<1000', abortOnFail: PROFILE === 'breakpoint', delayAbortEval: '30s' },
    ],
    http_req_failed: [
      { threshold: 'rate<0.02', abortOnFail: PROFILE === 'breakpoint', delayAbortEval: '30s' },
    ],
  },
};

export function setup() {
  return { tokens: tokenPool(20), feeds: feedIds(100) };
}

export default function (data) {
  const h = { headers: bearer(pick(data.tokens, __VU)) };

  switch (TARGET) {
    case 'login':
      check(
        http.post(`${AUTH}/login`, JSON.stringify({ email: account(__VU), password: PASSWORD }), {
          headers: { 'Content-Type': 'application/json' },
          tags: { name: 'POST /login' },
        }),
        { '200': (r) => r.status === 200 }
      );
      break;

    case 'feed-detail': {
      if (!data.feeds.length) return;
      const id = pick(data.feeds, __VU + __ITER);
      check(http.get(`${CONTENT}/feeds/${id}`, { ...h, tags: { name: 'GET /feeds/{id}' } }), {
        '200': (r) => r.status === 200,
      });
      break;
    }

    case 'hot':
      check(
        http.get(`${CONTENT}/feeds/hot?page=0&size=20`, { ...h, tags: { name: 'GET /feeds/hot' } }),
        { '200': (r) => r.status === 200 }
      );
      break;

    default: // feeds-scroll
      check(
        http.get(`${CONTENT}/feeds/scroll?size=20`, {
          ...h,
          tags: { name: 'GET /feeds/scroll' },
        }),
        { '200': (r) => r.status === 200 }
      );
  }
}
