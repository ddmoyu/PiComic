package io.github.ddmoyu.picomic

import androidx.test.core.app.ApplicationProvider
import io.github.ddmoyu.picomic.network.*
import io.github.ddmoyu.picomic.update.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.UnknownHostException

class UpdateMirrorTest {
    private val channel = ReleaseChannel("fixture", "PiComic")
    private val packageName = "io.github.ddmoyu.picomic"
    private fun release() = JSONObject().put("id", 42).put("tag_name", "v1.0").put("url", channel.api + "42")
        .put("html_url", channel.page + "tag/v1.0").put("draft", false).put("prerelease", false)
        .put("published_at", "2026-09-20T00:00:00Z").put("body", "备用线路测试")
        .put("assets", JSONArray().put(JSONObject().put("id", 1).put("name", "picomic-update.json").put("size", 20)
            .put("state", "uploaded").put("browser_download_url", channel.page + "download/v1.0/picomic-update.json")))
    private fun calls(server: MockWebServer): Call.Factory {
        val http = OkHttpClient()
        return Call.Factory { request -> http.newCall(request.newBuilder().url(server.url(request.url.encodedPath)).build()) }
    }
    @Test fun rateLimitedApiFallsBackAndOnlyItsOwnRouteStaysInCooldown() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "120"))
            repeat(2) { server.enqueue(MockResponse().setBody(release().toString()).addHeader("ETag", "\"mirror\"")) }
            server.start()
            val transport = calls(server); val cooldowns = MemoryUpdateCooldowns()
            val client = GitHubUpdateClient(channel, "test", packageName, transport, transport, true, cooldowns)
            assertEquals("gh-proxy", client.release(etag = "\"official\"").route)
            assertTrue(cooldowns.until("api:github") > System.currentTimeMillis() + 100000)
            assertEquals(0, cooldowns.until("asset:github"))
            val official = server.takeRequest(); val mirror = server.takeRequest()
            assertEquals("\"official\"", official.getHeader("If-None-Match"))
            assertNull(mirror.getHeader("If-None-Match")); assertNull(mirror.getHeader("Authorization")); assertNull(mirror.getHeader("Cookie"))
            val fresh = GitHubUpdateClient(channel, "test", packageName, transport, transport, true, cooldowns)
            fresh.release(id = 42, etag = "\"mirror\"", etagRoute = "gh-proxy")
            val resumed = server.takeRequest()
            assertEquals("/https://api.github.com/repos/fixture/PiComic/releases/42", resumed.path)
            assertEquals("\"mirror\"", resumed.getHeader("If-None-Match")); assertEquals(3, server.requestCount)
        }
    }
    @Test fun connectionFailureCanUseMirrorButDisabledFallbackDoesNot() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(release().toString())); server.start()
            val transport = calls(server); val attempted = mutableListOf<String>()
            val failing = Call.Factory { request ->
                attempted += request.url.host
                if (request.url.host == "api.github.com") throw UnknownHostException("fixture")
                transport.newCall(request)
            }
            assertEquals("gh-proxy", GitHubUpdateClient(channel, "test", packageName, failing, transport, true).release().route)
            assertEquals(listOf("api.github.com", "gh-proxy.com"), attempted)
            attempted.clear()
            assertTrue(runCatching { GitHubUpdateClient(channel, "test", packageName, failing, transport, false).release() }.exceptionOrNull() is UnknownHostException)
            assertEquals(listOf("api.github.com"), attempted)
        }
    }
    @Test fun networkGenerationChangesNeverStartAnotherRoute() = runBlocking {
        var attempts = 0
        val failing = Call.Factory { attempts++; throw StaleNetworkException() }
        assertTrue(runCatching { GitHubUpdateClient(channel, "test", packageName, failing, failing, true).release() }.exceptionOrNull() is StaleNetworkException)
        assertEquals(1, attempts)
    }
    @Test fun mirrorMetadataStillMustBelongToTheConfiguredRepository() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody(release().put("url", "https://api.github.com/repos/other/repo/releases/42").toString()))
            server.start(); val transport = calls(server)
            assertTrue(runCatching { GitHubUpdateClient(channel, "test", packageName, transport, transport, true).release() }.exceptionOrNull() is UpdateFailure)
            assertEquals(2, server.requestCount)
        }
    }
    @Test fun downloadTriesBothMirrorsAndDoesNotAppendDifferentRouteBytes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File(context.cacheDir, "mirror-download-${System.nanoTime()}").also { it.mkdirs() }
        try { MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503)); server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody("NEWAPK")); server.start()
            val url = (channel.page + "download/v1.0/PiComic.apk").toHttpUrl()
            val part = File(dir, "update.part").also { it.writeText("OLD") }
            File(part.path + ".resume").writeText(JSONObject().put("url", UpdateContract.hash(url.toString().toByteArray()))
                .put("etag", "\"official\"").put("total", 6).toString())
            val artifact = UpdateArtifact(ReleaseAsset(2, "PiComic.apk", 6, url, null), listOf("arm64-v8a"), 26, UpdateContract.hash("NEWAPK".toByteArray()))
            val transport = calls(server)
            GitHubUpdateClient(channel, "test", packageName, transport, transport, true).download(artifact, part)
            assertEquals("NEWAPK", part.readText())
            assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
            val second = server.takeRequest(); val third = server.takeRequest()
            assertNull(second.getHeader("Range")); assertNull(third.getHeader("If-Range"))
            assertTrue(third.path!!.startsWith("/https://github.com/fixture/PiComic/releases/download/"))
            assertFalse(File(part.path + ".resume").exists())
        } } finally { dir.deleteRecursively() }
    }
    @Test fun credentialsAreStrippedAndUntrustedMirrorRedirectIsRejected() {
        val requests = mutableListOf<Request>()
        val http = GitHubUpdateClient.assetClient(NetworkEngine(), true).newBuilder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("fixture").code(302)
                .header("Location", "https://evil.test/update.apk").body("".toResponseBody()).build()
        }.build()
        val url = UpdateRoute.GH_PROXY.url((channel.page + "download/v1.0/PiComic.apk").toHttpUrl())
        assertTrue(runCatching { http.newCall(Request.Builder().url(url).header("Authorization", "secret").header("Cookie", "secret").build()).execute().close() }.isFailure)
        assertEquals(1, requests.size); assertNull(requests.single().header("Authorization")); assertNull(requests.single().header("Cookie"))
    }
}
