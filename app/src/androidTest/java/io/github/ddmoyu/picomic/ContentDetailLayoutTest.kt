package io.github.ddmoyu.picomic

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.download.*
import io.github.ddmoyu.picomic.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Placeholder cover and synthetic text only; run headless without displaying remote content. */
class ContentDetailLayoutTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun groupedTagsEqualActionsAndTwoColumnChaptersKeepTheirBehavior() {
        val vm = AppViewModel(ui.activity.application)
        val store = ViewModelStore().apply { put("detail-layout", vm) }
        val key = ComicKey(Source.PICACG, "detail-layout-${System.nanoTime()}")
        val longTag = "测试长标签自动换行".repeat(8)
        val summary = ComicSummary(key, "测试作品标题与封面布局", "作者甲、作者乙", tags = listOf("测试分类", "测试标签", longTag), language = "中文", pageCount = 30)
        val detail = ComicDetails(summary, "测试简介", (1..5).map { Chapter("$it", "第 $it 话", it) })
        val task = DownloadTask("${key.id}-download", SavedComic.from(summary), "2", "第 2 话", 2,
            "fixture", DownloadStorage.INTERNAL, state = DownloadState.COMPLETED.name, total = 30, completed = 30)
        var selectedChapter: String? = null
        var selectedTag: String? = null
        val fixture = object : ComicSource {
            override val source = Source.PICACG
            override suspend fun categories() = emptyList<String>()
            override suspend fun search(query: ContentQuery): ContentPage<ComicSummary> = error("No list requests")
            override suspend fun details(id: String) = detail
            override suspend fun pages(comicId: String, chapter: Chapter): List<PageRef> = error("No image requests")
        }
        try {
            runBlocking {
                vm.network.awaitReady(); vm.picacgAccount.cancel(); vm.network.sessions.logout("picacg")
                vm.network.sessions.validateAndCommit(vm.network.sessions.begin("picacg"), SessionCandidate(CredentialKind.USER_TOKEN, "detail-layout-fixture".toByteArray())) { ValidationResult.Verified("测试账号") }
                vm.library.awaitReady()
                vm.library.record(summary, ContentProgress(key, "2", "page-7", 7, 0f, "纵向连续")); vm.library.flush()
                DownloadDatabase.get(ui.activity).downloads().save(task)
            }
            vm.content = ContentRepository(vm.network, picacgFactory = { fixture })
            ui.setContent { PiComicTheme(false, false) {
                Box(Modifier.width(360.dp).fillMaxHeight()) {
                    ContentDetailScreen(key, UiState(), vm, { selectedChapter = it }, {}, { selectedTag = it })
                }
            } }
            ui.waitUntil(5000) { ui.onAllNodesWithTag("content-detail").fetchSemanticsNodes().isNotEmpty() }
            val list = ui.onNodeWithTag("content-detail")
            val download = ui.onNodeWithTag("detail-download").fetchSemanticsNode().boundsInRoot
            val read = ui.onNodeWithTag("detail-read").fetchSemanticsNode().boundsInRoot
            assertTrue(download.right < read.left)
            assertEquals(download.top, read.top, 1f)
            assertEquals(download.width, read.width, 1f)
            assertEquals(download.height, read.height, 1f)
            ui.onNodeWithTag("detail-read").assertTextContains("继续阅读").performClick()
            ui.runOnIdle { assertEquals("2", selectedChapter) }
            ui.onNodeWithTag("detail-download").performClick()
            ui.onNodeWithText("选择下载章节").assertIsDisplayed()
            ui.onNodeWithText("取消", substring = false).performClick()
            ui.onNodeWithContentDescription("收藏作品").performClick()
            ui.waitUntil(5000) { key in vm.library.state.value.favorites }
            ui.onNodeWithContentDescription("取消收藏").assertIsDisplayed()
            ui.onNodeWithText("分享", substring = false).assertDoesNotExist()
            ui.onNodeWithText("评论", substring = false).assertDoesNotExist()
            list.performScrollToNode(hasTestTag("detail-info-作者"))
            ui.onNodeWithText("作者甲", useUnmergedTree = true).assertIsDisplayed()
            ui.onNodeWithText("作者乙", useUnmergedTree = true).assertIsDisplayed()
            list.performScrollToNode(hasText(longTag))
            val group = ui.onNodeWithTag("detail-info-分类 / 标签", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val wrapped = ui.onNodeWithText(longTag).fetchSemanticsNode().boundsInRoot
            assertTrue(wrapped.left >= group.left && wrapped.right <= group.right)
            assertTrue(wrapped.top > group.top)
            ui.onNodeWithText(longTag).performClick()
            ui.runOnIdle { assertEquals(longTag, selectedTag) }
            list.performScrollToNode(hasTestTag("detail-chapter-1"))
            val first = ui.onNodeWithTag("detail-chapter-1").fetchSemanticsNode().boundsInRoot
            val second = ui.onNodeWithTag("detail-chapter-2").fetchSemanticsNode().boundsInRoot
            assertEquals(first.top, second.top, 1f)
            assertEquals(first.width, second.width, 1f)
            assertEquals(first.height, second.height, 1f)
            assertTrue(first.right < second.left)
            ui.onNodeWithTag("detail-chapter-2").assertTextContains("继续 · 已下载")
            list.performScrollToNode(hasTestTag("detail-chapter-5"))
            val last = ui.onNodeWithTag("detail-chapter-5").fetchSemanticsNode().boundsInRoot
            assertEquals(first.width, last.width, 1f)
            ui.onNodeWithTag("detail-chapter-5").performClick()
            ui.runOnIdle { assertEquals("5", selectedChapter) }
        } finally {
            runBlocking {
                vm.library.deleteHistory(key); vm.library.favorite(summary, false); vm.library.flush()
                DownloadDatabase.get(ui.activity).downloads().delete(task.id)
                vm.network.sessions.logout("picacg")
            }
            ui.runOnIdle { store.clear() }
        }
    }
}
