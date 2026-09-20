package io.github.ddmoyu.picomic

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import io.github.ddmoyu.picomic.backup.BackupEncryption
import io.github.ddmoyu.picomic.backup.ConfigBackupCodec
import io.github.ddmoyu.picomic.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercises real encryption/repository/URI callbacks, substituting only the system file picker. */
class BackupSettingsFlowTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    @Test fun exportThenImportRejectsWrongPasswordAndDiscardsPreviewInBackground() {
        val file = File.createTempFile("config-fixture", ".picomic", ui.activity.cacheDir)
        var selections = 0
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                    selections++
                    dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
                }
            }
        }
        try {
            ui.setContent {
                val vm = ViewModelProvider(ui.activity)[AppViewModel::class.java]
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { PiComicTheme(false, false) { Column { BackupSettings(vm) } } }
            }
            ui.onNodeWithText("导出配置与账号").performClick()
            ui.onNodeWithText("备份密码").performTextInput("fixture-password")
            ui.onNodeWithText("再次输入备份密码").performTextInput("fixture-password")
            ui.onNode(isToggleable()).performScrollTo().performClick() // This test must not read any saved real account.
            ui.onNodeWithText("加密并选择保存位置").performClick()
            ui.waitUntil(20_000) { ui.onAllNodesWithText("配置已加密导出，请妥善保存备份密码").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, selections)
            assertTrue(BackupEncryption.isEncrypted(file.readBytes()))
            val plaintext = BackupEncryption.decrypt(file.readBytes(), "fixture-password".toCharArray())
            try { ConfigBackupCodec.decode(plaintext).use { assertTrue(it.accounts.isEmpty()) } } finally { plaintext.fill(0) }
            ui.onNodeWithText("导入配置与账号").performClick()
            ui.onNodeWithText("备份密码").performTextInput("incorrect-fixture")
            ui.onNodeWithText("解密并预览").performClick()
            ui.waitUntil(20_000) { ui.onAllNodesWithText("密码错误、文件损坏或版本不支持，原数据未改动").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("数据合并预览").assertDoesNotExist()
            ui.onNodeWithText("备份密码").performTextInput("fixture-password")
            ui.onNodeWithText("解密并预览").performClick()
            ui.waitUntil(20_000) { ui.onAllNodesWithText("数据合并预览").fetchSemanticsNodes().isNotEmpty() }
            ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            ui.onNodeWithText("数据合并预览").assertDoesNotExist()
            assertEquals(2, selections)
        } finally { file.delete() }
    }
}
