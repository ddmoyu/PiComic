package io.github.ddmoyu.picomic

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ContentLibraryTest {
    @Test fun undoNeverClobbersLaterFavoriteChangesAndHistoryDeletionPreservesTombstones() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ContentDatabase::class.java).build()
        val dao = database.content(); val comic = ComicSummary(ComicKey(Source.PICACG, "undo-work"), "测试作品")
        try {
            val first = dao.setFavorite(comic, true); assertTrue(dao.undoFavorite(first)); assertTrue(dao.favorite("PICACG", "undo-work")!!.deleted)
            val second = dao.setFavorite(comic, true); dao.setFavorite(comic, false)
            assertFalse(dao.undoFavorite(second)); assertTrue(dao.favorite("PICACG", "undo-work")!!.deleted)
            dao.record(comic, ContentProgress(comic.key, "chapter", "page", 1, 0f, "纵向连续"))
            dao.deleteHistory(comic.key); val deleted = dao.progressSnapshot().single()
            assertTrue(deleted.deleted); assertEquals("page", deleted.pageId)
            assertTrue(dao.backupSnapshot().progress.single().deleted)
        } finally { database.close() }
    }
    @Test fun favoritesAndRealPageIdsSurviveDatabaseReopenWithoutMergingSources() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "content-test-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, ContentDatabase::class.java, name).build()
        try {
            val repository = ContentLibrary(open())
            val first = ComicSummary(ComicKey(Source.PICACG, "same-id"), "作品一")
            val second = ComicSummary(ComicKey(Source.JMCOMIC, "same-id"), "作品二")
            repository.toggleFavorite(first); repository.toggleFavorite(second)
            repository.record(first, ContentProgress(first.key, "chapter-9", "page-original-id", 2, .37f, "从右向左"))
            repository.preference(first.key, "readingMode", "从右向左")
            repository.preference(second.key, "readingMode", "纵向连续")
            repository.search(Source.PICACG, "ＡＢＣ"); repository.search(Source.PICACG, "abc"); repository.search(Source.JMCOMIC, "abc")
            repository.flush(); repository.close()
            val reopened = ContentLibrary(open())
            val state = withTimeout(5000) { reopened.state.first { it.ready } }
            assertEquals(2, state.favorites.size)
            assertEquals("page-original-id", state.progress.single().pageId)
            assertEquals("chapter-9", state.progress.single().chapterId)
            assertEquals(.37f, state.progress.single().offset, .001f)
            assertEquals(2, state.searches.size)
            assertEquals("从右向左", state.preferences[first.key]?.get("readingMode"))
            assertEquals("纵向连续", state.preferences[second.key]?.get("readingMode"))
            reopened.toggleFavorite(first); reopened.flush()
            val updated = withTimeout(5000) { reopened.state.first { it.favorites.size == 1 } }
            assertEquals(second.key, updated.favorites.single())
            assertEquals(1, updated.progress.size)
            reopened.preference(first.key, "readingMode", null); reopened.flush()
            withTimeout(5000) { reopened.state.first { first.key !in it.preferences } }
            reopened.close()
            val database = open()
            assertTrue(database.content().favorite(first.key.source.name, first.key.id)!!.deleted)
            database.close()
        } finally { context.deleteDatabase(name) }
    }
    @Test fun versionOneDatabaseMigratesWithoutLosingFavorites() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "content-migrate-${java.util.UUID.randomUUID()}.db"
        val schema = org.json.JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("content-v1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        try {
            context.getDatabasePath(name).parentFile!!.mkdirs()
            android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
                val entities = schema.getJSONArray("entities")
                repeat(entities.length()) { index -> val entity = entities.getJSONObject(index); db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName"))) }
                val setup = schema.getJSONArray("setupQueries")
                repeat(setup.length()) { db.execSQL(setup.getString(it)) }
                db.execSQL("INSERT INTO favorites(source,id,updatedAt,deleted) VALUES('PICACG','old-work',123,0)")
                db.version = 1
            }
            val upgraded = Room.databaseBuilder(context, ContentDatabase::class.java, name).addMigrations(ContentDatabase.MIGRATION_1_2, ContentDatabase.MIGRATION_2_3).build()
            try {
                assertEquals("old-work", upgraded.content().favorites().first().single().id)
                upgraded.content().savePreference(WorkPreferenceEntity("PICACG", "old-work", "readingMode", "从右向左", 456))
                assertEquals("从右向左", upgraded.content().preferences().first().single().value)
            } finally { upgraded.close() }
        } finally { context.deleteDatabase(name) }
    }
}
