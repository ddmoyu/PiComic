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
    @Test fun registrationPostsCompleteSignedProfileWithoutAuthentication() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("""{"code":200,"message":"success"}""")); server.start()
            val api = PicacgClient(NetworkEngine(), server.url("/"))
            PicacgRegistration.generate().use { record ->
                api.register(record)
                val request = server.takeRequest()
                assertEquals("POST", request.method); assertEquals("/auth/register", request.path)
                assertNull(request.getHeader("authorization")); assertNull(request.getHeader("Cookie"))
                assertEquals(PicacgProtocol.signature(server.url("/auth/register"), "POST", request.getHeader("time")!!,
                    request.getHeader("nonce")!!), request.getHeader("signature"))
                val body = JSONObject(request.body.readUtf8())
                assertEquals(11, body.length())
                assertEquals(record.username, body.getString("email")); assertEquals(String(record.password), body.getString("password"))
                assertEquals(record.nickname, body.getString("name")); assertEquals(record.birthday, body.getString("birthday"))
                assertEquals("bot", body.getString("gender"))
                repeat(3) { i -> assertEquals(record.questions[i], body.getString("question${i + 1}")); assertEquals(record.answers[i], body.getString("answer${i + 1}")) }
            }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun businessThrottlingIsNotMisreportedAsWrongPasswordOrReplayed() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val api = PicacgClient(NetworkEngine(), server.url("/"))
            for (http in listOf(200, 400)) {
                repeat(2) { server.enqueue(json("""{"code":400,"error":"1023","message":"too many requests"}""", http).addHeader("Retry-After", "60")) }
                val login = runCatching { api.signIn("fixture", "fixture-password".toCharArray()) }.exceptionOrNull() as PicacgFailure
                assertEquals(PicacgFailureKind.RATE_LIMITED, login.kind); assertEquals(60L, login.retryAfterSeconds)
                PicacgRegistration.generate().use { record ->
                    val error = runCatching { api.register(record) }.exceptionOrNull() as PicacgFailure
                    assertEquals(PicacgFailureKind.RATE_LIMITED, error.kind)
                }
            }
            assertEquals(4, server.requestCount)
        }
    }

    @Test fun registrationErrorsRemainDistinctAndNeverEchoServerDetails() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val api = PicacgClient(NetworkEngine(), server.url("/"))
            mapOf("1002" to PicacgFailureKind.UNDERAGE, "1008" to PicacgFailureKind.DUPLICATE,
                "1009" to PicacgFailureKind.DUPLICATE, "unknown" to PicacgFailureKind.REGISTRATION).forEach { (code, kind) ->
                server.enqueue(json("""{"code":400,"error":"$code","message":"fixture-secret"}""", 400))
                PicacgRegistration.generate().use { record ->
                    val error = runCatching { api.register(record) }.exceptionOrNull() as PicacgFailure
                    assertEquals(kind, error.kind); assertFalse(error.message!!.contains("fixture-secret"))
                }
            }
            assertEquals(4, server.requestCount)
        }
    }

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
            server.enqueue(MockResponse().setResponseCode(400))
            assertEquals(PicacgFailureKind.RESPONSE, (runCatching { api.signIn("fixture", charArrayOf('x')) }.exceptionOrNull() as PicacgFailure).kind)
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
