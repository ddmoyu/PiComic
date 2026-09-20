package io.github.ddmoyu.picomic.source.nh

import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

class NhSource(private val client: NhClient) : ComicSource {
    override val source = Source.NHENTAI
    private val languages = mapOf("Chinese" to "29963", "English" to "12227", "Japanese" to "6346")
    private var imageHost: HttpUrl? = null
    private var thumbHost: HttpUrl? = null
    private var gallery: JSONObject? = null
    private suspend fun hosts() {
        if (imageHost != null) return
        val cdn = client.get(listOf("cdn"))
        fun host(name: String, prefix: String): HttpUrl {
            val options = cdn.optJSONArray(name) ?: throw NhClient.malformed()
            return (0 until options.length()).mapNotNull { index ->
                runCatching { options.getString(index).toHttpUrl() }.getOrNull()?.takeIf {
                    it.scheme == "https" && it.port == 443 && it.host.matches(Regex("$prefix[1-9][0-9]?\\.nhentai\\.net")) &&
                        it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null && it.encodedPath == "/"
                }
            }.firstOrNull() ?: throw NhClient.malformed()
        }
        val images = host("image_servers", "i"); val thumbs = host("thumb_servers", "t")
        imageHost = images; thumbHost = thumbs
    }
    override suspend fun categories() = languages.keys.toList()
    override suspend fun search(query: ContentQuery): ContentPage<ComicSummary> {
        require(query.page in 1..10000)
        val params = mutableMapOf("page" to query.page.toString())
        val path = when {
            query.keyword.isNotBlank() -> { params["query"] = query.keyword; listOf("search") }
            query.category != null -> { params["tag_id"] = languages[query.category] ?: throw NhClient.malformed(); listOf("galleries", "tagged") }
            else -> listOf("galleries")
        }
        params["sort"] = query.sort.takeIf { it in setOf("date", "popular", "popular-today", "popular-week", "popular-month") } ?: "date"
        val data = client.get(path, params)
        hosts()
        val rows = data.optJSONArray("result") ?: throw NhClient.malformed()
        val items = rows.objects().map { summary(it) }
        if (items.map { it.key }.distinct().size != items.size) throw NhClient.malformed()
        val pages = data.optInt("num_pages", -1)
        if (pages !in 0..1000000 || (items.isNotEmpty() && pages < query.page)) throw NhClient.malformed()
        return ContentPage(items, (query.page + 1).takeIf { query.page < pages && query.page < 10000 && items.isNotEmpty() })
    }
    private suspend fun data(id: String): JSONObject {
        NhClient.id(id)
        return gallery?.takeIf { it.optString("id") == id } ?: client.get(listOf("galleries", id)).also {
            if (it.optString("id") != id) throw NhClient.malformed()
            gallery = it
        }
    }
    override suspend fun details(id: String): ComicDetails {
        val data = data(id); hosts()
        val pages = data.optInt("num_pages", -1).takeIf { it in 1..10000 } ?: throw NhClient.malformed()
        return ComicDetails(summary(data), "", listOf(Chapter("whole", "全册", 1, pages)))
    }
    override suspend fun pages(comicId: String, chapter: Chapter): List<PageRef> {
        if (chapter.id != "whole") throw NhClient.malformed()
        val data = data(comicId); hosts()
        val pages = data.optJSONArray("pages")?.objects() ?: throw NhClient.malformed()
        if (pages.size !in 1..10000 || pages.size != data.optInt("num_pages", -1)) throw NhClient.malformed()
        val result = pages.map { page ->
            val number = page.optInt("number", -1).takeIf { it in 1..pages.size } ?: throw NhClient.malformed()
            val width = page.optInt("width", -1).takeIf { it > 0 } ?: throw NhClient.malformed()
            val height = page.optInt("height", -1).takeIf { it > 0 } ?: throw NhClient.malformed()
            PageRef(number.toString(), number - 1, media(page.optString("path"), false), width, height)
        }.sortedBy { it.index }
        if (result.map { it.index } != pages.indices.toList()) throw NhClient.malformed()
        return result
    }
    private fun summary(item: JSONObject): ComicSummary {
        val id = NhClient.id(item.optString("id"))
        val title = item.optJSONObject("title")
        val name = listOf(title?.optString("pretty"), title?.optString("english"), title?.optString("japanese"), item.optString("english_title"), item.optString("japanese_title")).firstOrNull { !it.isNullOrBlank() && it != "null" } ?: throw NhClient.malformed()
        val tags = item.optJSONArray("tags")?.objects().orEmpty()
        val tagIds = item.optJSONArray("tag_ids")?.let { array -> (0 until array.length()).map { array.optString(it) } }.orEmpty() + tags.map { it.optString("id") }
        val language = languages.entries.firstOrNull { it.value in tagIds }?.key
        val cover = item.optJSONObject("cover")?.optString("path") ?: item.optJSONObject("thumbnail")?.optString("path") ?: item.optString("thumbnail")
        return ComicSummary(ComicKey(source, id), name, tags.filter { it.optString("type") == "artist" }.joinToString("、") { it.optString("name") },
            cover.takeIf { it.isNotBlank() }?.let { media(it, true) }, tags.map { it.optString("name") }.filter(String::isNotBlank), language, 1, item.optInt("num_pages").takeIf { it > 0 })
    }
    private fun media(value: String, thumbnail: Boolean): String {
        val host = (if (thumbnail) thumbHost else imageHost) ?: throw NhClient.malformed()
        val path = value.removePrefix("/")
        if (!path.matches(Regex("galleries/[1-9][0-9]*/[A-Za-z0-9_-]+\\.(jpg|jpeg|png|webp|gif|avif)(\\.(jpg|jpeg|png|webp|gif|avif))?"))) throw NhClient.malformed()
        // v2 currently repeats the extension in some paths. Preserve it until live image checks confirm a repair rule.
        return host.newBuilder().apply { path.split('/').forEach(::addPathSegment) }.build().toString()
    }
    private fun JSONArray.objects() = (0 until length()).map { optJSONObject(it) ?: throw NhClient.malformed() }
}
