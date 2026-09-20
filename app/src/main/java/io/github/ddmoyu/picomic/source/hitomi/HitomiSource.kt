package io.github.ddmoyu.picomic.source.hitomi

import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.source.html.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

class HitomiSource(private val client: HitomiClient) : ComicSource {
    override val source = Source.HITOMI
    private val languages = SourceCategories.hitomiLanguages
    private val types = SourceCategories.hitomiTypes
    private var loaded: Pair<String, JSONObject>? = null
    override suspend fun categories() = SourceCategories.fixed(source)!!
    override suspend fun search(query: ContentQuery): ContentPage<ComicSummary> {
        require(query.page in 1..10000)
        val type = query.category?.let { types[it] }
        val language = query.category?.let { languages[it] ?: if (type != null) "all" else throw parseChanged("Hitomi") } ?: "all"
        val offset = (query.page - 1) * PAGE_SIZE
        val ids: IntArray; val more: Boolean
        if (query.keyword.isBlank()) {
            val path = if (type == null) listOf("index-$language.nozomi") else listOf("type", "$type-all.nozomi")
            val range = client.range(path, offset.toLong() * 4, PAGE_SIZE * 4)
            if (range.total % 4 != 0L) throw parseChanged("Hitomi")
            ids = HitomiProtocol.ids(range.bytes); more = (offset + ids.size).toLong() * 4 < range.total
        } else {
            val keyword = listOfNotNull(type?.let { "type:$it" }, query.keyword).joinToString(" ")
            val all = client.searchIds(keyword, language)
            ids = all.copyOfRange(offset.coerceAtMost(all.size), (offset + PAGE_SIZE).coerceAtMost(all.size)); more = offset + ids.size < all.size
        }
        val gate = Semaphore(3)
        val results = coroutineScope { ids.map { id -> async { gate.withPermit {
            try { summary(id.toString(), client.gallery(id.toString())) }
            catch (e: ContentFailure) { if (e.kind == ContentFailureKind.NOT_FOUND) null else throw e }
        } } }.awaitAll().filterNotNull() }
        return ContentPage(results, if (more) query.page + 1 else null)
    }
    override suspend fun details(id: String): ComicDetails {
        val data = client.gallery(id)
        val item = summary(id, data)
        loaded = id to data
        return ComicDetails(item, listOf(data.optString("japanese_title"), data.optString("type"), data.optString("datepublished")).filter(String::isNotBlank).joinToString("\n"), listOf(Chapter("whole", "全册", 1, item.pageCount)))
    }
    override suspend fun pages(comicId: String, chapter: Chapter): List<PageRef> {
        if (chapter.id != "whole") throw parseChanged("Hitomi")
        val data = loaded?.takeIf { it.first == comicId }?.second ?: client.gallery(comicId)
        summary(comicId, data)
        val files = data.getJSONArray("files")
        if (chapter.pageCount != null && chapter.pageCount != files.length()) throw parseChanged("Hitomi")
        val rules = client.rules()
        return (0 until files.length()).map { index ->
            val file = files.getJSONObject(index); val hash = file.optString("hash").also(HitomiImageRules::requireHash)
            val width = file.optInt("width").takeIf { it in 1..100000 } ?: throw parseChanged("Hitomi")
            val height = file.optInt("height").takeIf { it in 1..100000 } ?: throw parseChanged("Hitomi")
            PageRef("${index + 1}:$hash", index, rules.image(hash), width, height, resolver = "hitomi")
        }
    }
    override suspend fun image(page: PageRef): ByteArray = client.image(page.id.substringAfter(':').also(HitomiImageRules::requireHash))
    private fun summary(id: String, data: JSONObject): ComicSummary {
        if (data.optString("id") != positiveId(id, "Hitomi")) throw parseChanged("Hitomi")
        if (data.optBoolean("blocked")) throw ContentFailure(ContentFailureKind.ACCESS_DENIED, "Hitomi 当前作品不可访问")
        val title = data.optString("title").takeIf { it.isNotBlank() && it.length <= 2000 } ?: throw parseChanged("Hitomi")
        val files = data.optJSONArray("files")?.takeIf { it.length() in 1..20000 } ?: throw parseChanged("Hitomi")
        val hash = files.getJSONObject(0).optString("hash").also(HitomiImageRules::requireHash)
        val artists = data.optJSONArray("artists")?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("artist")?.takeIf(String::isNotBlank) } }.orEmpty()
        val tags = data.optJSONArray("tags")?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("tag")?.takeIf(String::isNotBlank) } }.orEmpty()
        val cover = "https://atn.gold-usergeneratedcontent.net/webpsmalltn/${hash.last()}/${hash.takeLast(3).take(2)}/$hash.webp"
        return ComicSummary(ComicKey(source, id), title, artists.joinToString("、"), cover, tags, data.optString("language").takeIf(String::isNotBlank), pageCount = files.length())
    }
    companion object { const val PAGE_SIZE = 25 }
}
