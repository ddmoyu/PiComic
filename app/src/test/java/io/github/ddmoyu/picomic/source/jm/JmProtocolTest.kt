package io.github.ddmoyu.picomic.source.jm

import io.github.ddmoyu.picomic.content.ContentFailure
import org.junit.Assert.*
import org.junit.Test

class JmProtocolTest {
    @Test fun publicProfileMatchesIndependentCipherVector() {
        assertEquals("880c64833265ad47a928afcf1b1220f5", JmProtocol.token(1700000000))
        assertEquals("{\"uid\":42,\"username\":\"fixture\"}", JmProtocol.decode(1700000000, "dSRfzlvKYpVwQvqQB+zl/m4QR7n3/DGg5m9WHd/6NCs="))
        assertTrue(runCatching { JmProtocol.decode(1700000001, "dSRfzlvKYpVwQvqQB+zl/m4QR7n3/DGg5m9WHd/6NCs=") }.isFailure)
    }
    @Test fun imageRowsAreReversedAsStripsAndRemainderMovesWithLastStrip() {
        assertEquals(listOf(JmStrips.Strip(8, 0, 5), JmStrips.Strip(4, 5, 4), JmStrips.Strip(0, 9, 4)), JmStrips.layout(13, 3))
        for (height in 20..97) for (count in 2..20 step 2) {
            val rows = JmStrips.layout(height, count)
            assertEquals((0 until height).toList(), rows.flatMap { (it.targetY until it.targetY + it.height).toList() })
            assertEquals((0 until height).toList(), rows.flatMap { (it.sourceY until it.sourceY + it.height).toList() }.sorted())
        }
    }
    @Test fun gifPlainAndLegacyImagesUseDifferentRules() {
        assertEquals(0, JmStrips.count(200000, 220980, "00001.jpg"))
        assertEquals(0, JmStrips.count(500000, 220980, "00001.GIF"))
        assertEquals(0, JmStrips.count(500000, 220980, "00001.jpg", true))
        assertEquals(10, JmStrips.count(220980, 220980, "00001.jpg"))
        assertEquals(10, JmStrips.count(268849, 220980, "00001.jpg"))
        for (photo in listOf(268850L, 421925L, 421926L, 600000L)) {
            val divisor = if (photo >= 421926) 8 else 10
            assertEquals(JmProtocol.md5("${photo}00001").last().code % divisor * 2 + 2, JmStrips.count(photo, 220980, "00001.jpg"))
        }
    }
    @Test fun untrustedHostsAndTraversalFilenamesAreRejected() {
        for (host in listOf("http://cdn-msp.jmapiproxy3.cc", "https://cdn-msp.jmapiproxy3.cc.evil.test", "https://localhost", "https://cdn-msp.jmapiproxy3.cc:8443", "https://user@cdn-msp.jmapiproxy3.cc"))
            assertTrue(runCatching { JmProtocol.imageHost(host) }.exceptionOrNull() is ContentFailure)
        for (name in listOf("../image.jpg", "%2e%2e.jpg", "image.jpg?token=secret", "image.svg")) assertTrue(runCatching { JmProtocol.filename(name) }.isFailure)
        assertEquals("cdn-msp3.jmapiproxy3.cc", JmProtocol.imageHost("https://cdn-msp3.jmapiproxy3.cc").host)
    }
    @Test fun corruptedCiphertextFailsClosed() {
        for (value in listOf("", "abcd", "AA==", "AAAAAAAAAAAAAAAAAAAAAA==")) assertTrue(runCatching { JmProtocol.decode(1700000000, value) }.exceptionOrNull() is ContentFailure)
    }
}
