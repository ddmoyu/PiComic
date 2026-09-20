package io.github.ddmoyu.picomic

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ddmoyu.picomic.ui.AppViewModel
import io.github.ddmoyu.picomic.data.ReadingProgressRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderFullscreenTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private var originalMode = "纵向连续"
    private var originalOrientation = "跟随系统"

    @Before fun rememberMode() {
        ui.runOnIdle { ViewModelProvider(ui.activity)[AppViewModel::class.java].preference("debugDemo", "true") }
        runBlocking { ReadingProgressRepository.get(ui.activity).clear() }
        ui.runOnIdle {
            originalMode = ViewModelProvider(ui.activity)[AppViewModel::class.java].state.value.pref("readingMode", "纵向连续")
            originalOrientation = ViewModelProvider(ui.activity)[AppViewModel::class.java].state.value.pref("readerOrientation", "跟随系统")
            ViewModelProvider(ui.activity)[AppViewModel::class.java].preference("readerOrientation", "跟随系统")
        }
    }

    @After fun restoreMode() {
        ui.runOnIdle { ViewModelProvider(ui.activity)[AppViewModel::class.java].preference("debugDemo", "false") }
        mode(originalMode)
        ui.runOnIdle { ViewModelProvider(ui.activity)[AppViewModel::class.java].preference("readerOrientation", originalOrientation) }
        ui.runOnUiThread { ui.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }

    private fun mode(value: String) {
        ui.runOnIdle { ViewModelProvider(ui.activity)[AppViewModel::class.java].preference("readingMode", value) }
    }

    private fun openReader() {
        ui.onVisibleText("雨后的第七站").performClick()
        ui.onNode(hasText("开始阅读") or hasText("继续阅读")).performClick()
    }

    private fun bars(visible: Boolean) {
        ui.waitForIdle()
        var diagnostic = ""
        try { ui.waitUntil(5000) {
            var matches = false
            ui.runOnUiThread {
                val insets = ViewCompat.getRootWindowInsets(ui.activity.window.decorView)
                // Pre-30 WindowInsetsCompat infers visibility from stable inset sizes; edge-to-edge
                // API 26 keeps those sizes even with immersive bars hidden. Check native flags there.
                matches = if (android.os.Build.VERSION.SDK_INT < 30) {
                    val flags = ui.activity.window.decorView.systemUiVisibility
                    (flags and android.view.View.SYSTEM_UI_FLAG_FULLSCREEN == 0) == visible &&
                        (flags and android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0) == visible
                } else insets != null &&
                    insets.isVisible(WindowInsetsCompat.Type.statusBars()) == visible &&
                    insets.isVisible(WindowInsetsCompat.Type.navigationBars()) == visible
                diagnostic = "expected=$visible status=${insets?.isVisible(WindowInsetsCompat.Type.statusBars())} nav=${insets?.isVisible(WindowInsetsCompat.Type.navigationBars())} flags=${ui.activity.window.decorView.systemUiVisibility}"
            }
            matches
        } } catch (e: Throwable) { shot("fullscreen-failure"); throw AssertionError(diagnostic, e) }
    }

    private fun hidden() {
        // Paged reading waits for the double-tap timeout before committing a single tap.
        ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("阅读设置").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithContentDescription("退出阅读").assertDoesNotExist()
        ui.onNodeWithContentDescription("阅读设置").assertDoesNotExist()
        ui.onNodeWithText("目录").assertDoesNotExist()
        bars(false)
    }

    private fun fillsWindow() {
        val bounds = ui.onNodeWithTag("reader").fetchSemanticsNode().boundsInRoot
        ui.runOnIdle {
            assertEquals(0f, bounds.top, 1f)
            assertEquals(0f, bounds.left, 1f)
            assertEquals(ui.activity.window.decorView.width.toFloat(), bounds.width, 1f)
            assertEquals(ui.activity.window.decorView.height.toFloat(), bounds.height, 1f)
        }
    }

    private fun tapPage() = ui.onNodeWithTag("reader").performTouchInput { click(center) }

    private fun showTools() {
        tapPage()
        ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("阅读设置").fetchSemanticsNodes().isNotEmpty() }
        bars(true)
        ui.onNodeWithContentDescription("阅读设置").assertIsDisplayed()
        ui.onNodeWithText("目录").assertIsDisplayed()
    }

    private fun systemBack() {
        ui.runOnUiThread { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitForIdle()
    }

    private fun shot(name: String) {
        val file = File(ui.activity.getExternalFilesDir(null), "screenshots/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun defaultFullscreenAndTapToggleInAllModes() {
        listOf("纵向连续", "从左向右", "从右向左").forEach { readingMode ->
            mode(readingMode)
            openReader()
            hidden()
            fillsWindow()
            if (readingMode == "纵向连续") shot("17-reader-fullscreen")
            val content = ui.onNodeWithTag(if (readingMode == "纵向连续") "reader-pages" else "reader-paged-pages")
            val boundsBeforeTap = content.fetchSemanticsNode().boundsInRoot
            showTools()
            fillsWindow()
            assertEquals(boundsBeforeTap, content.fetchSemanticsNode().boundsInRoot)
            if (readingMode == "纵向连续") shot("18-reader-controls")
            tapPage()
            hidden()
            if (readingMode == "纵向连续") {
                content.performTouchInput { swipeUp() }
                hidden()
            }
            showTools()
            ui.onNodeWithContentDescription("退出阅读").performClick()
            bars(true)
            ui.onNodeWithText("继续阅读").performClick()
            hidden()
            systemBack()
            bars(true)
            ui.onNodeWithContentDescription("返回").performClick()
        }
    }

    @Test fun fullscreenSurvivesRotationAndRestoresBarsOnExit() {
        mode("纵向连续")
        openReader()
        hidden()
        // Orientation is owned by the reader preference now; use the same action as the user.
        ui.runOnIdle { ViewModelProvider(ui.activity)[AppViewModel::class.java].preference("readerOrientation", "横屏") }
        ui.waitUntil(10000) {
            val bounds = ui.onNodeWithTag("reader").fetchSemanticsNode().boundsInRoot
            bounds.width > bounds.height
        }
        hidden()
        fillsWindow()
        showTools()
        tapPage()
        hidden()
        systemBack()
        bars(true)
        ui.onNodeWithText("继续阅读").assertIsDisplayed()
    }
}
