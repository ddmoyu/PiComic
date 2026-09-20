package io.github.ddmoyu.picomic.content

import io.github.ddmoyu.picomic.data.Source
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ContentControllerTest {
    private fun item(id: String, source: Source = Source.PICACG) = ComicSummary(ComicKey(source, id), "作品 $id")
    @Test fun sourceSwitchDiscardsLateResultsEvenIfTransportIgnoresCancellation() = runBlocking {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val controller = ContentListController(this) { source, _ ->
            if (source == Source.PICACG) withContext(NonCancellable) { started.complete(Unit); release.await() }
            ContentPage(listOf(item(source.name, source)))
        }
        controller.load(Source.PICACG, ContentQuery("old")); started.await()
        controller.load(Source.JMCOMIC, ContentQuery("new"))
        withTimeout(3000) { controller.state.first { it.loaded } }
        release.complete(Unit); yield(); delay(20)
        assertEquals(listOf(Source.JMCOMIC), controller.state.value.items.map { it.key.source })
    }
    @Test fun paginationDeduplicatesStableIdsAndRetryKeepsEarlierPages() = runBlocking {
        var fail = true
        val requests = mutableListOf<Int>()
        val controller = ContentListController(this) { _, query ->
            requests += query.page
            if (query.page == 1) ContentPage(listOf(item("a")), 2)
            else if (fail) { fail = false; throw ContentFailure(ContentFailureKind.NETWORK, "断网") }
            else ContentPage(listOf(item("a"), item("b")))
        }
        controller.load(Source.PICACG, ContentQuery()); controller.state.first { it.loaded }
        controller.more(); controller.state.first { it.error != null }
        assertEquals(listOf("a"), controller.state.value.items.map { it.key.id })
        controller.retry(); controller.state.first { !it.loading }
        assertEquals(listOf("a", "b"), controller.state.value.items.map { it.key.id })
        assertEquals(listOf(1, 2, 2), requests); assertNull(controller.state.value.nextPage)
    }
    @Test fun invalidPaginationAndWrongSourceAreErrorsNotEmptySuccess() = runBlocking {
        val controller = ContentListController(this) { _, _ -> ContentPage(listOf(item("wrong", Source.JMCOMIC)), 1) }
        controller.load(Source.PICACG, ContentQuery()); controller.state.first { !it.loading }
        assertNotNull(controller.state.value.error); assertFalse(controller.state.value.loaded)
    }
    @Test fun stableKeysDoNotMergeSameIdsAcrossSourcesAndFilterNormalizesFullWidthText() {
        assertNotEquals(ComicKey(Source.PICACG, "abc").stable, ComicKey(Source.JMCOMIC, "abc").stable)
        val comic = ComicSummary(ComicKey(Source.PICACG, "abc"), "ＡＢＣ 漫画", tags = listOf("日常"), language = "Chinese")
        assertFalse(ContentFilter.accepts(comic, listOf("abc"), emptySet()))
        assertFalse(ContentFilter.accepts(comic, emptyList(), setOf("English")))
        assertTrue(ContentFilter.accepts(comic, emptyList(), setOf("CHINESE")))
    }
}
