package io.github.ddmoyu.picomic.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Entity(tableName = "reading_progress", primaryKeys = ["source", "comicId"])
data class ReadingProgressEntity(
    val source: String,
    val comicId: Int,
    val chapter: Int,
    val page: Int,
    val pageId: String,
    val offsetRatio: Float,
    val mode: String,
    val updatedAt: Long
) {
    fun position(): ReadingPosition? = runCatching {
        ReadingPosition(Source.valueOf(source), comicId, chapter, page, offsetRatio, mode, updatedAt)
    }.getOrNull()
}

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC")
    suspend fun all(): List<ReadingProgressEntity>

    @Upsert suspend fun save(value: ReadingProgressEntity)

    @Query("DELETE FROM reading_progress") suspend fun clear()
}

@Database(entities = [ReadingProgressEntity::class], version = 1, exportSchema = true)
abstract class ReadingDatabase : RoomDatabase() {
    abstract fun progress(): ReadingProgressDao
}

data class ProgressSnapshot(
    val positions: List<ReadingPosition> = emptyList(),
    val loaded: Boolean = false,
    val error: String? = null
)

/** Serial app-lifetime writer: leaving a reader or clearing its ViewModel cannot cancel a save. */
class ReadingProgressRepository(private val database: ReadingDatabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operations = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val mutable = MutableStateFlow(ProgressSnapshot())
    val state = mutable.asStateFlow()

    init {
        scope.launch {
            try {
                mutable.value = ProgressSnapshot(database.progress().all().mapNotNull { it.position() }, true)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutable.value = ProgressSnapshot(loaded = true, error = "无法读取阅读记录，请检查可用存储空间。")
            }
            for (operation in operations) operation()
        }
    }

    fun save(position: ReadingPosition) {
        val safe = position.copy(
            chapter = position.chapter.coerceAtLeast(1), page = position.page.coerceAtLeast(1),
            offsetRatio = position.offsetRatio.takeIf { it.isFinite() }?.coerceIn(0f, .99999f) ?: 0f,
            updatedAt = System.currentTimeMillis()
        )
        operations.trySend {
            try {
                database.progress().save(ReadingProgressEntity(
                    safe.source.name, safe.comicId, safe.chapter, safe.page, "page-${safe.page}",
                    safe.offsetRatio, safe.mode, safe.updatedAt
                ))
                mutable.value = ProgressSnapshot(
                    listOf(safe) + mutable.value.positions.filterNot { it.source == safe.source && it.comicId == safe.comicId }, true
                )
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutable.value = mutable.value.copy(error = "阅读进度保存失败，请检查可用存储空间。")
            }
        }
    }

    suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        operations.send { done.complete(Unit) }
        done.await()
    }

    suspend fun clear() {
        val done = CompletableDeferred<Unit>()
        operations.send {
            try {
                database.progress().clear()
                mutable.value = ProgressSnapshot(loaded = true)
                done.complete(Unit)
            } catch (failure: Exception) { done.completeExceptionally(failure) }
        }
        done.await()
    }

    suspend fun close() { flush(); operations.close(); scope.cancel(); database.close() }

    companion object {
        @Volatile private var instance: ReadingProgressRepository? = null
        fun get(context: Context): ReadingProgressRepository = instance ?: synchronized(this) {
            instance ?: ReadingProgressRepository(Room.databaseBuilder(
                context.applicationContext, ReadingDatabase::class.java, "picomic.db"
            ).build()).also { instance = it }
        }
    }
}
