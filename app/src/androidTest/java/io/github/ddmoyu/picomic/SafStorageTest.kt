package io.github.ddmoyu.picomic

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.download.*
import io.github.ddmoyu.picomic.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Opt-in: tools/check-saf.ps1 drives the real system directory picker on a test emulator. */
class SafStorageTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun systemGrantSupportsVerifiedWriteOwnedDeletionAndRevocation() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("saf") == "1")
        lateinit var vm: AppViewModel
        ui.runOnUiThread {
            vm = ViewModelProvider(ui.activity)[AppViewModel::class.java]
            vm.preference("downloadTarget", "internal")
            ui.activity.setContent { val state by vm.state.collectAsStateWithLifecycle(); PiComicTheme(false, false) { Surface { Column { DownloadLocationSettings(state, vm) } } } }
        }
        ui.onNodeWithText("设置下载目录").performClick()
        ui.waitUntil(120000) { vm.state.value.pref("downloadTarget", "internal") != "internal" }
        val target = vm.state.value.pref("downloadTarget")
        assertTrue("Only the dedicated fixture directory may be tested", Uri.parse(target).lastPathSegment.orEmpty().endsWith("PiComic-SAF-Fixture"))
        val context = ui.activity; val resolver = context.contentResolver
        val storage = DownloadStorage(context)
        val comic = ComicSummary(ComicKey(Source.HITOMI, "saf-${System.nanoTime()}"), "合成图目录测试")
        val task = DownloadTask(DownloadTask.identity(comic.key, "whole", "anonymous"), SavedComic.from(comic), "whole", "全册", 1, "anonymous", target)
        val page = DownloadPage(task.id, "synthetic", 0, "{}")
        val file = File.createTempFile("saf-fixture", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        try { bitmap.eraseColor(android.graphics.Color.CYAN); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
        val tree = Uri.parse(target)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val unrelated = DocumentsContract.createDocument(resolver, root, "text/plain", "preserve-${System.nanoTime()}.txt")!!
        resolver.openOutputStream(unrelated)!!.use { it.write("owned-by-fixture-user".toByteArray()) }
        try { runBlocking {
            storage.checkTarget(target)
            val saved = storage.write(task, page, file)
            assertTrue(storage.verify(task, saved)); assertEquals(48, saved.width)
            storage.remove(task, listOf(saved)); assertNull(storage.ownedUri(task, saved))
            assertEquals("owned-by-fixture-user", resolver.openInputStream(unrelated)!!.bufferedReader().use { it.readText() })
            DocumentsContract.deleteDocument(resolver, unrelated)
            resolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertTrue(runCatching { storage.checkTarget(target) }.exceptionOrNull() is SecurityException)
            assertTrue(runCatching { storage.write(task, page, file) }.exceptionOrNull() is SecurityException)
        } } finally {
            file.delete(); runCatching { DocumentsContract.deleteDocument(resolver, unrelated) }
            ui.runOnIdle { vm.preference("downloadTarget", "internal") }
        }
    }
}
