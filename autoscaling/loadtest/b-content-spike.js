// 시나리오 B - 콘텐츠 조회 스파이크 (content-service)
// 읽기 경로라 target 500. 세 경로를 섞는다.
//   /feeds/scroll  커서 페이징. LAZY 연관(category·hashtags) 때문에 과거 N+1 이 관측된 지점
//                  (application.yml default_batch_fetch_size 주석 · rca-agent NF-11)
//   /feeds/hot     hotScore 정렬 + 오프셋 페이징
//   /feeds/{id}    단건 상세
// 토큰은 VU 당 1회만 발급(setup)해서 auth 부하가 content 측정에 섞이지 않게 한다.
//   k6 run autoscaling/loadtest/b-content-spike.js -e CONTENT_URL=https://<INGRESS>/api
import http from 'k6/http';
import { check, group } from 'k6';
import { CONTENT, account, login, bearer, feedIds } from './lib/common.js';

export const options = {
  stages: [
    { duration: '1m', target: 100 },
    { duration: '2m', target: 500 }, // 스파이크
    { duration: '2m', target: 500 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:GET /feeds/scroll}': ['p(99)<800'],
    'http_req_duration{name:GET /feeds/hot}': ['p(99)<800'],
    'http_req_duration{name:GET /feeds/{id}}': ['p(99)<500'],
  },
};

export function setup() {
  // 실재하는 피드 ID 를 뽑아 둔다. 없으면 단건 조회는 건너뛴다.
  return { token: login(account(1)), feedIds: feedIds(100) };
}

export default function (data) {
  const h = { headers: bearer(data.token) };

  group('scroll', () => {
    const res = http.get(`${CONTENT}/feeds/scroll?size=20`, {
      ...h,
      tags: { name: 'GET /feeds/scroll' },
    });
    check(res, { 'scroll 200': (r) => r.status === 200 });
  });

  group('hot', () => {
    const res = http.get(`${CONTENT}/feeds/hot?page=0&size=20`, {
      ...h,
      tags: { name: 'GET /feeds/hot' },
    });
    check(res, { 'hot 200': (r) => r.status === 200 });
  });

  if (data.feedIds.length) {
    const id = data.feedIds[(__VU + __ITER) % data.feedIds.length];
    const res = http.get(`${CONTENT}/feeds/${id}`, { ...h, tags: { name: 'GET /feeds/{id}' } });
    check(res, { 'detail 200': (r) => r.status === 200 });
  }
}
