const fs = require('fs');
const path = require('path');
const HTMLtoDOCX = require('html-to-docx');
const JSZip = require('jszip');

const REPO = 'C:/sources/yogurtte-rca-agent';
const SCRATCH = __dirname;

const b64 = (p) => 'data:image/png;base64,' + fs.readFileSync(p).toString('base64');
const img = (p, w) => `<img src="${b64(p)}" width="${w}" />`;

let body = fs.readFileSync(path.join(SCRATCH, 'portfolio.html'), 'utf8')
  .replace('{{ARCH_IMG}}', img(path.join(REPO, 'docs/architecture.png'), 360))
  .replace('{{SCORE_IMG}}', img(path.join(REPO, 'docs/round-2-score-tokens.png'), 260));

// html-to-docx는 <style> 블록(선택자 CSS)을 통째로 무시한다. 인라인 style 속성만 먹으므로
// 본문 크기는 아래 fontSize 옵션, 제목·문단 간격은 styles.xml 패치, 표 글자는
// document.xml 패치로 잡는다. 캡션·각주는 HTML에 인라인 style로 박아 뒀다.
const html = `<!DOCTYPE html><html><head><meta charset="UTF-8" /></head><body>${body}</body></html>`;

const TABLE_SZ = 18;              // 9pt — 본문 10.5pt 대비 한 단계 작게
const IMG_W_PT = [285, 330];      // [그림 1] 파이프라인 · [그림 2] 점수·토큰 (pt)

const footer = '<p style="text-align:center;font-size:8pt;color:#888888;">RCA-Agent · 2026-08</p>';

// margins는 반드시 7개 키를 모두 넘긴다. 일부만 주면 sectPr에 빈 값이 들어가
// Word가 파일을 아예 열지 못한다 (html-to-docx 1.8.0에서 실측 확인).
HTMLtoDOCX(html, null, {
  orientation: 'portrait',
  margins: { top: 620, right: 640, bottom: 620, left: 640, header: 300, footer: 300, gutter: 0 },
  title: 'RCA-Agent — Observability 기반 장애 원인 분석 에이전트',
  footer: true,
  pageNumber: true,
  font: 'Malgun Gothic',
  fontSize: 21, // 10.5pt
  lineHeight: 1.0,

}, footer).then(async (buf) => {
  const zip = await JSZip.loadAsync(buf);

  // ① styles.xml — 라이브러리 기본값이 페이지 수를 지배한다.
  //    문단마다 after=6pt, 줄간 atLeast 12pt, h1이 24pt에 앞 여백 24pt로 박혀 온다.
  const styles = (await zip.file('word/styles.xml').async('string'))
    .replace('w:after="120" w:line="240" w:lineRule="atLeast"',
             'w:after="40" w:line="218" w:lineRule="auto"')
    .replace('<w:spacing w:before="480"/>', '<w:spacing w:before="150" w:after="40"/>')
    .replace('<w:spacing w:before="360" w:after="80"/>', '<w:spacing w:before="150" w:after="40"/>')
    .replace('<w:sz w:val="48"/>', '<w:sz w:val="26"/>')   // h1 13pt
    .replace('<w:szCs w:val="48"/>', '<w:szCs w:val="26"/>')
    .replace('<w:sz w:val="36"/>', '<w:sz w:val="23"/>')   // h2 11.5pt
    .replace('<w:szCs w:val="36"/>', '<w:szCs w:val="23"/>');
  zip.file('word/styles.xml', styles);

  // ② document.xml — 표 안의 run에만 작은 글자를 넣는다.
  //    표는 인라인 style이 셀까지 안 내려가므로 <w:tbl> 구간을 직접 손본다.
  let doc = (await zip.file('word/document.xml').async('string'))
    .split(/(<w:tbl>[\s\S]*?<\/w:tbl>)/)
    .map((seg) => seg.startsWith('<w:tbl>')
      ? seg.replace(/<w:rPr\/>/g, `<w:rPr><w:sz w:val="${TABLE_SZ}"/><w:szCs w:val="${TABLE_SZ}"/></w:rPr>`)
           .replace(/<w:rPr>(?!<w:sz)/g, `<w:rPr><w:sz w:val="${TABLE_SZ}"/><w:szCs w:val="${TABLE_SZ}"/>`)
      : seg)
    .join('');

  // ③ 그림 크기 — <img width>는 무시되고 본문 폭(542pt)으로 늘어난다.
  //    drawing의 EMU 값을 직접 눌러야 한다 (1pt = 12700 EMU). 순서: 그림 1, 그림 2.
  [...doc.matchAll(/<wp:extent cx="(\d+)" cy="(\d+)"/g)]
    .map((m) => [m[1], m[2]])
    .forEach(([cx, cy], i) => {
      const targetCx = Math.round(IMG_W_PT[i] * 12700);
      const targetCy = Math.round((Number(cy) * targetCx) / Number(cx));
      doc = doc.split(`cx="${cx}" cy="${cy}"`).join(`cx="${targetCx}" cy="${targetCy}"`);
    });
  zip.file('word/document.xml', doc);

  const out = process.argv[2] || path.join(REPO, 'docs/RCA-Agent-포트폴리오.docx');
  const final = await zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' });
  fs.writeFileSync(out, final);
  console.log('OK', out, (final.length / 1024).toFixed(0) + 'KB');
});
