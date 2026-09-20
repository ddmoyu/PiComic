package io.github.ddmoyu.picomic

import android.content.pm.ActivityInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Before
import io.github.ddmoyu.picomic.data.ReadingProgressRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabSwipeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Before fun clearHistory() { runBlocking { ReadingProgressRepository.get(ui.activity).clear() }; ui.waitForIdle() }

    private fun selectSource(title: String) {
        ui.onNode(hasText(title) and hasClickAction()).performScrollTo().performClick()
        selected(title)
    }

    private fun selected(title: String) {
        ui.onNode(hasText(title) and isSelected()).assertIsDisplayed()
    }

    @Test fun discoverySwipesSyncSourceAndSearchReturn() {
        selectSource("picacg")
        val pages = ui.onNodeWithTag("discover-pages")
        pages.performTouchInput { swipeRight() }
        selected("picacg")
        // A short drag must snap back; vertical browsing must not change sources.
        pages.performTouchInput {
            swipe(Offset(width * .55f, centerY), Offset(width * .52f, centerY), 600)
        }
        selected("picacg")
        pages.performTouchInput { swipeUp() }
        selected("picacg")
        pages.performTouchInput { swipeLeft() }
        selected("E-Hentai")
        ui.onNodeWithText("E-Hentai · 本地示意作品").assertIsDisplayed()
        ui.onVisibleText("雨后的第七站").performClick()
        ui.onNodeWithText("E-Hentai").assertIsDisplayed()
        ui.onNodeWithContentDescription("返回").performClick()
        selected("E-Hentai")
        pages.performTouchInput { swipeRight() }
        selected("picacg")
        listOf("E-Hentai", "禁漫天堂", "Hitomi", "绅士漫画", "nhentai").forEach {
            pages.performTouchInput { swipeLeft() }
            selected(it)
        }
        pages.performTouchInput { swipeLeft() }
        selected("nhentai")
        ui.onNodeWithContentDescription("搜索").performClick()
        selectSource("picacg")
        ui.onNodeWithContentDescription("返回").performClick()
        selected("picacg")
        ui.onNodeWithText("picacg · 本地示意作品").assertIsDisplayed()
    }

    @Test fun categorySwipesShowMatchingCategories() {
        ui.onNodeWithContentDescription("分类").performClick()
        selectSource("picacg")
        val pages = ui.onNodeWithTag("category-pages")
        pages.performTouchInput { swipeLeft() }
        selected("E-Hentai")
        ui.onNodeWithText("E-Hentai / ExHentai").assertIsDisplayed()
        ui.onVisibleText("游戏 CG").performClick()
        ui.onNodeWithContentDescription("返回").performClick()
        selected("E-Hentai")
        selectSource("Hitomi")
        pages.performTouchInput { swipeRight() }
        selected("禁漫天堂")
        ui.onVisibleText("连载").assertIsDisplayed()
        ui.onNodeWithContentDescription("探索").performClick()
        selected("禁漫天堂")
        ui.onNodeWithText("禁漫天堂 · 本地示意作品").assertIsDisplayed()
    }

    @Test fun libraryEmptyPagesSwipeAndRestoreSelection() {
        ui.onNodeWithContentDescription("书架").performClick()
        ui.onNodeWithText("收藏").performClick()
        val pages = ui.onNodeWithTag("library-pages")
        pages.performTouchInput { swipeLeft() }
        selected("阅读历史")
        ui.onNodeWithText("还没有阅读记录").assertIsDisplayed()
        pages.performTouchInput { swipeLeft() }
        selected("下载管理")
        ui.onNodeWithText("暂无下载任务").assertIsDisplayed()
        pages.performTouchInput { swipeLeft() }
        selected("下载管理")
        pages.performTouchInput { swipeRight() }
        selected("阅读历史")
        ui.onNodeWithText("收藏").performClick()
        selected("收藏")
        ui.onNodeWithText("下载管理").performClick()
        selected("下载管理")
        ui.onNodeWithContentDescription("探索").performClick()
        ui.onNodeWithContentDescription("书架").performClick()
        selected("下载管理")
        ui.runOnUiThread { ui.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        selected("下载管理")
        ui.runOnUiThread { ui.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
}
