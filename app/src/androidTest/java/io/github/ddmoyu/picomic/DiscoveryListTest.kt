package io.github.ddmoyu.picomic

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Headless layout test using synthetic text and placeholder covers. No websites, images or screenshots. */
class DiscoveryListTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun coversStayLeftTitlesEllipsizeAndMetadataPaginationAndOpeningWork() = verifyList(category = false)

    @Test fun categoryChipsOpenAFilteredListAndRemainChipsOnReturn() = verifyList(category = true)
    @Test fun narrowLargeTextRowsKeepMetadataAndActionsReachable() = verifyList(category = false, width = 320, fontScale = 1.6f)

    private fun verifyList(category: Boolean, width: Int = 360, fontScale: Float = 1f) {
        val vm = AppViewModel(ui.activity.application)
        val store = ViewModelStore().apply { put("discover-test", vm) }
        val source = Source.PICACG
        val first = ComicSummary(ComicKey(source, "fixture-1"), "这是非常长的测试漫画标题".repeat(20), "测试作者",
            tags = listOf("测试分类", "日常", "短篇", "不应挤出第四个标签"), pageCount = 24, language = "中文")
        val second = ComicSummary(ComicKey(source, "fixture-2"), "第二本测试漫画", pageCount = 0)
        val third = ComicSummary(ComicKey(source, "fixture-3"), "下一页的测试漫画", "另一位作者", tags = listOf("测试分类"))
        var selected: ComicKey? = null
        val requests = java.util.concurrent.CopyOnWriteArrayList<ContentQuery>()
        val fixture = object : ComicSource {
            override val source = Source.PICACG
            override suspend fun categories() = listOf("测试分类")
            override suspend fun search(query: ContentQuery): ContentPage<ComicSummary> {
                requests += query
                return if (query.page == 1) ContentPage(listOf(first, second), 2) else ContentPage(listOf(third))
            }
            override suspend fun details(id: String): ComicDetails = error("Discovery must not request individual details")
            override suspend fun pages(comicId: String, chapter: Chapter): List<PageRef> = error("No reader requests")
        }
        try {
            runBlocking {
                vm.network.awaitReady(); vm.picacgAccount.cancel(); vm.network.sessions.logout("picacg")
                vm.network.sessions.validateAndCommit(vm.network.sessions.begin("picacg"), SessionCandidate(CredentialKind.USER_TOKEN, "discovery-fixture".toByteArray())) { ValidationResult.Verified("测试账号") }
                vm.library.awaitReady()
                vm.library.record(first, ContentProgress(first.key, "1", "1", 1, 0f, "纵向连续"))
                vm.library.flush()
            }
            vm.content = ContentRepository(vm.network, picacgFactory = { fixture })
            ui.runOnIdle {
                vm.preference("debugDemo", "false"); vm.preference("checkOnStart", "false")
                vm.source(source)
            }
            ui.setContent { PiComicTheme(false, false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                Box(Modifier.width(width.dp).fillMaxHeight()) {
                    if (category) PiComicApp(vm)
                    else ContentBrowseScreen(false, UiState(), vm, { selected = it }, { _, _ -> }, {})
                }
                }
            } }
            if (category) {
                ui.onNodeWithContentDescription("分类").performClick()
                ui.waitUntil(5000) { ui.onAllNodesWithTag("categories-PICACG").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithTag("content-list-PICACG").assertDoesNotExist()
                ui.onNodeWithText("测试分类").performClick()
            }
            ui.waitUntil(5000) { ui.onAllNodesWithTag("comic-row-${first.key.stable}").fetchSemanticsNodes().isNotEmpty() }
            val row = ui.onNodeWithTag("comic-row-${first.key.stable}")
            ui.onNodeWithTag("comic-meta-${first.key.stable}", useUnmergedTree = true)
                .assertTextEquals(if (category) "picacg · fixture-1 · 中文" else "fixture-1 · 中文")
            ui.onNodeWithText("继续上次阅读", substring = true).assertDoesNotExist()
            ui.onNodeWithText("继续阅读", substring = true).assertDoesNotExist()
            val cover = ui.onNodeWithTag("comic-cover-${first.key.stable}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val title = ui.onNodeWithTag("comic-title-${first.key.stable}", useUnmergedTree = true)
            val titleBounds = title.fetchSemanticsNode().boundsInRoot
            assertTrue(cover.right < titleBounds.left)
            assertEquals(cover.top, titleBounds.top, 1f)
            assertTrue(titleBounds.right <= row.fetchSemanticsNode().boundsInRoot.right)
            val layout = mutableListOf<TextLayoutResult>()
            title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            assertEquals(2, layout.single().lineCount)
            assertTrue(layout.single().isLineEllipsized(1))
            row.assertTextContains("测试作者").assertTextContains("测试分类").assertTextContains("日常").assertTextContains("短篇")
            val count = ui.onNodeWithTag("comic-page-count-${first.key.stable}", useUnmergedTree = true)
            count.assertTextEquals("共 24 张图片").assertIsDisplayed()
            assertTrue(count.fetchSemanticsNode().boundsInRoot.left > cover.right)
            ui.onNodeWithText("不应挤出第四个标签", useUnmergedTree = true).assertDoesNotExist()
            if (!category) {
                row.performClick()
                ui.runOnIdle { assertEquals(first.key, selected) }
            }
            val list = ui.onNodeWithTag("content-list-PICACG")
            list.performScrollToNode(hasText("作者未提供"))
            ui.onNodeWithText("作者未提供", useUnmergedTree = true).assertIsDisplayed()
            ui.onNodeWithTag("comic-page-count-${second.key.stable}", useUnmergedTree = true).assertDoesNotExist()
            list.performScrollToNode(hasText("加载更多"))
            ui.onNodeWithText("加载更多").performClick()
            ui.waitUntil(5000) { requests.any { it.page == 2 } }
            list.performScrollToNode(hasText(third.title))
            ui.onNodeWithTag("comic-page-count-${third.key.stable}", useUnmergedTree = true).assertDoesNotExist()
            if (category) {
                assertEquals(listOf(1, 2), requests.filter { it.category == "测试分类" }.map { it.page })
                ui.onNodeWithContentDescription("返回").performClick()
                ui.onNodeWithTag("categories-PICACG").assertIsDisplayed()
                ui.onNodeWithTag("content-list-PICACG").assertDoesNotExist()
            } else {
                ui.onNodeWithTag("comic-row-${third.key.stable}").performClick()
                ui.runOnIdle { assertEquals(third.key, selected); assertEquals(listOf(1, 2), requests.map { it.page }) }
            }
        } finally {
            runBlocking { vm.library.deleteHistory(first.key); vm.library.flush(); vm.network.sessions.logout("picacg") }
            ui.runOnIdle { store.clear() }
        }
    }
}
