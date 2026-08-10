/**
 * docs/charts/round-2-3-5-score-tokens.svg 생성기.
 *
 * make-round2-chart.js와 같은 판형이다 — 데이터·문안은 기존 SVG(e71aa30) 그대로 두고,
 * 문항 ID 행을 지우고 눈금 대신 막대 위 직접 라벨을 쓰는 판독성 개선판.
 *
 *   node docs/charts/make-round235-chart.js
 *
 * 시리즈 색은 회차 2 파랑 · 회차 3 주황 · 회차 5 청록 — 3색 조합은 CVD ΔE 9.2로 검증됐고,
 * 청록은 표면 대비 2.74:1이라 색만으로는 안 읽힌다 → 모든 막대에 값 라벨을 직접 붙인다.
 */
const fs = require('fs');
const path = require('path');

// 채점 대장 기준 실행 평균. tok은 컨텍스트 토큰(오버헤드 제외, ×1,000).
// s2/s3/s5 = 회차별 총점, t2/t3/t5 = 회차별 토큰. null = 측정 불가(AU-3 회차 2 미실행).
// § = 개정 앵커 적용값 · † = 분석 단계만 기재 — 라벨 문자열에 그대로 싣는다.
// 문항 ID는 출처 추적용으로만 둔다 - 차트에는 그리지 않는다.
// 순서는 회차 2 총점 내림차순 — round-2 차트와 같은 자리여야 옆에 놓고 대조가 된다.
const ITEMS = [
  { id: 'CH-2', name: ['알림 컨슈머', '전멸'],     s: [100, '92.5', 100],   t: [176, 233, 50] },
  { id: 'IN-2', name: ['Kafka 다운', '알림 유실'], s: [99, 98, 98],         t: [135, 208, 70] },
  { id: 'IN-1', name: ['Redis 다운', '복합 증상'], s: [98, 98, 100],        t: [118, 250, 62] },
  { id: 'AP-3', name: ['해시태그', '중복 저장'],   s: ['97.5', 95, 100],    t: [105, 86, 43] },
  { id: 'CH-1', name: ['MongoDB 다운', 'DLQ 적재'],s: ['97.5', 93, 93],     t: [148, 225, 176] },
  { id: 'AP-1', name: ['댓글 길이', '초과'],       s: [90, '90§', 100],     t: [67, '134†', 41] },
  { id: 'AU-4', name: ['인증 다운', '익명 폴백'],  s: ['85.5', 88, 90],     t: [133, 116, '45†'] },
  { id: 'AU-2', name: ['인증 서버', '전면 다운'],  s: [65, 85, 85],         t: [97, 123, 61] },
  { id: 'AP-2', name: ['팔로우 조회', 'NPE'],      s: [53, '88§', 88],      t: [77, 112, 42] },
  { id: 'CH-3', name: ['Mongo 장애', '25초'],      s: [4, 85, 100],         t: [167, 138, 65] },
  { id: 'AU-3', name: ['JWT 시크릿', '드리프트'],  s: [0, '81§', 93],       t: [null, 137, '105†'] },
];

const SERIES = [
  { label: '회차 2',             color: '#2a78d6', overhead: 48 },
  { label: '회차 3 (도구 개선)', color: '#eb6834', overhead: 48 },
  { label: '회차 5 (접기·압축)', color: '#1baf7a', overhead: 59 },
];
const C = {
  bg: '#fcfcfb', gray: '#c9c7c1',
  ink: '#0b0b0b', mid: '#52514e', faint: '#8f8d88', rule: '#e2e0dc',
};
const FONT = "'Malgun Gothic','맑은 고딕','Apple SD Gothic Neo',sans-serif";

const W = 2200, H = 1280;
const PAD = { left: 76, right: 64 };
const PLOT_W = W - PAD.left - PAD.right;
const PLOT_H = 330;
const A_TOP = 196, B_TOP = 784;
const STEP = PLOT_W / ITEMS.length;
const BARW = 54, PITCH = BARW + 2;      // 그룹 안 이웃 막대는 표면 2px 간격

const num = (v) => parseFloat(String(v));  // '92.5' · '134†' → 수치
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');
const out = [];
const push = (s) => out.push(s);

const text = (x, y, s, { size = 17, fill = C.ink, anchor = 'middle', weight = 400 } = {}) =>
  `<text x="${x}" y="${y}" font-size="${size}" fill="${fill}" text-anchor="${anchor}"` +
  `${weight !== 400 ? ` font-weight="${weight}"` : ''}>${esc(s)}</text>`;

/** 위쪽 두 모서리만 둥근 막대. h가 반지름보다 작으면 사각으로 떨어진다. */
function bar(cx, base, h, fill, r = 4) {
  if (h <= 0) return '';
  const x = cx - BARW / 2, y = base - h, rr = Math.min(r, h);
  return `<path d="M${x} ${base} V${y + rr} Q${x} ${y} ${x + rr} ${y} H${x + BARW - rr} ` +
         `Q${x + BARW} ${y} ${x + BARW} ${y + rr} V${base} Z" fill="${fill}"/>`;
}

/** 패널 하나 — 제목·부제는 왼쪽 정렬, 눈금 대신 막대 위 직접 라벨. 문항 ID 행은 없다. */
function panel(top, title, subtitle, draw) {
  const base = top + PLOT_H;
  push(text(PAD.left, top - 108, title, { size: 25, weight: 700, anchor: 'start' }));
  push(text(PAD.left, top - 78, subtitle, { size: 17, fill: C.mid, anchor: 'start' }));
  push(`<line x1="${PAD.left}" y1="${base}" x2="${W - PAD.right}" y2="${base}" stroke="${C.rule}" stroke-width="1.5"/>`);
  ITEMS.forEach((item, i) => {
    const cx = PAD.left + STEP * i + STEP / 2;
    SERIES.forEach((se, k) => draw(item, se, k, cx + (k - 1) * PITCH, base));
    push(text(cx, base + 36, item.name[0], { size: 19, weight: 600 }));
    push(text(cx, base + 60, item.name[1], { size: 19, weight: 600 }));
  });
}

push(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="${FONT}">`);
push(`<rect width="${W}" height="${H}" fill="${C.bg}"/>`);

// 범례 — 오른쪽 위 한 번. 회색 오버헤드는 (b)에만 나오지만 항목은 여기 같이 둔다.
{
  const entries = [...SERIES.map((s) => [s.label, s.color]), ['CLI 오버헤드', C.gray]];
  const widths = entries.map(([label]) => 26 + label.length * 15 + 34);
  let x = W - PAD.right - widths.reduce((a, b) => a + b, 0);
  const y = A_TOP - 108;
  entries.forEach(([label, color], i) => {
    push(`<rect x="${x}" y="${y - 15}" width="18" height="18" rx="4" fill="${color}"/>`);
    push(text(x + 26, y, label, { size: 16, fill: C.mid, anchor: 'start' }));
    x += widths[i];
  });
}

// (a) 총점 — 값은 막대마다 직접 붙는다. 0점은 막대 없이 숫자만.
const scoreScale = (v) => (v / 100) * PLOT_H;
panel(A_TOP,
  '(a) 회차 2·3·5 총점 - 문항별 실행 평균',
  '장애마다 같은 자(자연어 질문 · 앵커 v2 · 루브릭 v3)로 세 회차를 채점한 총점 (100점 만점, 실행 평균)',
  (item, se, k, cx, base) => {
    const h = scoreScale(num(item.s[k]));
    push(bar(cx, base, h, se.color));
    push(text(cx, base - h - 12, item.s[k], { size: 19, weight: 700 }));
  });

// (b) 입력 토큰 — 회색 오버헤드 위에 컨텍스트를 쌓는다. 숫자는 컨텍스트만.
const TOK_MAX = 315;                    // 최대 250 + 오버헤드 59
const tokScale = (v) => (v / TOK_MAX) * PLOT_H;
panel(B_TOP,
  '(b) 회차 2·3·5 입력 토큰 - 문항별 실행 평균',
  '조사 1회에 LLM으로 들어간 입력량. 색은 실제 관측 데이터, 회색은 어느 조사에나 붙는 CLI 고정비(회차 2·3 약 48 · 회차 5 약 59) · 숫자는 색 부분만 (단위 1,000 토큰)',
  (item, se, k, cx, base) => {
    if (item.t[k] == null) {
      push(text(cx, base - 10, '-', { size: 30, fill: C.mid, weight: 700 }));
      return;
    }
    const ho = tokScale(se.overhead), hc = tokScale(num(item.t[k]));
    push(bar(cx, base, ho, C.gray, 0));
    push(bar(cx, base - ho - 2, hc, se.color));
    push(text(cx, base - ho - 2 - hc - 12, item.t[k], { size: 19, weight: 700 }));
  });
// 회색은 대비가 낮아 보이는 라벨이 필요하다. 값이 시리즈별 상수라 첫 그룹 띠 안에 한 번만 적는다.
SERIES.forEach((se, k) => {
  const cx = PAD.left + STEP / 2 + (k - 1) * PITCH;
  const y = B_TOP + PLOT_H - tokScale(se.overhead) / 2 + 5;
  push(text(cx, y, se.overhead, { size: 14, fill: C.mid }));
});

push(text(PAD.left, H - 56,
  '회차 2→3→5는 같은 자로 채점됐으나 도구 세대·조사 창·주입이 회차마다 달라 문항별 델타의 귀속은 회차 문서를 따른다 · § 개정 앵커 적용값 · † 분석 단계만 기재',
  { size: 16, fill: C.faint, anchor: 'start' }));
push(text(PAD.left, H - 30,
  'AU-3 회차 2는 조사 미실행(0점 · 토큰 측정 불가) · 회차 5 CH-1 토큰은 실행 2뿐 · AU-2 회차 2는 편차 ±11로 §8.1 인용 보류 · 토큰 절감 인용은 AP-1 −60.5% · AP-2 −61.9% · AP-3 −49.7% · AU-4 −48.5% · AU-2 −39.0%(분석)만',
  { size: 16, fill: C.faint, anchor: 'start' }));
push('</svg>');

const dest = path.join(__dirname, 'round-2-3-5-score-tokens.svg');
fs.writeFileSync(dest, out.join(''), 'utf8');
console.log('wrote', dest);
