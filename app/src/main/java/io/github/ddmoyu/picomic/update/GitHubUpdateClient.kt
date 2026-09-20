package io.github.ddmoyu.picomic.update

import io.github.ddmoyu.picomic.network.*
import okhttp3.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class ReleaseReply(val bytes: ByteArray?, val etag: String?)
class GitHubUpdateClient internal constructor(private val channel: ReleaseChannel,
    private val version: String, private val packageName: String, private val api: Call.Factory, val assets: Call.Factory) {
    constructor(engine: NetworkEngine, channel: ReleaseChannel, version: String, packageName: String) :
        this(channel, version, packageName, engine.scopedClient(setOf("https://api.github.com:443")), assetClient(engine))
    companion object {
    /** Fresh requests contain no source headers. Only known GitHub HTTPS asset hosts can redirect. */
    internal fun assetClient(engine: NetworkEngine): OkHttpClient = engine.scopedClient(setOf("https://github.com:443")).newBuilder()
        .callTimeout(0, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            var request = chain.request(); var count = 0
            while (true) {
                if (!UpdateContract.assetTarget(request.url)) throw UpdateFailure("更新文件跳转地址不可信")
                val response = chain.proceed(request)
                if (response.code !in setOf(301, 302, 303, 307, 308)) return@addInterceptor response
                val next = response.header("Location")?.let(request.url::resolve); response.close()
                if (++count > 5 || next == null || !UpdateContract.assetTarget(next)) throw UpdateFailure("更新文件跳转地址不可信")
                request = request.newBuilder().url(next).removeHeader("Authorization").removeHeader("Cookie").build()
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }.build()
    }
    suspend fun release(id: Long? = null, etag: String? = null): ReleaseReply {
        val request = Request.Builder().url(channel.api + (id?.toString() ?: "latest"))
            .header("Accept", "application/vnd.github+json").header("User-Agent", "PiComic/$version")
            .header("X-GitHub-Api-Version", "2026-03-10").apply { etag?.let { header("If-None-Match", it) } }.build()
        var code = 0; var tag: String? = null
        val bytes = api.newCall(request).readBounded(1024 * 1024) { response ->
            code = response.code; tag = response.header("ETag")?.takeIf { it.length <= 512 && it.none(Char::isISOControl) }
            Responses.checkResponse(response, allow304 = true)
        }
        return ReleaseReply(if (code == 304) null else bytes, tag)
    }
    suspend fun bundle(release: ByteArray): UpdateBundle {
        val parsed = try { UpdateContract.release(release, channel) } catch (_: Exception) { throw UpdateFailure("发布信息无效或没有稳定版本") }
        val manifest = parsed.assets.singleOrNull { it.name == "picomic-update.json" } ?: throw UpdateFailure("此版本没有可用的更新清单")
        if (manifest.size > 65536) throw UpdateFailure("更新清单超过大小上限")
        val bytes = assets.newCall(Request.Builder().url(manifest.url).header("User-Agent", "PiComic/$version").build()).readBounded(65536) { Responses.checkResponse(it) }
        return try { UpdateContract.bundle(release, bytes, channel, packageName) } catch (_: Exception) { throw UpdateFailure("更新清单与发布文件不一致") }
    }
    internal object Responses {
        internal fun checkResponse(response: Response, allow304: Boolean = false, now: Long = System.currentTimeMillis()) {
            if (response.code == 200 || allow304 && response.code == 304) return
            val limited = response.code == 429 || response.code == 403 && (response.header("X-RateLimit-Remaining") == "0" || response.header("Retry-After") != null)
            if (limited) {
                val retry = response.header("Retry-After")?.let { value -> value.toLongOrNull()?.takeIf { it >= 0 }?.let { now + it.coerceAtMost(604800) * 1000 }
                    ?: runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() }
                val reset = response.header("X-RateLimit-Reset")?.toLongOrNull()?.takeIf { it > 0 && it < Long.MAX_VALUE / 1000 }?.times(1000)
                throw UpdateFailure("GitHub 请求已限流，请稍后重试", maxOf(now + 60_000, retry ?: 0, reset ?: 0).coerceAtMost(now + 604800_000))
            }
            throw UpdateFailure(when(response.code) { 404 -> "发布地址不可用或尚无版本"; 401, 403 -> "公开发布地址无法访问"; in 300..399 -> "发布接口发生跳转，已停止检查"; else -> "更新服务暂时不可用（HTTP ${response.code}）" })
        }
    }
}
