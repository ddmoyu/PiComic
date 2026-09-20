package io.github.ddmoyu.picomic

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.navigation.compose.*
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Real detail/reader navigation, synthetic metadata and plain local pixels; no websites or screenshots. */
class ContentDetailCacheFlowTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun returningFromReaderKeepsDetailsScrollAndLiveProgressWithoutFetchingAgain() {
        val vm = AppViewModel(ui.activity.application)
        val store = ViewModelStore().apply { put("detail-test", vm) }
        val key = ComicKey(Source.PICACG, "detail-cache-${System.nanoTime()}")
        val calls = AtomicInteger()
        var offline = false
        val picture = File(ui.activity.cacheDir, "${key.id}.png")
        val bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GRAY) }
        picture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val fixture = object : ComicSource {
            override val source = Source.PICACG
            override suspend fun search(query: ContentQuery): ContentPage<ComicSummary> = error("No discovery requests")
            override suspend fun categories() = emptyList<String>()
            override suspend fun details(id: String): ComicDetails {
                val count = calls.incrementAndGet()
                check(!offline) { "Fixture offline" }
                return ComicDetails(ComicSummary(key, "测试作品 $count"), "测试简介", (1..24).map { Chapter("$it", "测试第 $it 话", it) })
            }
            override suspend fun pages(comicId: String, chapter: Chapter) = (1..2).map {
                PageRef("${chapter.id}-$it", it - 1, picture.toURI().toString(), 40, 60)
            }
        }
        try {
            runBlocking {
                vm.network.awaitReady()
                vm.picacgAccount.cancel()
                vm.network.sessions.logout("picacg")
                vm.network.sessions.validateAndCommit(vm.network.sessions.begin("picacg"), SessionCandidate(CredentialKind.USER_TOKEN, "detail-fixture".toByteArray())) { ValidationResult.Verified("测试账号") }
                vm.library.awaitReady()
            }
            vm.content = ContentRepository(vm.network, picacgFactory = { fixture })
            ui.setContent { PiComicTheme(false, false) {
                val nav = rememberNavController()
                var selected by remember { mutableStateOf("1") }
                val state = UiState(preferences = mapOf("readingMode" to "从左向右"))
                NavHost(nav, startDestination = "detail") {
                    composable("detail") { ContentDetailScreen(key, state, vm, { selected = it; nav.navigate("reader") }, {}) }
                    composable("reader") { ContentReaderScreen(key, selected, state, vm, { nav.popBackStack() }, {}) }
                }
            } }
            ui.waitUntil(8000) { ui.onAllNodesWithTag("content-detail").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("content-detail").performScrollToNode(hasText("测试第 20 话"))
            offline = true
            repeat(2) {
                ui.onNodeWithText("测试第 20 话").assertIsDisplayed().performClick()
                ui.waitUntil(8000) { ui.onAllNodesWithTag("reader").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithTag("reader").performTouchInput { click(center) }
                ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("退出阅读").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithContentDescription("退出阅读").performClick()
                ui.waitUntil(5000) { ui.onAllNodesWithTag("content-detail").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("测试第 20 话").assertIsDisplayed()
                assertEquals(1, calls.get())
            }
            ui.waitUntil(5000) { vm.library.state.value.progress.any { it.key == key && it.chapterId == "20" } }
            ui.onNodeWithTag("content-detail").performScrollToIndex(0)
            ui.onNodeWithText("继续阅读").assertIsDisplayed()
            ui.onNodeWithText("上次读到第 1 页").assertIsDisplayed()
            offline = false
            ui.onNodeWithContentDescription("刷新详情").performClick()
            ui.waitUntil(5000) { ui.onAllNodesWithText("测试作品 2").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(2, calls.get())
            runBlocking { vm.network.sessions.logout("picacg") }
            ui.waitUntil(5000) { ui.onAllNodesWithText("管理账号").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("测试作品 2").assertDoesNotExist()
        } finally {
            runBlocking { vm.library.deleteHistory(key); vm.library.flush(); vm.network.sessions.logout("picacg") }
            ui.runOnIdle { store.clear() }
            picture.delete()
        }
    }
}
