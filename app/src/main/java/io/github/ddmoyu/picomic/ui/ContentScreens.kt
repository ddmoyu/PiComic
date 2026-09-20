@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.ddmoyu.picomic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.reader.ReaderImages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable fun ContentCover(comic: ComicSummary, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val model = remember(comic.cover, comic.key.source) {
        ImageRequest.Builder(context).data(comic.cover).apply {
            val referer = when (comic.key.source) {
                Source.HITOMI -> "https://hitomi.la/"
                Source.HTCOMIC -> "https://www.wnacg.com/"
                else -> null
            }
            if (referer != null) httpHeaders(NetworkHeaders.Builder().set("Referer", referer).build())
        }.build()
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (comic.cover == null) AppIcon(Glyph.Book) else AsyncImage(model, "${comic.title}封面",
            imageLoader = ReaderImages.loader(LocalContext.current), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
@Composable fun ContentCard(comic: ComicSummary, open: (ComicKey) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { open(comic.key) }) {
        ContentCover(comic, Modifier.fillMaxWidth().aspectRatio(2f / 3))
        Text(comic.title, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(comic.author.ifBlank { comic.key.source.shortTitle }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable fun ContentFailurePanel(message: String, retry: () -> Unit, login: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        if (login != null) Button(onClick = login) { Text("管理账号") }
        OutlinedButton(onClick = retry) { Text("重新加载") }
    }
}
@Composable fun ContentListScreen(source: Source, query: ContentQuery, slot: String, ui: UiState, vm: AppViewModel, open: (ComicKey) -> Unit, login: (Source) -> Unit) {
    val controller = remember(source, slot) { vm.contentList("$slot/${source.name}") }
    val state by controller.state.collectAsStateWithLifecycle()
    val network by vm.network.state.collectAsStateWithLifecycle()
    val revisions by vm.network.sessions.changes.collectAsStateWithLifecycle()
    val routes by vm.jmRoutes.state.collectAsStateWithLifecycle()
    val htRoutes by vm.htRoutes.state.collectAsStateWithLifecycle()
    val sourceConfig = when (source) { Source.JMCOMIC -> "${routes.selected}/${ui.pref("jm.image")}"; Source.NHENTAI -> ui.pref("nh.auth"); Source.HTCOMIC -> htRoutes.selected; Source.EHENTAI -> "${ui.pref("eh.site")}/${ui.pref("eh.warning")}"; else -> "" }
    val grid = rememberLazyGridState()
    LaunchedEffect(source, query, network.ready, network.generation, revisions, sourceConfig) {
        if (network.ready) controller.load(source, query, context = "${network.generation}/${revisions}/$sourceConfig")
    }
    DisposableEffect(controller) { onDispose { controller.cancel() } }
    val visible = state.items.filter { ContentFilter.accepts(it, ui.keywords, ui.languages, ui.enabled("unknownLanguage", true)) }
    LazyVerticalGrid(GridCells.Adaptive(105.dp), state = grid, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items(visible, key = { it.key.stable }) { ContentCard(it, open) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.loading || !network.ready) ContentLoading(Modifier.fillMaxWidth().padding(vertical = 20.dp))
                state.error?.let { ContentFailurePanel(it, controller::retry, if (state.needsLogin) ({ login(source) }) else null) }
                if (state.loaded && visible.isEmpty() && !state.loading) Text(if (state.items.isEmpty()) "没有找到作品" else "当前结果已被内容筛选隐藏")
                if (state.nextPage != null && !state.loading && state.error == null) OutlinedButton(onClick = controller::more) { Text("加载更多") }
            }
        }
    }
}

@Composable fun ContentBrowseScreen(categories: Boolean, ui: UiState, vm: AppViewModel, open: (ComicKey) -> Unit, category: (Source, String) -> Unit, login: (Source) -> Unit) {
    val pager = rememberPagerState(initialPage = ui.source.ordinal) { Source.entries.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) {
        pager.scrollToPage(ui.source.ordinal)
        snapshotFlow { pager.settledPage }.collect { vm.source(Source.entries[it]) }
    }
    Column(Modifier.fillMaxSize()) {
        SourceTabs(Source.entries[pager.currentPage]) { scope.launch { pager.animateScrollToPage(it.ordinal) }; vm.source(it) }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().testTag(if (categories) "category-pages" else "discover-pages"), key = { Source.entries[it].name }, verticalAlignment = Alignment.Top) { index ->
            val source = Source.entries[index]
            if (categories) ContentCategories(source, vm, category, login)
            else ContentListScreen(source, ContentQuery(sort = contentSort(ui)), "discover", ui, vm, open, login)
        }
    }
}
@Composable private fun ContentCategories(source: Source, vm: AppViewModel, category: (Source, String) -> Unit, login: (Source) -> Unit) {
    var values by remember(source) { mutableStateOf<List<String>>(emptyList()) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var loading by remember(source) { mutableStateOf(true) }
    var retry by remember(source) { mutableIntStateOf(0) }
    val revisions by vm.network.sessions.changes.collectAsStateWithLifecycle()
    val network by vm.network.state.collectAsStateWithLifecycle()
    val routes by vm.jmRoutes.state.collectAsStateWithLifecycle()
    LaunchedEffect(source, retry, revisions, network.generation, network.ready, routes.selected) {
        if (!network.ready) return@LaunchedEffect
        loading = true; error = null; values = emptyList()
        try { values = vm.content.run(source) { adapter, _ -> adapter.categories() } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = contentError(e) }
        finally { loading = false }
    }
    if (loading) { ContentLoading(); return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        error?.let { ContentFailurePanel(it, { retry++ }, if (source == Source.PICACG) ({ login(source) }) else null) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { values.forEach { name -> AssistChip(onClick = { category(source, name) }, label = { Text(name) }) } }
    }
}
@Composable fun ContentSearchScreen(ui: UiState, vm: AppViewModel, back: () -> Unit, open: (ComicKey) -> Unit, login: (Source) -> Unit, initialQuery: String = "") {
    var text by rememberSaveable(initialQuery) { mutableStateOf(initialQuery.take(300)) }
    var submitted by rememberSaveable(initialQuery) { mutableStateOf(initialQuery.take(300).takeIf { it.isNotBlank() }) }
    LaunchedEffect(initialQuery) { if (initialQuery.isNotBlank()) vm.library.search(ui.source, initialQuery) }
    val keyboard = LocalSoftwareKeyboardController.current
    val library by vm.library.state.collectAsStateWithLifecycle()
    fun submit(value: String = text) { text = value.take(300); submitted = text.trim(); vm.library.search(ui.source, text); keyboard?.hide() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(Glyph.Back, "返回", back)
            OutlinedTextField(text, { text = it.take(300) }, Modifier.weight(1f), placeholder = { Text("作品、作者或标签") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit() }))
            TextButton(onClick = { submit() }) { Text("搜索") }
        }
        SourceTabs(ui.source, vm::source)
        if (submitted == null) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("最近搜索", Modifier.weight(1f))
                TextButton(onClick = { vm.library.clearSearches(ui.source) }) { Text("清空") }
            }
            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                library.searches.filter { it.source == ui.source.name }.forEach { item -> SuggestionChip(onClick = { submit(item.query) }, label = { Text(item.query) }) }
            }
        } else ContentListScreen(ui.source, ContentQuery(submitted.orEmpty(), sort = contentSort(ui)), "search", ui, vm, open, login)
    }
}
fun contentSort(ui: UiState) = when (ui.pref("pica.search", "新到旧")) { "旧到新" -> "da"; "最多喜欢" -> "ld"; else -> "dd" }

@Composable fun ContentDetailScreen(key: ComicKey, ui: UiState, vm: AppViewModel, read: (String) -> Unit, login: (Source) -> Unit, searchTag: (String) -> Unit = {}) {
    val snackbar = remember { SnackbarHostState() }; val scope = rememberCoroutineScope()
    var favoriteBusy by remember { mutableStateOf(false) }
    var selectDownloads by remember { mutableStateOf(false) }
    val downloads by vm.downloadsRepository.tasks.collectAsStateWithLifecycle()
    var value by remember(key) { mutableStateOf<ComicDetails?>(null) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var retry by remember(key) { mutableIntStateOf(0) }
    val library by vm.library.state.collectAsStateWithLifecycle()
    val revisions by vm.network.sessions.changes.collectAsStateWithLifecycle()
    val network by vm.network.state.collectAsStateWithLifecycle()
    val routes by vm.jmRoutes.state.collectAsStateWithLifecycle()
    val htRoutes by vm.htRoutes.state.collectAsStateWithLifecycle()
    LaunchedEffect(key, retry, revisions, network.generation, routes.selected, htRoutes.selected, ui.pref("jm.image"), ui.pref("nh.auth"), ui.pref("eh.warning"), ui.pref("eh.subtitle")) {
        value = null; error = null
        try { value = vm.content.run(key.source) { adapter, _ -> adapter.details(key.id) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = contentError(e) }
    }
    val detail = value
    if (detail == null) { if (error == null) ContentLoading() else ContentFailurePanel(error!!, { retry++ }, { login(key.source) }); return }
    val progress = library.progress.firstOrNull { it.key == key }
    if (selectDownloads) DownloadSelection(detail, vm) { selectDownloads = false }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ContentCover(detail.summary, Modifier.width(120.dp).aspectRatio(2f / 3))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(key.source.shortTitle, color = MaterialTheme.colorScheme.primary)
                    Text(detail.summary.title, style = MaterialTheme.typography.headlineSmall)
                    Text(detail.summary.author)
                    Text("${detail.chapters.size} 话")
                }
            }
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { read(progress?.chapterId?.takeIf { id -> detail.chapters.any { it.id == id } } ?: detail.chapters.first().id) }, enabled = library.ready, modifier = Modifier.weight(1f)) { Text(if (progress == null) "开始阅读" else "继续阅读") }
                FilledTonalIconButton(enabled = library.ready && !favoriteBusy, onClick = {
                    favoriteBusy = true
                    scope.launch {
                        try {
                            val selected = key !in library.favorites
                            val change = vm.library.favorite(detail.summary, selected)
                            favoriteBusy = false; snackbar.currentSnackbarData?.dismiss()
                            if (snackbar.showSnackbar(if (selected) "已收藏" else "已取消收藏", "撤销") == SnackbarResult.ActionPerformed)
                                if (!vm.library.undoFavorite(change)) snackbar.showSnackbar("收藏已发生后续变更，无法撤销旧操作")
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { snackbar.showSnackbar("收藏保存失败，请重试") }
                        finally { favoriteBusy = false }
                    }
                }) { AppIcon(if (key in library.favorites) Glyph.Check else Glyph.Heart, if (key in library.favorites) "取消收藏" else "收藏作品") }
                FilledTonalIconButton(onClick = { selectDownloads = true }) { AppIcon(Glyph.Download, "下载章节") }
            }
            progress?.let { Note("上次读到第 ${it.page} 页") }
            Text(detail.description, Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { detail.summary.tags.forEach { SuggestionChip(onClick = { searchTag(it) }, label = { Text(it) }) } }
            SectionTitle("目录")
        }
        items(detail.chapters, key = { it.id }) { chapter -> SettingRow(chapter.title,
            subtitle = if (downloads.any { it.key() == key && it.chapterId == chapter.id && it.state == "COMPLETED" }) "已下载 · 可在书架离线阅读" else "",
            value = if (chapter.id == progress?.chapterId) "继续" else "", onClick = { read(chapter.id) }) }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
@Composable fun ContentLibraryScreen(vm: AppViewModel, open: (ComicKey) -> Unit, offline: (String) -> Unit = {}) {
    val state by vm.library.state.collectAsStateWithLifecycle()
    val error by vm.library.error.collectAsStateWithLifecycle()
    val pager = rememberPagerState { 3 }; val scope = rememberCoroutineScope()
    var deleteHistory by remember { mutableStateOf<ComicKey?>(null) }; var clearHistory by remember { mutableStateOf(false) }
    if (deleteHistory != null || clearHistory) AlertDialog(onDismissRequest = { deleteHistory = null; clearHistory = false }, title = { Text(if (clearHistory) "清空阅读历史？" else "删除这条历史？") },
        text = { Text("将移除阅读位置，并将此删除纳入下次备份或同步。") }, confirmButton = { TextButton(onClick = { vm.library.deleteHistory(deleteHistory); deleteHistory = null; clearHistory = false }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleteHistory = null; clearHistory = false }) { Text("取消") } })
    val downloadTab by vm.downloadTab.collectAsStateWithLifecycle()
    LaunchedEffect(downloadTab) { if (downloadTab > 0) { pager.scrollToPage(2); vm.downloadTab.value = 0 } }
    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(pager.currentPage) { listOf("收藏", "阅读历史", "下载管理").forEachIndexed { index, title ->
            Tab(pager.currentPage == index, onClick = { scope.launch { pager.animateScrollToPage(index) } }, text = { Text(title) })
        } }
        error?.let { Note(it) }
        HorizontalPager(pager, Modifier.weight(1f).testTag("library-pages"), verticalAlignment = Alignment.Top) { tab ->
            if (!state.ready) ContentLoading()
            else if (tab == 0) {
                val favorites = state.favorites.mapNotNull { state.comics[it] }
                if (favorites.isEmpty()) EmptyState("书架还是空的", "在作品详情中收藏作品。", Glyph.Heart)
                else LazyVerticalGrid(GridCells.Adaptive(105.dp), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) { items(favorites, key = { it.key.stable }) { ContentCard(it, open) } }
            } else if (tab == 1) {
                if (state.progress.isEmpty()) EmptyState("还没有阅读记录", "读过的作品会保存在这里。", Glyph.Clock)
                else LazyColumn {
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { clearHistory = true }) { Text("清空阅读历史") } } }
                    items(state.progress, key = { it.key.stable }) { progress ->
                    state.comics[progress.key]?.let { comic -> SettingRow(comic.title, "${comic.key.source.shortTitle} · 第 ${progress.page} 页", Glyph.Clock, onClick = { open(comic.key) }, trailing = {
                        IconAction(Glyph.Close, "删除 ${comic.title} 的历史") { deleteHistory = comic.key }
                    }) }
                } }
            } else DownloadManagerScreen(vm, offline)
        }
    }
}
