package io.github.ddmoyu.picomic

import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ContentDetailCacheTest {
    private val key = ComicKey(Source.JMCOMIC, "fixture")
    private fun detail(key: ComicKey = this.key, title: String = "测试") = ComicDetails(ComicSummary(key, title), "", listOf(Chapter("1", "第一话", 1)))

    @Test fun detailAndReaderShareOneFetchAndChangedAccountOrSettingsCannotHitOldData() = runBlocking {
        val cache = ContentDetailCache()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val first = async { cache.load(key, "account-1/route-1") { calls++; release.await(); detail() } }
        val reader = async { cache.load(key, "account-1/route-1") { calls++; detail() } }
        yield(); release.complete(Unit)
        assertSame(first.await(), reader.await())
        assertEquals(1, calls)
        assertSame(first.await(), cache.peek(key, "account-1/route-1"))
        assertNull(cache.peek(key, "account-2/route-1"))
        cache.load(key, "account-2/route-1") { calls++; detail(title = "新账号") }
        assertNull(cache.peek(key, "account-1/route-1"))
        cache.load(key, "account-2/route-2") { calls++; detail(title = "新线路") }
        assertEquals(3, calls)
    }

    @Test fun explicitRefreshReplacesDataAndFailedOrCancelledRefreshKeepsLastSuccess() = runBlocking {
        val cache = ContentDetailCache()
        cache.load(key, "context") { detail() }
        cache.load(key, "context", force = true) { detail(title = "已更新") }
        assertEquals("已更新", cache.peek(key, "context")!!.summary.title)
        assertTrue(runCatching { cache.load(key, "context", force = true) { error("offline") } }.isFailure)
        val started = CompletableDeferred<Unit>()
        val task = launch { cache.load(key, "context", force = true) { started.complete(Unit); awaitCancellation() } }
        started.await(); task.cancelAndJoin()
        assertEquals("已更新", cache.peek(key, "context")!!.summary.title)
    }

    @Test fun memoryCacheIsBoundedAndSeparatesPlatforms() = runBlocking {
        val cache = ContentDetailCache(capacity = 2)
        val second = ComicKey(Source.PICACG, key.id)
        val third = ComicKey(Source.JMCOMIC, "another")
        cache.load(key, "c") { detail() }
        cache.load(second, "c") { detail(second) }
        assertEquals(Source.PICACG, cache.peek(second, "c")!!.summary.key.source)
        cache.peek(key, "c")
        cache.load(third, "c") { detail(third) }
        assertNull(cache.peek(second, "c"))
        assertNotNull(cache.peek(key, "c"))
    }
}
