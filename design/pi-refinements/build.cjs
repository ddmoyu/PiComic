// Round 03: D-led pi refinements. SVG paths are original editable outlines.
const fs = require('node:fs/promises');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const sharp = require('sharp');
const { chromium } = require('playwright');
const ink = '#1D2230';
// Same lower strokes as round 02 D, from the right bar/leg junction to the left.
const legsD = 'H65l-4 26q-2 12 6 9 4-1 8-6l2 2q-6 11-15 11-12 0-9-15l5-27H44q-1 25-11 42h-9q13-19 15-42';
const entries = [
  { id: 'D1', slug: 'flat', name: '平切起笔', note: '去掉左钩，保留 D 的主体', d: `M31 28H77l-3 8${legsD}H31z` },
  { id: 'D2', slug: 'short', name: '短横起笔', note: '缩短左端，更紧凑干净', d: `M38 28H77l-3 8${legsD}H38z` },
  { id: 'D3', slug: 'round', name: '柔圆起笔', note: '左端圆收，保留粗细对比', d: `M32 28H77l-3 8${legsD}H32C26 36 26 28 32 28z` },
  { id: 'D4', slug: 'angled', name: '斜切起笔', note: '斜面切入，呼应双腿的斜势', d: `M34 28H77l-3 8${legsD}H29z` },
  { id: 'D5', slug: 'arc', name: '微弧起笔', note: '横画微微拱起，左端无下钩', d: `M30 30Q45 25 58 28T77 28l-3 8${legsD}Q34 36 30 38z` },
  { id: 'D6', slug: 'upturn', name: '短上挑起笔', note: '左端轻轻上扬，保留笔锋', d: `M30 25Q37 29 45 28H77l-3 8${legsD}Q34 36 30 33z` },
  { id: 'D7', slug: 'light', name: '轻横平切', note: '横画减细，整体更轻盈', d: `M31 30H77l-3 6${legsD}H31z` },
  { id: 'D8', slug: 'handwritten', name: '手写融合', note: 'D 的粗细层次，C 的舒展弯尾', d: 'M31 30Q44 27 56 30T79 29L77 35Q71 38 64 36L59 64Q57 75 65 70L78 60L80 63Q70 78 61 78Q49 78 52 63L58 36H44Q42 60 33 78H27Q37 59 39 36H30z' },
];

function svg(title, group, background = '') {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108" role="img" aria-labelledby="title"><title id="title">${title}</title>${background}${group}</svg>\n`;
}
function uri(source) { return `data:image/svg+xml;base64,${Buffer.from(source).toString('base64')}`; }

async function main() {
  await fs.mkdir(path.join(__dirname, 'android'), { recursive: true });
  const cards = [], report = [];
  for (const entry of entries) {
    // Keep precisely the same scale and position as D for fair comparisons.
    const group = `<g id="foreground" fill="${ink}" transform="translate(54 54) scale(0.82402) translate(-54 -54)"><path id="pi" d="${entry.d}"/></g>`;
    const source = svg(`PiComic · ${entry.id} ${entry.name}`, group);
    const icon = svg(`PiComic · ${entry.id} ${entry.name} · Android 母版`, group.replaceAll(ink, '#FFFFFF'), '<g id="background"><path fill="#4560E8" d="M0 0h108v108H0z"/></g>');
    const zoom = source.replace('width="108" height="108" viewBox="0 0 108 108"', 'width="72" height="54" viewBox="29 27 24 18"');
    const file = `${entry.id.toLowerCase()}-${entry.slug}.svg`;
    await fs.writeFile(path.join(__dirname, file), source);
    await fs.writeFile(path.join(__dirname, 'android', file), icon);
    const { data, info } = await sharp(Buffer.from(source.replace('width="108" height="108"', 'width="1080" height="1080"'))).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
    let radius = 0;
    for (let y = 0; y < info.height; y++) for (let x = 0; x < info.width; x++) {
      if (data[(y * info.width + x) * info.channels + info.channels - 1] > 32) radius = Math.max(radius, Math.hypot((x + .5) / 10 - 54, (y + .5) / 10 - 54));
    }
    if (radius > 33) throw new Error(`${entry.id}: outside safe circle`);
    report.push({ id: entry.id, radius: +radius.toFixed(2) });
    cards.push(`<article class="card"><div class="top"><span class="number">${entry.id}</span><a href="${file}" download aria-label="下载 ${entry.id} 透明字形 SVG">SVG ↗</a></div><div class="glyph"><img src="${uri(source)}" alt="${entry.id} ${entry.name} 圆周率字形"></div><h2>${entry.name}</h2><p class="note">${entry.note}</p><div class="details"><div class="detail-label">左端放大</div><div class="zoom"><img src="${uri(zoom)}" alt="${entry.id} 横画左端放大"></div><a class="icon" href="android/${file}" download aria-label="下载 ${entry.id} Android 图标 SVG"><img src="${uri(icon)}" alt="${entry.id} 48 像素图标"></a></div></article>`);
  }
  const referenceC = await fs.readFile(path.join(__dirname, '..', 'pi-letterforms', 'c-handwritten.svg'), 'utf8');
  const referenceD = await fs.readFile(path.join(__dirname, '..', 'pi-letterforms', 'd-serif.svg'), 'utf8');
  // Embed reference images so the downloaded comparison page remains standalone.
  const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>PiComic · D 字形左端细化</title><style>
*{box-sizing:border-box}body{margin:0;background:#F3F2EF;color:#1D2230;font-family:"Segoe UI","Microsoft YaHei",sans-serif;-webkit-font-smoothing:antialiased}main{max-width:1440px;margin:auto;padding:36px 40px 28px}.eyebrow{font-size:10px;font-weight:700;letter-spacing:2.5px;color:#84858E;margin:0 0 12px}header{display:flex;align-items:center;justify-content:space-between;gap:25px;margin-bottom:28px}h1{font-size:34px;letter-spacing:-.8px;margin:0;font-weight:700}.lead{font-size:13px;color:#80818A;line-height:1.8;margin:10px 0 0}.references{display:flex;align-items:center;gap:20px}.ref{display:flex;align-items:center;font-size:11px;color:#8B8C95}.ref img{width:72px;height:72px}.ref.chosen{background:#E9EAEF;border-radius:12px;padding:3px 14px 3px 0;color:#555967}.grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:18px}.card{background:white;border:1px solid #E2E2E2;border-radius:17px;padding:18px 20px 17px}.top{display:flex;align-items:center;justify-content:space-between}.number{color:#4560E8;font-size:14px;font-weight:700;letter-spacing:.5px}.top a{font-size:10px;color:#9A9CA6;text-decoration:none;letter-spacing:.8px}.glyph{height:205px;display:flex;justify-content:center;align-items:center}.glyph img{width:218px;height:218px}h2{margin:0;font-size:17px;font-weight:650;letter-spacing:.3px}.note{color:#8A8C96;font-size:11px;line-height:1.7;margin:8px 0 18px}.details{border-top:1px solid #EFF0F2;padding-top:13px;display:flex;gap:9px;align-items:center}.detail-label{font-size:9px;color:#9A9CA6;white-space:nowrap}.zoom{width:72px;height:54px;overflow:hidden;background:#F3F4F8;border:1px solid #E9EBF1;border-radius:7px;flex-shrink:0}.zoom img{width:72px;height:54px}.icon{display:block;position:relative;width:48px;height:48px;border-radius:26%;overflow:hidden;flex-shrink:0;margin-left:auto}.icon img{position:absolute;left:-25%;top:-25%;width:150%;height:150%;max-width:none}a:focus-visible{outline:3px solid #4560E8;outline-offset:4px}footer{display:flex;justify-content:space-between;gap:25px;color:#9698A0;font-size:11px;line-height:1.8;margin-top:22px}footer strong{color:#666B79;font-weight:500}@media(max-width:1100px){.grid{grid-template-columns:repeat(3,minmax(0,1fr))}.references{display:none}}@media(max-width:820px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}main{padding:25px}h1{font-size:28px}.glyph{height:190px}.glyph img{width:196px;height:196px}.card{padding:16px}footer{display:block}}@media(max-width:520px){.grid{grid-template-columns:1fr}main{padding:22px 18px}.glyph{height:210px}.glyph img{width:230px;height:230px}.detail-label{margin-right:8px}.card{padding:18px 24px}h1{font-size:27px}}
</style></head><body><main><p class="eyebrow">PICOMIC / PI LETTERFORMS / ROUND 03</p><header><div><h1>保留 D 的气质，重画左端</h1><p class="lead">8 个细化版本 · 去掉原来的左侧下钩 · 参考 C 的轻快手写感</p></div><div class="references"><div class="ref"><img src="${uri(referenceC)}" alt="上一轮 C 手写连笔">原 C</div><div class="ref chosen"><img src="${uri(referenceD)}" alt="上一轮 D 经典衬线">原 D · 主参考</div></div></header><section class="grid" aria-label="八款 D 字形细化">${cards.join('\n')}</section><footer><span><strong>D1–D7 保留 D 的双腿与弯尾</strong> · D8 融合 C 的收笔 · 灰框为左端放大，蓝框为 48 px 图标预览。</span><span>点击 SVG 下载字形 · 点击蓝框下载图标母版</span></footer></main></body></html>`;
  await fs.writeFile(path.join(__dirname, 'index.html'), html);
  const browser = await chromium.launch({ headless: true, channel: process.env.PICOM_ICON_BROWSER_CHANNEL || undefined });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1100 }, deviceScaleFactor: 1.5 });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(pathToFileURL(path.join(__dirname, 'index.html')).href);
    await page.evaluate(async () => { await document.fonts.ready; await Promise.all([...document.images].map(image => image.decode())); });
    await page.screenshot({ path: path.join(__dirname, 'preview.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Mobile overflow');
    if (errors.length) throw new Error(errors.join('\n'));
    console.log(JSON.stringify({ variants: report, svgFiles: 16, browserErrors: errors }, null, 2));
  } finally { await browser.close(); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
