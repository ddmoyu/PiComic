package io.github.ddmoyu.picomic

import io.github.ddmoyu.picomic.reader.ReaderMath
import org.junit.Assert.*
import org.junit.Test

class ReaderMathTest {
    @Test fun prefetchWindowHonorsChapterBoundsAndNeverIncludesCurrentPage() {
        assertEquals(listOf(2, 3, 4), ReaderMath.prefetchPages(1, 3, 24))
        assertEquals(listOf(23), ReaderMath.prefetchPages(24, 10, 24))
        assertEquals(11, ReaderMath.prefetchPages(12, 30, 100).size)
        assertFalse(ReaderMath.prefetchPages(12, 10, 100).contains(12))
        assertTrue(ReaderMath.prefetchPages(0, 3, 24).isEmpty())
    }
    @Test fun progressUsesImageFractionAcrossScreenSizes() {
        val ratio = ReaderMath.ratio(375, 1500)
        assertEquals(.25f, ratio, .00001f)
        assertEquals(750, ReaderMath.offset(ratio, 3000))
        assertEquals(0, ReaderMath.offset(Float.NaN, 1500))
        assertEquals(0f, ReaderMath.ratio(10, 0), 0f)
    }
    @Test fun autoTimerRejectsInvalidAndUnboundedIntervals() {
        assertEquals(5000L, ReaderMath.intervalMillis("invalid"))
        assertEquals(2000L, ReaderMath.intervalMillis("-1 秒"))
        assertEquals(60000L, ReaderMath.intervalMillis("999 秒"))
    }
}
