package io.github.ddmoyu.picomic

import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.source.picacg.*
import io.github.ddmoyu.picomic.ui.PicacgLoginScreen
import io.github.ddmoyu.picomic.ui.PiComicTheme
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PicacgRegistrationScreenTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @After fun finish() { scope.cancel() }

    @Test fun backgroundDuringSubmissionKeepsRecordAndWaitsForExplicitResume() {
        val store = object : SecretStore {
            val data = mutableMapOf<String, ByteArray>()
            override fun read(key: String) = data[key]?.copyOf()
            override fun write(key: String, value: ByteArray) { data[key] = value.copyOf() }
            override fun remove(key: String) { data.remove(key) }
        }
        var submits = 0; var logins = 0
        val engine = NetworkEngine(); val sessions = SessionCoordinator(store, engine)
        val controller = PicacgAccountController(sessions, engine, scope, {}) { object : PicacgRegistrationApi {
            override suspend fun probe() {}
            override suspend fun register(details: PicacgRegistration) { submits++; awaitCancellation() }
            override suspend fun signIn(email: String, password: CharArray): SessionCandidate {
                logins++; return SessionCandidate(CredentialKind.USER_TOKEN, "fixture-token".toByteArray())
            }
            override suspend fun profile(candidate: SessionCandidate) = "恢复测试账号"
        } }
        ui.setContent { PiComicTheme(false, false) { PicacgLoginScreen(controller, true, openRecovery = {}, openNetwork = {}) } }
        ui.waitUntil(5000) { controller.registration.value.ready }
        ui.onNodeWithTag("picacg-register").performScrollTo().performClick()
        ui.waitUntil(5000) { submits == 1 }
        ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        ui.onNodeWithTag("registration-result").assertDoesNotExist()
        assertEquals(RegistrationPhase.SUBMITTED, controller.registration.value.phase)
        assertEquals(0, logins)
        ui.onNodeWithTag("picacg-register").performScrollTo().performClick()
        ui.waitUntil(5000) { !controller.state.value.busy && controller.state.value.loginSucceeded }
        ui.onNodeWithTag("registration-result").assertIsDisplayed()
        assertEquals(1, submits); assertEquals(1, logins)
        ui.onNodeWithText("完成").performClick()
        runBlocking { sessions.logout("picacg") }
    }

    @Test fun oneTapWithoutAnyInputRegistersCopiesAndRestoresEncryptedCredentials() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val store = KeystoreSecretStore(context, "picomic.registration-test")
        val engine = NetworkEngine(); val sessions = SessionCoordinator(store, engine)
        runBlocking { sessions.logout("picacg") }
        try {
            MockWebServer().use { server ->
                fun json(text: String, code: Int = 200) = MockResponse().setResponseCode(code).setBody(text)
                server.enqueue(json("""{"code":401,"error":"1005","message":"unauthorized"}""", 401))
                server.enqueue(json("""{"code":200,"message":"success"}"""))
                server.enqueue(json("""{"code":200,"message":"success","data":{"token":"fixture-token"}}"""))
                server.enqueue(json("""{"code":200,"message":"success","data":{"user":{"_id":"fixture-id","name":"注册联调账号"}}}"""))
                server.start()
                val controller = PicacgAccountController(sessions, engine, scope, {}) { PicacgClient(engine, server.url("/")) }
                var selected by mutableStateOf(controller)
                ui.setContent { PiComicTheme(false, false) { PicacgLoginScreen(selected, true, openRecovery = {}, openNetwork = {}) } }
                ui.waitUntil(5000) { controller.registration.value.ready }
                ui.onNodeWithText("登录并验证").assertIsNotEnabled()
                ui.onNodeWithTag("picacg-register").performScrollTo()
                screenshot("register-entry", ui.onRoot())
                ui.onNodeWithTag("picacg-register").performClick()
                ui.waitUntil(15000) { controller.registration.value.phase == RegistrationPhase.REGISTERED && !controller.state.value.busy }
                ui.onNodeWithTag("registration-result").assertIsDisplayed()
                ui.onNodeWithTag("login-success-dialog").assertDoesNotExist()
                assertEquals(AccountStatus.AUTHENTICATED, sessions.state.value["picacg"]?.status)
                assertEquals(4, server.requestCount)
                var username = ""; var password = ""; var expected = ""
                runBlocking { controller.withRegistration { username = it.username; password = String(it.password); expected = it.displayText() } }
                ui.onNodeWithTag("registration-details").assertTextContains("密码: ••••••••", substring = true)
                ui.onNodeWithText("显示密码").performScrollTo().performClick()
                ui.onNodeWithTag("registration-details").assertTextContains("密码: $password", substring = true)
                ui.onNodeWithText("隐藏密码").performClick()
                screenshot("registration-result", ui.onNodeWithTag("registration-result"))
                ui.onNodeWithText("一键复制").performClick()
                ui.onNodeWithText("已复制").assertExists()
                ui.runOnIdle {
                    val clip = (ui.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!
                    assertEquals(expected, clip.getItemAt(0).text.toString())
                    if (android.os.Build.VERSION.SDK_INT >= 33) assertTrue(clip.description.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
                }
                val ciphertext = File(context.noBackupFilesDir, "credentials/registration.picacg.enc").readBytes()
                assertFalse(ciphertext.toString(Charsets.ISO_8859_1).contains(username))
                assertFalse(ciphertext.toString(Charsets.ISO_8859_1).contains(password))
                ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                ui.onNodeWithTag("registration-result").assertDoesNotExist()
                val restartedSessions = SessionCoordinator(KeystoreSecretStore(context, "picomic.registration-test"), engine)
                val restarted = PicacgAccountController(restartedSessions, engine, scope, {}) { error("Form restoration needs no network") }
                ui.runOnIdle { selected = restarted }
                ui.waitUntil(5000) { ui.onAllNodesWithText(username).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithContentDescription("显示密码").performScrollTo().assertExists()
                ui.onNodeWithText("查看注册资料").performScrollTo().performClick()
                ui.onNodeWithTag("registration-result").assertIsDisplayed()
                ui.onNodeWithTag("registration-details").assertTextContains("用户名: $username", substring = true)
                ui.onNodeWithText("完成").performClick()
                ui.onNodeWithText("清除本地账号").performScrollTo().performClick()
                ui.waitUntil(5000) { !restarted.state.value.busy && restarted.registration.value.phase == null }
                assertNull(runBlocking { restartedSessions.registrationRecord("picacg") })
                assertNull(runBlocking { restartedSessions.rememberedLogin("picacg") })
            }
        } finally { runBlocking { sessions.logout("picacg") } }
    }

    private fun screenshot(name: String, node: SemanticsNodeInteraction) {
        val file = File(ui.activity.getExternalFilesDir(null), "registration/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
