const fs = require('fs');
const path = require('path');
const HTMLtoDOCX = require('html-to-docx');
const JSZip = require('jszip');

const REPO = path.resolve(__dirname, '../..');
const SCRATCH = __dirname;

const b64 = (p) => 'data:image/png;base64,' + fs.readFileSync(p).toString('base64');
const img = (p, w) => `<img src="${b64(p)}" width="${w}" />`;

let body = fs.readFileSync(path.join(SCRATCH, 'portfolio-v4.html'), 'utf8')
  .replace('{{ARCH_IMG}}', img(path.join(REPO, 'docs/monitoring_v15_preview.png'), 520))
  .replace('{{SCORE_IMG}}', img(path.join(SCRATCH, 'score-tokens-round1.png'), 300));

// html-to-docx는 <style> 블록(선택자 CSS)을 통째로 무시한다. 인라인 style 속성만 먹으므로
// 본문 크기는 아래 fontSize 옵션, 제목·문단 간격은 styles.xml 패치로 잡는다.
// v4는 표를 전부 산문으로 풀어서 표 셀 글자 패치가 필요 없다.
const html = `<!DOCTYPE html><html><head><meta charset="UTF-8" /></head><body>${body}</body></html>`;

const IMG_W_PT = [500, 330];      // [그림 1] 관측 파이프라인 v15 · [그림 2] 점수·토큰 (pt)

const footer = '<p style="text-align:center;font-size:8pt;color:#888888;">RCA-Agent · 2026-08</p>';

// margins는 반드시 7개 키를 모두 넘긴다. 일부만 주면 sectPr에 빈 값이 들어가
// Word가 파일을 아예 열지 못한다 (html-to-docx 1.8.0에서 실측 확인).
HTMLtoDOCX(html, null, {
  orientation: 'portrait',
  margins: { top: 850, right: 850, bottom: 850, left: 850, header: 300, footer: 300, gutter: 0 },
  title: 'RCA-Agent — Observability 기반 장애 원인 분석 에이전트',
  footer: true,
  pageNumber: true,
  font: 'Batang',
  fontSize: 20, // 10pt — 논문 본문 크기
  lineHeight: 1.0,

}, footer).then(async (buf) => {
  const zip = await JSZip.loadAsync(buf);

  // ① styles.xml — 논문 조판.
  //    라이브러리 기본 rFonts는 eastAsiaTheme=minorHAnsi라 한글이 라틴 테마 폰트로 대체된다
  //    (v4 초판의 "글자간 이상"의 원인). eastAsia까지 바탕으로 못박는다.
  //    본문: 바탕 10pt · 양쪽 정렬 · 줄간 1.4행. 제목: 맑은 고딕 볼드 (명조 본문 + 고딕 제목).
  const HEAD_FONT = '<w:rFonts w:ascii="Malgun Gothic" w:eastAsia="Malgun Gothic" w:hAnsi="Malgun Gothic"/>';
  const styles = (await zip.file('word/styles.xml').async('string'))
    .replace(/<w:rFonts w:ascii="Batang"[^/]*\/>/,
             '<w:rFonts w:ascii="Batang" w:eastAsia="Batang" w:hAnsi="Batang" w:cs="Batang"/>')
    .replace('w:after="120" w:line="240" w:lineRule="atLeast"',
             'w:after="80" w:line="336" w:lineRule="auto"/><w:jc w:val="both"')
    .replace('<w:spacing w:before="480"/>', '<w:spacing w:before="320" w:after="120"/>')
    .replace('<w:spacing w:before="360" w:after="80"/>', '<w:spacing w:before="240" w:after="100"/>')
    .replace(/<w:b\/>(\s*)<w:sz w:val="48"\/>/, `${HEAD_FONT}<w:b/>$1<w:sz w:val="24"/>`)  // h1 12pt
    .replace('<w:szCs w:val="48"/>', '<w:szCs w:val="24"/>')
    .replace(/<w:b\/>(\s*)<w:sz w:val="36"\/>/, `${HEAD_FONT}<w:b/>$1<w:sz w:val="22"/>`)  // h2 11pt
    .replace('<w:szCs w:val="36"/>', '<w:szCs w:val="22"/>');
  zip.file('word/styles.xml', styles);

  // ② 그림 크기 — <img width>는 무시되고 본문 폭(542pt)으로 늘어난다.
  //    drawing의 EMU 값을 직접 눌러야 한다 (1pt = 12700 EMU). 순서: 그림 1, 그림 2.
  let doc = await zip.file('word/document.xml').async('string');
  [...doc.matchAll(/<wp:extent cx="(\d+)" cy="(\d+)"/g)]
    .map((m) => [m[1], m[2]])
    .forEach(([cx, cy], i) => {
      const targetCx = Math.round(IMG_W_PT[i] * 12700);
      const targetCy = Math.round((Number(cy) * targetCx) / Number(cx));
      doc = doc.split(`cx="${cx}" cy="${cy}"`).join(`cx="${targetCx}" cy="${targetCy}"`);
    });
  zip.file('word/document.xml', doc);

  const out = process.argv[2] || path.join(REPO, 'docs/RCA-Agent-포트폴리오-v4.docx');
  const final = await zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' });
  fs.writeFileSync(out, final);
  console.log('OK', out, (final.length / 1024).toFixed(0) + 'KB');
});
