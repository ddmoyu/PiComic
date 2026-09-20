package io.github.ddmoyu.picomic.content

import android.content.Context
import androidx.room.*
import io.github.ddmoyu.picomic.data.Source
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.JSONArray

@Entity(tableName = "comics", primaryKeys = ["source", "id"])
data class SavedComic(
    val source: String, val id: String, val title: String, val author: String, val cover: String?,
    val tags: String, val language: String?, val chapterCount: Int?, val pageCount: Int?
) {
    fun summary() = ComicSummary(ComicKey(Source.valueOf(source), id), title, author, cover,
        JSONArray(tags).let { array -> List(array.length()) { array.getString(it) } }, language, chapterCount, pageCount)
    companion object {
        fun from(comic: ComicSummary) = SavedComic(comic.key.source.name, comic.key.id, comic.title, comic.author,
            comic.cover, JSONArray(comic.tags).toString(), comic.language, comic.chapterCount, comic.pageCount)
    }
}
@Entity(tableName = "favorites", primaryKeys = ["source", "id"])
data class FavoriteEntity(val source: String, val id: String, val updatedAt: Long, val deleted: Boolean = false)
data class FavoriteChange(val previous: FavoriteEntity?, val applied: FavoriteEntity)
@Entity(tableName = "progress", primaryKeys = ["source", "id"])
data class ContentProgressEntity(
    val source: String, val id: String, val chapterId: String, val pageId: String, val page: Int,
    val offset: Float, val mode: String, val updatedAt: Long, val deleted: Boolean = false
) {
    fun progress() = ContentProgress(ComicKey(Source.valueOf(source), id), chapterId, pageId, page, offset, mode, updatedAt)
}
@Entity(tableName = "search_history", primaryKeys = ["source", "normalized"])
data class SearchEntity(val source: String, val normalized: String, val query: String, val updatedAt: Long)
@Entity(tableName = "work_preferences", primaryKeys = ["source", "id", "name"])
data class WorkPreferenceEntity(val source: String, val id: String, val name: String, val value: String, val updatedAt: Long, val deleted: Boolean = false)
@Entity(tableName = "portable_preferences")
data class PortablePreferenceEntity(@PrimaryKey val key: String, val value: String, val updatedAt: Long, val deleted: Boolean = false)

@Dao abstract class ContentDao {
    @Query("SELECT * FROM comics") abstract fun comics(): Flow<List<SavedComic>>
    @Query("SELECT * FROM favorites ORDER BY updatedAt DESC") abstract fun favorites(): Flow<List<FavoriteEntity>>
    @Query("SELECT * FROM progress ORDER BY updatedAt DESC") abstract fun progress(): Flow<List<ContentProgressEntity>>
    @Query("SELECT * FROM progress WHERE source=:source AND id=:id") abstract suspend fun readingPosition(source: String, id: String): ContentProgressEntity?
    @Query("SELECT * FROM search_history ORDER BY updatedAt DESC") abstract fun searches(): Flow<List<SearchEntity>>
    @Query("SELECT * FROM work_preferences") abstract fun preferences(): Flow<List<WorkPreferenceEntity>>
    @Query("SELECT * FROM favorites WHERE source=:source AND id=:id") abstract suspend fun favorite(source: String, id: String): FavoriteEntity?
    @Upsert abstract suspend fun saveComic(comic: SavedComic)
    @Upsert abstract suspend fun saveFavorite(value: FavoriteEntity)
    @Upsert abstract suspend fun saveProgress(value: ContentProgressEntity)
    @Upsert abstract suspend fun saveSearch(value: SearchEntity)
    @Upsert abstract suspend fun savePreference(value: WorkPreferenceEntity)
    @Upsert abstract suspend fun savePortable(value: PortablePreferenceEntity)
    @Query("SELECT * FROM portable_preferences") abstract suspend fun portableSnapshot(): List<PortablePreferenceEntity>
    @Query("SELECT * FROM portable_preferences") abstract fun portableChanges(): Flow<List<PortablePreferenceEntity>>
    @Query("SELECT * FROM comics") abstract suspend fun comicSnapshot(): List<SavedComic>
    @Query("SELECT * FROM favorites") abstract suspend fun favoriteSnapshot(): List<FavoriteEntity>
    @Query("SELECT * FROM progress") abstract suspend fun progressSnapshot(): List<ContentProgressEntity>
    @Query("SELECT * FROM work_preferences") abstract suspend fun preferenceSnapshot(): List<WorkPreferenceEntity>
    @Transaction open suspend fun backupSnapshot(): io.github.ddmoyu.picomic.backup.BackupData = io.github.ddmoyu.picomic.backup.BackupData(
        comicSnapshot(), favoriteSnapshot(), progressSnapshot(), preferenceSnapshot(), portableSnapshot().map { io.github.ddmoyu.picomic.backup.TransferPreference(it.key, it.value, it.updatedAt, it.deleted) })
    @Transaction open suspend fun applyBackup(fingerprint: String, data: io.github.ddmoyu.picomic.backup.BackupData) {
        check(io.github.ddmoyu.picomic.backup.BackupCodec.fingerprint(backupSnapshot()) == fingerprint) { "本地数据在预览后已变化，请重新预览" }
        data.comics.forEach { saveComic(it) }; data.favorites.forEach { saveFavorite(it) }; data.progress.forEach { saveProgress(it) }
        data.workPreferences.forEach { savePreference(it) }
        data.preferences.forEach { savePortable(PortablePreferenceEntity(it.key, it.value, it.updatedAt, it.deleted)) }
    }
    @Query("DELETE FROM search_history WHERE source=:source AND normalized NOT IN (SELECT normalized FROM search_history WHERE source=:source ORDER BY updatedAt DESC LIMIT 30)") abstract suspend fun trimSearches(source: String)
    @Query("DELETE FROM search_history WHERE source=:source") abstract suspend fun clearSearches(source: String)
    @Transaction open suspend fun toggleFavorite(comic: ComicSummary) {
        saveComic(SavedComic.from(comic))
        val old = favorite(comic.key.source.name, comic.key.id)
        saveFavorite(FavoriteEntity(comic.key.source.name, comic.key.id, System.currentTimeMillis(), old?.deleted == false))
    }
    @Transaction open suspend fun setFavorite(comic: ComicSummary, selected: Boolean): FavoriteChange {
        saveComic(SavedComic.from(comic))
        val old = favorite(comic.key.source.name, comic.key.id)
        val value = FavoriteEntity(comic.key.source.name, comic.key.id, maxOf(System.currentTimeMillis(), (old?.updatedAt ?: 0) + 1), !selected)
        saveFavorite(value); return FavoriteChange(old, value)
    }
    @Transaction open suspend fun undoFavorite(change: FavoriteChange): Boolean {
        if (favorite(change.applied.source, change.applied.id) != change.applied) return false
        saveFavorite(change.applied.copy(deleted = change.previous?.deleted ?: true, updatedAt = maxOf(System.currentTimeMillis(), change.applied.updatedAt + 1)))
        return true
    }
    @Transaction open suspend fun deleteHistory(key: ComicKey?) {
        progressSnapshot().filter { !it.deleted && (key == null || it.source == key.source.name && it.id == key.id) }.forEach {
            saveProgress(it.copy(deleted = true, updatedAt = maxOf(System.currentTimeMillis(), it.updatedAt + 1)))
        }
    }
    @Transaction open suspend fun record(comic: ComicSummary, value: ContentProgress) {
        require(value.key == comic.key)
        saveComic(SavedComic.from(comic))
        saveProgress(ContentProgressEntity(value.key.source.name, value.key.id, value.chapterId, value.pageId,
            value.page.coerceAtLeast(1), value.offset.takeIf { it.isFinite() }?.coerceIn(0f, .99999f) ?: 0f, value.mode, value.updatedAt))
    }
    @Transaction open suspend fun search(source: Source, query: String) {
        val text = query.trim().take(300)
        if (text.isEmpty()) return
        saveSearch(SearchEntity(source.name, ContentFilter.normalize(text), text, System.currentTimeMillis()))
        trimSearches(source.name)
    }
}
@Database(entities = [SavedComic::class, FavoriteEntity::class, ContentProgressEntity::class, SearchEntity::class, WorkPreferenceEntity::class, PortablePreferenceEntity::class], version = 3, exportSchema = true)
abstract class ContentDatabase : RoomDatabase() {
    abstract fun content(): ContentDao
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `work_preferences` (`source` TEXT NOT NULL, `id` TEXT NOT NULL, `name` TEXT NOT NULL, `value` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`source`, `id`, `name`))")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `portable_preferences` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            }
        }
        @Volatile private var instance: ContentDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ContentDatabase::class.java, "content.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
data class LibrarySnapshot(
    val comics: Map<ComicKey, ComicSummary> = emptyMap(), val favorites: Set<ComicKey> = emptySet(),
    val progress: List<ContentProgress> = emptyList(), val searches: List<SearchEntity> = emptyList(), val ready: Boolean = false,
    val preferences: Map<ComicKey, Map<String, String>> = emptyMap()
)
class ContentLibrary(private val database: ContentDatabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operations = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val dao = database.content()
    val state = combine(dao.comics(), dao.favorites(), dao.progress(), dao.searches(), dao.preferences()) { comics, favorites, progress, searches, preferences ->
        LibrarySnapshot(comics.mapNotNull { runCatching { val summary = it.summary(); summary.key to summary }.getOrNull() }.toMap(),
            favorites.filterNot { it.deleted }.mapNotNull { runCatching { ComicKey(Source.valueOf(it.source), it.id) }.getOrNull() }.toSet(),
            progress.filterNot { it.deleted }.mapNotNull { runCatching { it.progress() }.getOrNull() }, searches, true,
            preferences.filterNot { it.deleted }.mapNotNull { preference -> runCatching { ComicKey(Source.valueOf(preference.source), preference.id) to (preference.name to preference.value) }.getOrNull() }.groupBy({ it.first }, { it.second }).mapValues { it.value.toMap() })
    }.catch { emit(LibrarySnapshot()); mutableError.value = "无法读取本地书架，请检查存储空间" }
        .stateIn(scope, SharingStarted.Eagerly, LibrarySnapshot())
    init { scope.launch { for (operation in operations) try { operation() } catch (e: Exception) {
        if (e is CancellationException) throw e
        mutableError.value = "本地数据保存失败，请检查存储空间"
    } } }
    fun toggleFavorite(comic: ComicSummary) { operations.trySend { dao.toggleFavorite(comic) } }
    private suspend fun <T> perform(action: suspend () -> T): T {
        val result = CompletableDeferred<T>()
        operations.send { try { result.complete(action()) } catch (e: Exception) { result.completeExceptionally(e); throw e } }
        return result.await()
    }
    suspend fun favorite(comic: ComicSummary, selected: Boolean) = perform { dao.setFavorite(comic, selected) }
    suspend fun undoFavorite(change: FavoriteChange) = perform { dao.undoFavorite(change) }
    fun deleteHistory(key: ComicKey? = null) { operations.trySend { dao.deleteHistory(key) } }
    fun record(comic: ComicSummary, progress: ContentProgress) { operations.trySend { dao.record(comic, progress) } }
    // Read after queued writes; the UI flow may still contain the previous reading position.
    suspend fun readingPosition(key: ComicKey): ContentProgress? = perform {
        dao.readingPosition(key.source.name, key.id)?.takeUnless { it.deleted }?.progress()
    }
    fun search(source: Source, query: String) { operations.trySend { dao.search(source, query) } }
    fun clearSearches(source: Source) { operations.trySend { dao.clearSearches(source.name) } }
    fun preference(key: ComicKey, name: String, value: String?) {
        require(name in setOf("readingMode", "readerBackground", "readerOrientation", "readerBrightness"))
        operations.trySend { dao.savePreference(WorkPreferenceEntity(key.source.name, key.id, name, value.orEmpty(), System.currentTimeMillis(), value == null)) }
    }
    suspend fun flush() { val done = CompletableDeferred<Unit>(); operations.send { done.complete(Unit) }; done.await() }
    suspend fun awaitReady(): LibrarySnapshot {
        val result = combine(state, error) { snapshot, failure -> snapshot to failure }.first { it.first.ready || it.second != null }
        if (!result.first.ready) throw java.io.IOException(result.second ?: "无法读取本地书架")
        return result.first
    }
    suspend fun close() { flush(); operations.close(); scope.coroutineContext[Job]!!.cancelAndJoin(); database.close() }
    companion object {
        @Volatile private var instance: ContentLibrary? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: ContentLibrary(ContentDatabase.get(context)).also { instance = it } }
    }
}
