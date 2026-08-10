/**
 * docs/charts/round-2-score-tokens.svg 생성기.
 *
 * 원본 SVG는 스크립트 없이 손으로 나갔고(e71aa30), 고칠 때마다 SVG를 역파싱해야 했다.
 * 데이터·팔레트·문안은 그대로 두고 판독성만 손본 판이다.
 *
 *   node docs/charts/make-round2-chart.js
 *
 * 회색(#c9c7c1)은 표면 대비 1.65:1이라 색만으로는 읽히지 않는다 — 그래서 오버헤드 띠에는
 * 보이는 라벨을 하나 붙인다(전 문항 동일한 상수라 11번 반복하지 않는다).
 */
const fs = require('fs');
const path = require('path');

// 채점 대장 기준 회차 2 실행 평균. tok은 컨텍스트 토큰(오버헤드 제외, ×1,000).
// 문항 ID(AP-1 등)는 출처 추적용으로만 둔다 - 차트에는 그리지 않는다. 실행 간 편차도 대장에만 있다.
// 순서는 (a) 총점 내림차순이고 (b)도 같은 순서를 쓴다 - 위아래가 같은 자리여야 대조가 된다.
const ITEMS = [
  { id: 'CH-2', name: ['알림 컨슈머', '전멸'],       score: 100,  tok: 176 },
  { id: 'IN-2', name: ['Kafka 다운', '알림 유실'],   score: 99,   tok: 135 },
  { id: 'IN-1', name: ['Redis 다운', '복합 증상'], score: 98,   tok: 118 },
  { id: 'AP-3', name: ['해시태그', '중복 저장'],     score: 97.5, tok: 105 },
  { id: 'CH-1', name: ['MongoDB 다운', 'DLQ 적재'],  score: 97.5, tok: 148 },
  { id: 'AP-1', name: ['댓글 길이', '초과'],       score: 90,   tok: 67 },
  { id: 'AU-4', name: ['인증 다운', '익명 폴백'],    score: 85.5, tok: 133 },
  { id: 'AU-2', name: ['인증 서버', '전면 다운'],  score: 65,   tok: 97 },
  { id: 'AP-2', name: ['팔로우 조회', 'NPE'],        score: 53,   tok: 77 },
  { id: 'CH-3', name: ['Mongo 장애', '25초'],        score: 4,    tok: 167 },
  { id: 'AU-3', name: ['JWT 시크릿', '드리프트'],    score: 0,    tok: null },
];

const OVERHEAD = 48;           // CLI 오버헤드 실측 평균 (×1,000) — 회차 2 전 문항 공통
const C = {
  bg: '#fcfcfb', blue: '#2a78d6', gray: '#c9c7c1',
  ink: '#0b0b0b', mid: '#52514e', faint: '#8f8d88', rule: '#e2e0dc',
};
const FONT = "'Malgun Gothic','맑은 고딕','Apple SD Gothic Neo',sans-serif";

const W = 1680, H = 1240;
const PAD = { left: 72, right: 64 };
const PLOT_W = W - PAD.left - PAD.right;
const PLOT_H = 330;
const A_TOP = 178, B_TOP = 756;
const STEP = PLOT_W / ITEMS.length;
const BAR = Math.round(STEP * 0.5);
const GAP = 2;                 // 쌓인 두 칸 사이 표면 간격

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');
const out = [];
const push = (s) => out.push(s);

const text = (x, y, s, { size = 17, fill = C.ink, anchor = 'middle', weight = 400 } = {}) =>
  `<text x="${x}" y="${y}" font-size="${size}" fill="${fill}" text-anchor="${anchor}"` +
  `${weight !== 400 ? ` font-weight="${weight}"` : ''}>${esc(s)}</text>`;

/** 위쪽 두 모서리만 둥근 막대. h가 반지름보다 작으면 사각으로 떨어진다. */
function bar(cx, base, h, fill, r = 4) {
  if (h <= 0) return '';
  const x = cx - BAR / 2, y = base - h, rr = Math.min(r, h);
  return `<path d="M${x} ${base} V${y + rr} Q${x} ${y} ${x + rr} ${y} H${x + BAR - rr} ` +
         `Q${x + BAR} ${y} ${x + BAR} ${y + rr} V${base} Z" fill="${fill}"/>`;
}

/** 패널 하나 — 제목·부제는 왼쪽 정렬, 눈금 대신 막대 위 직접 라벨을 쓴다. */
function panel(top, title, subtitle, draw) {
  const base = top + PLOT_H;
  push(text(PAD.left, top - 108, title, { size: 25, weight: 700, anchor: 'start' }));
  push(text(PAD.left, top - 78, subtitle, { size: 17, fill: C.mid, anchor: 'start' }));
  push(`<line x1="${PAD.left}" y1="${base}" x2="${W - PAD.right}" y2="${base}" stroke="${C.rule}" stroke-width="1.5"/>`);
  ITEMS.forEach((item, i) => draw(item, PAD.left + STEP * i + STEP / 2, base));
  ITEMS.forEach((item, i) => {
    const x = PAD.left + STEP * i + STEP / 2;
    push(text(x, base + 36, item.name[0], { size: 19, weight: 600 }));
    push(text(x, base + 60, item.name[1], { size: 19, weight: 600 }));
  });
}

push(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="${FONT}">`);
push(`<rect width="${W}" height="${H}" fill="${C.bg}"/>`);

// (a) 총점 — 만점선 하나만 두고 나머지 격자는 없앤다. 값은 막대마다 직접 붙는다.
const scoreScale = (v) => (v / 100) * PLOT_H;
panel(A_TOP,
  '(a) 회차 2 총점 - 문항별 실행 평균',
  '장애마다 자연어 질문 하나로 조사시킨 뒤, 나온 원인 리포트를 100점 만점으로 채점 (실행 2회 평균)',
  (item, cx, base) => {
    const h = scoreScale(item.score);
    push(bar(cx, base, h, C.blue));
    push(text(cx, base - h - 14, item.score, { size: 27, weight: 700 }));
  });

// (b) 입력 토큰 — 회색 오버헤드 위에 컨텍스트를 쌓는다. 숫자는 컨텍스트만.
const TOK_MAX = 245;
const tokScale = (v) => (v / TOK_MAX) * PLOT_H;
panel(B_TOP,
  '(b) 회차 2 입력 토큰 - 문항별 실행 평균',
  '조사 1회에 LLM으로 들어간 입력량. 파랑은 실제 관측 데이터, 회색은 어느 조사에나 붙는 CLI 고정비 (단위 1,000 토큰)',
  (item, cx, base) => {
    if (item.tok == null) {
      push(text(cx, base - 10, '-', { size: 34, fill: C.mid, weight: 700 }));
      return;
    }
    const ho = tokScale(OVERHEAD), hc = tokScale(item.tok);
    push(bar(cx, base, ho, C.gray, 0));
    push(bar(cx, base - ho - GAP, hc, C.blue));
    push(text(cx, base - ho - GAP - hc - 14, item.tok, { size: 27, weight: 700 }));
  });
// 회색은 대비가 낮아 라벨이 필요하다. 상수이므로 오른쪽 끝에 한 번만 적는다.
const ovY = B_TOP + PLOT_H - tokScale(OVERHEAD);
push(`<line x1="${PAD.left}" y1="${ovY}" x2="${W - PAD.right}" y2="${ovY}" stroke="${C.rule}" stroke-width="1"/>`);
push(text(W - PAD.right, ovY - 12, '오버헤드 48', { size: 15, fill: C.faint, anchor: 'end' }));

push(text(PAD.left, H - 30,
  'Redis 다운은 실행 1회 · 댓글 길이 초과는 구 v2 4항목 자로 채점(루브릭 v3 아님) · 인증 서버 전면 다운은 실행 간 편차 ±10 초과로 §8.1 인용 보류',
  { size: 16, fill: C.faint, anchor: 'start' }));
push('</svg>');

const dest = path.join(__dirname, 'round-2-score-tokens.svg');
fs.writeFileSync(dest, out.join(''), 'utf8');
console.log('wrote', dest);
