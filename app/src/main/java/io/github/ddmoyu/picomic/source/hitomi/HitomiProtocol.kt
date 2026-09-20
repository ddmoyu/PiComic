package io.github.ddmoyu.picomic.source.hitomi

import io.github.ddmoyu.picomic.source.html.parseChanged
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

data class HitomiIndexData(val offset: Long, val length: Int)
data class HitomiNode(val keys: List<ByteArray>, val data: List<HitomiIndexData>, val children: List<Long>)
data class HitomiImageRules(val prefix: String, val initial: Int, val branch: Int, val cases: Set<Int>) {
    fun image(hash: String): String {
        requireHash(hash)
        val bucket = (hash.last() + hash.takeLast(3).take(2)).toInt(16)
        val shard = 1 + if (bucket in cases) branch else initial
        return "https://w$shard.gold-usergeneratedcontent.net/$prefix$bucket/$hash.webp"
    }
    companion object {
        fun requireHash(hash: String) { if (!hash.matches(Regex("[a-f0-9]{64}"))) throw parseChanged("Hitomi") }
        fun parse(script: String): HitomiImageRules {
            if (script.length > 128 * 1024) throw parseChanged("Hitomi")
            // Accept only the observed two-valued switch and hash-bucket function; never evaluate JS.
            val pattern = Regex("""(?:'use strict';\s*)?gg\s*=\s*\{\s*m:\s*function\(g\)\s*\{\s*var o\s*=\s*([01]);\s*switch\s*\(g\)\s*\{\s*(.*?)o\s*=\s*([01]);\s*break;\s*\}\s*return o;\s*\},\s*s:\s*function\(h\)\s*\{\s*var m\s*=\s*/\(\.\.\)\(\.\)\$/\.exec\(h\);\s*return parseInt\(m\[2\]\+m\[1\],\s*16\)\.toString\(10\);\s*\},\s*b:\s*'([0-9]{1,16}/)'\s*\};?""", RegexOption.DOT_MATCHES_ALL)
            val match = pattern.matchEntire(script.trim()) ?: throw parseChanged("Hitomi")
            val label = Regex("case\\s+(\\d+):")
            if (label.replace(match.groupValues[2], "").isNotBlank()) throw parseChanged("Hitomi")
            val cases = label.findAll(match.groupValues[2]).map { it.groupValues[1].toIntOrNull()?.takeIf { n -> n in 0..4095 } ?: throw parseChanged("Hitomi") }.toSet()
            return HitomiImageRules(match.groupValues[4], match.groupValues[1].toInt(), match.groupValues[3].toInt(), cases)
        }
    }
}
object HitomiProtocol {
    const val NODE_SIZE = 464
    const val MAX_INDEX_BYTES = 8 * 1024 * 1024
    fun key(term: String) = MessageDigest.getInstance("SHA-256").digest(term.toByteArray(Charsets.UTF_8)).copyOf(4)
    fun compare(a: ByteArray, b: ByteArray): Int {
        for (index in 0 until minOf(a.size, b.size)) { val result = (a[index].toInt() and 255) - (b[index].toInt() and 255); if (result != 0) return result }
        return a.size - b.size
    }
    fun node(bytes: ByteArray): HitomiNode = try {
        if (bytes.size != NODE_SIZE) throw parseChanged("Hitomi")
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val count = data.int.takeIf { it in 0..16 } ?: throw parseChanged("Hitomi")
        val keys = List(count) { val size = data.int.takeIf { it in 1..32 } ?: throw parseChanged("Hitomi"); ByteArray(size).also(data::get) }
        if (keys.zipWithNext().any { compare(it.first, it.second) >= 0 } || data.int != count) throw parseChanged("Hitomi")
        val entries = List(count) {
            val offset = data.long; val length = data.int
            if (offset < 0 || length !in 4..MAX_INDEX_BYTES || offset > Long.MAX_VALUE - length) throw parseChanged("Hitomi")
            HitomiIndexData(offset, length)
        }
        val children = List(17) { data.long.also { if (it < 0 || it > Long.MAX_VALUE - NODE_SIZE) throw parseChanged("Hitomi") } }
        HitomiNode(keys, entries, children)
    } catch (e: io.github.ddmoyu.picomic.content.ContentFailure) { throw e } catch (_: Exception) { throw parseChanged("Hitomi") }
    fun ids(bytes: ByteArray, counted: Boolean = false): IntArray {
        if (bytes.size % 4 != 0 || bytes.size > MAX_INDEX_BYTES || (counted && bytes.size < 4)) throw parseChanged("Hitomi")
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val count = if (counted) data.int else bytes.size / 4
        if (count < 0 || count.toLong() * 4 != data.remaining().toLong()) throw parseChanged("Hitomi")
        return IntArray(count) { data.int.also { id -> if (id <= 0) throw parseChanged("Hitomi") } }
    }
    fun intersect(first: IntArray, second: IntArray): IntArray {
        val a = first.sortedArray(); val b = second.sortedArray(); val out = IntArray(minOf(a.size, b.size))
        var i = 0; var j = 0; var size = 0
        while (i < a.size && j < b.size) when { a[i] < b[j] -> i++; a[i] > b[j] -> j++; else -> { if (size == 0 || out[size - 1] != a[i]) out[size++] = a[i]; i++; j++ } }
        return out.copyOf(size).apply { reverse() }
    }
}
