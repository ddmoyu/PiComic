package io.github.ddmoyu.picomic

import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.source.picacg.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PicacgContentTest {
    private fun response(data: String) = MockResponse().setBody("""{"code":200,"message":"success","data":$data}""")
    private val thumb = """{"fileServer":"https://images.example.test","path":"folder/cover.png"}"""
    private val comic = """{"_id":"comic-id","title":"契约作品","author":"作者","thumb":$thumb,"description":"简介","tags":["测试"],"categories":["分类"],"epsCount":2,"pagesCount":240}"""
    private fun adapter(server: MockWebServer) = PicacgSource(PicacgClient(NetworkEngine(), server.url("/")), SessionCandidate(CredentialKind.USER_TOKEN, "fixture-token".toByteArray()))
    @Test fun searchAndCategoryUseDifferentRoutesWithEncodedParameters() = runBlocking {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(response("""{"comics":{"docs":[$comic],"page":1,"pages":2}}""")) }; server.start()
            val source = adapter(server)
            val first = source.search(ContentQuery("中文 & 特殊", sort = "ld"))
            assertEquals("comic-id", first.items.single().key.id); assertEquals(2, first.nextPage)
            assertEquals(240, first.items.single().pageCount)
            val request = server.takeRequest()
            assertEquals("POST", request.method); assertEquals("/comics/advanced-search?page=1", request.path)
            assertEquals("中文 & 特殊", JSONObject(request.body.readUtf8()).getString("keyword"))
            assertEquals("fixture-token", request.getHeader("authorization"))
            source.search(ContentQuery(category = "分类 & 标签"))
            val category = server.takeRequest(); assertEquals("GET", category.method)
            assertEquals("分类 & 标签", category.requestUrl!!.queryParameter("c"))
        }
    }
    @Test fun chapterPaginationPreservesActualIdsAndSortsByExplicitOrder() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response("""{"comic":$comic}"""))
            server.enqueue(response("""{"eps":{"docs":[{"_id":"ep-second","title":"后篇","order":9}],"page":1,"pages":2}}"""))
            server.enqueue(response("""{"eps":{"docs":[{"_id":"ep-first","title":"前篇","order":3}],"page":2,"pages":2}}""")); server.start()
            val result = adapter(server).details("comic-id")
            assertEquals(listOf("ep-first", "ep-second"), result.chapters.map { it.id })
            assertEquals(listOf(3, 9), result.chapters.map { it.order })
            assertEquals(3, server.requestCount)
        }
    }
    @Test fun imagePaginationUsesChapterOrderAndNeverEmbedsApiTokenInUrls() = runBlocking {
        MockWebServer().use { server ->
            for (page in 1..2) server.enqueue(response("""{"pages":{"docs":[{"_id":"page-$page","media":{"fileServer":"https://images.example.test/","path":"a/$page.png","width":640,"height":900}}],"page":$page,"pages":2}}"""))
            server.start(); val result = adapter(server).pages("comic-id", Chapter("ep-id", "章节", 9))
            assertEquals(listOf("page-1", "page-2"), result.map { it.id })
            assertEquals(listOf(0, 1), result.map { it.index })
            assertEquals("https://images.example.test/static/a/1.png", result.first().url)
            assertEquals("/comics/comic-id/order/9/pages?page=1", server.takeRequest().path)
            assertFalse(result.any { it.url.contains("fixture-token") })
        }
    }
    @Test fun malformedOrRepeatedPaginationFailsWithoutPublishingPartialChapter() = runBlocking {
        MockWebServer().use { server ->
            val repeated = """{"pages":{"docs":[{"_id":"same-page","media":$thumb}],"pages":2}}"""
            repeat(2) { server.enqueue(response(repeated)) }; server.start()
            val error = runCatching { adapter(server).pages("comic-id", Chapter("ep", "章节", 1)) }.exceptionOrNull()
            assertTrue(error is ContentFailure); assertEquals(2, server.requestCount)
        }
    }
    @Test fun invalidMediaUrlAndMismatchedDetailsAreRejected() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response("""{"comics":{"docs":[${comic.replace("https://images.example.test", "http://images.example.test")}],"pages":1}}"""))
            server.enqueue(response("""{"comic":$comic}""")); server.start()
            val source = adapter(server)
            assertTrue(runCatching { source.search(ContentQuery()) }.exceptionOrNull() is ContentFailure)
            assertTrue(runCatching { source.details("different-id") }.exceptionOrNull() is ContentFailure)
        }
    }
}
