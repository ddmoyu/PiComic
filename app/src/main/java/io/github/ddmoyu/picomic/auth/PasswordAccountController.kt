package io.github.ddmoyu.picomic.auth

import io.github.ddmoyu.picomic.source.picacg.*
import io.github.ddmoyu.picomic.content.*

import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.network.StaleNetworkException
import io.github.ddmoyu.picomic.network.networkError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PasswordLoginState(val busy: Boolean = false, val message: String? = null, val loginSucceeded: Boolean = false)

/** All entry points are called on the UI scope. Credentials are never part of observable UI state. */
open class PasswordAccountController(
    val sourceId: String,
    val title: String,
    private val sessions: SessionCoordinator,
    private val engine: NetworkEngine,
    private val scope: CoroutineScope,
    private val awaitNetwork: suspend () -> Unit,
    recovery: PasswordSessionRecovery? = null,
    private val apiFactory: suspend () -> PasswordAuthApi
) {
    val sessionRecovery = recovery ?: PasswordSessionRecovery(sourceId, sessions, engine, scope, awaitNetwork, apiFactory)
    val accounts = sessions.state
    val rememberedAccounts = sessions.rememberedAccounts
    private val mutable = MutableStateFlow(PasswordLoginState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var attempt: LoginAttempt? = null
    private var operation = 0L

    fun dismissLoginSuccess() { mutable.value = mutable.value.copy(loginSucceeded = false) }

    fun login(email: String, password: CharArray, rememberPassword: Boolean = false) {
        val account = email.trim()
        if (account.isBlank() || account.length > 320 || account.any { it.isISOControl() } || password.isEmpty() || password.size > 1024) {
            password.fill('\u0000')
            mutable.value = mutable.value.copy(message = "请输入账号和密码（账号最多 320 字，密码最多 1024 字）")
            return
        }
        val pending = start { authenticate(account, password, rememberPassword) }
        pending.invokeOnCompletion { password.fill('\u0000') }
    }

    fun loginSaved() { start {
        val saved = sessions.rememberedLogin(sourceId) ?: error("没有已保存的账号密码，请先手动登录")
        saved.use { authenticate(it.username, it.password, true) }
    } }
    fun forgetPassword() { start {
        sessions.forgetPassword(sourceId)
        mutable.value = mutable.value.copy(message = "已删除保存的账号密码，当前有效会话保留")
    } }
    private suspend fun authenticate(account: String, password: CharArray, rememberPassword: Boolean) {
        awaitNetwork()
        val active = sessions.begin(sourceId).also { attempt = it }
        val api = apiFactory()
        api.probe()
        currentCoroutineContext().ensureActive()
        if (!sessions.isCurrent(active)) throw CancellationException()
        val candidate = api.signIn(account, password)
        try {
            val retention = if (rememberPassword) PasswordRetention.Remember(RememberedLogin(account, password)) else PasswordRetention.Forget
            sessions.validateAndCommit(active, candidate, retention) { api.validate(it) }
            mutable.value = mutable.value.copy(loginSucceeded = true,
                message = if (rememberPassword) "登录成功，会话和账号密码已加密保存" else "登录成功，已验证账号并保存加密会话")
        } finally { candidate.value.fill(0) }
    }

    /** Manual verification and startup restoration share one job, so concurrent requests are coalesced. */
    fun restore() {
        if (job?.isActive == true) return
        start(cancelRecovery = false) {
            sessionRecovery.ensure(force = true)
            if (sessions.state.value[sourceId]?.status == AccountStatus.AUTHENTICATED)
                mutable.value = mutable.value.copy(message = "会话已恢复")
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
            mutable.value = mutable.value.copy(message = "已清除本机${title}会话和已保存的账号密码")
        }
    }

    fun cancel(cancelRecovery: Boolean = true) {
        operation++
        job?.cancel(); job = null
        attempt?.let(sessions::cancel); attempt = null
        if (cancelRecovery) sessionRecovery.cancel()
        mutable.value = mutable.value.copy(busy = false)
    }

    private fun start(cancelRecovery: Boolean = true, action: suspend () -> Unit): Job {
        cancel(cancelRecovery)
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
