package io.github.ddmoyu.picomic.content

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Small, memory-only cache owned by the app ViewModel. Context includes account and source settings. */
class ContentDetailCache(private val capacity: Int = 12) {
    private data class Key(val comic: ComicKey, val context: String)
    private val values = LinkedHashMap<Key, ComicDetails>(16, .75f, true)
    private val gate = Mutex()
    init { require(capacity > 0) }

    @Synchronized fun peek(comic: ComicKey, context: String): ComicDetails? = values[Key(comic, context)]

    suspend fun load(comic: ComicKey, context: String, force: Boolean = false, fetch: suspend () -> ComicDetails): ComicDetails = gate.withLock {
        if (!force) peek(comic, context)?.let { return@withLock it }
        val detail = fetch()
        currentCoroutineContext().ensureActive()
        synchronized(this) {
            values.keys.removeAll { it.comic == comic && it.context != context }
            values[Key(comic, context)] = detail
            while (values.size > capacity) values.remove(values.keys.first())
        }
        detail
    }
}
