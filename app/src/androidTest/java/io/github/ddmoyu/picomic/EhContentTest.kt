package io.github.ddmoyu.picomic

import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.network.*
import io.github.ddmoyu.picomic.source.eh.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class EhContentTest {
    @Test fun listPageCountsComeOnlyFromNativeMetadataWithoutDetailRequests() = runBlocking {
        MockWebServer().use { eh -> MockWebServer().use { ex ->
            eh.start(); ex.start()
            val source = EhSource(client(eh, ex))
            val title = "<a href='/g/42/abcdef0123/'><div class=glink>999 pages</div></a>"
            val variants = listOf(
                "<table class=itg><tr><td>$title</td><td class='gl4c glhide'><div><a>上传者</a></div><div>42 pages</div></td></tr></table>" to 42,
                "<table class=itg><tr><td>$title<div class=gl3e><div>1,234 pages</div></div></td></tr></table>" to 1234,
                "<div class=itg><div class=gl1t>$title<div class=gl5t><div><div>1 page</div></div></div></div></div>" to 1,
                "<table class=itg><tr><td>$title<div class=glthumb><div><div>256 pages</div></div></div></td></tr></table>" to 256,
                "<table class=itg><tr><td>$title<div class=gt>88 pages</div></td></tr></table>" to null,
                "<table class=itg><tr><td>$title</td><td class=glhide><div>0 pages</div><div>unknown pages</div><div>999999999999 pages</div><div>1,23 pages</div></td></tr></table>" to null)
            variants.forEach { (html, expected) ->
                eh.enqueue(MockResponse().setBody(html))
                assertEquals(expected, source.search(ContentQuery()).items.single().pageCount)
            }
            assertEquals(variants.size, eh.requestCount); assertEquals(0, ex.requestCount)
        } }
    }
    private val candidate get() = SessionCandidate(CredentialKind.COOKIE, "ipb_member_id=42; ipb_pass_hash=fixture; igneous=fixture-ex".toByteArray())
    private fun client(eh: MockWebServer, ex: MockWebServer, account: SessionCandidate? = null) = EhClient(NetworkEngine(), eh.url("/"), ex.url("/"), account)
    private val detail = """<h1 id=gn>主标题</h1><h1 id=gj>副标题</h1><div id=gdd><table><tr><td class=gdt2>3 pages</td></tr></table></div><div id=gdn><a>上传者</a></div><div id=gd1><div style="background:url(https://ehgt.org/fixture.jpg)"></div></div>"""
    private fun thumb(number: Int) = "<a href='/s/0123456789/42-$number'><div>thumb</div></a>"
    @Test fun cursorAndGalleryIdentityPreserveOriginalSite() = runBlocking {
        MockWebServer().use { eh -> MockWebServer().use { ex ->
            eh.start(); ex.start()
            ex.enqueue(MockResponse().setBody("<table class=itg><tr><td><a href='/g/42/abcdef0123/'><div class=glink>夹具</div></a><img src='https://ehgt.org/cover.jpg'></td></tr></table><a id=dnext href='/?next=40'>next</a>"))
            val source = EhSource(client(eh, ex), ex = true)
            val result = source.search(ContentQuery("中文 & word"))
            assertEquals("ex:42:abcdef0123", result.items.single().key.id); assertEquals("40", result.nextCursor)
            assertEquals("中文 & word", ex.takeRequest().requestUrl!!.queryParameter("f_search"))
            ex.enqueue(MockResponse().setBody("<p>No hits found</p>"))
            assertTrue(source.search(ContentQuery("中文 & word", page = 2, cursor = result.nextCursor)).items.isEmpty())
            assertEquals("40", ex.takeRequest().requestUrl!!.queryParameter("next")); assertEquals(0, eh.requestCount)
        } }
    }
    @Test fun galleryPaginationOnlyLoadsThumbnailsAndKeepsActualPageNumbers() = runBlocking {
        MockWebServer().use { eh -> MockWebServer().use { ex ->
            eh.start(); ex.start()
            ex.enqueue(MockResponse().setBody(detail + "<div id=gdt>${thumb(2)}${thumb(1)}</div>"))
            ex.enqueue(MockResponse().setBody("<div id=gdt>${thumb(3)}</div>"))
            // Selected EH must not rewrite an EX gallery saved earlier.
            val source = EhSource(client(eh, ex), ex = false, original = true, subtitle = true)
            val book = source.details("ex:42:abcdef0123"); assertEquals("主标题", book.summary.title); assertEquals("副标题", book.summary.subtitle)
            val pages = source.pages(book.summary.key.id, book.chapters.single())
            assertEquals(listOf("1", "2", "3"), pages.map { it.id }); assertTrue(pages.all { it.resolver == "eh-original" })
            assertEquals(2, ex.requestCount); assertEquals(0, eh.requestCount)
            assertTrue(ex.takeRequest().path!!.startsWith("/g/42/abcdef0123/")); assertEquals("1", ex.takeRequest().requestUrl!!.queryParameter("p"))
        } }
    }
    @Test fun exDenialIsNotEhExpirationAndCookiesStayScoped() = runBlocking {
        MockWebServer().use { eh -> MockWebServer().use { ex ->
            eh.start(); ex.start()
            eh.enqueue(MockResponse().setBody("<select name=profile_set><option>Default</option></select>"))
            ex.enqueue(MockResponse().setResponseCode(200).setBody(""))
            val api = client(eh, ex, candidate)
            assertEquals("42", api.validate().accountId)
            assertEquals("ipb_member_id=42; ipb_pass_hash=fixture", eh.takeRequest().getHeader("Cookie"))
            assertEquals(ContentFailureKind.ACCESS_DENIED, (runCatching { api.checkExAccess() }.exceptionOrNull() as ContentFailure).kind)
            assertTrue(ex.takeRequest().getHeader("Cookie")!!.contains("igneous=fixture-ex"))
            assertTrue(runCatching { api.request("https://evil.test/".let { okhttp3.HttpUrl.Builder().scheme("https").host("evil.test").build() }) }.isFailure)
        } }
    }
    @Test fun quotaStopsQueuedImagesWithoutAnotherRequestUntilExplicitRetry() = runBlocking {
        MockWebServer().use { eh -> MockWebServer().use { ex ->
            eh.start(); ex.start(); eh.enqueue(MockResponse().setResponseCode(509))
            val gate = EhImageGate(); val source = EhSource(client(eh, ex), images = gate)
            val page = PageRef("1", 0, eh.url("/s/0123456789/42-1").toString(), resolver = "eh")
            val errors = coroutineScope { (1..4).map { async { runCatching { source.image(page) }.exceptionOrNull() } }.awaitAll() }
            assertTrue(errors.all { (it as? ContentFailure)?.kind == ContentFailureKind.QUOTA }); assertEquals(1, eh.requestCount)
            gate.retry(); eh.enqueue(MockResponse().setBody("<div id=i3></div>"))
            assertEquals(ContentFailureKind.PARSE, (runCatching { source.image(page) }.exceptionOrNull() as ContentFailure).kind); assertEquals(2, eh.requestCount)
        } }
    }
    @Test fun originalFailureOffersFallbackWithoutSilentlySpendingMoreQuota() = runBlocking {
        MockWebServer().use { eh -> MockWebServer().use { ex ->
            eh.start(); ex.start(); eh.enqueue(MockResponse().setBody("<img id=img src='https://ehgt.org/ordinary.jpg'>"))
            val page = PageRef("1", 0, eh.url("/s/0123456789/42-1").toString(), resolver = "eh-original")
            assertEquals(ContentFailureKind.ORIGINAL_UNAVAILABLE, (runCatching { EhSource(client(eh, ex)).image(page) }.exceptionOrNull() as ContentFailure).kind)
            assertEquals(1, eh.requestCount)
        } }
    }
    @Test fun repeatedGalleryPageIsParseFailureRatherThanInfiniteLoop() = runBlocking {
        MockWebServer().use { eh -> MockWebServer().use { ex ->
            eh.start(); ex.start(); repeat(2) { eh.enqueue(MockResponse().setBody("<div id=gdt>${thumb(1)}</div>")) }
            assertEquals(ContentFailureKind.PARSE, (runCatching { EhSource(client(eh, ex)).pages("eh:42:abcdef0123", Chapter("whole", "全册", 1, 3)) }.exceptionOrNull() as ContentFailure).kind)
            assertEquals(2, eh.requestCount)
        } }
    }
    @Test fun webCookieCandidateRequiresEhFieldsButIgneousIsOptional() {
        val spec = WebLoginSpec(EhClient.EH, setOf(EhClient.EH.origin()), EhClient.EH,
            listOf(WebCookieScope("ipb_member_id", "/"), WebCookieScope("ipb_pass_hash", "/"), WebCookieScope("igneous", "/")), optionalCookies = setOf("igneous"))
        assertNotNull(spec.candidate("ipb_member_id=42; ipb_pass_hash=fixture"))
        assertNull(spec.candidate("igneous=fixture; cf_clearance=fixture"))
        assertEquals(setOf("ipb_member_id", "ipb_pass_hash"), EhClient.cookieValues(spec.candidate("ipb_member_id=42; ipb_pass_hash=fixture; extra=ignored")!!).keys)
    }
}
