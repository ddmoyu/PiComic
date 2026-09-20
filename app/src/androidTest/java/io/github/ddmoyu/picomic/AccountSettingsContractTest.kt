package io.github.ddmoyu.picomic

import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.data.*
import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.source.eh.EhConfiguration
import io.github.ddmoyu.picomic.source.picacg.PicacgClient
import io.github.ddmoyu.picomic.source.jm.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class AccountSettingsContractTest {
    @Test fun picaRequiresProfileConfirmationAndDoesNotPostWhenAlreadyDone() = runBlocking {
        MockWebServer().use { server ->
            fun profile(value: Boolean) = MockResponse().addHeader("Content-Type", "application/json").setBody("""{"code":200,"message":"success","data":{"user":{"_id":"one","isPunched":$value}}}""")
            server.enqueue(profile(true)); server.enqueue(profile(false))
            server.enqueue(MockResponse().addHeader("Content-Type", "application/json").setBody("""{"code":200,"message":"success","data":{}}""")); server.enqueue(profile(true))
            server.enqueue(profile(false)); server.enqueue(MockResponse().addHeader("Content-Type", "application/json").setBody("""{"code":200,"message":"success","data":{}}""")); server.enqueue(profile(false)); server.start()
            val api = PicacgClient(NetworkEngine(), server.url("/"))
            val token = SessionCandidate(CredentialKind.USER_TOKEN, "fixture".toByteArray(), accountId = "one")
            assertEquals(CheckInOutcome.ALREADY, api.checkIn(token)); assertEquals(1, server.requestCount)
            assertEquals(CheckInOutcome.DONE, api.checkIn(token)); assertEquals(4, server.requestCount)
            assertTrue(runCatching { api.checkIn(token) }.isFailure)
            val requests = List(7) { server.takeRequest() }; assertEquals("POST", requests[2].method)
            assertEquals("/users/punch-in", requests[2].path); assertEquals("fixture", requests[2].getHeader("authorization"))
        }
    }
    @Test fun jmSendsDailyIdentityAndRejectsUnrecognizedSuccessText() = runBlocking {
        MockWebServer().use { server ->
            val time = 1700000000L
            fun encrypted(value: String): MockResponse {
                val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(JmProtocol.token(time).toByteArray(), "AES"))
                return MockResponse().setBody(JSONObject().put("code", 200).put("data", Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray()))).toString())
            }
            server.enqueue(encrypted("""{"uid":42,"username":"测试"}""").addHeader("Set-Cookie", "AVS=fixture; Path=/; HttpOnly"))
            server.enqueue(encrypted("""{"daily_id":"17"}""")); server.enqueue(encrypted("""{"msg":"簽到成功！"}"""))
            server.enqueue(encrypted("""{"daily_id":"17"}""")); server.enqueue(encrypted("""{"msg":"some text"}""")); server.start()
            val api = JmClient(NetworkEngine(), server.url("/")) { time }
            val candidate = api.signIn("fixture", charArrayOf('x')); api.install(candidate)
            assertEquals(CheckInOutcome.DONE, api.checkIn(candidate)); assertTrue(runCatching { api.checkIn(candidate) }.isFailure)
            server.takeRequest(); val daily = server.takeRequest(); val check = server.takeRequest()
            assertEquals("42", daily.requestUrl!!.queryParameter("user_id")); assertEquals("/daily_chk", check.path)
            assertEquals("user_id=42&daily_id=17", check.body.readUtf8()); assertEquals("AVS=fixture", check.getHeader("Cookie"))
        }
    }
    @Test fun importedEhSettingsCannotCarryCredentialsAndLogsCannotEchoUnknownFields() {
        val settings = """{"schemaVersion":1,"site":"exhentai.org","original":true,"ignoreWarning":false,"subtitle":true}"""
        assertEquals("exhentai.org", EhConfiguration.decode(settings.toByteArray()).site)
        listOf(settings.replace("exhentai.org", "evil.test"), settings.dropLast(1) + ",\"cookie\":\"secret\"}", settings.replace(":true", ":\"true\""), settings + "junk").forEach {
            assertTrue(runCatching { EhConfiguration.decode(it.toByteArray()) }.isFailure)
        }
        val logs = EventLog.parse("""[{"time":1,"code":"APP_START","source":null,"message":"fixture-secret"},{"time":2,"code":"fixture-secret","source":null}]""".toByteArray())
        assertEquals(1, logs.size); assertFalse(EventLog.render(logs).contains("fixture-secret"))
    }
}
