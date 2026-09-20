package io.github.ddmoyu.picomic

import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.source.hitomi.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class HitomiContentTest {
    private fun client(server: MockWebServer) = HitomiClient(NetworkEngine(), server.url("/"))
    private fun MockWebServer.bytes(data: ByteArray, start: Long, total: Long) { enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes $start-${start + data.size - 1}/$total").setBody(Buffer().write(data))) }
    @Test fun rangeChecksHeadersAndSmallWholeResponseFallback() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val api = client(server)
            val data = ByteBuffer.allocate(12).putInt(1).putInt(2).putInt(3).array()
            server.enqueue(MockResponse().setBody(Buffer().write(data)))
            val result = api.range(listOf("index-all.nozomi"), 4, 4)
            assertArrayEquals(intArrayOf(2), HitomiProtocol.ids(result.bytes)); assertEquals(12, result.total)
            val request = server.takeRequest(); assertEquals("bytes=4-7", request.getHeader("Range")); assertEquals("https://hitomi.la/", request.getHeader("Referer")); assertNull(request.getHeader("Cookie"))
            server.bytes(data.copyOfRange(0, 4), 0, 12)
            assertTrue(runCatching { api.range(listOf("index-all.nozomi"), 4, 4) }.exceptionOrNull() is ContentFailure)
        }
    }
    @Test fun treeSearchReadsMatchingDataWithCapturedVersionAndCachesResult() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val api = client(server); val key = HitomiProtocol.key("fixture")
            server.enqueue(MockResponse().setBody("12345"))
            val node = ByteBuffer.allocate(464).putInt(1).putInt(4).put(key).putInt(1).putLong(16).putInt(12)
            repeat(17) { node.putLong(0) }; server.bytes(node.array(), 0, 464)
            server.bytes(ByteBuffer.allocate(12).putInt(2).putInt(42).putInt(7).array(), 16, 28)
            assertArrayEquals(intArrayOf(42, 7), api.searchIds("fixture", "all"))
            assertArrayEquals(intArrayOf(42, 7), api.searchIds("fixture", "all")); assertEquals(3, server.requestCount)
            server.takeRequest(); assertEquals("/galleriesindex/galleries.12345.index", server.takeRequest().path)
            assertEquals("bytes=16-27", server.takeRequest().getHeader("Range"))
        }
    }
    @Test fun cyclicIndexCannotRunForever() = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setBody("1"))
            val node = ByteBuffer.allocate(464).putInt(0).putInt(0); repeat(17) { node.putLong(if (it == 0) 464 else 0) }
            server.bytes(node.array(), 0, 928); server.bytes(node.array(), 464, 928)
            assertTrue(runCatching { client(server).searchIds("fixture", "all") }.exceptionOrNull() is ContentFailure)
            assertEquals(3, server.requestCount)
        }
    }
    @Test fun largeServerIgnoringRangeStopsBeforeDownloadingIndex() = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setHeader("Content-Length", "1900000000"))
            assertEquals(ContentFailureKind.NETWORK, (runCatching { client(server).range(listOf("index"), 0, 464) }.exceptionOrNull() as ContentFailure).kind)
        }
    }
    @Test fun galleryAssignmentCannotExecuteAdditionalScriptAndKeepsDimensions() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val hash = "a".repeat(64)
            val json = """{"id":"42","title":"结构夹具","language":"chinese","files":[{"hash":"$hash","name":"one.png","width":640,"height":930}]}"""
            server.enqueue(MockResponse().setBody("var galleryinfo = $json;"))
            server.enqueue(MockResponse().setBody("""gg = { m: function(g) { var o = 1; switch (g) { case 1: o = 0; break; } return o; }, s: function(h) { var m = /(..)(.)$/.exec(h); return parseInt(m[2]+m[1], 16).toString(10); }, b: '12345/' };"""))
            val source = HitomiSource(client(server)); val detail = source.details("42"); val pages = source.pages("42", detail.chapters.single())
            assertEquals("1:$hash", pages.single().id); assertEquals(640, pages.single().width); assertEquals("hitomi", pages.single().resolver)
            server.enqueue(MockResponse().setBody("var galleryinfo = $json; evil();"))
            assertTrue(runCatching { client(server).gallery("42") }.exceptionOrNull() is ContentFailure)
        }
    }
}
