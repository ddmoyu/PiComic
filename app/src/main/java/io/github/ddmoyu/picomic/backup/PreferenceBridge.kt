package io.github.ddmoyu.picomic.backup

import android.content.Context
import io.github.ddmoyu.picomic.content.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Portable settings join the same Room transaction as the library; the legacy UI store is a projection. */
class PreferenceBridge private constructor(context: Context) {
    private val store = context.getSharedPreferences("picomic_ui", 0)
    private val dao = ContentDatabase.get(context).content()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val lock = Mutex()
    @Volatile private var failure: Exception? = null
    private val ready = scope.async {
        try { lock.withLock { reconcile() } } catch (e: CancellationException) { throw e } catch (e: Exception) { failure = e }
    }
    private suspend fun reconcile() {
            val existing = dao.portableSnapshot().associateBy { it.key }
            val legacy = store.all.mapNotNull { (key, value) ->
                val portable = when { key.startsWith("pref.") -> key.removePrefix("pref."); key == "keywords" -> "filter.keywords"; key == "languages" -> "filter.languages"; else -> null }
                portable?.takeIf { PortablePreferences.allowed(it) && value is String && PortablePreferences.valid(it, value) }?.let { it to value.toString() }
            }
            for ((key, value) in legacy) {
                val savedTime = store.getLong("backup.time.$key", 0)
                val row = existing[key]
                if (row == null || savedTime > row.updatedAt) dao.savePortable(PortablePreferenceEntity(key, value, savedTime.takeIf { it > 0 } ?: System.currentTimeMillis()))
            }
            project(dao.portableSnapshot())
    }
    init { scope.launch {
        ready.await()
        for (command in commands) try { command() } catch (e: CancellationException) { throw e } catch (e: Exception) { failure = e }
    } }
    fun save(key: String, value: String) {
        if (!PortablePreferences.allowed(key)) return
        require(PortablePreferences.valid(key, value)) { "设置值不在支持范围内" }
        val time = maxOf(System.currentTimeMillis(), store.getLong("backup.time.$key", 0) + 1)
        store.edit().putLong("backup.time.$key", time).apply()
        commands.trySend { lock.withLock { dao.savePortable(PortablePreferenceEntity(key, value, time)) } }
    }
    suspend fun flush() {
        ready.await()
        val done = CompletableDeferred<Unit>()
        commands.send {
            try { lock.withLock { reconcile() }; failure = null; done.complete(Unit) }
            catch (e: Exception) { failure = e; done.completeExceptionally(java.io.IOException("偏好写入失败，请检查存储空间后重试")); throw e }
        }
        done.await()
    }
    suspend fun refresh() = withContext(Dispatchers.IO) { flush(); lock.withLock { project(dao.portableSnapshot()) } }
    private fun project(rows: List<PortablePreferenceEntity>) {
        val editor = store.edit()
        for (row in rows) {
            if (!PortablePreferences.allowed(row.key) || row.updatedAt < store.getLong("backup.time.${row.key}", 0)) continue
            val key = when (row.key) { "filter.keywords" -> "keywords"; "filter.languages" -> "languages"; else -> "pref.${row.key}" }
            if (row.deleted) editor.remove(key) else editor.putString(key, row.value)
            editor.putLong("backup.time.${row.key}", row.updatedAt)
        }
        check(editor.commit()) { "偏好投影保存失败" }
    }
    companion object {
        @Volatile private var instance: PreferenceBridge? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: PreferenceBridge(context.applicationContext).also { instance = it } }
    }
}
