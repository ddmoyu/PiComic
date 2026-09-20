package io.github.ddmoyu.picomic

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import io.github.ddmoyu.picomic.ui.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettingsLayoutTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun narrowLargeTextSettingsKeepSyncAndUpdateActionsReachable() {
        ui.runOnUiThread {
            val vm = ViewModelProvider(ui.activity)[AppViewModel::class.java]
            vm.preference("debugDemo", "false")
            ui.activity.setContent {
                val native = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(native.density, 1.6f)) {
                    Box(Modifier.width(360.dp)) { PiComicApp(vm) }
                }
            }
        }
        ui.onNodeWithContentDescription("设置").performClick()
        ui.onNodeWithText("数据与同步").performScrollTo().performClick()
        ui.onNodeWithText("WebDAV 同步").performScrollTo().performClick()
        ui.onNodeWithText("HTTPS 同步目录").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("预览只读导入").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        ui.onNodeWithText("测试连接").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        val file = File(ui.activity.getExternalFilesDir(null), "screenshots/23-large-text-webdav.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("更新").performScrollTo().performClick()
        ui.onNodeWithText("检查更新").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
    }
}
