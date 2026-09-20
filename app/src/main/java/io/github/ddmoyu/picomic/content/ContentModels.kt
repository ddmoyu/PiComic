package io.github.ddmoyu.picomic.content

import io.github.ddmoyu.picomic.data.Source
import java.io.IOException
import java.text.Normalizer
import java.util.Locale

data class ComicKey(val source: Source, val id: String) {
    init { require(id.isNotBlank() && id.length <= 512 && id.none { it.isISOControl() }) }
    val stable: String get() = "${source.name}:${id.length}:$id"
}
data class ComicSummary(
    val key: ComicKey, val title: String, val author: String = "", val cover: String? = null,
    val tags: List<String> = emptyList(), val language: String? = null, val chapterCount: Int? = null,
    val pageCount: Int? = null, val subtitle: String? = null
)
data class ComicDetails(val summary: ComicSummary, val description: String, val chapters: List<Chapter>)
data class Chapter(val id: String, val title: String, val order: Int, val pageCount: Int? = null)
data class JmImageRule(val photoId: Long, val scrambleId: Long, val filename: String, val alreadyDecoded: Boolean = false, val version: Int = 1)
data class PageRef(val id: String, val index: Int, val url: String, val width: Int? = null, val height: Int? = null, val jm: JmImageRule? = null, val resolver: String? = null)
data class ContentPage<T>(val items: List<T>, val nextPage: Int? = null, val nextCursor: String? = null)
data class ContentQuery(val keyword: String = "", val category: String? = null, val sort: String = "dd", val page: Int = 1, val cursor: String? = null, val randomSeed: Long = 0)
data class ContentProgress(
    val key: ComicKey, val chapterId: String, val pageId: String, val page: Int,
    val offset: Float, val mode: String, val updatedAt: Long = System.currentTimeMillis()
)
enum class ContentFailureKind { LOGIN, EXPIRED, ACCESS_DENIED, WARNING, ORIGINAL_UNAVAILABLE, QUOTA, UNSUPPORTED, NOT_FOUND, PARSE, LIMIT, NETWORK }
class ContentFailure(val kind: ContentFailureKind, message: String) : IOException(message)
interface ComicSource {
    val source: Source
    suspend fun search(query: ContentQuery): ContentPage<ComicSummary>
    suspend fun categories(): List<String>
    suspend fun details(id: String): ComicDetails
    suspend fun pages(comicId: String, chapter: Chapter): List<PageRef>
    suspend fun image(page: PageRef): ByteArray = throw ContentFailure(ContentFailureKind.UNSUPPORTED, "此来源无需解析图片页")
}

object ContentFilter {
    fun normalize(value: String) = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
    fun accepts(comic: ComicSummary, blocked: List<String>, languages: Set<String>, keepUnknown: Boolean = false): Boolean {
        val text = normalize(listOf(comic.title, comic.author, comic.tags.joinToString(" ")).joinToString(" "))
        return blocked.none { it.isNotBlank() && text.contains(normalize(it)) } &&
            (languages.isEmpty() || (comic.language == null && keepUnknown) || comic.language?.let { language -> languages.any { languageCode(it) == languageCode(language) } } == true)
    }
    fun languageCode(value: String) = when (normalize(value)) { "chinese", "中文", "汉语", "zh", "zh-cn", "zh-tw" -> "zh"; "english", "英文", "en" -> "en"; "japanese", "日文", "ja" -> "ja"; else -> "unknown" }
}
