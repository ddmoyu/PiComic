package io.github.ddmoyu.picomic

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import io.github.ddmoyu.picomic.ui.PiComicTheme
import io.github.ddmoyu.picomic.ui.UpdateSettingsContent
import io.github.ddmoyu.picomic.update.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Headless layout/interaction checks; release notes are synthetic text and no links are opened. */
class UpdateSettingsUiTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val asset = ReleaseAsset(2, "fixture.apk", 4 * 1048576L, "https://github.com/fixture/app/releases/download/v1.0/fixture.apk".toHttpUrl(), null)
    private val artifact = UpdateArtifact(asset, listOf("arm64-v8a"), 26, "a".repeat(64))
    private val bundle = UpdateBundle(ReleaseInfo(1, "v1.0", "https://github.com/fixture/app/releases/tag/v1.0", "2026-09-20T00:00:00Z",
        (1..100).joinToString("\n") { "测试更新说明第 $it 行" }, listOf(asset)), "1.0", BuildConfig.VERSION_CODE + 1L, listOf(artifact), "", "")
    private fun available() = UpdateState(true, UpdatePhase.AVAILABLE, bundle, artifact, initialized = true)

    @Test fun longNotesKeepActionsFixedAndButtonControlsDownloadPauseResumeAndCancel() {
        var state by mutableStateOf(available())
        var downloads = 0; var pauses = 0; var cancels = 0
        ui.setContent { PiComicTheme(false, false) {
            UpdateSettingsContent(state, true, false, {},
                onDownload = { downloads++; state = state.copy(phase = UpdatePhase.DOWNLOADING, task = true, downloaded = 1048576) },
                onPause = { pauses++; state = state.copy(phase = UpdatePhase.PAUSED) },
                onCancel = { cancels++; state = UpdateState(true, initialized = true) }, onMirrors = {}, onOpenRelease = {}, onInstall = {})
        } }
        val button = ui.onNodeWithTag("update-primary")
        button.assertIsDisplayed().assertTextContains("下载更新")
        val before = button.fetchSemanticsNode().boundsInRoot
        val footer = ui.onNodeWithTag("update-actions").fetchSemanticsNode().boundsInRoot
        assertEquals(ui.onNodeWithTag("update-page").fetchSemanticsNode().boundsInRoot.bottom, footer.bottom, 1f)
        assertTrue(ui.onNodeWithTag("update-content").fetchSemanticsNode().boundsInRoot.bottom <= footer.top)
        ui.onNodeWithText("查看 GitHub Release").performScrollTo()
        button.assertIsDisplayed()
        assertEquals(before, button.fetchSemanticsNode().boundsInRoot)
        button.performClick()
        button.assertTextContains("下载中 25%")
        button.assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(.25f, 0f..1f)))
        ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(1)
        button.performClick()
        button.assertTextContains("继续下载 25%")
        button.performClick()
        ui.runOnIdle { assertEquals(2, downloads); assertEquals(1, pauses) }
        ui.onNodeWithText("取消更新").performClick()
        ui.onNodeWithText("保留").performClick()
        ui.runOnIdle { assertEquals(0, cancels) }
        ui.onNodeWithText("取消更新").performClick()
        ui.onNode(hasText("取消更新") and hasAnyAncestor(isDialog())).performClick()
        button.assertTextContains("检查更新").assertIsDisplayed()
        ui.runOnIdle { assertEquals(1, cancels) }
    }

    @Test fun checkingVerificationAndInstallationStayInTheSameActionArea() {
        var state by mutableStateOf(UpdateState(false))
        var installing by mutableStateOf(false)
        var installs = 0
        ui.setContent { PiComicTheme(true, false) {
            UpdateSettingsContent(state, true, installing, {}, {}, {}, {}, {}, {}, { installs++ })
        } }
        val button = ui.onNodeWithTag("update-primary")
        button.assertIsNotEnabled()
        ui.runOnIdle { state = available().copy(phase = UpdatePhase.CHECKING) }
        button.assertTextContains("正在检查更新").assertIsNotEnabled()
        ui.runOnIdle { state = available().copy(phase = UpdatePhase.VERIFYING, task = true, downloaded = asset.size) }
        button.assertTextContains("正在校验安装包").assertIsNotEnabled()
        ui.onNodeWithText("取消更新").assertIsDisplayed()
        ui.runOnIdle { state = state.copy(phase = UpdatePhase.READY) }
        button.assertTextContains("安装更新").assertIsEnabled().performClick()
        ui.runOnIdle { assertEquals(1, installs); installing = true }
        button.assertTextContains("正在核对安装包").assertIsNotEnabled()
    }
}
