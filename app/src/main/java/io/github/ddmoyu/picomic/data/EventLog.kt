package io.github.ddmoyu.picomic.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Structured events only: the API cannot accept response bodies, URLs, credentials or account names. */
enum class EventCode(val area: String, val level: String, val summary: String) {
    APP_START("应用", "信息", "应用已启动"), NETWORK_SAVED("网络", "信息", "网络设置已更新"),
    DOWNLOAD_COMPLETE("下载", "信息", "章节下载并校验完成"), DOWNLOAD_PAUSED("下载", "信息", "下载已暂停"), DOWNLOAD_FAILED("下载", "警告", "下载中断，等待处理"),
    BACKUP_EXPORTED("数据", "信息", "用户备份已生成"), BACKUP_IMPORTED("数据", "信息", "用户数据已合并"),
    SYNC_TESTED("同步", "信息", "同步连接已测试"), SYNC_COMPLETE("同步", "信息", "同步完成并核对远端数据"), SYNC_FAILED("同步", "警告", "同步操作未完成"),
    CHECKIN_COMPLETE("账号", "信息", "平台签到已确认"), CHECKIN_FAILED("账号", "警告", "平台签到未确认"),
    UPDATE_CHECKED("更新", "信息", "版本检查已完成"), UPDATE_VERIFIED("更新", "信息", "安装包校验通过"), UPDATE_FAILED("更新", "警告", "更新操作未完成")
}
data class AppEvent(val time: Long, val code: EventCode, val source: Source? = null)
class EventLog private constructor(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "events.json"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class Write(val event: AppEvent? = null, val done: CompletableDeferred<Unit>? = null)
    private val queue = Channel<Write>(Channel.UNLIMITED)
    private val mutable = MutableStateFlow<List<AppEvent>>(emptyList())
    val events = mutable.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    init { scope.launch {
        try {
            if (file.baseFile.exists()) {
                require(file.baseFile.length() <= 256 * 1024)
                mutable.value = parse(file.openRead().use { it.readBytes() })
            }
        } catch (_: Exception) { mutableError.value = "旧日志无法读取，将记录新的操作" }
        for (operation in queue) {
            if (operation.done != null) { operation.done.complete(Unit); continue }
            val event = operation.event ?: continue
            mutable.value = (mutable.value + event).takeLast(300)
            try {
                val out = file.startWrite()
                try {
                    out.write(JSONArray(mutable.value.map { JSONObject().put("time", it.time).put("code", it.code.name).put("source", it.source?.name ?: JSONObject.NULL) }).toString().toByteArray())
                    file.finishWrite(out); mutableError.value = null
                } catch (e: Exception) { file.failWrite(out); throw e }
            } catch (_: Exception) { mutableError.value = "日志暂时无法保存，请检查存储空间" }
        }
    } }
    fun record(code: EventCode, source: Source? = null) { queue.trySend(Write(AppEvent(System.currentTimeMillis(), code, source))) }
    suspend fun export(): ByteArray {
        val done = CompletableDeferred<Unit>(); queue.send(Write(done = done)); done.await()
        return render(events.value).toByteArray(Charsets.UTF_8)
    }
    companion object {
        @Volatile private var instance: EventLog? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: EventLog(context.applicationContext).also { instance = it } }
        internal fun parse(bytes: ByteArray): List<AppEvent> {
            require(bytes.size <= 256 * 1024)
            val rows = JSONArray(bytes.toString(Charsets.UTF_8)); require(rows.length() <= 300)
            return (0 until rows.length()).mapNotNull { index -> runCatching {
                val row = rows.getJSONObject(index); val code = EventCode.valueOf(row.getString("code"))
                val time = row.getLong("time"); require(time >= 0)
                val source = if (row.isNull("source")) null else Source.valueOf(row.getString("source"))
                AppEvent(time, code, source)
            }.getOrNull() }
        }
        internal fun render(events: List<AppEvent>): String = "PiComic 诊断事件（不包含账号、地址或内容正文）\n" + events.joinToString("\n") {
            "${java.time.Instant.ofEpochMilli(it.time)}\t${it.code.level}\t${it.code.area}\t${it.source?.shortTitle.orEmpty()}\t${it.code.summary}"
        }
    }
}
