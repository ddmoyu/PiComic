package io.github.ddmoyu.picomic.source.eh

import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.network.origin
import io.github.ddmoyu.picomic.source.html.*
import okhttp3.HttpUrl
import org.jsoup.nodes.Document
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EhGalleryId(val ex: Boolean, val gid: String, val token: String) {
    val value get() = "${if (ex) "ex" else "eh"}:$gid:$token"
    companion object {
        fun parse(value: String): EhGalleryId {
            val match = Regex("(eh|ex):([1-9][0-9]{0,11}):([a-f0-9]{10})").matchEntire(value) ?: throw parseChanged(EhClient.TITLE)
            return EhGalleryId(match.groupValues[1] == "ex", match.groupValues[2], match.groupValues[3])
        }
    }
}
/** Serializing EH image resolution prevents queued prefetch from continuing after a quota response. */
class EhImageGate {
    private val mutex = Mutex()
    private val blocked = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    suspend fun <T> run(account: String = "anonymous", action: suspend () -> T): T = mutex.withLock {
        if (account in blocked) throw EhClient.quota()
        try { action() } catch (e: ContentFailure) { if (e.kind == ContentFailureKind.QUOTA) blocked += account; throw e }
    }
    fun retry() { blocked.clear() }
}
class EhSource(private val client: EhClient, private val ex: Boolean = false, private val original: Boolean = false,
    private val subtitle: Boolean = false, private val images: EhImageGate = EhImageGate()) : ComicSource {
    override val source = Source.EHENTAI
    private val categories = linkedMapOf("同人志" to 2, "漫画" to 4, "画师 CG" to 8, "游戏 CG" to 16, "西方" to 512, "普通内容" to 256, "图集" to 32, "角色扮演" to 64, "其他" to 1)
    private var detailCache: Pair<String, Document>? = null
    override suspend fun categories() = categories.keys.toList()
    override suspend fun search(query: ContentQuery): ContentPage<ComicSummary> {
        val base = client.site(ex)
        val builder = base.newBuilder().addQueryParameter("f_search", query.keyword)
        query.category?.let { builder.addQueryParameter("f_cats", (1023 xor (categories[it] ?: throw parseChanged(EhClient.TITLE))).toString()) }
        if (query.page > 1) {
            val cursor = query.cursor?.takeIf { it.matches(Regex("[0-9]{1,12}(?:-[0-9]{1,4})?")) } ?: throw parseChanged(EhClient.TITLE)
            builder.addQueryParameter("next", cursor)
        }
        val document = client.document(builder.build())
        val titles = document.select(".itg .glink")
        val result = titles.map { title ->
            val anchor = title.closest("a[href]") ?: throw parseChanged(EhClient.TITLE)
            val url = base.resolve(anchor.attr("href")) ?: throw parseChanged(EhClient.TITLE)
            val id = fromUrl(url)
            if (url.origin() != base.origin()) throw parseChanged(EhClient.TITLE)
            val row = title.closest("tr") ?: title.closest(".gl1t") ?: title.parent() ?: throw parseChanged(EhClient.TITLE)
            val image = row.selectFirst("img[data-src], img[src]")
            val cover = image?.let { it.attr("data-src").ifBlank { it.attr("src") } }?.takeIf(String::isNotBlank)?.let { secureUrl(base, it, EhClient.TITLE).also(EhClient::validateImage).toString() }
            val tags = row.select(".gt, .gtl").map { it.attr("title").ifBlank { it.text() } }.filter(String::isNotBlank).distinct()
            val language = tags.firstOrNull { it.startsWith("language:") }?.substringAfter(':')
            ComicSummary(ComicKey(source, id.value), title.text().takeIf(String::isNotBlank) ?: throw parseChanged(EhClient.TITLE), cover = cover, tags = tags, language = language)
        }
        if (result.isEmpty() && !document.text().contains("No hits found", true) && !document.text().contains("No unfiltered results", true)) throw parseChanged(EhClient.TITLE)
        val next = document.selectFirst("a#dnext[href]")?.let { base.resolve(it.attr("href")) ?: throw parseChanged(EhClient.TITLE) }
        if (next != null && (next.origin() != base.origin() || next.encodedPath != "/")) throw parseChanged(EhClient.TITLE)
        val cursor = next?.queryParameter("next")?.takeIf { it.matches(Regex("[0-9]{1,12}(?:-[0-9]{1,4})?")) }
        if (next != null && (cursor == null || cursor == query.cursor)) throw parseChanged(EhClient.TITLE)
        return ContentPage(result.distinctBy { it.key }, if (cursor == null) null else query.page + 1, cursor)
    }
    override suspend fun details(id: String): ComicDetails {
        val key = EhGalleryId.parse(id)
        val url = gallery(key)
        val page = client.document(url)
        val main = page.selectFirst("#gn")?.text()?.takeIf(String::isNotBlank) ?: throw parseChanged(EhClient.TITLE)
        val alt = page.selectFirst("#gj")?.text().orEmpty()
        val count = page.select("#gdd .gdt2").firstNotNullOfOrNull { Regex("([0-9]+)\\s+pages?").find(it.text())?.groupValues?.get(1)?.toIntOrNull() }?.takeIf { it in 1..20000 } ?: throw parseChanged(EhClient.TITLE)
        val tags = page.select("#taglist tr").flatMap { row ->
            val name = row.selectFirst("td")?.text()?.removeSuffix(":").orEmpty()
            row.select("a[id^=ta_]").map { "$name:${it.text()}" }
        }
        val imageStyle = page.selectFirst("#gd1 > div")?.attr("style").orEmpty()
        val cover = Regex("url\\(['\"]?([^)'\"]+)['\"]?\\)").find(imageStyle)?.groupValues?.get(1)?.let { secureUrl(url, it, EhClient.TITLE).also(EhClient::validateImage).toString() }
        val summary = ComicSummary(ComicKey(source, id), main, page.select("#gdn a").text(), cover, tags,
            tags.firstOrNull { it.startsWith("language:") }?.substringAfter(':'), pageCount = count, subtitle = alt.takeIf(String::isNotBlank))
        detailCache = id to page
        return ComicDetails(summary, listOf(alt.takeIf { it != summary.title }.orEmpty(), page.select("#gdd").text()).filter(String::isNotBlank).joinToString("\n"), listOf(Chapter("whole", "全册", 1, count)))
    }
    override suspend fun pages(comicId: String, chapter: Chapter): List<PageRef> {
        if (chapter.id != "whole") throw parseChanged(EhClient.TITLE)
        val key = EhGalleryId.parse(comicId)
        val count = chapter.pageCount ?: details(comicId).summary.pageCount!!
        val pages = sortedMapOf<Int, PageRef>()
        var index = 0
        do {
            if (index > 1000) throw parseChanged(EhClient.TITLE)
            val url = gallery(key).newBuilder().addQueryParameter("p", index.toString()).build()
            val document = if (index == 0 && detailCache?.first == comicId) detailCache!!.second else client.document(url)
            val anchors = document.select("#gdt a[href]")
            if (anchors.isEmpty()) throw parseChanged(EhClient.TITLE)
            val before = pages.size
            for (anchor in anchors) {
                val link = client.site(key.ex).resolve(anchor.attr("href")) ?: throw parseChanged(EhClient.TITLE)
                if (link.origin() != client.site(key.ex).origin()) throw parseChanged(EhClient.TITLE)
                val match = Regex("/s/([a-f0-9]{10})/${key.gid}-([0-9]+)").matchEntire(link.encodedPath) ?: throw parseChanged(EhClient.TITLE)
                val number = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..count } ?: throw parseChanged(EhClient.TITLE)
                val page = PageRef(number.toString(), number - 1, link.toString(), resolver = if (original) "eh-original" else "eh")
                if (pages[number]?.let { it != page } == true) throw parseChanged(EhClient.TITLE)
                pages[number] = page
            }
            if (pages.size == before) throw parseChanged(EhClient.TITLE)
            index++
        } while (pages.size < count)
        if (pages.keys.toList() != (1..count).toList()) throw parseChanged(EhClient.TITLE)
        return pages.values.toList()
    }
    override suspend fun image(page: PageRef): ByteArray = images.run(client.accountKey) {
        val url = client.eh.resolve(page.url) ?: throw parseChanged(EhClient.TITLE)
        if (url.origin() !in setOf(client.eh.origin(), client.ex.origin()) || !url.encodedPath.matches(Regex("/s/[a-f0-9]{10}/[1-9][0-9]{0,11}-[1-9][0-9]{0,5}"))) throw parseChanged(EhClient.TITLE)
        val document = client.document(url)
        val raw = if (page.resolver == "eh-original") document.selectFirst("#i7 a[href*=fullimg.php], a[href*=fullimg.php]")?.attr("href") ?: throw EhClient.originalUnavailable()
            else document.selectFirst("img#img, #i3 img")?.attr("src") ?: throw parseChanged(EhClient.TITLE)
        val image = client.eh.resolve(raw) ?: throw parseChanged(EhClient.TITLE)
        client.image(image, url, page.resolver == "eh-original")
    }
    private fun gallery(id: EhGalleryId) = client.site(id.ex).resolve("g/${id.gid}/${id.token}/")!!
    private fun fromUrl(url: HttpUrl): EhGalleryId {
        if (url.origin() !in setOf(client.eh.origin(), client.ex.origin()) || url.query != null || url.fragment != null) throw parseChanged(EhClient.TITLE)
        val match = Regex("/g/([1-9][0-9]{0,11})/([a-f0-9]{10})/").matchEntire(url.encodedPath) ?: throw parseChanged(EhClient.TITLE)
        return EhGalleryId(url.origin() == client.ex.origin(), match.groupValues[1], match.groupValues[2])
    }
}
