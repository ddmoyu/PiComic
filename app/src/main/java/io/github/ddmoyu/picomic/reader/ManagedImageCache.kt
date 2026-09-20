package io.github.ddmoyu.picomic.reader

import android.content.Context
import coil3.disk.DiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File

/** Reopen Coil's LRU at a new quota only after all readers/editors release their files. */
class ManagedDiskCache(override val directory: Path, initialLimit: Long) : DiskCache {
    private val lock = Any()
    private var desired = initialLimit
    private var cache = build(initialLimit)
    private var references = 0
    private var clearPending = false
    private var closed = false
    override val fileSystem = FileSystem.SYSTEM
    override val size get() = synchronized(lock) { cache.size }
    override val maxSize get() = synchronized(lock) { desired }
    val pending get() = synchronized(lock) { clearPending || desired != cache.maxSize }
    fun quota(bytes: Long) = synchronized(lock) { require(bytes > 0); desired = bytes; applyPending() }
    private fun build(bytes: Long) = DiskCache.Builder().directory(directory).maxSizeBytes(bytes).cleanupCoroutineContext(Dispatchers.IO).build()
    private fun applyPending() {
        if (references != 0 || closed) return
        if (clearPending) { cache.clear(); clearPending = false }
        if (desired != cache.maxSize) { cache.shutdown(); cache = build(desired); cache.size /* initialize journal and schedule LRU trim */ }
    }
    override fun openSnapshot(key: String): DiskCache.Snapshot? = synchronized(lock) {
        if (closed) null else cache.openSnapshot(key)?.let { references++; Snapshot(it) }
    }
    override fun openEditor(key: String): DiskCache.Editor? = synchronized(lock) {
        if (closed) null else cache.openEditor(key)?.let { references++; Editor(it) }
    }
    override fun remove(key: String) = synchronized(lock) { !closed && cache.remove(key) }
    override fun clear() = synchronized(lock) { clearPending = true; applyPending() }
    override fun shutdown() = synchronized(lock) { closed = true; cache.shutdown() }
    private inner class Snapshot(private val value: DiskCache.Snapshot) : DiskCache.Snapshot {
        private var done = false
        override val metadata get() = value.metadata
        override val data get() = value.data
        override fun close() = synchronized(lock) { if (!done) { done = true; try { value.close() } finally { references--; applyPending() } } }
        override fun closeAndOpenEditor(): DiskCache.Editor? = synchronized(lock) {
            if (done) return@synchronized null
            done = true
            try { value.closeAndOpenEditor()?.let { references++; Editor(it) } }
            finally { references--; applyPending() }
        }
    }
    private inner class Editor(private val value: DiskCache.Editor) : DiskCache.Editor {
        private var done = false
        override val metadata get() = value.metadata
        override val data get() = value.data
        override fun commit() = synchronized(lock) {
            if (!done) { done = true; try { value.commit() } finally { references--; applyPending() } }
        }
        override fun abort() = synchronized(lock) {
            if (!done) { done = true; try { value.abort() } finally { references--; applyPending() } }
        }
        override fun commitAndOpenSnapshot(): DiskCache.Snapshot? = synchronized(lock) {
            if (done) return@synchronized null
            done = true
            try { value.commitAndOpenSnapshot()?.let { references++; Snapshot(it) } }
            finally { references--; applyPending() }
        }
    }
}

object ManagedImageCache {
    @Volatile private var cache: ManagedDiskCache? = null
    fun bytes(label: String) = when (label) { "250 MB" -> 250_000_000L; "1 GB" -> 1_000_000_000L; "2 GB" -> 2_000_000_000L; else -> 500_000_000L }
    fun get(context: Context): ManagedDiskCache = cache ?: synchronized(this) {
        cache ?: ManagedDiskCache(File(context.cacheDir, "pages").toOkioPath(), bytes(context.getSharedPreferences("picomic_ui", 0).getString("pref.cache", "500 MB")!!)).also { cache = it }
    }
    suspend fun setQuota(context: Context, label: String) = withContext(Dispatchers.IO) { get(context).quota(bytes(label)) }
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        ReaderImages.loader(context).memoryCache?.clear()
        get(context).clear()
    }
}
