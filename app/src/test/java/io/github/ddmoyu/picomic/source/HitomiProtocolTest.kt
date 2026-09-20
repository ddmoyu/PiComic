package io.github.ddmoyu.picomic.source

import io.github.ddmoyu.picomic.source.hitomi.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class HitomiProtocolTest {
    private fun script(cases: String = "case 4078:") = """'use strict';
gg = { m: function(g) { var o = 1; switch (g) { $cases o = 0; break; } return o; },
s: function(h) { var m = /(..)(.)$/.exec(h); return parseInt(m[2]+m[1], 16).toString(10); }, b: '1789876802/' };"""
    @Test fun rulesAreBoundedDataAndPreserveInitialValueAcrossRefresh() {
        val hash = "0".repeat(61) + "abc"
        val parsed = HitomiImageRules.parse(script("case 3243:"))
        assertEquals("https://w1.gold-usergeneratedcontent.net/1789876802/3243/$hash.webp", parsed.image(hash))
        assertTrue(HitomiImageRules.parse(script()).image(hash).startsWith("https://w2."))
        assertTrue(runCatching { HitomiImageRules.parse(script().replace("return o;", "return execute(o);")) }.isFailure)
        assertTrue(runCatching { HitomiImageRules.parse(script() + "evil();") }.isFailure)
    }
    @Test fun maximumSwitchDoesNotOverflowRegexStack() {
        val rules = HitomiImageRules.parse(script((0..4095).joinToString("\n") { "case $it:" }))
        assertEquals(4096, rules.cases.size)
        assertTrue(runCatching { HitomiImageRules.parse(script("case 4096:")) }.isFailure)
    }
    @Test fun nodeOffsetsAre64BitAndInvalidLengthsFail() {
        val buffer = ByteBuffer.allocate(464).putInt(1).putInt(4).put(byteArrayOf(1, 2, 3, 4)).putInt(1).putLong(4_500_000_000L).putInt(12)
        repeat(17) { buffer.putLong(0) }
        assertEquals(4_500_000_000L, HitomiProtocol.node(buffer.array()).data.single().offset)
        buffer.putInt(24, Int.MAX_VALUE)
        assertTrue(runCatching { HitomiProtocol.node(buffer.array()) }.isFailure)
        assertTrue(runCatching { HitomiProtocol.node(byteArrayOf()) }.isFailure)
    }
    @Test fun idCountsUnsignedOrderingAndIntersectionAreExact() {
        assertTrue(HitomiProtocol.compare(byteArrayOf(0x80.toByte()), byteArrayOf(0x7f)) > 0)
        assertArrayEquals(intArrayOf(42, 7), HitomiProtocol.ids(ByteBuffer.allocate(12).putInt(2).putInt(42).putInt(7).array(), true))
        assertArrayEquals(intArrayOf(9, 2), HitomiProtocol.intersect(intArrayOf(2, 9, 9, 4), intArrayOf(9, 2, 6)))
        assertTrue(runCatching { HitomiProtocol.ids(ByteBuffer.allocate(8).putInt(2).putInt(42).array(), true) }.isFailure)
        assertTrue(runCatching { HitomiProtocol.ids(byteArrayOf(0, 1)) }.isFailure)
    }
}
