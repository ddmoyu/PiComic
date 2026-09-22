package io.github.ddmoyu.picomic.auth

import io.github.ddmoyu.picomic.content.ContentFailure
import io.github.ddmoyu.picomic.content.ContentFailureKind
import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.source.picacg.PicacgFailure
import io.github.ddmoyu.picomic.source.picacg.PicacgFailureKind
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** One recovery per source, shared by startup, reading and downloads; never owns UI credentials. */
class PasswordSessionRecovery(
    private val source: String,
    private val sessions: SessionCoordinator,
    private val engine: NetworkEngine,
    private val scope: CoroutineScope,
    private val awaitNetwork: suspend () -> Unit,
    private val apiFactory: suspend () -> PasswordAuthApi
) {
    private data class Stamp(val session: Long?, val network: Long)
    private val lock = Any()
    private var flight: Deferred<Result<Unit>>? = null
    private var attempt: LoginAttempt? = null
    private var rejected: Pair<Stamp, Exception>? = null
    private fun stamp() = Stamp(sessions.changes.value[source], engine.status.generation)

    suspend fun ensure(force: Boolean = false, failed: SessionLease? = null) {
        val pending = synchronized(lock) {
            flight?.takeIf { it.isActive } ?: run {
                if (failed != null && failed.networkGeneration != engine.status.generation)
                    throw CancellationException("网络已更改")
                if (!force && sessions.state.value[source]?.status == AccountStatus.AUTHENTICATED &&
                    (failed == null || !sessions.isCurrent(failed))) return
                if (force) rejected = null
                rejected?.takeIf { it.first == stamp() }?.let { throw it.second }
                scope.async(start = CoroutineStart.LAZY) {
                    try { restore(failed); Result.success(Unit) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        currentCoroutineContext().ensureActive()
                        // A wrong password/challenge must not create a login loop after UI recomposition.
                        if (e.isAuthenticationFailure()) synchronized(lock) { rejected = stamp() to e }
                        Result.failure(e)
                    }
                }.also { flight = it; it.start() }
            }
        }
        pending.await().getOrThrow()
    }

    private suspend fun restore(failed: SessionLease?) {
        if (failed == null) sessions.requireValidation(source)
        awaitNetwork()
        // An interactive login owns its attempt. Wait for it instead of replacing it.
        if (sessions.state.value[source]?.status == AccountStatus.AUTHENTICATING) {
            sessions.state.firstReady(source)
            if (sessions.state.value[source]?.status == AccountStatus.AUTHENTICATED &&
                (failed == null || !sessions.isCurrent(failed))) return
        }
        val stored = sessions.beginStoredRecovery(source, failed) ?: return
        val active = stored.first
        synchronized(lock) { attempt = active }
        var expired = failed != null
        try {
            stored.second.use { account ->
                val api = apiFactory()
                api.probe()
                checkCurrent(active)
                if (!expired && account.candidate != null) {
                    try {
                        sessions.validateAndCommit(active, account.candidate) { api.validate(it) }
                        return
                    } catch (e: Exception) {
                        if (!e.isExpiredSession()) throw e
                        expired = true
                    }
                }
                val saved = account.login ?: throw ContentFailure(ContentFailureKind.EXPIRED, "会话已失效，请重新登录")
                checkCurrent(active)
                // A fresh client must not carry cookies rejected during the token-first attempt.
                val loginApi = apiFactory()
                loginApi.probe()
                checkCurrent(active)
                val candidate = loginApi.signIn(saved.username, saved.password)
                try {
                    checkCurrent(active)
                    sessions.validateAndCommit(active, candidate, PasswordRetention.Remember(saved)) { loginApi.validate(it) }
                } finally { candidate.value.fill(0) }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) checkCurrent(active)
            if (expired && e !is CancellationException) sessions.expire(active)
            throw e
        } finally {
            sessions.cancel(active)
            synchronized(lock) { if (attempt == active) attempt = null }
        }
    }

    private suspend fun checkCurrent(active: LoginAttempt) {
        currentCoroutineContext().ensureActive()
        if (!sessions.isCurrent(active)) throw CancellationException("账号或网络已更改")
    }

    /** A replay also expired: stop, retain remembered credentials, and require explicit verification. */
    suspend fun reject(lease: SessionLease, error: Exception) {
        if (!sessions.isCurrent(lease)) return
        sessions.expire(lease)
        synchronized(lock) { rejected = stamp() to error }
    }

    fun cancel() = synchronized(lock) {
        flight?.cancel(); flight = null
        attempt?.let(sessions::cancel); attempt = null
        rejected = null
    }
}

internal fun Exception.isExpiredSession() =
    this is ContentFailure && kind == ContentFailureKind.EXPIRED ||
        this is PicacgFailure && kind == PicacgFailureKind.EXPIRED

private fun Exception.isAuthenticationFailure() = isExpiredSession() ||
    this is ContentFailure && kind in setOf(ContentFailureKind.LOGIN, ContentFailureKind.ACCESS_DENIED) ||
    this is PicacgFailure && kind in setOf(PicacgFailureKind.CREDENTIALS, PicacgFailureKind.ACCESS_DENIED)

private suspend fun kotlinx.coroutines.flow.StateFlow<Map<String, AccountState>>.firstReady(source: String) {
    first { it[source]?.status != AccountStatus.AUTHENTICATING }
}
