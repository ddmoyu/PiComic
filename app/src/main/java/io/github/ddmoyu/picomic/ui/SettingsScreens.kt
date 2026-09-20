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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.ddmoyu.picomic.BuildConfig
import io.github.ddmoyu.picomic.R
import io.github.ddmoyu.picomic.data.Source

val settingsTitles = mapOf("settings" to "设置", "accounts" to "账号管理", "sources" to "漫画源", "filters" to "内容筛选", "reading" to "阅读", "appearance" to "外观", "updates" to "更新", "data" to "数据与同步", "logs" to "日志", "network" to "设置代理", "about" to "关于 PiComic", "webdav" to "WebDAV 同步")

@Composable fun SettingsHome(ui: UiState, go: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("内容与来源")
        SettingRow("账号管理","登录、会话与平台账号",Glyph.User,onClick={go("accounts")})
        SettingRow("漫画源","六个平台的专属偏好",Glyph.Explore,onClick={go("sources")})
        SettingRow("内容筛选","${ui.keywords.size} 个屏蔽词 · ${if(ui.languages.isEmpty()) "语言不限" else ui.languages.joinToString()}",Glyph.Filter,onClick={go("filters")})
        SectionTitle("阅读体验")
        SettingRow("阅读",ui.pref("readingMode","纵向连续"),Glyph.Book,onClick={go("reading")})
        SettingRow("外观",if(ui.enabled("dark")) "深色模式" else "浅色模式",Glyph.Moon,onClick={go("appearance")})
        SectionTitle("APP")
        SettingRow("更新","GitHub Releases",Glyph.Refresh,onClick={go("updates")})
        SettingRow("数据与同步","下载偏好、缓存与备份",Glyph.Folder,onClick={go("data")})
        SettingRow("日志","运行记录",Glyph.Menu,onClick={go("logs")})
        SectionTitle("网络与关于")
        SettingRow("设置代理",ui.pref("network","跟随系统"),Glyph.Wifi,onClick={go("network")})
        SettingRow("关于 PiComic","介绍、项目地址与问题反馈",Glyph.Info,onClick={go("about")})
        Spacer(Modifier.height(24.dp))
    }
}

@Composable fun PreferenceToggle(title: String, key: String, ui: UiState, vm: AppViewModel, subtitle: String="", default: Boolean=false, enabled: Boolean=true) {
    SettingRow(title,subtitle,onClick={if(enabled) vm.toggle(key,default)},trailing={Switch(checked=ui.enabled(key,default),onCheckedChange={vm.preference(key,it.toString())},enabled=enabled)})
}
@Composable fun PreferenceChoice(title: String, key: String, choices: List<String>, ui: UiState, vm: AppViewModel, default: String=choices.first(), subtitle: String="") {
    var showing by remember { mutableStateOf(false) }
    SettingRow(title,subtitle,value=ui.pref(key,default),onClick={showing=true})
    if(showing) ModalBottomSheet(onDismissRequest={showing=false}) {
        Text(title,Modifier.padding(24.dp,8.dp),style=MaterialTheme.typography.titleLarge)
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
            choices.forEach { value -> SettingRow(value,onClick={vm.preference(key,value);showing=false},trailing={RadioButton(selected=ui.pref(key,default)==value,onClick={vm.preference(key,value);showing=false})}) }
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
    var action by remember { mutableStateOf<String?>(null) }
    val preview: (String) -> Unit = { action=it }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=28.dp)) {
        when(route) {
            "reading" -> {
                SectionTitle("翻页")
                PreferenceChoice("阅读模式","readingMode",listOf("纵向连续","从左向右","从右向左"),ui,vm)
                PreferenceToggle("音量键翻页","volume",ui,vm,"音量减向后阅读，音量加向前阅读")
                PreferenceChoice("自动翻页时间间隔","autoInterval",listOf("2 秒","3 秒","5 秒","10 秒","15 秒","30 秒","60 秒"),ui,vm,"5 秒")
                SectionTitle("显示与加载")
                PreferenceToggle("保持屏幕常亮","keepAwake",ui,vm,"仅在前台阅读时生效",true)
                PreferenceChoice("图片预加载","preload",(1..10).map{"$it 张"},ui,vm,"3 张","沿阅读顺序预加载，快速跳页取消旧任务")
                SectionTitle("缩放手势")
                PreferenceToggle("双击缩放","doubleTap",ui,vm,"双击放大，再次双击恢复",true)
                PreferenceToggle("长按缩放","longPress",ui,vm,"按住临时放大，松开恢复")
            }
            "appearance" -> {
                SectionTitle("主题")
                PreferenceToggle("深色模式","dark",ui,vm)
                PreferenceToggle("纯黑色模式","pureBlack",ui,vm,"深色模式下生效",enabled=ui.enabled("dark"))
                SectionTitle("显示")
                PreferenceToggle("高刷新率模式","highRefresh",ui,vm,"仅保存偏好，设备刷新率请求后续接入")
            }
            "updates" -> {
                SectionTitle("应用版本")
                SettingRow("当前版本",BuildConfig.VERSION_NAME,onClick={})
                SettingRow("发布渠道","GitHub Releases",onClick={})
                HorizontalDivider(Modifier.padding(horizontal=20.dp),color=MaterialTheme.colorScheme.outlineVariant)
                SettingRow("检查更新","暂未配置发布仓库",Glyph.Refresh,onClick={preview("检查更新")})
                PreferenceToggle("启动时检查更新","checkOnStart",ui,vm,"保存开关，正式更新服务后续接入")
                Note("当前为界面预览版。这里不会请求 GitHub、下载 APK 或显示未经检查的最新版本结论。")
            }
            "data" -> {
                SectionTitle("下载偏好")
                SettingRow("设置下载目录","尚未选择系统目录",Glyph.Folder,onClick={preview("设置下载目录")})
                PreferenceChoice("下载并行","parallel",(1..5).map{it.toString()},ui,vm,"2")
                SectionTitle("缓存")
                PreferenceChoice("缓存大小限制","cache",listOf("250 MB","500 MB","1 GB","2 GB"),ui,vm,"500 MB","222.18 MB / ${ui.pref("cache","500 MB")} · 示例数值")
                SettingRow("清除缓存","不影响收藏和下载",Glyph.Trash,onClick={preview("清除缓存")})
                SectionTitle("备份与同步")
                SettingRow("导入用户数据","从备份文件恢复",onClick={preview("导入用户数据")})
                SettingRow("导出用户数据","收藏、阅读记录与偏好",onClick={preview("导出用户数据")})
                SettingRow("WebDAV 同步","配置远端同步位置",Glyph.Refresh,onClick={go("webdav")})
            }
            "logs" -> {
                Note("以下为界面示例，不是设备采集日志。")
                SettingRow("界面初始化","PiComic 已载入本地示意作品",Glyph.Check,onClick={})
                SettingRow("服务状态","平台接口、登录、下载服务未接入",Glyph.Info,onClick={})
                SettingRow("导出日志","日志采集与导出后续接入",Glyph.Download,onClick={preview("导出日志")})
            }
            "network" -> {
                Note("默认跟随系统 VPN / 代理，不提供代理节点。此阶段仅保存界面配置。")
                PreferenceChoice("连接方式","network",listOf("跟随系统","自定义 HTTP 代理"),ui,vm)
                if(ui.pref("network","跟随系统")=="自定义 HTTP 代理") {
                    PreferenceText("代理主机","proxyHost",ui,vm,"127.0.0.1")
                    PreferenceText("端口","proxyPort",ui,vm,"7890")
                    SettingRow("代理认证","账号与口令功能尚未接入",onClick={preview("代理认证")})
                }
                SettingRow("测试连接","网络服务尚未接入",Glyph.Wifi,onClick={preview("测试连接")})
            }
            "about" -> {
                Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Image(painterResource(R.drawable.ic_launcher),"PiComic 标识",Modifier.size(80.dp))
                    Text("PiComic",style=MaterialTheme.typography.headlineMedium)
                    Text("让故事，陪你多走一站。",color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Note("一款面向手机的漫画阅读应用。支持多平台浏览、书架管理与沉浸式阅读。当前使用本地原创示意内容，真实平台服务待接入。")
                SettingRow("项目地址","GitHub 仓库待配置",Glyph.Book,onClick={preview("项目地址")})
                SettingRow("问题反馈","反馈入口待配置",Glyph.Info,onClick={preview("问题反馈")})
            }
            "webdav" -> WebDavForm(preview)
        }
    }
    action?.let { title -> AlertDialog(onDismissRequest={action=null},title={Text(title)},text={Text("${title}的界面入口已预留，实际服务将在后续开发中接入。本次未执行网络或文件操作。")},confirmButton={TextButton(onClick={action=null;notice("此功能尚未接入")}) { Text("知道了") }}) }
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
            Note("平台专属偏好 · 线路与签到服务尚未接入")
            when(selected) {
                Source.PICACG -> {
                    PreferenceChoice("搜索及分类排序模式","pica.search",listOf("新到旧","旧到新","最多喜欢"),ui,vm)
                    PreferenceChoice("收藏夹漫画排序模式","pica.favorite",listOf("新到旧","旧到新"),ui,vm)
                    PreferenceToggle("显示头像框","pica.avatar",ui,vm,default=true)
                    PreferenceToggle("自动打卡","pica.checkin",ui,vm,"启动或距离上次打卡一天时执行（待接入）")
                }
                Source.EHENTAI -> {
                    PreferenceChoice("画廊站点","eh.site",listOf("e-hentai.org","exhentai.org"),ui,vm)
                    PreferenceToggle("优先加载原图","eh.original",ui,vm,"可能受原图额度限制")
                    PreferenceToggle("忽略警告","eh.warning",ui,vm)
                    PreferenceToggle("优先显示副标题","eh.subtitle",ui,vm,"适用于已下载的画廊")
                    SettingRow("配置文件",onClick={notice("配置文件导入尚未接入")})
                }
                Source.JMCOMIC -> {
                    PreferenceToggle("自动选择域名","jm.auto",ui,vm,"登录时选择 API 域名（待接入）",true)
                    PreferenceChoice("API 域名","jm.api",(1..4).map{"分流 $it"},ui,vm,"分流 1","演示选项，不代表实际线路")
                    PreferenceChoice("图片分流","jm.image",(1..4).map{"分流 $it"},ui,vm)
                    PreferenceChoice("收藏夹漫画排序模式","jm.favorite",listOf("最新收藏","最早收藏","最近更新"),ui,vm)
                    SettingRow("更新 API 域名",glyph=Glyph.Refresh,onClick={notice("线路服务尚未接入")})
                    PreferenceToggle("自动签到","jm.checkin",ui,vm)
                    SettingRow("测试签到",onClick={notice("签到接口尚未接入")})
                }
                Source.HITOMI -> PreferenceText("CDN 域名","hitomi.cdn",ui,vm,"gold-usergeneratedcontent.net","待实测验证")
                Source.HTCOMIC -> PreferenceText("域名","ht.host",ui,vm,"www.wnacg.com","待实测验证")
                Source.NHENTAI -> SettingRow("删除 Cookie","网页会话尚未接入",Glyph.Trash,onClick={notice("当前没有真实 Cookie")})
            }
        } }
    }
}

@Composable fun AccountsScreen(ui: UiState, vm: AppViewModel, login: (Source) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Note("登录状态仅用于界面演示，不读取或保存真实 Token / Cookie。")
        Source.entries.forEach { source ->
            SectionTitle(source.title)
            if(source==Source.HITOMI) Note("该来源无需账号，内容服务待接入。")
            else if(source in ui.accounts) {
                SettingRow("演示账号","PiComic Reader · 非真实平台账号",glyph=Glyph.User,onClick={})
                SettingRow("重新登录","会话失效时重新授权",glyph=Glyph.Refresh,onClick={login(source)})
                SettingRow("退出登录",onClick={vm.logout(source)})
            } else SettingRow("未登录",if(source==Source.EHENTAI||source==Source.HTCOMIC) "网页登录 / 自动获取会话（待接入）" else "登录后管理平台账号",Glyph.User,"登录",{login(source)})
            HorizontalDivider(Modifier.padding(horizontal=20.dp),color=MaterialTheme.colorScheme.outlineVariant)
        }
        Spacer(Modifier.height(30.dp))
    }
}
@Composable fun LoginScreen(source: Source, onComplete: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        AppIcon(Glyph.User,modifier=Modifier.size(42.dp),color=MaterialTheme.colorScheme.primary)
        Text(source.title,style=MaterialTheme.typography.headlineSmall)
        Text("账号登录",style=MaterialTheme.typography.titleMedium)
        Text("网页登录与自动获取 Token / Cookie 将在服务接入阶段实现。这里仅展示授权入口，不需要输入真实账号。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                AppIcon(Glyph.Wifi)
                Text("网页登录区域",style=MaterialTheme.typography.titleMedium)
                Text("完成平台登录 → 获取会话 → 验证后返回",style=MaterialTheme.typography.bodySmall)
                Text("当前未打开网页，也未读取浏览器会话。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick=onComplete,modifier=Modifier.fillMaxWidth()) { Text("体验演示登录状态") }
    }
}
@Composable private fun WebDavForm(preview: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Note("配置界面预览，请勿输入真实口令。离开页面后口令清空，不执行网络请求。")
    Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(url,{url=it},Modifier.fillMaxWidth(),label={Text("服务器地址")},placeholder={Text("https://example.com/dav")},singleLine=true)
        OutlinedTextField(user,{user=it},Modifier.fillMaxWidth(),label={Text("用户名")},singleLine=true)
        OutlinedTextField(password,{password=it},Modifier.fillMaxWidth(),label={Text("密码 / 应用口令")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
        OutlinedButton(onClick={preview("测试 WebDAV 连接")},modifier=Modifier.fillMaxWidth()) { Text("测试连接") }
        Button(onClick={preview("同步用户数据")},modifier=Modifier.fillMaxWidth()) { Text("立即同步") }
    }
}
