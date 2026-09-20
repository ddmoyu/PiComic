package io.github.ddmoyu.picomic

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.espresso.Espresso.pressBack
import coil3.decode.DataSource
import coil3.request.SuccessResult
import io.github.ddmoyu.picomic.reader.*
import io.github.ddmoyu.picomic.ui.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Run headlessly; all image data is a generated solid-color fixture. No websites or screenshots. */
class ReaderPreloadUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun expandedPreloadPickerClosesWithOneBackAndSelectionPersists() {
        val vm = AppViewModel(ui.activity.application)
        val store = ViewModelStore().apply { put("test", vm) }
        var value by mutableStateOf("3 张")
        try {
            ui.setContent { PiComicTheme(false, false) {
                PreferenceChoice("图片预加载", "preload", (1..10).map { "$it 张" },
                    UiState(preferences = mapOf("preload" to value)), vm, save = { value = it })
            } }
            ui.onNodeWithText("图片预加载").performClick()
            ui.onNode(isDialog()).performTouchInput { swipeUp() }
            pressBack()
            ui.waitUntil(5000) { ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
            ui.onNodeWithText("图片预加载").performClick()
            ui.onNodeWithText("4 张").performScrollTo().performClick()
            ui.runOnIdle { assertEquals("4 张", value) }
            ui.onNodeWithText("图片预加载").performClick()
            pressBack()
            ui.waitUntil(5000) { ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
            ui.onNodeWithText("4 张").assertIsDisplayed()
        } finally { ui.runOnIdle { store.clear() } }
    }

    @Test fun readerSettingsCloseWithOneBackAfterChangingPreload() {
        val vm = AppViewModel(ui.activity.application)
        val store = ViewModelStore().apply { put("test", vm) }
        val bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GRAY) }
        val book = ReaderBook("fixture", "阅读测试", listOf("测试章节"), 8, false) {
            (1..8).map { ReaderPage("fixture/$it", bitmap, 40, 60) }
        }
        var exited = false
        val original = vm.state.value.pref("preload", "3 张")
        try {
            ui.runOnIdle { vm.preference("preload", "3 张") }
            ui.setContent { PiComicTheme(false, false) {
                val state by vm.state.collectAsState()
                ReaderSurface(book, 1, 1, 0f, state, vm, { exited = true }, {})
            } }
            ui.onNodeWithTag("reader").performTouchInput { click(center) }
            ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("阅读设置").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithContentDescription("阅读设置").performClick()
            ui.onNodeWithTag("reader-settings").performScrollToNode(hasText("图片预加载"))
            ui.onNodeWithText("图片预加载").performClick()
            ui.onNode(hasText("4 张") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
            ui.runOnIdle { assertEquals("4 张", vm.state.value.pref("preload")) }
            pressBack()
            ui.waitUntil(5000) { ui.onAllNodesWithTag("reader-settings").fetchSemanticsNodes().isEmpty() }
            ui.onNodeWithTag("reader").assertIsDisplayed()
            ui.runOnIdle { assertFalse(exited) }
        } finally { ui.runOnIdle { vm.preference("preload", original); store.clear() } }
    }

    @Test fun pendingImageShowsProgressThenBothReaderModesReusePrefetchedCache() {
        val release = CountDownLatch(1)
        val bytes = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GRAY) }
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    release.await(15, TimeUnit.SECONDS)
                    return MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(bytes))
                }
            }
            server.start()
            var page by mutableStateOf(ReaderPage("loading-${server.port}", server.url("/fixture.png").toString(), 40, 60))
            var zoomable by mutableStateOf(false)
            var completed = 0
            try {
                ui.setContent { PiComicTheme(false, false) {
                    ReaderImage(page, "测试图片", 200, Modifier.fillMaxSize(), zoomable = zoomable,
                        onDimensions = { _, _ -> completed++ })
                } }
                ui.onNodeWithContentDescription("图片正在加载").assertIsDisplayed()
                release.countDown()
                ui.waitUntil(5000) { completed > 0 }
                ui.onNodeWithContentDescription("图片正在加载").assertDoesNotExist()
                val result = runBlocking { ReaderImages.loader(ui.activity).execute(ReaderImages.request(ui.activity, page, 200)) }
                assertTrue(result is SuccessResult)
                assertEquals(DataSource.MEMORY_CACHE, (result as SuccessResult).dataSource)
                val prefetched = ReaderPage("prefetched-${server.port}", server.url("/prefetch.png").toString(), 40, 60)
                val preload = runBlocking { ReaderImages.loader(ui.activity).execute(ReaderImages.request(ui.activity, prefetched, 200)) }
                assertTrue(preload is SuccessResult)
                ui.runOnIdle { page = prefetched }
                ui.waitUntil(5000) { completed > 1 }
                ui.onNodeWithContentDescription("图片正在加载").assertDoesNotExist()
                ui.runOnIdle { zoomable = true }
                ui.waitUntil(5000) { completed > 2 }
                ui.onNodeWithContentDescription("图片正在加载").assertDoesNotExist()
                assertEquals(2, server.requestCount)
            } finally { release.countDown() }
        }
    }
}
