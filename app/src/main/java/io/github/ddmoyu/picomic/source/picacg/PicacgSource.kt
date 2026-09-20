package io.github.ddmoyu.picomic.source.picacg

import io.github.ddmoyu.picomic.auth.SessionCandidate
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/** Per-operation adapter. Its token and client belong to the same account/network lease. */
class PicacgSource(private val client: PicacgClient, private val token: SessionCandidate) : ComicSource {
    override val source = Source.PICACG
    override suspend fun search(query: ContentQuery): ContentPage<ComicSummary> {
        require(query.page in 1..10000 && query.keyword.length <= 300)
        val body = JSONObject().put("keyword", query.keyword).put("sort", query.sort.takeIf { it in setOf("dd", "da", "ld", "vd") } ?: "dd")
        val root = if (query.category != null || query.keyword.isEmpty()) client.content(listOf("comics"),
            buildMap { put("page", query.page.toString()); put("s", body.getString("sort")); query.category?.let { put("c", it) } }, token)
        else client.content(listOf("comics", "advanced-search"), mapOf("page" to query.page.toString()), token, body)
        val page = objectAt(root, "comics")
        return ContentPage(objects(page, "docs").map(::summary), nextPage(page, query.page))
    }
    override suspend fun categories(): List<String> = objects(client.content(listOf("categories"), emptyMap(), token), "categories")
        .filterNot { it.optBoolean("isWeb") }.map { requiredString(it, "title") }.distinct()

    override suspend fun details(id: String): ComicDetails {
        validateId(id)
        val comic = objectAt(client.content(listOf("comics", id), emptyMap(), token), "comic")
        val summary = summary(comic)
        if (summary.key.id != id) malformed()
        val chapters = mutableListOf<Chapter>()
        var page: Int? = 1
        val seen = mutableSetOf<String>()
        while (page != null) {
            val current = page
            val envelope = objectAt(client.content(listOf("comics", id, "eps"), mapOf("page" to current.toString()), token), "eps")
            val batch = objects(envelope, "docs")
            batch.forEach {
                val chapterId = requiredString(it, "_id")
                val order = positive(it, "order") ?: malformed()
                if (!seen.add(chapterId) || chapters.any { old -> old.order == order }) malformed()
                chapters += Chapter(chapterId, requiredString(it, "title"), order, positive(it, "pagesCount"))
            }
            page = nextPage(envelope, current)
            if (page != null && batch.isEmpty() || chapters.size > 10000) malformed()
        }
        if (chapters.isEmpty()) throw ContentFailure(ContentFailureKind.NOT_FOUND, "该作品还没有可读章节")
        return ComicDetails(summary, optionalString(comic, "description"), chapters.sortedBy { it.order })
    }

    override suspend fun pages(comicId: String, chapter: Chapter): List<PageRef> {
        validateId(comicId); require(chapter.order > 0)
        val pages = mutableListOf<PageRef>()
        val seen = mutableSetOf<String>()
        var cursor: Int? = 1
        while (cursor != null) {
            val current = cursor
            val envelope = objectAt(client.content(listOf("comics", comicId, "order", chapter.order.toString(), "pages"), mapOf("page" to current.toString()), token), "pages")
            val batch = objects(envelope, "docs")
            for (item in batch) {
                val id = requiredString(item, "_id")
                if (!seen.add(id)) malformed()
                val media = objectAt(item, "media")
                pages += PageRef(id, pages.size, image(media), positive(media, "width"), positive(media, "height"))
            }
            cursor = nextPage(envelope, current)
            if (cursor != null && batch.isEmpty() || pages.size > 10000) malformed()
        }
        if (pages.isEmpty()) throw ContentFailure(ContentFailureKind.NOT_FOUND, "本章没有可读图片")
        return pages
    }
    private fun summary(json: JSONObject): ComicSummary {
        val id = requiredString(json, "_id"); validateId(id)
        return ComicSummary(ComicKey(source, id), requiredString(json, "title"), optionalString(json, "author"),
            json.optJSONObject("thumb")?.let(::image), strings(json, "tags") + strings(json, "categories"),
            optionalString(json, "language").ifBlank { null }, positive(json, "epsCount"), positive(json, "pagesCount"))
    }
    private fun image(media: JSONObject): String {
        val host = requiredString(media, "fileServer").toHttpUrlOrNull() ?: malformed()
        if (host.scheme != "https" || host.username.isNotEmpty() || host.password.isNotEmpty() || host.query != null || host.fragment != null) malformed()
        val path = requiredString(media, "path")
        if (path.startsWith('/') || path.contains('\\') || path.split('/').any { it == ".." || it == "." } || path.contains('%')) malformed()
        return host.newBuilder().addPathSegment("static").addPathSegments(path).build().toString()
    }
    private fun nextPage(json: JSONObject, current: Int): Int? {
        val pages = (json.opt("pages") as? Number)?.toInt() ?: malformed()
        if (pages !in 0..10000 || (pages > 0 && current > pages)) malformed()
        val reported = (json.opt("page") as? Number)?.toInt()
        if (reported != null && reported != current) malformed()
        return if (current < pages) current + 1 else null
    }
    private fun requiredString(json: JSONObject, key: String): String = (json.opt(key) as? String)?.takeIf { it.isNotBlank() && it.length <= 20000 } ?: malformed()
    private fun optionalString(json: JSONObject, key: String): String = (json.opt(key) as? String)?.take(20000).orEmpty()
    private fun objectAt(json: JSONObject, key: String) = json.optJSONObject(key) ?: malformed()
    private fun objects(json: JSONObject, key: String): List<JSONObject> {
        val values = json.optJSONArray(key) ?: malformed()
        if (values.length() > 1000) malformed()
        return List(values.length()) { values.optJSONObject(it) ?: malformed() }
    }
    private fun strings(json: JSONObject, key: String): List<String> = json.optJSONArray(key)?.let { array ->
        if (array.length() > 1000) malformed()
        List(array.length()) { (array.opt(it) as? String)?.take(500) ?: malformed() }
    }.orEmpty()
    private fun positive(json: JSONObject, key: String) = (json.opt(key) as? Number)?.toInt()?.takeIf { it in 1..100000 }
    private fun validateId(id: String) { if (!id.matches(Regex("[a-zA-Z0-9_-]{1,128}"))) malformed() }
    private fun malformed(): Nothing = throw ContentFailure(ContentFailureKind.PARSE, "平台内容格式已变化，请稍后重试")
}
