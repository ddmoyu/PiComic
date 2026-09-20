/* Continuous-reader gestures for the offline prototype. */
// Survive chapter DOM replacement: a single wheel burst must never span chapters.
let readerWheelGuardUntil=0;
function bindContinuousReader(stage) {
  const reader=stage.closest('.reader'), pages=stage.querySelector('.reader-pages');
  const feedback=reader.querySelector('.reader-edge-feedback'), label=feedback.querySelector('span');
  const hint=reader.querySelector('.reader-end-hint');
  const threshold=180, originChapter=chapter, originKey=key();
  let pointer=null, pull=0, switching=false, disposed=false, wheelTimer=null, changeTimer=null, hintTimer=null;
  let wheelStartedAt=0, wheelEvents=0;
  // Two local pages fill the entering viewport without interrupting the chapter's image flow.
  const preview=document.createElement('div');preview.className='reader-chapter-preview';
  preview.setAttribute('aria-hidden','true');
  if(chapter<book().chapters){
    preview.innerHTML=[1,2].map(p=>`<img src="${pageImage(p)}" width="640" height="930" alt="">`).join('');
    reader.insertBefore(preview,feedback);
  }
  const events=new AbortController();
  const on=(name,fn,options={})=>stage.addEventListener(name,fn,{...options,signal:events.signal});
  const atBottom=()=>stage.scrollHeight-stage.clientHeight-stage.scrollTop<=1;
  const valid=()=>!disposed&&route==='reader'&&state.mode==='scroll'&&chapter===originChapter&&key()===originKey&&!$('#app').inert;
  function draw() {
    const distance=112*(1-Math.exp(-pull/145));
    pages.style.transform=`translateY(${-distance}px)`;
    reader.style.setProperty('--chapter-reveal',`${distance}px`);
    reader.classList.toggle('reader-edge-preview',pull>0);
    feedback.classList.toggle('visible',pull>0||atBottom()&&chapter<book().chapters);
    feedback.classList.toggle('ready',pull>=threshold);
    feedback.style.setProperty('--edge-progress',Math.min(1,pull/threshold));
    label.textContent=pull>=threshold?`松开进入第 ${chapter+1} 章`:pull>0?'继续上拉进入下一章':`第 ${chapter} 章已读完 · 继续上拉`;
  }
  function reset() {
    pull=0;wheelStartedAt=0;wheelEvents=0;
    reader.classList.remove('reader-edge-pulling','reader-chapter-leaving');draw();
  }
  function showEnd() {
    if(hint.classList.contains('visible'))return;
    hint.classList.add('visible');
    clearTimeout(hintTimer);hintTimer=setTimeout(()=>hint.classList.remove('visible'),1600);
  }
  function addPull(delta) {
    if(chapter>=book().chapters){if(delta>0)showEnd();return;}
    reader.classList.add('reader-edge-pulling');
    pull=Math.max(0,Math.min(280,pull+delta));draw();
  }
  function finish(fromWheel=false) {
    readerInteracting=false;resetAutoCountdown();
    if(!valid()||pull<threshold||fromWheel&&(wheelEvents<6||performance.now()-wheelStartedAt<500)){reset();return;}
    switching=true;readerInteracting=true;stopAutoTurn();
    readerWheelGuardUntil=performance.now()+1300;
    reader.classList.remove('reader-edge-pulling');
    reader.classList.add('reader-chapter-leaving');
    feedback.classList.add('changing');label.textContent=`正在进入第 ${chapter+1} 章`;
    pages.style.transform='translateY(-180px)';
    changeTimer=setTimeout(()=>{
      if(!valid()){switching=false;readerInteracting=false;feedback.classList.remove('changing');reset();return;}
      readerWheelGuardUntil=performance.now()+700;
      saveProgress();chapter++;setPage(1);
    },640);
  }
  on('pointerdown',e=>{
    if(e.isPrimary===false||e.button!==0||switching||!valid())return;
    readerWheelGuardUntil=0;
    clearTimeout(wheelTimer);reset();
    pointer={id:e.pointerId,x:e.clientX,y:e.clientY,lastY:e.clientY,moved:false};
    readerInteracting=true;stage.setPointerCapture(e.pointerId);
    stage.classList.add('dragging');
  });
  on('pointermove',e=>{
    if(!pointer||e.pointerId!==pointer.id||switching)return;
    const delta=pointer.lastY-e.clientY;
    if(!pointer.moved&&Math.hypot(e.clientX-pointer.x,e.clientY-pointer.y)<8)return;
    pointer.moved=true;pointer.lastY=e.clientY;
    e.preventDefault();
    // Only movement that starts at the boundary contributes to the next-chapter gesture.
    if(pull>0){addPull(delta);return;}
    if(delta>0&&atBottom())addPull(delta);
    else stage.scrollTop+=delta;
  });
  on('pointerup',e=>{
    if(!pointer||e.pointerId!==pointer.id)return;
    const moved=pointer.moved;pointer=null;stage.classList.remove('dragging');
    if(stage.hasPointerCapture(e.pointerId))stage.releasePointerCapture(e.pointerId);
    finish();if(!moved&&!switching&&valid())toggleTools();
  });
  function cancel() {
    const id=pointer?.id;pointer=null;stage.classList.remove('dragging');
    if(id!==undefined&&stage.hasPointerCapture(id))stage.releasePointerCapture(id);
    readerInteracting=false;resetAutoCountdown();reset();
  }
  on('pointercancel',cancel);
  on('reader-interrupt',()=>{
    clearTimeout(wheelTimer);clearTimeout(changeTimer);switching=false;
    feedback.classList.remove('changing');cancel();
  });
  on('lostpointercapture',()=>{if(pointer)cancel();});
  on('dragstart',e=>e.preventDefault());
  on('wheel',e=>{
    if(e.ctrlKey||Math.abs(e.deltaX)>Math.abs(e.deltaY))return;
    if(switching||performance.now()<readerWheelGuardUntil){
      e.preventDefault();readerWheelGuardUntil=performance.now()+700;return;
    }
    if(pointer){e.preventDefault();return;}
    if(!valid())return;
    const delta=e.deltaY*(e.deltaMode===1?16:e.deltaMode===2?stage.clientHeight:1);
    if(pull>0||delta>0&&atBottom()){
      e.preventDefault();readerInteracting=true;
      if(!wheelStartedAt)wheelStartedAt=performance.now();
      if(delta>0)wheelEvents++;
      addPull(Math.max(-28,Math.min(28,delta*.28)));
      clearTimeout(wheelTimer);wheelTimer=setTimeout(()=>finish(true),260);
    }else resetAutoCountdown();
  },{passive:false});
  on('scroll',()=>{
    if(!valid()||switching)return;
    const top=stage.getBoundingClientRect().top;let closest=1;
    for(const f of stage.querySelectorAll('figure'))if(f.getBoundingClientRect().top<=top+100)closest=Number(f.dataset.page);
    page=closest;$('#page-number').textContent=page;$('#page-slider').value=page;
    $('#page-badge').textContent=`${page} / ${book().pages}`;saveProgress();
    if(!pointer&&!pull)draw();
  },{passive:true});
  return ()=>{
    disposed=true;events.abort();clearTimeout(wheelTimer);clearTimeout(changeTimer);clearTimeout(hintTimer);
    cancel();preview.remove();hint.classList.remove('visible');feedback.classList.remove('changing');
  };
}
