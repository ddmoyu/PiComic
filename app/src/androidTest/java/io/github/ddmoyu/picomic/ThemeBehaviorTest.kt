package io.github.ddmoyu.picomic

import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.data.ThemeMode
import io.github.ddmoyu.picomic.ui.AppViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ThemeBehaviorTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private var originalNightMode = UiModeManager.MODE_NIGHT_NO
    private var originalPreferences = emptyMap<String, String>()
    private val light = 0xFFF8F9FD.toInt()
    private val dark = 0xFF12151E.toInt()
    private val vm get() = ViewModelProvider(ui.activity)[AppViewModel::class.java]

    @Before fun prepare() {
        ui.runOnIdle {
            originalNightMode = ui.activity.getSystemService(UiModeManager::class.java).nightMode
            originalPreferences = vm.state.value.preferences
            vm.preference("debugDemo", "true")
            vm.preference("themeMode", ThemeMode.SYSTEM.label)
            vm.preference("pureBlack", "false")
            vm.preference("readerBackground", "纯黑")
            vm.preference("readingMode", "从左向右")
        }
    }

    @After fun restore() {
        ui.mainClock.autoAdvance = true
        ui.runOnIdle {
            mapOf("debugDemo" to "false", "themeMode" to ThemeMode.fromPreferences(originalPreferences).label,
                "pureBlack" to "false", "readerBackground" to "深灰", "readingMode" to "纵向连续").forEach { (key, fallback) ->
                vm.preference(key, originalPreferences[key] ?: fallback)
            }
        }
        shellNight(when (originalNightMode) { UiModeManager.MODE_NIGHT_YES -> "yes"; UiModeManager.MODE_NIGHT_NO -> "no"; else -> "auto" })
    }

    private fun shellNight(value: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cmd uimode night $value")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun systemDark(value: Boolean) {
        shellNight(if (value) "yes" else "no")
        ui.waitUntil(10_000) {
            (ui.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) == value
        }
        ui.waitForIdle()
    }

    private fun appearance() {
        ui.onNodeWithContentDescription("设置").performClick()
        ui.onNodeWithText("外观").performClick()
    }

    private fun choose(label: String) {
        ui.onNodeWithText("主题模式").performClick()
        ui.onNode(hasText(label) and hasClickAction()).performClick()
        ui.waitForIdle()
    }

    private fun backgroundBeside(text: String, expected: Int) {
        // Dialog window dimming can outlive Compose's idle state for a few frames.
        // Wait for the exact color instead of accepting a blended transition frame.
        ui.waitUntil(5000) {
            val row = ui.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
            val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
            try { bitmap.getPixel(2, row.center.y.toInt()) == expected }
            finally { bitmap.recycle() }
        }
    }

    private fun lightSystemBars() = ui.runOnUiThread {
        val controller = WindowCompat.getInsetsController(ui.activity.window, ui.activity.window.decorView)
        assertTrue("阅读路由不能提前改变详情页的状态栏主题", controller.isAppearanceLightStatusBars)
        assertTrue("阅读路由不能改变全局导航栏主题", controller.isAppearanceLightNavigationBars)
    }

    private fun shot(name: String) {
        val file = File(ui.activity.getExternalFilesDir(null), "screenshots/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun systemChangesApplyUntilUserChoosesAndManualChoiceSurvivesRecreation() {
        systemDark(false)
        appearance()
        backgroundBeside("主题模式", light)
        systemDark(true)
        backgroundBeside("主题模式", dark)
        shot("theme-follow-system-dark")
        choose(ThemeMode.LIGHT.label)
        backgroundBeside("主题模式", light)
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        backgroundBeside("主题模式", light)
        systemDark(false)
        choose(ThemeMode.DARK.label)
        backgroundBeside("主题模式", dark)
        systemDark(true)
        backgroundBeside("主题模式", dark)
        choose(ThemeMode.SYSTEM.label)
        systemDark(false)
        backgroundBeside("主题模式", light)
    }

    @Test fun readerBackgroundStaysLocalThroughoutEntryAndExit() {
        systemDark(true)
        ui.runOnIdle { vm.preference("themeMode", ThemeMode.LIGHT.label) }
        ui.onVisibleText("雨后的第七站").performClick()
        backgroundBeside("作品详情", light)
        shot("theme-light-detail")
        ui.mainClock.autoAdvance = false
        ui.onNode(hasText("开始阅读") or hasText("继续阅读")).performClick()
        repeat(12) {
            ui.mainClock.advanceTimeBy(64)
            lightSystemBars()
        }
        ui.mainClock.autoAdvance = true
        ui.onNodeWithTag("reader").assertIsDisplayed()
        val bitmap = ui.onNodeWithTag("reader").captureToImage().asAndroidBitmap()
        try { assertEquals("纯黑仅用于阅读画布", android.graphics.Color.BLACK, bitmap.getPixel(2, 2)) }
        finally { bitmap.recycle() }
        ui.onNodeWithTag("reader").performTouchInput { click(center) }
        ui.waitUntil(5_000) { ui.onAllNodesWithContentDescription("阅读设置").fetchSemanticsNodes().isNotEmpty() }
        lightSystemBars()
        shot("theme-light-reader-controls")
        ui.onNodeWithContentDescription("阅读设置").performClick()
        // Reading panels continue to use the manual light theme over the black canvas.
        val settings = ui.onNodeWithText("阅读模式").fetchSemanticsNode().boundsInRoot
        val panel = ui.onRoot().captureToImage().asAndroidBitmap()
        try { assertTrue(android.graphics.Color.red(panel.getPixel(2, settings.center.y.toInt())) > 220) }
        finally { panel.recycle() }
        // The sheet owns a dialog window, so deliver Back to the focused window.
        androidx.test.espresso.Espresso.pressBack()
        ui.waitForIdle()
        ui.onNodeWithContentDescription("退出阅读").performClick()
        backgroundBeside("作品详情", light)
        lightSystemBars()
        ui.runOnIdle { assertEquals(ThemeMode.LIGHT, vm.state.value.themeMode) }
    }
}
