package io.github.ddmoyu.picomic

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Before
import io.github.ddmoyu.picomic.data.ReadingProgressRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Before fun clearHistory() {
        ui.runOnIdle { androidx.lifecycle.ViewModelProvider(ui.activity)[io.github.ddmoyu.picomic.ui.AppViewModel::class.java].preference("debugDemo", "true") }
        runBlocking { ReadingProgressRepository.get(ui.activity).clear() }
        // Reader-control tests persist other modes; each UI flow starts with its own declared baseline.
        ui.runOnIdle { androidx.lifecycle.ViewModelProvider(ui.activity)[io.github.ddmoyu.picomic.ui.AppViewModel::class.java].preference("readingMode", "纵向连续") }
        ui.waitForIdle()
    }
    private fun shot(name: String) {
        ui.waitForIdle()
        val file=File(ui.activity.getExternalFilesDir(null),"screenshots/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @org.junit.After fun disableDemo() { ui.runOnIdle { androidx.lifecycle.ViewModelProvider(ui.activity)[io.github.ddmoyu.picomic.ui.AppViewModel::class.java].preference("debugDemo", "false") } }
    @Test fun browseSearchAndCategoryNavigation() {
        ui.onNodeWithText("探索").assertExists()
        shot("01-discover")
        ui.onNodeWithText("Hitomi").performScrollTo().performClick()
        ui.onNodeWithContentDescription("搜索").performClick()
        ui.onNodeWithText("最近搜索").assertExists()
        ui.onNodeWithText("作品、作者或标签").performTextInput("雨后")
        ui.onNodeWithText("搜索").performClick()
        ui.onVisibleText("雨后的第七站").assertExists()
        shot("02-search")
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithContentDescription("分类").performClick()
        ui.onNode(hasText("Hitomi") and isSelected()).assertExists()
        shot("03-categories")
        ui.onVisibleText("全部").performClick()
        ui.onVisibleText("雨后的第七站").assertExists()
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithContentDescription("书架").performClick()
        ui.onNodeWithText("阅读历史").assertExists()
        ui.onNodeWithText("下载管理").assertExists()
    }
    @Test fun detailReadingAndLibraryFlow() {
        ui.onVisibleText("雨后的第七站").performClick()
        shot("04-detail")
        if(ui.onAllNodesWithContentDescription("收藏作品").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithContentDescription("收藏作品").performClick()
        ui.onNodeWithContentDescription("取消收藏").assertExists()
        ui.onNodeWithText("开始阅读").performClick()
        ui.onNodeWithTag("reader").performTouchInput { click(center) }
        ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("阅读设置").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("阅读设置").assertExists()
        shot("05-reader")
        ui.onNodeWithText("下一话").performClick()
        ui.onNodeWithText("第 2 话 · 纵向连续").assertExists()
        ui.onNodeWithContentDescription("退出阅读").performClick()
        ui.onNodeWithText("继续阅读").assertExists()
        ui.onNodeWithContentDescription("下载章节").performClick()
        ui.onAllNodesWithText("第 1 话").onLast().performClick()
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithContentDescription("书架").performClick()
        shot("06-library")
        ui.onNodeWithText("下载管理").performClick()
        ui.onNodeWithContentDescription("暂停任务").performClick()
        ui.onNodeWithContentDescription("继续任务").assertExists()
        ui.onNodeWithText("阅读历史").performClick()
        ui.onVisibleText("雨后的第七站").assertExists()
    }
    @Test fun settingsSourceAndAppearanceFlow() {
        ui.onNodeWithContentDescription("设置").performClick()
        ui.onNodeWithText("账号管理").assertExists()
        shot("07-settings")
        ui.onNodeWithText("账号管理").performClick()
        shot("08-accounts")
        ui.onNodeWithText("账号密码登录 · 加密会话").performClick()
        ui.onNodeWithText("账号 / 邮箱").assertExists()
        ui.onNodeWithText("登录并验证").assertIsNotEnabled()
        ui.onNodeWithText("体验演示登录状态").assertDoesNotExist()
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("漫画源").performClick()
        ui.onNodeWithText("禁漫天堂").performScrollTo().performClick()
        ui.onNodeWithText("图片分流").assertExists()
        shot("09-sources")
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("外观").performScrollTo().performClick()
        ui.onNodeWithText("深色模式").performClick()
        shot("10-dark-appearance")
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("更新").performScrollTo().performClick()
        ui.onNodeWithText("检查更新").assertIsNotEnabled()
        ui.onNodeWithText("公开发布渠道尚未配置").assertExists()
        shot("11-updates")
    }
    @Test fun continuousReaderPullsToNextChapter() {
        ui.onVisibleText("雨后的第七站").performClick()
        ui.onNodeWithText("开始阅读").performClick()
        ui.onNodeWithTag("reader").performTouchInput { click(center) }
        ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("阅读设置").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("reader-pages").performScrollToIndex(23)
        ui.onNodeWithTag("reader-pages").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(centerX,height*.65f),androidx.compose.ui.geometry.Offset(centerX,height*.48f),600)
        }
        ui.onNodeWithText("第 1 话 · 纵向连续").assertExists()
        ui.onNodeWithTag("reader-pages").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(centerX,height*.78f),androidx.compose.ui.geometry.Offset(centerX,height*.18f),700)
        }
        ui.waitUntil(10000) { ui.onAllNodesWithText("第 2 话 · 纵向连续").fetchSemanticsNodes().isNotEmpty() }
        ui.waitUntil(10000) { ui.onAllNodes(hasText("目录") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        shot("13-reader-next-chapter")
        ui.onNodeWithText("目录").performClick()
        ui.waitUntil(5000) { ui.onAllNodesWithTag("reader-chapters").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("reader-chapters").performScrollToIndex(7)
        ui.onNodeWithText("第 8 话").performClick()
        ui.onNodeWithTag("reader-pages").performScrollToIndex(23)
        ui.onNodeWithTag("reader-pages").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(centerX,height*.75f),androidx.compose.ui.geometry.Offset(centerX,height*.2f),500)
        }
        ui.onNodeWithText("第 8 话 · 纵向连续").assertExists()
    }
    @Test fun searchSurvivesRotation() {
        ui.onNodeWithContentDescription("搜索").performClick()
        ui.onNodeWithText("作品、作者或标签").performTextInput("雨后")
        ui.onNodeWithText("搜索").performClick()
        ui.runOnUiThread { ui.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        ui.waitForIdle()
        ui.waitUntil(10000) { ui.onAllNodesWithText("雨后的第七站").fetchSemanticsNodes().isNotEmpty() }
        ui.onVisibleText("雨后的第七站").assertExists()
        shot("12-landscape-search")
        ui.runOnUiThread { ui.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
}
