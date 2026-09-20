'use strict';
// Offline UX simulation. Never read browser cookies/storage or capture real tokens.
const webLoginSources = new Set(['ehentai','htcomic','nhentai']);
let webLoginAttempt = null, webLoginSequence = 0, accountScroll = 0;
const authMethodLabel = method => ({password:'账号密码',cookie:'导入 Cookie',key:'API Key',web:'网页登录'}[method] || '已保存会话');
function accountInfo(id){const value=state.accounts[id];return value===true?{method:sources.find(s=>s.id===id).auth,validatedAt:null}:value;}
function accountAction(i,title,sub,action,id){return `<button class="account-action" data-action="${action}" data-value="${id}"><span><strong>${title}</strong>${sub?`<small>${sub}</small>`:''}</span>${icon(i)}</button>`;}
function accountFields(id){
  if(id==='picacg')return [['账号','demo_reader'],['用户名','示例读者'],['等级','Lv 1 · Exp 90（演示）'],['简介','每一页，都值得慢慢读。']];
  if(id==='jmcomic'||id==='htcomic')return [['用户名','示例读者']];
  return [];
}
function renderAccounts(){
  return `${header('账号管理')}<main class="page-body account-page"><p class="account-demo-note">离线原型 · 请勿输入真实凭据</p>${sources.map(s=>{
    const info=accountInfo(s.id),expired=info&&scenario==='expired'&&state.source===s.id;
    const status=s.auth==='none'?'无需登录':expired?'登录已过期':info?'已登录（演示）':'未登录';
    const fields=info?accountFields(s.id):[];
    const credentials=s.id==='ehentai'||s.id==='htcomic'?'Cookies':info?.method==='key'?'API Key':'Token';
    return `<section class="account-section" data-account="${s.id}" aria-labelledby="account-${s.id}"><h3 id="account-${s.id}">${platformNames[s.id]}</h3><p class="account-status ${info&&!expired?'authenticated':''}">${status}</p>
      ${fields.length?`<dl class="account-fields">${fields.map(([k,v])=>`<div><dt>${k}</dt><dd>${v}</dd></div>`).join('')}</dl>`:''}
      ${info?`<details class="session-details"><summary>${credentials}<span>会话信息 ${icon('down')}</span></summary><dl><div><dt>登录方式</dt><dd>${authMethodLabel(info.method)}</dd></div><div><dt>验证状态</dt><dd>${expired?'需要重新登录':'验证通过（模拟）'}</dd></div><div><dt>凭据内容</dt><dd>不展示 · 原型未保存真实凭据</dd></div></dl></details>${s.id==='ehentai'?'<p class="outline-note">EX 访问权限：待单独验证</p>':''}
        ${accountAction('refresh','重新登录','登录失效时重新验证此平台','relogin',s.id)}${accountAction('logout','退出登录','','logout',s.id)}`:
        s.auth==='none'?`<p class="outline-note">当前按匿名方式浏览，不需要账号。</p>${accountAction('info','登录说明','','login',s.id)}`:
        `${webLoginSources.has(s.id)?accountAction('globe','网页登录','在平台网页完成登录，自动获取会话','web-login',s.id):''}${accountAction('user',s.auth==='cookie'?'导入 Cookie':s.auth==='key'?'使用 API Key':'账号密码登录','','login',s.id)}`}
    </section>`;
  }).join('')}</main>`;
}
function loginSheet(id){
  const s=sources.find(s=>s.id===id);
  if(s.auth==='none'){sheet('Hitomi',`<p class="sheet-sub">此来源按参考实现无需账号登录。实际网络与内容能力仍待联调。</p><button class="primary-button full-width" data-action="source" data-value="hitomi">浏览 Hitomi</button>`);return;}
  let fields='';
  if(s.auth==='password')fields=`<div class="form-field"><label for="demo-account">${id==='picacg'?'邮箱 / 账号':'账号'}</label><input id="demo-account" name="account" value="demo@example.com" autocomplete="off" required></div><div class="form-field"><label for="demo-password">演示密码</label><input id="demo-password" name="password" type="password" value="demo1234" autocomplete="off" required></div><label class="check-row"><input type="checkbox">记住密码以便重新登录（仅展示）</label>`;
  else if(s.auth==='cookie')fields=`<div class="form-field"><label for="demo-secret">演示 Cookie</label><textarea id="demo-secret" name="secret" required>demo-cookie</textarea></div><p class="outline-note">EH 登录与 EX 权限分别验证。</p>`;
  else fields=`<div class="form-field"><label for="demo-secret">演示 API Key</label><input id="demo-secret" name="secret" type="password" value="demo-api-key" autocomplete="off" required></div><p class="outline-note">API Key 与网页会话分别管理。</p>`;
  sheet(`登录 ${s.name}`,`<p class="sheet-sub">流程演示，请勿输入真实凭据。输入内容不会保存或发送。</p>${webLoginSources.has(id)?`<button class="secondary-button full-width" data-action="web-login" data-value="${id}">${icon('globe')}使用网页登录</button>`:''}<form id="login-form" data-source="${id}">${fields}<button type="submit" class="primary-button full-width">模拟登录</button></form>`);
}
function commitDemoSession(id,method){
  // Keep only non-secret demonstration metadata. Form values are deliberately ignored.
  state.accounts[id]={method,validatedAt:Date.now()};persist();scenario='normal';$('#scenario').value='normal';
}
function stopWebLogin(){if(webLoginAttempt)webLoginAttempt.timers.forEach(clearTimeout);webLoginAttempt=null;webLoginSequence++;}
function startWebLogin(id){
  if(!webLoginSources.has(id))return;
  stopWebLogin();webLoginAttempt={id,sequence:webLoginSequence,phase:'waiting',outcome:'success',timers:[]};go('web-login');
}
function afterWebLogin(delay,action){
  const attempt=webLoginAttempt;
  attempt.timers.push(setTimeout(()=>{if(webLoginAttempt===attempt&&attempt.sequence===webLoginSequence&&route==='web-login')action(attempt);},delay));
}
function simulateWebCompletion(){
  const attempt=webLoginAttempt;if(!attempt||attempt.phase!=='waiting')return;
  attempt.outcome=$('#web-outcome').value;attempt.phase='capturing';render();
  afterWebLogin(650,a=>{
    if(a.outcome==='missing'){a.phase='missing';render();return;}
    a.phase='validating';render();
    afterWebLogin(750,a=>{
      if(a.outcome==='rejected'){a.phase='rejected';render();return;}
      commitDemoSession(a.id,'web');a.phase='success';render();
      afterWebLogin(550,()=>{back();toast('网页登录成功，已自动获取并验证会话（模拟）');});
    });
  });
}
function renderWebLogin(){
  const a=webLoginAttempt,s=sources.find(s=>s.id===a.id),error=['missing','rejected'].includes(a.phase);
  const step={waiting:0,capturing:1,validating:2,success:3,missing:1,rejected:2}[a.phase];
  const messages={waiting:['在网页中完成登录','登录完成后，PiComic 会自动获取并验证此平台的会话。'],capturing:['正在获取登录凭据','检测平台会话，无需手动复制 Cookie 或 Token。'],validating:['正在验证账号','使用获取到的凭据确认账号与访问权限。'],success:['登录成功','已保存演示会话，即将返回。'],missing:['未获取到登录凭据','请先在网页完成登录，再尝试检测。'],rejected:['会话验证未通过','检测到的凭据尚不能用于访问，请重新登录。']};
  const [title,description]=messages[a.phase];
  return `${header('网页登录',ib('close','back',a.phase==='success'?'关闭网页登录':'取消网页登录'))}<main class="page-body web-login-page"><div class="web-origin">${icon('globe')}<strong>${s.name}</strong><span>平台登录页</span></div><p class="outline-note">离线流程演示 · 此区域在 Android 中由 WebView 打开真实登录页</p><ol class="auth-steps">${['网页登录','自动获取','验证会话'].map((t,i)=>`<li class="${i<step?'done':i===step?'current':''}"><span>${i<step?'✓':i+1}</span>${t}</li>`).join('')}</ol><section class="web-login-preview ${error?'failed':''}" aria-live="polite"><div class="web-login-symbol">${icon(error?'info':a.phase==='success'?'check':'user')}</div><h3>${title}</h3><p>${description}</p>${a.phase==='waiting'?`<button class="primary-button full-width" data-action="complete-web-demo">模拟完成网页登录</button>`:error?'<button class="primary-button full-width" data-action="retry-web-login">返回登录页</button>':['capturing','validating'].includes(a.phase)?'<div class="auth-progress" role="progressbar" aria-label="正在处理登录"></div>':''}</section>${a.phase==='waiting'?`<details class="web-demo-options"><summary>原型状态演示</summary><label for="web-outcome">网页完成后的结果</label><select id="web-outcome"><option value="success">成功获取并验证</option><option value="missing">未获取到凭据</option><option value="rejected">凭据验证失败</option></select></details>`:''}<button class="text-button full-width" data-action="back">${a.phase==='success'?'返回':'取消并返回'}</button></main>`;
}
