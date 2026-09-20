package io.github.ddmoyu.picomic

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.download.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DownloadStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun task() = ComicSummary(ComicKey(Source.HITOMI, "fixture-${java.util.UUID.randomUUID()}"), "下载夹具").let {
        DownloadTask(DownloadTask.identity(it.key, "whole", "anonymous"), SavedComic.from(it), "whole", "全册", 1, "anonymous", DownloadStorage.INTERNAL)
    }
    private fun fixture(): File {
        val file = File.createTempFile("download-fixture-", ".png", context.cacheDir)
        val image = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888)
        try { image.eraseColor(android.graphics.Color.CYAN); file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { image.recycle() }
        return file
    }
    @Test fun verifiedFilesRemainIndependentOfCacheAndDetectCorruption() = runBlocking {
        val task = task(); val storage = DownloadStorage(context); val fixture = fixture()
        val page = DownloadPage(task.id, "original-page-id", 0, PageManifest.encode(PageRef("original-page-id", 0, "https://example.test/page.png")))
        try {
            val saved = storage.write(task, page, fixture)
            assertTrue(saved.complete); assertEquals("image/png", saved.mime); assertEquals(32, saved.width); assertTrue(storage.verify(task, saved))
            assertFalse(saved.uri!!.contains(context.cacheDir.absolutePath))
            File(android.net.Uri.parse(saved.uri).path!!).appendText("corrupted")
            assertFalse(storage.verify(task, saved))
            storage.remove(task, listOf(saved)); assertNull(storage.ownedUri(task, saved))
        } finally { storage.remove(task, listOf(page)); fixture.delete() }
    }
    @Test fun invalidImageAndPathNeverBecomeCompletedFiles() = runBlocking {
        val task = task(); val storage = DownloadStorage(context)
        val invalid = File.createTempFile("download-invalid-", ".bin", context.cacheDir).apply { writeText("<html>authentication required</html>") }
        val page = DownloadPage(task.id, "id", 0, "{}")
        try {
            assertTrue(runCatching { storage.write(task, page, invalid) }.exceptionOrNull() is ContentFailure)
            assertTrue(runCatching { storage.ownedUri(task.copy(id = "../escape"), page) }.isFailure)
            assertTrue(runCatching { storage.checkTarget("content://missing/tree/folder") }.exceptionOrNull() is SecurityException)
        } finally { invalid.delete() }
    }
    @Test fun queueReopenRetainsFinishedPagesAndRejectsChangedManifest() = runBlocking {
        val name = "downloads-test-${java.util.UUID.randomUUID()}.db"
        val task = task(); val pages = listOf(PageRef("stable-a", 0, "https://example.test/a"), PageRef("stable-b", 1, "https://example.test/b"))
        var db = Room.databaseBuilder(context, DownloadDatabase::class.java, name).build()
        try {
            val dao = db.downloads(); dao.enqueue(task); assertEquals(-1L, dao.enqueue(task))
            dao.manifest(task, pages)
            val first = dao.pages(task.id).first().copy(uri = "file://fixture", size = 100, checksum = "fixture-sha", mime = "image/png")
            dao.completed(first)
            db.close(); db = Room.databaseBuilder(context, DownloadDatabase::class.java, name).build()
            db.downloads().recover(System.currentTimeMillis())
            val restored = db.downloads().task(task.id)!!
            assertEquals(DownloadState.QUEUED.name, restored.state); assertEquals(1, restored.completed)
            db.downloads().manifest(restored, pages.map { it.copy(url = it.url + "?fresh=1") })
            assertTrue(db.downloads().pages(task.id).first().complete)
            assertTrue(runCatching { db.downloads().manifest(restored, pages.reversed().mapIndexed { index, page -> page.copy(index = index) }) }.exceptionOrNull() is ContentFailure)
            assertEquals(listOf("stable-a", "stable-b"), db.downloads().pages(task.id).map { it.pageId })
            db.downloads().delete(task.id); assertTrue(db.downloads().pages(task.id).isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
