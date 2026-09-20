package io.github.ddmoyu.picomic

import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.reader.ManagedDiskCache
import kotlinx.coroutines.*
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ManagedCacheTest {
    @Test fun quotaAndClearWaitForActiveFilesAndPreserveDownloads() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "cache-test-${java.util.UUID.randomUUID()}")
        val download = File.createTempFile("cache-independent-", ".test", context.filesDir).apply { writeText("persistent-download") }
        val cache = ManagedDiskCache(directory.toOkioPath(), 4096)
        try {
            val editor = cache.openEditor("image-a")!!
            FileSystem.SYSTEM.write(editor.data) { writeUtf8("a".repeat(1024)) }
            val active = editor.commitAndOpenSnapshot()!!
            cache.quota(512); assertTrue(cache.pending)
            cache.clear(); assertTrue(FileSystem.SYSTEM.exists(active.data))
            assertEquals("a".repeat(1024), FileSystem.SYSTEM.read(active.data) { readUtf8() })
            active.close()
            withTimeout(3000) { while (cache.size > 512 || cache.pending) delay(20) }
            assertNull(cache.openSnapshot("image-a")); assertEquals(512L, cache.maxSize)
            assertEquals("persistent-download", download.readText())
        } finally { cache.shutdown(); directory.deleteRecursively(); download.delete() }
    }
}
