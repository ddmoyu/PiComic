package io.github.ddmoyu.picomic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.ui.AppViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class ContentFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(ui.activity)[AppViewModel::class.java]
    private var fixtureComic: ComicSummary? = null
    @Before fun setup() {
        ui.runOnIdle { vm.picacgAccount.cancel(); vm.preference("debugDemo", "false"); vm.source(Source.PICACG); vm.preference("readingMode", "从左向右") }
        runBlocking { vm.network.awaitReady(); vm.network.sessions.logout("picacg") }
        ui.onNode(hasText("picacg") and hasClickAction()).performScrollTo().performClick()
    }
    @After fun cleanup() {
        runBlocking {
            fixtureComic?.let { vm.library.favorite(it, false); vm.library.deleteHistory(it.key); vm.library.flush() }
            vm.network.sessions.logout("picacg")
        }
        ui.runOnIdle { vm.preference("readingMode", "纵向连续") }
    }
    @Test fun defaultUiDoesNotExposeDemoAndProvidesLoginAction() {
        ui.waitUntil(5000) { ui.onAllNodesWithText("管理账号").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("雨后的第七站").assertDoesNotExist()
        ui.onNodeWithText("请先登录并验证哔咔账号").assertExists()
        ui.onNodeWithText("管理账号").performClick()
        ui.onNodeWithText("账号 / 邮箱").assertExists()
    }
    @Test fun realModelNavigatesSearchDetailReadingHistoryAndKeepsChapterIds() {
        val id = "fixture-string-${System.nanoTime()}"
        val key = ComicKey(Source.PICACG, id)
        val comic = ComicSummary(key, "内容闭环夹具 ${id.takeLast(6)}", "测试作者", "android.resource://${ui.activity.packageName}/${R.drawable.cover_0}")
        fixtureComic = comic
        val chapters = listOf(Chapter("chapter-a", "上篇", 3), Chapter("chapter-b", "下篇", 9))
        val adapter = object : ComicSource {
            override val source = Source.PICACG
            override suspend fun search(query: ContentQuery) = ContentPage(listOf(comic))
            override suspend fun categories() = listOf("夹具分类")
            override suspend fun details(id: String) = ComicDetails(comic, "夹具简介", chapters)
            override suspend fun pages(comicId: String, chapter: Chapter) = (1..2).map {
                PageRef("${chapter.id}-page-$it", it - 1, "android.resource://${ui.activity.packageName}/${R.drawable.page_1}", 640, 930)
            }
        }
        ui.runOnIdle { vm.content = ContentRepository(vm.network) { adapter } }
        runBlocking { vm.network.sessions.validateAndCommit(vm.network.sessions.begin("picacg"), SessionCandidate(CredentialKind.USER_TOKEN, "fixture-token".toByteArray())) { ValidationResult.Verified("夹具账号") } }
        ui.waitUntil(10000) { ui.onAllNodesWithText(comic.title).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("搜索").performClick()
        ui.onNodeWithText("作品、作者或标签").performTextInput("内容闭环")
        ui.onNodeWithText("搜索").performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText(comic.title).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(comic.title).performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("开始阅读").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("收藏作品").performClick()
        ui.onNodeWithText("开始阅读").performClick()
        ui.waitUntil(10000) { vm.library.state.value.progress.any { it.key == key } }
        assertEquals("chapter-a", vm.library.state.value.progress.first { it.key == key }.chapterId)
        ui.onNodeWithTag("reader").performTouchInput { click(center) }
        ui.waitUntil(5000) { ui.onAllNodesWithText("下一话").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("下一话").performClick()
        ui.waitUntil(10000) { vm.library.state.value.progress.any { it.key == key && it.chapterId == "chapter-b" } }
        ui.onNodeWithTag("reader").performTouchInput { click(center) }
        ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("退出阅读").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("退出阅读").performClick()
        runBlocking { vm.library.flush() }
        val progress = vm.library.state.value.progress.first { it.key == key }
        assertEquals("chapter-b-page-1", progress.pageId)
        assertTrue(key in vm.library.state.value.favorites)
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithContentDescription("书架").performClick()
        ui.onNodeWithText(comic.title).assertExists()
        ui.onNodeWithText("阅读历史").performClick()
        ui.onNodeWithText(comic.title).assertExists()
    }
}
