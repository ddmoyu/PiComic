package io.github.ddmoyu.picomic.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Own this scheduler on one dispatcher (the reader's main scope).
 * Page turns only update queued work. Started downloads finish even after a fast jump.
 */
class ReaderPrefetcher<K>(private val scope: CoroutineScope, private val load: suspend (K) -> Boolean) {
    private class Entry { lateinit var job: Job; var running = false }
    private val entries = mutableMapOf<K, Entry>()
    private val permits = Semaphore(2)
    private var retained = emptySet<K>()

    fun update(ahead: List<K>, visible: Set<K>) {
        retained = ahead.toSet() + visible
        entries.filter { (key, entry) -> key !in retained && !entry.running }.keys
            .mapNotNull { entries.remove(it) }.forEach { it.job.cancel() }
        for (key in ahead) {
            if (key in entries || key in visible) continue
            val entry = Entry()
            entries[key] = entry
            entry.job = scope.launch(start = CoroutineStart.LAZY) {
                var succeeded = false
                try {
                    permits.withPermit { entry.running = true; succeeded = load(key) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The visible image handles errors; allow a later window update to retry.
                } finally {
                    entry.running = false
                    if ((!succeeded || key !in retained) && entries[key] === entry) entries.remove(key)
                }
            }
            entry.job.start()
        }
    }

    fun clear() {
        val previous = entries.values.toList()
        retained = emptySet()
        entries.clear()
        previous.forEach { it.job.cancel() }
    }
}
