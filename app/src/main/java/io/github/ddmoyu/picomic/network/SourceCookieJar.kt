package io.github.ddmoyu.picomic.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** One jar per source/account or temporary login candidate. Never shared with updates/WebDAV. */
class SourceCookieJar(private val origins: Set<String>, private val now: () -> Long = System::currentTimeMillis) : CookieJar {
    private val cookies = mutableListOf<Cookie>()
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (url.origin() !in origins) return
        cookies.forEach { cookie ->
            if (url.host != cookie.domain && (cookie.hostOnly || !url.host.endsWith(".${cookie.domain}"))) return@forEach
            this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (cookie.expiresAt > now()) this.cookies += cookie
        }
        while (this.cookies.size > 128) this.cookies.removeAt(0)
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        cookies.removeAll { it.expiresAt <= now() }
        return if (url.origin() in origins) cookies.filter { it.matches(url) } else emptyList()
    }
    @Synchronized fun clear() = cookies.clear()
}
