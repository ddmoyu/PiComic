package io.github.ddmoyu.picomic.auth

import android.content.Context
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.*
import io.github.ddmoyu.picomic.network.NetworkRepository
import io.github.ddmoyu.picomic.source.picacg.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

enum class CheckInOutcome { ALREADY, DONE }
data class CheckInStatus(val busy: Boolean = false, val message: String? = null)
interface CheckInJournal { fun number(key: String): Long; fun save(values: Map<String, Long>) }

class CheckInRunner(private val sessions: SessionCoordinator, private val journal: CheckInJournal,
    private val now: () -> Long = System::currentTimeMillis, private val perform: suspend (Source, SessionCandidate) -> CheckInOutcome) {
    suspend fun run(source: Source, manual: Boolean): String = withContext(Dispatchers.IO) {
        require(source in setOf(Source.PICACG, Source.JMCOMIC))
        val slot = if (source == Source.PICACG) "picacg" else "jmcomic"
        if (sessions.state.value[slot]?.status != AccountStatus.AUTHENTICATED) return@withContext "请先登录并验证账号"
        val lease = sessions.lease(slot)
        try { sessions.useLease(lease) { lock.withLock {
            val date = Instant.ofEpochMilli(now()).atZone(ZoneId.of(if (source == Source.PICACG) "Asia/Hong_Kong" else "Asia/Taipei")).toLocalDate()
            val key = "$slot.${lease.partition}.$date"
            if (journal.number("done.$key") > 0) return@withLock "今日已确认签到"
            val attempts = journal.number("attempts.$key")
            if (!manual && attempts >= 3) return@withLock "今日自动尝试已达上限，可手动重试"
            if (now() - journal.number("last.$key") < 60_000) return@withLock "请稍后重试签到"
            journal.save(mapOf("attempts.$key" to attempts + 1, "last.$key" to now()))
            val result = perform(source, lease.candidate)
            currentCoroutineContext().ensureActive()
            if (!sessions.isCurrent(lease)) throw CancellationException("账号已更改")
            journal.save(mapOf("done.$key" to now()))
            if (result == CheckInOutcome.ALREADY) "今日已确认签到" else "签到成功"
        } } } catch (e: ContentFailure) {
            if (e.kind == ContentFailureKind.EXPIRED) sessions.expire(lease)
            throw e
        } catch (e: PicacgFailure) {
            if (e.kind == PicacgFailureKind.EXPIRED) sessions.expire(lease)
            throw e
        } finally { lease.close() }
    }
    companion object { private val lock = Mutex() }
}

class CheckInController(context: Context, private val scope: CoroutineScope, private val enabled: (Source) -> Boolean) {
    private val network = NetworkRepository.get(context)
    private val runtime = ContentRuntime.get(context)
    private val store = context.getSharedPreferences("checkin_journal", 0)
    private val log = EventLog.get(context)
    private val mutable = MutableStateFlow<Map<Source, CheckInStatus>>(emptyMap())
    val state = mutable.asStateFlow()
    private val jobs = mutableMapOf<Source, Job>()
    private var foreground = false
    private val runner = CheckInRunner(network.sessions, object : CheckInJournal {
        override fun number(key: String) = store.getLong(key, 0)
        override fun save(values: Map<String, Long>) {
            val edit = store.edit()
            // Keep at most 90 days of non-sensitive account hashes and counters.
            val cutoff = java.time.LocalDate.now(ZoneId.of("Asia/Taipei")).minusDays(90).toString()
            store.all.keys.filter { it.substringAfterLast('.', "") < cutoff }.forEach { edit.remove(it) }
            values.forEach { (key, value) -> edit.putLong(key, value) }; check(edit.commit()) { "无法保存签到状态" }
        }
    }) { source, candidate ->
        when (source) {
            Source.PICACG -> PicacgClient(network.engine).checkIn(candidate)
            Source.JMCOMIC -> runtime.jmRoutes.client().also { it.install(candidate) }.checkIn(candidate)
            else -> error("来源不支持签到")
        }
    }
    fun foreground(value: Boolean) { foreground = value; if (value) refresh() else jobs.keys.toList().forEach(::cancel) }
    fun refresh() {
        for (source in listOf(Source.PICACG, Source.JMCOMIC)) {
            if (!enabled(source)) cancel(source)
            else if (foreground) run(source, false)
        }
    }
    fun run(source: Source, manual: Boolean = true) {
        if (!foreground || jobs[source]?.isActive == true) return
        mutable.update { it + (source to CheckInStatus(true)) }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                network.awaitReady()
                val message = runner.run(source, manual)
                mutable.update { it + (source to CheckInStatus(message = message)) }
                if (message in setOf("今日已确认签到", "签到成功")) log.record(EventCode.CHECKIN_COMPLETE, source)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { log.record(EventCode.CHECKIN_FAILED, source); mutable.update { it + (source to CheckInStatus(message = contentError(e))) } }
            finally { if (jobs[source] === currentCoroutineContext()[Job]) {
                jobs.remove(source)
                mutable.update { it + (source to (it[source] ?: CheckInStatus()).copy(busy = false)) }
            } }
        }
        jobs[source] = job; job.start()
    }
    fun cancel(source: Source) { jobs.remove(source)?.cancel(); mutable.update { it + (source to (it[source] ?: CheckInStatus()).copy(busy = false)) } }
}
