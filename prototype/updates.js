'use strict';
// All releases, APK transfers and installer actions below are local UI simulations.
const demoRelease={current:'1.0.0',version:'1.1.0',size:'86.4 MB',date:'2026-09-18',notes:['调整平台探索与分类体验','改进阅读进度恢复','优化长图加载与阅读体验']};
const updateDemo={phase:'idle',progress:0,result:'available',downloadResult:'success',lastChecked:null,timers:[],generation:0};
function clearUpdateTimers(){updateDemo.timers.forEach(clearTimeout);updateDemo.timers=[];updateDemo.generation++;}
function afterUpdate(delay,action){const generation=updateDemo.generation;updateDemo.timers.push(setTimeout(()=>{if(generation!==updateDemo.generation)return;action();if(route==='updates')render();},delay));}
function resetUpdateDemo(){clearUpdateTimers();Object.assign(updateDemo,{phase:'idle',progress:0,result:'available',downloadResult:'success',lastChecked:null});}
function checkUpdateDemo(){
  if(['checking','downloading','verifying'].includes(updateDemo.phase))return;
  clearUpdateTimers();updateDemo.result=$('#update-result')?.value||updateDemo.result;updateDemo.downloadResult=$('#update-download-result')?.value||updateDemo.downloadResult;updateDemo.phase='checking';updateDemo.progress=0;render();
  afterUpdate(700,()=>{updateDemo.phase=updateDemo.result;updateDemo.lastChecked=new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'});});
}
function downloadUpdateDemo(resume=false){
  if(resume?updateDemo.phase!=='paused':!['available','download-error','invalid'].includes(updateDemo.phase))return;
  clearUpdateTimers();if(!resume)updateDemo.progress=0;updateDemo.downloadResult=$('#update-download-result')?.value||updateDemo.downloadResult;updateDemo.phase='downloading';render();
  const advance=()=>{
    updateDemo.progress=Math.min(100,updateDemo.progress+17);
    if(updateDemo.downloadResult==='network'&&updateDemo.progress>=51){updateDemo.phase='download-error';return;}
    if(updateDemo.progress===100){updateDemo.phase='verifying';afterUpdate(650,()=>updateDemo.phase=updateDemo.downloadResult==='checksum'?'invalid':'ready');}
    else afterUpdate(280,advance);
  };
  afterUpdate(280,advance);
}
function pauseUpdateDemo(){if(updateDemo.phase!=='downloading')return;clearUpdateTimers();updateDemo.phase='paused';render();}
function cancelUpdateDemo(){clearUpdateTimers();updateDemo.phase='available';updateDemo.progress=0;closeSheet(false);render();}
function installUpdateDemo(){if(updateDemo.phase!=='ready')return;sheet('允许安装应用 · 演示',`<p class="sheet-sub">首次安装更新时，Android 会要求允许 PiComic 安装应用。返回后再继续安装。</p><div class="sheet-footer"><button class="secondary-button" data-action="close">暂不安装</button><button class="primary-button" data-action="update-permission-demo">模拟允许</button></div>`);}
function installerPreview(){sheet('系统安装器 · 演示',`<div class="update-installer"><img src="assets/icon.svg" alt="PiComic"><div><strong>PiComic ${demoRelease.version}</strong><p>是否安装此应用的更新？</p></div></div><p class="sheet-sub">原型不会安装应用。正式 App 将交给 Android 系统确认安装，并在下次启动核对版本。</p><div class="sheet-footer"><button class="secondary-button" data-action="close">取消</button><button class="primary-button" data-action="update-installer-demo">模拟交给系统安装</button></div>`);}
function releasePreview(){sheet('GitHub Release · 演示',`<span class="update-channel">稳定版 · v${demoRelease.version}</span><h3>PiComic ${demoRelease.version}</h3><p class="sheet-sub">${demoRelease.date} · 示例发布内容</p><ul class="update-notes">${demoRelease.notes.map(n=>`<li>${n}</li>`).join('')}</ul><p class="outline-note">正式 App 的“查看 GitHub Release”将打开项目对应版本的发布页。当前尚未绑定发布仓库。</p><button class="primary-button full-width" data-action="close">返回</button>`);}
function renderUpdates(){
  const u=updateDemo,hasRelease=['available','downloading','paused','verifying','ready','download-error','invalid','install-pending'].includes(u.phase);
  const states={idle:['refresh','检查新版本','从 GitHub Releases 获取稳定版本。'],checking:['refresh','正在检查更新','正在获取最新发布信息…'],latest:['check','当前已是最新版本','当前没有可升级的稳定版本。'],error:['wifi','检查更新失败','请检查网络或代理连接后重试。'],limited:['clock','请求过于频繁','GitHub 暂时限制了请求，请稍后再试。'],unpublished:['info','暂无可用发布','尚未找到可下载的稳定版本。'],incompatible:['info','暂无适配的更新包','此版本不支持当前 Android 系统或设备。']};
  let status='';
  if(!hasRelease){const [i,title,sub]=states[u.phase];status=`<section class="update-status" aria-live="polite">${icon(i)}<h3>${title}</h3><p>${sub}</p>${u.phase==='checking'?'<div class="auth-progress" aria-label="正在检查更新" role="progressbar"></div>':''}</section>`;}
  else {
    const labels={available:'发现新版本',downloading:'正在下载更新',paused:'下载已暂停',verifying:'正在校验安装包',ready:'安装包已准备好','download-error':'下载中断',invalid:'安装包校验失败','install-pending':'等待系统安装结果'};
    let action='';
    if(['available','download-error','invalid'].includes(u.phase))action=`<button class="primary-button full-width" data-action="update-download">${icon('download')}${u.phase==='available'?'下载更新':'重新下载'} · ${demoRelease.size}</button>`;
    if(['downloading','paused','verifying'].includes(u.phase))action=`<div class="update-transfer"><span>${u.progress}%</span><progress max="100" value="${u.progress}" aria-label="更新包下载进度"></progress></div><div class="sheet-footer">${u.phase==='verifying'?'':`<button class="secondary-button" data-action="${u.phase==='paused'?'update-resume':'update-pause'}">${u.phase==='paused'?'继续下载':'暂停下载'}</button>`}<button class="secondary-button" data-action="update-cancel">取消下载</button></div>`;
    if(u.phase==='ready')action='<button class="primary-button full-width" data-action="update-install">安装更新</button>';
    if(u.phase==='install-pending')action='<p class="outline-note">已演示交给系统处理。当前版本保持不变，未模拟安装成功。</p><button class="secondary-button full-width" data-action="update-reinstall">重新打开安装入口</button>';
    status=`<section class="update-release"><div class="update-release-heading"><span class="update-channel">稳定版</span><span>${demoRelease.date}</span></div><h3>${labels[u.phase]}</h3><div class="update-version">v${demoRelease.version}<small> · ${demoRelease.size}</small></div><h4>更新内容</h4><ul class="update-notes">${demoRelease.notes.map(n=>`<li>${n}</li>`).join('')}</ul>${u.phase==='invalid'?'<p class="update-error">文件完整性校验未通过，不能安装。请重新下载。</p>':u.phase==='download-error'?'<p class="update-error">请检查网络连接，再重新下载。</p>':''}${action}<button class="text-button full-width" data-action="update-release-page">查看 GitHub Release ${icon('next')}</button></section>`;
  }
  const busy=['checking','downloading','verifying'].includes(u.phase);
  return `${header('检查更新')}<main class="page-body updates-page"><section class="update-summary" aria-label="当前版本与更新渠道"><div><span>当前版本</span><strong>${demoRelease.current}<small>（演示）</small></strong></div><div><span>更新渠道</span><span>GitHub Releases · 稳定版</span></div></section>${status}<button class="${hasRelease?'secondary-button':'primary-button'} full-width" data-action="update-check" ${busy?'disabled':''}>${icon('refresh')}${u.phase==='idle'?'检查更新':'重新检查'}</button><p class="update-check-time">${u.lastChecked?`上次检查 ${u.lastChecked} · 模拟结果`:'尚未检查'}</p>${settingGroup('检查方式',prefToggle('startupUpdate','启动时检查更新','每天最多自动检查一次，仅提示，不自动下载'))}<details class="update-demo-options"><summary>原型状态演示</summary><label for="update-result">检查结果</label><select id="update-result" ${busy?'disabled':''}>${[['available','发现新版本'],['latest','已经是最新版本'],['error','网络失败'],['limited','GitHub 请求限流'],['unpublished','暂无发布'],['incompatible','系统或设备不兼容']].map(([v,t])=>`<option value="${v}" ${u.result===v?'selected':''}>${t}</option>`).join('')}</select><label for="update-download-result">下载结果</label><select id="update-download-result" ${busy?'disabled':''}>${[['success','文件校验通过'],['network','下载中断'],['checksum','文件校验失败']].map(([v,t])=>`<option value="${v}" ${u.downloadResult===v?'selected':''}>${t}</option>`).join('')}</select></details><p class="outline-note">离线交互演示，不访问 GitHub，不下载或安装 APK。版本和更新说明均为示意。</p></main>`;
}
