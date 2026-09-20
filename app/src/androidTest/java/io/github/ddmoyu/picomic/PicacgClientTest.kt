package io.github.ddmoyu.picomic

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ddmoyu.picomic.auth.CredentialKind
import io.github.ddmoyu.picomic.auth.SessionCandidate
import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.network.NetworkProfile
import io.github.ddmoyu.picomic.source.picacg.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PicacgClientTest {
    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).addHeader("Content-Type", "application/json").setBody(body)
    private val anonymous = """{"code":401,"error":"1005","message":"unauthorized"}"""
    private val session = """{"code":200,"message":"success","data":{"token":"fixture-token"}}"""
    private fun candidate() = SessionCandidate(CredentialKind.USER_TOKEN, "fixture-token".toByteArray())

    @Test fun nativeContractUsesScopedHeadersAndValidatesUserProfile() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json(anonymous, 401)); server.enqueue(json(session))
            server.enqueue(json("""{"code":200,"message":"success","data":{"user":{"_id":"fixture-id","name":"测试账号"}}}"""))
            server.start()
            val api = PicacgClient(NetworkEngine(), server.url("/"))
            api.probe()
            val secret = api.signIn("fixture@example.test", "引号\"和\\斜线".toCharArray())
            assertEquals("测试账号", api.profile(secret))
            val probe = server.takeRequest(); val login = server.takeRequest(); val profile = server.takeRequest()
            assertEquals("/users/profile", probe.path); assertNull(probe.getHeader("authorization"))
            assertEquals("POST", login.method); assertEquals("/auth/sign-in", login.path); assertNull(login.getHeader("authorization"))
            assertEquals("引号\"和\\斜线", JSONObject(login.body.readUtf8()).getString("password"))
            assertEquals("fixture-token", profile.getHeader("authorization")); assertNull(profile.getHeader("Cookie"))
            assertEquals(PicacgProtocol.signature(server.url("/auth/sign-in"), "POST", login.getHeader("time")!!, login.getHeader("nonce")!!), login.getHeader("signature"))
            assertNotEquals(login.getHeader("nonce"), profile.getHeader("nonce")); secret.value.fill(0)
        }
    }
    @Test fun failedLoginsAreNeverReplayedAndMessagesNeverEchoResponseSecrets() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val api = PicacgClient(NetworkEngine(), server.url("/"))
            val expected = mapOf(400 to PicacgFailureKind.CREDENTIALS, 401 to PicacgFailureKind.CREDENTIALS,
                403 to PicacgFailureKind.ACCESS_DENIED, 429 to PicacgFailureKind.RATE_LIMITED, 503 to PicacgFailureKind.SERVER)
            expected.forEach { (code, kind) ->
                server.enqueue(json("""{"message":"must-not-echo-fixture-password"}""", code).addHeader("Retry-After", "30"))
                val error = runCatching { api.signIn("fixture", charArrayOf('x')) }.exceptionOrNull() as PicacgFailure
                assertEquals(kind, error.kind); assertFalse(error.message!!.contains("must-not-echo"))
                if (code == 429) assertEquals(30L, error.retryAfterSeconds)
            }
            assertEquals(expected.size, server.requestCount)
        }
    }
    @Test fun anonymousProbeRequiresRecognizedUnauthenticatedEnvelope() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val api = PicacgClient(NetworkEngine(), server.url("/"))
            listOf(json("<html>challenge</html>"), json(anonymous), json("{}", 401), json("""{"code":401,"error":"unknown","message":"unauthorized"}""", 401)).forEach {
                server.enqueue(it)
                assertEquals(PicacgFailureKind.RESPONSE, (runCatching { api.probe() }.exceptionOrNull() as PicacgFailure).kind)
            }
        }
    }
    @Test fun malformedTokenOrProfileCannotBeAccepted() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val api = PicacgClient(NetworkEngine(), server.url("/"))
            listOf("null", "12345", "\"\"", "\"bad\\ntoken\"").forEach { token ->
                server.enqueue(json("""{"code":200,"message":"success","data":{"token":$token}}"""))
                assertTrue(runCatching { api.signIn("fixture", charArrayOf('x')) }.exceptionOrNull() is PicacgFailure)
            }
            server.enqueue(json("""{"code":200,"message":"success","data":{"user":{"name":"no-id"}}}"""))
            assertTrue(runCatching { api.profile(candidate()) }.exceptionOrNull() is PicacgFailure)
            server.enqueue(json("""{"code":401,"message":"unauthorized"}"""))
            assertEquals(PicacgFailureKind.EXPIRED, (runCatching { api.profile(candidate()) }.exceptionOrNull() as PicacgFailure).kind)
        }
    }
    @Test fun redirectsNeverForwardPasswordOrToken() = runBlocking {
        MockWebServer().use { server -> MockWebServer().use { other ->
            server.start(); other.start()
            val api = PicacgClient(NetworkEngine(), server.url("/"))
            repeat(2) { server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", other.url("/capture"))) }
            assertEquals(PicacgFailureKind.REDIRECT, (runCatching { api.signIn("fixture", charArrayOf('x')) }.exceptionOrNull() as PicacgFailure).kind)
            assertEquals(PicacgFailureKind.REDIRECT, (runCatching { api.profile(candidate()) }.exceptionOrNull() as PicacgFailure).kind)
            assertEquals(0, other.requestCount)
        } }
    }
    @Test fun cancellationAlsoInterruptsSlowResponseBody() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json(session).setBodyDelay(2, TimeUnit.SECONDS)); server.start()
            val api = PicacgClient(NetworkEngine(), server.url("/"))
            val pending = launch(Dispatchers.IO) { api.signIn("fixture", charArrayOf('x')) }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            withTimeout(1000) { pending.cancelAndJoin() }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun oldSourceClientIsUnusableAfterNetworkChange() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val engine = NetworkEngine(); val api = PicacgClient(engine, server.url("/"))
            engine.change(NetworkProfile.FollowSystem)
            assertTrue(runCatching { api.signIn("fixture", charArrayOf('x')) }.isFailure)
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun oversizedResponseIsRejected() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json(" ".repeat(256 * 1024 + 1))); server.start()
            val api = PicacgClient(NetworkEngine(), server.url("/"))
            assertEquals(PicacgFailureKind.RESPONSE, (runCatching { api.signIn("fixture", charArrayOf('x')) }.exceptionOrNull() as PicacgFailure).kind)
        }
    }
}
