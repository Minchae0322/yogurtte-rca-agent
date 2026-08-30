// 현실 모드 - VU = 동접. think time 가중 분포로 실사용자 요청 간격을 모사한다.
// (부하모델.md의 baseline 티어 검증용. 압력 모드(b-content-spike)와 축이 다르므로
//  회차 비교 시 섞지 않는다.)
//
//   VU 1개 = 사용자 1명: 행동 1회 후 분포에서 뽑은 시간만큼 쉰다.
//   분포(가중 평균 ~15초 - 부하모델.md §1의 think time과 일치 유지):
//     헤비 스크롤러 20% 1~3s · 일반 70% 5~25s · 느긋(방치 꼬리) 10% 30~50s
//   (v1: 헤비10/일반65/느긋25는 템포가 너무 느긋하다고 판단해 비중 재배분.
//    평균 15초는 보존 - 바꾸면 부하모델의 83rps·동접6,000 사슬이 전부 재계산된다)
//   기대 rps = VUS ÷ (평균 think 14.9s + 응답) ≈ VUS/15 → 동접 1,250이면 ~83 rps
//
//   k6 run autoscaling/loadtest/b-content-realistic.js \
//     -e CONTENT_URL=http://<INGRESS>/api/content -e AUTH_URL=http://<INGRESS>/api/auth \
//     -e VUS=1250
//
// 주의: 단일 소스 IP로 VU 수만큼 keepalive 커넥션이 열린다 - ingress
// limit-connections 시험용 상향(2000) 필수. 동접 2,000 초과 시험은 상향값도 올려야 한다.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { CONTENT, account, login, bearer, feedIds } from './lib/common.js';

const VUS = Number(__ENV.VUS || 1250);

export const options = {
  scenarios: {
    realistic: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: VUS }, // 점진 유입 (피크 진입)
        { duration: '5m', target: VUS }, // 피크 유지
        { duration: '1m', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  // 부하모델.md baseline SLO: 평시 피크에서 p99 < 800ms · 실패 0
  thresholds: {
    http_req_failed: ['rate<0.001'],
    http_req_duration: ['p(99)<800'],
  },
};

// 헤비 20% 1~3s · 일반 70% 5~25s · 느긋 10% 30~50s → 가중 평균 ~14.9s
function thinkTime() {
  const r = Math.random();
  if (r < 0.20) return 1 + Math.random() * 2;
  if (r < 0.90) return 5 + Math.random() * 20;
  return 30 + Math.random() * 20;
}

export function setup() {
  return { token: login(account(1)), feedIds: feedIds(100) };
}

export default function (data) {
  const h = { headers: bearer(data.token) };

  // 행동 믹스는 T2-B와 동일 비율(scroll/hot/detail 1:1:1) - 축은 다르되 경로는 같게
  const r = Math.random();
  if (r < 1 / 3) {
    const res = http.get(`${CONTENT}/feeds/scroll?size=20`, { ...h, tags: { name: 'GET /feeds/scroll' } });
    check(res, { 'scroll 200': (x) => x.status === 200 });
  } else if (r < 2 / 3) {
    const res = http.get(`${CONTENT}/feeds/hot?page=0&size=20`, { ...h, tags: { name: 'GET /feeds/hot' } });
    check(res, { 'hot 200': (x) => x.status === 200 });
  } else if (data.feedIds.length) {
    const id = data.feedIds[Math.floor(Math.random() * data.feedIds.length)];
    const res = http.get(`${CONTENT}/feeds/${id}`, { ...h, tags: { name: 'GET /feeds/{id}' } });
    check(res, { 'detail 200': (x) => x.status === 200 });
  }

  sleep(thinkTime());
}
