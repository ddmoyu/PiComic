package io.github.ddmoyu.picomic.update

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class UpdateRoutesTest {
    private val asset = "https://github.com/ddmoyu/PiComic/releases/download/v1.2.3/PiComic.apk".toHttpUrl()
    @Test fun apiAndAssetRoutesHaveDifferentCapabilities() {
        assertEquals(listOf(UpdateRoute.GITHUB), UpdateRoute.routes(false, true))
        assertEquals(listOf(UpdateRoute.GITHUB, UpdateRoute.GH_PROXY), UpdateRoute.routes(true, true))
        assertEquals(3, UpdateRoute.routes(true).size)
        UpdateRoute.entries.forEach { route ->
            assertEquals(asset, UpdateRoute.canonical(route.url(asset)))
            assertTrue(UpdateRoute.assetTarget(route.url(asset)))
        }
    }
    @Test fun mirrorsCannotWrapPrivateHostsNestedProxiesOrCredentials() {
        listOf(
            "http://gh-proxy.com/$asset", "https://gh-proxy.com:444/$asset", "https://user@gh-proxy.com/$asset",
            "https://gh-proxy.com/https://127.0.0.1/a.apk", "https://gh-proxy.com/https://ghproxy.net/$asset",
            "https://gh-proxy.com.evil.test/$asset", "https://gh-proxy.com/$asset?token=secret",
            "https://gh-proxy.com/https://github.com/ddmoyu/PiComic/raw/main/private.txt",
            "https://ghproxy.net/https://api.github.com/repos/ddmoyu/PiComic/releases/latest"
        ).forEach { assertFalse(it, UpdateRoute.assetTarget(it.toHttpUrl())) }
        assertTrue(UpdateRoute.apiTarget("https://gh-proxy.com/https://api.github.com/repos/ddmoyu/PiComic/releases/latest".toHttpUrl()))
        assertFalse(UpdateRoute.apiTarget("https://ghproxy.net/https://api.github.com/repos/ddmoyu/PiComic/releases/latest".toHttpUrl()))
        assertFalse(UpdateRoute.apiTarget("https://gh-proxy.com/https://api.github.com/user".toHttpUrl()))
    }
}
