package io.github.ddmoyu.picomic.backup

import android.content.Context
import io.github.ddmoyu.picomic.auth.KeystoreSecretStore
import io.github.ddmoyu.picomic.network.NetworkRepository
import io.github.ddmoyu.picomic.network.networkError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class WebDavState(val ready: Boolean = false, val url: String = "", val username: String = "", val configured: Boolean = false,
    val busy: Boolean = false, val tested: Boolean = false, val writable: Boolean = false, val message: String? = null,
    val preview: BackupPreview? = null, val lastSuccess: Long = 0, val pending: Boolean = false)
class WebDavController(context: Context, private val scope: CoroutineScope, private val backups: BackupRepository) {
    private val network = NetworkRepository.get(context)
    private val secrets = KeystoreSecretStore(context, "picomic.webdav.v1")
    private val status = context.getSharedPreferences("webdav_status", 0)
    private val log = io.github.ddmoyu.picomic.data.EventLog.get(context)
    private val mutable = MutableStateFlow(WebDavState(lastSuccess = status.getLong("lastSuccess", 0), pending = status.getBoolean("pending", false)))
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var sequence = 0L
    private var testedGeneration = -1L
    private var remote: RemoteBackup? = null
    private val configurationLock = Mutex()
    private val dao = io.github.ddmoyu.picomic.content.ContentDatabase.get(context).content()
    private val restored = scope.launch {
        try {
            val saved = withContext(Dispatchers.IO) { read() }
            mutable.value = mutable.value.copy(ready = true, url = saved?.getString("url").orEmpty(), username = saved?.getString("username").orEmpty(), configured = saved != null)
        } catch (_: Exception) { mutable.value = mutable.value.copy(ready = true, message = "同步配置无法解密，请重新保存") }
    }
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val changes = scope.launch {
        restored.join()
        combine(dao.comics(), dao.favorites(), dao.progress(), dao.preferences(), dao.portableChanges()) { comics, favorites, progress, work, settings ->
            BackupData(comics, favorites, progress, work, settings.map { TransferPreference(it.key, it.value, it.updatedAt, it.deleted) })
        }.debounce(250).catch { mutable.update { it.copy(message = "无法读取同步数据，请检查本地存储") } }.collect { data ->
            if (state.value.configured) {
                val fingerprint = withContext(Dispatchers.IO) { BackupCodec.fingerprint(data) }
                val pending = fingerprint != status.getString("lastFingerprint", null)
                status.edit().putBoolean("pending", pending).apply()
                mutable.update { it.copy(pending = pending) }
            }
        }
    }
    init { scope.launch { network.state.collect { networkState ->
        if (testedGeneration >= 0 && testedGeneration != networkState.generation) {
            cancel(); testedGeneration = -1; mutable.value = mutable.value.copy(tested = false, writable = false, message = "网络设置已改变，请重新测试连接")
        }
    } } }
    private fun read(): JSONObject? = secrets.read(KEY)?.let { bytes -> try { JSONObject(bytes.toString(Charsets.UTF_8)) } finally { bytes.fill(0) } }
    private suspend fun client(): WebDavClient {
        network.awaitReady()
        val configuration = withContext(Dispatchers.IO) { read() } ?: error("请先保存同步账号")
        return WebDavClient.create(network.engine, configuration.getString("url"), configuration.getString("username"), configuration.getString("password"))
    }
    fun cancel() { sequence++; job?.cancel(); job = null; remote = null; mutable.value = mutable.value.copy(busy = false, preview = null) }
    private fun operate(action: suspend (Long) -> Unit) {
        if (state.value.busy) return
        val attempt = ++sequence
        mutable.value = state.value.copy(busy = true, message = null)
        job = scope.launch {
            try { restored.join(); action(attempt) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                log.record(io.github.ddmoyu.picomic.data.EventCode.SYNC_FAILED)
                if (attempt == sequence) {
                    val invalidate = e is WebDavFailure && e.kind in setOf(WebDavError.AUTH, WebDavError.DENIED, WebDavError.PROTOCOL)
                    if (invalidate) testedGeneration = -1
                    mutable.value = state.value.copy(preview = null, tested = state.value.tested && !invalidate, writable = state.value.writable && !invalidate,
                        message = if (e is WebDavFailure) e.message else if (e is IllegalArgumentException || e is IllegalStateException) e.message else networkError(e))
                }
            } finally { if (attempt == sequence) mutable.value = state.value.copy(busy = false) }
        }
    }
    fun save(url: String, username: String, password: CharArray) {
        cancel(); testedGeneration = -1
        mutable.value = state.value.copy(tested = false, writable = false)
        operate { attempt ->
            try {
                val endpoint = WebDavClient.endpoint(url).toString()
                require(username.isNotBlank() && username.length <= 256 && username.none { it.isISOControl() || it == ':' }) { "请输入有效用户名" }
                require(password.size <= 4096) { "密码过长" }
                withContext(Dispatchers.IO) { configurationLock.withLock {
                    currentCoroutineContext().ensureActive()
                    check(attempt == sequence) { "配置保存已取消" }
                    val old = read()
                    val secret = if (password.isNotEmpty()) String(password) else old?.takeIf { it.optString("url") == endpoint && it.optString("username") == username }?.getString("password")
                        ?: throw IllegalArgumentException("地址或账号改变后，请重新输入密码")
                    val bytes = JSONObject().put("version", 1).put("url", endpoint).put("username", username).put("password", secret).toString().toByteArray()
                    try { secrets.write(KEY, bytes) } finally { bytes.fill(0) }
                    status.edit().remove("lastSuccess").remove("lastFingerprint").putBoolean("pending", true).commit()
                } }
                if (attempt == sequence) mutable.value = state.value.copy(url = endpoint, username = username, configured = true, lastSuccess = 0, pending = true, message = "配置已加密保存，请测试连接")
            } finally { password.fill('\u0000') }
        }
        job?.invokeOnCompletion { password.fill('\u0000') }
    }
    fun test() = operate { attempt ->
        network.awaitReady()
        val generation = network.engine.status.generation
        val result = client().test()
        log.record(io.github.ddmoyu.picomic.data.EventCode.SYNC_TESTED)
        network.engine.withGeneration(generation) { if (attempt == sequence) {
            testedGeneration = generation
            mutable.value = state.value.copy(tested = true, writable = result.writable, message = result.message, preview = null)
        } }
    }
    fun preview() = operate { attempt ->
        check(state.value.tested && testedGeneration == network.engine.status.generation) { "请先测试当前连接" }
        val value = client().read()
        val proposed = backups.preview(value.document?.data ?: BackupData())
        if (attempt == sequence) {
            remote = value
            val writable = state.value.writable && (value.document == null || value.etag != null)
            mutable.value = state.value.copy(preview = proposed, writable = writable, message = if (writable) null else "当前连接仅允许读取，合并后不会上传")
        }
    }
    fun dismissPreview() { if (!state.value.busy) { remote = null; mutable.value = state.value.copy(preview = null) } }
    fun sync(choices: Map<String, MergeSide>) = operate { attempt ->
        check(state.value.tested && testedGeneration == network.engine.status.generation) { "连接已变化，请重新测试" }
        val proposed = state.value.preview ?: error("请先预览数据")
        val previous = remote ?: error("远端版本已失效，请重新预览")
        val upload = state.value.writable
        // Bind this operation to the tested configuration before any local transaction.
        val remoteClient = client()
        val merged = backups.apply(proposed, choices)
        currentCoroutineContext().ensureActive()
        check(attempt == sequence) { "同步已取消" }
        withContext(Dispatchers.IO) { configurationLock.withLock {
            currentCoroutineContext().ensureActive(); check(attempt == sequence)
            check(status.edit().putBoolean("pending", upload).commit())
        } }
        mutable.value = state.value.copy(preview = null, pending = upload)
        if (upload) {
            val bytes = backups.encode(merged)
            try { remoteClient.upload(bytes, previous) } finally { bytes.fill(0) }
            log.record(io.github.ddmoyu.picomic.data.EventCode.SYNC_COMPLETE)
            val now = System.currentTimeMillis()
            val fingerprint = BackupCodec.fingerprint(merged)
            val pending = BackupCodec.fingerprint(backups.snapshot()) != fingerprint
            withContext(Dispatchers.IO) { configurationLock.withLock {
                currentCoroutineContext().ensureActive(); check(attempt == sequence) { "同步配置已改变" }
                check(status.edit().putLong("lastSuccess", now).putString("lastFingerprint", fingerprint).putBoolean("pending", pending).commit())
            } }
            if (attempt == sequence) mutable.value = state.value.copy(lastSuccess = now, pending = pending, message = if (pending) "此次同步完成，期间新增的本地变更仍待同步" else "同步完成，已核对远端数据")
        } else if (attempt == sequence) mutable.value = state.value.copy(message = "远端数据已合并到本地；未执行上传")
        if (attempt == sequence) remote = null
    }
    fun clear() {
        cancel(); testedGeneration = -1
        operate { attempt ->
            withContext(Dispatchers.IO) { configurationLock.withLock {
                currentCoroutineContext().ensureActive()
                check(attempt == sequence) { "配置清理已取消" }
                secrets.remove(KEY); status.edit().clear().commit()
            } }
            if (attempt == sequence) mutable.value = WebDavState(ready = true, message = "本机同步配置已清除，远端文件和本地书架保留")
        }
    }
    companion object { private const val KEY = "webdav.account" }
}
