package io.github.ddmoyu.picomic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.reader.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private data class OpenChapter(val details: ComicDetails, val index: Int, val pages: List<PageRef>, val partition: String,
    val readerPages: List<ReaderPage>, val initialPage: Int, val offset: Float, val notice: String?)

@Composable fun ContentReaderScreen(comicKey: ComicKey, chapterId: String?, ui: UiState, vm: AppViewModel, back: () -> Unit, login: (Source) -> Unit) {
    var selected by rememberSaveable(comicKey.stable, chapterId) { mutableStateOf(chapterId) }
    var chapter by remember(comicKey, selected) { mutableStateOf<OpenChapter?>(null) }
    var error by remember(comicKey, selected) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val detailContext = contentDetailContext(comicKey.source, ui, vm)
    val library by vm.library.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(comicKey, selected, retry, detailContext) {
        chapter = null; error = null
        try {
            val saved = vm.library.readingPosition(comicKey)
            val detail = vm.detailCache.load(comicKey, detailContext, force = retry > 0) {
                vm.content.run(comicKey.source) { adapter, _ -> adapter.details(comicKey.id) }
            }
            if (detail.chapters.isEmpty()) throw ContentFailure(ContentFailureKind.NOT_FOUND, "当前作品暂无可阅读章节")
            val index = detail.chapters.indexOfFirst { it.id == (selected ?: saved?.chapterId) }.let {
                if (it >= 0) it else if (selected == null) 0 else throw ContentFailure(ContentFailureKind.NOT_FOUND, "章节已变化，请返回目录选择章节")
            }
            val selectedId = detail.chapters[index].id
            val history = saved?.takeIf { it.chapterId == selectedId }
            val (pages, partition) = vm.content.run(comicKey.source) { adapter, partition ->
                adapter.pages(comicKey.id, detail.chapters[index]) to partition
            }
            if (pages.isEmpty()) throw ContentFailure(ContentFailureKind.NOT_FOUND, "当前章节暂无可阅读图片")
            val relocated = history != null && pages.none { it.id == history.pageId }
            val initial = history?.let { saved -> pages.indexOfFirst { it.id == saved.pageId }.takeIf { it >= 0 } ?: (saved.page - 1).coerceIn(pages.indices) } ?: 0
            val models = pages.map { page -> ReaderPage("${comicKey.stable}/$selectedId/${page.id}/$partition", if (page.resolver != null) SourceImage(comicKey, page, partition, vm.content) else page.jm?.let { io.github.ddmoyu.picomic.source.jm.JmImage(page.url, it, partition) } ?: page.url, page.width ?: 640, page.height ?: 930,
                page.width != null && page.height != null) }.toMutableList()
            // Know the resumed page's intrinsic ratio before restoring a proportional scroll offset.
            if (pages[initial].width == null || pages[initial].height == null) {
                val image = ReaderImages.loader(context).execute(ImageRequest.Builder(context).data(models[initial].model).size(800, 800).build())
                if (image is SuccessResult && image.image.width > 0 && image.image.height > 0)
                    models[initial] = models[initial].copy(width = image.image.width, height = image.image.height, dimensionsKnown = true)
            }
            val notice = when {
                selected == null && saved != null && history == null -> "原章节已变化，已从首章开始"
                relocated -> "原页面已变化，已定位到相邻页，请核对阅读位置"
                else -> null
            }
            chapter = OpenChapter(detail, index, pages, partition, models,
                initial + 1, if (relocated) 0f else history?.offset ?: 0f, notice)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = contentError(e) }
    }
    val current = chapter
    if (current == null) {
        if (error == null) ContentLoading()
        else ContentFailurePanel(error!!, { retry++ }, { login(comicKey.source) })
    } else key(comicKey, selected, current.partition) {
        val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
        LaunchedEffect(current) { current.notice?.let { snackbar.showSnackbar(it) } }
        val book = remember(current) { ReaderBook(comicKey.stable, current.details.summary.title,
            current.details.chapters.map { it.title }, current.pages.size, true) { current.readerPages } }
        val effective = ui.copy(preferences = ui.preferences + library.preferences[comicKey].orEmpty())
        Box(Modifier.fillMaxSize()) {
        ReaderSurface(book, current.index + 1, current.initialPage, current.offset, effective, vm, back,
            onRecord = { position ->
                val page = current.pages[position.page.coerceIn(1, current.pages.size) - 1]
                vm.library.record(current.details.summary, ContentProgress(comicKey, current.details.chapters[current.index].id, page.id, position.page, position.offsetRatio, position.mode))
            }, onChapter = { index -> selected = current.details.chapters[index - 1].id },
            onReadingPreference = { name, value -> vm.library.preference(comicKey, name, value) },
            resetPreferences = { listOf("readingMode", "readerBackground", "readerOrientation", "readerBrightness").forEach { vm.library.preference(comicKey, it, null) } })
        androidx.compose.material3.SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}
