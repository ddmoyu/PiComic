package io.github.ddmoyu.picomic

import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.update.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class UpdateDownloadControlTest {
    @Test fun pausingAnUpdateKeepsItsPartialFileAndContinuingUsesTheVerifiedRange() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val part = File.createTempFile("update-control-", ".part", context.cacheDir)
        val bytes = ByteArray(128000) { (it % 251).toByte() }
        try { MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Accept-Ranges", "bytes").setHeader("ETag", "\"fixture-v1\"")
                .setBody(Buffer().write(bytes)).throttleBody(2048, 50, TimeUnit.MILLISECONDS))
            server.start()
            val http = OkHttpClient()
            val calls = Call.Factory { request -> http.newCall(request.newBuilder().url(server.url(request.url.encodedPath)).build()) }
            val channel = ReleaseChannel("fixture", "app")
            val artifact = UpdateArtifact(ReleaseAsset(1, "fixture.apk", bytes.size.toLong(), (channel.page + "download/v1.0/fixture.apk").toHttpUrl(), null),
                listOf("arm64-v8a"), 26, UpdateContract.hash(bytes))
            val client = GitHubUpdateClient(channel, "fixture", context.packageName, calls, calls)
            val transfer = launch(Dispatchers.IO) { client.download(artifact, part) }
            withTimeout(5000) { while (part.length() == 0L) delay(20) }
            transfer.cancelAndJoin()
            val offset = part.length().toInt()
            assertTrue(offset in 1 until bytes.size)
            assertTrue(File(part.path + ".resume").exists())
            server.takeRequest()
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes $offset-${bytes.size - 1}/${bytes.size}")
                .setHeader("ETag", "\"fixture-v1\"").setBody(Buffer().write(bytes, offset, bytes.size - offset)))
            client.download(artifact, part)
            val resumed = server.takeRequest()
            assertEquals("bytes=$offset-", resumed.getHeader("Range"))
            assertEquals("\"fixture-v1\"", resumed.getHeader("If-Range"))
            assertArrayEquals(bytes, part.readBytes())
            assertFalse(File(part.path + ".resume").exists())
        } } finally { part.delete(); File(part.path + ".resume").delete() }
    }
}
