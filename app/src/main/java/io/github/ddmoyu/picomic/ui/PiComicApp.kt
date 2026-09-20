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
import kotlinx.coroutines.launch

@Composable fun PiComicApp(vm: AppViewModel = viewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val nav=rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route=entry?.destination?.route ?: "discover"
    val root=route in listOf("discover","categories","library")
    val reading=route.startsWith("reader/")
    val snackbar=remember { SnackbarHostState() }
    val scope=rememberCoroutineScope()
    val notice: (String) -> Unit = { scope.launch { snackbar.currentSnackbarData?.dismiss();snackbar.showSnackbar(it) } }
    val go: (String) -> Unit = { nav.navigate(it) { launchSingleTop=true } }
    val back: () -> Unit = { nav.popBackStack();Unit }
    val open: (Source,Comic) -> Unit = { source,comic -> go("detail/${source.name}/${comic.id}") }
    val read: (Source,Comic,Int,Int) -> Unit = { source,comic,chapter,page ->
        val offset = ui.history.firstOrNull { it.source==source && it.comicId==comic.id && it.chapter==chapter && it.page==page }?.offsetRatio ?: 0f
        go("reader/${source.name}/${comic.id}/$chapter/$page/$offset")
    }
    val view=LocalView.current
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
                if(!reading&&route!="search") {
                    val title=when(route) { "discover"->"探索";"categories"->"分类";"library"->"书架";"detail/{source}/{id}"->"作品详情";"category/{category}"->entry?.arguments?.getString("category")?:"分类";"login/{source}"->"账号登录";else->settingsTitles[route]?:"PiComic" }
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
                    composable("discover") { BrowseScreen(false,ui,vm,open) { source,label -> vm.source(source);go("category/${Uri.encode(label)}") } }
                    composable("categories") { BrowseScreen(true,ui,vm,open) { source,label -> vm.source(source);go("category/${Uri.encode(label)}") } }
                    composable("category/{category}") { target -> ComicGrid(DemoCatalog.filter("",target.arguments?.getString("category")?:"全部",ui.keywords,ui.languages),{open(ui.source,it)}) }
                    composable("library") { LibraryScreen(ui,vm,open) { h -> read(h.source,DemoCatalog.comic(h.comicId),h.chapter,h.page) } }
                    composable("search") { SearchScreen(ui,vm,back) { open(ui.source,it) } }
                    composable("detail/{source}/{id}") { target ->
                        val source=Source.valueOf(target.arguments!!.getString("source")!!)
                        val comic=DemoCatalog.comic(target.arguments!!.getString("id")!!.toInt())
                        DetailScreen(source,comic,ui,vm,{chapter,page->read(source,comic,chapter,page)},notice)
                    }
                    composable("reader/{source}/{id}/{chapter}/{page}/{offset}") { target ->
                        val args=target.arguments!!
                        ReaderScreen(Source.valueOf(args.getString("source")!!),DemoCatalog.comic(args.getString("id")!!.toInt()),args.getString("chapter")!!.toInt(),args.getString("page")!!.toInt(),args.getString("offset")?.toFloatOrNull()?:0f,ui,vm,back)
                    }
                    composable("settings") { SettingsHome(ui,go) }
                    composable("accounts") { AccountsScreen(ui,vm) { go("login/${it.name}") } }
                    composable("sources") { SourcesScreen(ui,vm,notice) }
                    composable("filters") { FiltersScreen(ui,vm) }
                    composable("login/{source}") { target ->
                        val source=Source.valueOf(target.arguments!!.getString("source")!!)
                        LoginScreen(source) { vm.login(source);back();notice("已进入演示登录状态") }
                    }
                    listOf("reading","appearance","updates","data","logs","network","about","webdav").forEach { id -> composable(id) { SettingsPage(id,ui,vm,go,notice) } }
                }
            }
        }
    }
}
