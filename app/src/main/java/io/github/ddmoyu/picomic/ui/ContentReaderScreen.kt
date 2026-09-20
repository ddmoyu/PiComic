package io.github.ddmoyu.picomic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
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
    val readerPages: List<ReaderPage>, val initialPage: Int, val offset: Float, val relocated: Boolean)

@Composable fun ContentReaderScreen(comicKey: ComicKey, chapterId: String, ui: UiState, vm: AppViewModel, back: () -> Unit, login: (Source) -> Unit) {
    var selected by rememberSaveable(comicKey.stable) { mutableStateOf(chapterId) }
    var chapter by remember(comicKey, selected) { mutableStateOf<OpenChapter?>(null) }
    var error by remember(comicKey, selected) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val revisions by vm.network.sessions.changes.collectAsStateWithLifecycle()
    val network by vm.network.state.collectAsStateWithLifecycle()
    val routes by vm.jmRoutes.state.collectAsStateWithLifecycle()
    val htRoutes by vm.htRoutes.state.collectAsStateWithLifecycle()
    val library by vm.library.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(comicKey, selected, retry, revisions, network.generation, routes.selected, htRoutes.selected, ui.pref("jm.image"), ui.pref("nh.auth"), ui.pref("eh.original"), ui.pref("eh.warning"), ui.pref("eh.subtitle")) {
        chapter = null; error = null
        try {
            val history = vm.library.awaitReady().progress.firstOrNull { it.key == comicKey && it.chapterId == selected }
            val loaded = vm.content.run(comicKey.source) { adapter, partition ->
                val detail = adapter.details(comicKey.id)
                val index = detail.chapters.indexOfFirst { it.id == selected }
                if (index < 0) throw ContentFailure(ContentFailureKind.NOT_FOUND, "章节已变化，请返回目录选择章节")
                val pages = adapter.pages(comicKey.id, detail.chapters[index])
                Triple(detail, pages, partition)
            }
            val (details, pages, partition) = loaded
            val relocated = history != null && pages.none { it.id == history.pageId }
            val initial = history?.let { saved -> pages.indexOfFirst { it.id == saved.pageId }.takeIf { it >= 0 } ?: (saved.page - 1).coerceIn(pages.indices) } ?: 0
            val models = pages.map { page -> ReaderPage("${comicKey.stable}/$selected/${page.id}/$partition", if (page.resolver != null) SourceImage(comicKey, page, partition, vm.content) else page.jm?.let { io.github.ddmoyu.picomic.source.jm.JmImage(page.url, it, partition) } ?: page.url, page.width ?: 640, page.height ?: 930,
                page.width != null && page.height != null) }.toMutableList()
            // Know the resumed page's intrinsic ratio before restoring a proportional scroll offset.
            if (pages[initial].width == null || pages[initial].height == null) {
                val image = ReaderImages.loader(context).execute(ImageRequest.Builder(context).data(models[initial].model).size(800, 800).build())
                if (image is SuccessResult && image.image.width > 0 && image.image.height > 0)
                    models[initial] = models[initial].copy(width = image.image.width, height = image.image.height, dimensionsKnown = true)
            }
            chapter = OpenChapter(details, details.chapters.indexOfFirst { it.id == selected }, pages, partition, models,
                initial + 1, if (relocated) 0f else history?.offset ?: 0f, relocated)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = contentError(e) }
    }
    val current = chapter
    if (current == null) {
        if (error == null) CircularProgressIndicator(Modifier.padding(32.dp))
        else ContentFailurePanel(error!!, { retry++ }, { login(comicKey.source) })
    } else key(comicKey, selected, current.partition) {
        val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
        LaunchedEffect(current) { if (current.relocated) snackbar.showSnackbar("原页面已变化，已定位到相邻页，请核对阅读位置") }
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
