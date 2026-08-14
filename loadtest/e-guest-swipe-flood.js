// E. 게스트 스와이프 폭주 - 인증 없는 쓰기 경로에 봇/이벤트 트래픽이 몰릴 때
//
// POST /battles/{battleId}/swipe 는 로그인 없이도 통한다. 비로그인은 gid 쿠키로 식별되고
// (CurrentVoterIdArgumentResolver), 쿠키가 없으면 요청마다 새 게스트가 발급된다.
// → 인증 게이트가 없어 auth 병목을 거치지 않고 content 의 쓰기 경로를 그대로 때린다.
//
// 요청 1건이 하는 일: swipe insert + BattleItem 카운터 update + Battle 카운터 update
//   = 같은 배틀·아이템 행에 update 가 집중된다(핫로우). D 가 명시적 락이라면 이쪽은 update 경합이다.
//
// 두 가지 모드:
//   기본        VU 마다 고정 gid → 멱등 덮어쓰기 경로 (update 위주)
//   -e NEW_GUEST=1  매 요청 새 gid → insert 폭증 + 테이블 증가 (더 가혹, 데이터 오염 주의)
//
// 볼 것: 배틀/아이템 행의 update 경합 · deadlock 로그 · content 의 write p99
import http from 'k6/http';
import { check } from 'k6';
import { CONTENT, guestCookie, pick, battleTargets } from './lib/common.js';

const VUS = Number(__ENV.VUS || 300);
const NEW_GUEST = __ENV.NEW_GUEST === '1';
const VERDICTS = ['STRONG_PICK', 'PICK', 'PASS'];

export const options = {
  stages: [
    { duration: '1m', target: VUS },
    { duration: '3m', target: VUS },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{name:POST /battles/{id}/swipe}': ['p(99)<1000'],
  },
};

export function setup() {
  const targets = battleTargets(3);
  if (!targets.length) throw new Error('활성 배틀/아이템이 없다. 배틀 seed 데이터를 먼저 넣어야 한다.');
  return { targets };
}

export default function (data) {
  const t = pick(data.targets, __VU + __ITER);
  const gid = NEW_GUEST ? `${__VU}-${__ITER}` : `${__VU}`;
  const res = http.post(
    `${CONTENT}/battles/${t.battleId}/swipe`,
    JSON.stringify({ itemId: t.itemId, verdict: pick(VERDICTS, __ITER) }),
    {
      headers: { 'Content-Type': 'application/json', ...guestCookie(gid) },
      tags: { name: 'POST /battles/{id}/swipe' },
    }
  );
  check(res, { 'swipe 200': (r) => r.status === 200 });
}
