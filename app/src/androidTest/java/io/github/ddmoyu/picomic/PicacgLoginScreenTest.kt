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
            server.enqueue(anonymous); server.enqueue(MockResponse().setResponseCode(400))
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
            ui.setContent { PiComicTheme(false, false) { PicacgLoginScreen(controller, true, openNetwork = {}) } }
            ui.onNodeWithText("登录并验证").assertIsNotEnabled()
            ui.onNodeWithText("账号 / 邮箱").performTextInput("fixture@example.test")
            ui.onNodeWithText("密码").performTextInput("fixture-password")
            ui.onNodeWithText("登录并验证").performScrollTo().performClick()
            ui.waitUntil(10000) { !controller.state.value.busy && controller.state.value.message != null }
            ui.onNodeWithText("登录未通过，请检查账号和密码").performScrollTo().assertExists()
            assertTrue(secrets.data.isEmpty())
            ui.onNodeWithText("密码").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            ui.onNodeWithText("密码").performTextInput("fixture-password")
            ui.onNodeWithText("登录并验证").performScrollTo().performClick()
            ui.waitUntil(10000) { sessions.state.value["picacg"]?.status == AccountStatus.AUTHENTICATED && !controller.state.value.busy }
            ui.onNodeWithText("已登录 · 联调测试账号").performScrollTo().assertIsDisplayed()
            assertNotNull(secrets.data["session.picacg"])
            assertEquals("fixture@example.test", controller.rememberedAccounts.value["picacg"])
            StoredAccountCodec.decode(secrets.data.getValue("session.picacg")).use { assertArrayEquals("fixture-password".toCharArray(), it.login!!.password) }
            ui.onNodeWithText("清除本地账号").performScrollTo().performClick()
            ui.waitUntil(10000) { sessions.state.value["picacg"]?.status == AccountStatus.ANONYMOUS && !controller.state.value.busy }
            assertTrue(secrets.data.isEmpty())
            assertEquals(5, server.requestCount)
        }
    }
    @Test fun leavingLoginDiscardsPasswordAndCancelsAttempt() {
        var show by androidx.compose.runtime.mutableStateOf(true)
        val secrets = object : SecretStore {
            override fun read(key: String): ByteArray? = null
            override fun write(key: String, value: ByteArray) { fail("Cancelled login must not save") }
            override fun remove(key: String) {}
        }
        val engine = NetworkEngine(); val sessions = SessionCoordinator(secrets, engine)
        val controller = PicacgAccountController(sessions, engine, scope, {}) { object : PicacgAuthApi {
            override suspend fun probe() { awaitCancellation() }
            override suspend fun signIn(email: String, password: CharArray): SessionCandidate = error("must not send credentials")
            override suspend fun profile(candidate: SessionCandidate): String = error("must not validate")
        } }
        ui.setContent { PiComicTheme(false, false) { if (show) PicacgLoginScreen(controller, true, openNetwork = {}) } }
        ui.onNodeWithText("账号 / 邮箱").performTextInput("fixture")
        ui.onNodeWithText("密码").performTextInput("fixture-password")
        ui.onNodeWithText("登录并验证").performScrollTo().performClick()
        ui.waitUntil { controller.state.value.busy }
        ui.runOnIdle { show = false }; ui.waitForIdle()
        assertFalse(controller.state.value.busy)
        assertEquals(AccountStatus.ANONYMOUS, sessions.state.value["picacg"]?.status)
        ui.runOnIdle { show = true }; ui.waitForIdle()
        ui.onNodeWithText("登录并验证").assertIsNotEnabled()
        val file = File(ui.activity.getExternalFilesDir(null), "screenshots/22-picacg-login.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
