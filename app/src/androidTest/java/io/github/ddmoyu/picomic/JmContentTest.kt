package io.github.ddmoyu.picomic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.source.jm.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class JmContentTest {
    @Test fun publisherFeedDecryptsButRejectsUntrustedHostShapes() {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(JmProtocol.md5("diosfjckwpqpdfjkvnqQjsik").toByteArray(), "AES"))
        val bytes = Base64.getEncoder().encode(cipher.doFinal("""{"Server":["www.cdngwc.club","www.cdngwc.club","http://127.0.0.1/","evil.test","www.cdnnew.cc"]}""".toByteArray()))
        assertEquals(listOf("www.cdngwc.club", "www.cdnnew.cc"), JmDomainFeed.decode(bytes))
        assertTrue(runCatching { JmDomainFeed.decode("invalid".toByteArray()) }.isFailure)
        listOf("127.0.0.1", "www.cdnsite.cc.evil.test", "www.cdnsite.cc:443", "www.cdnsite.cc/path").forEach { assertFalse(JmDomainFeed.validHost(it)) }
    }
    private val time = 1700000000L
    private val setting = """{"version":"1.8.2","img_host":"https://cdn-msp.jmapiproxy3.cc"}"""
    private val book = """{"id":"500001","name":"契约作品","author":["作者"],"tags":["测试"],"series":[],"description":"简介"}"""
    private fun encrypted(data: String, timestamp: Long = time): MockResponse {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(JmProtocol.token(timestamp).toByteArray(), "AES"))
        return MockResponse().setBody(JSONObject().put("code", 200).put("data", Base64.getEncoder().encodeToString(cipher.doFinal(data.toByteArray()))).toString())
    }
    private fun client(server: MockWebServer) = JmClient(NetworkEngine(), server.url("/")) { time }
    @Test fun anonymousSearchUsesOneProfileAndEncodesQueryWithoutSession() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(encrypted("""{"content":[$book],"total":81}""")); server.enqueue(encrypted(setting)); server.start()
            val result = JmSource(client(server)).search(ContentQuery("中文 & 测试"))
            assertEquals(2, result.nextPage); assertEquals("500001", result.items.single().key.id)
            val request = server.takeRequest()
            assertEquals("中文 & 测试", request.requestUrl!!.queryParameter("search_query"))
            assertEquals("$time,1.8.2", request.getHeader("tokenparam")); assertEquals(JmProtocol.token(time), request.getHeader("token"))
            assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("Authorization"))
        }
    }
    @Test fun eachConcurrentResponseUsesItsOwnRequestTime() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() { override fun dispatch(request: RecordedRequest): MockResponse {
                val timestamp = request.getHeader("tokenparam")!!.substringBefore(',').toLong()
                return encrypted(setting, timestamp).setBodyDelay(if (timestamp == time) 100 else 0, java.util.concurrent.TimeUnit.MILLISECONDS)
            } }; server.start()
            val sequence = java.util.concurrent.atomic.AtomicLong(time)
            val api = JmClient(NetworkEngine(), server.url("/")) { sequence.getAndIncrement() }
            coroutineScope { listOf(async { api.probe() }, async { api.probe() }).awaitAll() }
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun wholeAlbumAndChapterMetadataUseRealPhotoIds() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(encrypted(book)); server.enqueue(encrypted(setting))
            server.enqueue(encrypted("""{"id":"500001","images":["00001.jpg","00002.GIF"]}"""))
            server.enqueue(MockResponse().setBody("<script>var aid = 500001; var scramble_id = 220980; var speed = '0';</script>")); server.start()
            val source = JmSource(client(server)); val detail = source.details("500001")
            assertEquals("whole", detail.chapters.single().id)
            val pages = source.pages("500001", detail.chapters.single())
            assertEquals(listOf("00001.jpg", "00002.GIF"), pages.map { it.id })
            assertEquals(500001L, pages.first().jm!!.photoId); assertEquals(220980L, pages.first().jm!!.scrambleId)
            assertEquals("https://cdn-msp.jmapiproxy3.cc/media/photos/500001/00001.jpg", pages.first().url)
        }
    }
    @Test fun missingOrWrongScrambleMetadataCannotPublishPages() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(encrypted("""{"id":"500001","images":["00001.jpg"]}"""))
            server.enqueue(MockResponse().setBody("<script>var aid = 600001; var scramble_id = 220980;</script>")); server.start()
            assertTrue(runCatching { JmSource(client(server)).pages("500001", Chapter("whole", "全册", 1)) }.exceptionOrNull() is ContentFailure)
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun cookieCandidateMustPassIndependentProfileAndCannotCrossOrigins() = runBlocking {
        MockWebServer().use { server ->
            val profile = """{"uid":42,"username":"夹具账号"}"""
            server.enqueue(encrypted(profile).addHeader("Set-Cookie", "AVS=fixture-session; Path=/; HttpOnly"))
            server.enqueue(encrypted(profile)); server.start()
            val api = client(server); val candidate = api.signIn("fixture & name", charArrayOf('p', '&'))
            assertEquals("夹具账号", api.profile(candidate))
            val login = server.takeRequest(); assertEquals("POST", login.method)
            val form = login.body.readUtf8().split('&').associate { part -> part.substringBefore('=') to java.net.URLDecoder.decode(part.substringAfter('='), "UTF-8") }
            assertEquals("fixture & name", form["username"]); assertEquals("p&", form["password"])
            val validation = server.takeRequest(); assertEquals("GET", validation.method); assertEquals("/login", validation.path); assertEquals("AVS=fixture-session", validation.getHeader("Cookie"))
            MockWebServer().use { other -> other.start(); assertTrue(runCatching { client(other).install(candidate) }.exceptionOrNull() is ContentFailure); assertEquals(0, other.requestCount) }
            candidate.value.fill(0)
        }
    }
    @Test fun redirectsAndRateLimitsDoNotReplayRequests() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://example.test/")); server.enqueue(MockResponse().setResponseCode(429)); server.start()
            assertEquals(ContentFailureKind.NETWORK, (runCatching { client(server).probe() }.exceptionOrNull() as ContentFailure).kind)
            assertEquals(ContentFailureKind.LIMIT, (runCatching { client(server).probe() }.exceptionOrNull() as ContentFailure).kind)
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun originalResolutionReconstructionHasNoLostOrDuplicatedRows() = runBlocking {
        val bitmap = Bitmap.createBitmap(5, 13, Bitmap.Config.ARGB_8888)
        repeat(13) { row -> repeat(5) { x -> bitmap.setPixel(x, row, Color.rgb(row * 15, 255 - row * 15, x * 40)) } }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val target = File.createTempFile("jm-fixture-", ".png", context.cacheDir)
        try {
            JmImageFetcher.reconstruct(bytes, 3, target)
            val output = BitmapFactory.decodeFile(target.path)
            try {
                val rows = listOf(8, 9, 10, 11, 12, 4, 5, 6, 7, 0, 1, 2, 3)
                repeat(13) { y -> repeat(5) { x -> assertEquals(bitmap.getPixel(x, rows[y]), output.getPixel(x, y)) } }
            } finally { output.recycle() }
        } finally { bitmap.recycle(); target.delete() }
    }
}
