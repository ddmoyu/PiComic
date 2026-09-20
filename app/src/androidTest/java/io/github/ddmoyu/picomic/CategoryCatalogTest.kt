package io.github.ddmoyu.picomic

import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.network.*
import io.github.ddmoyu.picomic.source.jm.*
import io.github.ddmoyu.picomic.source.ht.*
import io.github.ddmoyu.picomic.source.hitomi.*
import io.github.ddmoyu.picomic.source.nh.*
import io.github.ddmoyu.picomic.source.picacg.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class CategoryCatalogTest {
    @Test fun liveJmDirectoryMetadataOnly() = runBlocking {
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("liveCategoryProbe") == "true")
        val source = JmSource(JmClient(NetworkEngine(NetworkProfile.HttpProxy("10.0.2.2", 7897))))
        val values = source.categories()
        assertTrue(values.size >= 34)
        assertEquals(values.size, values.toSet().size)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0,
            android.os.Bundle().apply { putInt("jm_category_count", values.size) })
    }
    private val time = 1700000000L
    private fun encrypted(body: String): MockResponse {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(JmProtocol.token(time).toByteArray(), "AES"))
        return MockResponse().setBody(JSONObject().put("code", 200).put("data", Base64.getEncoder().encodeToString(cipher.doFinal(body.toByteArray()))).toString())
    }
    private val catalog = """{"categories":[{"id":0,"name":"最新目录","slug":""},{"id":"1","name":"目录甲","slug":"a","sub_categories":[{"name":"译本","slug":"a-translated"}]},{"id":"2","name":"目录乙","slug":"b","sub_categories":[{"name":"译本","slug":"b-translated"}]}],"blocks":[{"title":"主题","content":["冒险","日常"]},{"title":"主题 / 风格","content":["黑白 / 彩色"]}]}"""
    private val emptyJm = """{"content":[],"total":0}"""
    private val setting = """{"img_host":"https://cdn-msp2.jmdanjonproxy.vip"}"""

    @Test fun jmDefaultEntryDuplicateChildrenAndTagsKeepDistinctWorkingRoutes() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(encrypted(catalog)); server.start()
            val source = JmSource(JmClient(NetworkEngine(), server.url("/")) { time })
            val values = source.categories()
            assertEquals(8, values.size)
            val groups = source.categoryGroups()
            assertEquals(values.toSet(), groups.flatMap { it.items }.map { it.value }.toSet())
            assertEquals(ContentCategory("目录甲 / 译本", "译本"), groups.single { it.title == "目录甲" }.items.single())
            assertEquals(ContentCategory("目录乙 / 译本", "译本"), groups.single { it.title == "目录乙" }.items.single())
            assertEquals(ContentCategory("主题 / 风格 / 黑白 / 彩色", "黑白 / 彩色"), groups.single { it.title == "主题 / 风格" }.items.single())
            assertEquals("/categories", server.takeRequest().path)
            for ((index, route) in listOf("最新目录" to "0", "目录甲 / 译本" to "a-translated", "目录乙 / 译本" to "b-translated").withIndex()) {
                server.enqueue(encrypted(emptyJm))
                if (index == 0) server.enqueue(encrypted(setting))
                assertTrue(source.search(ContentQuery(category = route.first)).items.isEmpty())
                val request = server.takeRequest()
                assertEquals("/categories/filter", request.requestUrl!!.encodedPath)
                assertEquals(route.second, request.requestUrl!!.queryParameter("c"))
                if (index == 0) assertEquals("/setting", server.takeRequest().path!!.substringBefore('?'))
            }
            server.enqueue(encrypted(emptyJm))
            source.search(ContentQuery(category = "主题 / 冒险"))
            val tag = server.takeRequest()
            assertEquals("/search", tag.requestUrl!!.encodedPath)
            assertEquals("冒险", tag.requestUrl!!.queryParameter("search_query"))
            server.enqueue(encrypted(emptyJm))
            source.search(ContentQuery(category = "主题 / 风格 / 黑白 / 彩色"))
            assertEquals("黑白 / 彩色", server.takeRequest().requestUrl!!.queryParameter("search_query"))
            assertEquals(7, server.requestCount)
        }
    }
    @Test fun jmBlankSlugIsOnlyValidForTheOfficialZeroIdDirectory() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(encrypted(catalog.replace("\"id\":0", "\"id\":3"))); server.start()
            val source = JmSource(JmClient(NetworkEngine(), server.url("/")) { time })
            assertEquals(ContentFailureKind.PARSE, (runCatching { source.categories() }.exceptionOrNull() as ContentFailure).kind)
        }
    }
    @Test fun htEveryPublishedDirectoryUsesItsOwnNumericRoute() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val source = HtSource(HtClient(NetworkEngine(), server.url("/"), setOf(server.url("/").origin())))
            val expected = setOf(1, 2, 3, 5, 6, 7, 9, 10, 12, 13, 14, 16, 17, 18, 19, 20, 21, 22, 37)
            val actual = mutableSetOf<Int>()
            for (category in source.categories()) {
                server.enqueue(MockResponse().setBody("<div class=gallary_wrap></div><p class=result>0</p>"))
                source.search(ContentQuery(category = category))
                val path = server.takeRequest().requestUrl!!.encodedPath
                actual += Regex("/albums-index-page-1-cate-([0-9]+)\\.html").matchEntire(path)!!.groupValues[1].toInt()
            }
            assertEquals(expected, actual)
            assertEquals(expected.size, server.requestCount)
        }
    }
    @Test fun hitomiTypeDirectoriesUsePagedBinaryIndexesInsteadOfLanguageIndexes() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val source = HitomiSource(HitomiClient(NetworkEngine(), server.url("/")))
            val paths = mutableSetOf<String>()
            for (category in source.categories()) {
                server.enqueue(MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */0"))
                assertTrue(source.search(ContentQuery(category = category)).items.isEmpty())
                val request = server.takeRequest()
                assertTrue(request.getHeader("Range")!!.startsWith("bytes=0-"))
                paths += request.requestUrl!!.encodedPath
            }
            assertEquals(setOf("/type/doujinshi-all.nozomi", "/type/manga-all.nozomi", "/type/artistcg-all.nozomi",
                "/type/gamecg-all.nozomi", "/type/imageset-all.nozomi", "/type/anime-all.nozomi",
                "/index-chinese.nozomi", "/index-english.nozomi", "/index-japanese.nozomi"), paths)
        }
    }
    @Test fun nhTypeAndLanguageDirectoriesSendDifferentFilters() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val source = NhSource(NhClient(NetworkEngine(), server.url("/")))
            val queries = mutableSetOf<String>()
            for ((index, category) in source.categories().withIndex()) {
                server.enqueue(MockResponse().setBody("""{"result":[],"num_pages":0}"""))
                if (index == 0) server.enqueue(MockResponse().setBody("""{"image_servers":["https://i1.nhentai.net/"],"thumb_servers":["https://t1.nhentai.net/"]}"""))
                source.search(ContentQuery(category = category))
                val request = server.takeRequest().requestUrl!!
                if (request.encodedPath == "/search") queries += request.queryParameter("query")!!
                else { assertEquals("/galleries/tagged", request.encodedPath); queries += request.queryParameter("tag_id")!! }
                if (index == 0) assertEquals("/cdn", server.takeRequest().path)
            }
            assertEquals(setOf("category:doujinshi", "category:manga", "29963", "12227", "6346"), queries)
        }
    }
    @Test fun picaKeepsAllServerCategoriesAndOnlyExcludesExternalWebEntries() = runBlocking {
        MockWebServer().use { server ->
            val names = (1..70).map { "目录 $it" }
            val rows = JSONArray(names.map { JSONObject().put("title", it) })
                .put(JSONObject().put("title", "外部网站").put("isWeb", true))
            server.enqueue(MockResponse().setBody(JSONObject().put("code", 200).put("message", "success").put("data", JSONObject().put("categories", rows)).toString()))
            server.start()
            val source = PicacgSource(PicacgClient(NetworkEngine(), server.url("/")), SessionCandidate(CredentialKind.USER_TOKEN, "fixture-token".toByteArray()))
            assertEquals(names, source.categories())
            assertEquals("/categories", server.takeRequest().path)
        }
    }
    @Test fun publicFixedDirectoriesCoverAllEhCategoryBitsAndNeedNoLogin() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = ContentRepository(NetworkRepository.get(context))
        for ((source, count) in listOf(Source.EHENTAI to 10, Source.HTCOMIC to 19, Source.HITOMI to 9, Source.NHENTAI to 5)) {
            assertEquals(count, repository.categories(source).size)
            val groups = repository.categoryGroups(source)
            assertEquals(count, groups.sumOf { it.items.size })
            assertEquals(SourceCategories.fixed(source)!!.toSet(), groups.flatMap { it.items }.map { it.value }.toSet())
            if (source in listOf(Source.HITOMI, Source.NHENTAI)) assertEquals(listOf("内容类型", "语言"), groups.map { it.title })
        }
        val mask: Int = SourceCategories.eh.values.fold(0) { bits, category -> bits or category }
        assertEquals(1023, mask)
        assertEquals(10, SourceCategories.eh.values.toSet().size)
    }
}
