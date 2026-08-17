// D. 핫키 락 경합 - 같은 피드 1건에 리액션이 몰릴 때 (인플루언서 게시물 시나리오)
//
// FeedReactionService.toggleReaction 은 findByIdWithPessimisticLock 으로 feed 행을 잠근다.
// → 같은 feedId 에 몰린 요청은 DB 행 하나에서 직렬화된다. VU 를 올려도 처리량이 늘지 않고
//   커넥션이 락 대기로 묶여 HikariCP 가 먼저 마른다.
//
// 대조군을 같이 돌려야 "락 때문"이 증명된다:
//   실험군  k6 run autoscaling/loadtest/d-hotkey-reaction.js                 (한 피드에 집중)
//   대조군  k6 run autoscaling/loadtest/d-hotkey-reaction.js -e SPREAD=1     (여러 피드로 분산)
// 같은 VU·같은 시간에 처리량(http_reqs)과 p99 가 갈리면 원인은 부하량이 아니라 락이다.
//
// 볼 것: hikaricp_connections_pending · hikaricp_connections_acquire_seconds ·
//        MySQL lock wait timeout 로그 · tomcat_threads_busy
import http from 'k6/http';
import { check } from 'k6';
import { CONTENT, tokenPool, bearer, pick, feedIds } from './lib/common.js';

const SPREAD = __ENV.SPREAD === '1';
const VUS = Number(__ENV.VUS || 200);
const TYPES = ['LIKE', 'HOT'];

export const options = {
  stages: [
    { duration: '30s', target: VUS },
    { duration: '3m', target: VUS },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    // 임계값을 낮게 두는 이유: 여기서 깨지는 것을 보려고 돌리는 테스트다
    http_req_failed: ['rate<0.05'],
    'http_req_duration{name:POST /feeds/{id}/reactions}': ['p(99)<1000'],
  },
};

export function setup() {
  const feeds = feedIds(100);
  if (!feeds.length) throw new Error('피드가 없다. seed 데이터를 먼저 넣어야 한다.');
  return { tokens: tokenPool(50), feeds: SPREAD ? feeds : [Number(__ENV.FEED_ID) || feeds[0]] };
}

export default function (data) {
  const feedId = pick(data.feeds, __VU + __ITER);
  const type = pick(TYPES, __ITER); // 토글이라 같은 타입 반복 시 on/off 가 번갈아 일어난다
  const res = http.post(
    `${CONTENT}/feeds/${feedId}/reactions?reactionType=${type}`,
    null,
    {
      headers: bearer(pick(data.tokens, __VU)),
      tags: { name: 'POST /feeds/{id}/reactions' },
    }
  );
  check(res, { 'reaction 200': (r) => r.status === 200 });
}
