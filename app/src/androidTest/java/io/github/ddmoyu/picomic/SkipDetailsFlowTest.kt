package io.github.ddmoyu.picomic

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Real app navigation with placeholder covers and generated gray pixels only. Run headlessly. */
class SkipDetailsFlowTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: AppViewModel
    private lateinit var store: ViewModelStore
    private lateinit var picture: File
    private lateinit var original: Map<String, String>
    private val books = (1..3).map { ComicSummary(ComicKey(Source.PICACG, "skip-${System.nanoTime()}-$it"), "阅读入口测试 $it") }
    private val chapters = listOf(Chapter("first-real-id", "测试首章", 3), Chapter("later-real-id", "测试后章", 9))
    private val opened = CopyOnWriteArrayList<Pair<String, String>>()

    @Before fun setup() {
        vm = AppViewModel(ui.activity.application)
        store = ViewModelStore().apply { put("skip-details", vm) }
        original = mapOf("skipDetails" to "false", "readingMode" to "纵向连续", "debugDemo" to "false", "checkOnStart" to "true")
            .mapValues { (key, default) -> vm.state.value.pref(key, default) }
        picture = File.createTempFile("skip-details-", ".png", ui.activity.cacheDir)
        val bitmap = Bitmap.createBitmap(80, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GRAY) }
        try { picture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
        val fixture = object : ComicSource {
            override val source = Source.PICACG
            override suspend fun search(query: ContentQuery) = ContentPage(books)
            override suspend fun categories() = listOf("测试分类")
            override suspend fun details(id: String) = ComicDetails(books.single { it.key.id == id }, "测试简介", chapters)
            override suspend fun pages(comicId: String, chapter: Chapter): List<PageRef> {
                opened += comicId to chapter.id
                return (1..4).map { PageRef("${chapter.id}-page-$it", it - 1, picture.toURI().toString(), 80, 600) }
            }
        }
        runBlocking {
            vm.network.awaitReady(); vm.picacgAccount.cancel(); vm.network.sessions.logout("picacg")
            vm.network.sessions.validateAndCommit(vm.network.sessions.begin("picacg"), SessionCandidate(CredentialKind.USER_TOKEN, "skip-fixture".toByteArray())) { ValidationResult.Verified("测试账号") }
            vm.library.awaitReady()
        }
        vm.content = ContentRepository(vm.network, picacgFactory = { fixture })
        ui.runOnIdle {
            vm.preference("debugDemo", "false"); vm.preference("checkOnStart", "false")
            vm.preference("readingMode", "从左向右"); vm.preference("skipDetails", "false"); vm.source(Source.PICACG)
        }
    }

    @After fun cleanup() {
        runBlocking {
            books.forEach { vm.library.deleteHistory(it.key) }; vm.library.flush()
            vm.network.sessions.logout("picacg")
        }
        ui.runOnIdle { original.forEach(vm::preference); store.clear() }
        picture.delete()
    }

    private fun waitReader(page: Int) {
        ui.waitUntil(8000) { ui.onAllNodesWithTag("reader-page-$page").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("reader-page-$page").assertIsDisplayed()
        ui.onNodeWithTag("content-detail").assertDoesNotExist()
    }

    private fun exitReader() {
        ui.onNodeWithTag("reader").performTouchInput { click(center) }
        ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("退出阅读").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("退出阅读").performClick()
        ui.onNodeWithTag("content-detail").assertDoesNotExist()
    }

    private fun toggleInSettings() {
        ui.onNodeWithContentDescription("设置").performClick()
        ui.onNodeWithText("阅读", substring = false).performClick()
        ui.onNodeWithText("跳过详情页").assertIsDisplayed().performClick()
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithContentDescription("返回").performClick()
    }

    @Test fun coverUsesFirstPageOrHistoryAndBackReturnsToItsListWhileTheSwitchCanRestoreDetails() {
        runBlocking {
            vm.library.record(books[1], ContentProgress(books[1].key, chapters[1].id, "later-real-id-page-3", 3, 0f, "从左向右"))
            vm.library.record(books[2], ContentProgress(books[2].key, "removed-chapter", "removed-page", 4, .5f, "从左向右"))
            vm.library.flush()
        }
        ui.setContent { PiComicApp(vm) }
        assertFalse(vm.state.value.enabled("skipDetails"))
        toggleInSettings()
        assertTrue(vm.state.value.enabled("skipDetails"))
        val exported = runBlocking { vm.backups.snapshot() }
        assertTrue(exported.preferences.any { it.key == "skipDetails" && it.value == "true" })
        ui.onNodeWithTag("comic-row-${books[0].key.stable}").performClick()
        waitReader(1)
        assertEquals(books[0].key.id to chapters[0].id, opened.last())
        exitReader()
        ui.onNodeWithTag("content-list-PICACG").assertIsDisplayed()
        ui.onNodeWithContentDescription("书架").performClick()
        ui.onNodeWithText("阅读历史", substring = false).performClick()
        val history = ui.onNodeWithTag("history-list")
        history.performScrollToNode(hasTestTag("comic-row-${books[1].key.stable}"))
        ui.onNodeWithTag("comic-row-${books[1].key.stable}").performClick()
        waitReader(3)
        assertEquals(books[1].key.id to chapters[1].id, opened.last())
        exitReader()
        history.assertIsDisplayed().performScrollToNode(hasTestTag("comic-row-${books[2].key.stable}"))
        ui.onNodeWithTag("comic-row-${books[2].key.stable}").performClick()
        waitReader(1)
        assertEquals(books[2].key.id to chapters[0].id, opened.last())
        ui.onNodeWithText("原章节已变化，已从首章开始").assertIsDisplayed()
        exitReader()
        toggleInSettings()
        assertFalse(vm.state.value.enabled("skipDetails"))
        history.performScrollToNode(hasTestTag("comic-row-${books[1].key.stable}"))
        ui.onNodeWithTag("comic-row-${books[1].key.stable}").performClick()
        ui.waitUntil(5000) { ui.onAllNodesWithTag("content-detail").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("content-detail").assertIsDisplayed()
        ui.onNodeWithTag("reader").assertDoesNotExist()
    }

    @Test fun directOpeningRestoresContinuousScrollOffsetAfterTheHistoryLoads() {
        val book = books[1]
        runBlocking {
            vm.library.record(book, ContentProgress(book.key, chapters[1].id, "later-real-id-page-3", 3, .27f, "纵向连续", updatedAt = 1000))
        }
        ui.setContent { PiComicTheme(false, false) {
            ContentReaderScreen(book.key, null, UiState(preferences = mapOf("readingMode" to "纵向连续")), vm, {}, {})
        } }
        waitReader(3)
        ui.waitUntil(8000) { vm.library.state.value.progress.any { it.key == book.key && it.updatedAt > 1000 } }
        val position = vm.library.state.value.progress.single { it.key == book.key }
        assertEquals(chapters[1].id, position.chapterId)
        assertEquals("later-real-id-page-3", position.pageId)
        assertEquals(3, position.page)
        assertEquals(.27f, position.offset, .02f)
    }
}
