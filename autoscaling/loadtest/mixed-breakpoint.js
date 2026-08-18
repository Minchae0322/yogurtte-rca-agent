// 혼합 Breakpoint - 실제 트래픽 비율(content 80 / auth 15 / chat 5)로 계단 상승,
// "동시 사용자 N명" 한계를 입력이 아니라 발견으로 만든다. (설계 1 · Phase 3)
// 주의: 한계 탐색은 ingress 직결로 - CloudFront 타임아웃(~30s)이 한계를 오염시킨다.
//   k6 run autoscaling/loadtest/mixed-breakpoint.js -e VUS_MAX=1000
// 각 VU는 iteration마다 가중치로 행동을 고른다: 읽기 80% · 로그인 15% · 챗 목록 5%.
// think time 없는 공격적 부하 - 사람 수로 환산하려면 T1.5(여정)와의 배율을 같이 본다.
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
    // 로그인(BCrypt)이 15% 섞여 p99를 지배하므로, abort는 로그인을 뺀 읽기 경로 p99와
    // 전체 실패율로 판정한다. 그래야 "로그인 꼬리"가 아니라 "시스템 한계"에서 멈춘다.
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
  if (dice < 80) {
    // 읽기 80%: 스크롤과 상세를 반반
    if (dice < 40) {
      check(http.get(`${CONTENT}/feeds/scroll?size=20`, { ...h, tags: { name: 'GET /feeds/scroll' } }),
        { 'scroll 200': (r) => r.status === 200 });
    } else if (data.feeds.length) {
      const id = pick(data.feeds, __VU + __ITER);
      check(http.get(`${CONTENT}/feeds/${id}`, { ...h, tags: { name: 'GET /feeds/{id}' } }),
        { 'detail 200': (r) => r.status === 200 });
    }
  } else if (dice < 95) {
    // 로그인 15%
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
