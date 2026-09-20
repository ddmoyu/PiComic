package io.github.ddmoyu.picomic

import androidx.test.core.app.ApplicationProvider
import io.github.ddmoyu.picomic.network.NetworkEngine
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

class UpdateContractTest {
    private val channel = ReleaseChannel("fixture", "PiComic")
    private val packageName = "io.github.ddmoyu.picomic"
    private val manifest get() = JSONObject().put("schemaVersion", 1).put("tag", "v1.0").put("versionName", "1.0").put("versionCode", 90)
        .put("packageName", packageName).put("channel", "stable").put("artifacts", JSONArray().put(JSONObject().put("assetName", "PiComic.apk")
            .put("abis", JSONArray(listOf("arm64-v8a", "x86_64"))).put("minSdk", 26).put("sizeBytes", 1234).put("sha256", "a".repeat(64))))
    private fun release(manifest: JSONObject = this.manifest): JSONObject {
        fun asset(id: Long, name: String, size: Long) = JSONObject().put("id", id).put("name", name).put("size", size).put("state", "uploaded")
            .put("browser_download_url", channel.page + "download/v1.0/$name")
        return JSONObject().put("id", 42).put("tag_name", "v1.0").put("url", channel.api + "42").put("html_url", channel.page + "tag/v1.0")
            .put("draft", false).put("prerelease", false).put("published_at", "2026-09-20T00:00:00Z").put("body", "更新说明 <script>仅作文本</script>")
            .put("assets", JSONArray().put(asset(1, "picomic-update.json", manifest.toString().toByteArray().size.toLong())).put(asset(2, "PiComic.apk", 1234)))
    }
    private fun bundle(manifest: JSONObject = this.manifest, release: JSONObject = release(manifest)) = UpdateContract.bundle(release.toString().toByteArray(), manifest.toString().toByteArray(), channel, packageName)
    @Test fun manifestIsBoundToReleaseVersionPackageAssetsAndDevice() {
        val bundle = bundle(); assertEquals(90L, bundle.versionCode)
        assertNotNull(bundle.select(listOf("x86_64", "x86"), 26)); assertNull(bundle.select(listOf("x86"), 36)); assertNull(bundle.select(listOf("arm64-v8a"), 25))
        listOf("tag" to "v2.0", "versionName" to "2.0", "packageName" to "other.app", "channel" to "beta", "schemaVersion" to 2, "versionCode" to 1.5).forEach { (key, value) ->
            assertTrue("should reject $key", runCatching { bundle(manifest.put(key, value)) }.isFailure)
        }
        val noAsset = manifest; noAsset.getJSONArray("artifacts").getJSONObject(0).put("assetName", "source.zip")
        assertTrue(runCatching { bundle(noAsset) }.isFailure)
        val wrongSize = manifest; wrongSize.getJSONArray("artifacts").getJSONObject(0).put("sizeBytes", 1235)
        assertTrue(runCatching { bundle(wrongSize) }.isFailure)
    }
    @Test fun untrustedOriginsDraftsDuplicateAssetsAndDigestMismatchAreRejected() {
        listOf("draft" to true, "prerelease" to true, "html_url" to "https://github.com/other/repo/releases/tag/v1.0", "url" to channel.api + "43").forEach { (key, value) ->
            assertTrue(runCatching { bundle(release = release().put(key, value)) }.isFailure)
        }
        val duplicate = release(); duplicate.getJSONArray("assets").put(duplicate.getJSONArray("assets").getJSONObject(1))
        assertTrue(runCatching { bundle(release = duplicate) }.isFailure)
        val digest = release(); digest.getJSONArray("assets").getJSONObject(1).put("digest", "sha256:" + "b".repeat(64))
        assertTrue(runCatching { bundle(release = digest) }.isFailure)
        listOf("http://github.com/a", "https://github.com.evil.test/a", "https://127.0.0.1/a", "https://github.com:444/a", "https://user@github.com/a").forEach {
            assertFalse(UpdateContract.assetTarget(it.toHttpUrl()))
        }
        assertTrue(UpdateContract.assetTarget("https://release-assets.githubusercontent.com/a?signature=short".toHttpUrl()))
        assertTrue(runCatching { UpdateContract.json(("[".repeat(20) + "]".repeat(20)).toByteArray(), 65536) }.isFailure)
    }
    @Test fun apiKeepsConditionalHeadersAndDifferentiatesNotFoundAndRateLimits() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(release().toString()).addHeader("ETag", "\"one\""))
            server.enqueue(MockResponse().setResponseCode(304).addHeader("ETag", "\"one\""))
            server.enqueue(MockResponse().setResponseCode(404)); server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "120")); server.start()
            val http = OkHttpClient()
            val calls = Call.Factory { request -> http.newCall(request.newBuilder().url(server.url(request.url.encodedPath)).build()) }
            val api = GitHubUpdateClient(channel, "fixture", packageName, calls, calls)
            assertEquals("\"one\"", api.release().etag); assertNull(api.release(etag = "\"one\"").bytes)
            assertTrue((runCatching { api.release() }.exceptionOrNull() as UpdateFailure).message!!.contains("尚无版本"))
            val limited = runCatching { api.release() }.exceptionOrNull() as UpdateFailure
            assertTrue(limited.cooldownUntil > System.currentTimeMillis() + 100000)
            server.takeRequest(); val conditional = server.takeRequest()
            assertEquals("\"one\"", conditional.getHeader("If-None-Match")); assertEquals("2026-03-10", conditional.getHeader("X-GitHub-Api-Version"))
            assertNull(conditional.getHeader("Authorization")); assertNull(conditional.getHeader("Cookie"))
        }
    }
    @Test fun redirectsAreBoundedAndPreserveRangeOnlyAcrossTrustedHttpsHosts() {
        val requests = mutableListOf<Request>()
        fun http(location: String) = GitHubUpdateClient.assetClient(NetworkEngine()).newBuilder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("fixture")
                .code(if (requests.size == 1) 302 else 200).apply { if (requests.size == 1) header("Location", location) }.body("".toResponseBody()).build()
        }.build()
        val request = Request.Builder().url("https://github.com/fixture/PiComic/releases/download/v1.0/PiComic.apk").header("Range", "bytes=100-").header("If-Range", "\"one\"").build()
        http("https://release-assets.githubusercontent.com/asset?sig=one").newCall(request).execute().close()
        assertEquals(2, requests.size); assertEquals("bytes=100-", requests.last().header("Range")); assertNull(requests.last().header("Cookie"))
        requests.clear(); assertTrue(runCatching { http("http://127.0.0.1/capture").newCall(request).execute().close() }.isFailure); assertEquals(1, requests.size)
    }
    @Test fun persistedMetadataIsRevalidatedAndCorruptionCannotRestoreInstallableState() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>(); val dir = File(context.cacheDir, "update-test-${System.nanoTime()}")
        try {
            val store = UpdateStore(dir, channel, packageName); val bundle = bundle()
            store.write("task.json", UpdateStore.Saved(bundle, 2)); assertEquals(bundle.identity(bundle.artifacts.single()), store.read("task.json")!!.bundle.let { it.identity(it.artifacts.single()) })
            val other = UpdateStore(dir, ReleaseChannel("other", "repo"), packageName); assertTrue(runCatching { other.read("task.json") }.isFailure)
            File(dir, "task.json").writeText("{}"); assertTrue(runCatching { store.read("task.json") }.isFailure)
            store.delete("task.json"); assertNull(store.read("task.json"))
        } finally { dir.deleteRecursively() }
    }
    @Test fun crossRoute304CannotReuseCacheAfterAnUnconditionalRetry() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File(context.cacheDir, "update-route-304-${System.nanoTime()}")
        try { MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            repeat(2) { server.enqueue(MockResponse().setResponseCode(304)) }; server.start()
            val http = OkHttpClient(); val calls = Call.Factory { request -> http.newCall(request.newBuilder().url(server.url(request.url.encodedPath)).build()) }
            val api = GitHubUpdateClient(channel, "fixture", packageName, calls, calls, true)
            val store = UpdateStore(dir, channel, packageName)
            store.write("cache.json", UpdateStore.Saved(bundle(), etag = "\"official\""))
            assertTrue(runCatching { UpdateChecker.check(api, store) }.exceptionOrNull() is UpdateFailure)
            val requests = List(3) { server.takeRequest() }
            assertEquals("\"official\"", requests[0].getHeader("If-None-Match"))
            assertNull(requests[1].getHeader("If-None-Match")); assertNull(requests[2].getHeader("If-None-Match"))
            assertEquals(3, server.requestCount)
        } } finally { dir.deleteRecursively() }
    }
    @Test fun unexpected304WithoutUsableCacheRetriesOnceAndValidCacheAvoidsAssetDownload() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>(); val dir = File(context.cacheDir, "update-304-${System.nanoTime()}")
        try { MockWebServer().use { server ->
            val manifest = manifest; val release = release(manifest)
            server.enqueue(MockResponse().setResponseCode(304))
            server.enqueue(MockResponse().setBody(release.toString()).addHeader("ETag", "\"verified\""))
            server.enqueue(MockResponse().setBody(manifest.toString()))
            server.enqueue(MockResponse().setResponseCode(304)); server.start()
            val http = OkHttpClient(); val calls = Call.Factory { request -> http.newCall(request.newBuilder().url(server.url(request.url.encodedPath)).build()) }
            val api = GitHubUpdateClient(channel, "fixture", packageName, calls, calls); val store = UpdateStore(dir, channel, packageName)
            assertEquals(90L, UpdateChecker.check(api, store).versionCode); assertEquals(3, server.requestCount)
            assertEquals(90L, UpdateChecker.check(api, store).versionCode); assertEquals(4, server.requestCount)
            val requests = List(4) { server.takeRequest() }
            assertNull(requests[1].getHeader("If-None-Match")); assertEquals("\"verified\"", requests[3].getHeader("If-None-Match"))
        } } finally { dir.deleteRecursively() }
    }
}
