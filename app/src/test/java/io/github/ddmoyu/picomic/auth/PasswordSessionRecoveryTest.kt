package io.github.ddmoyu.picomic.auth

import io.github.ddmoyu.picomic.content.ContentFailure
import io.github.ddmoyu.picomic.content.ContentFailureKind
import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.network.NetworkProfile
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class PasswordSessionRecoveryTest {
    private class Store : SecretStore {
        val values = mutableMapOf<String, ByteArray>()
        override fun read(key: String) = values[key]?.copyOf()
        override fun write(key: String, value: ByteArray) { values[key] = value.copyOf() }
        override fun remove(key: String) { values.remove(key) }
    }
    private open class Api : PasswordAuthApi {
        val logins = AtomicInteger()
        val tokens = mutableListOf<String>()
        var password: CharArray? = null
        var expired = false
        var loginError: Exception? = null
        override suspend fun probe() = Unit
        override suspend fun signIn(email: String, password: CharArray): SessionCandidate {
            assertEquals("fixture-user", email)
            assertEquals("fixture-password", String(password))
            logins.incrementAndGet(); this.password = password
            loginError?.let { throw it }
            return token("renewed")
        }
        override suspend fun profile(candidate: SessionCandidate) = "安全测试账号"
        override suspend fun validate(candidate: SessionCandidate): ValidationResult.Verified {
            val value = candidate.value.toString(Charsets.UTF_8)
            tokens += value
            if (expired && value == "stored") throw expired()
            return ValidationResult.Verified("安全测试账号", "fixture-id")
        }
    }
    private class Fixture(val scope: CoroutineScope, val api: Api = Api()) {
        val store = Store()
        val engine = NetworkEngine()
        var sessions = SessionCoordinator(store, engine)
        val recovery get() = PasswordSessionRecovery("jmcomic", sessions, engine, scope, {}, { api })
        suspend fun seed(remember: Boolean = true) {
            val retention = if (remember) PasswordRetention.Remember(RememberedLogin("fixture-user", "fixture-password".toCharArray())) else PasswordRetention.Forget
            sessions.validateAndCommit(sessions.begin("jmcomic"), token("stored"), retention) { ValidationResult.Verified("安全测试账号", "fixture-id") }
            if (retention is PasswordRetention.Remember) retention.login.close()
        }
        fun restart() { sessions = SessionCoordinator(store, engine) }
        fun status() = sessions.state.value["jmcomic"]?.status
        fun savedToken() = StoredAccountCodec.decode(store.values.getValue("session.jmcomic")).use { it.candidate?.value?.toString(Charsets.UTF_8) }
    }

    @Test fun coldStartUsesEncryptedSessionBeforePassword() = runBlocking {
        val f = Fixture(this); f.seed(); f.restart()
        f.recovery.ensure()
        assertEquals(listOf("stored"), f.api.tokens)
        assertEquals(0, f.api.logins.get())
        assertEquals(AccountStatus.AUTHENTICATED, f.status())
        assertEquals("stored", f.savedToken())
    }

    @Test fun expiredStoredSessionLogsInOnceAndPersistsNewSession() = runBlocking {
        val f = Fixture(this); f.seed(); f.restart(); f.api.expired = true
        f.recovery.ensure()
        assertEquals(listOf("stored", "renewed"), f.api.tokens)
        assertEquals(1, f.api.logins.get()); assertEquals("renewed", f.savedToken())
        assertTrue(f.api.password!!.all { it == '\u0000' })
        f.sessions.rememberedLogin("jmcomic")!!.use { assertEquals("fixture-user", it.username) }
    }

    @Test fun passwordOnlyAccountAfterPreviousExpiryRestoresAutomatically() = runBlocking {
        val f = Fixture(this); f.seed()
        f.sessions.lease("jmcomic").use { f.sessions.expire(it) }; f.restart()
        f.recovery.ensure()
        assertEquals(1, f.api.logins.get()); assertEquals("renewed", f.savedToken())
    }

    @Test fun simultaneousExpirySharesOneLoginAndDoesNotRetryRejectedToken() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val api = object : Api() {
            override suspend fun signIn(email: String, password: CharArray): SessionCandidate {
                entered.complete(Unit); release.await(); return super.signIn(email, password)
            }
        }
        val f = Fixture(this, api); f.seed()
        val recovery = f.recovery
        f.sessions.lease("jmcomic").use { lease ->
            val first = async { recovery.ensure(failed = lease) }
            entered.await()
            val others = List(8) { async(start = CoroutineStart.UNDISPATCHED) { recovery.ensure(failed = lease) } }
            release.complete(Unit); first.await(); others.awaitAll()
        }
        assertEquals(1, api.logins.get()); assertEquals(listOf("renewed"), api.tokens)
    }

    @Test fun failedPasswordIsSharedAndDoesNotLoopUntilExplicitRetry() = runBlocking {
        val f = Fixture(this); f.seed(); f.restart(); f.api.expired = true
        f.api.loginError = ContentFailure(ContentFailureKind.LOGIN, "账号密码未通过")
        val recovery = f.recovery
        repeat(4) { assertTrue(runCatching { recovery.ensure() }.exceptionOrNull() is ContentFailure) }
        assertEquals(1, f.api.logins.get()); assertNull(f.savedToken())
        f.api.loginError = null
        recovery.ensure(force = true)
        assertEquals(2, f.api.logins.get()); assertEquals(AccountStatus.AUTHENTICATED, f.status())
    }

    @Test fun temporaryNetworkErrorKeepsTokenAndDoesNotSubmitPassword() = runBlocking {
        var offline = true
        val api = object : Api() { override suspend fun probe() { if (offline) throw IOException("offline") } }
        val f = Fixture(this, api); f.seed(); f.restart()
        val recovery = f.recovery
        assertTrue(runCatching { recovery.ensure() }.exceptionOrNull() is IOException)
        assertEquals("stored", f.savedToken()); assertEquals(0, api.logins.get())
        offline = false; recovery.ensure()
        assertEquals(AccountStatus.AUTHENTICATED, f.status()); assertEquals(0, api.logins.get())
    }

    @Test fun expiredTokenWithoutRememberedPasswordNeverPostsLogin() = runBlocking {
        val f = Fixture(this); f.seed(remember = false); f.restart(); f.api.expired = true
        assertTrue(runCatching { f.recovery.ensure() }.exceptionOrNull() is ContentFailure)
        assertEquals(0, f.api.logins.get()); assertFalse(f.store.values.containsKey("session.jmcomic"))
    }

    @Test fun accessDenialDoesNotEraseTokenOrRetryPassword() = runBlocking {
        val api = object : Api() {
            override suspend fun validate(candidate: SessionCandidate): ValidationResult.Verified = throw ContentFailure(ContentFailureKind.ACCESS_DENIED, "权限不足")
        }
        val f = Fixture(this, api); f.seed(); f.restart()
        assertTrue(runCatching { f.recovery.ensure() }.exceptionOrNull() is ContentFailure)
        assertEquals("stored", f.savedToken()); assertEquals(0, api.logins.get())
    }

    @Test fun logoutDuringLoginCannotRestoreTheOldAccount() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val api = object : Api() {
            override suspend fun signIn(email: String, password: CharArray): SessionCandidate {
                withContext(NonCancellable) { entered.complete(Unit); release.await() }
                return super.signIn(email, password)
            }
        }
        val f = Fixture(this, api); f.seed(); f.restart(); api.expired = true
        val recovery = f.recovery
        val pending = async { runCatching { recovery.ensure() } }
        entered.await(); f.sessions.logout("jmcomic"); release.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertEquals(AccountStatus.ANONYMOUS, f.status()); assertTrue(f.store.values.isEmpty())
    }

    @Test fun networkChangeAfterProbeDoesNotSendPassword() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val api = object : Api() { override suspend fun probe() { entered.complete(Unit); release.await() } }
        val f = Fixture(this, api); f.seed(); f.restart()
        val pending = async { runCatching { f.recovery.ensure() } }
        entered.await(); f.engine.change(NetworkProfile.FollowSystem); f.sessions.networkChanged(); release.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertEquals(0, api.logins.get()); assertEquals("stored", f.savedToken())
    }

    @Test fun cancellingOneWaitingScreenDoesNotCancelSharedRecovery() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val api = object : Api() { override suspend fun probe() { entered.complete(Unit); release.await() } }
        val f = Fixture(this, api); f.seed(); f.restart()
        val recovery = f.recovery
        val first = launch { recovery.ensure() }; entered.await()
        val second = async { recovery.ensure() }
        first.cancelAndJoin(); release.complete(Unit); second.await()
        assertEquals(AccountStatus.AUTHENTICATED, f.status()); assertEquals(listOf("stored"), api.tokens)
    }

    @Test fun replayExpiryDoesNotBeginAnotherAutomaticLogin() = runBlocking {
        val f = Fixture(this); f.seed()
        val recovery = f.recovery
        f.sessions.lease("jmcomic").use { recovery.ensure(failed = it) }
        f.sessions.lease("jmcomic").use { recovery.reject(it, expired()) }
        assertTrue(runCatching { recovery.ensure() }.exceptionOrNull() is ContentFailure)
        assertEquals(1, f.api.logins.get()); assertEquals(AccountStatus.EXPIRED, f.status())
    }

    companion object {
        private fun token(value: String) = SessionCandidate(CredentialKind.COOKIE, value.toByteArray())
        private fun expired() = ContentFailure(ContentFailureKind.EXPIRED, "会话已过期")
    }
}
