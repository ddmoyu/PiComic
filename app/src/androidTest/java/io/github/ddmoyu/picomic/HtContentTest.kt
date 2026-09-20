package io.github.ddmoyu.picomic

import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.network.*
import io.github.ddmoyu.picomic.source.ht.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class HtContentTest {
    private fun client(server: MockWebServer) = HtClient(NetworkEngine(), server.url("/"), setOf(server.url("/").origin()))
    private val card = """<li class="gallary_item"><div class="pic_box"><img data-src="//t4.wnimg2.cfd/fixture.webp"></div><div class="title"><a href="/photos-index-aid-42.html">结构夹具</a></div><div class="info_col">2張圖片</div></li>"""
    @Test fun anonymousSearchPaginationAndEmptyResultsAreStructural() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("<div class=gallary_wrap><ul>$card</ul></div><div class=paginator><a href='?p=2'>2</a></div>")); server.start()
            val source = HtSource(client(server)); val page = source.search(ContentQuery("中文 & tag"))
            assertEquals(2, page.nextPage); assertEquals("42", page.items.single().key.id)
            assertEquals(2, page.items.single().pageCount)
            assertEquals("中文 & tag", server.takeRequest().requestUrl!!.queryParameter("q"))
            server.enqueue(MockResponse().setBody("<div class=gallary_wrap></div><p class=result>0</p>"))
            assertTrue(source.search(ContentQuery("none")).items.isEmpty())
            server.enqueue(MockResponse().setBody("<html>server changed</html>"))
            assertEquals(ContentFailureKind.PARSE, (runCatching { source.search(ContentQuery()) }.exceptionOrNull() as ContentFailure).kind)
        }
    }
    @Test fun scriptParserOnlyUsesKnownArrayAndUpgradesTrustedImages() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""advertUrl="https://evil.test/advert.jpg"; mReader.initData({"note":"} [ \\\"", "page_url":["http://img5.wnimg2.cfd/one.webp", "//img5.wnimg2.cfd/two.png",]});""")); server.start()
            val pages = HtSource(client(server)).pages("42", Chapter("whole", "全册", 1, 2))
            assertEquals(listOf("1", "2"), pages.map { it.id }); assertEquals("ht", pages.first().resolver)
            assertEquals("https://img5.wnimg2.cfd/one.webp", pages.first().url)
        }
    }
    @Test fun pageMismatchOrUntrustedHostNeverProducesPartialChapter() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val source = HtSource(client(server))
            for (payload in listOf("mReader.initData({\"page_url\":[\"https://img5.wnimg2.cfd.evil.test/x.jpg\"]});", "mReader.initData({\"page_url\":[\"https://img5.wnimg2.cfd/x.jpg\"]});")) {
                server.enqueue(MockResponse().setBody(payload))
                assertEquals(ContentFailureKind.PARSE, (runCatching { source.pages("42", Chapter("whole", "全册", 1, 2)) }.exceptionOrNull() as ContentFailure).kind)
            }
        }
    }
    @Test fun loginRequiresProtectedPageAndCookiesRemainOnOriginalOrigin() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Set-Cookie", "sid=fixture; Path=/; HttpOnly").setBody("{\"ret\":true}"))
            server.enqueue(MockResponse().setBody("<div class=fav_nav></div><a href=users-logout.html>退出</a>")); server.start()
            val api = client(server); val candidate = api.signIn("fixture", "test-only".toCharArray()); assertEquals("fixture", api.validate(candidate).accountId)
            assertNull(server.takeRequest().getHeader("Cookie")); assertEquals("sid=fixture", server.takeRequest().getHeader("Cookie"))
            MockWebServer().use { other -> other.start(); assertTrue(runCatching { client(other).install(candidate) }.exceptionOrNull() is ContentFailure); assertEquals(0, other.requestCount) }
            server.enqueue(MockResponse().setBody("<form id=login_form></form>"))
            assertEquals(ContentFailureKind.EXPIRED, (runCatching { api.validate(candidate) }.exceptionOrNull() as ContentFailure).kind)
            candidate.value.fill(0)
        }
    }
    @Test fun redirectsAndLoginErrorsNeverReplayPasswords() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://evil.test/")); server.start()
            assertTrue(runCatching { client(server).signIn("fixture", "fake".toCharArray()) }.exceptionOrNull() is ContentFailure)
            assertEquals(1, server.requestCount)
        }
    }
}
