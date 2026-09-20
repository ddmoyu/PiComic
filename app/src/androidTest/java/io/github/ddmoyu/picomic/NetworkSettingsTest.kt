package io.github.ddmoyu.picomic

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ddmoyu.picomic.network.NetworkProfile
import io.github.ddmoyu.picomic.network.NetworkRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NetworkSettingsTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @After fun restoreSystemProfile() = runBlocking { NetworkRepository.get(ui.activity).save(NetworkProfile.FollowSystem) }
    @Test fun invalidPortDoesNotChangeProfileAndDeadProxyReportsFailure() {
        runBlocking { NetworkRepository.get(ui.activity).save(NetworkProfile.FollowSystem) }
        ui.onNodeWithContentDescription("设置").performClick()
        ui.onNodeWithText("设置代理").performScrollTo().performClick()
        ui.onNodeWithText("自定义 HTTP 代理").performClick()
        ui.onNodeWithText("代理主机").performTextReplacement("127.0.0.1")
        ui.onNodeWithText("端口").performTextReplacement("99999")
        ui.onNodeWithText("保存网络设置").performScrollTo().performClick()
        ui.onNodeWithText("端口须为 1–65535").assertExists()
        ui.onNodeWithText("端口").performScrollTo().performTextReplacement("9")
        ui.onNodeWithText("保存网络设置").performScrollTo().performClick()
        ui.waitUntil(10000) { NetworkRepository.get(ui.activity).state.value.custom }
        ui.onNodeWithText("测试已保存的连接").performScrollTo().performClick()
        ui.waitUntil(20000) { NetworkRepository.get(ui.activity).state.value.message?.contains("连接失败") == true }
        ui.onNodeWithText("连接失败，请检查网络、代理地址与认证信息").performScrollTo().assertIsDisplayed()
        val file = File(ui.activity.getExternalFilesDir(null), "screenshots/21-network-proxy-failure.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
