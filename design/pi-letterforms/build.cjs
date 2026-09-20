// Original path-drawn pi letterforms. Dependencies: sharp, playwright (Chromium).
const fs = require('node:fs/promises');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const sharp = require('sharp');
const { chromium } = require('playwright');
const ink = '#1D2230';
const entries = [
  {
    id: 'A', slug: 'geometric', name: '几何直切', caption: '平直横画 · 利落切角',
    shapes: '<path d="M28 31h52v10H68v22q0 5 9 2v10q-21 6-21-12V41H46q1 21-6 35l-11-5q6-13 6-30h-7z"/>',
  },
  {
    id: 'B', slug: 'soft', name: '圆润软糖', caption: '圆头圆脚 · 饱满柔和',
    shapes: '<path fill="none" stroke="#1D2230" stroke-width="12" stroke-linecap="round" stroke-linejoin="round" d="M31 36Q52 31 77 35M43 35q1 23-7 36M64 35v28q0 12 12 6"/>',
  },
  {
    id: 'C', slug: 'handwritten', name: '手写连笔', caption: '轻快斜势 · 自然长尾',
    shapes: '<path fill="none" stroke="#1D2230" stroke-width="5.7" stroke-linecap="round" stroke-linejoin="round" d="M28 42q6-12 18-9t32-1M44 35q0 16-9 38M65 34q-7 20-7 31c0 14 13 5 21-3"/>',
  },
  {
    id: 'D', slug: 'serif', name: '经典衬线', caption: '粗细对比 · 数学书卷气',
    shapes: '<path d="M27 43q3-15 16-15h34l-3 8H65l-4 26q-2 12 6 9 4-1 8-6l2 2q-6 11-15 11-12 0-9-15l5-27H44q-1 25-11 42h-9q13-19 15-42-7 0-10 7z"/>',
  },
  {
    id: 'E', slug: 'comic-heavy', name: '漫画粗黑', caption: '夸张大横 · 粗壮短脚',
    shapes: '<path fill-rule="evenodd" d="M30 29h47l5 7-5 11H65l1 14 10-1-2 15-12 3-10-8-1-23h-6l-2 22-8 10-12-7 7-12 3-13h-9z M37 34l32-1-2 4H35z M59 50l2 13 3 3-4 2-3-5z"/>',
  },
  {
    id: 'F', slug: 'brush', name: '毛笔飞白', caption: '锋利起笔 · 奔放收锋',
    shapes: '<path fill-rule="evenodd" d="M24 42l8-12 14 1 35-6-4 13-11 1-2 22 9 1 9-8-6 19-14 6-10-13 5-26-11 2-5 25-12 12-5-5 9-17 3-14-11 4z M40 36l30-5-10 4-19 3z M38 49l-4 16 4-5 3-11z M59 47l-1 16 3 7-1-10 2-13z"/>',
  },
  {
    id: 'G', slug: 'pixel', name: '像素街机', caption: '阶梯轮廓 · 复古游戏感',
    shapes: '<path d="M30 30h48v12H66v24h12v12H60v-6h-6V42H42v24h-6v12H24V66h6V42h-6v-6h6z"/>',
  },
  {
    id: 'H', slug: 'folded', name: '折纸几何', caption: '斜切接笔 · 锐角折转',
    shapes: '<path fill-rule="evenodd" d="M28 31h53L70 43h-7v22l13-7v12L59 80l-8-8V43h-7v23L30 80l-7-8 9-10V43H20z M32 35h38l-5 4H29z M55 46l4-4v24l-4 4z"/>',
  },
  {
    id: 'I', slug: 'wide', name: '宽扁圆角', caption: '重心更低 · 宽横短腿',
    shapes: '<path d="M25 35h58q5 0 5 5t-5 5H69v15q0 5 9 3 5-1 5 4 0 7-13 7-14 0-14-14V45H43q0 16-7 26-4 6-10 2-5-3-1-8 7-9 7-20h-7q-5 0-5-5t5-5z"/>',
  },
  {
    id: 'J', slug: 'fine-line', name: '极细单线', caption: '等粗线条 · 轻盈留白',
    shapes: '<path fill="none" stroke="#1D2230" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" d="M29 36h50M43 36v19q0 12-10 21M62 36v29q0 13 15 8"/>',
  },
  {
    id: 'K', slug: 'condensed', name: '窄高标题', caption: '收窄横画 · 拉长双腿',
    shapes: '<path d="M35 24h39v11h-9v32q0 6 7 2v12q-19 7-19-13V35h-5v25q0 16-7 22l-10-7q6-6 6-20V35h-7z"/>',
  },
  {
    id: 'L', slug: 'stencil', name: '断笔模板', caption: '留缝切断 · 硬朗结构',
    shapes: '<path d="M28 31h24v11H28zM56 31h24v11H56zM35 46h11v14l-7 16-11-5 7-14zM58 46h11v17q0 4 9 2v10q-20 5-20-11z"/>',
  },
];

function svg(title, body, background = '') {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108" role="img" aria-labelledby="title"><title id="title">${title}</title>${background}${body}</svg>\n`;
}

async function alphaBounds(body) {
  const input = svg('Measure', body).replace('width="108" height="108"', 'width="1080" height="1080"');
  const { data, info } = await sharp(Buffer.from(input)).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  let radius = 0;
  for (let y = 0; y < info.height; y++) for (let x = 0; x < info.width; x++) {
    if (data[(y * info.width + x) * info.channels + info.channels - 1] > 32) {
      radius = Math.max(radius, Math.hypot((x + .5) / 10 - 54, (y + .5) / 10 - 54));
    }
  }
  return radius;
}

async function main() {
  await fs.mkdir(path.join(__dirname, 'android'), { recursive: true });
  const cards = [];
  const report = [];
  for (const entry of entries) {
    const raw = `<g fill="${ink}">${entry.shapes}</g>`;
    const radius = await alphaBounds(raw);
    const scale = Math.min(1, 31.6 / radius);
    const transform = `translate(54 54) scale(${scale.toFixed(5)}) translate(-54 -54)`;
    const group = `<g id="foreground" fill="${ink}" transform="${transform}">${entry.shapes}</g>`;
    const measured = await alphaBounds(group);
    if (measured > 32) throw new Error(`${entry.id} exceeds the safe area: ${measured}`);
    const filename = `${entry.id.toLowerCase()}-${entry.slug}.svg`;
    const source = svg(`PiComic · ${entry.id} ${entry.name} · π 字形`, group);
    const icon = svg(`PiComic · ${entry.id} ${entry.name} · Android 图标母版`, group.replaceAll(ink, '#FFFFFF'), '<g id="background"><path fill="#4560E8" d="M0 0h108v108H0z"/></g>');
    await fs.writeFile(path.join(__dirname, filename), source);
    await fs.writeFile(path.join(__dirname, 'android', filename), icon);
    const uri = `data:image/svg+xml;base64,${Buffer.from(source).toString('base64')}`;
    const iconUri = `data:image/svg+xml;base64,${Buffer.from(icon).toString('base64')}`;
    report.push({ id: entry.id, name: entry.name, radius: +measured.toFixed(2), safeRadius: 33 });
    cards.push(`<article class="card"><div class="card-top"><span class="letter">${entry.id}</span><a href="${filename}" download aria-label="下载${entry.id} ${entry.name}字形 SVG">SVG ↗</a></div><div class="glyph"><img src="${uri}" alt="${entry.id} ${entry.name} π 字形"></div><div class="card-bottom"><div><h2>${entry.name}</h2><p>${entry.caption}</p></div><a class="icon" href="android/${filename}" download aria-label="下载${entry.id} Android 图标 SVG"><img src="${iconUri}" alt="${entry.name} 同底色 48 像素图标"></a></div></article>`);
  }
  const html = `<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>PiComic · 12 种 π 字形</title><style>
*{box-sizing:border-box}body{margin:0;background:#F1F0EC;color:#1D2230;font-family:"Segoe UI","Microsoft YaHei",sans-serif;-webkit-font-smoothing:antialiased}main{max-width:1480px;margin:auto;padding:42px 46px 28px}.eyebrow{font-size:11px;color:#777A85;letter-spacing:2.7px;margin:0 0 13px;font-weight:700}header{display:flex;align-items:flex-end;justify-content:space-between;gap:30px;margin-bottom:28px}h1{font-size:38px;letter-spacing:-1px;margin:0;font-weight:750}.lead{font-size:13px;color:#7F818B;margin:12px 0 0}.tag{font-size:12px;color:#777A85;text-align:right;line-height:1.8;white-space:nowrap}.grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:17px}.card{border:1px solid #DFE0DF;background:#FFF;border-radius:17px;padding:16px 20px 19px}.card-top{display:flex;justify-content:space-between;align-items:center}.letter{font-size:16px;font-weight:750;color:#4560E8}.card-top a{font-size:10px;letter-spacing:1px;color:#9B9DA5;text-decoration:none}a:focus-visible{outline:3px solid #4560E8;outline-offset:4px}.glyph{height:178px;display:flex;align-items:center;justify-content:center}.glyph img{width:166px;height:166px}.card-bottom{display:flex;align-items:center;justify-content:space-between;gap:10px;border-top:1px solid #EEEFF1;padding-top:14px}h2{font-size:16px;letter-spacing:.4px;margin:0;font-weight:650}.card-bottom p{font-size:10px;color:#94969E;margin:6px 0 0}.icon{display:block;width:48px;height:48px;flex-shrink:0;border-radius:26%;overflow:hidden;position:relative}.icon img{position:absolute;left:-25%;top:-25%;width:150%;height:150%;max-width:none}footer{margin-top:21px;display:flex;justify-content:space-between;gap:20px;color:#8B8D97;font-size:11px;line-height:1.7}footer strong{color:#666A76;font-weight:500}@media(max-width:1050px){.grid{grid-template-columns:repeat(3,minmax(0,1fr))}main{padding:30px}.tag{display:none}}@media(max-width:750px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}main{padding:25px 18px}h1{font-size:30px}.card{padding:14px}.glyph{height:160px}.glyph img{width:152px;height:152px}.card-bottom{display:block}.icon{margin-top:12px}footer{display:block}}@media(max-width:380px){.grid{grid-template-columns:1fr}}
</style></head><body><main><p class="eyebrow">PICOMIC / PI LETTERFORMS / ROUND 02</p><header><div><h1>先选一个喜欢的 π</h1><p class="lead">12 种重新绘制的字形 · 统一黑白对比 · 每款附同底色图标预览</p></div><div class="tag">横画 / 粗细 / 比例 / 收笔<br>只看符号本身的性格</div></header><section class="grid" aria-label="十二种 π 字形">${cards.join('\n')}</section><footer><span><strong>A–L 为全新字形方案</strong> · 点击 SVG 下载透明字形；点击蓝色图标下载 Android SVG 母版。</span><span>纯矢量路径 · 无字体依赖 · 图标预览 48 px</span></footer></main></body></html>`;
  await fs.writeFile(path.join(__dirname, 'index.html'), html);
  const browser = await chromium.launch({ headless: true, channel: process.env.PICOM_ICON_BROWSER_CHANNEL || undefined });
  try {
    const page = await browser.newPage({ viewport: { width: 1480, height: 1250 }, deviceScaleFactor: 1.5 });
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.goto(pathToFileURL(path.join(__dirname, 'index.html')).href);
    await page.evaluate(async () => { await document.fonts.ready; await Promise.all([...document.images].map(image => image.decode())); });
    await page.screenshot({ path: path.join(__dirname, 'preview.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Mobile horizontal overflow');
    if (errors.length) throw new Error(errors.join('\n'));
    console.log(JSON.stringify({ letterforms: report, svgFiles: 24, preview: 'preview.png', browserErrors: errors }, null, 2));
  } finally { await browser.close(); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
