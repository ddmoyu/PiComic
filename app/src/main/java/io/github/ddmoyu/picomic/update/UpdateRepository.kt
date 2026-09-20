package io.github.ddmoyu.picomic.update

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import io.github.ddmoyu.picomic.BuildConfig
import io.github.ddmoyu.picomic.data.*
import io.github.ddmoyu.picomic.download.RangeTransfer
import io.github.ddmoyu.picomic.network.NetworkRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.Request
import java.io.File

enum class UpdatePhase { IDLE, CHECKING, AVAILABLE, CURRENT, INCOMPATIBLE, DOWNLOADING, PAUSED, VERIFYING, READY, WAITING_INSTALL, ERROR }
data class UpdateState(val configured: Boolean, val phase: UpdatePhase = UpdatePhase.IDLE, val bundle: UpdateBundle? = null,
    val artifact: UpdateArtifact? = null, val downloaded: Long = 0, val task: Boolean = false, val message: String? = null, val checkedAt: Long = 0, val initialized: Boolean = false) {
    val busy get() = phase in setOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING)
}
class UpdateRepository private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val network = NetworkRepository.get(context)
    private val log = EventLog.get(context)
    private val preferences = context.getSharedPreferences("update_status", 0)
    private val channel = if (BuildConfig.RELEASE_OWNER.isEmpty() || BuildConfig.RELEASE_REPO.isEmpty()) null else ReleaseChannel(BuildConfig.RELEASE_OWNER, BuildConfig.RELEASE_REPO)
    private val directory = File(context.filesDir, "updates")
    private val part = File(directory, "update.part")
    private val apk = File(directory, "update.apk")
    private var store: UpdateStore? = null
    private var job: Job? = null
    private var foreground = false
    private var coldCheck = false
    private val mutable = MutableStateFlow(UpdateState(channel != null, checkedAt = preferences.getLong("checkedAt", 0)))
    val state = mutable.asStateFlow()
    private val initialized = scope.async {
        try { withContext(Dispatchers.IO) {
            if (channel == null) return@withContext
            store = UpdateStore(directory, channel, context.packageName)
            val task = store!!.read("task.json") ?: return@withContext
            if (task.bundle.versionCode <= ApkVerifier.version(ApkVerifier.installed(context))) {
                clearTask(); mutable.update { it.copy(message = "当前应用版本已更新") }; return@withContext
            }
            val artifact = task.bundle.artifacts.single { it.asset.id == task.artifactId }
            require(artifact.minSdk <= Build.VERSION.SDK_INT && Build.SUPPORTED_ABIS.any { it in artifact.abis })
            mutable.update { it.copy(bundle = task.bundle, artifact = artifact, task = true, phase = UpdatePhase.PAUSED, downloaded = part.length(), message = "更新下载已恢复，可继续") }
            if (apk.exists()) {
                ApkVerifier.verify(context, apk, task.bundle, artifact)
                mutable.update { it.copy(phase = UpdatePhase.READY, downloaded = apk.length(), message = "更新包已重新校验，可安装") }
            }
        } } catch (_: Exception) {
            withContext(Dispatchers.IO) { apk.delete() }
            mutable.update { it.copy(phase = UpdatePhase.ERROR, message = "上次更新记录或安装包不可用，请取消后重新检查") }
        } finally { mutable.update { it.copy(initialized = true) } }
    }
    fun foreground(value: Boolean, auto: Boolean = false) {
        foreground = value
        if (!value && state.value.phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING)) pause()
        if (value && !coldCheck) {
            coldCheck = true
            scope.launch { initialized.await(); if (auto && !state.value.task && channel != null && System.currentTimeMillis() - preferences.getLong("autoAt", 0) >= 86_400_000) {
                preferences.edit().putLong("autoAt", System.currentTimeMillis()).commit(); check()
            } }
        }
    }
    fun check() {
        if (job?.isActive == true || !state.value.initialized || state.value.task) return
        if (channel == null) { mutable.update { it.copy(message = "此构建尚未配置公开发布渠道") }; return }
        val now = System.currentTimeMillis()
        if (now < preferences.getLong("cooldown", 0)) { mutable.update { it.copy(message = "GitHub 请求仍在冷却期，请稍后重试") }; return }
        mutable.update { it.copy(phase = UpdatePhase.CHECKING, message = null) }
        job = scope.launch {
            try {
                network.awaitReady(); val generation = network.engine.status.generation; val client = client()
                val bundle = withContext(Dispatchers.IO) { UpdateChecker.check(client, store!!) }
                network.engine.withGeneration(generation) { }
                val artifact = bundle.select(Build.SUPPORTED_ABIS.toList(), Build.VERSION.SDK_INT)
                val current = bundle.versionCode <= ApkVerifier.version(ApkVerifier.installed(context))
                val time = System.currentTimeMillis(); preferences.edit().putLong("checkedAt", time).commit()
                mutable.value = UpdateState(true, if (current) UpdatePhase.CURRENT else if (artifact == null) UpdatePhase.INCOMPATIBLE else UpdatePhase.AVAILABLE,
                    bundle, artifact, message = if (current) "暂无更高版本" else if (artifact == null) "新版本不兼容当前系统或设备架构" else "发现新版本", checkedAt = time, initialized = true)
                log.record(EventCode.UPDATE_CHECKED)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e) }
        }
    }
    fun download() {
        val selected = state.value
        if (!foreground || job?.isActive == true || selected.bundle == null || selected.artifact == null || selected.bundle.versionCode <= BuildConfig.VERSION_CODE) return
        if (System.currentTimeMillis() < preferences.getLong("cooldown", 0)) {
            mutable.update { it.copy(message = "GitHub 请求仍在冷却期，请稍后重试") }; return
        }
        mutable.update { it.copy(phase = UpdatePhase.DOWNLOADING, message = null, task = true) }
        job = scope.launch {
            try { withContext(Dispatchers.IO) {
                network.awaitReady(); val client = client()
                store!!.write("task.json", UpdateStore.Saved(selected.bundle, selected.artifact.asset.id))
                val reply = client.release(id = selected.bundle.release.id)
                val fresh = client.bundle(reply.bytes ?: throw UpdateFailure("发布信息不可用"))
                val artifact = fresh.artifacts.singleOrNull { it.asset.id == selected.artifact.asset.id } ?: throw UpdateFailure("发布文件已变化，请取消后重新检查")
                if (fresh.identity(artifact) != selected.bundle.identity(selected.artifact) || artifact != selected.artifact || fresh.versionName != selected.bundle.versionName)
                    throw UpdateFailure("发布文件已变化，请取消后重新检查")
                coroutineScope {
                    val progress = launch { while (isActive) { mutable.update { it.copy(downloaded = part.length()) }; delay(300) } }
                    try { RangeTransfer(client.assets).fetch(Request.Builder().url(artifact.asset.url).header("User-Agent", "PiComic/${BuildConfig.VERSION_NAME}").build(), part, artifact.asset.size) }
                    finally { progress.cancelAndJoin() }
                }
                mutable.update { it.copy(phase = UpdatePhase.VERIFYING, downloaded = part.length()) }
                try { ApkVerifier.verify(context, part, fresh, artifact) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { part.delete(); File(part.path + ".resume").delete(); throw e }
                java.nio.file.Files.move(part.toPath(), apk.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                mutable.update { it.copy(phase = UpdatePhase.READY, downloaded = apk.length(), message = "校验通过，可安装更新") }
                log.record(EventCode.UPDATE_VERIFIED)
            } } catch (e: CancellationException) { mutable.update { it.copy(phase = UpdatePhase.PAUSED, message = "下载已暂停，可在前台继续") }; throw e }
            catch (e: Exception) { failure(e) }
        }
    }
    fun pause() { job?.cancel() }
    fun cancel() {
        val previous = job
        job = scope.launch {
            previous?.cancelAndJoin(); initialized.await()
            try { withContext(Dispatchers.IO) { clearTask() }; mutable.value = UpdateState(channel != null, checkedAt = preferences.getLong("checkedAt", 0), initialized = true, message = "更新任务已取消") }
            catch (e: Exception) { failure(e) }
        }
    }
    suspend fun installIntent(): Intent {
        val current = state.value
        if (current.phase !in setOf(UpdatePhase.READY, UpdatePhase.WAITING_INSTALL) || current.bundle == null || current.artifact == null) throw UpdateFailure("请先下载并校验更新包")
        if (!context.packageManager.canRequestPackageInstalls()) throw UpdateFailure("请先允许安装此来源的应用")
        ApkVerifier.verify(context, apk, current.bundle, current.artifact)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", apk)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    fun installerOpened() { mutable.update { it.copy(phase = UpdatePhase.WAITING_INSTALL, message = "等待系统处理；取消后可重新安装，当前版本以系统记录为准") } }
    fun message(value: String) { mutable.update { it.copy(message = value) } }
    private fun client() = GitHubUpdateClient(network.engine, channel ?: throw UpdateFailure("尚未配置发布渠道"), BuildConfig.VERSION_NAME, context.packageName)
    private fun clearTask() {
        listOf(part, apk, File(part.path + ".resume"), File(part.path + ".resume.bak")).forEach { if (it.exists() && !it.delete()) throw UpdateFailure("无法清理更新包，请检查存储空间") }
        store?.delete("task.json")
    }
    private fun failure(error: Exception) {
        if (error is UpdateFailure && error.cooldownUntil > 0) preferences.edit().putLong("cooldown", error.cooldownUntil).commit()
        mutable.update { it.copy(phase = UpdatePhase.ERROR, message = (error as? UpdateFailure)?.message ?: "更新操作失败，请检查网络或存储空间后重试") }
        log.record(EventCode.UPDATE_FAILED)
    }
    companion object {
        @Volatile private var instance: UpdateRepository? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: UpdateRepository(context.applicationContext).also { instance = it } }
    }
}
