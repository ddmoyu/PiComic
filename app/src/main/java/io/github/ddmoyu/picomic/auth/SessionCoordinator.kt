package io.github.ddmoyu.picomic.auth

import io.github.ddmoyu.picomic.network.NetworkEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

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
    private val remembered = MutableStateFlow<Map<String, String>>(emptyMap())
    val rememberedAccounts = remembered.asStateFlow()

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

    suspend fun validateAndCommit(attempt: LoginAttempt, candidate: SessionCandidate, retention: PasswordRetention = PasswordRetention.Preserve,
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
                    val previousAccount = if (retention == PasswordRetention.Preserve) readAccount(attempt.source) else null
                    try {
                        val login = when (retention) {
                            PasswordRetention.Forget -> null
                            PasswordRetention.Preserve -> previousAccount?.takeIf {
                                if (it.accountId != null) it.accountId == result.accountId else it.displayName == result.displayName
                            }?.login
                            is PasswordRetention.Remember -> retention.login.also { require(attempt.source in AccountSlots.passwords) }
                        }
                        val bytes = StoredAccountCodec.encode(StoredAccount(result.displayName, result.accountId, candidate, login))
                        try { network.withGeneration(attempt.networkGeneration) { store.write("session.${attempt.source}", bytes) } }
                        finally { bytes.fill(0) }
                        rememberedName(attempt.source, login?.username)
                    } finally { previousAccount?.close() }
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
            network.withGeneration(attempt.networkGeneration) { removeSessionRetainingPassword(attempt.source) }
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
            rememberedName(source, null)
            publish(source, AccountState())
        }
    }
    /** Reading a stored credential only establishes that validation is needed, never success. */
    suspend fun storedCandidate(source: String): SessionCandidate? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val account = readAccount(source)
            rememberedName(source, account?.login?.username)
            account?.use {
                if (it.candidate == null) {
                    publish(source, AccountState(AccountStatus.EXPIRED, it.displayName))
                    return@synchronized null
                }
                publish(source, AccountState(AccountStatus.NEEDS_VALIDATION, it.displayName))
                SessionCandidate(it.candidate.kind, it.candidate.value.copyOf(), it.accountId)
            }
        }
    }
    suspend fun lease(source: String): SessionLease = withContext(Dispatchers.IO) {
        synchronized(lock) {
            check(mutable.value[source]?.status == AccountStatus.AUTHENTICATED) { "请先登录并验证账号" }
            (readAccount(source) ?: error("会话不存在")).use { account ->
                val candidate = account.candidate ?: error("会话已失效")
                SessionLease(source, generations[source] ?: 0, network.status.generation,
                    SessionCandidate(candidate.kind, candidate.value.copyOf(), account.accountId))
            }
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
            removeSessionRetainingPassword(lease.source)
            generations[lease.source] = lease.generation + 1
            revisions.value = generations.toMap()
            cancelLocked(lease.source)
            publish(lease.source, AccountState(AccountStatus.EXPIRED))
        }
    }
    suspend fun rememberedLogin(source: String): RememberedLogin? = withContext(Dispatchers.IO) {
        synchronized(lock) { readAccount(source)?.use { account -> account.login?.let { RememberedLogin(it.username, it.password.copyOf()) } } }
    }
    suspend fun forgetPassword(source: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            readAccount(source)?.use { account ->
                if (account.candidate == null) store.remove("session.$source")
                else {
                    val bytes = StoredAccountCodec.encode(StoredAccount(account.displayName, account.accountId, account.candidate, null))
                    try { store.write("session.$source", bytes) } finally { bytes.fill(0) }
                }
            }
            rememberedName(source, null)
        }
    }
    suspend fun exportAccounts(): List<AccountTransfer> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val result = mutableListOf<AccountTransfer>()
            try {
                for (source in AccountSlots.titles.keys) store.read("session.$source")?.let { result += AccountTransfer(source, it) }
                result
            } catch (e: Exception) { result.forEach(AccountTransfer::close); throw e }
        }
    }
    suspend fun accountFingerprint(source: String): String = withContext(Dispatchers.IO) { synchronized(lock) { fingerprint(source) } }
    suspend fun importAccount(transfer: AccountTransfer, expectedFingerprint: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val source = transfer.source
            check(fingerprint(source) == expectedFingerprint) { "本机账号已变化，请重新预览" }
            StoredAccountCodec.decode(transfer.bytes).use { account ->
                require(AccountSlots.accepts(source, account.candidate?.kind) && (account.login == null || source in AccountSlots.passwords))
                store.write("session.$source", transfer.bytes)
                cancelLocked(source)
                generations[source] = (generations[source] ?: 0) + 1
                revisions.value = generations.toMap()
                rememberedName(source, account.login?.username)
                publish(source, AccountState(if (account.candidate != null) AccountStatus.NEEDS_VALIDATION else AccountStatus.EXPIRED, account.displayName))
            }
        }
    }
    private fun readAccount(source: String): StoredAccount? = store.read("session.$source")?.let { bytes ->
        try { StoredAccountCodec.decode(bytes) } finally { bytes.fill(0) }
    }
    private fun fingerprint(source: String): String = store.read("session.$source")?.let { bytes ->
        try { java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } }
        finally { bytes.fill(0) }
    } ?: "absent"
    private fun removeSessionRetainingPassword(source: String) {
        readAccount(source)?.use { account ->
            if (account.login == null) store.remove("session.$source")
            else {
                val bytes = StoredAccountCodec.encode(StoredAccount(account.displayName, account.accountId, null, account.login))
                try { store.write("session.$source", bytes) } finally { bytes.fill(0) }
            }
            rememberedName(source, account.login?.username)
        }
    }
    private fun rememberedName(source: String, username: String?) {
        remembered.value = if (username == null) remembered.value - source else remembered.value + (source to username)
    }
    private fun publish(source: String, value: AccountState) { mutable.value = mutable.value + (source to value) }
}
