@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.ddmoyu.picomic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.ddmoyu.picomic.BuildConfig
import io.github.ddmoyu.picomic.R
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.data.ThemeMode

val settingsTitles = mapOf("settings" to "设置", "accounts" to "账号管理", "sources" to "漫画源", "filters" to "内容筛选", "reading" to "阅读", "appearance" to "外观", "updates" to "更新", "data" to "数据与同步", "logs" to "日志", "network" to "设置代理", "about" to "关于 PiComic", "webdav" to "WebDAV 同步")

@Composable fun SettingsHome(ui: UiState, vm: AppViewModel, go: (String) -> Unit) {
    val network by vm.network.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("内容与来源")
        SettingRow("账号管理","登录、会话与平台账号",Glyph.User,onClick={go("accounts")})
        SettingRow("漫画源","六个平台的专属偏好",Glyph.Explore,onClick={go("sources")})
        SettingRow("内容筛选","${ui.keywords.size} 个屏蔽词 · ${if(ui.languages.isEmpty()) "语言不限" else ui.languages.joinToString()}",Glyph.Filter,onClick={go("filters")})
        SectionTitle("阅读体验")
        SettingRow("阅读",ui.pref("readingMode","纵向连续"),Glyph.Book,onClick={go("reading")})
        SettingRow("外观",ui.themeMode.label,Glyph.Moon,onClick={go("appearance")})
        SectionTitle("APP")
        SettingRow("更新","GitHub Releases",Glyph.Refresh,onClick={go("updates")})
        SettingRow("数据与同步","下载偏好、缓存与备份",Glyph.Folder,onClick={go("data")})
        SettingRow("日志","运行记录",Glyph.Menu,onClick={go("logs")})
        SectionTitle("网络与关于")
        SettingRow("设置代理",network.label,Glyph.Wifi,onClick={go("network")})
        SettingRow("关于 PiComic","介绍、项目地址与问题反馈",Glyph.Info,onClick={go("about")})
        Spacer(Modifier.height(24.dp))
    }
}

@Composable fun PreferenceToggle(title: String, key: String, ui: UiState, vm: AppViewModel, subtitle: String="", default: Boolean=false, enabled: Boolean=true) {
    SettingRow(title,subtitle,onClick={if(enabled) vm.toggle(key,default)},trailing={Switch(checked=ui.enabled(key,default),onCheckedChange={vm.preference(key,it.toString())},enabled=enabled)})
}
@Composable fun PreferenceChoice(title: String, key: String, choices: List<String>, ui: UiState, vm: AppViewModel, default: String=choices.first(), subtitle: String="", save: (String) -> Unit = { vm.preference(key, it) }) {
    var showing by remember { mutableStateOf(false) }
    SettingRow(title,subtitle,value=ui.pref(key,default),onClick={showing=true})
    if(showing) ModalBottomSheet(onDismissRequest={showing=false},
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Text(title,Modifier.padding(24.dp,8.dp),style=MaterialTheme.typography.titleLarge)
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
            choices.forEach { value -> SettingRow(value,onClick={save(value);showing=false},trailing={RadioButton(selected=ui.pref(key,default)==value,onClick={save(value);showing=false})}) }
        }
        Spacer(Modifier.height(20.dp))
    }
}
@Composable private fun PreferenceText(title: String, key: String, ui: UiState, vm: AppViewModel, default: String="", subtitle: String="") {
    var showing by remember { mutableStateOf(false) }
    var text by remember(ui.pref(key,default)) { mutableStateOf(ui.pref(key,default)) }
    SettingRow(title,subtitle,value=ui.pref(key,default).ifBlank { "未设置" },onClick={showing=true})
    if(showing) AlertDialog(onDismissRequest={showing=false},title={Text(title)},text={OutlinedTextField(text,{text=it},singleLine=true,label={Text(title)})},confirmButton={TextButton(onClick={vm.preference(key,text.trim());showing=false}){Text("保存")}},dismissButton={TextButton(onClick={showing=false}){Text("取消")}})
}

@Composable fun SettingsPage(route: String, ui: UiState, vm: AppViewModel, go: (String) -> Unit, notice: (String) -> Unit) {
    if (route == "updates") { UpdateSettings(ui, vm); return }
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=28.dp)) {
        when(route) {
            "reading" -> {
                SectionTitle("翻页")
                PreferenceChoice("阅读模式","readingMode",listOf("纵向连续","从左向右","从右向左"),ui,vm)
                PreferenceToggle("音量键翻页","volume",ui,vm,"音量减向后阅读，音量加向前阅读")
                PreferenceChoice("自动翻页时间间隔","autoInterval",listOf("2 秒","3 秒","5 秒","10 秒","15 秒","30 秒","60 秒"),ui,vm,"5 秒")
                SectionTitle("显示与加载")
                PreferenceChoice("阅读背景", "readerBackground", listOf("深灰", "纯黑", "米白"), ui, vm)
                PreferenceChoice("屏幕方向", "readerOrientation", listOf("跟随系统", "竖屏", "横屏"), ui, vm)
                PreferenceChoice("阅读亮度", "readerBrightness", listOf("跟随系统", "10", "25", "50", "75", "100"), ui, vm, subtitle = "数值为百分比，仅阅读页面生效")
                PreferenceToggle("保持屏幕常亮","keepAwake",ui,vm,"仅在前台阅读时生效",true)
                PreferenceChoice("图片预加载","preload",(1..10).map{"$it 张"},ui,vm,"3 张","持续预加载可见区域之后的图片，翻页不中断已开始的下载")
                SectionTitle("缩放手势")
                PreferenceToggle("双击缩放","doubleTap",ui,vm,"双击放大，再次双击恢复",true)
                PreferenceToggle("长按缩放","longPress",ui,vm,"按住临时放大，松开恢复")
            }
            "appearance" -> {
                SectionTitle("主题")
                PreferenceChoice("主题模式","themeMode",ThemeMode.entries.map { it.label },ui,vm,default=ui.themeMode.label)
                PreferenceToggle("纯黑色模式","pureBlack",ui,vm,"深色模式下生效",enabled=ui.isDarkTheme())
                SectionTitle("显示")
                PreferenceToggle("高刷新率模式","highRefresh",ui,vm,"优先请求设备支持的较高刷新率，仍受系统和省电影响")
            }
            "data" -> {
                SectionTitle("下载偏好")
                DownloadLocationSettings(ui, vm)
                SectionTitle("缓存")
                CacheSettings(ui, vm)
                SectionTitle("备份与同步")
                BackupSettings(vm)
                SettingRow("WebDAV 同步","配置远端同步位置",Glyph.Refresh,onClick={go("webdav")})
            }
            "logs" -> {
                LogSettings()
            }
            "network" -> NetworkSettings(vm.network)
            "about" -> {
                Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Image(painterResource(R.drawable.ic_launcher),"PiComic 标识",Modifier.size(80.dp))
                    Text("PiComic",style=MaterialTheme.typography.headlineMedium)
                    Text("让故事，陪你多走一站。",color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Note("一款面向手机的漫画阅读应用。提供漫画浏览、本地书架与沉浸式阅读，平台服务按来源接入。")
                SettingRow("项目地址","ddmoyu/PiComic · 私有仓库",Glyph.Book,onClick={runCatching { uriHandler.openUri("https://github.com/ddmoyu/PiComic") }.onFailure { notice("无法打开浏览器") }})
                SettingRow("问题反馈","GitHub Issues · 需要仓库访问权限",Glyph.Info,onClick={runCatching { uriHandler.openUri("https://github.com/ddmoyu/PiComic/issues") }.onFailure { notice("无法打开浏览器") }})
            }
            "webdav" -> WebDavSettings(vm)
        }
    }
}

@Composable fun FiltersScreen(ui: UiState, vm: AppViewModel) {
    var keyword by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("关键词屏蔽")
        Note("过滤作品标题、作者和标签，不影响本地书架。")
        Row(Modifier.padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(keyword,{keyword=it.take(80)},Modifier.weight(1f),singleLine=true,label={Text("输入关键词")})
            FilledTonalButton(onClick={vm.keyword(keyword);keyword=""},enabled=keyword.isNotBlank()) { Text("添加") }
        }
        FlowRow(Modifier.padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) { ui.keywords.forEach { word -> InputChip(selected=false,onClick={vm.removeKeyword(word)},label={Text(word)},trailingIcon={AppIcon(Glyph.Close,"删除 $word",Modifier.size(16.dp))}) } }
        SectionTitle("语言筛选")
        Note("不选择表示不限语言。")
        listOf("Chinese","English","Japanese").forEach { language -> SettingRow(language,onClick={vm.language(language)},trailing={Checkbox(checked=language in ui.languages,onCheckedChange={vm.language(language)})}) }
        PreferenceToggle("保留未标注语言的作品","unknownLanguage",ui,vm,default=true)
    }
}

@Composable fun SourcesScreen(ui: UiState, vm: AppViewModel, notice: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(Source.PICACG) }
    Column(Modifier.fillMaxSize()) {
        SourceTabs(selected) { selected=it }
        key(selected) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=24.dp)) {
            Note("平台专属偏好。线路、账号和图片服务各自验证。")
            when(selected) {
                Source.PICACG -> {
                    PreferenceChoice("搜索及分类排序模式","pica.search",listOf("新到旧","旧到新","最多喜欢"),ui,vm)
                    PreferenceChoice("收藏夹漫画排序模式","pica.favorite",listOf("新到旧","旧到新"),ui,vm)
                    PreferenceToggle("显示头像框","pica.avatar",ui,vm,default=true)
                    PreferenceToggle("自动打卡","pica.checkin",ui,vm,"仅在前台、已验证账号时执行；成功后当日去重")
                    CheckInSettings(Source.PICACG, vm)
                }
                Source.EHENTAI -> {
                    PreferenceChoice("画廊站点","eh.site",listOf("e-hentai.org","exhentai.org"),ui,vm)
                    PreferenceToggle("优先加载原图","eh.original",ui,vm,"可能受原图额度限制")
                    PreferenceToggle("忽略警告","eh.warning",ui,vm)
                    PreferenceToggle("优先显示副标题","eh.subtitle",ui,vm,"适用于已下载的画廊")
                    EhConfigurationSettings(vm)
                }
                Source.JMCOMIC -> {
                    val routes by vm.jmRoutes.state.collectAsStateWithLifecycle()
                    PreferenceToggle("自动选择域名","jm.auto",ui,vm,"登录前匿名验证候选线路；会话保持原线路隔离",true)
                    SectionTitle("API 线路")
                    routes.trusted.forEach { host ->
                        SettingRow(host, if (host in routes.available) "连接验证通过" else "本次尚未验证", onClick = { vm.jmAccount.cancel(); vm.jmRoutes.select(host) },
                            trailing = { RadioButton(routes.selected == host, onClick = { vm.jmAccount.cancel(); vm.jmRoutes.select(host) }) })
                    }
                    PreferenceChoice("图片分流","jm.image",(1..4).map{"分流 $it"},ui,vm)
                    PreferenceChoice("收藏夹漫画排序模式","jm.favorite",listOf("最新收藏","最早收藏","最近更新"),ui,vm)
                    SettingRow("更新并验证 API 线路", if (routes.busy) "正在验证候选线路" else "读取可信发布列表，通过匿名验证后加入可选线路", glyph=Glyph.Refresh,onClick=vm.jmRoutes::refresh)
                    routes.message?.let { Note(it) }
                    PreferenceToggle("自动签到","jm.checkin",ui,vm)
                    CheckInSettings(Source.JMCOMIC, vm)
                }
                Source.HITOMI -> {
                    SettingRow("CDN 域名", "gold-usergeneratedcontent.net · 使用来源动态图片规则", onClick = {})
                    Note("无需账号。正文采用 WebP，兼容 Android 8 及以上；地址失效时仅刷新一次来源规则。")
                }
                Source.HTCOMIC -> {
                    val routes by vm.htRoutes.state.collectAsStateWithLifecycle()
                    routes.trusted.sorted().forEach { host ->
                        SettingRow(host, if (host in routes.available) "匿名验证通过" else "可信候选，尚未验证", onClick = { vm.htAccount.cancel(); vm.htRoutes.select(host) },
                            trailing = { RadioButton(routes.selected == host, onClick = { vm.htAccount.cancel(); vm.htRoutes.select(host) }) })
                    }
                    SettingRow("更新并验证域名", if(routes.busy) "正在检查发布页和来源结构" else "从可信发布页发现候选域名", Glyph.Refresh, onClick = vm.htRoutes::refresh)
                    routes.message?.let { Note(it) }
                }
                Source.NHENTAI -> {
                    val operation by vm.nhWebAccount.state.collectAsStateWithLifecycle()
                    var clear by remember { mutableStateOf(false) }
                    SettingRow("删除网页会话", "仅清理 nhentai 网页会话，保留独立 API Key", Glyph.Trash, onClick = { clear = true })
                    operation.message?.let { Note(it) }
                    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("删除网页会话？") },
                        text = { Text("网页会话的请求会取消。API Key、本地收藏和阅读记录将保留。") },
                        confirmButton = { TextButton(onClick = { clear = false; vm.nhWebAccount.logout() }) { Text("删除") } },
                        dismissButton = { TextButton(onClick = { clear = false }) { Text("取消") } })
                }
            }
        } }
    }
}

@Composable fun AccountsScreen(ui: UiState, vm: AppViewModel, login: (Source) -> Unit) {
    val accounts by vm.picacgAccount.accounts.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Note("各来源账号单独验证和保存。权限由平台决定，不影响本地收藏与历史。")
        Source.entries.forEach { source ->
            SectionTitle(source.title)
            if(source==Source.HITOMI) Note("该来源无需账号，可直接搜索和阅读。")
            else if(source==Source.PICACG) SettingRow((accounts[io.github.ddmoyu.picomic.source.picacg.PicacgAccountController.SOURCE] ?: io.github.ddmoyu.picomic.auth.AccountState()).label(),"账号密码登录 · 加密会话",Glyph.User,"管理",{login(source)})
            else if(source==Source.JMCOMIC) SettingRow((accounts["jmcomic"] ?: io.github.ddmoyu.picomic.auth.AccountState()).label(),"可匿名浏览 · 登录后验证会话",Glyph.User,"管理",{login(source)})
            else if(source==Source.NHENTAI) SettingRow(vm.nhSessionId()?.let { (accounts[it] ?: io.github.ddmoyu.picomic.auth.AccountState()).label() } ?: "匿名浏览", "API Key / 网页会话分别验证", Glyph.User, "管理", { login(source) })
            else if(source==Source.HTCOMIC) SettingRow((accounts["htcomic"] ?: io.github.ddmoyu.picomic.auth.AccountState()).label(), "可匿名浏览 · 账号密码登录", Glyph.User, "管理", { login(source) })
            else if(source==Source.EHENTAI) SettingRow((accounts["ehentai"] ?: io.github.ddmoyu.picomic.auth.AccountState()).label(), "网页登录 / Cookie 导入 · 单独验证 EX 权限", Glyph.User, "管理", { login(source) })
            HorizontalDivider(Modifier.padding(horizontal=20.dp),color=MaterialTheme.colorScheme.outlineVariant)
        }
        Spacer(Modifier.height(30.dp))
    }
}
@Composable fun LoginScreen(source: Source) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        AppIcon(Glyph.User,modifier=Modifier.size(42.dp),color=MaterialTheme.colorScheme.primary)
        Text(source.title,style=MaterialTheme.typography.headlineSmall)
        Text("无需账号",style=MaterialTheme.typography.titleMedium)
        Text("此来源使用匿名浏览。连接失败时请检查网络或代理设置，再返回重试。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
