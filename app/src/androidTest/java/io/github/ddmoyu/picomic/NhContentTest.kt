package io.github.ddmoyu.picomic

import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.source.nh.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class NhContentTest {
    private val cdn = """{"image_servers":["https://i1.nhentai.net"],"thumb_servers":["https://t3.nhentai.net"]}"""
    private val listItem = """{"id":42,"english_title":"接口夹具","thumbnail":"galleries/123/thumb.webp.webp","num_pages":2,"tag_ids":[29963]}"""
    private val gallery = """{"id":42,"title":{"pretty":"接口夹具"},"cover":{"path":"galleries/123/cover.webp.webp"},"num_pages":2,"tags":[{"id":29963,"type":"language","name":"Chinese"}],"pages":[{"number":2,"path":"galleries/123/2.webp","width":700,"height":1000},{"number":1,"path":"galleries/123/1.png","width":640,"height":930}]}"""
    private fun client(server: MockWebServer, candidate: SessionCandidate? = null) = NhClient(NetworkEngine(), server.url("/"), candidate)
    private fun MockWebServer.json(body: String) { enqueue(MockResponse().setBody(body)) }
    @Test fun anonymousSearchPreservesPaginationLanguageAndOriginalMediaPath() = runBlocking {
        MockWebServer().use { server ->
            server.json("""{"result":[$listItem],"num_pages":3}"""); server.json(cdn); server.start()
            val results = NhSource(client(server)).search(ContentQuery("中文 & key"))
            assertEquals(2, results.nextPage); assertEquals("Chinese", results.items.single().language)
            assertEquals("https://t3.nhentai.net/galleries/123/thumb.webp.webp", results.items.single().cover)
            val request = server.takeRequest(); assertEquals("中文 & key", request.requestUrl!!.queryParameter("query")); assertNull(request.getHeader("Authorization"))
        }
    }
    @Test fun pageNumbersRemainStableWhenResponseOrderChanges() = runBlocking {
        MockWebServer().use { server ->
            server.json(gallery); server.json(cdn); server.start()
            val source = NhSource(client(server)); val detail = source.details("42"); val pages = source.pages("42", detail.chapters.single())
            assertEquals("whole", detail.chapters.single().id); assertEquals(listOf("1", "2"), pages.map { it.id })
            assertEquals(640, pages.first().width); assertEquals("https://i1.nhentai.net/galleries/123/1.png", pages.first().url)
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun keyAndUserSchemesAreDistinctAndRequireActualUserIdentity() = runBlocking {
        MockWebServer().use { server ->
            repeat(2) { server.json("""{"id":42,"username":"夹具账号"}""") }; server.start()
            for ((kind, scheme) in listOf(CredentialKind.API_KEY to "Key", CredentialKind.USER_TOKEN to "User")) {
                val validated = client(server, SessionCandidate(kind, "fixture-token".toByteArray())).validate()
                assertEquals("42", validated.accountId); assertEquals("夹具账号", validated.displayName)
                assertEquals("$scheme fixture-token", server.takeRequest().getHeader("Authorization"))
            }
            server.json("""{"message":"ok"}""")
            assertTrue(runCatching { client(server, SessionCandidate(CredentialKind.API_KEY, byteArrayOf(65))).validate() }.exceptionOrNull() is ContentFailure)
        }
    }
    @Test fun expiredAndForbiddenNeverRetryUsingAnotherIdentity() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401)); server.enqueue(MockResponse().setResponseCode(403)); server.start()
            val api = client(server, SessionCandidate(CredentialKind.API_KEY, "fixture".toByteArray()))
            assertEquals(ContentFailureKind.EXPIRED, (runCatching { api.validate() }.exceptionOrNull() as ContentFailure).kind)
            assertEquals(ContentFailureKind.LOGIN, (runCatching { api.validate() }.exceptionOrNull() as ContentFailure).kind)
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun duplicatePagesAndUntrustedCdnCannotReachReader() = runBlocking {
        MockWebServer().use { server ->
            server.json(gallery.replace("\"number\":2", "\"number\":1")); server.json(cdn); server.start()
            assertTrue(runCatching { NhSource(client(server)).pages("42", Chapter("whole", "全册", 1)) }.exceptionOrNull() is ContentFailure)
            server.json(gallery); server.json(cdn.replace("https://i1.nhentai.net", "https://i1.nhentai.net.evil.test"))
            assertTrue(runCatching { NhSource(client(server)).details("42") }.exceptionOrNull() is ContentFailure)
        }
    }
    @Test fun webCandidateAcceptsOneVerifiedTokenNameWithoutUnrelatedCookies() {
        val site = NhClient.API
        val spec = WebLoginSpec(site, setOf("https://nhentai.net:443"), site, listOf(WebCookieScope("access_token", "/"), WebCookieScope("__Host-access_token", "/")), anyCookie = true)
        assertNull(spec.candidate("cf_clearance=only-challenge"))
        val candidate = spec.candidate("cf_clearance=not-an-account; __Host-access_token=fixture")!!
        assertEquals("__Host-access_token=fixture", candidate.value.toString(Charsets.UTF_8))
    }
}
