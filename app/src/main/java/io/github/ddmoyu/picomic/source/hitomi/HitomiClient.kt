package io.github.ddmoyu.picomic.source.hitomi

import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.network.*
import io.github.ddmoyu.picomic.source.html.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

data class HitomiRange(val bytes: ByteArray, val total: Long)
class HitomiClient internal constructor(private val engine: NetworkEngine, val base: HttpUrl) {
    constructor(engine: NetworkEngine) : this(engine, BASE)
    init { require(base == BASE || (base.host in setOf("localhost", "127.0.0.1") && base.encodedPath == "/")) }
    private val rulesLock = Mutex()
    private var rules: Triple<Long, Long, HitomiImageRules>? = null
    private val queryLock = Mutex()
    private val queryCache = linkedMapOf<String, Triple<Long, Long, IntArray>>()
    suspend fun range(path: List<String>, start: Long? = null, length: Int? = null, limit: Int = HitomiProtocol.MAX_INDEX_BYTES): HitomiRange {
        require((start == null) == (length == null) && (start == null || start >= 0 && length!! > 0 && start <= Long.MAX_VALUE - length))
        val url = base.newBuilder().apply { path.forEach(::addPathSegment) }.build()
        val request = Request.Builder().url(url).header("Referer", REFERER).header("User-Agent", BROWSER_AGENT).header("Accept-Encoding", "identity")
        if (start != null) request.header("Range", "bytes=$start-${start + length!! - 1}")
        var total = -1L; var actualStart = 0L; var expected: Long? = null; var whole = false; var empty = false
        val bytes = engine.newCall(request.build()).readBounded(limit.toLong()) { response ->
            when (response.code) {
                200 -> {
                    whole = true
                    if (response.body.contentLength() > limit) throw ContentFailure(ContentFailureKind.NETWORK, "Hitomi 服务未提供所需的分段响应，已停止超大索引下载")
                }
                206 -> {
                    val match = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(response.header("Content-Range").orEmpty()) ?: throw parseChanged("Hitomi")
                    actualStart = match.groupValues[1].toLongOrNull() ?: throw parseChanged("Hitomi")
                    val end = match.groupValues[2].toLongOrNull() ?: throw parseChanged("Hitomi")
                    total = match.groupValues[3].toLongOrNull() ?: throw parseChanged("Hitomi")
                    if (start == null || actualStart != start || end < start || total <= end || end != minOf(start + length!! - 1, total - 1)) throw parseChanged("Hitomi")
                    expected = end - start + 1
                    if (expected!! > limit) throw parseChanged("Hitomi")
                }
                416 -> {
                    total = Regex("bytes \\*/([0-9]+)").matchEntire(response.header("Content-Range").orEmpty())?.groupValues?.get(1)?.toLongOrNull() ?: throw parseChanged("Hitomi")
                    if (start == null || start < total) throw parseChanged("Hitomi")
                    empty = true
                }
                404 -> throw ContentFailure(ContentFailureKind.NOT_FOUND, "Hitomi 作品或索引不存在")
                403 -> throw ContentFailure(ContentFailureKind.LOGIN, "Hitomi 请求受限，请在系统浏览器检查网络验证")
                429 -> throw ContentFailure(ContentFailureKind.LIMIT, "Hitomi 请求过于频繁，请稍后重试")
                else -> throw ContentFailure(ContentFailureKind.NETWORK, "Hitomi 索引请求失败")
            }
        }
        if (empty) return HitomiRange(byteArrayOf(), total)
        if (expected != null && bytes.size.toLong() != expected) throw parseChanged("Hitomi")
        if (whole) {
            total = bytes.size.toLong()
            if (start != null) return HitomiRange(if (start >= total) byteArrayOf() else bytes.copyOfRange(start.toInt(), minOf(start + length!!, total).toInt()), total)
        }
        return HitomiRange(bytes, total)
    }
    suspend fun gallery(id: String): JSONObject {
        positiveId(id, "Hitomi")
        val script = range(listOf("galleries", "$id.js"), limit = 2 * 1024 * 1024).bytes.toString(Charsets.UTF_8).trim()
        val prefix = Regex("^var\\s+galleryinfo\\s*=\\s*").find(script) ?: throw parseChanged("Hitomi")
        val json = script.substring(prefix.range.last + 1).trim().removeSuffix(";").trim()
        // JSONObject reads data only; require the complete assignment to be a single object.
        val strict = jsonArgument("parse($json)", "parse(", "Hitomi")
        if (strict != json) throw parseChanged("Hitomi")
        return try { JSONObject(json) } catch (_: Exception) { throw parseChanged("Hitomi") }
    }
    suspend fun rules(force: Boolean = false): HitomiImageRules = rulesLock.withLock {
        val generation = engine.status.generation
        val now = android.os.SystemClock.elapsedRealtime()
        rules?.takeIf { !force && it.first == generation && now - it.second in 0..300000 }?.third ?: run {
            val parsed = HitomiImageRules.parse(range(listOf("gg.js"), limit = 128 * 1024).bytes.toString(Charsets.UTF_8))
            engine.withGeneration(generation) { rules = Triple(generation, now, parsed) }
            parsed
        }
    }
    suspend fun searchIds(keyword: String, language: String): IntArray = queryLock.withLock {
        val generation = engine.status.generation; val now = android.os.SystemClock.elapsedRealtime(); val cacheKey = "$language/$keyword"
        queryCache[cacheKey]?.takeIf { it.first == generation && now - it.second in 0..300000 }?.let { return@withLock it.third }
        val terms = keyword.lowercase(java.util.Locale.ROOT).trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (terms.size > 12 || terms.any { it.length > 200 }) throw ContentFailure(ContentFailureKind.PARSE, "Hitomi 搜索最多支持 12 个关键词")
        val version = range(listOf("galleriesindex", "version"), limit = 128).bytes.toString(Charsets.UTF_8).trim()
        if (!version.matches(Regex("[0-9]{1,16}"))) throw parseChanged("Hitomi")
        var result: IntArray? = null
        for (term in terms) {
            val ids = if (':' in term) {
                val namespace = term.substringBefore(':'); val value = term.substringAfter(':').replace('_', ' ')
                if (namespace !in setOf("artist", "group", "series", "character", "tag", "female", "male", "language", "type") || value.isBlank() || '/' in value || '\\' in value || ':' in value) throw ContentFailure(ContentFailureKind.PARSE, "Hitomi 标签格式无效")
                val path = when (namespace) { "language" -> listOf("index-$value.nozomi"); "female", "male" -> listOf("tag", "$namespace:$value-all.nozomi"); else -> listOf(namespace, "$value-all.nozomi") }
                try { HitomiProtocol.ids(range(path).bytes) } catch (e: ContentFailure) { if (e.kind == ContentFailureKind.NOT_FOUND) intArrayOf() else throw e }
            } else termIds(term.replace('_', ' '), version)
            result = result?.let { HitomiProtocol.intersect(it, ids) } ?: ids
            if (result.isEmpty()) break
        }
        if (language != "all" && result?.isEmpty() != true) result = HitomiProtocol.intersect(result ?: intArrayOf(), HitomiProtocol.ids(range(listOf("index-$language.nozomi")).bytes))
        val ids = (result ?: intArrayOf()).sortedArrayDescending()
        engine.withGeneration(generation) {
            queryCache[cacheKey] = Triple(generation, now, ids)
            while (queryCache.size > 4 || queryCache.values.sumOf { it.third.size.toLong() * 4 } > 16 * 1024 * 1024) queryCache.remove(queryCache.keys.first())
        }
        ids
    }
    private suspend fun termIds(term: String, version: String): IntArray {
        val key = HitomiProtocol.key(term); val visited = mutableSetOf<Long>(); var address = 0L
        repeat(32) {
            if (!visited.add(address)) throw parseChanged("Hitomi")
            val node = HitomiProtocol.node(range(listOf("galleriesindex", "galleries.$version.index"), address, HitomiProtocol.NODE_SIZE).bytes)
            val slot = node.keys.indexOfFirst { HitomiProtocol.compare(key, it) <= 0 }.let { if (it < 0) node.keys.size else it }
            if (slot < node.keys.size && HitomiProtocol.compare(key, node.keys[slot]) == 0) {
                val value = node.data[slot]
                return HitomiProtocol.ids(range(listOf("galleriesindex", "galleries.$version.data"), value.offset, value.length).bytes, counted = true)
            }
            address = node.children[slot]
            if (address == 0L) return intArrayOf()
        }
        throw parseChanged("Hitomi")
    }
    suspend fun image(hash: String): ByteArray {
        HitomiImageRules.requireHash(hash)
        for (attempt in 0..1) {
            val url = rules(force = attempt > 0).image(hash).toHttpUrl()
            var stale = false
            val bytes = engine.newCall(Request.Builder().url(url).header("Referer", REFERER).header("User-Agent", BROWSER_AGENT).build()).readBounded(32 * 1024 * 1024) { response ->
                stale = response.code in setOf(403, 404)
                if (!stale && (response.code != 200 || !response.header("Content-Type").orEmpty().startsWith("image/"))) throw ContentFailure(ContentFailureKind.NETWORK, "Hitomi 图片加载失败")
            }
            if (!stale) return bytes
        }
        throw ContentFailure(ContentFailureKind.NETWORK, "Hitomi 图片规则刷新后仍不可用，请稍后重试")
    }
    companion object { val BASE = "https://ltn.gold-usergeneratedcontent.net/".toHttpUrl(); const val REFERER = "https://hitomi.la/" }
}
