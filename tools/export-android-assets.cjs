// Rasterize the project's original SVG illustrations; no remote artwork is used.
const {chromium}=require('playwright');
const path=require('node:path'),fs=require('node:fs'),{pathToFileURL}=require('node:url');
(async()=>{
 const root=path.resolve(__dirname,'..'),out=path.join(root,'app/src/main/res/drawable-nodpi');fs.mkdirSync(out,{recursive:true});
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try { const page=await browser.newPage({viewport:{width:640,height:960},deviceScaleFactor:1});
  for(const file of [...Array.from({length:9},(_,i)=>`cover-${i}`),...Array.from({length:6},(_,i)=>`page-${i+1}`)]){
   await page.goto(pathToFileURL(path.join(root,`prototype/assets/${file}.svg`)).href);
   await page.locator('svg').evaluate(e=>{e.style.width='640px';e.style.height=e.viewBox.baseVal.height/e.viewBox.baseVal.width*640+'px';});
   await page.locator('svg').screenshot({path:path.join(out,file.replace('-','_')+'.png')});
  }
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
