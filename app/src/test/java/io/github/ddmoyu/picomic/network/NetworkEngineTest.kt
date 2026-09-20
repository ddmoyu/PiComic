package io.github.ddmoyu.picomic.network

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.*
import java.util.concurrent.TimeUnit

class NetworkEngineTest {
    @Test fun customProxyCarriesTheRequestAndNeverNeedsOriginDns() {
        MockWebServer().use { proxy ->
            proxy.enqueue(MockResponse().setBody("via proxy")); proxy.start()
            val engine = NetworkEngine(NetworkProfile.HttpProxy("127.0.0.1", proxy.port))
            engine.newCall(Request.Builder().url("http://origin.invalid/chapter").build()).execute().use {
                assertEquals("via proxy", it.body.string())
            }
            assertEquals("GET http://origin.invalid/chapter HTTP/1.1", proxy.takeRequest().requestLine)
        }
    }
    @Test fun deadCustomProxyNeverFallsBackToReachableOrigin() {
        MockWebServer().use { origin ->
            origin.enqueue(MockResponse().setBody("must not be read")); origin.start()
            val closedPort = ServerSocket(0).use { it.localPort }
            val engine = NetworkEngine(NetworkProfile.HttpProxy("127.0.0.1", closedPort))
            assertThrows(IOException::class.java) { engine.newCall(Request.Builder().url(origin.url("/")).build()).execute() }
            assertEquals(0, origin.requestCount)
        }
    }
    @Test fun followSystemUsesTheDefaultProxySelector() {
        MockWebServer().use { proxy ->
            proxy.enqueue(MockResponse().setBody("system")); proxy.start()
            val original = ProxySelector.getDefault()
            try {
                ProxySelector.setDefault(object : ProxySelector() {
                    override fun select(uri: URI) = listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxy.port)))
                    override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit
                })
                NetworkEngine().newCall(Request.Builder().url("http://system-route.invalid/").build()).execute().use {
                    assertEquals("system", it.body.string())
                }
            } finally { ProxySelector.setDefault(original) }
        }
    }
    @Test fun proxyAuthenticationIsBoundedAndOnlySentToProxy() {
        MockWebServer().use { proxy ->
            proxy.enqueue(MockResponse().setResponseCode(407).addHeader("Proxy-Authenticate", "Basic realm=proxy"))
            proxy.enqueue(MockResponse().setBody("ok")); proxy.start()
            val credentials = ProxyCredentials("fixture-user", "fixture-password")
            val engine = NetworkEngine(NetworkProfile.HttpProxy("127.0.0.1", proxy.port, credentials))
            engine.newCall(Request.Builder().url("http://origin.invalid/").build()).execute().close()
            assertNull(proxy.takeRequest().getHeader("Proxy-Authorization"))
            assertEquals(Credentials.basic("fixture-user", "fixture-password"), proxy.takeRequest().getHeader("Proxy-Authorization"))
            assertFalse(credentials.toString().contains("fixture-password"))
        }
    }
    @Test fun authenticatedRedirectIsNotFollowed() {
        MockWebServer().use { origin -> MockWebServer().use { stranger ->
            stranger.start(); origin.enqueue(MockResponse().setResponseCode(302).addHeader("Location", stranger.url("/"))); origin.start()
            val scoped = NetworkEngine().scopedClient(setOf(origin.url("/").origin()))
            scoped.newCall(Request.Builder().url(origin.url("/")).header("Authorization", "fixture-token").build()).execute().use {
                assertEquals(302, it.code)
            }
            assertEquals(0, stranger.requestCount)
            assertThrows(IOException::class.java) { scoped.newCall(Request.Builder().url(stranger.url("/")).build()).execute() }
        } }
    }
    @Test fun switchingProfileCancelsInflightRequestsAndRejectsRetainedClients() = runBlocking {
        MockWebServer().use { origin ->
            origin.enqueue(MockResponse().setHeadersDelay(1, TimeUnit.SECONDS).setBody("old")); origin.start()
            val engine = NetworkEngine()
            val retained = engine.scopedClient(setOf(origin.url("/").origin()))
            val pending = async(Dispatchers.IO) { runCatching { engine.newCall(Request.Builder().url(origin.url("/")).build()).await().close() } }
            assertNotNull(origin.takeRequest(5, TimeUnit.SECONDS))
            engine.change(NetworkProfile.FollowSystem)
            assertTrue(pending.await().isFailure)
            assertThrows(StaleNetworkException::class.java) { retained.newCall(Request.Builder().url(origin.url("/")).build()).execute() }
            Unit
        }
    }
    @Test fun cookieJarEnforcesOriginPathSecureHostOnlyAndExpiry() {
        val url = "https://api.example.test/account/login".toHttpUrl()
        val jar = SourceCookieJar(setOf(url.origin(), "https://sub.api.example.test:443")) { 5000L }
        jar.saveFromResponse(url, listOf(
            Cookie.Builder().name("session").value("fixture").hostOnlyDomain(url.host).path("/account").secure().expiresAt(10000).build(),
            Cookie.Builder().name("expired").value("gone").hostOnlyDomain(url.host).expiresAt(4000).build(),
            Cookie.Builder().name("unrelated").value("bad").domain("stranger.test").build()
        ))
        assertEquals(listOf("session"), jar.loadForRequest(url).map { it.name })
        assertTrue(jar.loadForRequest("https://api.example.test/images/1".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("http://api.example.test/account".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://sub.api.example.test/account".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://evil.test/account".toHttpUrl()).isEmpty())
    }
    @Test fun proxyInputRejectsUrlsCredentialsAndInvalidPorts() {
        listOf("https://proxy.test", "user@proxy.test", "proxy.test/path", "proxy.test?x", "bad host", "bad:80").forEach { host ->
            assertThrows(host, IllegalArgumentException::class.java) { NetworkProfile.HttpProxy(host, 7890) }
        }
        assertThrows(IllegalArgumentException::class.java) { NetworkProfile.HttpProxy("localhost", 0) }
        assertEquals("[::1]:8080", NetworkProfile.HttpProxy("::1", 8080).authority())
    }
}
