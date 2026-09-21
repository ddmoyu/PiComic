package io.github.ddmoyu.picomic

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Browser intents are intercepted; no websites, external browser windows or images are opened. */
class PasswordRecoveryFlowTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun allFiveLoginEntriesUseTheDefaultBrowserAndKeepTheLoginScreen() {
        val vm = AppViewModel(ui.activity.application)
        val store = ViewModelStore().apply { put("recovery-flow", vm) }
        var source by mutableStateOf(Source.PICACG)
        val opened = mutableListOf<Intent>()
        val context = object : ContextWrapper(ui.activity) {
            override fun startActivity(intent: Intent) { opened += intent }
        }
        val htHost = "www.wn10.cfd"
        try {
            runBlocking { vm.network.awaitReady() }
            ui.runOnIdle { vm.picacgAccount.cancel(); vm.jmAccount.cancel(); vm.htAccount.cancel(); vm.ehAccount.cancel(); vm.nhKeyAccount.cancel(); vm.nhWebAccount.cancel() }
            val revisions = vm.network.sessions.changes.value
            ui.setContent { PiComicTheme(false, false) { key(source) {
                val open = { openPasswordRecovery(context, source, htHost) {} }
                when (source) {
                    Source.PICACG, Source.JMCOMIC, Source.HTCOMIC -> PicacgLoginScreen(
                        when (source) { Source.PICACG -> vm.picacgAccount; Source.JMCOMIC -> vm.jmAccount; else -> vm.htAccount },
                        true, openRecovery = open, openNetwork = {})
                    Source.EHENTAI -> EhLoginScreen(vm, open, {})
                    Source.NHENTAI -> NhLoginScreen(UiState(), vm, open, {})
                    else -> LoginScreen(source)
                }
            } } }
            for ((index, platform) in Source.entries.filter { it != Source.HITOMI }.withIndex()) {
                ui.runOnIdle { source = platform }
                ui.onNodeWithText("忘记密码", substring = false).performScrollTo().performClick()
                ui.runOnIdle {
                    assertEquals(index + 1, opened.size)
                    val intent = opened.last()
                    assertEquals(Intent.ACTION_VIEW, intent.action)
                    assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
                    assertEquals(passwordRecoveryPage(platform, htHost)!!.url.toString(), intent.dataString)
                    assertNull(intent.component)
                    assertNull(intent.`package`)
                    assertNull(intent.selector)
                    assertNull(intent.extras)
                }
                ui.onNodeWithText("忘记密码", substring = false).assertIsDisplayed()
                ui.onNodeWithTag("password-recovery").assertDoesNotExist()
                ui.onNodeWithTag("recovery-webview").assertDoesNotExist()
            }
            ui.runOnIdle { source = Source.HITOMI }
            ui.onNodeWithText("忘记密码", substring = false).assertDoesNotExist()
            assertEquals(revisions, vm.network.sessions.changes.value)
        } finally { ui.runOnIdle { store.clear() } }
    }

    @Test fun unavailableOrBlockedBrowserShowsAMessageWithoutCrashing() {
        for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
            val context = object : ContextWrapper(ui.activity) {
                override fun startActivity(intent: Intent) { throw failure }
            }
            val messages = mutableListOf<String>()
            ui.runOnIdle { openPasswordRecovery(context, Source.JMCOMIC, "www.wn10.shop", messages::add) }
            assertEquals(1, messages.size)
            assertEquals(if (failure is ActivityNotFoundException) "未找到可用浏览器，请先安装或启用浏览器"
                else "无法打开浏览器，请检查系统设置后重试", messages.single())
        }
    }
}
