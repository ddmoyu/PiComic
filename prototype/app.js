'use strict';
const $ = (s, root = document) => root.querySelector(s);
const $$ = (s, root = document) => [...root.querySelectorAll(s)];
const esc = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon = (name, cls = '') => `<svg class="ico ${cls}" aria-hidden="true"><use href="#i-${name}"></use></svg>`;
const ib = (name, action, label, value = '') => `<button class="icon-button" data-action="${action}" data-value="${esc(value)}" aria-label="${esc(label)}" title="${esc(label)}">${icon(name)}</button>`;
const sources = [
  {id:'picacg',name:'哔咔漫画',label:'picacg',short:'P',desc:'发现新的故事',auth:'password'},
  {id:'ehentai',name:'E-Hentai / EX',label:'e-hentai / exhentai',short:'EH',desc:'画廊与单行本',auth:'cookie'},
  {id:'jmcomic',name:'禁漫天堂',label:'jmcomic',short:'JM',desc:'章节与漫画收藏',auth:'password'},
  {id:'hitomi',name:'Hitomi',label:'hitomi',short:'H',desc:'画廊与标签索引',auth:'none'},
  {id:'htcomic',name:'绅士漫画',label:'htcomic · wnacg',short:'HT',desc:'按分类浏览作品',auth:'password'},
  {id:'nhentai',name:'nhentai',label:'nhentai',short:'N',desc:'画廊与 API 授权',auth:'key'}
];
const platformNames = {picacg:'picacg',ehentai:'EH / EX',jmcomic:'禁漫天堂',hitomi:'Hitomi',htcomic:'绅士漫画',nhentai:'nhentai'};
// Demo navigation vocabulary only; production categories come from each adapter.
const platformCategories = {
  picacg:[['快捷入口',['排行榜','推荐']],['分类',['全部','日常','奇幻','治愈','冒险','全彩','长篇','短篇','单行本','同人','中文','英文']]],
  ehentai:[['画廊类型',['全部','漫画','同人志','画师 CG','游戏 CG','图像集']],['语言标签',['中文','日文','英文']]],
  jmcomic:[['快捷入口',['最新更新','排行榜']],['作品分类',['全部','单行本','同人','短篇','连载','全彩']],['题材标签',['日常','奇幻','冒险']]],
  hitomi:[['作品类型',['全部','漫画','同人志','画师 CG','游戏 CG']],['浏览索引',['语言','作者','社团','系列']],['语言标签',['中文','日文','英文']]],
  htcomic:[['漫画分类',['全部','同人志','单行本','杂志','短篇','连载']],['内容标签',['中文','日文','全彩','日常']]],
  nhentai:[['浏览索引',['全部','标签','作者','社团','系列']],['语言标签',['中文','日文','英文']],['作品类型',['漫画','同人志']]]
};
const books = [
  {id:'0',title:'雨后的第七站',author:'青空工作室',category:'日常',chapters:8,pages:24,tag:'更新至第 8 话',description:'离开城市之前，她决定再坐一次那列慢车。一个关于雨、错过的站台，以及重新出发的故事。'},
  {id:'1',title:'月面邮差',author:'无声岛',category:'奇幻',chapters:6,pages:32,tag:'更新至第 6 话',description:'每一封未寄出的信，都会抵达月球。新来的邮差开始寻找最后一封信的收件人。'},
  {id:'2',title:'鲸落电台',author:'深蓝编辑部',category:'冒险',chapters:12,pages:28,tag:'更新至第 12 话',description:'在海底最后一座电台，一段来自遥远夏天的声音，让安静的城市重新开始呼吸。'},
  {id:'3',title:'森林事务所',author:'绿间',category:'治愈',chapters:5,pages:20,tag:'更新至第 5 话',description:'门牌藏在树叶之后。这里处理丢失的季节、迷路的风，以及一些微不足道的小心事。'},
  {id:'4',title:'昨日咖啡',author:'日光社',category:'日常',chapters:1,pages:36,tag:'全册 · 36 页',description:'只在昨天开门的咖啡店，今天迎来了第一位客人。'},
  {id:'5',title:'夏日航线',author:'千帆',category:'冒险',chapters:9,pages:24,tag:'更新至第 9 话',description:'带上一张旧地图，向着海风出发。夏天比我们想象的更长一点。'},
  {id:'6',title:'星星收集员',author:'木野',category:'奇幻',chapters:3,pages:22,tag:'更新至第 3 话',description:'她在天亮前，把落在屋顶的星星一颗颗收好。'},
  {id:'7',title:'风住的街角',author:'淡青',category:'治愈',chapters:4,pages:26,tag:'更新至第 4 话',description:'街角的花店记得所有来过这里的风。'},
  {id:'8',title:'蓝色远行',author:'云川',category:'冒险',chapters:1,pages:30,tag:'全册 · 30 页',description:'我们的旅程，从没有名字的海岸开始。'}
];
books.forEach((b,i)=>b.language= i===8?null:[1,5].includes(i)?'English':[2,6].includes(i)?'Japanese':'Chinese');
const defaults = () => ({preferences:preferenceDefaults(),source:'picacg',theme:'light',favorites:['picacg:1','picacg:3'],history:{'picacg:0':{page:4,chapter:1,time:Date.now()}},queries:['雨后','奇幻','日常'],browse:{},accounts:{},network:'system',proxy:{host:'127.0.0.1',port:'7890'},lines:{},ehSite:'EH',mode:'ltr',brightness:100,downloads:[]});
let state = defaults();
try {const saved = JSON.parse(localStorage.getItem('picomic-prototype-v1') || 'null');if(saved) state = {...state,...saved};}catch{}
state.preferences={...preferenceDefaults(),...state.preferences};
state.lines ||= {};
state.browse ||= {};
if(!sources.some(s => s.id === state.source)) state.source = 'picacg';
let route = 'discover', libraryTab = 'favorites', category = '全部', query = '', activeId = '0', chapter = 1, page = 1, zoom = 1, panX = 0, panY = 0, toolsShown = true, scenario = 'normal', sheetReturn = null, lastFocus = null, lastPageBeforeReader = 'detail', toastTimer;
const primaryRoutes = ['discover','categories','library'];
const routeStack = [], browseScroll = new Map();
function rememberBrowse(){state.browse[state.source]={category,query};}
function restoreBrowse(){const saved=state.browse[state.source]||{};category=saved.category||'全部';query=saved.query||'';}
restoreBrowse();
const persist = () => {try{localStorage.setItem('picomic-prototype-v1',JSON.stringify(state));}catch{}};
const source = () => sources.find(s => s.id === state.source);
const sourceLines = () => state.lines[state.source] || {api:1,image:1};
const book = () => books.find(b => b.id === activeId) || books[0];
const key = (id = activeId) => `${state.source}:${id}`;
const cover = id => `assets/cover-${id}.svg`;
const pageImage = p => `assets/page-${(p-1)%6+1}.svg`;
function toast(message){$('#toast').textContent=message;$('#toast').classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('#toast').classList.remove('show'),2500);}
function saveProgress(){if(route==='reader'){state.history[key()]={page,chapter,time:Date.now()};persist();}}
function go(next){readerGestureCleanup();saveProgress();if(next!=='reader')stopAutoTurn();if(route==='web-login'&&next!==route)stopWebLogin();closeSheet(false);if(primaryRoutes.includes(next))routeStack.length=0;else if(next!==route)routeStack.push(route);route=next;render();}
function back(){readerGestureCleanup();stopAutoTurn();if(route==='web-login')stopWebLogin();if(route==='reader'){saveProgress();route=lastPageBeforeReader;}else route=routeStack.pop()||'discover';closeSheet(false);render();}
function openBook(id, sourceId = state.source){saveProgress();rememberBrowse();state.source=sourceId;restoreBrowse();activeId=id;persist();go('detail');}
function selectSource(id, fromTab=false){const searchQuery=route==='search'?($('#search-input')?.value??query).trim():null;saveProgress();rememberBrowse();state.source=id;restoreBrowse();if(searchQuery!==null){query=searchQuery;rememberBrowse();}scenario='normal';$('#scenario').value='normal';closeSheet(false);persist();if(!fromTab){routeStack.length=0;route='discover';}render();if(fromTab)$('.platform-tab.selected')?.focus({preventScroll:true});}
function startRead(newChapter, from = route){stopAutoTurn();const previous=state.history[key()];chapter=newChapter || previous?.chapter || 1;page=newChapter ? 1 : previous?.page || 1;page=Math.min(page,book().pages);lastPageBeforeReader=from==='reader'?lastPageBeforeReader:from;zoom=1;panX=0;panY=0;toolsShown=true;route='reader';closeSheet(false);render();saveProgress();}
function setPage(p){resetAutoCountdown();page=Math.max(1,Math.min(book().pages,Number(p)));zoom=1;panX=0;panY=0;saveProgress();renderReader();}
function stepPage(delta){if(page+delta>book().pages){toast(chapter<book().chapters?'本章已结束，可在底栏进入下一章':'已经读到最后一页');return;}setPage(page+delta);}
function header(title, right=''){return `<header class="topbar">${ib('arrow','back','返回')}<h2>${title}</h2>${right}</header>`;}
function mainHeader(title){return `<header class="topbar main-topbar"><h2>${title}</h2>${ib('search','navigate','搜索','search')}${ib('settings','navigate','设置','settings')}</header>`;}
function platformTabs(){return `<div class="platform-tabs" role="tablist" aria-label="${route==='search'?'搜索漫画源':route==='categories'?'分类平台':'探索平台'}">${sources.map(s=>`<button id="platform-${s.id}" class="platform-tab ${state.source===s.id?'selected':''}" role="tab" aria-selected="${state.source===s.id}" aria-controls="platform-panel" tabindex="${state.source===s.id?'0':'-1'}" data-action="platform" data-value="${s.id}">${platformNames[s.id]}</button>`).join('')}</div>`;}
function platformBody(){return `<main id="platform-panel" class="page-body" role="tabpanel" aria-labelledby="platform-${state.source}" tabindex="0">`;}
function nav(){const items=[['discover','compass','探索'],['categories','grid','分类'],['library','book','书架']];return `<nav class="bottomnav" aria-label="主导航">${items.map(([id,i,t])=>`<button class="nav-item ${route===id?'selected':''}" data-action="navigate" data-value="${id}" aria-label="${t}" title="${t}" ${route===id?'aria-current="page"':''}><span class="nav-icon">${icon(i)}</span></button>`).join('')}</nav>`;}
function card(b){return `<button class="comic-card" data-action="detail" data-value="${b.id}" aria-label="查看${b.title}"><div class="cover"><img src="${cover(b.id)}" alt="${b.title}示意封面"><span class="cover-badge">${b.chapters>1?`${b.chapters} 话`:'全册'}</span></div><span class="card-title">${b.title}</span><span class="card-sub">${b.author}</span></button>`;}
const grid = list => {const visible=filteredBooks(list);return `${visible.length!==list.length?'<p class="result-note">已应用内容筛选</p>':''}${visible.length?`<div class="grid">${visible.map(card).join('')}</div>`:empty('empty')}`;};
function empty(kind = scenario){const map={empty:['search','没有找到相关作品','换个关键词，或试试其他分类。','重新搜索','navigate','search'],offline:['wifi','暂时连接不上','请检查系统代理或当前线路。已下载的漫画仍可阅读。','网络与线路','navigate','network'],expired:['user','登录已过期','当前阅读位置已保存，重新登录后即可继续。','重新登录','login',state.source],quota:['info','图片额度暂时用尽','已暂停加载更多图片。稍后再试，或阅读已下载内容。','查看下载','downloads',''],favorites:['heart','书架等你填满','把喜欢的作品加入书架，下次轻松找到。','去探索','navigate','discover'],history:['clock','还没有阅读记录','打开一本漫画，故事从第一页开始。','去探索','navigate','discover'],downloads:['download','把故事带在身边','在作品详情下载章节，离线也能继续阅读。','去探索','navigate','discover']};const [i,t,d,b,a,v]=map[kind]||map.empty;return `<div class="empty-state"><div class="empty-icon">${icon(i)}</div><h3>${t}</h3><p>${d}</p><button class="primary-button" data-action="${a}" data-value="${v}">${b}</button>${['offline','expired','quota'].includes(kind)?'<button class="text-button" data-action="recover">恢复正常演示</button>':''}</div>`;}
function render(){document.body.classList.toggle('dark',state.theme==='dark');document.body.classList.toggle('pure-black',state.theme==='dark'&&prefs().pureBlack);$('#device').classList.toggle('reading',route==='reader');if(route==='reader'){renderReader();return;}let content='';
  if(route==='discover')content=renderDiscover();
  else if(route==='categories')content=renderCategories();
  else if(route==='category-results')content=renderCategoryResults();
  else if(route==='search')content=renderSearch();
  else if(route==='detail')content=renderDetail();
  else if(route==='library')content=renderLibrary();
  else if(route==='accounts')content=renderAccounts();
  else if(route==='web-login')content=renderWebLogin();
  else if(route==='network')content=renderNetwork();
  else if(route==='settings')content=renderSettings();
  else if(route==='updates')content=renderUpdates();
  else content=renderSettingsRoute()||'';
  $('#app').innerHTML=content+(['discover','categories','library'].includes(route)?nav():'');
  if($('.preferences-page')){const body=$('.page-body'),k=route+':'+settingsSource;body.scrollTop=settingsScroll.get(k)||0;body.addEventListener('scroll',()=>settingsScroll.set(k,body.scrollTop),{passive:true});const tab=$('.source-pref-tabs .selected');tab?.scrollIntoView({block:'nearest',inline:'nearest'});}
  if(route==='accounts'){const body=$('.page-body');body.scrollTop=accountScroll;body.addEventListener('scroll',()=>accountScroll=body.scrollTop,{passive:true});}
  if(route==='search')$('.platform-tab.selected')?.scrollIntoView({block:'nearest',inline:'nearest'});
  if(['discover','categories'].includes(route)){
    const body=$('.page-body'),scrollKey=`${route}:${state.source}`;
    body.scrollTop=browseScroll.get(scrollKey)||0;
    body.addEventListener('scroll',()=>browseScroll.set(scrollKey,body.scrollTop),{passive:true});
    const tabs=$('.platform-tabs'),selected=$('.platform-tab.selected');
    if(selected.offsetLeft+selected.offsetWidth>tabs.clientWidth)tabs.scrollLeft=selected.offsetLeft+selected.offsetWidth-tabs.clientWidth+18;
  }
}
function renderDiscover(){
  return `${mainHeader('探索')}${platformTabs()}${platformBody()}${scenario!=='normal'?empty():`
    <div class="discover-feed">${grid(books)}</div>
    <div class="notice">${icon('info')}<span>${source().name} · 原创示意作品，非平台抓取结果</span></div>`}</main>`;
}
function renderCategories(){return `${mainHeader('分类')}${platformTabs()}${platformBody()}${scenario!=='normal'?empty():`<h1 class="platform-heading">${source().name}</h1>${platformCategories[state.source].map(([heading,items])=>`<section class="category-section"><h3>${heading}</h3><div class="category-chips">${items.map(c=>`<button class="category-chip" data-action="category" data-value="${c}">${c}</button>`).join('')}</div></section>`).join('')}<p class="outline-note category-note">分类为该平台的交互示意，实际栏目以接入后返回的数据为准。</p>`}</main>`;}
function renderCategoryResults(){
  const generic=['日常','奇幻','治愈','冒险'].includes(category),list=generic?books.filter(b=>b.category===category):category==='排行榜'?[...books].reverse():category==='推荐'?books.filter((_,i)=>i%2===0):books;
  return `${header(category,ib('search','navigate','搜索','search'))}<main class="page-body"><p class="result-note">${source().name} · ${category} · 演示结果</p>${scenario!=='normal'?empty():grid(list)}</main>`;
}
function renderSearch(){
 const found=filteredBooks(books.filter(b=>`${b.title} ${b.author} ${b.category}`.toLocaleLowerCase().includes(query.toLocaleLowerCase())));
 const history=`<section class="search-history"><div class="section-heading"><h3>最近搜索</h3>${state.queries.length?'<button class="text-button" data-action="clear-queries">清空</button>':''}</div>${state.queries.length?`<div class="pill-list">${state.queries.map(q=>`<button class="history-query" data-action="query" data-value="${esc(q)}">${esc(q)}</button>`).join('')}</div>`:'<p class="search-hint">暂无搜索记录</p>'}</section><p class="search-hint">选择漫画源，搜索作品、作者或标签。</p>`;
 const results=scenario!=='normal'?empty():found.length?`<div class="section-heading"><h3>搜索结果</h3><span class="muted search-count">${found.length} 部作品</span></div>${grid(found)}`:empty('empty');
 return `<header class="topbar search-topbar" aria-label="搜索">${ib('arrow','back','返回')}<form class="search-form" id="search-form" role="search"><div class="search-box">${icon('search')}<input id="search-input" type="search" enterkeyhint="search" placeholder="作品、作者或标签" aria-label="搜索作品、作者或标签" value="${esc(query)}" autocomplete="off"><button class="search-clear" type="button" data-action="clear-search" aria-label="清空关键词" ${query?'':'hidden'}>${icon('close')}</button></div><button class="text-button search-submit" type="submit">搜索</button></form></header>${platformTabs()}${platformBody()}<p class="result-note">${source().name} · ${query?'搜索结果':'当前漫画源'} · 演示内容</p>${query?results:history}</main>`;
}
function renderDetail(){const b=book(),saved=state.favorites.includes(key()),h=state.history[key()];return `${header('作品详情',ib('settings','detail-more','更多作品信息'))}<main class="page-body"><div class="detail-hero"><img class="detail-cover" src="${cover(b.id)}" alt="${b.title}封面"><div class="detail-meta"><span class="source-badge">${source().name}</span><h1 style="margin-top:10px">${b.title}</h1><p>${b.author}</p><p>${b.chapters>1?`连载中 · ${b.chapters} 话`:`全册 · ${b.pages} 页`}</p><p>示意作品 · ${b.language||'未标注语言'}</p></div></div><div class="detail-actions"><button class="primary-button" data-action="read">${icon('book')}${h?'继续阅读':'开始阅读'}</button><button class="square-action ${saved?'saved':''}" data-action="favorite" aria-label="${saved?'取消收藏':'收藏作品'}" aria-pressed="${saved}">${icon('heart')}</button><button class="square-action" data-action="download-sheet" aria-label="下载章节">${icon('download')}</button></div>${h?`<p class="section-sub">上次读到：第 ${h.chapter} 话 · 第 ${h.page} 页</p>`:''}<div class="tags">${[b.category,b.language||'未标注语言','原创示意'].map(t=>`<button data-action="query" data-value="${t}">${t}</button>`).join('')}</div><p class="description">${b.description}</p><div class="section-heading"><h3>目录 <span class="muted" style="font-size:12px;font-weight:400">${b.chapters>1?`${b.chapters} 话`:'全册'}</span></h3><span class="muted" style="font-size:11px">正序</span></div>${Array.from({length:b.chapters},(_,i)=>`<button class="chapter-row ${h?.chapter===i+1?'current':''}" data-action="read-chapter" data-value="${i+1}"><span class="chapter-number">${String(i+1).padStart(2,'0')}</span><span class="chapter-text">${b.chapters===1?'全册':`第 ${i+1} 话 · ${['雨停之前','慢车进站','窗外的光','一封来信','再见，昨天','新的方向','旅途之中','第七站'][i%8]}`}<small>${h?.chapter===i+1?`正在阅读 · 第 ${h.page} 页`:`${b.pages} 页`}</small></span>${icon(h?.chapter===i+1?'book':'next')}</button>`).join('')}</main>`;}
function renderLibrary(){let body='';if(scenario==='empty')body=empty(libraryTab);else if(libraryTab==='favorites'){const entries=state.favorites;body=entries.length?`<p class="result-note">${entries.length} 部收藏 · 保存在本机</p><div class="grid">${entries.map(k=>{const [s,id]=k.split(':');const b=books.find(b=>b.id===id);return b?card(b).replace('data-action="detail"',`data-source="${s}" data-action="detail"`):'';}).join('')}</div>`:empty('favorites');}else if(libraryTab==='history'){const entries=Object.entries(state.history).sort((a,b)=>b[1].time-a[1].time);body=entries.length?entries.map(([k,h])=>{const [s,id]=k.split(':'),b=books.find(b=>b.id===id);return `<button class="history-item" data-action="resume" data-value="${k}"><img src="${cover(id)}" alt="${b.title}封面"><span class="text"><strong>${b.title}</strong><small>${sources.find(x=>x.id===s).name} · 第 ${h.chapter} 话</small><small>第 ${h.page} / ${b.pages} 页</small></span>${icon('play')}</button>`;}).join(''):empty('history');}else body=renderDownloads();return `${mainHeader('书架')}<div class="tabs" role="tablist" aria-label="书架分类">${[['favorites','收藏'],['history','阅读历史'],['downloads','下载管理']].map(([id,t])=>`<button class="tab ${libraryTab===id?'selected':''}" role="tab" aria-selected="${libraryTab===id}" data-action="library-tab" data-value="${id}">${t}</button>`).join('')}</div><main class="page-body">${body}</main>`;}
function renderDownloads(){
  if(!state.downloads.length)return empty('downloads');
  const items=state.downloads.map(d=>{
    const b=books.find(b=>b.id===d.book),done=d.done>=12;
    return `<div class="download-item"><img src="${cover(b.id)}" alt="${b.title}封面"><div class="text"><strong>${b.title}</strong><small>第 ${d.chapter} 话 · ${done?'已完成':d.paused?'已暂停':'下载中（演示）'}</small><progress value="${d.done}" max="12" aria-label="${b.title}下载进度"></progress><small>${d.done} / 12 页 · ${done?'可离线阅读（演示）':Math.round(d.done/12*100)+'%'}</small></div><div class="download-controls">${done?ib('book','read-download','阅读已下载章节',d.id):ib(d.paused?'play':'pause','toggle-download',d.paused?'继续下载':'暂停下载',d.id)}${ib('trash',done?'delete-download':'cancel-download',done?'删除下载':'取消下载',d.id)}</div></div>`;
  }).join('');
  return `<p class="section-sub">模拟下载队列 · 不进行网络下载</p>${items}`;
}
function row(i,title,sub,action,value='',extra=''){return `<button class="settings-row" data-action="${action}" data-value="${esc(value)}">${icon(i)}<span class="row-text"><strong>${title}</strong>${sub?`<small>${sub}</small>`:''}</span>${extra||icon('next','chevron')}</button>`;}
function renderNetwork(){return `${header('网络与线路')}<main class="page-body"><div class="notice">${icon('wifi')}<span>默认跟随系统网络，支持系统 VPN / 代理。PiComic 不提供代理节点。</span></div><div class="settings-label">连接方式</div><div class="settings-group" style="padding:4px 14px">${[['system','跟随系统（推荐）','使用系统网络、VPN 或系统代理'],['http','自定义 HTTP 代理','通过指定的主机和端口连接']].map(([id,t,s])=>`<button class="radio-row ${state.network===id?'selected':''}" data-action="network-mode" data-value="${id}" role="radio" aria-checked="${state.network===id}"><span class="radio-dot"></span><span>${t}<small>${s}</small></span></button>`).join('')}</div>${state.network==='http'?`<form id="proxy-form"><div class="form-field"><label for="proxy-host">代理主机（演示）</label><input id="proxy-host" name="host" value="${esc(state.proxy.host)}" autocomplete="off" required></div><div class="form-field"><label for="proxy-port">端口</label><input id="proxy-port" name="port" type="number" min="1" max="65535" value="${esc(state.proxy.port)}" required></div><div class="form-field"><label for="proxy-user">代理用户名（可选，仅当前表单）</label><input id="proxy-user" autocomplete="off" placeholder="不填写真实账号"></div><div class="form-field"><label for="proxy-password">代理密码（可选，不保存）</label><input id="proxy-password" type="password" autocomplete="off" placeholder="仅使用演示值"></div><button type="submit" class="secondary-button full-width">保存演示配置</button></form>`:''}<div class="settings-label">当前来源 · ${source().name}</div><div class="settings-group">${row('compass','API 线路',`线路 ${sourceLines().api} · 尚未真实检测`,'line','api')}${row('grid','图片线路',`线路 ${sourceLines().image} · 尚未真实检测`,'line','image')}${state.source==='ehentai'?row('book','访问站点',state.ehSite,'eh-site'):''}</div><button class="primary-button full-width" data-action="test-network">${icon('refresh')}模拟连接检测</button><p class="outline-note">此页面仅演示设置。真实 App 的 API、图片、下载和网页登录需统一网络策略；代理失败时不会自动切为直连。</p></main>`;}
function modeOptions(){return [['ltr','从左向右'],['rtl','从右向左'],['scroll','纵向连续']].map(([id,t])=>`<button class="radio-row ${state.mode===id?'selected':''}" data-action="reading-mode" data-value="${id}" role="radio" aria-checked="${state.mode===id}"><span class="radio-dot"></span>${t}</button>`).join('');}
function renderReader(){const b=book();$('#app').innerHTML=`<main class="reader ${toolsShown?'':'reader-tools-hidden'}" style="--brightness:${state.brightness/100}"><div class="reader-top">${ib('arrow','back','退出阅读')}<div class="reader-title"><strong>${b.title}</strong><small>第 ${chapter} 话 · ${state.mode==='rtl'?'从右向左':state.mode==='scroll'?'纵向连续':'从左向右'}</small></div>${ib(autoTurning?'pause':'play','auto-turn',autoTurning?'停止自动翻页':'开始自动翻页')}${ib('zoom','zoom','切换图片缩放')}${ib('settings','reader-settings','阅读设置')}</div>${scenario!=='normal'&&scenario!=='empty'?empty():`<div class="reader-stage ${state.mode==='scroll'?'reader-scroll':''} ${zoom>1?'zoomed':''}" id="reader-stage" aria-label="漫画画面，点击中间隐藏工具栏，双击缩放">${state.mode==='scroll'?'<div class="reader-pages">'+Array.from({length:b.pages},(_,i)=>`<figure data-page="${i+1}"><img src="${pageImage(i+1)}" alt="${b.title}第 ${i+1} 页示意漫画" width="640" height="930" loading="lazy" draggable="false"></figure>`).join('')+'</div>':`<img id="reader-image" src="${pageImage(page)}" alt="${b.title}第 ${page} 页示意漫画" style="transform:translate(${panX}px,${panY}px) scale(${zoom})">`}</div><span class="reader-page-indicator" id="page-badge" ${state.mode==='scroll'?'hidden':''}>${page} / ${b.pages}</span>`}<div class="reader-edge-feedback" role="status"><i aria-hidden="true"></i><span>继续上拉进入下一章</span></div><div class="reader-end-hint" role="status">已经是最后一章了</div><div class="reader-bottom"><div class="reader-progress"><span id="page-number">${page}</span><input id="page-slider" type="range" min="1" max="${b.pages}" value="${page}" aria-label="阅读页码"><span>${b.pages}</span></div><div class="reader-controls"><button class="previous" data-action="prev-chapter" ${chapter===1?'disabled':''}>${icon('next')}上一话</button><button data-action="reader-chapters">${icon('menu')}目录</button><button data-action="next-chapter" ${chapter>=b.chapters?'disabled':''}>下一话${icon('next')}</button></div></div></main>`;bindReader();if(state.mode==='scroll'){$('#reader-stage')?.querySelector(`[data-page="${page}"]`)?.scrollIntoView({block:'start'});}}
let readerGestureCleanup=()=>{};
function bindReader(){
  readerGestureCleanup();const stage=$('#reader-stage');if(!stage)return;
  if(state.mode==='scroll'){readerGestureCleanup=bindContinuousReader(stage);return;}
  let down=null,moved=false,hold=null,tap=null,holding=false,previousZoom=1;
  const transform=()=>{const img=$('#reader-image');if(img)img.style.transform=`translate(${panX}px,${panY}px) scale(${zoom})`;};
  const releaseHold=()=>{clearTimeout(hold);if(holding){zoom=previousZoom;holding=false;transform();return true;}return false;};
  readerGestureCleanup=()=>{releaseHold();clearTimeout(tap);down=null;readerInteracting=false;};
  stage.addEventListener('pointerdown',e=>{readerInteracting=true;down={x:e.clientX,y:e.clientY,px:panX,py:panY};moved=false;if(state.mode!=='scroll'){stage.setPointerCapture(e.pointerId);if(prefs().longPress)hold=setTimeout(()=>{if(down&&!moved){holding=true;previousZoom=zoom;zoom=2.5;transform();}},450);}});
  stage.addEventListener('pointermove',e=>{if(!down)return;const dx=e.clientX-down.x,dy=e.clientY-down.y;if(Math.abs(dx)+Math.abs(dy)>12){moved=true;if(!holding)clearTimeout(hold);}if(zoom>1&&state.mode!=='scroll'&&!holding){panX=Math.max(-stage.clientWidth/2,Math.min(stage.clientWidth/2,down.px+dx));panY=Math.max(-stage.clientHeight/2,Math.min(stage.clientHeight/2,down.py+dy));transform();}});
  stage.addEventListener('pointerup',e=>{readerInteracting=false;resetAutoCountdown();if(!down)return;const dx=e.clientX-down.x,dy=e.clientY-down.y;down=null;if(releaseHold())return;if(state.mode==='scroll'){if(!moved)toggleTools();return;}if(zoom>1)return;if(Math.abs(dx)>45&&Math.abs(dx)>Math.abs(dy)){stepPage((dx<0?1:-1)*(state.mode==='rtl'?-1:1));}else if(!moved){const r=stage.getBoundingClientRect(),x=(e.clientX-r.left)/r.width;clearTimeout(tap);tap=setTimeout(()=>{if(route!=='reader')return;if(x<.25)stepPage(state.mode==='rtl'?1:-1);else if(x>.75)stepPage(state.mode==='rtl'?-1:1);else toggleTools();},250);}});
  stage.addEventListener('pointercancel',()=>{readerInteracting=false;resetAutoCountdown();releaseHold();down=null;});
  stage.addEventListener('dblclick',()=>{clearTimeout(tap);if(prefs().doubleTap&&state.mode!=='scroll'){zoom=zoom===1?2:1;panX=panY=0;renderReader();}});
  if(state.mode==='scroll')stage.addEventListener('scroll',()=>{const rect=stage.getBoundingClientRect();let closest=1;for(const f of $$('figure',stage)){if(f.getBoundingClientRect().top<=rect.top+100)closest=Number(f.dataset.page);}page=closest;$('#page-number').textContent=page;$('#page-slider').value=page;$('#page-badge').textContent=`${page} / ${book().pages}`;saveProgress();},{passive:true});
}
function toggleTools(){toolsShown=!toolsShown;$('.reader')?.classList.toggle('reader-tools-hidden',!toolsShown);}
function sheet(title,body){$('#reader-stage')?.dispatchEvent(new Event('reader-interrupt'));closeSheet(false);lastFocus=document.activeElement;$('#app').inert=true;$('#overlay-root').innerHTML=`<div class="sheet-backdrop"><section class="sheet" role="dialog" aria-modal="true" aria-labelledby="sheet-title"><div class="sheet-handle"></div><div class="sheet-head"><h2 id="sheet-title">${title}</h2>${ib('close','close','关闭面板')}</div>${body}</section></div>`;$('.sheet .icon-button')?.focus();}
function closeSheet(restore=true){$('#overlay-root').innerHTML='';$('#app').inert=false;if(restore&&lastFocus?.isConnected)lastFocus.focus();}

function readerSettings(){sheet('阅读设置',`${modeOptions()}<div class="range-row"><label for="brightness">阅读亮度</label><input id="brightness" type="range" min="40" max="100" value="${state.brightness}"><output id="brightness-value">${state.brightness}%</output></div><p class="outline-note">双击图片缩放；点击中间显示/隐藏工具栏。当前为交互演示，原生大图与多指手势另行验证。</p>`);}
function downloadSheet(){const b=book();sheet('下载章节',`<p class="sheet-sub">${b.title} · 模拟下载，不请求网络</p><form id="download-form"><fieldset><legend>选择要下载的章节</legend>${Array.from({length:b.chapters},(_,i)=>`<label class="check-row"><input name="chapter" type="checkbox" value="${i+1}" ${i===0?'checked':''}>${b.chapters===1?'全册':`第 ${i+1} 话`} <span class="muted">· ${b.pages} 页</span></label>`).join('')}</fieldset><button class="primary-button full-width" type="submit">加入演示下载队列</button></form>`);}
function lineSheet(type){const current=sourceLines()[type];sheet(type==='api'?'API 线路':'图片线路',`<p class="sheet-sub">${source().name} · 演示候选，不显示未经验证的真实主机。</p>${[1,2,3].map(i=>`<button class="radio-row ${current===i?'selected':''}" data-action="choose-line" data-value="${type}:${i}"><span class="radio-dot"></span><span>线路 ${i}<small>${i===1?'默认候选':'备用候选'} · 尚未真实检测</small></span></button>`).join('')}`);}
function confirmSheet(title,text,action,value){sheet(title,`<p class="sheet-sub">${text}</p><div class="sheet-footer"><button class="secondary-button" data-action="close">取消</button><button class="primary-button" data-action="${action}" data-value="${value}">确认</button></div>`);}
document.addEventListener('click',e=>{if(e.target.classList.contains('sheet-backdrop')){closeSheet();return;}const el=e.target.closest('[data-action]');if(!el||el.disabled)return;const a=el.dataset.action,v=el.dataset.value;
  if(a==='navigate'){go(v);if(v==='search')$('#search-input')?.focus();}
  else if(a==='back')back();
  else if(a==='close')closeSheet();
  else if(a==='platform')selectSource(v,true);
  else if(a==='source')selectSource(v);
  else if(a==='detail')openBook(v,el.dataset.source||state.source);
  else if(a==='demo-detail')openBook('0');
  else if(a==='demo-read'){activeId='0';scenario='normal';startRead(null,'detail');}
  else if(a==='category'){category=v;rememberBrowse();persist();closeSheet(false);go('category-results');}
  else if(a==='clear-search'){query='';rememberBrowse();persist();render();$('#search-input').focus();}
  else if(a==='query'){query=v;rememberBrowse();state.queries=[query,...state.queries.filter(x=>x!==query)].slice(0,8);persist();go('search');}
  else if(a==='clear-queries'){state.queries=[];persist();render();toast('已清空保存的搜索历史');}
  else if(a==='favorite'){const k=key(),exists=state.favorites.includes(k);state.favorites=exists?state.favorites.filter(x=>x!==k):[k,...state.favorites];persist();render();toast(exists?'已从本地书架移除':'已加入本地书架');}
  else if(a==='read')startRead();
  else if(a==='read-chapter')startRead(Number(v));
  else if(a==='resume'){const [s,id]=v.split(':');state.source=s;activeId=id;startRead(null,'library');}
  else if(a==='downloads'){libraryTab='downloads';go('library');}
  else if(a==='library-tab'){libraryTab=v;render();}
  else if(a==='download-sheet')downloadSheet();
  else if(a==='toggle-download'){const d=state.downloads.find(x=>x.id===v);if(d)d.paused=!d.paused;persist();render();}
  else if(a==='cancel-download')confirmSheet('取消下载？','将移除这个演示任务，不影响书架和阅读记录。','remove-download',v);
  else if(a==='delete-download')confirmSheet('删除已下载章节？','演示文件状态将移除，本地收藏和历史仍然保留。','remove-download',v);
  else if(a==='remove-download'){state.downloads=state.downloads.filter(d=>d.id!==v);closeSheet(false);persist();render();toast('已移除演示任务');}
  else if(a==='read-download'){const d=state.downloads.find(x=>x.id===v);state.source=d.source;activeId=d.book;startRead(d.chapter,'library');}
  else if(a==='login')loginSheet(v);
  else if(a==='relogin'){if(accountInfo(v)?.method==='web')startWebLogin(v);else loginSheet(v);}
  else if(a==='complete-web-demo')simulateWebCompletion();
  else if(a==='retry-web-login'){webLoginAttempt.phase='waiting';render();}
  else if(a==='logout'){delete state.accounts[v];persist();closeSheet(false);render();toast('已退出演示登录');}
  else if(a==='web-login')startWebLogin(v);

  else if(a==='network-mode'){state.network=v;persist();render();}
  else if(a==='line')lineSheet(v);
  else if(a==='choose-line'){const [type,num]=v.split(':');state.lines[state.source]={...sourceLines(),[type]:Number(num)};persist();closeSheet(false);render();toast('已切换演示线路');}
  else if(a==='eh-site'){state.ehSite=state.ehSite==='EH'?'EX':'EH';persist();render();toast(`已选择 ${state.ehSite}（演示，权限未实测）`);}
  else if(a==='test-network')sheet('连接检测 · 模拟结果',`<p class="sheet-sub">未发送网络请求；以下仅演示检测结果的呈现方式。</p><div class="settings-group">${row('check','API 可访问','模拟响应正常','close')}${row('check','图片线路可访问','模拟图片加载正常','close')}${row('user','会话需要登录','真实结果以来源验证为准','login',state.source)}</div><button class="primary-button full-width" data-action="close">完成</button>`);
  else if(a==='theme'){state.theme=state.theme==='light'?'dark':'light';persist();render();}
  else if(a==='reader-settings')readerSettings();
  else if(a==='reading-mode'){saveProgress();state.mode=v;persist();if(route==='reader'){renderReader();readerSettings();}else{closeSheet(false);render();}}
  else if(a==='zoom'){if(state.mode==='scroll'){toast('连续模式用滚动阅读；放大交互请切换逐页模式');return;}zoom=zoom===1?2:1;panX=panY=0;renderReader();}
  else if(a==='next-chapter'){if(chapter<book().chapters){chapter++;setPage(1);}}
  else if(a==='prev-chapter'){if(chapter>1){chapter--;setPage(1);}}
  else if(a==='reader-chapters')sheet('阅读目录',Array.from({length:book().chapters},(_,i)=>`<button class="chapter-row ${chapter===i+1?'current':''}" data-action="read-chapter" data-value="${i+1}"><span class="chapter-text">${book().chapters===1?'全册':`第 ${i+1} 话`}</span>${chapter===i+1?icon('check'):icon('next')}</button>`).join(''));
  else if(a==='detail-more')sheet('作品信息',`<p class="sheet-sub">${book().title}是为 PiComic 原型创作的示意作品。封面与阅读页面均为本地 SVG，无真实平台内容。</p><button class="primary-button full-width" data-action="close">了解了</button>`);
  else if(a==='update-check')checkUpdateDemo();
  else if(a==='update-download')downloadUpdateDemo();
  else if(a==='update-resume')downloadUpdateDemo(true);
  else if(a==='update-pause')pauseUpdateDemo();
  else if(a==='update-cancel')confirmSheet('取消更新下载？','将移除这个演示更新任务，当前应用版本不变。','update-confirm-cancel','');
  else if(a==='update-confirm-cancel')cancelUpdateDemo();
  else if(a==='update-install')installUpdateDemo();
  else if(a==='update-permission-demo')installerPreview();
  else if(a==='update-installer-demo'){updateDemo.phase='install-pending';closeSheet(false);render();}
  else if(a==='update-reinstall'){updateDemo.phase='ready';render();}
  else if(a==='update-release-page')releasePreview();
  else if(a==='recover'){scenario='normal';$('#scenario').value='normal';render();}
  else if(a==='fluid'){document.body.classList.toggle('fluid');toast('自适应预览 · 按 Esc 返回手机评审框');}
  else if(a==='reset')confirmSheet('重置演示状态？','收藏、历史、演示账号和设置将恢复初始值。','confirm-reset','');
  else if(a==='confirm-reset'){stopAutoTurn();settingsScroll.clear();cacheUsed=222.18;state=defaults();resetUpdateDemo();accountScroll=0;stopWebLogin();restoreBrowse();browseScroll.clear();scenario='normal';$('#scenario').value='normal';persist();go('discover');toast('演示状态已重置');}
});
document.addEventListener('submit',e=>{e.preventDefault();if(e.target.id==='search-form'){query=$('#search-input').value.trim();if(query)state.queries=[query,...state.queries.filter(x=>x!==query)].slice(0,8);rememberBrowse();persist();render();}else if(e.target.id==='login-form'){const id=e.target.dataset.source;commitDemoSession(id,sources.find(s=>s.id===id).auth);closeSheet(false);render();toast('登录成功（模拟，未保存输入的凭据）');}else if(e.target.id==='proxy-form'){const host=$('#proxy-host').value.trim(),port=Number($('#proxy-port').value);if(!host||port<1||port>65535||!Number.isInteger(port)){toast('请输入主机和 1–65535 的端口');return;}state.proxy={host,port:String(port)};persist();render();toast('已保存演示代理配置，未改变浏览器网络');}else if(e.target.id==='download-form'){const selected=$$('input[name="chapter"]:checked',e.target).map(x=>Number(x.value));if(!selected.length){toast('请至少选择一个章节');return;}for(const ch of selected){const id=`${key()}:${ch}`;if(!state.downloads.some(d=>d.id===id))state.downloads.push({id,source:state.source,book:activeId,chapter:ch,done:0,paused:false});}persist();closeSheet(false);libraryTab='downloads';go('library');toast('已加入演示下载队列');}});
document.addEventListener('input',e=>{if(e.target.id==='search-input'){$('.search-clear').hidden=!e.target.value;}else if(e.target.id==='page-slider'){const newPage=Number(e.target.value);if(state.mode==='scroll'){$('#reader-stage figure[data-page="'+newPage+'"]')?.scrollIntoView({block:'start'});page=newPage;saveProgress();}else setPage(newPage);}else if(e.target.id==='brightness'){state.brightness=Number(e.target.value);$('#brightness-value').textContent=`${state.brightness}%`;$('.reader')?.style.setProperty('--brightness',state.brightness/100);persist();}});
$('#scenario').addEventListener('change',e=>{scenario=e.target.value;render();});
document.addEventListener('keydown',e=>{const dialog=$('.sheet');if(dialog){if(e.key==='Escape'){closeSheet();e.preventDefault();}else if(e.key==='Tab'){const focusable=$$('button:not(:disabled),input,textarea,select,a[href]',dialog),first=focusable[0],last=focusable.at(-1);if(e.shiftKey&&document.activeElement===first){last.focus();e.preventDefault();}else if(!e.shiftKey&&document.activeElement===last){first.focus();e.preventDefault();}}return;}const platform=document.activeElement?.closest('.platform-tab');if(platform&&['ArrowLeft','ArrowRight','Home','End'].includes(e.key)){e.preventDefault();const index=sources.findIndex(s=>s.id===platform.dataset.value),next=e.key==='Home'?0:e.key==='End'?sources.length-1:(index+(e.key==='ArrowRight'?1:-1)+sources.length)%sources.length;if(platform.dataset.action==='settings-source'){settingsSource=sources[next].id;render();$('.source-pref-tabs .selected')?.focus();}else selectSource(sources[next].id,true);return;}if(['INPUT','TEXTAREA','SELECT'].includes(document.activeElement?.tagName))return;if(e.key==='Escape'){if(document.body.classList.contains('fluid'))document.body.classList.remove('fluid');else if(route!=='discover')back();}if(route==='reader'){if(e.key==='ArrowRight'){e.preventDefault();stepPage(state.mode==='rtl'?-1:1);}else if(e.key==='ArrowLeft'){e.preventDefault();stepPage(state.mode==='rtl'?1:-1);}else if(e.key===' '){e.preventDefault();toggleTools();}}});
setInterval(()=>{let changed=false;for(const d of state.downloads.filter(d=>!d.paused&&d.done<12).slice(0,prefs().parallel)){if(!d.paused&&d.done<12){d.done++;changed=true;}}if(changed){persist();if(route==='library'&&libraryTab==='downloads'&&!$('.sheet'))render();}},1200);
window.addEventListener('beforeunload',saveProgress);
render();
startupUpdateDemo();
