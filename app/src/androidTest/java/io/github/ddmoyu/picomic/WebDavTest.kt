package io.github.ddmoyu.picomic

import io.github.ddmoyu.picomic.backup.*
import io.github.ddmoyu.picomic.network.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class WebDavTest {
    private val collection = """<d:multistatus xmlns:d="DAV:"><d:response><d:href>/dav/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>"""
    private inner class Server(val conditions: Boolean = true, val readOnly: Boolean = false) : AutoCloseable {
        val files = ConcurrentHashMap<String, ByteArray>()
        val server = MockWebServer()
        val requests = java.util.concurrent.CopyOnWriteArrayList<RecordedRequest>()
        init {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val path = request.requestUrl!!.encodedPath; val current = files[path]
                    val etag = current?.let { "\"${io.github.ddmoyu.picomic.download.digest(it)}\"" }
                    return when (request.method) {
                        "PROPFIND" -> MockResponse().setResponseCode(207).setBody(collection)
                        "GET" -> if (current == null) MockResponse().setResponseCode(404) else MockResponse().setHeader("ETag", etag!!).setBody(Buffer().write(current))
                        "PUT" -> {
                            if (readOnly) return MockResponse().setResponseCode(403)
                            if (conditions && (request.getHeader("If-None-Match") == "*" && current != null || request.getHeader("If-Match")?.let { it != etag } == true)) return MockResponse().setResponseCode(412)
                            files[path] = request.body.readByteArray()
                            MockResponse().setResponseCode(if (current == null) 201 else 204)
                        }
                        "DELETE" -> {
                            if (conditions && request.getHeader("If-Match")?.let { it != etag } == true) return MockResponse().setResponseCode(412)
                            files.remove(path); MockResponse().setResponseCode(204)
                        }
                        else -> MockResponse().setResponseCode(405)
                    }
                }
            }
            server.start()
        }
        fun client() = WebDavClient(NetworkEngine(), server.url("/dav/"), "Basic fixture-only")
        override fun close() = server.shutdown()
    }
    private fun data(revision: Long) = BackupCodec.encode(BackupDocument("00000000-0000-4000-8000-000000000001", revision, 100, BackupData()))
    @Test fun safeConditionalWriteReadbackAndConflictingDevicePreserveRemote() = runBlocking {
        Server().use { server ->
            val client = server.client(); val tested = client.test()
            assertTrue(tested.message, tested.writable); assertTrue(server.files.isEmpty())
            val absent = client.read(); assertNull(absent.document)
            val bytes = data(1); client.upload(bytes, absent)
            assertArrayEquals(bytes, server.files["/dav/picomic-user-data.json"])
            val error = runCatching { server.client().upload(data(2), absent) }.exceptionOrNull()
            assertEquals(WebDavError.CONFLICT, (error as WebDavFailure).kind)
            assertArrayEquals(bytes, server.files["/dav/picomic-user-data.json"])
            val current = client.read(); client.upload(data(3), current)
            assertEquals(3L, client.read().document!!.revision)
            assertTrue(server.requests.all { it.getHeader("Authorization") == "Basic fixture-only" && it.getHeader("Cookie") == null })
            assertTrue(server.requests.filter { it.method == "PROPFIND" }.all { it.getHeader("Depth") == "0" })
            assertTrue(server.requests.none { it.method == "MKCOL" })
        }
    }
    @Test fun ignoredConditionsAndReadOnlyServicesDisableUploadingWithoutTouchingBackup() = runBlocking {
        for (readOnly in listOf(false, true)) Server(conditions = false, readOnly = readOnly).use { server ->
            val existing = data(1); server.files["/dav/picomic-user-data.json"] = existing
            val result = server.client().test(); assertFalse(result.writable)
            assertArrayEquals(existing, server.files["/dav/picomic-user-data.json"])
            assertEquals(setOf("/dav/picomic-user-data.json"), server.files.keys)
            assertEquals(1L, server.client().read().document!!.revision)
        }
    }
    @Test fun redirectsInvalidXmlAndChangedNetworkDoNotSendRequestsToOtherOrigins() = runBlocking {
        MockWebServer().use { server -> MockWebServer().use { outside ->
            server.start(); outside.start()
            val engine = NetworkEngine(); val client = WebDavClient(engine, server.url("/dav/"), "Basic fixture-only")
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", outside.url("/")))
            assertTrue(runCatching { client.test() }.exceptionOrNull() is WebDavFailure)
            assertEquals(0, outside.requestCount)
            server.enqueue(MockResponse().setResponseCode(207).setBody("<!DOCTYPE d [<!ENTITY x SYSTEM 'file:///private'>]>" + collection))
            assertTrue(runCatching { client.test() }.isFailure)
            server.enqueue(MockResponse().setResponseCode(207).setBody(collection.replace("200 OK", "404 Not Found")))
            assertTrue(runCatching { client.test() }.isFailure)
            server.enqueue(MockResponse().setResponseCode(401))
            assertEquals(WebDavError.AUTH, (runCatching { client.read() }.exceptionOrNull() as WebDavFailure).kind)
            engine.change(NetworkProfile.FollowSystem)
            assertTrue(runCatching { client.read() }.exceptionOrNull() is StaleNetworkException)
            assertEquals(4, server.requestCount)
        } }
    }
    @Test fun existingResourceWithoutStrongVersionNeverUploads() = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setHeader("ETag", "W/\"weak\"").setBody(Buffer().write(data(1))))
            val client = WebDavClient(NetworkEngine(), server.url("/dav/"), "Basic fixture-only")
            val current = client.read(); assertNull(current.etag)
            assertTrue(runCatching { client.upload(data(2), current) }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }
}
