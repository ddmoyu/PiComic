package io.github.ddmoyu.picomic.ui

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import io.github.ddmoyu.picomic.data.*
import io.github.ddmoyu.picomic.BuildConfig
import io.github.ddmoyu.picomic.content.*
import kotlinx.coroutines.launch

@Composable fun PiComicApp(vm: AppViewModel = viewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val accountChanges by vm.network.sessions.changes.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { vm.checkins.foreground(true); vm.updates.foreground(true, ui.enabled("checkOnStart")) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_PAUSE) { vm.checkins.foreground(false); vm.updates.foreground(false) }
    LaunchedEffect(accountChanges) { vm.checkins.refresh() }
    io.github.ddmoyu.picomic.reader.AppRefreshRateEffect(ui.enabled("highRefresh"))
    val nav=rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route=entry?.destination?.route ?: "discover"
    val root=route in listOf("discover","categories","library")
    val reading=route.startsWith("reader/") || route.startsWith("content-reader/") || route.startsWith("offline-reader/")
    val demo = BuildConfig.DEBUG && ui.enabled("debugDemo")
    val snackbar=remember { SnackbarHostState() }
    val scope=rememberCoroutineScope()
    val notice: (String) -> Unit = { scope.launch { snackbar.currentSnackbarData?.dismiss();snackbar.showSnackbar(it) } }
    val go: (String) -> Unit = { nav.navigate(it) { launchSingleTop=true } }
    val update by vm.updates.state.collectAsStateWithLifecycle()
    var shownRelease by remember { mutableStateOf(0L) }
    LaunchedEffect(update.phase, reading, route) {
        val release = update.bundle?.release?.id ?: 0
        if (update.phase == io.github.ddmoyu.picomic.update.UpdatePhase.AVAILABLE && !reading && route != "updates" && release != shownRelease) {
            shownRelease = release
            if (snackbar.showSnackbar("发现 PiComic ${update.bundle?.versionName}", "查看更新") == SnackbarResult.ActionPerformed) go("updates")
        }
    }
    val back: () -> Unit = {
        if (route == "login/{source}") { vm.picacgAccount.cancel(); vm.jmAccount.cancel(); vm.htAccount.cancel(); vm.ehAccount.cancel(); vm.nhKeyAccount.cancel(); vm.nhWebAccount.cancel() }
        nav.popBackStack(); Unit
    }
    val open: (Source,Comic) -> Unit = { source,comic -> go("detail/${source.name}/${comic.id}") }
    val openContent: (ComicKey) -> Unit = { key -> go("content-detail/${key.source.name}/${Uri.encode(key.id)}") }
    val login: (Source) -> Unit = { go("login/${it.name}") }
    val read: (Source,Comic,Int,Int) -> Unit = { source,comic,chapter,page ->
        val offset = ui.history.firstOrNull { it.source==source && it.comicId==comic.id && it.chapter==chapter && it.page==page }?.offsetRatio ?: 0f
        go("reader/${source.name}/${comic.id}/$chapter/$page/$offset")
    }
    val view=LocalView.current
    val activity = view.context as? io.github.ddmoyu.picomic.MainActivity
    val downloadsRequest by (activity?.downloadsRequest ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0) }).collectAsStateWithLifecycle()
    LaunchedEffect(downloadsRequest) { if (downloadsRequest > 0) { vm.downloadTab.value++; go("library"); activity?.downloadsRequest?.value = 0 } }
    SideEffect {
        (view.context as? Activity)?.window?.let {
            WindowCompat.getInsetsController(it,view).isAppearanceLightStatusBars=!ui.enabled("dark")&&!reading
            WindowCompat.getInsetsController(it,view).isAppearanceLightNavigationBars=!ui.enabled("dark")&&!reading
        }
    }
    PiComicTheme(ui.enabled("dark")||reading, ui.enabled("pureBlack")) {
        Scaffold(containerColor=MaterialTheme.colorScheme.background,
            contentWindowInsets=if(reading) WindowInsets(0,0,0,0) else ScaffoldDefaults.contentWindowInsets,
            snackbarHost={SnackbarHost(snackbar)},
            topBar={
                if(!reading&&!route.startsWith("search")) {
                    val title=when(route) { "discover"->"探索";"categories"->"分类";"library"->"书架";"detail/{source}/{id}","content-detail/{source}/{id}"->"作品详情";"category/{category}"->entry?.arguments?.getString("category")?:"分类";"login/{source}"->"账号登录";else->settingsTitles[route]?:"PiComic" }
                    PageTop(title,if(root)null else back,if(root)({go("search")}) else null,if(root)({go("settings")}) else null)
                }
            },
            bottomBar={if(root) NavigationBar(containerColor=MaterialTheme.colorScheme.surface) {
                listOf(Triple("discover","探索",Glyph.Explore),Triple("categories","分类",Glyph.Grid),Triple("library","书架",Glyph.Book)).forEach { (id,title,icon) ->
                    NavigationBarItem(selected=route==id,onClick={nav.navigate(id){popUpTo(nav.graph.startDestinationId){saveState=true};launchSingleTop=true;restoreState=true}},icon={AppIcon(icon,title)},alwaysShowLabel=false)
                }
            }}) { padding ->
            Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter) {
                NavHost(nav,startDestination="discover",modifier=Modifier.widthIn(max=if(reading) 10000.dp else 900.dp).fillMaxSize()) {
                    composable("discover") {
                        if (demo) BrowseScreen(false,ui,vm,open) { source,label -> vm.source(source);go("category/${Uri.encode(label)}") }
                        else ContentBrowseScreen(false,ui,vm,openContent,{ source,label -> vm.source(source);go("category/${Uri.encode(label)}") },login)
                    }
                    composable("categories") {
                        if (demo) BrowseScreen(true,ui,vm,open) { source,label -> vm.source(source);go("category/${Uri.encode(label)}") }
                        else ContentBrowseScreen(true,ui,vm,openContent,{ source,label -> vm.source(source);go("category/${Uri.encode(label)}") },login)
                    }
                    composable("category/{category}") { target ->
                        if (demo) ComicGrid(DemoCatalog.filter("",target.arguments?.getString("category")?:"全部",ui.keywords,ui.languages),{open(ui.source,it)})
                        else ContentListScreen(ui.source,ContentQuery(category=target.arguments?.getString("category"), sort=contentSort(ui)),"category/${target.arguments?.getString("category")}",ui,vm,openContent,login)
                    }
                    composable("library") { if (demo) LibraryScreen(ui,vm,open) { h -> read(h.source,DemoCatalog.comic(h.comicId),h.chapter,h.page) } else ContentLibraryScreen(vm,openContent) { go("offline-reader/$it") } }
                    composable("offline-reader/{id}") { target -> OfflineReaderScreen(target.arguments!!.getString("id")!!, ui, vm, back) }
                    composable("search?query={query}", arguments = listOf(androidx.navigation.navArgument("query") { type = androidx.navigation.NavType.StringType; defaultValue = "" })) { target ->
                        if (demo) SearchScreen(ui,vm,back) { open(ui.source,it) } else ContentSearchScreen(ui,vm,back,openContent,login,target.arguments?.getString("query").orEmpty())
                    }
                    composable("detail/{source}/{id}") { target ->
                        val source=Source.valueOf(target.arguments!!.getString("source")!!)
                        val comic=DemoCatalog.comic(target.arguments!!.getString("id")!!.toInt())
                        if (demo) DetailScreen(source,comic,ui,vm,{chapter,page->read(source,comic,chapter,page)},notice)
                    }
                    composable("reader/{source}/{id}/{chapter}/{page}/{offset}") { target ->
                        val args=target.arguments!!
                        if (demo) ReaderScreen(Source.valueOf(args.getString("source")!!),DemoCatalog.comic(args.getString("id")!!.toInt()),args.getString("chapter")!!.toInt(),args.getString("page")!!.toInt(),args.getString("offset")?.toFloatOrNull()?:0f,ui,vm,back)
                    }
                    composable("content-detail/{source}/{id}") { target ->
                        val key = ComicKey(Source.valueOf(target.arguments!!.getString("source")!!), target.arguments!!.getString("id")!!)
                        ContentDetailScreen(key,ui,vm,{ chapter -> go("content-reader/${key.source.name}/${Uri.encode(key.id)}/${Uri.encode(chapter)}") },login) { tag -> vm.source(key.source); go("search?query=${Uri.encode(tag)}") }
                    }
                    composable("content-reader/{source}/{id}/{chapter}") { target ->
                        val key = ComicKey(Source.valueOf(target.arguments!!.getString("source")!!), target.arguments!!.getString("id")!!)
                        ContentReaderScreen(key,target.arguments!!.getString("chapter")!!,ui,vm,back,login)
                    }
                    composable("settings") { SettingsHome(ui,vm,go) }
                    composable("accounts") { AccountsScreen(ui,vm) { go("login/${it.name}") } }
                    composable("sources") { SourcesScreen(ui,vm,notice) }
                    composable("filters") { FiltersScreen(ui,vm) }
                    composable("login/{source}") { target ->
                        val source=Source.valueOf(target.arguments!!.getString("source")!!)
                        if (source in setOf(Source.PICACG, Source.JMCOMIC, Source.HTCOMIC)) {
                            val network by vm.network.state.collectAsStateWithLifecycle()
                            PicacgLoginScreen(when(source) { Source.PICACG -> vm.picacgAccount; Source.JMCOMIC -> vm.jmAccount; else -> vm.htAccount }, network.ready, ui.enabled("pica.avatar", true)) { go("network") }
                        } else if (source == Source.NHENTAI) NhLoginScreen(ui, vm) { go("network") }
                        else if (source == Source.EHENTAI) EhLoginScreen(vm) { go("network") }
                        else LoginScreen(source)
                    }
                    listOf("reading","appearance","updates","data","logs","network","about","webdav").forEach { id -> composable(id) { SettingsPage(id,ui,vm,go,notice) } }
                }
            }
        }
    }
}
