/**
 * T2-B 총정리용 차트 3장 생성기 — docs/charts/make-round235-chart.js와 같은 판형.
 * (배경·색·글꼴·직접 값 라벨·좌측 정렬 제목·우상단 범례·하단 각주)
 *
 *   node autoscaling/charts/make-t2b-charts.js
 *
 * 출력: t2b-journey.svg · t2b-spike.svg · t2b-model.svg (같은 이름 .png는 변환 산출물)
 * 데이터 출처: t2b-총정리.md · cpu총개선.md · 라우팅개선.md · 부하모델.md ·
 *             loadtest/results/04-콘텐츠폭주 · 14-현실모드 (전부 실측, 가정치 없음)
 */
const fs = require('fs');
const path = require('path');

const C = {
  bg: '#fcfcfb', gray: '#c9c7c1',
  blue: '#2a78d6', orange: '#eb6834', green: '#1baf7a',
  ink: '#0b0b0b', mid: '#52514e', faint: '#8f8d88', rule: '#e2e0dc',
};
const FONT = "'Malgun Gothic','맑은 고딕','Apple SD Gothic Neo',sans-serif";

function svgDoc(W, H, body) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="${FONT}">` +
    `<rect width="${W}" height="${H}" fill="${C.bg}"/>` + body + '</svg>';
}
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');
const text = (x, y, s, { size = 17, fill = C.ink, anchor = 'middle', weight = 400 } = {}) =>
  `<text x="${x}" y="${y}" font-size="${size}" fill="${fill}" text-anchor="${anchor}"` +
  `${weight !== 400 ? ` font-weight="${weight}"` : ''}>${esc(s)}</text>`;
function bar(cx, base, h, w, fill, r = 4) {
  if (h <= 0) return '';
  const x = cx - w / 2, y = base - h, rr = Math.min(r, h);
  return `<path d="M${x} ${base} V${y + rr} Q${x} ${y} ${x + rr} ${y} H${x + w - rr} ` +
         `Q${x + w} ${y} ${x + w} ${y + rr} V${base} Z" fill="${fill}"/>`;
}
function legend(push, W, right, y, entries) {
  const widths = entries.map(([label]) => 26 + label.length * 15 + 34);
  let x = W - right - widths.reduce((a, b) => a + b, 0);
  entries.forEach(([label, color], i) => {
    push(`<rect x="${x}" y="${y - 15}" width="18" height="18" rx="4" fill="${color}"/>`);
    push(text(x + 26, y, label, { size: 16, fill: C.mid, anchor: 'start' }));
    x += widths[i];
  });
}
function panelHead(push, left, top, title, subtitle) {
  push(text(left, top - 108, title, { size: 25, weight: 700, anchor: 'start' }));
  push(text(left, top - 78, subtitle, { size: 17, fill: C.mid, anchor: 'start' }));
}
function baseline(push, left, right, base) {
  push(`<line x1="${left}" y1="${base}" x2="${right}" y2="${base}" stroke="${C.rule}" stroke-width="1.5"/>`);
}

function write(name, svg) {
  const dest = path.join(__dirname, name);
  fs.writeFileSync(dest, svg, 'utf8');
  console.log('wrote', dest);
}

/* ------------------------------------------------------------------ */
/* 1) t2b-journey — (a) 처리량 여정 11단계, (b) 요청당 CPU 단가        */
/* ------------------------------------------------------------------ */
{
  const W = 2200, H = 1280, PAD = { left: 76, right: 64 };
  const PLOT_W = W - PAD.left - PAD.right, PLOT_H = 330;
  const A_TOP = 196, B_TOP = 784;
  const out = []; const push = (s) => out.push(s);

  // 시간 순서 그대로. layer: app(주황)·infra(파랑)·ctrl(초록)·gray(기준선·반증·등가)
  const STEPS = [
    { name: ['초기', 'limit 1.5'],            v: 176.7, c: C.gray },
    { name: ['limit 1.7 상향', '(반증 −16%)'], v: 148.8, c: C.gray },
    { name: ['CPU limit', '제거'],             v: 180.8, c: C.green },
    { name: ['Xmx 768→512', '(메모리 무죄)'],  v: 181.6, c: C.gray },
    { name: ['readOnly', '조회수 분리'],       v: 195.6, c: C.orange },
    { name: ['t3.micro 축소', '(반증 −20%)'],  v: 192.6, c: C.gray },
    { name: ['spot 전용', '노드 분리'],        v: 241.2, c: C.blue },
    { name: ['replica 3', '풀 20→12'],         v: 314.3, c: C.blue },
    { name: ['핫리스트 캐시', '(등가→단서)'],  v: 305.1, c: C.gray },
    { name: ['톰캣 스레드', '40→200'],         v: 337.0, c: C.green },
    { name: ['쿼리', '다이어트'],              v: 398.5, c: C.orange },
  ];
  const STEP = PLOT_W / STEPS.length, BARW = 96;
  const MAX_A = 420;
  const scaleA = (v) => (v / MAX_A) * PLOT_H;

  legend(push, W, PAD.right, A_TOP - 108, [
    ['앱 코드', C.orange], ['자원·인프라', C.blue], ['제어·설정', C.green], ['기준선·반증·등가', C.gray],
  ]);

  panelHead(push, PAD.left, A_TOP,
    '(a) T2-B 처리량 개선 여정 - 병목을 옮겨가며 176.7 → 398.5 rps (+126%)',
    '같은 부하(500 VU 콘텐츠 읽기 폭주)를 구성만 바꿔가며 반복 측정한 서버 처리량. 회색은 기준선과 반증·등가로 끝난 실험 - 반증이 다음 병목의 좌표가 됐다 (단위 rps)');
  baseline(push, PAD.left, W - PAD.right, A_TOP + PLOT_H);
  STEPS.forEach((s, i) => {
    const cx = PAD.left + STEP * i + STEP / 2, base = A_TOP + PLOT_H;
    push(bar(cx, base, scaleA(s.v), BARW, s.c));
    push(text(cx, base - scaleA(s.v) - 12, s.v.toFixed(1), { size: 19, weight: 700 }));
    push(text(cx, base + 36, s.name[0], { size: 19, weight: 600 }));
    push(text(cx, base + 60, s.name[1], { size: 19, weight: 600 }));
  });

  // (b) 요청당 CPU 단가 — 4포인트, 폭을 절반만 쓴다 (희소해 보이지 않게)
  const CPU = [
    { name: ['초기'],           v: 20.9 },
    { name: ['readOnly 후'],    v: 18.6 },
    { name: ['다이어트 직전'],  v: 16.0 },
    { name: ['다이어트 후'],    v: 12.6 },
  ];
  const B_W = 1000, B_STEP = B_W / CPU.length, B_BARW = 110;
  const MAX_B = 24;
  const scaleB = (v) => (v / MAX_B) * PLOT_H;
  panelHead(push, PAD.left, B_TOP,
    '(b) 요청당 CPU 단가 - 20.9 → 12.6ms (−40%)',
    '요청 1건 처리에 쓰인 CPU 시간. 증설 전에 단가부터 줄인 축 - 다이어트 후 사상 처음 CPU 미포화(노드 71~96%)가 됐다 (단위 ms/요청)');
  baseline(push, PAD.left, PAD.left + B_W, B_TOP + PLOT_H);
  CPU.forEach((s, i) => {
    const cx = PAD.left + B_STEP * i + B_STEP / 2, base = B_TOP + PLOT_H;
    push(bar(cx, base, scaleB(s.v), B_BARW, C.orange));
    push(text(cx, base - scaleB(s.v) - 12, s.v.toFixed(1), { size: 19, weight: 700 }));
    push(text(cx, base + 36, s.name[0], { size: 19, weight: 600 }));
  });
  // (b) 오른쪽 여백에 핵심 문장
  push(text(PAD.left + B_W + 80, B_TOP + 130, '단가 −40% = 같은 하드웨어에서', { size: 21, fill: C.mid, anchor: 'start' }));
  push(text(PAD.left + B_W + 80, B_TOP + 162, '1.7배를 처리할 수 있는 여력', { size: 21, fill: C.mid, anchor: 'start' }));
  push(text(PAD.left + B_W + 80, B_TOP + 194, '(readOnly·중복 쿼리 제거·MGET 배치·캐시)', { size: 17, fill: C.faint, anchor: 'start' }));

  push(text(PAD.left, H - 56,
    '전 구간 T2-B 500 VU 폐루프 · CloudFront 경유 축(회차 1~11) 실측 - 직결 축 회차(2026-08-30)와 절대값 직접 비교 불가 · 반증 막대는 해당 실험 직후 원복',
    { size: 16, fill: C.faint, anchor: 'start' }));
  push(text(PAD.left, H - 30,
    '캐시 등가(305.1)는 실패가 아니라 "DB가 병목이 아니다"의 증거가 되어 톰캣 40 발견으로 이어짐 · 상세: t2b-총정리.md §2 · cpu총개선.md',
    { size: 16, fill: C.faint, anchor: 'start' }));
  write('t2b-journey.svg', svgDoc(W, H, out.join('')));
}

/* ------------------------------------------------------------------ */
/* 2) t2b-spike — (a) 스파이크 실패 601→0, (b) 파드별 유입 편차        */
/* ------------------------------------------------------------------ */
{
  const W = 1400, H = 1280, PAD = { left: 76, right: 64 };
  const PLOT_H = 330, A_TOP = 196, B_TOP = 784;
  const out = []; const push = (s) => out.push(s);

  legend(push, W, PAD.right, A_TOP - 108, [
    ['round_robin (개선 전)', C.gray], ['ewma (개선 후)', C.green],
  ]);

  // (a) 실패 건수
  const FAIL = [
    { name: ['round_robin', '재실행 11'], v: 601, pct: '실패율 0.46%', c: C.gray },
    { name: ['ewma', '재검증 (08-30)'],   v: 0,   pct: '실패율 0.00%', c: C.green },
  ];
  const A_W = 760, A_STEP = A_W / FAIL.length, A_BARW = 150;
  const scaleA = (v) => (v / 650) * PLOT_H;
  panelHead(push, PAD.left, A_TOP,
    '(a) 스파이크(400rps급 · 500 VU) 실패 - 601건 → 0건',
    '실패 601건은 전량이 한 파드의 5초 버스트 2회에 집중(풀 10초 대기 후 500). round_robin은 멈칫한 파드에도 같은 몫을 계속 보낸다 - ewma 설정 1줄(비용 0)로 해소');
  baseline(push, PAD.left, PAD.left + A_W, A_TOP + PLOT_H);
  FAIL.forEach((s, i) => {
    const cx = PAD.left + A_STEP * i + A_STEP / 2, base = A_TOP + PLOT_H;
    if (s.v === 0) {
      push(text(cx, base - 14, '0', { size: 34, weight: 700, fill: C.green }));
    } else {
      push(bar(cx, base, scaleA(s.v), A_BARW, s.c));
      push(text(cx, base - scaleA(s.v) - 12, String(s.v), { size: 22, weight: 700 }));
    }
    push(text(cx, base + 36, s.name[0], { size: 19, weight: 600 }));
    push(text(cx, base + 60, s.name[1], { size: 19, weight: 600 }));
    push(text(cx, base + 86, s.pct, { size: 16, fill: C.mid }));
  });
  push(text(PAD.left + A_W + 80, A_TOP + 120, '요청 수 기준 등급', { size: 17, fill: C.faint, anchor: 'start' }));
  push(text(PAD.left + A_W + 80, A_TOP + 150, '129,536건 중 601 → 116,981건 중 0', { size: 19, fill: C.mid, anchor: 'start' }));

  // (b) 파드별 유입 편차 — 회차가 달라 파드 1:1 대응은 없다. 몫 크기 순 정렬 비교.
  const SHARE = [
    { name: ['최대 몫'],  rr: 37.3, ew: 36.5 },
    { name: ['중간 몫'],  rr: 37.3, ew: 33.1 },
    { name: ['최소 몫'],  rr: 25.3, ew: 30.5 },
  ];
  const B_W = 760, B_STEP = B_W / SHARE.length, B_BARW = 92, PITCH = B_BARW + 2;
  const scaleB = (v) => (v / 42) * PLOT_H;
  panelHead(push, PAD.left, B_TOP,
    '(b) 파드 3개의 유입 편차 - 12.0%p → 6.0%p',
    'round_robin은 커넥션 고정 탓에 한 파드가 굶는(25.3%) 동안 느린 파드도 같은 몫을 받았다. ewma는 지연 가중 분배 - 실제로 전 회차 버스트 파드가 최소 몫(30.5%)을 받았다');
  baseline(push, PAD.left, PAD.left + B_W, B_TOP + PLOT_H);
  SHARE.forEach((s, i) => {
    const cx = PAD.left + B_STEP * i + B_STEP / 2, base = B_TOP + PLOT_H;
    push(bar(cx - PITCH / 2, base, scaleB(s.rr), B_BARW, C.gray));
    push(text(cx - PITCH / 2, base - scaleB(s.rr) - 12, s.rr.toFixed(1), { size: 19, weight: 700 }));
    push(bar(cx + PITCH / 2, base, scaleB(s.ew), B_BARW, C.green));
    push(text(cx + PITCH / 2, base - scaleB(s.ew) - 12, s.ew.toFixed(1), { size: 19, weight: 700 }));
    push(text(cx, base + 36, s.name[0], { size: 19, weight: 600 }));
  });
  push(text(PAD.left + B_W + 80, B_TOP + 120, '단위 % (전체 유입 대비)', { size: 17, fill: C.faint, anchor: 'start' }));
  push(text(PAD.left + B_W + 80, B_TOP + 150, '최대−최소 격차', { size: 17, fill: C.faint, anchor: 'start' }));
  push(text(PAD.left + B_W + 80, B_TOP + 182, '12.0%p → 6.0%p', { size: 22, weight: 700, anchor: 'start' }));

  push(text(PAD.left, H - 56,
    '(a) round_robin은 CF 경유·ewma는 nginx 직결 - 경로 혼입이 있어 N=1 단서로 기록(라우팅개선.md) · (b)는 서로 다른 회차라 파드 식별자 1:1 대응 없음, 몫 크기 순 정렬 비교',
    { size: 16, fill: C.faint, anchor: 'start' }));
  push(text(PAD.left, H - 30,
    'round_robin 분배는 300 VU 수용성 측정(146/146/99 rps) · ewma 분배는 2026-08-30 재검증의 인그레스 로그 전수(116,980건) 집계',
    { size: 16, fill: C.faint, anchor: 'start' }));
  write('t2b-spike.svg', svgDoc(W, H, out.join('')));
}

/* ------------------------------------------------------------------ */
/* 3) t2b-model — (a) 모델 예측 vs 실측, (b) 평시 vs 스파이크           */
/* ------------------------------------------------------------------ */
{
  const W = 1400, H = 1280, PAD = { left: 76, right: 64 };
  const PLOT_H = 330, A_TOP = 196, B_TOP = 784;
  const out = []; const push = (s) => out.push(s);

  legend(push, W, PAD.right, A_TOP - 108, [
    ['모델 예측', C.gray], ['실측', C.blue],
  ]);

  // (a) 예측 vs 실측 — 환산식 3회 적중
  const PRED = [
    { name: ['스파이크 압력', '300 VU'],          p: 400, m: 394.1 },
    { name: ['현실 모드 v1', '동접 1,250'],        p: 77,  m: 75.4 },
    { name: ['현실 모드 v2', '동접 1,250·분포 개정'], p: 83.1, m: 83.0 },
  ];
  const A_W = 900, A_STEP = A_W / PRED.length, A_BARW = 100, PITCH = A_BARW + 2;
  const scaleA = (v) => (v / 430) * PLOT_H;
  panelHead(push, PAD.left, A_TOP,
    '(a) 부하 모델 예측 vs 실측 - 환산식 3회 연속 적중',
    'DAU→동접→think time→rps 사슬(부하모델.md)로 계산한 예측치와 k6 실측 처리량. 가정으로 세운 모델이 실측과 일치해 사후 모델로 성립한다 (단위 rps)');
  baseline(push, PAD.left, PAD.left + A_W, A_TOP + PLOT_H);
  PRED.forEach((s, i) => {
    const cx = PAD.left + A_STEP * i + A_STEP / 2, base = A_TOP + PLOT_H;
    push(bar(cx - PITCH / 2, base, scaleA(s.p), A_BARW, C.gray));
    push(text(cx - PITCH / 2, base - scaleA(s.p) - 12, String(s.p), { size: 19, weight: 700 }));
    push(bar(cx + PITCH / 2, base, scaleA(s.m), A_BARW, C.blue));
    push(text(cx + PITCH / 2, base - scaleA(s.m) - 12, String(s.m), { size: 19, weight: 700 }));
    push(text(cx, base + 36, s.name[0], { size: 19, weight: 600 }));
    push(text(cx, base + 60, s.name[1], { size: 19, weight: 600 }));
  });

  // (b) 평시 vs 스파이크 — 같은 서버의 두 얼굴
  const LAT = [
    { name: ['평시 med'],     v: 41,   c: C.green, label: '41ms' },
    { name: ['평시 p95'],     v: 69,   c: C.green, label: '69ms' },
    { name: ['스파이크 med'], v: 622,  c: C.orange, label: '622ms' },
    { name: ['스파이크 p95'], v: 3040, c: C.orange, label: '3,040ms' },
  ];
  const B_W = 900, B_STEP = B_W / LAT.length, B_BARW = 130;
  const scaleB = (v) => (v / 3300) * PLOT_H;
  panelHead(push, PAD.left, B_TOP,
    '(b) 같은 서버의 두 얼굴 - 평시 42ms vs 스파이크 622ms',
    '평시(동접 1,250 · 83rps)는 SLO p99<800ms를 여유 있게 통과(2회 재현), 스파이크(400rps급)는 초 단위 - 이 서비스의 위험은 평시가 아니라 푸시 직후 몇 분이다');
  baseline(push, PAD.left, PAD.left + B_W, B_TOP + PLOT_H);
  LAT.forEach((s, i) => {
    const cx = PAD.left + B_STEP * i + B_STEP / 2, base = B_TOP + PLOT_H;
    const h = Math.max(scaleB(s.v), 3);
    push(bar(cx, base, h, B_BARW, s.c));
    push(text(cx, base - h - 12, s.label, { size: 19, weight: 700 }));
    push(text(cx, base + 36, s.name[0], { size: 19, weight: 600 }));
  });
  push(text(PAD.left + B_W + 60, B_TOP + 120, '격차 ~15배 =', { size: 19, fill: C.mid, anchor: 'start' }));
  push(text(PAD.left + B_W + 60, B_TOP + 152, '개선의 과녁이', { size: 19, fill: C.mid, anchor: 'start' }));
  push(text(PAD.left + B_W + 60, B_TOP + 184, '스파이크인 이유', { size: 19, fill: C.mid, anchor: 'start' }));

  push(text(PAD.left, H - 56,
    '(a) 300 VU=394.1은 수용성 측정(CF 경유) · 현실 모드 v1·v2는 2026-08-30 직결, think time 분포 v1(평균 15.5s)·v2(평균 14.9s) - 유지 구간 3~6분 집계',
    { size: 16, fill: C.faint, anchor: 'start' }));
  push(text(PAD.left, H - 30,
    '(b) 평시 = 현실 모드 v2 실측(med 41.3ms·p95 68.8ms) · 스파이크 = 500 VU 직결 ewma 회차(med 622ms·p95 3.04s) · p99 SLO 조건부 수용점은 개방 루프로 확정 예정',
    { size: 16, fill: C.faint, anchor: 'start' }));
  write('t2b-model.svg', svgDoc(W, H, out.join('')));
}

/* ------------------------------------------------------------------ */
/* 4) t2b-summary — 한눈 요약: (a) 목표선 하나에 수렴하는 여정,        */
/*    (b) 개선 전 vs 후 핵심 4지표. "뭘 봐야 하는지"를 차트가 말한다.  */
/* ------------------------------------------------------------------ */
{
  const W = 2000, H = 1280, PAD = { left: 76, right: 76 };
  const PLOT_H = 330, A_TOP = 196, B_TOP = 784;
  const RED = '#d43f3f';
  const out = []; const push = (s) => out.push(s);

  legend(push, W, PAD.right, A_TOP - 108, [
    ['개선 전·기준', C.gray], ['앱 코드', C.orange], ['자원·인프라', C.blue], ['제어·설정', C.green],
  ]);

  // (a) 목표 400 rps 한 줄을 향해 올라가는 6단계 (반증 실험은 상세 차트로 분리)
  const STEPS = [
    { name: ['초기'],                 v: 176.7, c: C.gray },
    { name: ['CPU limit 제거'],       v: 180.8, c: C.green },
    { name: ['readOnly 전환'],        v: 195.6, c: C.orange },
    { name: ['spot 노드 분리'],       v: 241.2, c: C.blue },
    { name: ['replica 3'],            v: 314.3, c: C.blue },
    { name: ['톰캣 40→200'],          v: 337.0, c: C.green },
    { name: ['쿼리 다이어트'],        v: 398.5, c: C.orange },
  ];
  const A_W = W - PAD.left - PAD.right, A_STEP = A_W / STEPS.length, A_BARW = 120;
  const MAX_A = 460;
  const scaleA = (v) => (v / MAX_A) * PLOT_H;
  const baseY = A_TOP + PLOT_H;
  const goalY = baseY - scaleA(400);

  panelHead(push, PAD.left, A_TOP,
    '(a) 기준은 하나 - 목표 400 rps (푸시 스파이크 부하)에 도달하기까지',
    '점선이 부하 모델이 정한 목표(스파이크 400 rps). 초기엔 목표의 44%였고, 병목을 하나씩 제거할 때마다 막대가 점선에 다가간다 (T2-B 500 VU 실측, 단위 rps)');
  baseline(push, PAD.left, W - PAD.right, baseY);
  // 목표 점선 — 이 차트의 유일한 기준
  push(`<line x1="${PAD.left}" y1="${goalY}" x2="${W - PAD.right}" y2="${goalY}" stroke="${RED}" stroke-width="2.5" stroke-dasharray="10 7"/>`);
  push(text(W - PAD.right, goalY - 12, '목표 400 rps', { size: 19, weight: 700, fill: RED, anchor: 'end' }));
  STEPS.forEach((s, i) => {
    const cx = PAD.left + A_STEP * i + A_STEP / 2;
    push(bar(cx, baseY, scaleA(s.v), A_BARW, s.c));
    push(text(cx, baseY - scaleA(s.v) - 12, s.v.toFixed(1), { size: 20, weight: 700 }));
    push(text(cx, baseY + 36, s.name[0], { size: 19, weight: 600 }));
    const pct = Math.round((s.v / 400) * 100);
    push(text(cx, baseY + 62, `목표의 ${pct}%`, { size: 16, fill: i === STEPS.length - 1 ? C.ink : C.faint, weight: i === STEPS.length - 1 ? 700 : 400 }));
  });

  // (b) 개선 전 vs 후 — 지표 4개, 회색(전) → 초록(후), 배지가 개선 폭
  const METRICS = [
    { name: ['처리량', 'rps'],            before: 176.7, after: 398.5, bl: '176.7', al: '398.5', delta: '+126%', lowerBetter: false },
    { name: ['요청당 CPU', 'ms'],         before: 20.9,  after: 12.6,  bl: '20.9',  al: '12.6',  delta: '−40%',  lowerBetter: true },
    { name: ['스파이크 실패', '건'],      before: 601,   after: 0,     bl: '601',   al: '0',     delta: '−100%', lowerBetter: true },
    { name: ['파드 유입 격차', '%p'],     before: 12.0,  after: 6.0,   bl: '12.0',  al: '6.0',   delta: '−50%',  lowerBetter: true },
  ];
  const B_W = W - PAD.left - PAD.right, B_STEP = B_W / METRICS.length, B_BARW = 110, PITCH = B_BARW + 4;
  const bBase = B_TOP + PLOT_H;
  panelHead(push, PAD.left, B_TOP,
    '(b) 개선 전 vs 후 - 핵심 4지표',
    '회색이 개선 전, 초록이 개선 후. 배지가 개선 폭이다 - 처리량은 클수록, 나머지 셋은 작을수록 좋다 (지표마다 축은 제각각, 값은 막대 위 숫자)');
  baseline(push, PAD.left, W - PAD.right, bBase);
  METRICS.forEach((m, i) => {
    const cx = PAD.left + B_STEP * i + B_STEP / 2;
    const pairMax = Math.max(m.before, m.after) * 1.3;
    const hB = (m.before / pairMax) * PLOT_H, hA = (m.after / pairMax) * PLOT_H;
    push(bar(cx - PITCH / 2, bBase, hB, B_BARW, C.gray));
    push(text(cx - PITCH / 2, bBase - hB - 12, m.bl, { size: 20, weight: 700 }));
    if (m.after === 0) {
      push(text(cx + PITCH / 2, bBase - 14, '0', { size: 32, weight: 700, fill: C.green }));
    } else {
      push(bar(cx + PITCH / 2, bBase, hA, B_BARW, C.green));
      push(text(cx + PITCH / 2, bBase - hA - 12, m.al, { size: 20, weight: 700 }));
    }
    // 개선 폭 배지 — 그룹 위 중앙
    const badgeY = B_TOP - 26;
    const bw = m.delta.length * 16 + 28;
    push(`<rect x="${cx - bw / 2}" y="${badgeY - 24}" width="${bw}" height="34" rx="17" fill="${C.green}"/>`);
    push(text(cx, badgeY, m.delta, { size: 19, weight: 700, fill: '#ffffff' }));
    push(text(cx, bBase + 36, m.name[0], { size: 20, weight: 600 }));
    push(text(cx, bBase + 62, `(${m.name[1]})`, { size: 16, fill: C.faint }));
  });

  push(text(PAD.left, H - 56,
    '(a)는 반증·등가로 끝난 실험 3건(limit 상향 −16% · micro −20% · 캐시 등가)을 뺀 개선 단계만 - 전체 여정과 반증 기록은 t2b-journey 차트 · 회차 1~11 CF 경유 축 실측',
    { size: 16, fill: C.faint, anchor: 'start' }));
  push(text(PAD.left, H - 30,
    '(b) 처리량·CPU 단가는 500 VU 회차 실측 · 스파이크 실패는 재실행 11(601) → ewma 재검증(0, 직결·N=1) · 파드 격차는 최대−최소 유입 비중 · 상세: t2b-총정리.md',
    { size: 16, fill: C.faint, anchor: 'start' }));
  write('t2b-summary.svg', svgDoc(W, H, out.join('')));
}
