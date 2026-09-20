package io.github.ddmoyu.picomic

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import io.github.ddmoyu.picomic.reader.ReaderPage
import io.github.ddmoyu.picomic.ui.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Headless tests with localhost responses and generated plain pixels only. */
class ReaderRetryUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun retryRestartsCurrentPageAndThreeAheadWithoutRetryingEarlierFailures() {
        val vm = AppViewModel(ui.activity.application)
        val store = ViewModelStore().apply { put("retry-test", vm) }
        val calls = ConcurrentHashMap<Int, Int>()
        val pixels = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GRAY) }
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val page = request.requestUrl!!.pathSegments.last().substringBefore('.').toInt()
                    val attempt = calls.merge(page, 1, Int::plus)!!
                    return if (attempt == 1) MockResponse().setResponseCode(503)
                    else MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(pixels))
                }
            }
            server.start()
            val book = ReaderBook("retry-${server.port}", "测试", listOf("测试章"), 12, true) {
                (1..12).map { ReaderPage("retry-${server.port}/$it", server.url("/$it.png").toString(), 40, 60) }
            }
            try {
                ui.setContent { PiComicTheme(false, false) {
                    ReaderSurface(book, 1, 5, 0f, UiState(preferences = mapOf("readingMode" to "从左向右", "preload" to "3 张", "readerBackground" to "纯黑")), vm, {}, {})
                } }
                ui.waitUntil(8000) { (4..8).all { calls[it] == 1 } }
                val current = hasAnyAncestor(hasTestTag("reader-page-5"))
                ui.onNode(hasText("图片加载失败，请重试") and current).assertIsDisplayed()
                ui.onNode(hasText("重试") and current).performClick()
                ui.waitUntil(8000) { (5..8).all { calls[it] == 2 } && ui.onAllNodes(hasText("重试") and current).fetchSemanticsNodes().isEmpty() }
                ui.runOnIdle {
                    assertEquals(1, calls[4])
                    assertEquals(setOf(4, 5, 6, 7, 8), calls.keys)
                    (5..8).forEach { assertEquals(2, calls[it]) }
                }
            } catch (failure: Throwable) {
                throw AssertionError("Fixture request counts: $calls", failure)
            } finally { ui.runOnIdle { store.clear() } }
        }
    }

    @Test fun longImageKeepsLoadingAndErrorControlsInTheVisibleArea() {
        val vm = AppViewModel(ui.activity.application)
        val store = ViewModelStore().apply { put("long-test", vm) }
        val release = CountDownLatch(1)
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    release.await(15, TimeUnit.SECONDS)
                    return MockResponse().setResponseCode(503)
                }
            }
            server.start()
            val book = ReaderBook("long-${server.port}", "测试长图", listOf("测试章"), 2, true) {
                (1..2).map { ReaderPage("long-${server.port}/$it", server.url("/$it.png").toString(), 100, 1000) }
            }
            try {
                ui.setContent { PiComicTheme(false, false) {
                    ReaderSurface(book, 1, 1, 0f, UiState(preferences = mapOf("readingMode" to "纵向连续", "readerBackground" to "纯黑")), vm, {}, {})
                } }
                val first = hasAnyAncestor(hasTestTag("reader-page-1"))
                val spinner = ui.onNode(hasContentDescription("图片正在加载") and first)
                spinner.assertIsDisplayed()
                ui.onNodeWithTag("reader-pages").performTouchInput { swipeUp() }
                spinner.assertIsDisplayed()
                release.countDown()
                ui.waitUntil(8000) { ui.onAllNodes(hasText("重试") and first).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasText("图片加载失败，请重试") and first).assertIsDisplayed()
                ui.onNode(hasText("重试") and first).assertIsDisplayed()
            } finally { release.countDown(); ui.runOnIdle { store.clear() } }
        }
    }
}
