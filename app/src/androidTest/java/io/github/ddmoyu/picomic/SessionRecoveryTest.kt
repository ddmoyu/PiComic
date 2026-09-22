package io.github.ddmoyu.picomic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.network.NetworkRepository
import io.github.ddmoyu.picomic.network.origin
import io.github.ddmoyu.picomic.source.jm.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Exercises the actual JM protocol, encrypted store and content request replay without external content. */
class SessionRecoveryTest {
    private class Fixture : AutoCloseable {
        val network = NetworkRepository.get(ApplicationProvider.getApplicationContext<Context>())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = MockWebServer()
        val loginPosts = AtomicInteger()
        val validations = AtomicInteger()
        val searches = AtomicInteger()
        @Volatile var acceptStored = true
        @Volatile var rejectPassword = false
        @Volatile var rejectReplay = false
        @Volatile var unavailable = false
        @Volatile var allowAnonymous = false
        private val time = 1_700_000_000L
        private val profile = """{"uid":"42","username":"安全测试账号"}"""
        init {
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val cookie = request.getHeader("Cookie")
                    val accepted = cookie == "AVS=renewed" || acceptStored && cookie == "AVS=stored"
                    return when (request.requestUrl!!.encodedPath) {
                        "/setting" -> encrypted("""{"version":"1.8.2","img_host":"https://cdn-msp2.jmdanjonproxy.vip"}""")
                        "/login" -> if (request.method == "POST") {
                            loginPosts.incrementAndGet()
                            val body = request.body.readUtf8()
                            if (rejectPassword || body != "username=fixture-user&password=fixture-password") MockResponse().setResponseCode(401)
                            else encrypted(profile).addHeader("Set-Cookie", "AVS=renewed; Path=/; HttpOnly")
                        } else {
                            validations.incrementAndGet()
                            if (unavailable) MockResponse().setResponseCode(503)
                            else if (accepted) encrypted(profile) else MockResponse().setResponseCode(401)
                        }
                        "/search", "/categories/filter" -> {
                            searches.incrementAndGet()
                            if (rejectReplay || !accepted && !allowAnonymous) MockResponse().setResponseCode(401)
                            else encrypted("""{"content":[{"id":"500001","name":"会话恢复测试作品","author":["测试作者"],"tags":["安全测试"]}],"total":1}""")
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            server.start()
        }
        fun client() = JmClient(network.engine, server.url("/")) { time }
        val recovery = PasswordSessionRecovery("jmcomic", network.sessions, network.engine, scope, network::awaitReady) { client() }
        val repository = ContentRepository(network, jmClient = { client() }, passwordSessions = mapOf("jmcomic" to recovery))
        suspend fun seed(needsValidation: Boolean = false, origin: String = server.url("/").origin()) {
            network.awaitReady(); network.sessions.logout("jmcomic")
            val bytes = JSONObject().put("origin", origin).put("profile", JmProtocol.ID).put("uid", "42").put("name", "安全测试账号")
                .put("cookies", JSONArray(listOf("AVS=stored; Path=/; HttpOnly"))).toString().toByteArray()
            RememberedLogin("fixture-user", "fixture-password".toCharArray()).use { login ->
                network.sessions.validateAndCommit(network.sessions.begin("jmcomic"), SessionCandidate(CredentialKind.COOKIE, bytes), PasswordRetention.Remember(login)) {
                    ValidationResult.Verified("安全测试账号", "42")
                }
            }
            if (needsValidation) network.sessions.storedCandidate("jmcomic")!!.value.fill(0)
        }
        suspend fun load() = repository.run(Source.JMCOMIC) { adapter, _ -> adapter.search(ContentQuery("安全测试")) }
        private fun encrypted(data: String): MockResponse {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(JmProtocol.token(time).toByteArray(), "AES"))
            return MockResponse().setBody(JSONObject().put("code", 200).put("data", Base64.getEncoder().encodeToString(cipher.doFinal(data.toByteArray()))).toString())
        }
        override fun close() {
            recovery.cancel(); scope.cancel()
            runBlocking { network.sessions.logout("jmcomic") }
            server.close()
        }
    }

    @Test fun savedCookieValidatesAndLoadsWithoutPasswordPost() = runBlocking {
        Fixture().use { f ->
            f.seed(needsValidation = true)
            assertEquals("会话恢复测试作品", f.load().items.single().title)
            assertEquals(0, f.loginPosts.get()); assertEquals(1, f.validations.get())
        }
    }

    @Test fun expiredCookieAtStartupRestoresPasswordThenLoads() = runBlocking {
        Fixture().use { f ->
            f.seed(needsValidation = true); f.acceptStored = false
            assertEquals(1, f.load().items.size)
            assertEquals(1, f.loginPosts.get()); assertEquals(2, f.validations.get())
            f.network.sessions.lease("jmcomic").use {
                assertTrue(it.candidate.value.toString(Charsets.UTF_8).contains("AVS=renewed"))
            }
        }
    }

    @Test fun requestExpiryAutomaticallyReplaysReadOnce() = runBlocking {
        Fixture().use { f ->
            f.seed(); f.acceptStored = false
            assertEquals(1, f.load().items.size)
            assertEquals(1, f.loginPosts.get()); assertEquals(2, f.searches.get())
        }
    }

    @Test fun parallelExpiredReadsShareLoginAndComplete() = runBlocking {
        Fixture().use { f ->
            f.seed(); f.acceptStored = false
            val pages = List(8) { async { f.load() } }.awaitAll()
            assertTrue(pages.all { it.items.size == 1 }); assertEquals(1, f.loginPosts.get())
        }
    }

    @Test fun rejectedPasswordDoesNotCreateLoginLoop() = runBlocking {
        Fixture().use { f ->
            f.seed(); f.acceptStored = false; f.rejectPassword = true
            repeat(4) { assertEquals(ContentFailureKind.LOGIN, (runCatching { f.load() }.exceptionOrNull() as ContentFailure).kind) }
            assertEquals(1, f.loginPosts.get())
            f.network.sessions.rememberedLogin("jmcomic")!!.use { assertEquals("fixture-user", it.username) }
        }
    }

    @Test fun replayExpiryStopsAfterOneLogin() = runBlocking {
        Fixture().use { f ->
            f.seed(); f.rejectReplay = true
            repeat(3) { assertEquals(ContentFailureKind.EXPIRED, (runCatching { f.load() }.exceptionOrNull() as ContentFailure).kind) }
            assertEquals(1, f.loginPosts.get()); assertEquals(2, f.searches.get())
        }
    }

    @Test fun temporaryServerFailureKeepsCookieAndRetriesWithoutPassword() = runBlocking {
        Fixture().use { f ->
            f.seed(needsValidation = true); f.unavailable = true
            assertEquals(ContentFailureKind.NETWORK, (runCatching { f.load() }.exceptionOrNull() as ContentFailure).kind)
            assertEquals(0, f.loginPosts.get())
            f.unavailable = false
            assertEquals(1, f.load().items.size); assertEquals(0, f.loginPosts.get())
        }
    }

    @Test fun selectedRouteUsesFreshLoginWithoutForwardingOldCookie() = runBlocking {
        Fixture().use { f ->
            f.seed(origin = "https://previous.example:443")
            assertEquals(1, f.load().items.size); assertEquals(1, f.loginPosts.get())
            repeat(f.server.requestCount) {
                val request = f.server.takeRequest()
                assertNotEquals("AVS=stored", request.getHeader("Cookie"))
            }
        }
    }

    @Test fun anonymousJmRemainsAvailableWhenNothingWasSaved() = runBlocking {
        Fixture().use { f ->
            f.network.awaitReady(); f.network.sessions.logout("jmcomic"); f.allowAnonymous = true
            assertEquals(1, f.load().items.size); assertEquals(0, f.loginPosts.get())
        }
    }
}
