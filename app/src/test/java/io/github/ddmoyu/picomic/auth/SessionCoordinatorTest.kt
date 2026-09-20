package io.github.ddmoyu.picomic.auth

import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.network.NetworkProfile
import io.github.ddmoyu.picomic.network.origin
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class SessionCoordinatorTest {
    @Test fun verifiedAccountPartitionSurvivesTokenRotationAndSeparatesAccounts() = runBlocking {
        val store = MemorySecrets(); val sessions = SessionCoordinator(store, NetworkEngine())
        suspend fun partition(token: String, account: String): String {
            sessions.validateAndCommit(sessions.begin("picacg"), SessionCandidate(CredentialKind.USER_TOKEN, token.toByteArray())) { ValidationResult.Verified("同名用户", account) }
            return sessions.lease("picacg").use { it.partition }
        }
        val first = partition("first-token", "account-1")
        assertEquals(first, partition("rotated-token", "account-1"))
        assertNotEquals(first, partition("rotated-token", "account-2"))
        assertFalse(first.contains("account-1"))
    }
    private class MemorySecrets : SecretStore {
        val data = mutableMapOf<String, ByteArray>()
        override fun read(key: String) = data[key]?.copyOf()
        override fun write(key: String, value: ByteArray) { data[key] = value.copyOf() }
        override fun remove(key: String) { data.remove(key) }
    }
    private fun candidate() = SessionCandidate(CredentialKind.USER_TOKEN, "test-session".toByteArray())
    @Test fun credentialsAreOnlyPersistedAfterValidationAndReloadNeedsValidation() = runBlocking {
        val store = MemorySecrets(); val engine = NetworkEngine(); val sessions = SessionCoordinator(store, engine)
        val attempt = sessions.begin("PICACG")
        assertTrue(store.data.isEmpty())
        sessions.validateAndCommit(attempt, candidate()) { ValidationResult.Verified("测试账号") }
        assertEquals(AccountStatus.AUTHENTICATED, sessions.state.value["PICACG"]?.status)
        val restored = SessionCoordinator(store, engine)
        assertNotNull(restored.storedCandidate("PICACG"))
        assertEquals(AccountStatus.NEEDS_VALIDATION, restored.state.value["PICACG"]?.status)
    }
    @Test fun failedOrCancelledReplacementPreservesOldSessionAndOtherSource() = runBlocking {
        val store = MemorySecrets(); val sessions = SessionCoordinator(store, NetworkEngine())
        listOf("PICACG", "JMCOMIC").forEach { source -> sessions.validateAndCommit(sessions.begin(source), candidate()) { ValidationResult.Verified(source) } }
        val original = store.data.getValue("session.PICACG").copyOf()
        sessions.validateAndCommit(sessions.begin("PICACG"), candidate()) { ValidationResult.Rejected("密码错误") }
        assertArrayEquals(original, store.data["session.PICACG"])
        sessions.cancel(sessions.begin("PICACG"))
        assertEquals(AccountStatus.AUTHENTICATED, sessions.state.value["PICACG"]?.status)
        sessions.logout("PICACG")
        assertNull(store.data["session.PICACG"])
        assertNotNull(store.data["session.JMCOMIC"])
    }
    @Test fun logoutRejectsLateValidationEvenWhenValidatorIgnoresCancellation() = runBlocking {
        val store = MemorySecrets(); val sessions = SessionCoordinator(store, NetworkEngine())
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val attempt = sessions.begin("PICACG")
        val job = launch { sessions.validateAndCommit(attempt, candidate()) {
            withContext(NonCancellable) { started.complete(Unit); release.await() }
            ValidationResult.Verified("迟到账号")
        } }
        started.await(); sessions.logout("PICACG"); release.complete(Unit); job.join()
        assertTrue(store.data.isEmpty())
        assertEquals(AccountStatus.ANONYMOUS, sessions.state.value["PICACG"]?.status)
    }
    @Test fun networkChangeInvalidatesCandidateAndCancelledAttemptCannotCommit() = runBlocking {
        val store = MemorySecrets(); val engine = NetworkEngine(); val sessions = SessionCoordinator(store, engine)
        val attempt = sessions.begin("PICACG")
        engine.change(NetworkProfile.FollowSystem); sessions.networkChanged()
        val outcome = runCatching { sessions.validateAndCommit(attempt, candidate()) { ValidationResult.Verified("旧网络") } }
        assertTrue(outcome.isFailure); assertTrue(store.data.isEmpty())
    }
    @Test fun webpageCookieDetectionRequiresEveryDeclaredCookieAndExactOrigin() {
        val url = "https://login.example.test/".toHttpUrl()
        val spec = WebLoginSpec(url, setOf(url.origin()), url, listOf(WebCookieScope("session", "/"), WebCookieScope("user", "/")))
        assertNull(spec.candidate("cf_clearance=challenge-only; user=42"))
        assertFalse(spec.allows("https://login.example.test.evil.test/"))
        assertFalse(spec.allows("http://login.example.test/"))
        assertFalse(spec.allows("javascript:alert(1)"))
        assertTrue(spec.allows(url.toString()))
        val value = spec.candidate("unrelated=ignored; user=42; session=fixture")!!
        assertEquals("session=fixture; user=42", String(value.value))
        assertFalse(value.toString().contains("fixture"))
    }
    @Test fun authenticatedLeaseDoesNotDemoteAccountAndLogoutCancelsItsRequests() = runBlocking {
        val sessions = SessionCoordinator(MemorySecrets(), NetworkEngine())
        sessions.validateAndCommit(sessions.begin("picacg"), candidate()) { ValidationResult.Verified("用户") }
        val lease = sessions.lease("picacg")
        assertEquals(AccountStatus.AUTHENTICATED, sessions.state.value["picacg"]?.status)
        val started = CompletableDeferred<Unit>()
        val request = async { runCatching { sessions.useLease(lease) { started.complete(Unit); awaitCancellation() } } }
        started.await(); sessions.logout("picacg")
        assertTrue(withTimeout(3000) { request.await() }.exceptionOrNull() is CancellationException)
        assertFalse(sessions.isCurrent(lease)); lease.close(); assertTrue(lease.candidate.value.all { it == 0.toByte() })
    }
    @Test fun late401CannotExpireAReplacementAccount() = runBlocking {
        val sessions = SessionCoordinator(MemorySecrets(), NetworkEngine())
        sessions.validateAndCommit(sessions.begin("picacg"), candidate()) { ValidationResult.Verified("旧账号") }
        val old = sessions.lease("picacg")
        sessions.validateAndCommit(sessions.begin("picacg"), candidate()) { ValidationResult.Verified("新账号") }
        sessions.expire(old); old.close()
        assertEquals("新账号", sessions.state.value["picacg"]?.displayName)
        assertEquals(AccountStatus.AUTHENTICATED, sessions.state.value["picacg"]?.status)
    }
}
