package io.github.ddmoyu.picomic

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.backup.*
import io.github.ddmoyu.picomic.content.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BackupTransactionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val first = SavedComic("HITOMI", "backup-first", "原作品", "", null, "[]", null, 1, 10)
    private val next = first.copy(source = "UNKNOWN_SOURCE", id = "backup-next", title = "导入作品")
    @Test fun changedLocalSnapshotRequiresNewPreviewAndCannotBeOverwritten() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ContentDatabase::class.java).build()
        try {
            val dao = db.content(); dao.saveComic(first); dao.saveFavorite(FavoriteEntity(first.source, first.id, 10))
            val proposed = BackupMerge.preview(dao.backupSnapshot(), BackupData(listOf(next), listOf(FavoriteEntity(next.source, next.id, 20))))
            dao.saveFavorite(FavoriteEntity(first.source, first.id, 30, true))
            assertTrue(runCatching { dao.applyBackup(proposed.fingerprint, BackupMerge.resolve(proposed, emptyMap())) }.isFailure)
            assertTrue(dao.favorite(first.source, first.id)!!.deleted)
            assertEquals(listOf(first), dao.comicSnapshot())
        } finally { db.close() }
    }
    @Test fun failedImportRollsBackMetadataFavoritesAndPreferencesTogether() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ContentDatabase::class.java).build()
        try {
            val dao = db.content(); dao.saveComic(first); dao.saveFavorite(FavoriteEntity(first.source, first.id, 10))
            dao.savePortable(PortablePreferenceEntity("readingMode", "纵向连续", 10))
            val before = dao.backupSnapshot()
            val incoming = BackupData(listOf(next), listOf(FavoriteEntity(next.source, next.id, 20)), listOf(ContentProgressEntity(next.source, next.id, "chapter", "page", 3, .5f, "从右向左", 20)),
                preferences = listOf(TransferPreference("readingMode", "从右向左", 20)))
            val proposed = BackupMerge.preview(before, incoming)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_fixture BEFORE INSERT ON progress BEGIN SELECT RAISE(ABORT, 'fixture write failure'); END")
            assertTrue(runCatching { dao.applyBackup(proposed.fingerprint, BackupMerge.resolve(proposed, proposed.conflicts.associate { it.key to MergeSide.INCOMING })) }.isFailure)
            assertEquals(BackupCodec.fingerprint(before), BackupCodec.fingerprint(dao.backupSnapshot()))
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_fixture")
            dao.applyBackup(proposed.fingerprint, BackupMerge.resolve(proposed, proposed.conflicts.associate { it.key to MergeSide.INCOMING }))
            assertEquals("从右向左", dao.portableSnapshot().single().value)
            assertEquals("UNKNOWN_SOURCE", dao.progressSnapshot().single().source)
            assertEquals(2, dao.comicSnapshot().size)
        } finally { db.close() }
    }
}
