package io.github.ddmoyu.picomic

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.network.*
import io.github.ddmoyu.picomic.source.jm.*
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in backend probe. No UI, websites, screenshots or retained comic images. */
class JmLiveProbeTest {
    @Test fun requestedAlbumParsesAndSampleImagesCanBeDecoded() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val requested = InstrumentationRegistry.getArguments().getString("liveJmAlbum")
        assumeTrue(!requested.isNullOrBlank())
        val id = JmProtocol.id(requested!!)
        val engine = NetworkEngine(NetworkProfile.HttpProxy("10.0.2.2", 7897))
        val source = JmSource(JmClient(engine))
        val detail = source.details(id)
        assertEquals(id, detail.summary.key.id)
        assertTrue(detail.chapters.isNotEmpty())
        assertEquals(detail.chapters.size, detail.chapters.map { it.id }.toSet().size)
        assertEquals(detail.chapters.size, detail.chapters.map { it.order }.toSet().size)
        val samples = listOf(detail.chapters.first(), detail.chapters[detail.chapters.size / 2], detail.chapters.last()).distinctBy { it.id }
        val results = samples.map { chapter ->
            val pages = source.pages(id, chapter)
            assertTrue(pages.isNotEmpty())
            assertEquals(pages.size, pages.map { it.id }.toSet().size)
            val first = pages.first()
            val rule = first.jm!!
            assertEquals((if (chapter.id == "whole") id else chapter.id).toLong(), rule.photoId)
            val bytes = engine.newCall(Request.Builder().url(first.url).header("User-Agent", JmProtocol.USER_AGENT).build())
                .readBounded(24 * 1024 * 1024) { assertEquals(200, it.code) }
            val target = File.createTempFile("jm-live-probe-", ".image", instrumentation.targetContext.cacheDir)
            try {
                JmImageFetcher.process(bytes, rule, target)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(target.path, bounds)
                assertTrue(bounds.outWidth > 0 && bounds.outHeight > 0)
                "${chapter.id}:${pages.size}:${bounds.outWidth}x${bounds.outHeight}"
            } finally { target.delete(); bytes.fill(0) }
        }
        instrumentation.sendStatus(0, Bundle().apply {
            putString("album_id", id); putInt("chapter_count", detail.chapters.size)
            putString("sample_id_pages_dimensions", results.joinToString(","))
        })
    }
}
