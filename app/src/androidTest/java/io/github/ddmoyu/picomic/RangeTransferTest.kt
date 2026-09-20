package io.github.ddmoyu.picomic

import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.download.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class RangeTransferTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun strongValidatorResumesButIgnoredRangeRestartsOnlyThisPage() = runBlocking {
        val server = MockWebServer(); server.start()
        val file = File.createTempFile("range-fixture-", ".part", context.cacheDir)
        try {
            val request = Request.Builder().url(server.url("/page")).build()
            seed(file, request, "abc", 6)
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6").setHeader("ETag", "\"v1\"").setBody("def"))
            val transfer = RangeTransfer(OkHttpClient())
            transfer.fetch(request, file)
            assertEquals("abcdef", file.readText()); val first = server.takeRequest(); assertEquals("bytes=3-", first.getHeader("Range")); assertEquals("\"v1\"", first.getHeader("If-Range"))
            seed(file, request, "abc", 6)
            server.enqueue(MockResponse().setBody("new-version"))
            transfer.fetch(request, file); assertEquals("new-version", file.readText())
        } finally { file.delete(); File(file.path + ".resume").delete(); server.shutdown() }
    }
    @Test fun staleValidatorAndOverlargeResponsesCannotBeAppended() = runBlocking {
        val server = MockWebServer(); server.start(); val file = File.createTempFile("range-fixture-", ".part", context.cacheDir)
        try {
            val request = Request.Builder().url(server.url("/page")).build(); seed(file, request, "abc", 6)
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6").setHeader("ETag", "\"v2\"").setBody("xyz"))
            assertTrue(runCatching { RangeTransfer(OkHttpClient()).fetch(request, file) }.isFailure)
            assertFalse(file.exists())
            server.enqueue(MockResponse().setBody("too-many-bytes"))
            assertTrue(runCatching { RangeTransfer(OkHttpClient()).fetch(request, file, 4) }.isFailure)
        } finally { file.delete(); File(file.path + ".resume").delete(); server.shutdown() }
    }
    @Test fun cancellationWaitsForWriterBeforeAnotherTaskCanUseThePartialFile() = runBlocking {
        val server = MockWebServer(); server.start(); val file = File.createTempFile("range-fixture-", ".part", context.cacheDir)
        try {
            server.enqueue(MockResponse().setHeader("Accept-Ranges", "bytes").setHeader("ETag", "\"v1\"").setBody("x".repeat(100000)).throttleBody(1000, 100, TimeUnit.MILLISECONDS))
            val request = Request.Builder().url(server.url("/page")).build()
            val job = launch(Dispatchers.IO) { RangeTransfer(OkHttpClient()).fetch(request, file) }
            withTimeout(5000) { while (file.length() == 0L) delay(20) }
            job.cancelAndJoin()
            file.writeText("owned-by-next-job"); delay(200)
            assertEquals("owned-by-next-job", file.readText())
            assertTrue(File(file.path + ".resume").exists())
        } finally { file.delete(); File(file.path + ".resume").delete(); server.shutdown() }
    }
    private fun seed(file: File, request: Request, partial: String, total: Int) {
        file.writeText(partial)
        File(file.path + ".resume").writeText(JSONObject().put("url", digest(request.url.toString().toByteArray())).put("etag", "\"v1\"").put("total", total).toString())
    }
}
