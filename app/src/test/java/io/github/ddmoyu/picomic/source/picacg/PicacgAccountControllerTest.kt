package io.github.ddmoyu.picomic.source.picacg

import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.network.NetworkProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class PicacgAccountControllerTest {
    @Test fun autofillReadsStoredCredentialsWithoutNetworkAndClearsTheTemporaryCopy() = runBlocking {
        val f = Fixture(this)
        f.controller.login("fixture-user", "fixture-password".toCharArray(), true); f.done()
        var temporary: CharArray? = null
        f.controller.fillRememberedLogin { saved ->
            assertEquals("fixture-user", saved.username)
            assertArrayEquals("fixture-password".toCharArray(), saved.password)
            temporary = saved.password
        }
        assertTrue(temporary!!.all { it == '\u0000' })
        assertEquals(1, f.api.probes); assertEquals(1, f.api.logins)
        f.sessions.rememberedLogin("picacg")!!.use { assertArrayEquals("fixture-password".toCharArray(), it.password) }
        f.controller.forgetPassword(); f.done()
        f.controller.fillRememberedLogin { fail("Forgotten passwords must not autofill") }
    }
    @Test fun successfulManualOrSavedLoginAcknowledgesButRestorationStaysQuiet() = runBlocking {
        val f = Fixture(this)
        f.controller.login("fixture", charArrayOf('x'), true); f.done()
        assertTrue(f.controller.state.value.loginSucceeded)
        f.controller.dismissLoginSuccess(); assertFalse(f.controller.state.value.loginSucceeded)
        f.controller.restore(); f.done(); assertFalse(f.controller.state.value.loginSucceeded)
        f.controller.loginSaved(); f.done(); assertTrue(f.controller.state.value.loginSucceeded)
        f.controller.logout(); f.done(); assertFalse(f.controller.state.value.loginSucceeded)
    }
    private class Store : SecretStore {
        val data = mutableMapOf<String, ByteArray>()
        override fun read(key: String) = data[key]?.copyOf()
        override fun write(key: String, value: ByteArray) { data[key] = value.copyOf() }
        override fun remove(key: String) { data.remove(key) }
    }
    private open class Api : PicacgAuthApi {
        var probes = 0; var logins = 0; var profiles = 0
        override suspend fun probe() { probes++ }
        override suspend fun signIn(email: String, password: CharArray): SessionCandidate { logins++; return candidate() }
        override suspend fun profile(candidate: SessionCandidate): String { profiles++; return "夹具账号" }
    }
    private class Fixture(scope: CoroutineScope, val api: Api = Api(), val ready: suspend () -> Unit = {}) {
        val store = Store(); val engine = NetworkEngine(); val sessions = SessionCoordinator(store, engine)
        val controller = PicacgAccountController(sessions, engine, scope, ready) { api }
        suspend fun done() { withTimeout(5000) { controller.state.first { !it.busy } }; yield() }
        fun status() = sessions.state.value[PicacgAccountController.SOURCE]?.status ?: AccountStatus.ANONYMOUS
        suspend fun seed() { sessions.validateAndCommit(sessions.begin(PicacgAccountController.SOURCE), candidate()) { ValidationResult.Verified("旧账号") } }
    }
    @Test fun loginProbesAndValidatesBeforeSavingAndClearsPassword() = runBlocking {
        val f = Fixture(this); val password = "fixture-password".toCharArray()
        f.controller.login("fixture@example.test", password); f.done()
        assertEquals(1, f.api.probes); assertEquals(1, f.api.logins); assertEquals(1, f.api.profiles)
        assertEquals(AccountStatus.AUTHENTICATED, f.status()); assertTrue(password.all { it == '\u0000' })
        assertEquals(setOf("session.picacg"), f.store.data.keys)
        assertFalse(f.controller.state.value.toString().contains("fixture-password"))
    }
    @Test fun rememberedPasswordAttemptsRecoveryOnceAndStillAllowsExplicitRelogin() = runBlocking {
        var expired = false
        val f = Fixture(this, object : Api() {
            override suspend fun signIn(email: String, password: CharArray): SessionCandidate {
                assertEquals("fixture-user", email); assertArrayEquals("fixture-password".toCharArray(), password)
                return super.signIn(email, password)
            }
            override suspend fun profile(candidate: SessionCandidate): String {
                if (expired) throw PicacgFailure(PicacgFailureKind.EXPIRED)
                return super.profile(candidate)
            }
        })
        val password = "fixture-password".toCharArray()
        f.controller.login("fixture-user", password, true); f.done()
        assertTrue(password.all { it == '\u0000' }); assertEquals("fixture-user", f.controller.rememberedAccounts.value["picacg"])
        expired = true; f.controller.restore(); f.done()
        assertEquals(AccountStatus.EXPIRED, f.status()); assertEquals(2, f.api.logins)
        f.sessions.rememberedLogin("picacg")!!.use { assertEquals("fixture-user", it.username) }
        expired = false; f.controller.loginSaved(); f.done()
        assertEquals(3, f.api.logins); assertEquals(AccountStatus.AUTHENTICATED, f.status())
        f.controller.forgetPassword(); f.done()
        assertNull(f.sessions.rememberedLogin("picacg")); assertEquals(AccountStatus.AUTHENTICATED, f.status())
    }
    @Test fun disablingRememberOnNextSuccessfulLoginRemovesSavedPassword() = runBlocking {
        val f = Fixture(this)
        f.controller.login("fixture", "fixture-password".toCharArray(), true); f.done()
        assertTrue(f.controller.rememberedAccounts.value.isNotEmpty())
        f.controller.login("fixture", "fixture-password".toCharArray(), false); f.done()
        assertNull(f.sessions.rememberedLogin("picacg")); assertEquals(AccountStatus.AUTHENTICATED, f.status())
    }
    @Test fun tokenAloneCannotAuthenticateAndFailedReplacementPreservesExistingSession() = runBlocking {
        val f = Fixture(this, object : Api() { override suspend fun profile(candidate: SessionCandidate): String { throw PicacgFailure(PicacgFailureKind.EXPIRED) } })
        f.seed(); val old = f.store.data.getValue("session.picacg").copyOf()
        f.controller.login("fixture", charArrayOf('x')); f.done()
        assertArrayEquals(old, f.store.data["session.picacg"])
        assertEquals(AccountStatus.AUTHENTICATED, f.status())
        assertTrue(f.controller.state.value.message!!.contains("会话已失效"))
        assertFalse(f.controller.state.value.loginSucceeded)
    }
    @Test fun expiredStoredSessionIsRemovedAndRequiresNewLogin() = runBlocking {
        val f = Fixture(this, object : Api() { override suspend fun profile(candidate: SessionCandidate): String { throw PicacgFailure(PicacgFailureKind.EXPIRED) } })
        f.seed(); f.controller.restore(); f.done()
        assertEquals(AccountStatus.EXPIRED, f.status()); assertTrue(f.store.data.isEmpty())
        assertEquals(0, f.api.logins)
    }
    @Test fun unavailableNetworkCannotSendCredentials() = runBlocking {
        val f = Fixture(this, ready = { error("not ready") })
        f.controller.login("fixture", charArrayOf('x')); f.done()
        assertEquals(0, f.api.probes); assertEquals(0, f.api.logins); assertTrue(f.store.data.isEmpty())
    }
    @Test fun changedNetworkAfterProbeCannotSendPassword() = runBlocking {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val f = Fixture(this, object : Api() { override suspend fun probe() { started.complete(Unit); release.await() } })
        f.controller.login("fixture", charArrayOf('x')); started.await()
        f.engine.change(NetworkProfile.FollowSystem); f.sessions.networkChanged(); release.complete(Unit); f.done()
        assertEquals(0, f.api.logins); assertTrue(f.store.data.isEmpty())
    }
    @Test fun logoutRejectsLateLoginAndDoesNotTouchAnotherSource() = runBlocking {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var secret: SessionCandidate? = null
        val f = Fixture(this, object : Api() {
            override suspend fun signIn(email: String, password: CharArray): SessionCandidate {
                withContext(NonCancellable) { started.complete(Unit); release.await() }
                return candidate().also { secret = it }
            }
        })
        f.sessions.validateAndCommit(f.sessions.begin("jmcomic"), candidate()) { ValidationResult.Verified("其他来源") }
        f.controller.login("fixture", charArrayOf('x')); started.await()
        f.controller.logout(); f.done(); release.complete(Unit)
        withTimeout(5000) { while (secret?.value?.all { it == 0.toByte() } != true) delay(10) }
        assertEquals(AccountStatus.ANONYMOUS, f.status()); assertEquals(setOf("session.jmcomic"), f.store.data.keys)
    }
    @Test fun parallelRestoresUseOneProbeAndOneValidation() = runBlocking {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val f = Fixture(this, object : Api() {
            override suspend fun profile(candidate: SessionCandidate): String { profiles++; started.complete(Unit); release.await(); return "有效账号" }
        })
        f.seed(); f.controller.restore(); started.await()
        repeat(5) { f.controller.restore() }
        assertEquals(1, f.api.probes); assertEquals(1, f.api.profiles)
        release.complete(Unit); f.done(); assertEquals(AccountStatus.AUTHENTICATED, f.status())
    }
    @Test fun networkFailurePreservesStoredTokenButDoesNotClaimItIsAuthenticated() = runBlocking {
        val f = Fixture(this, object : Api() { override suspend fun probe() { throw java.io.IOException("offline") } })
        f.seed(); f.controller.restore(); f.done()
        assertEquals(AccountStatus.NEEDS_VALIDATION, f.status()); assertNotNull(f.store.data["session.picacg"])
    }
    @Test fun unavailableSavedNetworkLeavesRestoredAccountAvailableForManualValidation() = runBlocking {
        val f = Fixture(this, ready = { error("network profile cannot be loaded") })
        f.seed(); f.controller.restore(); f.done()
        assertEquals(AccountStatus.NEEDS_VALIDATION, f.status())
        assertNotNull(f.store.data["session.picacg"]); assertEquals(0, f.api.probes)
    }
    companion object { private fun candidate() = SessionCandidate(CredentialKind.USER_TOKEN, "fixture-token".toByteArray()) }
}
