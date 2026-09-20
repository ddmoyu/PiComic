package io.github.ddmoyu.picomic.update

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Public HTTPS endpoints only. Mirror URLs wrap a validated canonical GitHub URL. */
enum class UpdateRoute(val key: String, val label: String, val origin: String?, val supportsApi: Boolean) {
    GITHUB("github", "GitHub", null, true),
    GH_PROXY("gh-proxy", "GH-Proxy 备用线路", "https://gh-proxy.com", true),
    GHPROXY_NET("ghproxy-net", "GHProxy.net 备用线路", "https://ghproxy.net", false);

    fun url(canonical: HttpUrl): HttpUrl = if (origin == null) canonical else "$origin/$canonical".toHttpUrl()

    companion object {
        fun routes(mirrors: Boolean, api: Boolean = false) = entries.filter { (mirrors || it == GITHUB) && (!api || it.supportsApi) }
        fun canonical(url: HttpUrl): HttpUrl? {
            if (!secure(url)) return null
            val mirror = entries.firstOrNull { it.origin != null && url.host == it.origin.toHttpUrl().host } ?: return url
            if (url.query != null) return null
            val embedded = runCatching { url.encodedPath.removePrefix("/").toHttpUrl() }.getOrNull() ?: return null
            return embedded.takeIf { secure(it) && it.query == null && mirror.url(it) == url }
        }
        fun apiTarget(url: HttpUrl): Boolean {
            val canonical = canonical(url) ?: return false
            val mirror = entries.firstOrNull { it.origin != null && url.host == it.origin.toHttpUrl().host }
            return (mirror == null || mirror.supportsApi) && canonical.host == "api.github.com" && canonical.query == null &&
                Regex("/repos/[A-Za-z0-9-]+/[A-Za-z0-9_.-]+/releases/(latest|[1-9][0-9]*)").matches(canonical.encodedPath)
        }
        fun assetTarget(url: HttpUrl): Boolean {
            val canonical = canonical(url) ?: return false
            if (canonical == url) return UpdateContract.assetTarget(url)
            return canonical.host == "github.com" && canonical.query == null &&
                Regex("/[A-Za-z0-9-]+/[A-Za-z0-9_.-]+/releases/download/v[0-9][A-Za-z0-9.+-]*/[A-Za-z0-9_][A-Za-z0-9_.-]*").matches(canonical.encodedPath)
        }
        private fun secure(url: HttpUrl) = url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null
    }
}

interface UpdateRouteCooldowns {
    fun until(key: String): Long
    fun set(key: String, until: Long)
}

internal class MemoryUpdateCooldowns : UpdateRouteCooldowns {
    private val values = java.util.concurrent.ConcurrentHashMap<String, Long>()
    override fun until(key: String) = values[key] ?: 0
    override fun set(key: String, until: Long) { values[key] = until }
}
