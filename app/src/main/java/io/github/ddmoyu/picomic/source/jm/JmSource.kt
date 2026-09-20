package io.github.ddmoyu.picomic.source.jm

import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

class JmSource(private val client: JmClient, private val imageLine: Int = 1) : ComicSource {
    override val source = Source.JMCOMIC
    private var host: HttpUrl? = null
    private suspend fun imageHost(): HttpUrl = host ?: JmProtocol.imageHost(client.get("setting", mapOf("app_img_shunt" to imageLine.coerceIn(1, 4).toString(), "express" to "off")).optString("img_host")).also { host = it }
    private suspend fun categoryMap(): Map<String, String> {
        val data = client.get("categories").array("categories")
        return buildMap {
            data.objects().forEach { category ->
                put(category.text("name"), category.text("slug"))
                category.optJSONArray("sub_categories")?.objects()?.forEach { put(it.text("name"), it.text("slug")) }
            }
        }
    }
    override suspend fun categories() = categoryMap().keys.toList()
    override suspend fun search(query: ContentQuery): ContentPage<ComicSummary> {
        require(query.page in 1..10000)
        val order = query.sort.takeIf { it in setOf("mr", "mv", "mp", "tf") } ?: "mr"
        val params = mutableMapOf("page" to query.page.toString(), "o" to order)
        val path = if (query.keyword.isNotBlank()) { params["search_query"] = query.keyword; "search" }
        else { params["c"] = query.category?.let { categoryMap()[it] ?: throw ContentFailure(ContentFailureKind.NOT_FOUND, "JM 分类已变化，请重新选择") } ?: "0"; "categories/filter" }
        val data = client.get(path, params)
        val images = imageHost()
        val items = data.array("content").objects().map { summary(it, images) }
        if (items.map { it.key }.distinct().size != items.size) throw JmProtocol.malformed()
        val total = data.optString("total").toIntOrNull()?.takeIf { it >= 0 } ?: throw JmProtocol.malformed()
        val pageSize = data.optInt("limit", 80).takeIf { it in 1..200 } ?: throw JmProtocol.malformed()
        if (items.isEmpty() && total > (query.page - 1).toLong() * pageSize) throw JmProtocol.malformed()
        return ContentPage(items, (query.page + 1).takeIf { items.isNotEmpty() && query.page.toLong() * pageSize < total && query.page < 10000 })
    }
    override suspend fun details(id: String): ComicDetails {
        JmProtocol.id(id)
        val data = client.get("album", mapOf("id" to id))
        if (JmProtocol.id(data.optString("id")) != id) throw JmProtocol.malformed()
        val series = data.array("series").objects()
        val chapters = if (series.isEmpty()) listOf(Chapter("whole", "全册", 1)) else series.map {
            Chapter(JmProtocol.id(it.optString("id")), it.optString("name").ifBlank { "第 ${it.optInt("sort")} 话" }, it.optString("sort").toIntOrNull()?.takeIf { n -> n > 0 } ?: throw JmProtocol.malformed())
        }.sortedBy { it.order }
        if (chapters.map { it.id }.distinct().size != chapters.size || chapters.map { it.order }.distinct().size != chapters.size) throw JmProtocol.malformed()
        return ComicDetails(summary(data, imageHost()).copy(chapterCount = chapters.size), data.optString("description"), chapters)
    }
    override suspend fun pages(comicId: String, chapter: Chapter): List<PageRef> {
        JmProtocol.id(comicId)
        val photo = JmProtocol.id(if (chapter.id == "whole") comicId else chapter.id)
        val data = client.get("chapter", mapOf("id" to photo))
        if (data.has("id") && JmProtocol.id(data.optString("id")) != photo) throw JmProtocol.malformed()
        val files = data.array("images").let { array -> (0 until array.length()).map { JmProtocol.filename(array.getString(it)) } }
        if (files.isEmpty() || files.size > 10000 || files.distinct().size != files.size) throw JmProtocol.malformed()
        val html = client.html("chapter_view_template", mapOf("id" to photo, "mode" to "vertical", "page" to "0", "app_img_shunt" to imageLine.toString(), "express" to "off"))
        fun number(name: String): Long = Regex("\\bvar\\s+$name\\s*=\\s*([0-9]+)\\s*;").findAll(html).toList().singleOrNull()?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 } ?: throw JmProtocol.malformed()
        if (number("aid") != photo.toLong()) throw JmProtocol.malformed()
        val scramble = number("scramble_id")
        val speed = Regex("\\bvar\\s+speed\\s*=\\s*['\"]([01])['\"]\\s*;").find(html)?.groupValues?.get(1) == "1"
        val imageBase = imageHost()
        return files.mapIndexed { index, filename ->
            val url = imageBase.newBuilder().addPathSegments("media/photos").addPathSegment(photo).addPathSegment(filename).build()
            PageRef(filename, index, url.toString(), jm = JmImageRule(photo.toLong(), scramble, filename, speed))
        }
    }
    private fun summary(data: JSONObject, images: HttpUrl): ComicSummary {
        val id = JmProtocol.id(data.optString("id"))
        val authors = data.optJSONArray("author")?.strings()?.joinToString("、") ?: data.optString("author")
        val tags = data.optJSONArray("tags")?.strings().orEmpty() + listOfNotNull(data.optJSONObject("category")?.optString("title")?.takeIf { it.isNotBlank() })
        return ComicSummary(ComicKey(source, id), data.text("name"), authors,
            images.newBuilder().addPathSegments("media/albums").addPathSegment("${id}_3x4.jpg").build().toString(), tags)
    }
    private fun JSONObject.array(name: String) = optJSONArray(name) ?: throw JmProtocol.malformed()
    private fun JSONObject.text(name: String) = (opt(name) as? String)?.takeIf { it.isNotBlank() && it.length <= 2000 } ?: throw JmProtocol.malformed()
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { optJSONObject(it) ?: throw JmProtocol.malformed() }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { opt(it) as? String ?: throw JmProtocol.malformed() }
}
