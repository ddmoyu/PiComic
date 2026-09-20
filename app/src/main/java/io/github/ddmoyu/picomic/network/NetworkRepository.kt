package io.github.ddmoyu.picomic.network

import android.content.Context
import io.github.ddmoyu.picomic.auth.KeystoreSecretStore
import io.github.ddmoyu.picomic.auth.SessionCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Request
import java.io.*
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

data class NetworkUiState(
    val ready: Boolean = false, val custom: Boolean = false, val host: String = "", val port: String = "",
    val hasCredentials: Boolean = false, val generation: Long = 0, val busy: Boolean = false,
    val message: String? = null, val label: String = "正在载入网络设置"
)

class NetworkRepository private constructor(context: Context) : Call.Factory {
    private val log = io.github.ddmoyu.picomic.data.EventLog.get(context)
    private val secrets = KeystoreSecretStore(context.applicationContext)
    val engine = NetworkEngine()
    val sessions = SessionCoordinator(secrets, engine)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(NetworkUiState())
    val state = mutable.asStateFlow()
    private var testJob: Job? = null
    private val testSequence = java.util.concurrent.atomic.AtomicLong()
    private val initialized = scope.async {
        mutex.withLock {
            try {
                val bytes = secrets.read("network.profile")
                val profile = if (bytes == null) NetworkProfile.FollowSystem else try { decode(bytes) } finally { bytes.fill(0) }
                publish(engine.change(profile))
            } catch (_: Exception) {
                mutable.value = NetworkUiState(message = "网络设置无法解密，请重新保存连接方式", label = "网络设置不可用")
            }
        }
    }
    override fun newCall(request: Request): Call {
        check(mutable.value.ready) { "请先载入或重新保存网络设置" }
        return engine.newCall(request)
    }
    suspend fun awaitReady() {
        initialized.await()
        check(state.value.ready) { "请先载入或重新保存网络设置" }
    }
    suspend fun save(profile: NetworkProfile) {
        initialized.await()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                testJob?.cancel()
                testSequence.incrementAndGet()
                mutable.value = mutable.value.copy(busy = false)
                val bytes = encode(profile)
                try { secrets.write("network.profile", bytes) } finally { bytes.fill(0) }
                val status = engine.change(profile)
                sessions.networkChanged()
                publish(status, "网络设置已生效")
                log.record(io.github.ddmoyu.picomic.data.EventCode.NETWORK_SAVED)
            }
        }
    }
    fun startTest() {
        val testId = testSequence.incrementAndGet()
        testJob?.cancel()
        testJob = scope.launch {
            initialized.await()
            val generation = mutex.withLock {
                if (!state.value.ready || testId != testSequence.get()) return@launch
                mutable.value = mutable.value.copy(busy = true, message = null)
                engine.status.generation
            }
            val result = try {
                newCall(Request.Builder().url("https://api.github.com/zen").header("User-Agent", "PiComic").build()).await().use {
                    if (it.isSuccessful) "连接成功：GitHub HTTPS 可达（不代表漫画平台可用）" else "已收到响应：HTTP ${it.code}"
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { networkError(error) }
            mutex.withLock {
                if (generation == engine.status.generation && testId == testSequence.get())
                    mutable.value = mutable.value.copy(busy = false, message = result)
            }
        }
    }
    private fun publish(status: NetworkStatus, message: String? = null) {
        val proxy = status.profile as? NetworkProfile.HttpProxy
        mutable.value = NetworkUiState(true, proxy != null, proxy?.host.orEmpty(), proxy?.port?.toString().orEmpty(),
            proxy?.credentials != null, status.generation, false, message, status.profile.label())
    }
    companion object {
        @Volatile private var instance: NetworkRepository? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: NetworkRepository(context.applicationContext).also { instance = it }
        }
        private fun encode(profile: NetworkProfile): ByteArray = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(1); out.writeBoolean(profile is NetworkProfile.HttpProxy)
                if (profile is NetworkProfile.HttpProxy) {
                    out.writeUTF(profile.host); out.writeInt(profile.port); out.writeBoolean(profile.credentials != null)
                    profile.credentials?.let { out.writeUTF(it.username); out.writeUTF(it.password) }
                }
            }
        }.toByteArray()
        private fun decode(bytes: ByteArray): NetworkProfile = DataInputStream(bytes.inputStream()).use { input ->
            require(input.readInt() == 1)
            if (!input.readBoolean()) NetworkProfile.FollowSystem else {
                val host = input.readUTF(); val port = input.readInt()
                val credentials = if (input.readBoolean()) ProxyCredentials(input.readUTF(), input.readUTF()) else null
                NetworkProfile.HttpProxy(host, port, credentials)
            }
        }
    }
}

fun networkError(error: Exception): String = when (error) {
    is StaleNetworkException -> "网络设置已更改，请重新测试"
    is SocketTimeoutException -> "连接超时，请检查网络或代理"
    is SSLException -> "TLS 证书验证失败"
    is IOException -> "连接失败，请检查网络、代理地址与认证信息"
    else -> "无法完成操作，请检查设置后重试"
}
