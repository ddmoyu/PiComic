package io.github.ddmoyu.picomic.update

import io.github.ddmoyu.picomic.download.RangeTransfer
import io.github.ddmoyu.picomic.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class ReleaseReply(val bytes: ByteArray?, val etag: String?, val route: String = UpdateRoute.GITHUB.key)
class GitHubUpdateClient internal constructor(private val channel: ReleaseChannel,
    private val version: String, private val packageName: String, private val api: Call.Factory, val assets: Call.Factory,
    private val mirrors: Boolean = false, private val cooldowns: UpdateRouteCooldowns = MemoryUpdateCooldowns(),
    private val onRoute: (String) -> Unit = {}) {
    constructor(engine: NetworkEngine, channel: ReleaseChannel, version: String, packageName: String,
        mirrors: Boolean = true, cooldowns: UpdateRouteCooldowns = MemoryUpdateCooldowns(), onRoute: (String) -> Unit = {}) :
        this(channel, version, packageName, apiClient(engine, mirrors), assetClient(engine, mirrors), mirrors, cooldowns, onRoute)

    companion object {
        private fun origins(mirrors: Boolean, api: Boolean) = buildSet {
            add(if (api) "https://api.github.com:443" else "https://github.com:443")
            UpdateRoute.routes(mirrors, api).mapNotNull { it.origin }.forEach { add("$it:443") }
        }
        private fun apiClient(engine: NetworkEngine, mirrors: Boolean): OkHttpClient = engine.scopedClient(origins(mirrors, true))
            .newBuilder().callTimeout(18, TimeUnit.SECONDS).addInterceptor { chain ->
                val request = chain.request()
                if (!UpdateRoute.apiTarget(request.url)) throw UpdateFailure("更新接口地址不可信")
                chain.proceed(request.newBuilder().removeHeader("Authorization").removeHeader("Cookie").removeHeader("Proxy-Authorization").build())
            }.build()

        /** Redirects never widen a mirror request into an arbitrary HTTPS destination. */
        internal fun assetClient(engine: NetworkEngine, mirrors: Boolean = false): OkHttpClient = engine.scopedClient(origins(mirrors, false)).newBuilder()
            .callTimeout(0, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                var request = chain.request().newBuilder().removeHeader("Authorization").removeHeader("Cookie").removeHeader("Proxy-Authorization").build()
                var count = 0
                while (true) {
                    if (!(if (mirrors) UpdateRoute.assetTarget(request.url) else UpdateContract.assetTarget(request.url))) throw UpdateFailure("更新文件跳转地址不可信")
                    val response = chain.proceed(request)
                    if (response.code !in setOf(301, 302, 303, 307, 308)) return@addInterceptor response
                    val next = response.header("Location")?.let(request.url::resolve); response.close()
                    if (++count > 5 || next == null || !(if (mirrors) UpdateRoute.assetTarget(next) else UpdateContract.assetTarget(next))) throw UpdateFailure("更新文件跳转地址不可信")
                    val original = UpdateRoute.canonical(request.url)
                    val target = UpdateRoute.canonical(next)
                    if (target != next && (original?.host != "github.com" || target != original)) throw UpdateFailure("更新镜像跳转的文件不一致")
                    request = request.newBuilder().url(next).removeHeader("Authorization").removeHeader("Cookie").removeHeader("Proxy-Authorization").build()
                }
                @Suppress("UNREACHABLE_CODE") error("unreachable")
            }.build()
    }

    private suspend fun <T> routes(api: Boolean, block: suspend (UpdateRoute) -> T): T {
        var last: IOException? = null
        var earliestCooldown = Long.MAX_VALUE
        for (route in UpdateRoute.routes(mirrors, api)) {
            currentCoroutineContext().ensureActive()
            val key = (if (api) "api:" else "asset:") + route.key
            val until = cooldowns.until(key)
            if (until > System.currentTimeMillis()) { earliestCooldown = minOf(earliestCooldown, until); continue }
            onRoute(route.label)
            try { return block(route) }
            catch (error: CancellationException) { throw error }
            catch (error: StaleNetworkException) { throw error }
            catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                if (error is UpdateFailure) {
                    if (!error.retryable) throw error
                    if (error.cooldownUntil > 0) { cooldowns.set(key, error.cooldownUntil); earliestCooldown = minOf(earliestCooldown, error.cooldownUntil) }
                }
                last = error
            }
        }
        throw last ?: UpdateFailure("更新线路暂时限流，请稍后重试", earliestCooldown.takeIf { it != Long.MAX_VALUE } ?: 0, true)
    }

    suspend fun release(id: Long? = null, etag: String? = null, etagRoute: String = UpdateRoute.GITHUB.key): ReleaseReply {
        val canonical = (channel.api + (id?.toString() ?: "latest")).toHttpUrl()
        return routes(api = true) { route ->
            val request = Request.Builder().url(route.url(canonical))
                .header("Accept", "application/vnd.github+json").header("User-Agent", "PiComic/$version")
                .header("X-GitHub-Api-Version", "2026-03-10")
                .apply { if (route.key == etagRoute) etag?.let { header("If-None-Match", it) } }.build()
            var code = 0; var tag: String? = null
            val bytes = api.newCall(request).readBounded(1024 * 1024) { response ->
                code = response.code; tag = response.header("ETag")?.takeIf { it.length <= 512 && it.none(Char::isISOControl) }
                Responses.checkResponse(response, allow304 = true, mirror = route != UpdateRoute.GITHUB)
            }
            if (code == 304 && (etag == null || etagRoute != route.key)) return@routes ReleaseReply(null, null, route.key)
            if (code != 304) {
                val parsed = try { UpdateContract.release(bytes, channel) } catch (_: Exception) { throw UpdateFailure("发布信息无效或没有稳定版本") }
                if (id != null && parsed.id != id) throw UpdateFailure("发布版本已变化，请重新检查")
            }
            ReleaseReply(if (code == 304) null else bytes, tag, route.key)
        }
    }

    suspend fun bundle(release: ByteArray): UpdateBundle {
        val parsed = try { UpdateContract.release(release, channel) } catch (_: Exception) { throw UpdateFailure("发布信息无效或没有稳定版本") }
        val manifest = parsed.assets.singleOrNull { it.name == "picomic-update.json" } ?: throw UpdateFailure("此版本没有可用的更新清单")
        if (manifest.size > 65536) throw UpdateFailure("更新清单超过大小上限")
        val bytes = routes(api = false) { route ->
            assets.newCall(Request.Builder().url(route.url(manifest.url)).header("User-Agent", "PiComic/$version").build())
                .readBounded(65536) { Responses.checkResponse(it, mirror = route != UpdateRoute.GITHUB) }
        }
        return try { UpdateContract.bundle(release, bytes, channel, packageName) } catch (_: Exception) { throw UpdateFailure("更新清单与发布文件不一致") }
    }

    suspend fun download(artifact: UpdateArtifact, destination: File) = routes(api = false) { route ->
        // The resume validator is keyed by this full URL; switching mirrors restarts safely.
        RangeTransfer(assets).fetch(Request.Builder().url(route.url(artifact.asset.url)).header("User-Agent", "PiComic/$version").build(), destination, artifact.asset.size) {
            Responses.checkResponse(it, allowPartial = true, mirror = route != UpdateRoute.GITHUB)
        }
    }

    internal object Responses {
        internal fun checkResponse(response: Response, allow304: Boolean = false, now: Long = System.currentTimeMillis(),
            allowPartial: Boolean = false, mirror: Boolean = false) {
            if (response.code == 200 || allow304 && response.code == 304 || allowPartial && response.code == 206) return
            val limited = response.code == 429 || response.code == 403 && (response.header("X-RateLimit-Remaining") == "0" || response.header("Retry-After") != null)
            if (limited) {
                val retry = response.header("Retry-After")?.let { value -> value.toLongOrNull()?.takeIf { it >= 0 }?.let { now + it.coerceAtMost(604800) * 1000 }
                    ?: runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() }
                val reset = response.header("X-RateLimit-Reset")?.toLongOrNull()?.takeIf { it > 0 && it < Long.MAX_VALUE / 1000 }?.times(1000)
                throw UpdateFailure("当前更新线路已限流，请稍后重试", maxOf(now + 60_000, retry ?: 0, reset ?: 0).coerceAtMost(now + 604800_000), true)
            }
            throw UpdateFailure(when(response.code) { 404 -> "发布地址不可用或尚无版本"; 401, 403 -> "公开发布地址无法访问"; in 300..399 -> "发布接口发生跳转，已停止检查"; else -> "更新服务暂时不可用（HTTP ${response.code}）" },
                retryable = response.code in 500..599 || response.code in setOf(403, 408) || mirror && response.code == 404)
        }
    }
}
