package io.github.ddmoyu.picomic

import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.source.hitomi.*
import io.github.ddmoyu.picomic.source.jm.*
import io.github.ddmoyu.picomic.source.nh.*
import io.github.ddmoyu.picomic.source.picacg.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Synthetic API contracts only; no media is requested or displayed. */
class CategorySortingTest {
    private val time = 1700000000L
    private fun encrypted(body: String): MockResponse {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(JmProtocol.token(time).toByteArray(), "AES"))
        return MockResponse().setBody(JSONObject().put("code", 200).put("data",
            Base64.getEncoder().encodeToString(cipher.doFinal(body.toByteArray()))).toString())
    }

    @Test fun jmSevenOrdersKeepCategoryOrTagAndPage() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = encrypted(when (request.requestUrl!!.encodedPath) {
                    "/categories" -> """{"categories":[{"id":1,"name":"测试分类","slug":"test"}],"blocks":[{"title":"主题","content":["测试标签"]}]}"""
                    "/setting" -> """{"img_host":"https://cdn-msp2.jmdanjonproxy.vip"}"""
                    else -> """{"content":[],"total":0}"""
                })
            }
            server.start()
            val source = JmSource(JmClient(NetworkEngine(), server.url("/")) { time })
            source.categories(); server.takeRequest()
            val orders = listOf("mr", "mv", "mv_m", "mv_w", "mv_t", "mp", "tf")
            assertEquals(orders, CategorySorts.options(Source.JMCOMIC).map { it.value })
            for ((index, order) in orders.withIndex()) {
                source.search(ContentQuery(category = "测试分类", sort = order, page = 2))
                val request = server.takeRequest().requestUrl!!
                assertEquals("/categories/filter", request.encodedPath)
                assertEquals("test", request.queryParameter("c")); assertEquals(order, request.queryParameter("o"))
                assertEquals("2", request.queryParameter("page"))
                if (index == 0) server.takeRequest() // image-host metadata, never image bytes
                source.search(ContentQuery(category = "主题 / 测试标签", sort = order))
                val tag = server.takeRequest().requestUrl!!
                assertEquals("/search", tag.encodedPath)
                assertEquals("测试标签", tag.queryParameter("search_query")); assertEquals(order, tag.queryParameter("o"))
            }
        }
    }

    @Test fun picaFourOrdersAreSentWithTheCategory() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val source = PicacgSource(PicacgClient(NetworkEngine(), server.url("/")),
                SessionCandidate(CredentialKind.USER_TOKEN, "fixture".toByteArray()))
            for (order in listOf("dd", "da", "ld", "vd")) {
                server.enqueue(MockResponse().setBody("""{"code":200,"message":"success","data":{"comics":{"docs":[],"page":1,"pages":1}}}"""))
                source.search(ContentQuery(category = "测试 & 分类", sort = order))
                val url = server.takeRequest().requestUrl!!
                assertEquals("测试 & 分类", url.queryParameter("c")); assertEquals(order, url.queryParameter("s"))
            }
        }
    }

    @Test fun nhFiveOrdersApplyToBothLanguageAndTypeDirectories() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val source = NhSource(NhClient(NetworkEngine(), server.url("/")))
            var initialized = false
            for (category in listOf(SourceCategories.nhTypes.keys.first(), SourceCategories.nhLanguages.keys.first())) {
                for (order in listOf("date", "popular", "popular-month", "popular-week", "popular-today")) {
                    server.enqueue(MockResponse().setBody("""{"result":[],"num_pages":0}"""))
                    if (!initialized) server.enqueue(MockResponse().setBody("""{"image_servers":["https://i1.nhentai.net/"],"thumb_servers":["https://t1.nhentai.net/"]}"""))
                    source.search(ContentQuery(category = category, sort = order, page = 3))
                    val url = server.takeRequest().requestUrl!!
                    assertEquals(order, url.queryParameter("sort")); assertEquals("3", url.queryParameter("page"))
                    if (category in SourceCategories.nhTypes) {
                        assertEquals("/search", url.encodedPath); assertEquals("category:${SourceCategories.nhTypes[category]}", url.queryParameter("query"))
                    } else {
                        assertEquals("/galleries/tagged", url.encodedPath); assertEquals(SourceCategories.nhLanguages[category], url.queryParameter("tag_id"))
                    }
                    if (!initialized) { server.takeRequest(); initialized = true }
                }
            }
        }
    }

    @Test fun hitomiUsesNativeTypeAndLanguageRankIndexesWithPageRanges() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val source = HitomiSource(HitomiClient(NetworkEngine(), server.url("/")))
            for ((category, type) in listOf(SourceCategories.hitomiTypes.entries.first().let { it.key to it.value },
                SourceCategories.hitomiLanguages.entries.first().let { it.key to null })) {
                val language = SourceCategories.hitomiLanguages[category] ?: "all"
                for (sort in listOf("date_added", "published", "today", "week", "month", "year")) {
                    server.enqueue(MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */0"))
                    source.search(ContentQuery(category = category, sort = sort, page = 2))
                    val request = server.takeRequest()
                    val order = when (sort) { "date_added" -> ""; "published" -> "date/published/"; else -> "popular/$sort/" }
                    val path = if (type != null) "/type/$order$type-$language.nozomi"
                        else if (sort == "date_added") "/index-$language.nozomi"
                        else "/${order.dropLast(1)}-$language.nozomi"
                    assertEquals(path, request.requestUrl!!.encodedPath)
                    assertEquals("bytes=100-199", request.getHeader("Range"))
                }
            }
        }
    }

    @Test fun hitomiRandomOrderIsPinnedAcrossPagesAndSourceInstances() = runBlocking {
        MockWebServer().use { server ->
            val ids = (1..30).toList()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    if (path.endsWith(".nozomi")) return MockResponse().setBody(Buffer().write(ByteBuffer.allocate(ids.size * 4).apply { ids.forEach(::putInt) }.array()))
                    val id = path.substringAfterLast('/').removeSuffix(".js")
                    return MockResponse().setBody("""var galleryinfo = {"id":"$id","title":"测试 $id","files":[{"hash":"${"a".repeat(64)}"}]};""")
                }
            }
            server.start()
            val client = HitomiClient(NetworkEngine(), server.url("/"))
            val query = ContentQuery(category = SourceCategories.hitomiTypes.keys.first(), sort = "random", randomSeed = 812L)
            val first = HitomiSource(client).search(query)
            val second = HitomiSource(client).search(query.copy(page = 2))
            assertEquals(2, first.nextPage); assertNull(second.nextPage)
            val actual = (first.items + second.items).map { it.key.id.toInt() }
            assertEquals(ids.toSet(), actual.toSet()); assertEquals(30, actual.size); assertNotEquals(ids, actual)
            assertEquals(first.items, HitomiSource(client).search(query).items)
            assertEquals(1, (1..server.requestCount).map { server.takeRequest() }.count { it.path!!.endsWith(".nozomi") })
        }
    }
}
