package io.github.ddmoyu.picomic

import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import io.github.ddmoyu.picomic.data.*
import io.github.ddmoyu.picomic.ui.AppViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class ReaderControlsTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(ui.activity)[AppViewModel::class.java]
    private var preferences = emptyMap<String, String>()
    @Before fun setup() {
        runBlocking { ReadingProgressRepository.get(ui.activity).clear() }
        ui.runOnIdle { preferences = vm.state.value.preferences; vm.preference("volume", "true"); vm.preference("keepAwake", "true"); vm.preference("doubleTap", "true"); vm.preference("longPress", "true"); vm.preference("autoInterval", "2 秒"); vm.preference("readingMode", "从左向右") }
    }
    @After fun cleanup() {
        ui.runOnIdle {
            mapOf("volume" to "false", "keepAwake" to "true", "doubleTap" to "true", "longPress" to "false", "autoInterval" to "5 秒", "readingMode" to "纵向连续").forEach { (key, fallback) -> vm.preference(key, preferences[key] ?: fallback) }
        }
    }
    private fun openReader() { ui.onVisibleText("雨后的第七站").performClick(); ui.onNodeWithText("开始阅读").performClick(); ui.waitUntil(5000) { vm.state.value.history.isNotEmpty() } }
    private fun showTools() {
        ui.onNodeWithTag("reader").performTouchInput { click(center) }
        ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("阅读设置").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun volume(next: Boolean) {
        val code = if (next) KeyEvent.KEYCODE_VOLUME_DOWN else KeyEvent.KEYCODE_VOLUME_UP
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code)
    }
    private fun waitPage(page: Int) = ui.waitUntil(6000) { vm.state.value.history.firstOrNull()?.page == page }
    private fun zoomed(value: Boolean) {
        ui.waitUntil(5000) { ui.onNodeWithTag("reader").fetchSemanticsNode().config[SemanticsProperties.StateDescription] == if (value) "已放大" else "原始比例" }
    }
    @Test fun volumeKeysAndKeepAwakeBelongOnlyToForegroundReader() {
        openReader()
        ui.runOnIdle { assertNotNull(ui.activity.readerVolumeAction); assertTrue(ui.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0) }
        volume(true); waitPage(2)
        volume(false); waitPage(1)
        ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        ui.runOnUiThread { assertNull(ui.activity.readerVolumeAction); assertEquals(0, ui.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        showTools()
        ui.onNodeWithContentDescription("退出阅读").performClick()
        ui.runOnIdle { assertNull(ui.activity.readerVolumeAction); assertEquals(0, ui.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    @Test fun doubleTapAndTemporaryLongPressWorkInBothLayouts() {
        openReader()
        for (mode in listOf("从左向右", "纵向连续")) {
            ui.runOnIdle { vm.preference("readingMode", mode) }
            ui.onNodeWithTag("reader").performTouchInput { doubleClick(center) }
            zoomed(true)
            ui.onNodeWithTag("reader").performTouchInput { doubleClick(center) }
            zoomed(false)
            ui.onNodeWithTag("reader").performTouchInput { down(center) }
            zoomed(true)
            ui.onNodeWithTag("reader").performTouchInput { up() }
            zoomed(false)
        }
    }
    @Test fun autoTurningRequiresStartAndStopsInBackground() {
        openReader()
        waitPage(1)
        showTools()
        ui.onNodeWithContentDescription("开始自动翻页").performClick()
        waitPage(2)
        ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        showTools()
        ui.onNodeWithContentDescription("开始自动翻页").assertIsDisplayed()
    }
    @Test fun autoTurningStopsAtChapterEnd() {
        openReader()
        ui.onNodeWithTag("reader-paged-pages").performScrollToIndex(23)
        waitPage(24)
        showTools()
        ui.onNodeWithContentDescription("开始自动翻页").performClick()
        showTools()
        ui.waitUntil(6000) { ui.onAllNodesWithContentDescription("开始自动翻页").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, vm.state.value.history.first().chapter)
        assertEquals(24, vm.state.value.history.first().page)
    }
    @Test fun nextChapterAfterModeSwitchSavesFirstPage() {
        ui.runOnIdle { vm.preference("readingMode", "纵向连续") }
        openReader()
        ui.onNodeWithTag("reader-pages").performScrollToIndex(5)
        waitPage(6)
        showTools()
        ui.runOnIdle { vm.preference("readingMode", "从左向右") }
        ui.waitUntil(5000) { ui.onAllNodesWithTag("reader-paged-pages").fetchSemanticsNodes().isNotEmpty() }
        waitPage(6)
        ui.onNodeWithText("下一话").performClick()
        ui.waitUntil(5000) { vm.state.value.history.firstOrNull()?.chapter == 2 }
        waitPage(1)
        ui.onNodeWithContentDescription("退出阅读").performClick()
        runBlocking { vm.readingProgress.flush() }
        assertEquals(2, vm.state.value.history.first().chapter)
        assertEquals(1, vm.state.value.history.first().page)
        ui.onNodeWithText("继续阅读").performClick()
        ui.waitUntil(5000) { ui.onAllNodesWithTag("reader-paged-pages").fetchSemanticsNodes().isNotEmpty() }
        volume(true)
        waitPage(2)
    }
    @Test fun continuousProgressRestoresPageAndOffset() {
        ui.runOnIdle { vm.preference("readingMode", "纵向连续") }
        openReader()
        ui.onNodeWithTag("reader-pages").performScrollToIndex(5)
        ui.onNodeWithTag("reader-pages").performTouchInput { swipe(androidx.compose.ui.geometry.Offset(centerX, height * .6f), androidx.compose.ui.geometry.Offset(centerX, height * .5f), 700) }
        ui.waitUntil(5000) { (vm.state.value.history.firstOrNull()?.offsetRatio ?: 0f) > .05f }
        showTools()
        ui.onNodeWithContentDescription("退出阅读").performClick()
        runBlocking { vm.readingProgress.flush() }
        val saved = vm.state.value.history.first()
        ui.onNodeWithText("继续阅读").performClick()
        ui.waitUntil(5000) { ui.onAllNodesWithTag("reader-pages").fetchSemanticsNodes().isNotEmpty() }
        runBlocking { vm.readingProgress.flush() }
        ui.waitUntil(5000) { vm.state.value.history.first().updatedAt > saved.updatedAt }
        val restored = vm.state.value.history.first()
        assertEquals(saved.page, restored.page)
        assertEquals(saved.offsetRatio, restored.offsetRatio, .015f)
        val file = java.io.File(ui.activity.getExternalFilesDir(null), "screenshots/19-reader-resumed.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        showTools()
        ui.onNodeWithContentDescription("退出阅读").performClick()
        runBlocking { vm.readingProgress.flush() }
    }
}
