package io.github.ddmoyu.picomic.download

import android.content.Context
import androidx.room.*
import io.github.ddmoyu.picomic.content.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.security.MessageDigest

enum class DownloadState { QUEUED, RESOLVING, DOWNLOADING, PROCESSING, COMPLETED, PAUSED, WAITING_AUTH, WAITING_NETWORK, WAITING_STORAGE, WAITING_QUOTA, FAILED, DELETING }

@Entity(tableName = "download_tasks")
data class DownloadTask(
    @PrimaryKey val id: String,
    @Embedded(prefix = "comic_") val comic: SavedComic,
    val chapterId: String, val chapterTitle: String, val chapterOrder: Int, val accountPartition: String,
    val target: String, val state: String = DownloadState.QUEUED.name, val total: Int = 0, val completed: Int = 0,
    val message: String? = null, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt, val subtitle: String? = null
) {
    fun key() = comic.summary().key
    fun chapter() = Chapter(chapterId, chapterTitle, chapterOrder, total.takeIf { it > 0 })
    fun displayTitle(preferSubtitle: Boolean) = subtitle?.takeIf { preferSubtitle && comic.source == "EHENTAI" && it.isNotBlank() } ?: comic.title
    companion object {
        fun identity(key: ComicKey, chapterId: String, partition: String) = digest("${key.stable}/${chapterId.length}:$chapterId/$partition".toByteArray())
    }
}
@Entity(tableName = "download_pages", primaryKeys = ["taskId", "pageId"],
    foreignKeys = [ForeignKey(entity = DownloadTask::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["taskId", "position"], unique = true)])
data class DownloadPage(
    val taskId: String, val pageId: String, val position: Int, val reference: String,
    val uri: String? = null, val size: Long = 0, val checksum: String? = null, val mime: String? = null,
    val width: Int? = null, val height: Int? = null
) {
    val fileName get() = digest(pageId.toByteArray()) + ".image"
    val complete get() = uri != null && size > 0 && checksum != null && mime != null
}
@Dao abstract class DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC") abstract fun observe(): Flow<List<DownloadTask>>
    @Query("SELECT * FROM download_tasks ORDER BY createdAt") abstract suspend fun tasks(): List<DownloadTask>
    @Query("SELECT * FROM download_tasks WHERE id=:id") abstract suspend fun task(id: String): DownloadTask?
    @Query("SELECT * FROM download_pages WHERE taskId=:id ORDER BY position") abstract suspend fun pages(id: String): List<DownloadPage>
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun enqueue(task: DownloadTask): Long
    @Upsert abstract suspend fun save(task: DownloadTask)
    @Upsert abstract suspend fun save(page: DownloadPage)
    @Insert abstract suspend fun insertPages(pages: List<DownloadPage>)
    @Query("DELETE FROM download_tasks WHERE id=:id") abstract suspend fun delete(id: String)
    @Query("UPDATE download_tasks SET state='QUEUED', message='已恢复任务，等待继续', updatedAt=:now WHERE state IN ('RESOLVING','DOWNLOADING','PROCESSING')") abstract suspend fun recover(now: Long)
    @Transaction open suspend fun manifest(task: DownloadTask, pages: List<PageRef>) {
        require(pages.isNotEmpty() && pages.map { it.id }.distinct().size == pages.size && pages.map { it.index } == pages.indices.toList())
        val existing = this.pages(task.id)
        if (existing.isEmpty()) insertPages(pages.map { DownloadPage(task.id, it.id, it.index, PageManifest.encode(it), width = it.width, height = it.height) })
        else {
            if (existing.map { it.pageId } != pages.map { it.id }) throw ContentFailure(ContentFailureKind.PARSE, "章节页序发生变化，已保留原下载；请删除该任务后重新下载")
            pages.forEachIndexed { index, page -> save(existing[index].copy(reference = PageManifest.encode(page))) }
        }
        save(task.copy(total = pages.size, state = DownloadState.DOWNLOADING.name, message = null, updatedAt = System.currentTimeMillis()))
    }
    @Transaction open suspend fun completed(page: DownloadPage) {
        require(page.complete)
        save(page)
        val task = task(page.taskId) ?: error("下载任务不存在")
        val count = pages(page.taskId).count { it.complete }
        save(task.copy(completed = count, updatedAt = System.currentTimeMillis()))
    }
    @Transaction open suspend fun invalidate(page: DownloadPage) {
        if (!page.complete) return
        save(page.copy(uri = null, size = 0, checksum = null, mime = null))
        task(page.taskId)?.let { task -> save(task.copy(completed = pages(task.id).count { it.complete }, updatedAt = System.currentTimeMillis())) }
    }
}
@Database(entities = [DownloadTask::class, DownloadPage::class], version = 2, exportSchema = true)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("ALTER TABLE download_tasks ADD COLUMN subtitle TEXT") }
        }
        @Volatile private var instance: DownloadDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, DownloadDatabase::class.java, "downloads.db").addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Device-local manifest only. Credentials/headers are absent and these references never enter user backups. */
object PageManifest {
    fun encode(page: PageRef): String = JSONObject().put("version", 1).put("id", page.id).put("index", page.index).put("url", page.url)
        .put("width", page.width).put("height", page.height).put("resolver", page.resolver).apply { page.jm?.let {
            put("jm", JSONObject().put("photo", it.photoId).put("scramble", it.scrambleId).put("name", it.filename).put("decoded", it.alreadyDecoded).put("version", it.version))
        } }.toString()
    fun decode(value: String): PageRef {
        require(value.length <= 32768)
        val data = JSONObject(value)
        require(data.getInt("version") == 1)
        val jm = data.optJSONObject("jm")?.let { JmImageRule(it.getLong("photo"), it.getLong("scramble"), it.getString("name"), it.getBoolean("decoded"), it.getInt("version")) }
        return PageRef(data.getString("id"), data.getInt("index"), data.getString("url"),
            data.optInt("width").takeIf { it > 0 }, data.optInt("height").takeIf { it > 0 }, jm, data.optString("resolver").takeIf(String::isNotBlank))
    }
}
