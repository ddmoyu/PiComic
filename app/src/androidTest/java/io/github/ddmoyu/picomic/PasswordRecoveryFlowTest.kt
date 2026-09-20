package io.github.ddmoyu.picomic

import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceResponse
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.ui.*
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

/** All webpages are local HTML fixtures in a headless emulator. No platform pages are rendered. */
class PasswordRecoveryFlowTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun findBrowser(view: View): PasswordRecoveryView? = when (view) {
        is PasswordRecoveryView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findBrowser(view.getChildAt(it)) }
        else -> null
    }
    private fun browser() = checkNotNull(findBrowser(ui.activity.window.decorView))
    private fun html(value: String) = WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(value.toByteArray()))

    @Test fun allFiveLoginEntriesOpenAnEmbeddedPageAndReturnWithoutChangingAccounts() {
        val vm = AppViewModel(ui.activity.application)
        val store = ViewModelStore().apply { put("recovery-flow", vm) }
        val page = RecoveryPage("https://recovery-fixture.example.test/".toHttpUrl(), setOf("recovery-fixture.example.test"))
        var source by mutableStateOf(Source.PICACG)
        var recovery by mutableStateOf(false)
        val opened = AtomicInteger()
        try {
            runBlocking { vm.network.awaitReady() }
            ui.runOnIdle { vm.picacgAccount.cancel(); vm.jmAccount.cancel(); vm.htAccount.cancel(); vm.ehAccount.cancel(); vm.nhKeyAccount.cancel(); vm.nhWebAccount.cancel() }
            val revisions = vm.network.sessions.changes.value
            ui.setContent { PiComicTheme(false, false) { key(source) {
                val open = { recovery = true }
                if (recovery) RecoveryWebPage(page, vm, { recovery = false }) {
                    if (it.isForMainFrame) opened.incrementAndGet()
                    html("<html><body>Recovery fixture</body></html>")
                }
                else when (source) {
                    Source.PICACG, Source.JMCOMIC, Source.HTCOMIC -> PicacgLoginScreen(
                        when (source) { Source.PICACG -> vm.picacgAccount; Source.JMCOMIC -> vm.jmAccount; else -> vm.htAccount },
                        true, openRecovery = open, openNetwork = {})
                    Source.EHENTAI -> EhLoginScreen(vm, open, {})
                    Source.NHENTAI -> NhLoginScreen(UiState(), vm, open, {})
                    else -> LoginScreen(source)
                }
            } } }
            for (platform in Source.entries.filter { it != Source.HITOMI }) {
                ui.runOnIdle { source = platform }
                ui.onNodeWithText("忘记密码", substring = false).performScrollTo().performClick()
                try { ui.waitUntil(10000) { ui.onAllNodesWithTag("recovery-webview").fetchSemanticsNodes().isNotEmpty() } }
                catch (failure: Throwable) { throw AssertionError("$platform: ${ui.onRoot().printToString()}", failure) }
                ui.runOnIdle {
                    assertFalse(browser().settings.allowFileAccess)
                    assertFalse(browser().settings.allowContentAccess)
                }
                val count = opened.get()
                ui.onNodeWithText("重新加载").performClick()
                ui.waitUntil(5000) { opened.get() > count }
                ui.onNodeWithTag("recovery-webview").assertIsDisplayed()
                ui.onNodeWithText("返回登录").performClick()
                ui.onNodeWithTag("password-recovery").assertDoesNotExist()
                ui.onNodeWithText("忘记密码", substring = false).assertExists()
            }
            ui.runOnIdle { source = Source.HITOMI }
            ui.onNodeWithText("忘记密码", substring = false).assertDoesNotExist()
            assertEquals(revisions, vm.network.sessions.changes.value)
        } finally { ui.runOnIdle { recovery = false }; ui.waitForIdle(); ui.runOnIdle { store.clear() } }
    }

    @Test fun systemBackUsesWebHistoryThenReturnsAndNetworkChangesAllowRetry() {
        val vm = AppViewModel(ui.activity.application)
        val store = ViewModelStore().apply { put("recovery-back", vm) }
        val page = RecoveryPage("https://recovery-fixture.example.test/".toHttpUrl(), setOf("recovery-fixture.example.test"))
        val opened = AtomicInteger()
        var closed by mutableStateOf(false)
        try {
            ui.setContent { PiComicTheme(false, false) {
                if (!closed) RecoveryWebPage(page, vm, { closed = true }) {
                    if (it.isForMainFrame) opened.incrementAndGet()
                    html("<html><body style='margin:0'><a style='display:block;height:100vh' href='/step2/'>Recovery next step</a></body></html>")
                }
            } }
            ui.waitUntil(5000) { ui.onAllNodesWithTag("recovery-webview").fetchSemanticsNodes().isNotEmpty() }
            ui.waitUntil(5000) { var ready = false; ui.runOnIdle { ready = opened.get() > 0 && browser().progress == 100 && browser().url == page.url.toString() }; ready }
            // Create actual WebView navigation history using two fixture document loads.
            ui.runOnIdle { browser().loadUrl(page.url.resolve("step2/")!!.toString()) }
            try { ui.waitUntil(5000) { var history = false; ui.runOnIdle { history = browser().hasHistory() }; history } }
            catch (failure: Throwable) {
                var state = ""
                ui.runOnIdle { state = "url=${browser().url}, progress=${browser().progress}, history=${browser().copyBackForwardList().currentIndex}/${browser().copyBackForwardList().size}, nativeBack=${browser().canGoBack()}, requests=${opened.get()}" }
                throw AssertionError("$state\n${ui.onRoot().printToString()}", failure)
            }
            ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.waitUntil(5000) { var start = false; ui.runOnIdle { start = browser().url == page.url.toString() }; start }
            assertFalse(closed)
            ui.runOnIdle { vm.network.engine.change(vm.network.engine.status.profile) }
            ui.waitUntil(5000) { ui.onAllNodesWithText("网络设置已更改，请重新加载页面").fetchSemanticsNodes().isNotEmpty() }
            val before = opened.get()
            ui.onNodeWithText("重新加载").performClick()
            ui.waitUntil(5000) { opened.get() > before }
            ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.runOnIdle { assertTrue(closed) }
        } finally { ui.runOnIdle { closed = true }; ui.waitForIdle(); ui.runOnIdle { store.clear() } }
    }
}
