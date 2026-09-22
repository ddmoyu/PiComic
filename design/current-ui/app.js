/* Every view corresponds to a fresh production Compose screenshot. Review navigation only. */
const views = [
 ['discover','探索','01-discover','discover',false,'保留左封面、右信息；统一标题、作者、标签及页数层级。'],
 ['categories','分类','02-categories','categories',false,'恢复接近原版的紧凑浅底标签：可见高度 32 px，点击区域 48 px；保留现有分类。'],
 ['detail','作品详情','03-detail','detail',false,'保留封面、操作、信息、简介和章节；统一文字与对齐。'],
 ['chapters','详情 · 章节','04-chapters','chapters',false,'继续使用单行章节列表，调整分隔与行内对齐。'],
 ['favorites','书架 · 收藏','05-library','favorites',false,'沿用作品列表；保留现有三项书架标签。'],
 ['history-empty','书架 · 空历史','06-library-empty','history-empty',false,'保留现有空状态及文案，不添加推荐或操作入口。'],
 ['history','书架 · 阅读历史','07-history','history',false,'保留阅读位置、单条删除和清空操作，统一列表排版。'],
 ['downloads','书架 · 下载管理','08-downloads','downloads',false,'保留任务、章节、进度和现有操作，整理行内间距。'],
 ['settings','设置','09-settings','settings',false,'保留四组入口及顺序，统一分组间距、图标和摘要对比度。'],
 ['settings-dark','设置 · 深色','10-settings-dark','settings',true,'现有设置结构；使用一致的深色表面与次要文字。'],
 ['discover-dark','探索 · 深色','11-discover-dark','discover',true,'同一列表结构；降低标签视觉权重，突出作品标题。'],
 ['search','搜索 · 空关键词','12-search-dark','search',true,'保留输入框、提交、来源 Tab 与最近搜索，不填充推荐内容。'],
 ['search-results-dark','搜索结果 · 深色','13-search-results-dark','results',true,'保留现有封面网格、标题和作者；调整封面圆角和文字层级。'],
 ['search-results','搜索结果 · 浅色','14-search-results','results',false,'保留当前关键词、来源与结果网格，不添加标签或页数字段。'],
 ['reader','阅读器 · 全屏','15-reader','reader',false,'保持连续图片和零间距，工具栏隐藏时不增加浮层。'],
 ['reader-controls','阅读器 · 工具栏','16-reader-controls','reader-controls',false,'保留当前工具及位置；提高文字和图标在图片上的可读性。'],
 ['reading','阅读设置','17-reading-settings','reading',false,'保留所有现有设置项；整理标题、说明、选项和开关的层级。'],
 ['appearance','外观设置','18-appearance','appearance',false,'保留主题、纯黑和高刷新率三个设置，统一视觉样式。'],
].map(([id,label,shot,kind,dark,note]) => ({id,label,shot,kind,dark,note}));
// Official Phosphor paths shared with the Android resources.
const glyphs = ICON_LIBRARY;
const icon=(name,extra='')=>`<svg class="ic ${extra}" viewBox="0 0 256 256" aria-hidden="true">${glyphs[name]||glyphs.info}</svg>`;
const action=(name,label,target)=>target ? `<button class="icon-action" aria-label="${label}" data-page="${target}">${icon(name)}</button>` : `<span class="icon-action" role="img" aria-label="${label}">${icon(name)}</span>`;
const comics=[['雨后的第七站','青禾',192],['沿着海岸去旅行：在漫长的夏日遇见远方的风景','林间',216],['留给明天的一封信','南风',240],['城市里的小小花园','小满',264]];
function cover(i){return `<div class="cover" role="img" aria-label="安全测试封面"><svg viewBox="0 0 360 540"><rect width="360" height="540" fill="${['#dce6f0','#e8e1d4','#d9e7e1','#e6dceb'][i]}"/><circle cx="270" cy="110" r="54" fill="#526d84"/><path d="M0 330h360v210H0Z" fill="#a2b8bc"/><path d="M40 245h95v240H40Zm120 50h85v190h-85Z" fill="#708f9d"/><path d="M34 50h146M34 68h96" stroke="white" stroke-width="5"/></svg></div>`}
const tags=()=>'<div class="tags"><span class="tag">日常</span><span class="tag">旅行</span><span class="tag">短篇</span></div>';
function comicRow(i,extra='',showSource=true){
 const c=comics[i]; const tag=extra?'div':'button';
 return `<${tag} class="comic-row ${extra==='download'?'download-row':''}" ${extra?'':'data-page="detail"'}>${cover(i)}<div class="comic-text"><div><h3 class="comic-title">${c[0]}</h3><p class="author">${c[1]}</p>${tags()}</div><div class="comic-footer"><div class="count">共 ${c[2]} 张图片</div><div class="meta">${showSource?'picacg · ':''}ui-audit-${i} · 中文</div>${extra==='history'?`<div class="history-line"><span>读到第 7 页</span>${action('close',`删除 ${c[0]} 的历史`)}</div>`:''}${extra==='download'?'<div class="download-extra"><h4>第 1 话 · 出发</h4><div>已暂停 · 8 / 24 页</div><div class="progress" role="img" aria-label="已完成三分之一"><span></span></div><div class="download-actions"><span class="plain-action">继续下载</span><span class="plain-action">删除</span></div></div>':''}</div></div></${tag}>`;
}
function sourceTabs(){return `<div class="app-tabs" aria-label="当前来源 picacg">${['Hitomi','禁漫天堂','绅士漫画','nhentai','E-Hentai','picacg'].map(s=>`<span class="tab ${s==='picacg'?'selected':''}">${s}</span>`).join('')}</div>`}
function libraryTabs(kind){return `<div class="app-tabs library-tabs">${[['favorites','收藏'],['history','阅读历史'],['downloads','下载管理']].map(([id,t])=>`<button class="tab ${kind===id||kind==='history-empty'&&id==='history'?'selected':''}" data-page="${id}">${t}</button>`).join('')}</div>`}
function appTop(title,back,dark,trailing=''){return `<header class="app-top ${back?'has-back':''}">${back?action('back','返回',back):''}<h2>${title}</h2>${back?trailing:action('search','搜索',dark?'search':'search-results')+action('settings','设置',dark?'settings-dark':'settings')}</header>`}
function bottom(active,dark){return `<nav class="app-nav" aria-label="底部导航">${[['discover','explore','探索'],['categories','categories','分类'],['favorites','book','书架']].map(([id,g,t])=>`<button class="${active===id?'active':''}" data-page="${id==='discover'&&dark?'discover-dark':id}" aria-label="${t}"><span class="nav-pill">${icon(g)}</span></button>`).join('')}</nav>`}
const section=t=>`<h3 class="group-title">${t}</h3>`;
function row(title,subtitle='',g='',target='',value='',toggle=null){
 const tag=target?'button':'div';
 return `<${tag} class="setting-row" ${target?`data-page="${target}"`:''}>${g?icon(g):''}<div class="setting-copy"><strong>${title}</strong>${subtitle?`<small>${subtitle}</small>`:''}</div>${toggle!==null?`<span class="switch ${toggle===true?'on':''} ${toggle==='disabled'?'disabled':''}" aria-label="${toggle===true?'已开启':'已关闭'}"></span>`:value?`<span class="value">${value}</span>${icon('chevron','chevron')}`:icon('chevron','chevron')}</${tag}>`;
}
function settings(dark){return `<div class="settings-content">${section('内容与来源')}${row('账号管理','登录、会话与平台账号','user')}${row('漫画源','六个平台的专属偏好','explore')}${row('内容筛选','0 个屏蔽词 · 语言不限','filter')}${section('阅读体验')}${row('阅读','纵向连续','book','reading')}${row('外观',dark?'深色模式':'浅色模式','moon','appearance')}${section('APP')}${row('更新','GitHub Releases','refresh')}${row('数据与同步','下载偏好、缓存与备份','folder')}${row('日志','运行记录','menu')}${section('网络与关于')}${row('设置代理','跟随系统','wifi')}${row('关于 PiComic','介绍、项目地址与问题反馈','info')}</div>`}
function reading(){return `<div class="settings-content">${section('打开作品')}${row('跳过详情页','点击封面直接阅读；新作品从头开始，有历史记录时继续阅读','','','',false)}${section('翻页')}${row('阅读模式','','','','纵向连续')}${row('音量键翻页','音量减向后阅读，音量加向前阅读','','','',false)}${row('自动翻页时间间隔','','','','5 秒')}${section('显示与加载')}${row('阅读背景','','','','深灰')}${row('屏幕方向','','','','跟随系统')}${row('阅读亮度','数值为百分比，仅阅读页面生效','','','跟随系统')}${row('保持屏幕常亮','仅在前台阅读时生效','','','',true)}${row('图片预加载','持续预加载可见区域之后的图片，翻页不中断已开始的下载','','','3 张')}${section('缩放手势')}${row('双击缩放','双击放大，再次双击恢复','','','',true)}${row('长按缩放','按住临时放大，松开恢复','','','',false)}</div>`}
function detail(){return `<div class="detail-hero">${cover(0)}<div class="detail-copy"><h3>${comics[0][0]}</h3><div class="source-line"><span>picacg</span></div></div></div><div class="detail-actions">${action('heart','收藏作品')}${action('download','下载章节')}<button class="main-action" data-page="reader">开始阅读</button></div><section class="information"><h3>信息</h3>${[['ID','ui-audit-0'],['作者','青禾'],['标签','<span class="info-tags"><span>日常</span><span>旅行</span><span>短篇</span></span>'],['语言','中文'],['页数','192 页']].map(([a,b])=>`<div class="info-row"><span>${a}</span><span>${b}</span></div>`).join('')}<h4>简介</h4><p class="description">一段关于日常与远方的旅程。从熟悉的街角出发，记录沿途遇见的风景，也发现生活中被忽略的小小美好。</p></section><section class="chapters"><h3>章节 · 8</h3>${Array.from({length:8},(_,i)=>`<button class="chapter" data-page="reader"><span>第 ${i+1} 话 · ${['出发','沿途','相遇','远方'][i%4]}</span></button>`).join('')}</section>`}
function reader(controls){return `<div class="reader-scroll">${READER_PAGES.map((src,i)=>`<img src="${src}" alt="原创安全测试漫画，第 ${i+1} 页">`).join('')}</div>${controls?`<div class="reader-top">${action('back','退出阅读','detail')}<div class="reader-title">雨后的第七站<small>第 1 话 · 出发 · 纵向连续</small></div>${action('play','开始自动翻页')}${action('search','放大图片')}${action('settings','阅读设置')}</div><div class="reader-bottom"><div class="slider-row"><span>1</span><span class="slider" role="img" aria-label="第 1 页，共 6 页"></span><span>6</span></div><div class="reader-actions"><span class="plain-action disabled">上一话</span><span class="plain-action">${icon('menu')}目录</span><span class="plain-action">下一话</span></div></div>`:''}`}
function render(id){
 const view=views.find(v=>v.id===id)||views[0];const {kind,dark}=view;
 document.querySelector('#view-title').textContent=view.label;
 document.querySelector('#view-note').textContent=view.note;
 document.querySelector('#baseline').src=BASELINES[view.shot];
 document.querySelector('#baseline').alt=`本次原生截图：${view.label}`;
 document.querySelector('#shot-name').textContent=`ui-current / baseline / ${view.shot}.png`;
 document.querySelectorAll('#views button').forEach(b=>b.setAttribute('aria-current',String(b.dataset.page===view.id)));
 const phone=document.querySelector('#proposal');phone.className=`phone ${dark?'dark':''} ${kind.startsWith('reader')?'reader':''}`;phone.dataset.page=view.id;
 let head='',body='',nav='';
 if(kind.startsWith('reader')){phone.innerHTML=reader(kind==='reader-controls');return}
 if(kind==='discover'){head=appTop('探索',null,dark)+sourceTabs();body=`<div class="list">${comics.map((_,i)=>comicRow(i,'',false)).join('')}</div>`;nav=bottom('discover',dark)}
 else if(kind==='categories'){head=appTop('分类',null,dark)+sourceTabs();body='<div class="categories"><h3 class="section-label">分类</h3><div class="category-chips">'+['日常','旅行','短篇','自然','城市','校园','奇幻','冒险','科幻','悬疑','治愈','美食'].map(t=>`<span class="category-chip"><span class="category-chip-label">${t}</span></span>`).join('')+'</div></div>';nav=bottom('categories',dark)}
 else if(kind==='detail'||kind==='chapters'){head=appTop('作品详情','discover',dark,action('refresh','刷新详情'));body=detail()}
 else if(['favorites','history','history-empty','downloads'].includes(kind)){
  head=appTop('书架',null,dark)+libraryTabs(kind);nav=bottom('favorites',dark);
  if(kind==='favorites')body=`<div class="list">${comicRow(1)}${comicRow(0)}</div>`;
  if(kind==='history-empty')body=`<div class="empty">${icon('clock')}<h3>还没有阅读记录</h3><p>读过的作品会保存在这里。</p></div>`;
  if(kind==='history')body=`<div class="list"><div class="list-actions end"><span class="plain-action">清空阅读历史</span></div>${comicRow(0,'history')}</div>`;
  if(kind==='downloads')body=`<div class="list"><div class="list-actions"><span class="plain-action">暂停全部</span></div>${comicRow(1,'download')}</div>`;
 }
 else if(kind==='settings'){head=appTop('设置',dark?'discover-dark':'discover',dark);body=settings(dark)}
 else if(kind==='reading'){head=appTop('阅读','settings',dark);body=reading()}
 else if(kind==='appearance'){head=appTop('外观','settings',dark);body=`<div class="settings-content">${section('主题')}${row('主题模式','','','','浅色模式')}${row('纯黑色模式','深色模式下生效','','','','disabled')}${section('显示')}${row('高刷新率模式','优先请求设备支持的较高刷新率，仍受系统和省电影响','','','',false)}</div>`}
 else if(kind==='search'||kind==='results'){
  head=`<div class="search-input-row">${action('back','返回',dark?'discover-dark':'discover')}<input class="search-input" readonly aria-label="作品、作者或标签" placeholder="作品、作者或标签" value="${kind==='results'?'雨后':''}"><button class="plain-action" data-page="${dark?'search-results-dark':'search-results'}">搜索</button></div>`+sourceTabs();
  body=kind==='results'?`<div class="result-grid"><button class="result-card" data-page="detail">${cover(0)}<h3>${comics[0][0]}</h3><p>${comics[0][1]}</p></button></div>`:'<div class="search-heading"><span>最近搜索</span><span class="plain-action">清空</span></div>';
 }
 phone.innerHTML='<div class="status-space" aria-hidden="true"></div>'+head+'<div class="app-body">'+body+'</div>'+nav;
 const tabs=phone.querySelector('.app-tabs');if(tabs)tabs.scrollLeft=tabs.scrollWidth;
 if(kind==='chapters'){
  const first=phone.querySelector('.chapter');first.classList.add('current');first.insertAdjacentHTML('beforeend','<small>继续</small>');
  phone.querySelector('.main-action').textContent='继续阅读';
  const scroll=phone.querySelector('.app-body');scroll.scrollTop=scroll.scrollHeight;
 }
}
document.querySelector('#views').innerHTML=views.map((v,i)=>`<button data-page="${v.id}" aria-current="false"><span>${String(i+1).padStart(2,'0')}</span>${v.label}</button>`).join('');
document.addEventListener('click',event=>{const button=event.target.closest('button[data-page]');if(button)render(button.dataset.page)});
document.querySelector('#width').addEventListener('change',event=>document.documentElement.style.setProperty('--preview-width',event.target.value+'px'));
render('discover');
