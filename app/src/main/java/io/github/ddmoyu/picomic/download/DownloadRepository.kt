package io.github.ddmoyu.picomic.download

import android.content.Context
import io.github.ddmoyu.picomic.content.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

class StorageFailure(cause: Exception) : IOException("下载目录不可写、空间不足或授权失效，请检查目录后继续", cause)
data class OfflineChapter(val task: DownloadTask, val pages: List<DownloadPage>, val chapters: List<DownloadTask>)

/** The service owns execution; Room owns intent. UI destruction never cancels a chapter. */
class DownloadRepository internal constructor(
    private val context: Context, private val dao: DownloadDao, private val access: DownloadAccess,
    private val parallel: () -> Int, private val target: () -> String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO), private val networkAllowed: () -> Boolean = { true }
) {
    val storage = DownloadStorage(context)
    private val log = io.github.ddmoyu.picomic.data.EventLog.get(context)
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    val tasks = dao.observe().catch { mutableError.value = "无法读取下载记录，请检查存储空间" }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val mutex = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val jobs = mutableMapOf<String, Job>()
    private val mutableRunning = MutableStateFlow(false)
    private var owner: String? = null
    val running = mutableRunning.asStateFlow()
    private val temporary = File(context.filesDir, "download-parts")
    private val initialized = scope.async { dao.recover(System.currentTimeMillis()) }
    init {
        scope.launch {
            try { initialized.await() } catch (e: CancellationException) { throw e } catch (_: Exception) { mutableError.value = "下载记录无法恢复，请检查存储空间"; return@launch }
            for (signal in wake) try { mutex.withLock {
                jobs.entries.removeAll { it.value.isCompleted }
                if (!mutableRunning.value) continue
                val all = dao.tasks()
                val activeSources = all.filter { it.id in jobs }.groupingBy { it.comic.source }.eachCount().toMutableMap()
                for (task in all.filter { it.state == DownloadState.QUEUED.name && it.id !in jobs }) {
                    if (jobs.size >= parallel().coerceIn(1, 6)) break
                    val count = activeSources[task.comic.source] ?: 0
                    if (count >= 2) continue
                    val job = scope.launch(start = CoroutineStart.LAZY) {
                        try { execute(task) } catch (e: CancellationException) { throw e } catch (_: Exception) { mutableError.value = "下载状态保存失败，请检查存储空间"; mutableRunning.value = false }
                    }
                    jobs[task.id] = job; activeSources[task.comic.source] = count + 1
                    job.invokeOnCompletion { wake.trySend(Unit) }; job.start()
                }
                if (jobs.isEmpty()) mutableRunning.value = false
            } } catch (e: CancellationException) { throw e } catch (_: Exception) { mutableError.value = "下载调度失败，请检查存储空间后重试"; mutableRunning.value = false }
        }
    }
    suspend fun awaitReady() = initialized.await()
    suspend fun enqueue(comic: ComicSummary, chapters: List<Chapter>): List<String> {
        initialized.await()
        require(chapters.isNotEmpty() && chapters.size <= 10000 && chapters.map { it.id }.distinct().size == chapters.size)
        val partition = access.partition(comic.key.source)
        val location = target(); storage.checkTarget(location)
        return mutex.withLock {
            chapters.map { chapter ->
                val id = DownloadTask.identity(comic.key, chapter.id, partition)
                dao.enqueue(DownloadTask(id, SavedComic.from(comic), chapter.id, chapter.title, chapter.order, partition, location, subtitle = comic.subtitle))
                id
            }.also { wake.trySend(Unit) }
        }
    }
    suspend fun start(owner: String = "test") = mutex.withLock { initialized.await(); this.owner = owner; mutableRunning.value = true; wake.trySend(Unit); Unit }
    suspend fun pause(id: String) = mutex.withLock {
        initialized.await(); jobs.remove(id)?.cancelAndJoin()
        dao.task(id)?.takeIf { it.state !in setOf(DownloadState.COMPLETED.name, DownloadState.DELETING.name) }?.let { set(it, DownloadState.PAUSED, "已暂停") }
        log.record(io.github.ddmoyu.picomic.data.EventCode.DOWNLOAD_PAUSED)
        wake.trySend(Unit); Unit
    }
    suspend fun resume(id: String) = mutex.withLock {
        initialized.await()
        val task = dao.task(id) ?: return@withLock
        if (task.state !in ACTIVE && task.state != DownloadState.COMPLETED.name && task.state != DownloadState.DELETING.name) {
            set(task, DownloadState.QUEUED, null); wake.trySend(Unit)
        }
    }
    suspend fun stop(message: String = "已暂停，进入下载管理继续", owner: String? = null) = mutex.withLock {
        if (owner != null && owner != this.owner) return@withLock
        initialized.await(); mutableRunning.value = false
        val active = jobs.values.toList(); active.forEach { it.cancel() }; active.joinAll(); jobs.clear()
        dao.tasks().filter { it.state in ACTIVE }.forEach { set(it, DownloadState.PAUSED, message) }
    }
    suspend fun remove(id: String) = mutex.withLock {
        initialized.await(); jobs.remove(id)?.cancelAndJoin()
        val task = dao.task(id) ?: return@withLock
        set(task, DownloadState.DELETING, "正在删除本章文件")
        try {
            storage.remove(task, dao.pages(id)); removeParts(id); dao.delete(id)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { set(task, DownloadState.DELETING, "删除未完成，请恢复原目录授权后重试"); throw e }
        finally { wake.trySend(Unit) }
    }
    private suspend fun execute(original: DownloadTask) {
        try {
            checkNetwork()
            set(original, DownloadState.RESOLVING, null)
            disk { storage.checkTarget(original.target) }
            val pages = access.pages(original)
            dao.manifest(dao.task(original.id)!!, pages)
            for (page in dao.pages(original.id)) {
                currentCoroutineContext().ensureActive()
                if (disk { storage.verify(original, page) }) continue
                checkNetwork()
                dao.invalidate(page)
                set(dao.task(original.id)!!, DownloadState.DOWNLOADING, null)
                val part = part(original.id, page.fileName)
                disk { check(part.parentFile!!.isDirectory || part.parentFile!!.mkdirs()) }
                access.fetch(original, page, part)
                set(dao.task(original.id)!!, DownloadState.PROCESSING, null)
                val completed = disk { storage.write(original, page, part) }
                currentCoroutineContext().ensureActive()
                dao.completed(completed)
                part.delete(); File(part.path + ".resume").delete()
            }
            set(dao.task(original.id)!!, DownloadState.COMPLETED, null)
            log.record(io.github.ddmoyu.picomic.data.EventCode.DOWNLOAD_COMPLETE, original.key().source)
            removeParts(original.id)
        } catch (e: CancellationException) {
            // A user pause cancels this job; a changed network/session may cancel only its lease.
            if (currentCoroutineContext().isActive) set(dao.task(original.id) ?: return, DownloadState.WAITING_NETWORK, "账号或网络已更改，请确认后继续")
            else throw e
        } catch (e: Exception) {
            log.record(io.github.ddmoyu.picomic.data.EventCode.DOWNLOAD_FAILED, original.key().source)
            val state = when (e) {
                is StorageFailure, is SecurityException -> DownloadState.WAITING_STORAGE
                is ContentFailure -> when(e.kind) {
                    ContentFailureKind.LOGIN, ContentFailureKind.EXPIRED -> DownloadState.WAITING_AUTH
                    ContentFailureKind.QUOTA -> DownloadState.WAITING_QUOTA
                    ContentFailureKind.NETWORK -> DownloadState.WAITING_NETWORK
                    else -> DownloadState.FAILED
                }
                is IOException -> DownloadState.WAITING_NETWORK
                else -> DownloadState.FAILED
            }
            val message = when (e) { is StorageFailure -> e.message!!; is SecurityException -> "原下载目录授权已失效，请重新授权后继续"; else -> contentError(e) }
            set(dao.task(original.id) ?: return, state, message)
        }
    }
    private fun checkNetwork() { if (!networkAllowed()) throw ContentFailure(ContentFailureKind.NETWORK, "已启用仅 Wi-Fi 下载，请连接 Wi-Fi 后继续") }
    /** No content adapter, session or network call is used to open a finished chapter. */
    suspend fun offline(id: String): OfflineChapter = withContext(Dispatchers.IO) {
        initialized.await()
        val task = dao.task(id) ?: throw IOException("下载任务不存在")
        require(task.state == DownloadState.COMPLETED.name) { "本章尚未下载完成" }
        val pages = dao.pages(id)
        if (pages.isEmpty() || pages.size != task.total) throw IOException("下载清单不完整")
        for (page in pages) if (!storage.verify(task, page)) {
            mutex.withLock {
                dao.invalidate(page)
                dao.task(id)?.let { set(it, DownloadState.FAILED, "下载文件缺失或校验失败，请继续下载修复") }
            }
            throw IOException("下载文件缺失或校验失败，请在下载管理中修复")
        }
        OfflineChapter(task, pages, dao.tasks().filter { it.comic.source == task.comic.source && it.comic.id == task.comic.id && it.accountPartition == task.accountPartition && it.state == DownloadState.COMPLETED.name }.sortedBy { it.chapterOrder })
    }
    private suspend fun <T> disk(block: suspend () -> T): T = try { block() } catch (e: CancellationException) { throw e } catch (e: ContentFailure) { throw e } catch (e: Exception) { throw StorageFailure(e) }
    private suspend fun set(task: DownloadTask, state: DownloadState, message: String?) = dao.save(task.copy(state = state.name, message = message, updatedAt = System.currentTimeMillis()))
    private fun part(id: String, file: String): File {
        require(id.matches(Regex("[a-f0-9]{64}")) && file.matches(Regex("[a-f0-9]{64}\\.image")))
        return File(File(temporary, id), file)
    }
    private fun removeParts(id: String) {
        val directory = part(id, "0".repeat(64) + ".image").parentFile!!
        require(directory.canonicalFile.parentFile == temporary.canonicalFile)
        directory.listFiles().orEmpty().filter { it.isFile && it.name.matches(Regex("[a-f0-9]{64}\\.image(?:\\.resume(?:\\.bak|\\.new)?)?")) }.forEach { it.delete() }
        if (directory.listFiles().orEmpty().isEmpty()) directory.delete()
    }
    companion object {
        val ACTIVE = setOf(DownloadState.QUEUED.name, DownloadState.RESOLVING.name, DownloadState.DOWNLOADING.name, DownloadState.PROCESSING.name)
        @Volatile private var instance: DownloadRepository? = null
        fun get(context: Context): DownloadRepository = instance ?: synchronized(this) {
            instance ?: context.applicationContext.let { app ->
                val prefs = app.getSharedPreferences("picomic_ui", 0)
                DownloadRepository(app, DownloadDatabase.get(app).downloads(), SourceDownloadAccess(app, ContentRuntime.get(app).repository),
                    { prefs.getString("pref.parallel", "3")?.toIntOrNull() ?: 3 }, { prefs.getString("pref.downloadTarget", DownloadStorage.INTERNAL)!! }, networkAllowed = {
                        if (prefs.getString("pref.downloadWifi", "false") != "true") true else {
                            val manager = app.getSystemService(android.net.ConnectivityManager::class.java)
                            manager.getNetworkCapabilities(manager.activeNetwork)?.let { it.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) } == true
                        }
                    })
            }.also { instance = it }
        }
    }
}
