package io.github.ddmoyu.picomic

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.download.*
import io.github.ddmoyu.picomic.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic metadata and placeholder covers only; no remote content or screenshots. */
class LibraryListLayoutTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun favoritesAndHistoryShareCoverRowsAndHistoryDeletionKeepsFavorites() {
        val vm = AppViewModel(ui.activity.application)
        val store = ViewModelStore().apply { put("library-list", vm) }
        val comic = ComicSummary(ComicKey(Source.PICACG, "library-list-fixture"), "书架布局测试", "测试作者", tags = listOf("测试分类"), pageCount = 350)
        var selected: ComicKey? = null
        try {
            runBlocking {
                vm.library.awaitReady()
                vm.library.favorite(comic, true)
                vm.library.record(comic, ContentProgress(comic.key, "chapter", "page-7", 7, 0f, "纵向连续"))
                vm.library.flush()
            }
            ui.setContent { PiComicTheme(false, false) {
                Box(Modifier.width(360.dp).fillMaxHeight()) { ContentLibraryScreen(vm, { selected = it }) }
            } }
            ui.waitUntil(5000) { ui.onAllNodesWithTag("favorites-list").fetchSemanticsNodes().isNotEmpty() }
            fun row(list: String) = ui.onNode(hasTestTag("comic-row-${comic.key.stable}") and hasAnyAncestor(hasTestTag(list)))
            fun assertCoverOnLeft(list: String) {
                val cover = ui.onNode(hasTestTag("comic-cover-${comic.key.stable}") and hasAnyAncestor(hasTestTag(list)), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val title = ui.onNode(hasTestTag("comic-title-${comic.key.stable}") and hasAnyAncestor(hasTestTag(list)), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue(cover.right < title.left)
                row(list).assertTextContains("共 350 张图片")
                row(list).assertTextContains("picacg · library-list-fixture")
            }
            assertCoverOnLeft("favorites-list")
            row("favorites-list").assertTextContains("测试作者").assertTextContains("测试分类").performClick()
            ui.runOnIdle { assertEquals(comic.key, selected); selected = null }
            ui.onNodeWithText("阅读历史").performClick()
            ui.onNodeWithTag("history-list").assertIsDisplayed()
            assertCoverOnLeft("history-list")
            row("history-list").assertTextContains("读到第 7 页").performClick()
            ui.runOnIdle { assertEquals(comic.key, selected); selected = null }
            ui.onNodeWithContentDescription("删除 ${comic.title} 的历史").performClick()
            ui.onNodeWithText("删除这条历史？").assertIsDisplayed()
            ui.runOnIdle { assertNull(selected) }
            ui.onNodeWithText("取消").performClick()
            row("history-list").assertIsDisplayed()
            ui.onNodeWithContentDescription("删除 ${comic.title} 的历史").performClick()
            ui.onNodeWithText("删除", substring = false).performClick()
            ui.waitUntil(5000) { vm.library.state.value.progress.none { it.key == comic.key } }
            assertTrue(comic.key in vm.library.state.value.favorites)
            ui.onNodeWithText("收藏", substring = false).performClick()
            row("favorites-list").assertIsDisplayed()
        } finally {
            runBlocking { vm.library.deleteHistory(comic.key); vm.library.favorite(comic, false); vm.library.flush() }
            ui.runOnIdle { store.clear() }
        }
    }

    @Test fun narrowDownloadRowsKeepProgressPauseResumeDeleteAndOfflineReadActions() {
        val comic = ComicSummary(ComicKey(Source.EHENTAI, "download-list-fixture"), "下载布局测试", "测试作者", tags = listOf("测试分类"), pageCount = 350)
        var task by mutableStateOf(DownloadTask("download-row", SavedComic.from(comic), "chapter", "测试章节", 0,
            "fixture", DownloadStorage.INTERNAL, DownloadState.DOWNLOADING.name, total = 10, completed = 3, subtitle = "备用测试标题"))
        val actions = mutableListOf<String>()
        ui.setContent { PiComicTheme(false, false) {
            Box(Modifier.width(320.dp).padding(20.dp)) {
                DownloadTaskRow(task, preferSubtitle = true, running = true,
                    read = { actions += "read" }, pause = { actions += "pause" },
                    resume = { actions += "resume" }, delete = { actions += "delete" })
            }
        } }
        val cover = ui.onNodeWithTag("comic-cover-${comic.key.stable}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = ui.onNodeWithTag("comic-title-${comic.key.stable}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(cover.right < title.left)
        ui.onNodeWithText("备用测试标题").assertIsDisplayed()
        ui.onNodeWithText("共 350 张图片").assertIsDisplayed()
        ui.onNodeWithText("共 10 张图片").assertDoesNotExist()
        ui.onNodeWithText("正在下载 · 3 / 10 页").assertIsDisplayed()
        ui.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo(0.3f, 0f..1f))).assertIsDisplayed()
        ui.onNodeWithText("暂停", substring = false).performClick()
        ui.runOnIdle { task = task.copy(state = DownloadState.PAUSED.name) }
        ui.onNodeWithText("继续下载").performClick()
        ui.runOnIdle { task = task.copy(state = DownloadState.WAITING_QUOTA.name) }
        ui.onNodeWithText("额度恢复后继续").assertIsDisplayed().performClick()
        ui.onNodeWithText("删除", substring = false).assertIsDisplayed().performClick()
        ui.runOnIdle { task = task.copy(state = DownloadState.DELETING.name) }
        ui.onNodeWithText("重试删除").performClick()
        ui.onNodeWithText("继续下载").assertDoesNotExist()
        ui.runOnIdle { task = task.copy(state = DownloadState.COMPLETED.name, completed = 10) }
        ui.onNodeWithTag("comic-row-${comic.key.stable}").performClick()
        ui.onNodeWithText("离线阅读").performClick()
        ui.onNodeWithText("删除", substring = false).performClick()
        ui.runOnIdle { assertEquals(listOf("pause", "resume", "resume", "delete", "delete", "read", "read", "delete"), actions) }
    }
}
