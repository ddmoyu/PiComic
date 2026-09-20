package io.github.ddmoyu.picomic

import io.github.ddmoyu.picomic.backup.*
import io.github.ddmoyu.picomic.content.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupContractTest {
    @Test fun allThemeChoicesSurvivePortableBackupAlongsideLegacyPreferences() {
        for (mode in io.github.ddmoyu.picomic.data.ThemeMode.entries) {
            val data = BackupData(preferences = listOf(TransferPreference("themeMode", mode.label, 100), TransferPreference("dark", "true", 90)))
            val restored = BackupCodec.decode(BackupCodec.encode(document(data))).data.preferences.associate { it.key to it.value }
            assertEquals(mode, io.github.ddmoyu.picomic.data.ThemeMode.fromPreferences(restored))
        }
        assertFalse(PortablePreferences.valid("themeMode", "unknown"))
    }
    private val comic = SavedComic("FUTURE_SOURCE", "opaque:id/1", "未知来源作品", "作者", "https://secret-query.invalid/image?token=secret", "[\"标签\"]", "Chinese", 1, 2)
    private val favorite = FavoriteEntity(comic.source, comic.id, 100)
    private val progress = ContentProgressEntity(comic.source, comic.id, "chapter-original", "page-original", 2, .37f, "纵向连续", 100)
    private fun document(data: BackupData) = BackupDocument("00000000-0000-4000-8000-000000000001", 1, 100, data)
    @Test fun sourceMetadataWithLongTitlesAndLineBreaksCanBeRestored() {
        val sourceComic = comic.copy(title = "长标题".repeat(1000) + "\n副标题", author = "作者甲\n作者乙")
        val restored = BackupCodec.decode(BackupCodec.encode(document(BackupData(listOf(sourceComic))))).data.comics.single()
        assertEquals(sourceComic.title, restored.title); assertEquals(sourceComic.author, restored.author)
        val invalid = sourceComic.copy(title = "异常\u0000标题")
        assertTrue(runCatching { BackupCodec.decode(BackupCodec.encode(document(BackupData(listOf(invalid))))) }.isFailure)
    }
    @Test fun portableRoundTripPreservesUnknownSourcesAndDeletesWithoutExportingSecretsOrUris() {
        val data = BackupData(listOf(comic), listOf(favorite.copy(deleted = true)), listOf(progress), preferences = listOf(
            TransferPreference("readingMode", "从右向左", 100), TransferPreference("downloadTarget", "content://private/tree/a", 100),
            TransferPreference("token", "do-not-export", 100), TransferPreference("eh.site", "exhentai.org", 100)))
        val bytes = BackupCodec.encode(document(data)); val text = bytes.toString(Charsets.UTF_8)
        assertFalse(text.contains("token")); assertFalse(text.contains("secret")); assertFalse(text.contains("content://")); assertFalse(text.contains("exhentai.org"))
        val restored = BackupCodec.decode(bytes).data
        assertEquals("FUTURE_SOURCE", restored.comics.single().source); assertNull(restored.comics.single().cover)
        assertTrue(restored.favorites.single().deleted); assertEquals("page-original", restored.progress.single().pageId)
        assertEquals(1, restored.preferences.size)
    }
    @Test fun invalidVersionsDuplicateIdsMissingMetadataAndSensitiveFieldsAreRejected() {
        val valid = BackupCodec.encode(document(BackupData(listOf(comic), listOf(favorite))))
        val changed = listOf<(JSONObject) -> Unit>(
            { it.put("schemaVersion", 2) },
            { it.getJSONObject("data").getJSONArray("favorites").put(it.getJSONObject("data").getJSONArray("favorites").getJSONObject(0)) },
            { it.getJSONObject("data").put("comics", org.json.JSONArray()) },
            { it.getJSONObject("data").getJSONArray("comics").getJSONObject(0).put("cover", "https://injected.invalid") },
            { it.getJSONObject("data").getJSONArray("preferences").put(JSONObject().put("key", "downloadTarget").put("value", "content://injected").put("deleted", false).put("updatedAt", 100)) },
            { it.getJSONObject("data").getJSONArray("favorites").getJSONObject(0).put("updatedAt", Long.MAX_VALUE) }
        )
        for (change in changed) assertTrue(runCatching { BackupCodec.decode(JSONObject(valid.toString(Charsets.UTF_8)).also(change).toString().toByteArray()) }.isFailure)
        assertTrue(runCatching { BackupCodec.decode(("[".repeat(1000) + "]".repeat(1000)).toByteArray()) }.isFailure)
        assertTrue(runCatching { BackupCodec.decode(byteArrayOf(0xc3.toByte(), 0x28)) }.isFailure)
    }
    @Test fun mergeNeverUsesFurthestPageOrResurrectsDeletedFavoritesWithoutChoice() {
        val local = BackupData(listOf(comic), listOf(favorite.copy(updatedAt = 200, deleted = true)), listOf(progress.copy(page = 2)))
        val remote = BackupData(listOf(comic.copy(cover = null)), listOf(favorite), listOf(progress.copy(page = 99, updatedAt = 300)))
        val preview = BackupMerge.preview(local, remote)
        assertEquals(2, preview.conflicts.size); assertEquals(setOf("FUTURE_SOURCE"), preview.unknownSources)
        assertTrue(runCatching { BackupMerge.resolve(preview, emptyMap()) }.isFailure)
        val kept = BackupMerge.resolve(preview, preview.conflicts.associate { it.key to MergeSide.LOCAL }, 400)
        assertTrue(kept.favorites.single().deleted); assertEquals(2, kept.progress.single().page)
        assertEquals(401L, kept.progress.single().updatedAt)
        val incoming = BackupMerge.resolve(preview, preview.conflicts.associate { it.key to MergeSide.INCOMING }, 400)
        assertFalse(incoming.favorites.single().deleted); assertEquals(99, incoming.progress.single().page)
        assertEquals(comic.cover, incoming.comics.single().cover)
    }
}
