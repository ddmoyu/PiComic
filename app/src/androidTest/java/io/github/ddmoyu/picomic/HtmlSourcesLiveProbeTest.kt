package io.github.ddmoyu.picomic

import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.network.*
import io.github.ddmoyu.picomic.source.eh.*
import io.github.ddmoyu.picomic.source.ht.*
import io.github.ddmoyu.picomic.source.hitomi.*
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit metadata-only probe. Never loads covers, comic images, or account credentials. */
class HtmlSourcesLiveProbeTest {
    private fun engine(): NetworkEngine {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSourceProbe") == "true")
        return NetworkEngine(NetworkProfile.HttpProxy("10.0.2.2", 7897))
    }
    @Test fun hitomiCurrentIndexRulesAndGallerySchema() = runBlocking {
        val client = HitomiClient(engine())
        client.rules()
        val range = client.range(listOf("index-all.nozomi"), 0, 4)
        val id = HitomiProtocol.ids(range.bytes).single().toString()
        val source = HitomiSource(client); val details = source.details(id); val pages = source.pages(id, details.chapters.single())
        assertEquals(details.summary.pageCount, pages.size); assertTrue(pages.all { it.url.startsWith("https://w") })
        assertTrue(client.searchIds("picomic_fixture_unmatched_20260920", "all").isEmpty())
    }
    @Test fun htPublishedRouteAndReaderMetadata() = runBlocking {
        val base = "https://www.wn10.shop/".toHttpUrl()
        val api = HtClient(engine(), base, setOf(base.origin())); api.probe()
        val source = HtSource(api); val list = source.search(ContentQuery()); assertTrue(list.items.isNotEmpty())
        val details = source.details(list.items.first().key.id)
        assertEquals(details.summary.pageCount, source.pages(details.summary.key.id, details.chapters.single()).size)
    }
    @Test fun ehAnonymousSearchSchema() = runBlocking {
        assertTrue(EhSource(EhClient(engine())).search(ContentQuery("PiComic_fixture_no_result_20260920")).items.isEmpty())
    }
}
