package io.github.ddmoyu.picomic

import io.github.ddmoyu.picomic.reader.ReaderMath
import io.github.ddmoyu.picomic.reader.ReaderPrefetcher
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ReaderPrefetcherTest {
    @Test fun turningPagesKeepsDownloadsAndContinuouslyFillsThreeAhead() = runBlocking {
        val started = mutableListOf<Int>()
        val cancelled = mutableListOf<Int>()
        val gates = (1..10).associateWith { CompletableDeferred<Unit>() }
        val loader = ReaderPrefetcher<Int>(this) { page ->
            started += page
            try { gates.getValue(page).await(); true }
            catch (e: CancellationException) { cancelled += page; throw e }
        }
        try {
            loader.update(ReaderMath.prefetchPages(1, 3, 10), setOf(1))
            yield()
            assertEquals(listOf(2, 3), started)
            loader.update(ReaderMath.prefetchPages(2, 3, 10), setOf(2))
            yield()
            assertTrue(cancelled.isEmpty())
            assertEquals(listOf(2, 3), started)
            gates.getValue(2).complete(Unit)
            withTimeout(1000) { while (4 !in started) yield() }
            assertTrue(4 in started)
            gates.getValue(3).complete(Unit)
            withTimeout(1000) { while (5 !in started) yield() }
            assertTrue(5 in started)
            gates.values.forEach { it.complete(Unit) }; yield()
            loader.update(ReaderMath.prefetchPages(2, 3, 10), setOf(2)); yield()
            assertEquals(1, started.count { it == 2 })
            assertEquals(1, started.count { it == 3 })
            assertEquals(1, started.count { it == 4 })
            assertEquals(1, started.count { it == 5 })
        } finally { loader.clear() }
    }

    @Test fun fastJumpKeepsStartedDownloadsButReplacesStaleQueuedPages() = runBlocking {
        val started = mutableListOf<Int>()
        val cancelled = mutableListOf<Int>()
        val gate = CompletableDeferred<Unit>()
        val loader = ReaderPrefetcher<Int>(this) { page ->
            started += page
            try { gate.await(); true }
            catch (e: CancellationException) { cancelled += page; throw e }
        }
        try {
            loader.update(listOf(2, 3, 4), setOf(1)); yield()
            loader.update(listOf(21, 22, 23), setOf(20)); yield()
            assertEquals(listOf(2, 3), started)
            assertTrue(cancelled.isEmpty())
            gate.complete(Unit)
            withTimeout(1000) { while (started.size < 5) yield() }
            assertEquals(listOf(2, 3, 21, 22, 23), started)
            assertTrue(cancelled.isEmpty())
        } finally { loader.clear() }
    }

    @Test fun leavingReaderCancelsWorkAndResumeCanStartAgain() = runBlocking {
        var starts = 0
        var cancellations = 0
        val loader = ReaderPrefetcher<Int>(this) {
            starts++
            try { awaitCancellation() } finally { cancellations++ }
        }
        loader.update(listOf(2, 3, 4), setOf(1)); yield()
        loader.clear(); yield()
        assertEquals(2, cancellations)
        loader.update(listOf(2, 3, 4), setOf(1)); yield()
        assertEquals(4, starts)
        loader.clear()
    }

    @Test fun failedPrefetchCanRetryOnTheNextWindowUpdate() = runBlocking {
        var attempts = 0
        val loader = ReaderPrefetcher<Int>(this) { ++attempts > 1 }
        repeat(3) { loader.update(listOf(2), setOf(1)); yield() }
        assertEquals(2, attempts)
        loader.clear()
    }

    @Test fun visibleShortPagesDoNotConsumeTheThreePagesAhead() {
        assertEquals(listOf(5, 6, 7), ReaderMath.prefetchPages(1, 3, 10, lastVisible = 4))
        assertEquals(listOf(9, 10, 3), ReaderMath.prefetchPages(4, 3, 10, lastVisible = 8))
        assertEquals(listOf(7), ReaderMath.prefetchPages(8, 3, 10, lastVisible = 10))
    }
}
