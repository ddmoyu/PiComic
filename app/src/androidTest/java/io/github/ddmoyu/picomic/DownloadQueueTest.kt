package io.github.ddmoyu.picomic

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.download.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class DownloadQueueTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun versionOneQueueMigratesWithPagesAndSubtitleFallback() = runBlocking {
        val name = "download-migration-${java.util.UUID.randomUUID()}.db"
        val schema = org.json.JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("download-v1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        try {
            context.getDatabasePath(name).parentFile!!.mkdirs()
            android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
                val entities = schema.getJSONArray("entities")
                repeat(entities.length()) { index ->
                    val entity = entities.getJSONObject(index); val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                    repeat(indices.length()) { db.execSQL(indices.getJSONObject(it).getString("createSql").replace("\${TABLE_NAME}", table)) }
                }
                val setup = schema.getJSONArray("setupQueries")
                repeat(setup.length()) { db.execSQL(setup.getString(it)) }
                db.execSQL("INSERT INTO download_tasks(id,chapterId,chapterTitle,chapterOrder,accountPartition,target,state,total,completed,createdAt,updatedAt,comic_source,comic_id,comic_title,comic_author,comic_tags) VALUES('task','chapter','全册',1,'anonymous','internal','DOWNLOADING',1,0,100,100,'EHENTAI','work','主标题','','[]')")
                db.execSQL("INSERT INTO download_pages(taskId,pageId,position,reference,size) VALUES('task','page-original',0,'{}',0)")
                db.version = 1
            }
            val upgraded = Room.databaseBuilder(context, DownloadDatabase::class.java, name).addMigrations(DownloadDatabase.MIGRATION_1_2).build()
            try {
                val task = upgraded.downloads().task("task")!!
                assertNull(task.subtitle); assertEquals("主标题", task.displayTitle(true))
                assertEquals("page-original", upgraded.downloads().pages("task").single().pageId)
                upgraded.downloads().recover(200); assertEquals("QUEUED", upgraded.downloads().task("task")!!.state)
                upgraded.downloads().save(task.copy(subtitle = "副标题"))
                assertEquals("副标题", upgraded.downloads().task("task")!!.displayTitle(true))
                assertEquals("主标题", upgraded.downloads().task("task")!!.displayTitle(false))
            } finally { upgraded.close() }
        } finally { context.deleteDatabase(name) }
    }
    private class Fixture : DownloadAccess {
        var account = "account-a"
        var failure: ContentFailure? = null
        val calls = AtomicInteger()
        var blockPage = -1
        var blocked = CompletableDeferred<Unit>()
        val chapters = listOf(Chapter("chapter-a", "第一话", 1), Chapter("chapter-b", "第二话", 2))
        override suspend fun partition(source: Source) = account
        override suspend fun pages(task: DownloadTask): List<PageRef> {
            if (task.accountPartition != account) throw ContentFailure(ContentFailureKind.LOGIN, "原账号待恢复")
            failure?.let { throw it }
            return (0..2).map { PageRef("stable-$it", it, "https://fixture.test/$it", 24, 36) }
        }
        override suspend fun fetch(task: DownloadTask, page: DownloadPage, destination: File) {
            calls.incrementAndGet()
            if (page.position == blockPage) { blocked.complete(Unit); awaitCancellation() }
            val bitmap = Bitmap.createBitmap(24, 36, Bitmap.Config.ARGB_8888)
            try { bitmap.eraseColor(android.graphics.Color.MAGENTA); destination.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
        }
    }
    @Test fun pauseRecoveryAndOfflineDoNotRefetchVerifiedPagesOrNeedLogin() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java).build()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val fixture = Fixture(); val repo = DownloadRepository(context, db.downloads(), fixture, { 3 }, { DownloadStorage.INTERNAL }, owner)
        val comic = ComicSummary(ComicKey(Source.HITOMI, "fixture-${java.util.UUID.randomUUID()}"), "合成测试")
        val id = repo.enqueue(comic, fixture.chapters.take(1)).single()
        try {
            fixture.blockPage = 1; repo.start()
            withTimeout(10000) { fixture.blocked.await() }; repo.pause(id)
            assertEquals("PAUSED", db.downloads().task(id)!!.state); assertEquals(1, db.downloads().task(id)!!.completed)
            fixture.blockPage = -1; repo.resume(id); repo.start()
            waitState(db, id, "COMPLETED")
            assertEquals(4, fixture.calls.get()) // one done page, one cancelled page, two resumed pages
            fixture.account = "other-account"; fixture.failure = ContentFailure(ContentFailureKind.NETWORK, "断网")
            val offline = repo.offline(id)
            assertEquals(3, offline.pages.size); assertEquals(listOf("stable-0", "stable-1", "stable-2"), offline.pages.map { it.pageId })
            assertEquals(4, fixture.calls.get())
            File(Uri.parse(offline.pages[1].uri).path!!).writeText("tampered")
            assertTrue(runCatching { repo.offline(id) }.isFailure)
            assertEquals("FAILED", db.downloads().task(id)!!.state); assertEquals(2, db.downloads().task(id)!!.completed)
            repo.resume(id); repo.start(); waitState(db, id, "WAITING_AUTH")
            fixture.account = "account-a"; fixture.failure = null
            repo.resume(id); repo.start(); waitState(db, id, "COMPLETED")
            assertEquals(5, fixture.calls.get())
        } finally { repo.remove(id); owner.coroutineContext[Job]!!.cancelAndJoin(); db.close() }
    }
    @Test fun quotaPausesWithoutRetriesAndOldServiceCannotStopNewOwner() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO); val fixture = Fixture()
        val repo = DownloadRepository(context, db.downloads(), fixture, { 1 }, { DownloadStorage.INTERNAL }, scope)
        val comic = ComicSummary(ComicKey(Source.EHENTAI, "fixture-${java.util.UUID.randomUUID()}"), "额度夹具")
        val id = repo.enqueue(comic, fixture.chapters.take(1)).single()
        try {
            fixture.failure = ContentFailure(ContentFailureKind.QUOTA, "额度不足")
            repo.start("old"); waitState(db, id, "WAITING_QUOTA"); assertEquals(0, fixture.calls.get())
            fixture.failure = null; fixture.blockPage = 0
            repo.resume(id); repo.start("new"); withTimeout(10000) { fixture.blocked.await() }
            repo.stop(owner = "old")
            assertTrue(repo.running.value); assertEquals("DOWNLOADING", db.downloads().task(id)!!.state)
            repo.stop(owner = "new"); assertEquals("PAUSED", db.downloads().task(id)!!.state)
        } finally { repo.remove(id); scope.coroutineContext[Job]!!.cancelAndJoin(); db.close() }
    }
    private suspend fun waitState(db: DownloadDatabase, id: String, state: String) = withTimeout(15000) {
        while (db.downloads().task(id)?.state != state) delay(20)
    }
}
