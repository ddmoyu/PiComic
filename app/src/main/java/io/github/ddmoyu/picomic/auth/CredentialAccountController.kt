package io.github.ddmoyu.picomic.auth

import io.github.ddmoyu.picomic.content.ContentFailure
import io.github.ddmoyu.picomic.content.ContentFailureKind
import io.github.ddmoyu.picomic.content.contentError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** API keys and web tokens have separate source slots; neither mode overwrites the other. */
class CredentialAccountController(val sourceId: String, private val sessions: SessionCoordinator, private val scope: CoroutineScope,
    private val awaitNetwork: suspend () -> Unit,
    private val validate: suspend (SessionCandidate) -> ValidationResult.Verified) {
    private val mutable = MutableStateFlow(PasswordLoginState())
    val state = mutable.asStateFlow()
    val accounts = sessions.state
    private var job: Job? = null
    private var attempt: LoginAttempt? = null
    private var sequence = 0L
    fun submit(candidate: SessionCandidate) {
        val operation = start {
            awaitNetwork()
            val active = sessions.begin(sourceId).also { attempt = it }
            sessions.validateAndCommit(active, candidate, validate)
            mutable.value = mutable.value.copy(message = "已验证账号并加密保存凭据")
        }
        operation.invokeOnCompletion { candidate.value.fill(0) }
    }
    fun restore() {
        if (job?.isActive == true) return
        start {
            val candidate = sessions.storedCandidate(sourceId) ?: return@start
            try {
                awaitNetwork()
                val active = sessions.begin(sourceId).also { attempt = it }
                try { sessions.validateAndCommit(active, candidate, validate) }
                catch (e: ContentFailure) { if (e.kind == ContentFailureKind.EXPIRED) sessions.expire(active); throw e }
                mutable.value = mutable.value.copy(message = "会话验证通过")
            } finally { candidate.value.fill(0) }
        }
    }
    fun logout() { start { sessions.logout(sourceId); mutable.value = mutable.value.copy(message = "已清除此认证方式的本地凭据") } }
    fun cancel() {
        sequence++; job?.cancel(); job = null
        attempt?.let(sessions::cancel); attempt = null
        mutable.value = mutable.value.copy(busy = false)
    }
    private fun start(action: suspend () -> Unit): Job {
        cancel(); val current = sequence; mutable.value = PasswordLoginState(true)
        return scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current == sequence) mutable.value = mutable.value.copy(message = contentError(e)) }
            finally { if (current == sequence) { attempt?.let(sessions::cancel); attempt = null; mutable.value = mutable.value.copy(busy = false); job = null } }
        }.also { job = it }
    }
}
