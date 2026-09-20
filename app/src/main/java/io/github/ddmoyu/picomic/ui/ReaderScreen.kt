@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
package io.github.ddmoyu.picomic.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.panpf.zoomimage.compose.zoom.*
import com.github.panpf.zoomimage.zoom.GestureType
import io.github.ddmoyu.picomic.data.*
import io.github.ddmoyu.picomic.reader.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Composable fun ReaderScreen(
    source: Source, comic: Comic, initialChapter: Int, initialPage: Int,
    initialOffset: Float, ui: UiState, vm: AppViewModel, back: () -> Unit
) {
    var chapter by rememberSaveable { mutableIntStateOf(initialChapter.coerceIn(1, comic.chapters)) }
    var currentPage by rememberSaveable { mutableIntStateOf(initialPage.coerceIn(1, comic.pages)) }
    var currentOffset by rememberSaveable { mutableFloatStateOf(initialOffset.coerceIn(0f, .99999f)) }
    var tools by rememberSaveable { mutableStateOf(false) }
    var panel by remember { mutableStateOf("") }
    var endHint by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(false) }
    var touching by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var width by remember { mutableIntStateOf(0) }
    var restoring by remember { mutableStateOf(true) }
    var transferring by remember { mutableStateOf(false) }
    var pull by remember { mutableFloatStateOf(0f) }
    var changing by remember { mutableStateOf(false) }
    var transitionChapter by remember { mutableIntStateOf(initialChapter) }
    var transitionJob by remember { mutableStateOf<Job?>(null) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    val mode = ui.pref("readingMode", "纵向连续")
    val scroll = mode == "纵向连续"
    val rtl = mode == "从右向左"
    val list = rememberLazyListState((initialPage - 1).coerceIn(0, comic.pages - 1))
    val pager = rememberPagerState(initialPage = (initialPage - 1).coerceIn(0, comic.pages - 1), pageCount = { comic.pages })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    val continuousZoom = rememberZoomableState()
    var pagedZoom by remember { mutableStateOf<ZoomableState?>(null) }
    val activeZoom = if (scroll) continuousZoom else pagedZoom
    val zoomed = activeZoom?.userTransform?.scaleX?.let { it > 1.02f } == true
    var restoreHold by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val pages = remember(source, comic.id, chapter) { ReaderImages.demoPages(source.name, comic.id, chapter, comic.pages) }
    // Capture one layout snapshot; the mutable width can change before a launched effect starts.
    val viewportWidth = width
    val imageWidth = minOf(viewportWidth, with(density) { 850.dp.roundToPx() }).coerceAtLeast(1)
    val imageHeight = imageWidth * 930 / 640
    val threshold = with(density) { 180.dp.toPx() }
    val distance = with(density) { 112.dp.toPx() * (1 - kotlin.math.exp(-pull / 145.dp.toPx())) }

    fun position(): ReadingPosition {
        if (restoring || transferring) return ReadingPosition(source, comic.id, chapter, currentPage, currentOffset, mode)
        if (!scroll) return ReadingPosition(source, comic.id, chapter, pager.currentPage + 1, 0f, mode)
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == list.firstVisibleItemIndex }
        return ReadingPosition(source, comic.id, chapter, list.firstVisibleItemIndex + 1,
            ReaderMath.ratio(list.firstVisibleItemScrollOffset, item?.size ?: imageHeight), mode)
    }
    fun save() {
        if (restoring || transferring) return
        val value = position()
        vm.record(value.source, value.comicId, value.chapter, value.page, value.offsetRatio, value.mode)
    }
    suspend fun jump(page: Int, offset: Float = 0f) {
        if (transferring) return
        transferring = true
        try {
            currentPage = page.coerceIn(1, comic.pages); currentOffset = offset
            if (scroll) list.scrollToItem(currentPage - 1, ReaderMath.offset(offset, imageHeight))
            else pager.scrollToPage(currentPage - 1)
        } finally { transferring = false }
        save()
    }
    suspend fun changeChapter(next: Int) {
        auto = false
        if (next !in 1..comic.chapters) { endHint = true; return }
        if (transferring) return
        activeZoom?.reset()
        chapter = next
        jump(1)
        pull = 0f
    }
    suspend fun advance(next: Boolean): Boolean {
        if (restoring || transferring || changing || panel.isNotEmpty() || zoomed) return false
        if (scroll) {
            if (next && !list.canScrollForward || !next && !list.canScrollBackward) return false
            val viewport = list.layoutInfo.viewportEndOffset - list.layoutInfo.viewportStartOffset
            return kotlin.math.abs(list.animateScrollBy(viewport * .88f * if (next) 1f else -1f)) > 1f
        }
        val target = pager.currentPage + if (next) 1 else -1
        if (target !in 0 until comic.pages) return false
        pager.animateScrollToPage(target)
        return true
    }
    fun exit() { auto = false; save(); back() }
    val longPress: ((ZoomableState, Offset) -> Unit)? = if (ui.enabled("longPress")) ({ state, point ->
        if (restoreHold == null) {
            val scale = state.transform.scaleX
            val offset = state.transform.offset
            restoreHold = { state.scale(scale); state.offset(offset) }
            scope.launch { state.scale((scale * 2f).coerceAtMost(state.maxScale), centroidContentPointF = state.touchPointToContentPointF(point)) }
        }
    }) else null

    SideEffect { continuousZoom.setDisabledGestureTypes(if (ui.enabled("doubleTap", true)) 0 else GestureType.DOUBLE_TAP_SCALE or GestureType.ONE_FINGER_SCALE) }
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previousBehavior != null) controller?.systemBarsBehavior = previousBehavior
        }
    }
    LaunchedEffect(tools, window, view) {
        window?.let {
            val controller = WindowCompat.getInsetsController(it, view)
            if (tools) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    // A local function reference can compare equal after its captured mode changes.
    // Give lifecycle/volume callbacks a new identity when those captures change.
    val saveOnLifecycle = remember(mode, imageHeight) { { save() } }
    val turnOnVolume: (Boolean) -> Unit = remember(mode, zoomed) {
        { next: Boolean -> interaction++; scope.launch { if (!advance(next)) endHint = true } }
    }
    ReaderDeviceEffects(ui.enabled("keepAwake", true), ui.enabled("volume"),
        onTurn = turnOnVolume,
        onForeground = { resumed = it; if (!it) auto = false }, save = saveOnLifecycle)
    LaunchedEffect(mode, viewportWidth) {
        if (viewportWidth == 0) return@LaunchedEffect
        restoring = true
        activeZoom?.reset()
        if (scroll) {
            snapshotFlow { list.layoutInfo.totalItemsCount }.first { it > 0 }
            list.scrollToItem(currentPage - 1, ReaderMath.offset(currentOffset, imageHeight))
        } else pager.scrollToPage(currentPage - 1)
        restoring = false
        snapshotFlow { if (transferring) null else position() }.filterNotNull().collect { value ->
            currentPage = value.page; currentOffset = value.offsetRatio
        }
    }
    LaunchedEffect(source, comic.id, mode) {
        snapshotFlow { if (restoring || transferring) null else position() }.filterNotNull().sample(350).collect { value ->
            vm.record(source, comic.id, value.chapter, value.page, value.offsetRatio, value.mode)
        }
    }
    val preload = ui.pref("preload", "3 张").substringBefore(' ').toIntOrNull() ?: 3
    LaunchedEffect(pages, currentPage, preload, imageWidth, resumed) {
        if (!resumed || viewportWidth == 0) return@LaunchedEffect
        val semaphore = Semaphore(2)
        ReaderMath.prefetchPages(currentPage, preload, comic.pages).map { page ->
            launch { semaphore.withPermit { ReaderImages.loader(context).execute(ReaderImages.request(context, pages[page - 1], imageWidth)) } }
        }.joinAll()
    }
    LaunchedEffect(auto, mode, ui.pref("autoInterval", "5 秒"), resumed, touching, interaction, panel, zoomed, restoring) {
        if (!auto || !resumed || touching || panel.isNotEmpty() || zoomed || restoring) return@LaunchedEffect
        while (isActive && auto) {
            snapshotFlow { !list.isScrollInProgress && !pager.isScrollInProgress }.first { it }
            delay(ReaderMath.intervalMillis(ui.pref("autoInterval", "5 秒")))
            if (!advance(true)) { auto = false; endHint = true; break }
            if (scroll && !list.canScrollForward || !scroll && pager.currentPage == comic.pages - 1) { auto = false; break }
        }
    }
    LaunchedEffect(endHint) { if (endHint) { delay(1600); endHint = false } }
    LaunchedEffect(mode, panel, resumed) {
        if (!resumed || panel.isNotEmpty()) { transitionJob?.cancel(); changing = false; pull = 0f }
    }
    DisposableEffect(Unit) { onDispose { transitionJob?.cancel() } }
    BackHandler(enabled = tools || zoomed) {
        if (zoomed) activeZoom?.reset() else tools = false
    }
    val connection = remember(scroll, chapter, panel, threshold, zoomed) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (changing) return available
                if (pull > 0 && available.y > 0) { val consumed = available.y.coerceAtMost(pull); pull -= consumed; return Offset(0f, consumed) }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!scroll || panel.isNotEmpty() || changing || zoomed || source != NestedScrollSource.UserInput || available.y >= 0 || list.canScrollForward) return Offset.Zero
                if (chapter >= comic.chapters) { endHint = true; return available }
                auto = false
                pull = (pull - available.y).coerceAtMost(threshold * 1.5f)
                return available
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= 0) return Velocity.Zero
                if (pull >= threshold && !changing) {
                    transitionChapter = chapter + 1; changing = true
                    transitionJob = scope.launch { try { delay(640); changeChapter(chapter + 1) } finally { changing = false; pull = 0f } }
                } else pull = 0f
                return available
            }
        }
    }
    Box(Modifier.fillMaxSize().testTag("reader").onSizeChanged { width = it.width }
        .semantics { stateDescription = if (zoomed) "已放大" else "原始比例" }
        .background(Color(0xFF11141B)).pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touching = true; interaction++
                try {
                    do { val event = awaitPointerEvent(PointerEventPass.Initial) } while (event.changes.any { it.pressed })
                } finally {
                    touching = false; interaction++
                    restoreHold?.let { restore -> restoreHold = null; scope.launch { restore() } }
                }
            }
        }) {
        if (scroll) Box(Modifier.fillMaxSize().zoom(continuousZoom, onTap = { tools = !tools }, onLongPress = longPress?.let { callback -> { point -> callback(continuousZoom, point) } })) {
            LazyColumn(state = list, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize().testTag("reader-pages").nestedScroll(connection).graphicsLayer { translationY = -distance }, horizontalAlignment = Alignment.CenterHorizontally) {
                items(pages, key = { it.id }) { page ->
                    ReaderImage(page, "第 $chapter 话，第 ${pages.indexOf(page) + 1} 页", imageWidth,
                        Modifier.widthIn(max = 850.dp).fillMaxWidth().aspectRatio(page.width.toFloat() / page.height))
                }
            }
        } else HorizontalPager(state = pager, reverseLayout = rtl, modifier = Modifier.fillMaxSize().testTag("reader-paged-pages")) { page ->
            ReaderImage(pages[page], "第 $chapter 话，第 ${page + 1} 页", imageWidth, Modifier.fillMaxSize(),
                zoomable = true, doubleTap = ui.enabled("doubleTap", true), active = page == pager.currentPage,
                onZoomState = { if (page == pager.currentPage) pagedZoom = it }, onTap = { tools = !tools }, onLongPress = longPress)
        }
        if (pull > 0 && !changing) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(with(density) { distance.toDp() }).clipToBounds().background(Color(0xFFF5F3ED))) {
            Image(painterResource(readerPages[0]), null, Modifier.widthIn(max = 850.dp).fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).aspectRatio(640f / 930), contentScale = ContentScale.FillWidth, alignment = Alignment.TopCenter)
        }
        AnimatedVisibility(changing, enter = slideInVertically(tween(640)) { it }, exit = fadeOut(tween(0))) {
            Column(Modifier.fillMaxSize().background(Color(0xFF11141B)), horizontalAlignment = Alignment.CenterHorizontally) {
                for (i in 0..1) Image(painterResource(readerPages[i]), null, Modifier.widthIn(max = 850.dp).fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).aspectRatio(640f / 930), contentScale = ContentScale.FillWidth)
            }
        }
        if (tools) {
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0xED11141B)).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)), verticalAlignment = Alignment.CenterVertically) {
                IconAction(Glyph.Back, "退出阅读", ::exit)
                Column(Modifier.weight(1f)) { Text(comic.title, fontSize = 14.sp, maxLines = 1); Text("第 $chapter 话 · $mode", fontSize = 11.sp, color = Color.LightGray) }
                IconAction(if (auto) Glyph.Pause else Glyph.Play, if (auto) "停止自动翻页" else "开始自动翻页") { auto = !auto; if (auto) tools = false }
                IconAction(if (zoomed) Glyph.Close else Glyph.Search, if (zoomed) "还原缩放" else "放大图片") {
                    activeZoom?.let { state -> scope.launch { state.scale(if (zoomed) state.minScale else state.mediumScale, animated = true) } }
                }
                IconAction(Glyph.Settings, "阅读设置") { if (!changing) panel = "settings" }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xED11141B)).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)).padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${seeking?.toInt() ?: currentPage}", fontSize = 12.sp)
                    Slider(value = seeking ?: currentPage.toFloat(), onValueChange = { seeking = it; interaction++ },
                        onValueChangeFinished = { val target = seeking?.toInt(); seeking = null; if (target != null) scope.launch { jump(target) } },
                        enabled = !restoring && !changing, valueRange = 1f..comic.pages.toFloat(), modifier = Modifier.weight(1f).testTag("reader-progress"))
                    Text("${comic.pages}", fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { scope.launch { changeChapter(chapter - 1) } }, enabled = chapter > 1 && !changing) { Text("上一话") }
                    TextButton(onClick = { panel = "chapters" }, enabled = !changing) { AppIcon(Glyph.Menu); Spacer(Modifier.width(8.dp)); Text("目录") }
                    TextButton(onClick = { scope.launch { changeChapter(chapter + 1) } }, enabled = chapter < comic.chapters && !changing) { Text("下一话") }
                }
            }
        }
        if (endHint || pull > 0 || changing) Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = if (tools) 120.dp else 22.dp), color = Color(0xDD222222), shape = MaterialTheme.shapes.large) {
            Text(when { endHint -> if (chapter == comic.chapters && currentPage == comic.pages) "已经是最后一章了" else "已到本章边界"; changing -> "正在进入第 $transitionChapter 章"; pull >= threshold -> "松开进入下一章"; else -> "继续上拉进入下一章" }, Modifier.padding(12.dp, 6.dp), fontSize = 11.sp, color = Color.LightGray)
        }
    }
    if (panel.isNotEmpty()) ModalBottomSheet(onDismissRequest = { panel = ""; pull = 0f }) {
        Text(if (panel == "chapters") "目录" else "阅读设置", Modifier.padding(24.dp, 8.dp), style = MaterialTheme.typography.titleLarge)
        if (panel == "chapters") LazyColumn(Modifier.heightIn(max = 380.dp).testTag("reader-chapters")) {
            items((1..comic.chapters).toList()) { c -> SettingRow("第 $c 话", value = if (c == chapter) "正在阅读" else "", onClick = { panel = ""; scope.launch { changeChapter(c) } }) }
        } else LazyColumn(Modifier.heightIn(max = 480.dp)) {
            item { PreferenceChoice("阅读模式", "readingMode", listOf("纵向连续", "从左向右", "从右向左"), ui, vm) }
            item { PreferenceToggle("音量键翻页", "volume", ui, vm) }
            item { PreferenceChoice("自动翻页时间间隔", "autoInterval", listOf("2 秒", "3 秒", "5 秒", "10 秒", "15 秒", "30 秒", "60 秒"), ui, vm, "5 秒") }
            item { PreferenceToggle("保持屏幕常亮", "keepAwake", ui, vm, default = true) }
            item { PreferenceChoice("图片预加载", "preload", (1..10).map { "$it 张" }, ui, vm, "3 张") }
            item { PreferenceToggle("双击缩放", "doubleTap", ui, vm, default = true) }
            item { PreferenceToggle("长按缩放", "longPress", ui, vm, "按住临时放大，松开恢复") }
            item { Note("自动翻页在本章末停止。阅读进度保存在本机。") }
        }
        Spacer(Modifier.height(20.dp))
    }
}
