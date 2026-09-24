package io.github.ddmoyu.picomic

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.source.picacg.*
import io.github.ddmoyu.picomic.ui.PicacgLoginScreen
import io.github.ddmoyu.picomic.ui.PiComicTheme
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PicacgLoginScreenTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @After fun finish() { scope.cancel() }
    @Test fun loginErrorSuccessAndLogoutUseRealHttpContract() {
        MockWebServer().use { server ->
            val anonymous = MockResponse().setResponseCode(401).setBody("""{"code":401,"error":"1005","message":"unauthorized"}""")
            server.enqueue(anonymous); server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":400,"message":"invalid credentials"}"""))
            server.enqueue(anonymous); server.enqueue(MockResponse().setBody("""{"code":200,"message":"success","data":{"token":"fixture-token"}}"""))
            server.enqueue(MockResponse().setBody("""{"code":200,"message":"success","data":{"user":{"_id":"fixture","name":"联调测试账号"}}}"""))
            server.start()
            val secrets = object : SecretStore {
                val data = mutableMapOf<String, ByteArray>()
                override fun read(key: String) = data[key]?.copyOf()
                override fun write(key: String, value: ByteArray) { data[key] = value.copyOf() }
                override fun remove(key: String) { data.remove(key) }
            }
            val engine = NetworkEngine(); val sessions = SessionCoordinator(secrets, engine)
            val controller = PicacgAccountController(sessions, engine, scope, {}) { PicacgClient(engine, server.url("/")) }
            ui.setContent { PiComicTheme(false, false) { PicacgLoginScreen(controller, true, openRecovery = {}, openNetwork = {}) } }
            ui.onNodeWithText("登录并验证").assertIsNotEnabled()
            ui.onNodeWithText("账号 / 邮箱").performTextInput("fixture@example.test")
            ui.onNodeWithText("密码").performTextInput("fixture-password")
            ui.onNodeWithText("登录并验证").performScrollTo().performClick()
            ui.waitUntil(10000) { !controller.state.value.busy && controller.state.value.message != null }
            ui.onNodeWithTag("login-success-dialog").assertDoesNotExist()
            ui.onNodeWithText("登录未通过，请检查账号和密码").performScrollTo().assertExists()
            assertNull(secrets.data["session.picacg"])
            assertNotNull(secrets.data["login-input.picacg"])
            ui.onNodeWithContentDescription("显示密码").performScrollTo().performClick()
            ui.onNodeWithText("密码").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("fixture-password")))
            ui.onNodeWithContentDescription("隐藏密码").performClick()
            ui.onNodeWithText("登录并验证").performScrollTo().performClick()
            ui.waitUntil(10000) { sessions.state.value["picacg"]?.status == AccountStatus.AUTHENTICATED && !controller.state.value.busy }
            ui.onNodeWithTag("login-success-dialog").assertIsDisplayed()
            ui.onNode(hasText("已登录 · 联调测试账号") and hasAnyAncestor(hasTestTag("login-success-dialog"))).assertIsDisplayed()
            ui.onNodeWithText("知道了").performClick()
            ui.onNodeWithTag("login-success-dialog").assertDoesNotExist()
            ui.onNodeWithTag("account-status").assertIsDisplayed()
            ui.onNodeWithText("已登录 · 联调测试账号").assertIsDisplayed()
            assertNotNull(secrets.data["session.picacg"])
            assertEquals("fixture@example.test", controller.rememberedAccounts.value["picacg"])
            StoredAccountCodec.decode(secrets.data.getValue("session.picacg")).use { assertArrayEquals("fixture-password".toCharArray(), it.login!!.password) }
            ui.onNodeWithText("清除本地账号").performScrollTo().performClick()
            ui.waitUntil(10000) { sessions.state.value["picacg"]?.status == AccountStatus.ANONYMOUS && !controller.state.value.busy }
            ui.onNodeWithText("未登录").performScrollTo().assertIsDisplayed()
            ui.onNodeWithTag("login-success-dialog").assertDoesNotExist()
            assertTrue(secrets.data.isEmpty())
            assertEquals(5, server.requestCount)
        }
    }
    @Test fun leavingLoginCancelsAttemptButRefillsRememberedInput() {
        var show by androidx.compose.runtime.mutableStateOf(true)
        val secrets = object : SecretStore {
            val data = mutableMapOf<String, ByteArray>()
            override fun read(key: String) = data[key]?.copyOf()
            override fun write(key: String, value: ByteArray) { data[key] = value.copyOf() }
            override fun remove(key: String) { data.remove(key) }
        }
        var probing = false
        val engine = NetworkEngine(); val sessions = SessionCoordinator(secrets, engine)
        val controller = PicacgAccountController(sessions, engine, scope, {}) { object : PicacgAuthApi {
            override suspend fun probe() { probing = true; awaitCancellation() }
            override suspend fun signIn(email: String, password: CharArray): SessionCandidate = error("must not send credentials")
            override suspend fun profile(candidate: SessionCandidate): String = error("must not validate")
        } }
        ui.setContent { PiComicTheme(false, false) { if (show) PicacgLoginScreen(controller, true, openRecovery = {}, openNetwork = {}) } }
        ui.onNodeWithText("账号 / 邮箱").performTextInput("fixture")
        ui.onNodeWithText("密码").performTextInput("fixture-password")
        ui.onNodeWithText("登录并验证").performScrollTo().performClick()
        ui.waitUntil { probing }
        ui.runOnIdle { show = false }; ui.waitForIdle()
        assertFalse(controller.state.value.busy)
        assertEquals(AccountStatus.ANONYMOUS, sessions.state.value["picacg"]?.status)
        assertNull(secrets.data["session.picacg"])
        ui.runOnIdle { show = true }; ui.waitForIdle()
        ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("清空密码").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("账号 / 邮箱").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("fixture")))
        ui.onNodeWithText("登录并验证").assertIsEnabled()
        ui.onNodeWithContentDescription("显示密码").performClick()
        ui.onNodeWithText("密码").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("fixture-password")))
        ui.onNodeWithContentDescription("隐藏密码").performClick()
        val file = File(ui.activity.getExternalFilesDir(null), "screenshots/22-picacg-login.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun failedCredentialsFillFromEncryptedStorageAfterRestartAcrossSources() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val engine = NetworkEngine()
        val secrets = KeystoreSecretStore(context, "picomic.failed-login-form-test")
        val sessions = SessionCoordinator(secrets, engine)
        val sources = listOf("picacg", "jmcomic", "htcomic")
        fun controller(source: String, coordinator: SessionCoordinator) = PasswordAccountController(
            source, AccountSlots.titles.getValue(source), coordinator, engine, scope, {}
        ) { object : PasswordAuthApi {
            override suspend fun probe() {}
            override suspend fun signIn(email: String, password: CharArray): SessionCandidate = throw PicacgFailure(PicacgFailureKind.CREDENTIALS)
            override suspend fun profile(candidate: SessionCandidate): String = error("A rejected login must not validate")
        } }
        var active by androidx.compose.runtime.mutableStateOf(controller(sources.first(), sessions))
        var show by androidx.compose.runtime.mutableStateOf(true)
        try {
            runBlocking { sources.forEach { sessions.logout(it) } }
            ui.setContent { PiComicTheme(false, false) { if (show) PicacgLoginScreen(active, true, openRecovery = {}, openNetwork = {}) } }
            sources.forEach { source ->
                ui.runOnIdle { active = controller(source, sessions) }
                ui.onNodeWithText("账号 / 邮箱").performTextInput("$source@example.test")
                ui.onNodeWithText("密码").performTextInput("$source-failed-password")
                ui.onNodeWithText("登录并验证").performScrollTo().performClick()
                ui.waitUntil(5000) { !active.state.value.busy && active.state.value.message != null }
                ui.onNodeWithTag("login-success-dialog").assertDoesNotExist()
                assertNull(runBlocking { sessions.storedCandidate(source) })
            }
            ui.runOnIdle { show = false }; ui.waitForIdle()
            // Recreate the encrypted store and coordinator, not merely the Compose form.
            val restarted = SessionCoordinator(KeystoreSecretStore(context, "picomic.failed-login-form-test"), engine)
            sources.forEach { source ->
                ui.runOnIdle { active = controller(source, restarted); show = true }
                ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("清空密码").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("账号 / 邮箱").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("$source@example.test")))
                ui.onNodeWithContentDescription("显示密码").performClick()
                ui.onNodeWithText("密码").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("$source-failed-password")))
                ui.onNodeWithContentDescription("隐藏密码").performClick()
                if (source == "picacg") screenshot("failed-reopened")
                ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("清空密码").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithContentDescription("显示密码").assertExists()
                ui.onNodeWithContentDescription("清空密码").performClick()
                ui.onNodeWithText("密码").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
                ui.runOnIdle { show = false }; ui.waitForIdle()
            }
        } finally { runBlocking { sources.forEach { sessions.logout(it) } } }
    }

    @Test fun savedCredentialsFillAfterRestartAndStayIsolatedBetweenSources() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val secrets = KeystoreSecretStore(context, "picomic.login-form-test")
        val engine = NetworkEngine()
        val seed = SessionCoordinator(secrets, engine)
        val sources = listOf("picacg", "jmcomic", "htcomic")
        try {
            runBlocking {
                sources.forEach { source ->
                    seed.logout(source)
                    RememberedLogin("$source@example.test", "$source-password".toCharArray()).use { login ->
                        val kind = if (source == "picacg") CredentialKind.USER_TOKEN else CredentialKind.COOKIE
                        seed.validateAndCommit(seed.begin(source), SessionCandidate(kind, "fixture-session".toByteArray()), PasswordRetention.Remember(login)) {
                            ValidationResult.Verified("界面测试账号")
                        }
                    }
                }
            }
            // A fresh coordinator has no remembered-account metadata: the form must read encrypted storage.
            val restarted = SessionCoordinator(KeystoreSecretStore(context, "picomic.login-form-test"), engine)
            val controllers = sources.map { source ->
                PasswordAccountController(source, AccountSlots.titles.getValue(source), restarted, engine, scope, {}) { error("Filling must not use the network") }
            }
            var selected by androidx.compose.runtime.mutableStateOf(0)
            var show by androidx.compose.runtime.mutableStateOf(true)
            ui.setContent { PiComicTheme(false, false) {
                if (show) PicacgLoginScreen(controllers[selected], true, openRecovery = {}, openNetwork = {})
            } }
            sources.forEachIndexed { index, source ->
                ui.runOnIdle { selected = index }
                ui.waitUntil(5000) { ui.onAllNodesWithText("$source@example.test").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithContentDescription("显示密码").performScrollTo().assertExists()
                ui.onNodeWithText("登录并验证").assertIsEnabled()
                if (index == 0) screenshot("after-hidden")
                ui.onNodeWithContentDescription("显示密码").performClick()
                ui.onNodeWithText("密码").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("$source-password")))
                if (index == 0) screenshot("after-visible")
            }
            ui.onNodeWithContentDescription("清空密码").performClick()
            ui.onNodeWithText("密码").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            ui.onNodeWithText("登录并验证").assertIsNotEnabled()
            ui.onNodeWithContentDescription("显示密码").assertExists()
            // Removing the form and reopening restores the stored values, with visibility reset.
            ui.runOnIdle { show = false }; ui.waitForIdle()
            ui.runOnIdle { show = true }
            ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("清空密码").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithContentDescription("显示密码").assertExists()
            repeat(3) {
                ui.onNodeWithContentDescription("显示密码").performClick()
                ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("清空密码").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithContentDescription("显示密码").assertExists()
            }
            ui.onNodeWithContentDescription("显示密码").performClick()
            ui.onNodeWithText("密码").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("htcomic-password")))
            ui.onNodeWithText("账号 / 邮箱").performTextReplacement("different@example.test")
            ui.onNodeWithText("密码").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            ui.onNodeWithContentDescription("清空账号").performClick()
            ui.onNodeWithText("账号 / 邮箱").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            ui.onNodeWithText("登录并验证").assertIsNotEnabled()
        } finally { runBlocking { sources.forEach { seed.logout(it) } } }
    }

    @Test fun forgettingPasswordAndClearingAccountRemoveAutofill() {
        val secrets = object : SecretStore {
            val data = mutableMapOf<String, ByteArray>()
            override fun read(key: String) = data[key]?.copyOf()
            override fun write(key: String, value: ByteArray) { data[key] = value.copyOf() }
            override fun remove(key: String) { data.remove(key) }
        }
        val engine = NetworkEngine(); val sessions = SessionCoordinator(secrets, engine)
        runBlocking {
            RememberedLogin("fixture@example.test", "fixture-password".toCharArray()).use { login ->
                sessions.validateAndCommit(sessions.begin("picacg"), SessionCandidate(CredentialKind.USER_TOKEN, "fixture".toByteArray()), PasswordRetention.Remember(login)) {
                    ValidationResult.Verified("界面测试账号")
                }
            }
        }
        val controller = PicacgAccountController(sessions, engine, scope, {}) { error("Must not contact the platform") }
        var show by androidx.compose.runtime.mutableStateOf(true)
        ui.setContent { PiComicTheme(false, false) { if (show) PicacgLoginScreen(controller, true, openRecovery = {}, openNetwork = {}) } }
        ui.waitUntil(5000) { ui.onAllNodesWithContentDescription("清空密码").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("忘记已保存的密码").performScrollTo().performClick()
        ui.waitUntil(5000) { !controller.state.value.busy && controller.rememberedAccounts.value.isEmpty() }
        ui.onNodeWithText("密码").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertEquals(AccountStatus.AUTHENTICATED, sessions.state.value["picacg"]?.status)
        ui.runOnIdle { show = false }; ui.waitForIdle(); ui.runOnIdle { show = true }; ui.waitForIdle()
        ui.onNodeWithText("密码").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        ui.onNodeWithText("账号 / 邮箱").performTextInput("another@example.test")
        ui.onNodeWithText("密码").performTextInput("another-password")
        ui.onNodeWithText("清除本地账号").performScrollTo().performClick()
        ui.waitUntil(5000) { !controller.state.value.busy && sessions.state.value["picacg"]?.status == AccountStatus.ANONYMOUS }
        ui.onNodeWithText("账号 / 邮箱").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        ui.onNodeWithText("密码").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertTrue(secrets.data.isEmpty())
    }

    private fun screenshot(name: String) {
        val file = File(ui.activity.getExternalFilesDir(null), "password-input/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
