package io.github.ddmoyu.picomic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.download.*
import io.github.ddmoyu.picomic.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual production Compose screens, with local geometric covers and synthetic metadata only.
 * Run exclusively on a disposable, -no-window emulator; never on a personal device.
 * Network remains available; fixture covers and metadata prevent adult content from being fetched.
 */
class UiAuditScreenshotTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String) {
        ui.waitForIdle()
        val stage = InstrumentationRegistry.getArguments().getString("auditStage", "after")!!
        require(stage.matches(Regex("[a-z0-9-]+")))
        val file = File(ui.activity.getExternalFilesDir(null), "ui-current/$stage/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun cover(index: Int): String {
        val bitmap = Bitmap.createBitmap(360, 540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = listOf(0xFFDCE6F0, 0xFFE8E1D4, 0xFFD9E7E1, 0xFFE6DCEB)
        canvas.drawColor(colors[index % colors.size].toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF526D84.toInt() }
        canvas.drawCircle(270f, 110f, 54f, paint)
        paint.color = 0xFFA2B8BC.toInt()
        canvas.drawRect(0f, 330f, 360f, 540f, paint)
        paint.color = 0xFF708F9D.toInt()
        canvas.drawRect(40f, 245f, 135f, 485f, paint)
        canvas.drawRect(160f, 295f, 245f, 485f, paint)
        paint.color = Color.WHITE
        paint.strokeWidth = 5f
        canvas.drawLine(34f, 50f, 180f, 50f, paint)
        canvas.drawLine(34f, 68f, 130f, 68f, paint)
        val file = File(ui.activity.cacheDir, "ui-audit-cover-$index.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.toURI().toString()
    }

    @Test fun captureProductionScreensWithSafeFixtures() {
        org.junit.Assume.assumeTrue("Opt-in screenshot audit only", InstrumentationRegistry.getArguments().containsKey("auditStage"))
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish")) { "Use a disposable emulator" }
        val vm = AppViewModel(ui.activity.application)
        val store = ViewModelStore().apply { put("ui-audit", vm) }
        val titles = listOf("雨后的第七站", "沿着海岸去旅行：在漫长的夏日遇见远方的风景", "留给明天的一封信", "城市里的小小花园")
        val comics = titles.mapIndexed { index, title ->
            ComicSummary(ComicKey(Source.PICACG, "ui-audit-$index"), title, listOf("青禾", "林间", "南风", "小满")[index],
                cover(index), listOf("日常", "旅行", "短篇"), "中文", 8, 192 + index * 24)
        }
        val fixture = object : ComicSource {
            override val source = Source.PICACG
            override suspend fun search(query: ContentQuery) = ContentPage(comics.filter { query.keyword.isBlank() || it.title.contains(query.keyword) })
            override suspend fun categories() = listOf("日常", "旅行", "短篇", "自然", "城市", "校园", "奇幻", "冒险", "科幻", "悬疑", "治愈", "美食")
            override suspend fun details(id: String) = ComicDetails(comics.first { it.key.id == id },
                "一段关于日常与远方的旅程。从熟悉的街角出发，记录沿途遇见的风景，也发现生活中被忽略的小小美好。",
                (1..8).map { Chapter("chapter-$it", "第 $it 话 · ${listOf("出发", "沿途", "相遇", "远方")[(it - 1) % 4]}", it, 24) })
            override suspend fun pages(comicId: String, chapter: Chapter): List<PageRef> =
                (1..6).map { PageRef("page-$it", it - 1, "android.resource://${ui.activity.packageName}/${readerPages[it - 1]}", 640, 930) }
        }
        try {
            runBlocking {
                vm.network.awaitReady(); vm.picacgAccount.cancel(); vm.network.sessions.logout("picacg")
                vm.library.awaitReady()
                comics.forEach { vm.library.deleteHistory(it.key); vm.library.favorite(it, false) }
                vm.library.flush()
                vm.network.sessions.validateAndCommit(vm.network.sessions.begin("picacg"),
                    SessionCandidate(CredentialKind.USER_TOKEN, "ui-current-fixture".toByteArray())) { ValidationResult.Verified("本地界面验证") }
            }
            vm.content = ContentRepository(vm.network, picacgFactory = { fixture })
            ui.runOnIdle {
                ui.activity.enableEdgeToEdge()
                vm.source(Source.PICACG)
                vm.preference("debugDemo", "false")
                vm.preference("checkOnStart", "false")
                vm.preference("themeMode", "浅色模式")
                vm.preference("skipDetails", "false")
                vm.preference("pureBlack", "false")
                vm.preference("readingMode", "纵向连续")
                vm.preference("readerOrientation", "跟随系统")
            }
            ui.setContent { PiComicApp(vm) }
            ui.waitUntil(10000) { ui.onAllNodesWithText(titles[0]).fetchSemanticsNodes().isNotEmpty() }
            shot("01-discover")
            ui.onNodeWithContentDescription("分类").performClick()
            ui.waitUntil(5000) { ui.onAllNodesWithTag("categories-PICACG").fetchSemanticsNodes().isNotEmpty() }
            shot("02-categories")
            ui.onNodeWithContentDescription("探索").performClick()
            ui.onNodeWithText(titles[0]).performClick()
            ui.waitUntil(10000) { ui.onAllNodes(hasText("开始阅读") or hasText("继续阅读")).fetchSemanticsNodes().isNotEmpty() }
            shot("03-detail")
            ui.onNode(hasText("开始阅读") or hasText("继续阅读")).performScrollTo().performClick()
            ui.waitUntil(10000) { ui.onAllNodesWithTag("reader").fetchSemanticsNodes().isNotEmpty() }
            ui.waitForIdle()
            shot("15-reader")
            ui.onNodeWithTag("reader").performTouchInput { click(center) }
            ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("阅读设置").fetchSemanticsNodes().isNotEmpty() }
            shot("16-reader-controls")
            ui.onNodeWithContentDescription("退出阅读").performClick()
            ui.onNodeWithTag("content-detail").performScrollToNode(hasTestTag("detail-chapter-chapter-8"))
            shot("04-chapters")
            ui.onNodeWithContentDescription("返回").performClick()
            runBlocking { vm.library.deleteHistory(comics[0].key); vm.library.favorite(comics[0], true); vm.library.favorite(comics[1], true); vm.library.flush() }
            ui.onNodeWithContentDescription("书架").performClick()
            ui.waitUntil(5000) { ui.onAllNodesWithTag("favorites-list").fetchSemanticsNodes().isNotEmpty() }
            shot("05-library")
            ui.onNodeWithText("阅读历史").performClick()
            shot("06-library-empty")
            runBlocking {
                vm.library.record(comics[0], ContentProgress(comics[0].key, "chapter-2", "page-7", 7, 0f, "纵向连续"))
                vm.library.flush()
                DownloadDatabase.get(ui.activity).downloads().save(DownloadTask("ui-current-download", SavedComic.from(comics[1]), "chapter-1", "第 1 话 · 出发", 1,
                    "fixture", DownloadStorage.INTERNAL, DownloadState.PAUSED.name, total = 24, completed = 8))
            }
            ui.waitUntil(5000) { ui.onAllNodesWithTag("history-list").fetchSemanticsNodes().isNotEmpty() }
            shot("07-history")
            ui.onNodeWithText("下载管理").performClick()
            ui.waitUntil(5000) { ui.onAllNodesWithText("继续下载").fetchSemanticsNodes().isNotEmpty() }
            shot("08-downloads")
            ui.onNodeWithContentDescription("设置").performClick()
            shot("09-settings")
            ui.onNodeWithText("阅读", substring = false).performScrollTo().performClick()
            shot("17-reading-settings")
            ui.onNodeWithContentDescription("返回").performClick()
            ui.onNodeWithText("外观").performScrollTo().performClick()
            shot("18-appearance")
            ui.onNodeWithContentDescription("返回").performClick()
            ui.runOnIdle { vm.preference("themeMode", "深色模式") }
            shot("10-settings-dark")
            ui.onNodeWithContentDescription("返回").performClick()
            ui.onNodeWithContentDescription("探索").performClick()
            shot("11-discover-dark")
            ui.onNodeWithContentDescription("搜索").performClick()
            shot("12-search-dark")
            ui.onNode(hasSetTextAction()).performTextInput("雨后")
            ui.onNodeWithText("搜索", substring = false).performClick()
            ui.waitUntil(5000) { ui.onAllNodesWithText(titles[0]).fetchSemanticsNodes().isNotEmpty() }
            shot("13-search-results-dark")
            ui.runOnIdle { vm.preference("themeMode", "浅色模式") }
            shot("14-search-results")
        } finally {
            runBlocking {
                comics.forEach { vm.library.favorite(it, false) }
                comics.forEach { vm.library.deleteHistory(it.key) }
                DownloadDatabase.get(ui.activity).downloads().delete("ui-current-download")
                vm.library.flush()
                vm.network.sessions.logout("picacg")
            }
            ui.runOnIdle { store.clear() }
        }
    }
}
