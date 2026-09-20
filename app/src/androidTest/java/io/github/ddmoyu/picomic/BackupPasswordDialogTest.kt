package io.github.ddmoyu.picomic

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import io.github.ddmoyu.picomic.ui.BackupPasswordDialog
import io.github.ddmoyu.picomic.ui.PiComicTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackupPasswordDialogTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun exportRequiresMatchingPasswordAndTransfersClearedInputWithExplicitAccountChoice() {
        var captured: CharArray? = null; var include = true
        ui.setContent { PiComicTheme(false, false) { BackupPasswordDialog(true, false, null, {}, { secret, accounts -> captured = secret; include = accounts }) } }
        val confirm = ui.onNodeWithText("加密并选择保存位置")
        confirm.assertIsNotEnabled()
        ui.onNodeWithText("备份密码").performTextInput("fixture-password")
        ui.onNodeWithText("再次输入备份密码").performTextInput("different-password")
        confirm.assertIsNotEnabled()
        ui.onNodeWithText("再次输入备份密码").performTextReplacement("fixture-password")
        ui.onNode(isToggleable()).performScrollTo().performClick()
        confirm.assertIsEnabled().performClick()
        ui.runOnIdle { assertArrayEquals("fixture-password".toCharArray(), captured); assertFalse(include); captured?.fill('\u0000') }
        ui.onNodeWithText("备份密码").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        ui.onNodeWithText("再次输入备份密码").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }
    @Test fun cancellingAndReopeningImportDoesNotRetainPassword() {
        val show = mutableStateOf(true)
        ui.setContent { PiComicTheme(false, false) { if (show.value) BackupPasswordDialog(false, false, null, { show.value = false }, { _, _ -> fail("Cancelled import must not decrypt") }) } }
        ui.onNodeWithText("备份密码").performTextInput("fixture-password")
        ui.onNodeWithText("取消").performClick()
        ui.runOnIdle { show.value = true }
        ui.onNodeWithText("备份密码").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        ui.onNodeWithText("解密并预览").assertIsNotEnabled()
    }
}
