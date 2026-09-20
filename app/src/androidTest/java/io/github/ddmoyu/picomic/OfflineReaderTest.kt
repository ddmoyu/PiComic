package io.github.ddmoyu.picomic

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.download.*
import io.github.ddmoyu.picomic.ui.AppViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class OfflineReaderTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(ui.activity)[AppViewModel::class.java]
    @Test fun finishedChapterOpensFromLibraryWithoutAPlatformSessionAndRecordsRealPageIds() {
        val summary = ComicSummary(ComicKey(Source.PICACG, "offline-fixture-${System.nanoTime()}"), "离线阅读合成夹具")
        val task = DownloadTask(DownloadTask.identity(summary.key, "offline-chapter", "fixture-account"), SavedComic.from(summary), "offline-chapter", "已下载章节", 1, "fixture-account", DownloadStorage.INTERNAL)
        ui.runOnIdle { vm.picacgAccount.cancel(); vm.preference("debugDemo", "false"); vm.preference("readingMode", "从左向右"); vm.source(Source.PICACG) }
        val file = File.createTempFile("offline-ui-", ".png", ui.activity.cacheDir)
        val bitmap = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.CYAN); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            runBlocking {
                vm.network.awaitReady(); vm.network.sessions.logout("picacg")
                val dao = DownloadDatabase.get(ui.activity).downloads(); dao.enqueue(task)
                dao.manifest(task, (0..1).map { PageRef("offline-real-$it", it, "https://never-requested.invalid/$it", 80, 120) })
                for (page in dao.pages(task.id)) dao.completed(vm.downloadsRepository.storage.write(task, page, file))
                dao.save(dao.task(task.id)!!.copy(state = "COMPLETED"))
                io.github.ddmoyu.picomic.reader.ManagedImageCache.clear(ui.activity)
            }
            ui.onNodeWithContentDescription("书架").performClick()
            ui.onNodeWithText("下载管理").performClick()
            ui.waitUntil(10000) { ui.onAllNodesWithText(summary.title).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("离线阅读").performClick()
            ui.waitUntil(10000) { ui.onAllNodesWithTag("reader").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("请先登录并验证哔咔账号").assertDoesNotExist()
            ui.onNodeWithText("图片加载失败").assertDoesNotExist()
            ui.waitUntil(10000) { vm.library.state.value.progress.any { it.key == summary.key } }
            assertEquals("offline-real-0", vm.library.state.value.progress.first { it.key == summary.key }.pageId)
            assertEquals("offline-chapter", vm.library.state.value.progress.first { it.key == summary.key }.chapterId)
        } finally {
            bitmap.recycle(); file.delete()
            runBlocking { vm.downloadsRepository.remove(task.id) }
            ui.runOnIdle { vm.preference("readingMode", "纵向连续") }
        }
    }
}
