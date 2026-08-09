const fs = require('fs');
const path = require('path');
const HTMLtoDOCX = require('html-to-docx');
const JSZip = require('jszip');

const REPO = path.resolve(__dirname, '../..');
const SCRATCH = '/private/tmp/claude-501/-Users-minchae-sources-yogurtte-rca-agent/7666e352-75b7-41f9-9602-88c4a4a72f5c/scratchpad';

const b64 = (p) => 'data:image/png;base64,' + fs.readFileSync(p).toString('base64');
const img = (p, w) => `<img src="${b64(p)}" width="${w}" />`;

let body = fs.readFileSync(path.join(__dirname, 'portfolio-v10.html'), 'utf8')
  .replace('{{ARCH_IMG}}', img(path.join(SCRATCH, 'v9-arch.png'), 460));

const html = `<!DOCTYPE html><html><head><meta charset="UTF-8" /></head><body>${body}</body></html>`;

const IMG_W_PT = [440];           // 관측 파이프라인 (pt)

const footer = '<p style="text-align:center;font-size:8pt;color:#9CA3AF;">RCA-Agent · 정민채 · 2026-08</p>';

// margins는 반드시 7개 키를 모두 넘긴다 (html-to-docx 1.8.0, 일부 누락 시 Word가 파일을 못 연다).
HTMLtoDOCX(html, null, {
  orientation: 'portrait',
  margins: { top: 880, right: 880, bottom: 880, left: 880, header: 300, footer: 300, gutter: 0 },
  title: 'RCA-Agent — 장애 원인 분석을 자동화한 Spring AI 기반 RCA 에이전트',
  footer: true,
  pageNumber: true,
  font: 'Malgun Gothic',
  fontSize: 20, // 10pt
  lineHeight: 1.0,
}, footer).then(async (buf) => {
  const zip = await JSZip.loadAsync(buf);

  // 포트폴리오 조판: 왼쪽 정렬(양쪽 정렬 없음) · 줄간 1.3행 · 문단 사이 여백 넉넉히.
  // 라이브러리 기본 rFonts는 eastAsiaTheme=minorHAnsi라 한글이 라틴 테마 폰트로 대체된다 — eastAsia까지 못박는다.
  const styles = (await zip.file('word/styles.xml').async('string'))
    .replace(/<w:rFonts w:ascii="Malgun Gothic"[^/]*\/>/,
             '<w:rFonts w:ascii="Malgun Gothic" w:eastAsia="Malgun Gothic" w:hAnsi="Malgun Gothic" w:cs="Malgun Gothic"/>')
    .replace('w:after="120" w:line="240" w:lineRule="atLeast"',
             'w:after="130" w:line="312" w:lineRule="auto"')
    .replace('<w:spacing w:before="480"/>', '<w:spacing w:before="400" w:after="160"/>')
    .replace('<w:spacing w:before="360" w:after="80"/>', '<w:spacing w:before="280" w:after="120"/>')
    .replace('<w:sz w:val="48"/>', '<w:sz w:val="27"/>')   // h1 13.5pt
    .replace('<w:szCs w:val="48"/>', '<w:szCs w:val="27"/>')
    .replace('<w:sz w:val="36"/>', '<w:sz w:val="23"/>')   // h2 11.5pt
    .replace('<w:szCs w:val="36"/>', '<w:szCs w:val="23"/>');
  zip.file('word/styles.xml', styles);

  // 그림 크기 — drawing의 EMU를 직접 누른다 (1pt = 12700 EMU).
  let doc = await zip.file('word/document.xml').async('string');
  [...doc.matchAll(/<wp:extent cx="(\d+)" cy="(\d+)"/g)]
    .map((m) => [m[1], m[2]])
    .forEach(([cx, cy], i) => {
      const targetCx = Math.round(IMG_W_PT[i] * 12700);
      const targetCy = Math.round((Number(cy) * targetCx) / Number(cx));
      doc = doc.split(`cx="${cx}" cy="${cy}"`).join(`cx="${targetCx}" cy="${targetCy}"`);
    });
  zip.file('word/document.xml', doc);

  const out = process.argv[2] || path.join(REPO, 'docs/RCA-Agent_포트폴리오_v10.docx');
  const final = await zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' });
  fs.writeFileSync(out, final);
  console.log('OK', out, (final.length / 1024).toFixed(0) + 'KB');
});
