package io.github.ddmoyu.picomic.source.ht

import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.network.origin
import io.github.ddmoyu.picomic.source.html.*
import okhttp3.HttpUrl
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HtSource(private val client: HtClient) : ComicSource {
    override val source = Source.HTCOMIC
    private val base get() = client.base
    private val categoryIds = SourceCategories.ht
    override suspend fun categories() = categoryIds.keys.toList()
    override suspend fun search(query: ContentQuery): ContentPage<ComicSummary> {
        require(query.page in 1..10000)
        val url = if (query.keyword.isNotBlank()) base.resolve("search/")!!.newBuilder().addQueryParameter("q", query.keyword)
            .addQueryParameter("f", "_all").addQueryParameter("s", "create_time_DESC").addQueryParameter("syn", "yes").addQueryParameter("p", query.page.toString()).build()
        else base.resolve("albums-index-page-${query.page}${query.category?.let { "-cate-${categoryIds[it] ?: throw parseChanged(HtClient.TITLE)}" }.orEmpty()}.html")!!
        val document = client.document(url)
        val container = document.selectFirst(".gallary_wrap") ?: throw parseChanged(HtClient.TITLE)
        val items = container.select("li.gallary_item").map { item ->
            val anchor = item.selectFirst(".title a") ?: throw parseChanged(HtClient.TITLE)
            val link = base.resolve(anchor.attr("href")) ?: throw parseChanged(HtClient.TITLE)
            if (link.origin() != base.origin()) throw parseChanged(HtClient.TITLE)
            val id = Regex("/photos-index(?:-page-[0-9]+)?-aid-([1-9][0-9]{0,11})\\.html").matchEntire(link.encodedPath)?.groupValues?.get(1) ?: throw parseChanged(HtClient.TITLE)
            val title = anchor.text().trim().takeIf { it.isNotEmpty() } ?: throw parseChanged(HtClient.TITLE)
            ComicSummary(ComicKey(source, id), title, cover = cover(item.selectFirst(".pic_box img")), pageCount = Regex("([0-9]+)\\s*[張张頁页P]").find(item.select(".info_col").text())?.groupValues?.get(1)?.toIntOrNull())
        }
        if (items.map { it.key }.distinct().size != items.size) throw parseChanged(HtClient.TITLE)
        if (items.isEmpty() && document.selectFirst(".result, .demo-error, .text-error") == null) throw parseChanged(HtClient.TITLE)
        val more = document.select(".paginator a").any { anchor -> base.resolve(anchor.attr("href"))?.let { next ->
            next.origin() == base.origin() && ((next.queryParameter("p")?.toIntOrNull() ?: Regex("-page-([0-9]+)").find(next.encodedPath)?.groupValues?.get(1)?.toIntOrNull() ?: 1) > query.page)
        } == true }
        return ContentPage(items, if (more) query.page + 1 else null)
    }
    override suspend fun details(id: String): ComicDetails {
        positiveId(id, HtClient.TITLE)
        val document = client.document(base.resolve("photos-index-page-1-aid-$id.html")!!)
        val title = document.selectFirst(".userwrap > h2")?.text()?.takeIf(String::isNotBlank) ?: throw parseChanged(HtClient.TITLE)
        val labels = document.select(".uwconn label").text()
        val pages = Regex("[頁页][數数][:：]\\s*([0-9]+)").find(labels)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..20000 } ?: throw parseChanged(HtClient.TITLE)
        val tags = document.select("a.tagshow").map { it.text() }.filter(String::isNotBlank).distinct()
        val summary = ComicSummary(ComicKey(source, id), title, document.select(".uwuinfo > a > p").text(), cover(document.selectFirst(".uwthumb img")), tags, pageCount = pages)
        return ComicDetails(summary, document.select(".uwconn > p").text(), listOf(Chapter("whole", "全册", 1, pages)))
    }
    override suspend fun pages(comicId: String, chapter: Chapter): List<PageRef> {
        positiveId(comicId, HtClient.TITLE)
        if (chapter.id != "whole") throw parseChanged(HtClient.TITLE)
        val script = client.text(base.resolve("photos-item-aid-$comicId.html")!!)
        if (script.indexOf("mReader.initData(") != script.lastIndexOf("mReader.initData(")) throw parseChanged(HtClient.TITLE)
        val data = try { JSONObject(withoutTrailingCommas(jsonArgument(script, "mReader.initData(", HtClient.TITLE))) } catch (_: Exception) { throw parseChanged(HtClient.TITLE) }
        val urls = data.optJSONArray("page_url") ?: throw parseChanged(HtClient.TITLE)
        if (urls.length() !in 1..20000 || (chapter.pageCount != null && chapter.pageCount != urls.length())) throw parseChanged(HtClient.TITLE)
        val result = (0 until urls.length()).map { index ->
            val raw = urls.getString(index).let { if (it.startsWith("http://")) "https://" + it.removePrefix("http://") else it }
            val url = secureUrl(base, raw, HtClient.TITLE).also(HtClient::validateImage)
            PageRef((index + 1).toString(), index, url.toString(), resolver = "ht")
        }
        if (result.map { it.url }.distinct().size != result.size) throw parseChanged(HtClient.TITLE)
        return result
    }
    override suspend fun image(page: PageRef): ByteArray = client.image(secureUrl(base, page.url, HtClient.TITLE))
    private fun cover(element: Element?): String? = element?.let { image ->
        listOf("data-src", "data-original", "data-lazyload", "src").firstNotNullOfOrNull { attribute -> image.attr(attribute).takeIf(String::isNotBlank) }
            ?.let { secureUrl(base, it, HtClient.TITLE).also(HtClient::validateImage).toString() }
    }
}
