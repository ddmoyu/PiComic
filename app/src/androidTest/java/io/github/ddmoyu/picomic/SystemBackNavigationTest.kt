package io.github.ddmoyu.picomic

import androidx.activity.BackEventCompat
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.github.ddmoyu.picomic.data.ReadingProgressRepository
import io.github.ddmoyu.picomic.ui.AppViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry

class SystemBackNavigationTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Before fun setup() {
        ui.runOnIdle { ViewModelProvider(ui.activity)[AppViewModel::class.java].preference("debugDemo", "true") }
        runBlocking { ReadingProgressRepository.get(ui.activity).clear() }
    }
    @After fun cleanup() {
        ui.runOnIdle { ViewModelProvider(ui.activity)[AppViewModel::class.java].preference("debugDemo", "false") }
    }
    private fun progress(value: Float) = BackEventCompat(100f * value, 300f, value, BackEventCompat.EDGE_LEFT)

    @Test @SdkSuppress(minSdkVersion = 29)
    fun nativeEdgeGestureWaitsForReleaseAndCanBeCancelled() {
        val mode = ui.activity.resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
        assumeTrue(mode != 0 && ui.activity.resources.getInteger(mode) == 2)
        ui.onNodeWithContentDescription("设置").performClick()
        ui.onNodeWithText("关于 PiComic").performScrollTo().performClick()
        ui.onNodeWithText("关于 PiComic").assertIsDisplayed()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val metrics = ui.activity.resources.displayMetrics
        val y = metrics.heightPixels * .5f
        val end = metrics.widthPixels * .65f
        var downTime = 0L
        fun touch(action: Int, x: Float) {
            if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        fun dragOut() {
            touch(MotionEvent.ACTION_DOWN, 1f)
            for (step in 1..12) { SystemClock.sleep(20); touch(MotionEvent.ACTION_MOVE, end * step / 12) }
        }
        try {
            dragOut()
            ui.onNodeWithText("关于 PiComic").assertIsDisplayed()
            for (step in 11 downTo 0) { SystemClock.sleep(20); touch(MotionEvent.ACTION_MOVE, maxOf(1f, end * step / 12)) }
            touch(MotionEvent.ACTION_UP, 1f)
            ui.onNodeWithText("关于 PiComic").assertIsDisplayed()
            dragOut()
            ui.onNodeWithText("关于 PiComic").assertIsDisplayed()
            touch(MotionEvent.ACTION_UP, end)
            ui.waitUntil(5000) { ui.onAllNodesWithText("设置").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("设置").assertIsDisplayed()
        } finally {
            // Never leave an injected pointer down when an assertion fails.
            val cancel = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 1f, y, 0)
            cancel.source = InputDevice.SOURCE_TOUCHSCREEN
            try { automation.injectInputEvent(cancel, true) } finally { cancel.recycle() }
        }
    }

    @Test fun systemGestureWaitsForCommitAndCancellationKeepsTheCurrentPage() {
        ui.onNodeWithContentDescription("设置").performClick()
        ui.onNodeWithText("关于 PiComic").performScrollTo().performClick()
        ui.onNodeWithText("关于 PiComic").assertIsDisplayed()
        val back = ui.activity.onBackPressedDispatcher
        ui.runOnIdle { back.dispatchOnBackStarted(progress(0f)); back.dispatchOnBackProgressed(progress(.9f)) }
        ui.onNodeWithText("关于 PiComic").assertIsDisplayed()
        ui.onNodeWithText("账号管理").assertDoesNotExist()
        ui.runOnIdle { back.dispatchOnBackProgressed(progress(.1f)); back.dispatchOnBackCancelled() }
        ui.onNodeWithText("关于 PiComic").assertIsDisplayed()
        ui.runOnIdle { back.dispatchOnBackStarted(progress(0f)); back.dispatchOnBackProgressed(progress(.8f)) }
        ui.onNodeWithText("关于 PiComic").assertIsDisplayed()
        ui.runOnIdle { back.onBackPressed() }
        ui.onNodeWithText("设置").assertIsDisplayed()
        ui.runOnIdle { back.onBackPressed() }
        ui.onNodeWithText("探索").assertIsDisplayed()
        ui.runOnIdle { assertFalse("首页应将返回桌面交给系统", back.hasEnabledCallbacks()) }
    }

    @Test fun readerControlsHandleCommittedBackBeforeLeavingThePage() {
        ui.onVisibleText("雨后的第七站").performClick()
        ui.onNodeWithText("开始阅读").performClick()
        ui.waitUntil(5000) { ViewModelProvider(ui.activity)[AppViewModel::class.java].state.value.history.isNotEmpty() }
        ui.onNodeWithTag("reader").performTouchInput { click(center) }
        ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("退出阅读").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("退出阅读").assertIsDisplayed()
        val back = ui.activity.onBackPressedDispatcher
        ui.runOnIdle { back.dispatchOnBackStarted(progress(0f)); back.dispatchOnBackProgressed(progress(.9f)); back.dispatchOnBackCancelled() }
        ui.onNodeWithContentDescription("退出阅读").assertIsDisplayed()
        ui.runOnIdle { back.onBackPressed() }
        ui.onNodeWithTag("reader").assertIsDisplayed()
        ui.onNodeWithContentDescription("退出阅读").assertDoesNotExist()
        ui.runOnIdle { back.onBackPressed() }
        ui.onNodeWithText("作品详情").assertIsDisplayed()
    }
}
