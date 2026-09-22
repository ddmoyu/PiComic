@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.ddmoyu.picomic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        ContentCover(comic, Modifier.fillMaxWidth().aspectRatio(2f / 3).clip(RoundedCornerShape(10.dp)))
        Text(comic.title, Modifier.padding(top = 7.dp), fontSize = 13.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(comic.author.ifBlank { comic.key.source.shortTitle }, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable internal fun ContentComicRow(
    comic: ComicSummary,
    open: ((ComicKey) -> Unit)? = null,
    showSource: Boolean = true,
    divider: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().testTag("comic-row-${comic.key.stable}")
            .then(if (open != null) Modifier.clickable { open(comic.key) } else Modifier),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ContentCover(comic, Modifier.width(100.dp).aspectRatio(2f / 3).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest).testTag("comic-cover-${comic.key.stable}"))
            Column(Modifier.weight(1f).heightIn(min = 150.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(comic.title, Modifier.testTag("comic-title-${comic.key.stable}"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(comic.author.ifBlank { "作者未提供" }, fontSize = 13.sp, lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val tags = comic.tags.filter(String::isNotBlank).distinct().take(3)
                    if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp), maxLines = 2) {
                        tags.forEach { tag ->
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                                Text(tag, Modifier.widthIn(max = 100.dp).padding(horizontal = 7.dp, vertical = 3.dp),
                                    fontSize = 11.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    comic.pageCount?.takeIf { it > 0 }?.let { count ->
                        Text("共 $count 张图片", Modifier.testTag("comic-page-count-${comic.key.stable}"),
                            fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(listOfNotNull(comic.key.source.shortTitle.takeIf { showSource }, comic.key.id,
                        comic.language?.takeIf(String::isNotBlank)).joinToString(" · "),
                        modifier = Modifier.testTag("comic-meta-${comic.key.stable}"),
                        fontSize = 11.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    footer?.invoke()
                }
            }
        }
        if (divider) HorizontalDivider(Modifier.padding(top = 12.dp))
    }
}
@Composable fun ContentFailurePanel(message: String, retry: () -> Unit, login: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        if (login != null) Button(onClick = login) { Text("管理账号") }
        OutlinedButton(onClick = retry) { Text("重新加载") }
    }
}
@Composable fun ContentCategoryScreen(source: Source, category: String, ui: UiState, vm: AppViewModel, open: (ComicKey) -> Unit, login: (Source) -> Unit) {
    val options = remember(source) { CategorySorts.options(source) }
    var selected by rememberSaveable(source, category) { mutableStateOf(options.first().value) }
    val randomSeed = rememberSaveable(source, category) { kotlin.random.Random.nextLong() }
    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("category-sorts-${source.name}"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(selected = selected == option.value, onClick = { selected = option.value },
                    label = { Text(option.label) }, modifier = Modifier.testTag("category-sort-${option.value}"))
            }
        }
        HorizontalDivider()
        Box(Modifier.weight(1f)) {
            ContentListScreen(source, ContentQuery(category = category, sort = selected, randomSeed = randomSeed),
                "category/$category", ui, vm, open, login, listLayout = true)
        }
    }
}

@Composable fun ContentListScreen(source: Source, query: ContentQuery, slot: String, ui: UiState, vm: AppViewModel, open: (ComicKey) -> Unit, login: (Source) -> Unit, listLayout: Boolean = false) {
    val controller = remember(source, slot) { vm.contentList("$slot/${source.name}") }
    val state by controller.state.collectAsStateWithLifecycle()
    val network by vm.network.state.collectAsStateWithLifecycle()
    val revisions by vm.network.sessions.changes.collectAsStateWithLifecycle()
    val routes by vm.jmRoutes.state.collectAsStateWithLifecycle()
    val htRoutes by vm.htRoutes.state.collectAsStateWithLifecycle()
    val sourceConfig = when (source) { Source.JMCOMIC -> "${routes.selected}/${ui.pref("jm.image")}"; Source.NHENTAI -> ui.pref("nh.auth"); Source.HTCOMIC -> htRoutes.selected; Source.EHENTAI -> "${ui.pref("eh.site")}/${ui.pref("eh.warning")}"; else -> "" }
    val grid = rememberLazyGridState()
    val list = rememberLazyListState()
    // Save the query with scroll state: a sort change resets it, returning from details does not.
    var scrollQuery by rememberSaveable(source, slot) { mutableStateOf(listOf(query.keyword, query.category.orEmpty(), query.sort)) }
    LaunchedEffect(query.keyword, query.category, query.sort, listLayout) {
        val current = listOf(query.keyword, query.category.orEmpty(), query.sort)
        if (scrollQuery != current) {
            scrollQuery = current
            if (listLayout) list.scrollToItem(0) else grid.scrollToItem(0)
        }
    }
    LaunchedEffect(source, query, network.ready, network.generation, revisions, sourceConfig) {
        if (network.ready) controller.load(source, query, context = "${network.generation}/${revisions}/$sourceConfig")
    }
    DisposableEffect(controller) { onDispose { controller.cancel() } }
    val visible = state.items.filter { ContentFilter.accepts(it, ui.keywords, ui.languages, ui.enabled("unknownLanguage", true)) }
    val footer: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.loading || !network.ready) ContentLoading(Modifier.fillMaxWidth().padding(vertical = 20.dp))
            state.error?.let { ContentFailurePanel(it, controller::retry, if (state.needsLogin) ({ login(source) }) else null) }
            if (state.loaded && visible.isEmpty() && !state.loading) Text(if (state.items.isEmpty()) "没有找到作品" else "当前结果已被内容筛选隐藏")
            if (state.nextPage != null && !state.loading && state.error == null) OutlinedButton(onClick = controller::more) { Text("加载更多") }
        }
    }
    if (listLayout) LazyColumn(Modifier.fillMaxSize().testTag("content-list-${source.name}"), state = list,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(visible, key = { it.key.stable }) { ContentComicRow(it, open, showSource = slot != "discover", divider = it.key != visible.last().key) }
        item { footer() }
    } else LazyVerticalGrid(GridCells.Adaptive(105.dp), state = grid, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items(visible, key = { it.key.stable }) { ContentCard(it, open) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            footer()
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
            else ContentListScreen(source, ContentQuery(sort = contentSort(ui)), "discover", ui, vm, open, login, listLayout = true)
        }
    }
}
@Composable private fun ContentCategories(source: Source, vm: AppViewModel, category: (Source, String) -> Unit, login: (Source) -> Unit) {
    var groups by remember(source) { mutableStateOf<List<ContentCategoryGroup>>(emptyList()) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var loading by remember(source) { mutableStateOf(true) }
    var retry by remember(source) { mutableIntStateOf(0) }
    val revisions by vm.network.sessions.changes.collectAsStateWithLifecycle()
    val network by vm.network.state.collectAsStateWithLifecycle()
    val routes by vm.jmRoutes.state.collectAsStateWithLifecycle()
    LaunchedEffect(source, retry, revisions, network.generation, network.ready, routes.selected) {
        loading = true; error = null; groups = emptyList()
        try { groups = vm.content.categoryGroups(source) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = contentError(e) }
        finally { loading = false }
    }
    if (loading) { ContentLoading(); return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("categories-${source.name}"), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        error?.let { ContentFailurePanel(it, { retry++ }, if (source == Source.PICACG) ({ login(source) }) else null) }
        if (error == null && groups.isEmpty()) ContentFailurePanel("来源暂未返回分类", { retry++ })
        groups.forEach { group ->
            Column(Modifier.fillMaxWidth().testTag("category-group-${source.name}-${group.title}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    group.items.forEach { entry ->
                        SuggestionChip(onClick = { category(source, entry.value) }, label = { Text(entry.label, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                            modifier = Modifier.testTag("category-${source.name}-${entry.value}"), shape = RoundedCornerShape(12.dp), border = null,
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                labelColor = MaterialTheme.colorScheme.onSurface))
                    }
                }
            }
        }
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

@Composable internal fun contentDetailContext(source: Source, ui: UiState, vm: AppViewModel): String {
    val revisions by vm.network.sessions.changes.collectAsStateWithLifecycle()
    val network by vm.network.state.collectAsStateWithLifecycle()
    val routes by vm.jmRoutes.state.collectAsStateWithLifecycle()
    val htRoutes by vm.htRoutes.state.collectAsStateWithLifecycle()
    val account = when (source) {
        Source.PICACG -> "picacg"; Source.JMCOMIC -> "jmcomic"; Source.HTCOMIC -> "htcomic"
        Source.EHENTAI -> "ehentai"; Source.HITOMI -> "hitomi"
        Source.NHENTAI -> when (ui.pref("nh.auth")) { "API Key" -> "nhentai_key"; "网页会话" -> "nhentai_web"; else -> "nhentai.anonymous" }
    }
    val settings = when (source) {
        Source.JMCOMIC -> listOf(routes.selected, ui.pref("jm.image"))
        Source.HTCOMIC -> listOf(htRoutes.selected)
        Source.EHENTAI -> listOf("eh.site", "eh.original", "eh.warning", "eh.subtitle").map { ui.pref(it) }
        Source.NHENTAI -> listOf(ui.pref("nh.auth"))
        else -> emptyList()
    }
    return "${network.generation}/$account/${revisions[account]}/$settings"
}

@Composable fun ContentDetailScreen(key: ComicKey, ui: UiState, vm: AppViewModel, read: (String) -> Unit, login: (Source) -> Unit,
    back: () -> Unit = {}, searchTag: (String) -> Unit = {}) {
    val snackbar = remember { SnackbarHostState() }; val scope = rememberCoroutineScope()
    var favoriteBusy by remember { mutableStateOf(false) }
    var selectDownloads by remember { mutableStateOf(false) }
    val downloads by vm.downloadsRepository.tasks.collectAsStateWithLifecycle()
    val context = contentDetailContext(key.source, ui, vm)
    var value by remember(key, context) { mutableStateOf(vm.detailCache.peek(key, context)) }
    var error by remember(key, context) { mutableStateOf<String?>(null) }
    var loading by remember(key, context) { mutableStateOf(false) }
    var retry by remember(key) { mutableIntStateOf(0) }
    val library by vm.library.state.collectAsStateWithLifecycle()
    LaunchedEffect(key, retry, context) {
        error = null; loading = true
        try { value = vm.detailCache.load(key, context, force = retry > 0) { vm.content.run(key.source) { adapter, _ -> adapter.details(key.id) } } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = contentError(e) }
        finally { loading = false }
    }
    val detail = value
    Column(Modifier.fillMaxSize()) {
        // The outer app scaffold already supplies the safe drawing insets on this route.
        PageTop("作品详情", back = back, refresh = { retry++ }, refreshEnabled = !loading,
            windowInsets = WindowInsets(0, 0, 0, 0))
        if (detail == null) { if (error == null) ContentLoading() else ContentFailurePanel(error!!, { retry++ }, { login(key.source) }); return@Column }
        val progress = library.progress.firstOrNull { it.key == key }
        val downloadedChapters = downloads.filter { it.key() == key && it.state == "COMPLETED" }.map { it.chapterId }.toSet()
        if (selectDownloads) DownloadSelection(detail, vm) { selectDownloads = false }
        Box(Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(Modifier.fillMaxSize().testTag("content-detail"), contentPadding = PaddingValues(bottom = 30.dp)) {
            item {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    ContentCover(detail.summary, Modifier.width(112.dp).aspectRatio(2f / 3).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(detail.summary.title, fontSize = 19.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
                        Text(key.source.shortTitle, fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                error?.let { Note("刷新失败：$it") }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconToggleButton(checked = key in library.favorites, enabled = library.ready && !favoriteBusy,
                        modifier = Modifier.size(48.dp).testTag("detail-favorite"), onCheckedChange = {
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
                    }) {
                        AppIcon(if (key in library.favorites) Glyph.HeartFilled else Glyph.Heart, if (key in library.favorites) "取消收藏" else "收藏作品")
                    }
                    IconButton(onClick = { selectDownloads = true }, enabled = detail.chapters.isNotEmpty(),
                        modifier = Modifier.size(48.dp).testTag("detail-download")) {
                        AppIcon(Glyph.Download, "下载章节")
                    }
                    FilledTonalButton(onClick = { read(progress?.chapterId?.takeIf { id -> detail.chapters.any { it.id == id } } ?: detail.chapters.first().id) },
                        enabled = library.ready && detail.chapters.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("detail-read")) {
                        Text(if (progress == null) "开始阅读" else "继续阅读")
                    }
                }
                progress?.let { Note("上次读到第 ${it.page} 页") }
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp).testTag("detail-information"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("信息", Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    DetailInfoGroup("ID", listOf(key.id))
                    DetailInfoGroup("作者", detail.summary.author.split('、').map(String::trim).filter(String::isNotEmpty).ifEmpty { listOf("未提供") })
                    DetailInfoGroup("标签", detail.summary.tags.filter(String::isNotBlank).distinct(), searchTag)
                    detail.summary.language?.takeIf(String::isNotBlank)?.let {
                        DetailInfoGroup("语言", listOf(it))
                    }
                    detail.summary.pageCount?.let {
                        DetailInfoGroup("页数", listOf("$it 页"))
                    }
                    if (detail.description.isNotBlank()) {
                        Text("简介", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(detail.description, fontSize = 13.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                Text("章节 · ${detail.chapters.size}", Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(detail.chapters, key = { it.id }, contentType = { "chapter" }) { chapter ->
                val current = chapter.id == progress?.chapterId
                Row(Modifier.fillMaxWidth().testTag("detail-chapter-${chapter.id}")
                    .clickable(onClickLabel = "阅读章节") { read(chapter.id) }
                    .heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(chapter.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val status = listOfNotNull("继续".takeIf { current }, "已下载".takeIf { chapter.id in downloadedChapters }).joinToString(" · ")
                    if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (chapter.id != detail.chapters.last().id) HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}
@Composable private fun DetailInfoGroup(label: String, values: List<String>, select: ((String) -> Unit)? = null) {
    if (values.isEmpty()) return
    Row(Modifier.fillMaxWidth().testTag("detail-info-$label"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.width(82.dp).padding(vertical = 2.dp), fontSize = 13.sp, lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            values.forEach { value ->
                Text(value, Modifier.then(if (select == null) Modifier else Modifier.clickable(onClickLabel = "搜索标签") { select(value) })
                    .padding(vertical = 2.dp), fontSize = 13.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
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
                else LazyColumn(Modifier.fillMaxSize().testTag("favorites-list"), contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(favorites, key = { it.key.stable }) { ContentComicRow(it, open) }
                }
            } else if (tab == 1) {
                if (state.progress.isEmpty()) EmptyState("还没有阅读记录", "读过的作品会保存在这里。", Glyph.Clock)
                else LazyColumn(Modifier.fillMaxSize().testTag("history-list"), contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { clearHistory = true }) { Text("清空阅读历史") } } }
                    items(state.progress, key = { it.key.stable }) { progress ->
                        state.comics[progress.key]?.let { comic ->
                            ContentComicRow(comic, open) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("读到第 ${progress.page} 页", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    IconAction(Glyph.Close, "删除 ${comic.title} 的历史") { deleteHistory = comic.key }
                                }
                            }
                        }
                } }
            } else DownloadManagerScreen(vm, offline)
        }
    }
}
