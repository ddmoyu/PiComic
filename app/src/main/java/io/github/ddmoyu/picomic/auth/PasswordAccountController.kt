package io.github.ddmoyu.picomic.auth

import io.github.ddmoyu.picomic.source.picacg.*
import io.github.ddmoyu.picomic.content.*

import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.network.StaleNetworkException
import io.github.ddmoyu.picomic.network.networkError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PasswordLoginState(val busy: Boolean = false, val message: String? = null)

/** All entry points are called on the UI scope. Credentials are never part of observable UI state. */
open class PasswordAccountController(
    val sourceId: String,
    val title: String,
    private val sessions: SessionCoordinator,
    private val engine: NetworkEngine,
    private val scope: CoroutineScope,
    private val awaitNetwork: suspend () -> Unit,
    private val apiFactory: suspend () -> PasswordAuthApi
) {
    val accounts = sessions.state
    private val mutable = MutableStateFlow(PasswordLoginState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var attempt: LoginAttempt? = null
    private var operation = 0L

    fun login(email: String, password: CharArray) {
        val account = email.trim()
        if (account.isBlank() || account.length > 320 || account.any { it.isISOControl() } || password.isEmpty() || password.size > 1024) {
            password.fill('\u0000')
            mutable.value = mutable.value.copy(message = "请输入账号和密码（账号最多 320 字，密码最多 1024 字）")
            return
        }
        val pending = start {
            awaitNetwork()
            val active = sessions.begin(sourceId).also { attempt = it }
            val api = apiFactory()
            api.probe()
            currentCoroutineContext().ensureActive()
            if (!sessions.isCurrent(active)) throw CancellationException()
            val candidate = api.signIn(account, password)
            try {
                sessions.validateAndCommit(active, candidate) { api.validate(it) }
                mutable.value = mutable.value.copy(message = "登录成功，已验证账号并保存加密会话")
            } finally { candidate.value.fill(0) }
        }
        pending.invokeOnCompletion { password.fill('\u0000') }
    }

    /** Manual verification and startup restoration share one job, so concurrent requests are coalesced. */
    fun restore() {
        if (job?.isActive == true) return
        start {
            val candidate = sessions.storedCandidate(sourceId) ?: return@start
            try {
                currentCoroutineContext().ensureActive()
                awaitNetwork()
                val active = sessions.begin(sourceId).also { attempt = it }
                val api = apiFactory()
                api.probe()
                try {
                    sessions.validateAndCommit(active, candidate) { api.validate(it) }
                    mutable.value = mutable.value.copy(message = "会话验证通过")
                } catch (error: Exception) {
                    if ((error is PicacgFailure && error.kind == PicacgFailureKind.EXPIRED) || (error is ContentFailure && error.kind == ContentFailureKind.EXPIRED)) sessions.expire(active)
                    throw error
                }
            } finally { candidate.value.fill(0) }
        }
    }

    fun probe() {
        if (job?.isActive == true) return
        start {
            awaitNetwork()
            val generation = engine.status.generation
            apiFactory().probe()
            engine.withGeneration(generation) {
                mutable.value = mutable.value.copy(message = "${title} API 可达，可以尝试登录")
            }
        }
    }

    fun logout() {
        start {
            sessions.logout(sourceId)
            mutable.value = mutable.value.copy(message = "已清除本机${title}会话")
        }
    }

    fun cancel() {
        operation++
        job?.cancel(); job = null
        attempt?.let(sessions::cancel); attempt = null
        mutable.value = mutable.value.copy(busy = false)
    }

    private fun start(action: suspend () -> Unit): Job {
        cancel()
        val id = operation
        mutable.value = PasswordLoginState(busy = true)
        return scope.launch {
            try { action() }
            catch (error: CancellationException) {
                if (id == operation) mutable.value = mutable.value.copy(message = "操作已取消，请重试")
                throw error
            } catch (error: Exception) {
                if (id == operation) mutable.value = mutable.value.copy(message = when (error) {
                    is PicacgFailure, is ContentFailure -> error.message
                    is StaleNetworkException -> "网络设置已更改，请重试"
                    else -> networkError(error)
                })
            } finally {
                if (id == operation) {
                    attempt?.let(sessions::cancel); attempt = null
                    mutable.value = mutable.value.copy(busy = false)
                    job = null
                }
            }
        }.also { job = it }
    }
}
