// Regenerate the self-contained comparison page and PNG from the six SVG masters.
// Dependencies: sharp and playwright. No remote assets or network requests.
const fs = require('node:fs/promises');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const sharp = require('sharp');
const { chromium } = require('playwright');

const concepts = [
  ['01-shonen-burst.svg', '热血爆框', 'SHONEN / IMPACT', '爆炸对白框，朱红 π，少年漫的冲击感。', '#F54B43'],
  ['02-manga-ink.svg', '黑白墨绘', 'MANGA / INK', '毛笔笔触与集中线，经典黑白原稿感。', '#16191B'],
  ['03-shojo-ribbon.svg', '少女漫书页', 'SHOJO / RIBBON', '樱花粉丝带 π，翻开的漫画与闪光。', '#C36192'],
  ['04-retro-pop.svg', '复古波普', 'RETRO / POP', '错版印刷、青绿底色与立体奶油 π。', '#238C81'],
  ['05-cyber-panels.svg', '赛博漫格', 'CYBER / PANELS', '倾斜分镜与电光青 π，深夜科幻感。', '#6655B2'],
  ['06-minimal-bubble.svg', '极简气泡', 'MINIMAL / BUBBLE', '对白气泡藏入 π，简洁、清晰、耐看。', '#4560E8'],
];

async function main() {
  const cards = [];
  const report = [];
  for (const [file, name, style, detail, accent] of concepts) {
    const source = await fs.readFile(path.join(__dirname, file), 'utf8');
    if (!source.includes('viewBox="0 0 108 108"') || !source.includes('id="background"') || !source.includes('id="foreground"')) {
      throw new Error(`${file}: missing adaptive master dimensions or layers`);
    }
    if (/<(?:image|text|script|foreignObject|filter)\b|\b(?:href|xlink:href)=/i.test(source)) {
      throw new Error(`${file}: expected self-contained vector paths`);
    }
    // Measure the visible pi path including the inherited foreground outline.
    const foreground = source.match(/<g id="foreground"([^>]*)>/)[1];
    const pi = source.match(/<path id="pi"[^>]*\/>/)[0];
    const onlyPi = `<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1080" viewBox="0 0 108 108"><g ${foreground}>${pi}</g></svg>`;
    const { data, info } = await sharp(Buffer.from(onlyPi)).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
    let radius = 0;
    for (let y = 0; y < info.height; y++) {
      for (let x = 0; x < info.width; x++) {
        if (data[(y * info.width + x) * info.channels + info.channels - 1] > 32) {
          radius = Math.max(radius, Math.hypot((x + 0.5) / 10 - 54, (y + 0.5) / 10 - 54));
        }
      }
    }
    if (radius > 33.1) throw new Error(`${file}: pi exceeds the 66-unit safety circle (r=${radius.toFixed(2)})`);
    report.push({ file, piRadius: Number(radius.toFixed(2)), safeCircleRadius: 33 });
    const uri = `data:image/svg+xml;base64,${Buffer.from(source).toString('base64')}`;
    const num = file.slice(0, 2);
    cards.push(`<article class="card" style="--accent:${accent}">
      <div class="card-top"><span class="number">${num}</span><span class="style">${style}</span></div>
      <div class="hero"><div class="adaptive large"><img src="${uri}" alt="${name}：π 漫画图标" draggable="false"></div></div>
      <div class="title-row"><h2>${name}</h2><a href="${file}" download aria-label="下载${name} SVG">SVG <span aria-hidden="true">↗</span></a></div>
      <p class="description">${detail}</p>
      <div class="mini-row"><span class="mini-label">桌面预览</span><div class="adaptive mini rounded"><img src="${uri}" alt="${name} 48 像素圆角预览"></div><div class="adaptive mini circle"><img src="${uri}" alt="${name} 48 像素圆形预览"></div><span class="size">48 px</span></div>
    </article>`);
  }
  const html = `<!doctype html>
<html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>PiComic · 六款 π 漫画图标</title>
<style>
*{box-sizing:border-box}body{margin:0;background:#F3F2EE;color:#23242B;font-family:Inter,"Segoe UI","Microsoft YaHei",sans-serif;-webkit-font-smoothing:antialiased}main{max-width:1360px;margin:auto;padding:46px 48px 32px}.eyebrow{font-size:11px;letter-spacing:2.6px;font-weight:700;color:#72737B;margin:0 0 13px}.header{display:flex;justify-content:space-between;align-items:flex-end;margin-bottom:30px}h1{font-size:42px;letter-spacing:-1.6px;line-height:1.1;margin:0;font-weight:750}h1 span{font-size:20px;letter-spacing:.2px;font-weight:500;color:#72737B;margin-left:18px}.subtitle{font-size:14px;color:#74757D;margin:13px 0 0}.shape-switch{display:flex;border:1px solid #DAD9D4;background:#ECEBE6;border-radius:11px;padding:4px;gap:3px;margin-bottom:2px}button{font:inherit;font-size:12px;border:0;background:transparent;border-radius:7px;padding:9px 15px;color:#70717B;cursor:pointer}button[aria-pressed="true"]{color:#282B39;background:#FFF;box-shadow:0 1px 3px #0000000D}button:focus-visible,a:focus-visible{outline:3px solid #4560E8;outline-offset:3px}.grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:20px}.card{background:#FFFFFF;border:1px solid #E4E2DE;border-radius:19px;padding:21px 24px 18px;overflow:hidden}.card-top{display:flex;align-items:center;justify-content:space-between}.number{font-size:14px;font-weight:750;letter-spacing:1px;color:var(--accent)}.style{font-size:9px;letter-spacing:1.5px;color:#94939A;font-weight:650}.hero{height:218px;display:flex;align-items:center;justify-content:center}.adaptive{overflow:hidden;position:relative;flex-shrink:0;isolation:isolate;border-radius:26%}.adaptive img{position:absolute;width:150%;height:150%;max-width:none;left:-25%;top:-25%;display:block}.large{width:176px;height:176px;box-shadow:0 9px 17px #191A3014}.mini{height:48px;width:48px}.circle{border-radius:50%}.rounded{border-radius:26%}.square{border-radius:0}.title-row{display:flex;align-items:center;justify-content:space-between;gap:10px}h2{font-size:18px;letter-spacing:.2px;margin:0;font-weight:700}.title-row a{font-size:10px;text-decoration:none;color:var(--accent);border:1px solid #ECEAED;border-radius:6px;padding:5px 8px;font-weight:700;letter-spacing:.5px}.title-row a span{margin-left:5px}.description{font-size:12px;color:#808089;line-height:1.6;margin:9px 0 18px}.mini-row{display:flex;align-items:center;gap:12px;border-top:1px solid #EFEDEE;padding-top:15px}.mini-label{font-size:10px;color:#93929A;margin-right:auto}.size{font-size:10px;color:#AAA7AF;font-variant-numeric:tabular-nums;margin-left:2px}.footer{display:flex;justify-content:space-between;align-items:center;margin-top:24px;font-size:11px;color:#8A8990;gap:20px}.footer a{color:#747584;text-decoration:underline;text-underline-offset:3px}.footer strong{font-weight:600;color:#61616C}body[data-shape="circle"] .large{border-radius:50%}body[data-shape="square"] .large{border-radius:0}@media(max-width:900px){main{padding:28px}.grid{grid-template-columns:repeat(2,minmax(0,1fr))}h1{font-size:34px}h1 span{display:block;font-size:17px;margin:10px 0 0}.header{gap:20px}.shape-switch{flex-shrink:0}}@media(max-width:580px){main{padding:24px 18px}.header{display:block}.shape-switch{margin-top:22px;width:max-content}.grid{grid-template-columns:1fr}.footer{display:block;line-height:2}.hero{height:210px}.card{padding:20px 24px}}
</style></head><body data-shape="rounded"><main>
<p class="eyebrow">PICOMIC / ICON EXPLORATION</p>
<header class="header"><div><h1>一个 π，六种漫画风格<span>PiComic 图标设计</span></h1><p class="subtitle">圆周率 π × 漫画语言 · 六款独立 SVG 矢量稿</p></div><div class="shape-switch" role="group" aria-label="大图裁切形状"><button type="button" data-shape="rounded" aria-pressed="true">圆角</button><button type="button" data-shape="circle" aria-pressed="false">圆形</button><button type="button" data-shape="square" aria-pressed="false">方形</button></div></header>
<section class="grid" aria-label="六款图标设计">${cards.join('\n')}</section>
<footer class="footer"><span><strong>Android 自适应图标比例</strong> · 108 × 108 母版 · π 位于中央安全区</span><span>预览应用系统裁切；SVG 保留完整背景。 <a href="https://developer.android.com/develop/ui/compose/system/icon_design_adaptive?hl=zh-cn" target="_blank" rel="noreferrer">设计规范 ↗</a></span></footer>
</main><script>document.querySelectorAll('button[data-shape]').forEach(button=>button.addEventListener('click',()=>{document.body.dataset.shape=button.dataset.shape;document.querySelectorAll('button[data-shape]').forEach(other=>other.setAttribute('aria-pressed',String(other===button)));}));</script></body></html>`;
  await fs.writeFile(path.join(__dirname, 'index.html'), html);
  const browser = await chromium.launch({ headless: true, channel: process.env.PICOM_ICON_BROWSER_CHANNEL || undefined });
  try {
    const page = await browser.newPage({ viewport: { width: 1400, height: 1100 }, deviceScaleFactor: 1.5 });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(pathToFileURL(path.join(__dirname, 'index.html')).href);
    await page.evaluate(async () => { await document.fonts.ready; await Promise.all([...document.images].map(image => image.decode())); });
    await page.screenshot({ path: path.join(__dirname, 'preview.png'), fullPage: true });
    await page.getByRole('button', { name: '圆形', exact: true }).click();
    if (await page.locator('body').getAttribute('data-shape') !== 'circle') throw new Error('Circle preview switch failed');
    await page.screenshot({ path: path.join(__dirname, 'preview-circle.png'), fullPage: true });
    await page.getByRole('button', { name: '方形', exact: true }).click();
    if (await page.locator('body').getAttribute('data-shape') !== 'square') throw new Error('Square preview switch failed');
    await page.setViewportSize({ width: 390, height: 844 });
    if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Mobile preview overflows');
    if (errors.length) throw new Error(errors.join('\n'));
    console.log(JSON.stringify({ designs: report, preview: 'preview.png', circlePreview: 'preview-circle.png', browserErrors: errors, mobileWidth: 390, validation: 'passed' }, null, 2));
  } finally {
    await browser.close();
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
