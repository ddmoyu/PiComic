package io.github.ddmoyu.picomic.auth

import io.github.ddmoyu.picomic.network.NetworkEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

enum class CredentialKind { USER_TOKEN, COOKIE, API_KEY }
class SessionCandidate(val kind: CredentialKind, val value: ByteArray, val accountId: String? = null) {
    init { require(value.isNotEmpty() && value.size <= 48 * 1024) }
    override fun toString() = "SessionCandidate($kind, [redacted])"
}
enum class AccountStatus { ANONYMOUS, AUTHENTICATING, AUTHENTICATED, NEEDS_VALIDATION, EXPIRED }
data class AccountProfile(val avatar: String? = null, val frame: String? = null, val level: Int? = null, val title: String? = null)
data class AccountState(val status: AccountStatus = AccountStatus.ANONYMOUS, val displayName: String? = null, val profile: AccountProfile? = null)
data class LoginAttempt(val source: String, val id: Long, val sessionGeneration: Long, val networkGeneration: Long)
class SessionLease internal constructor(val source: String, val generation: Long, val networkGeneration: Long, val candidate: SessionCandidate) : AutoCloseable {
    val partition = java.security.MessageDigest.getInstance("SHA-256").digest(candidate.accountId?.let { "$source/$it".toByteArray() } ?: candidate.value).take(16).joinToString("") { "%02x".format(it) }
    override fun close() { candidate.value.fill(0) }
    override fun toString() = "SessionLease($source, [redacted])"
}
sealed interface ValidationResult {
    data class Verified(val displayName: String, val accountId: String? = null, val profile: AccountProfile? = null) : ValidationResult
    data class Rejected(val reason: String) : ValidationResult
}

/** A candidate can only become a session after source validation and both generation checks. */
class SessionCoordinator(private val store: SecretStore, private val network: NetworkEngine) {
    private val lock = Any()
    private var sequence = 0L
    private val generations = mutableMapOf<String, Long>()
    private val revisions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val changes = revisions.asStateFlow()
    private val attempts = mutableMapOf<String, LoginAttempt>()
    private val jobs = mutableMapOf<String, Job>()
    private val previous = mutableMapOf<String, AccountState>()
    private val mutable = MutableStateFlow<Map<String, AccountState>>(emptyMap())
    val state = mutable.asStateFlow()

    fun begin(source: String): LoginAttempt = synchronized(lock) {
        require(source.matches(Regex("[a-zA-Z0-9_-]{1,40}")))
        cancelLocked(source)
        previous[source] = mutable.value[source] ?: AccountState()
        LoginAttempt(source, ++sequence, generations[source] ?: 0, network.status.generation).also {
            attempts[source] = it
            publish(source, (previous[source] ?: AccountState()).copy(status = AccountStatus.AUTHENTICATING))
        }
    }
    fun isCurrent(attempt: LoginAttempt): Boolean = synchronized(lock) { current(attempt) }
    private fun current(attempt: LoginAttempt) = attempts[attempt.source] == attempt &&
        (generations[attempt.source] ?: 0) == attempt.sessionGeneration && network.status.generation == attempt.networkGeneration

    suspend fun validateAndCommit(attempt: LoginAttempt, candidate: SessionCandidate,
                                  validate: suspend (SessionCandidate) -> ValidationResult): ValidationResult = withContext(Dispatchers.IO) {
        val validationJob = currentCoroutineContext()[Job]!!
        try {
            synchronized(lock) {
                check(current(attempt)) { "登录已取消或网络已更改" }
                check(jobs[attempt.source] == null) { "正在验证，请稍候" }
                jobs[attempt.source] = validationJob
            }
            val result = validate(candidate)
            ensureActive()
            synchronized(lock) {
                if (!current(attempt)) throw CancellationException("登录已失效")
                if (result is ValidationResult.Verified) {
                    require(result.displayName.isNotBlank() && result.displayName.length <= 200)
                    require(result.accountId == null || (result.accountId.isNotBlank() && result.accountId.length <= 512 && result.accountId.none(Char::isISOControl)))
                    val bytes = ByteArrayOutputStream().also { buffer ->
                        DataOutputStream(buffer).use { out ->
                            out.writeInt(2); out.writeUTF(candidate.kind.name); out.writeUTF(result.displayName); out.writeUTF(result.accountId.orEmpty())
                            out.writeInt(candidate.value.size); out.write(candidate.value)
                        }
                    }.toByteArray()
                    try {
                        network.withGeneration(attempt.networkGeneration) { store.write("session.${attempt.source}", bytes) }
                    } finally { bytes.fill(0) }
                    generations[attempt.source] = attempt.sessionGeneration + 1
                    revisions.value = generations.toMap()
                    attempts.remove(attempt.source)
                    previous.remove(attempt.source)
                    publish(attempt.source, AccountState(AccountStatus.AUTHENTICATED, result.displayName, result.profile))
                } else {
                    // A rejected replacement must not erase the previous, potentially usable session.
                    publish(attempt.source, previous[attempt.source] ?: AccountState())
                }
            }
            result
        } finally {
            candidate.value.fill(0)
            synchronized(lock) {
                if (attempts[attempt.source] == attempt && jobs[attempt.source] == validationJob) {
                    jobs.remove(attempt.source)
                    publish(attempt.source, previous[attempt.source] ?: AccountState())
                } else if (attempts[attempt.source] == null) jobs.remove(attempt.source)
            }
        }
    }

    fun cancel(attempt: LoginAttempt) = synchronized(lock) { if (attempts[attempt.source] == attempt) cancelLocked(attempt.source) }
    fun networkChanged() = synchronized(lock) {
        attempts.keys.toList().forEach(::cancelLocked)
        mutable.value = mutable.value.mapValues { (_, account) ->
            if (account.status == AccountStatus.AUTHENTICATED) account.copy(status = AccountStatus.NEEDS_VALIDATION) else account
        }
    }
    /** Only rejection of the current stored session can expire it; a failed replacement cannot. */
    suspend fun expire(attempt: LoginAttempt) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (!current(attempt)) return@synchronized
            network.withGeneration(attempt.networkGeneration) { store.remove("session.${attempt.source}") }
            generations[attempt.source] = attempt.sessionGeneration + 1
            revisions.value = generations.toMap()
            cancelLocked(attempt.source)
            publish(attempt.source, AccountState(AccountStatus.EXPIRED))
        }
    }
    private fun cancelLocked(source: String) {
        attempts.remove(source)
        jobs.remove(source)?.cancel()
        previous.remove(source)?.let { publish(source, it) }
    }
    suspend fun logout(source: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            generations[source] = (generations[source] ?: 0) + 1
            revisions.value = generations.toMap()
            cancelLocked(source)
            store.remove("session.$source")
            publish(source, AccountState())
        }
    }
    /** Reading a stored credential only establishes that validation is needed, never success. */
    suspend fun storedCandidate(source: String): SessionCandidate? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val bytes = store.read("session.$source") ?: return@synchronized null
            try {
                java.io.DataInputStream(bytes.inputStream()).use { input ->
                    val version = input.readInt(); require(version in 1..2)
                    val kind = CredentialKind.valueOf(input.readUTF())
                    val displayName = input.readUTF()
                    val identity = if (version == 2) input.readUTF().ifBlank { null } else null
                    val length = input.readInt()
                    require(length in 1..(48 * 1024) && length == input.available())
                    val value = ByteArray(length).also(input::readFully)
                    publish(source, AccountState(AccountStatus.NEEDS_VALIDATION, displayName))
                    SessionCandidate(kind, value, identity)
                }
            } finally { bytes.fill(0) }
        }
    }
    suspend fun lease(source: String): SessionLease = withContext(Dispatchers.IO) {
        synchronized(lock) {
            check(mutable.value[source]?.status == AccountStatus.AUTHENTICATED) { "请先登录并验证账号" }
            val bytes = store.read("session.$source") ?: error("会话不存在")
            try {
                java.io.DataInputStream(bytes.inputStream()).use { input ->
                    val version = input.readInt(); require(version in 1..2)
                    val kind = CredentialKind.valueOf(input.readUTF()); input.readUTF()
                    val identity = if (version == 2) input.readUTF().ifBlank { null } else null
                    val length = input.readInt(); require(length in 1..(48 * 1024) && length == input.available())
                    SessionLease(source, generations[source] ?: 0, network.status.generation,
                        SessionCandidate(kind, ByteArray(length).also(input::readFully), identity))
                }
            } finally { bytes.fill(0) }
        }
    }
    fun isCurrent(lease: SessionLease): Boolean = synchronized(lock) {
        generations[lease.source] == lease.generation && network.status.generation == lease.networkGeneration
    }
    suspend fun <T> useLease(lease: SessionLease, action: suspend () -> T): T = coroutineScope {
        check(isCurrent(lease)) { "账号或网络已更改" }
        val request = async { action() }
        val watcher = launch {
            combine(revisions, network.generations) { _, _ -> !isCurrent(lease) }.first { it }
            request.cancel(CancellationException("账号或网络已更改"))
        }
        try { request.await().also { if (!isCurrent(lease)) throw CancellationException("会话已失效") } }
        finally { watcher.cancel() }
    }
    suspend fun expire(lease: SessionLease) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (!isCurrent(lease)) return@synchronized
            store.remove("session.${lease.source}")
            generations[lease.source] = lease.generation + 1
            revisions.value = generations.toMap()
            cancelLocked(lease.source)
            publish(lease.source, AccountState(AccountStatus.EXPIRED))
        }
    }
    private fun publish(source: String, value: AccountState) { mutable.value = mutable.value + (source to value) }
}
