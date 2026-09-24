package io.github.ddmoyu.picomic.source.picacg

import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.network.NetworkProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class PicacgRegistrationTest {
    @Test fun generatedInformationRoundTripsAndCopiesEveryField() {
        PicacgRegistration.generate(LocalDate.of(2026, 9, 24)).use { record ->
            assertEquals("2006-09-24", record.birthday)
            assertEquals("bot", record.gender)
            assertEquals(24, record.password.size)
            assertFalse(String(record.password).contains(record.username))
            val encoded = record.encode(RegistrationPhase.REGISTERED)
            PicacgRegistration.decode(encoded).use { restored ->
                assertEquals(RegistrationPhase.REGISTERED, restored.phase)
                assertEquals(record.displayText(), restored.displayText())
                val text = restored.displayText()
                listOf("=== 账号信息 ===", "昵称: ${record.nickname}", "用户名: ${record.username}",
                    "密码: ${String(record.password)}", "生日: 2006-09-24", "性别: 机器人", "*** 请妥善保存此信息 ***").forEach {
                    assertTrue(text.contains(it))
                }
                repeat(3) { index ->
                    assertTrue(text.contains("安全问题${index + 1}: ${record.questions[index]}"))
                    assertTrue(text.contains("安全答案${index + 1}: ${record.answers[index]}"))
                }
                assertFalse(restored.displayText(false).contains(String(record.password)))
                assertFalse(restored.toString().contains(record.username))
            }
            assertTrue(runCatching { PicacgRegistration.decode(encoded + byteArrayOf(1)) }.isFailure)
            encoded.fill(0)
        }
        PicacgRegistration.generate(LocalDate.of(2020, 2, 29)).use { assertEquals("2000-02-29", it.birthday) }
        PicacgRegistration.generate(LocalDate.of(2120, 2, 29)).use { assertEquals("2100-02-28", it.birthday) }
    }

    private class Store : SecretStore {
        val data = mutableMapOf<String, ByteArray>()
        var failWrite = false
        override fun read(key: String) = data[key]?.copyOf()
        override fun write(key: String, value: ByteArray) { if (failWrite) throw IOException("fixture disk full"); data[key] = value.copyOf() }
        override fun remove(key: String) { data.remove(key) }
    }
    private open class Api : PicacgRegistrationApi {
        var registrations = 0; var logins = 0; var probes = 0
        val usernames = mutableListOf<String>()
        override suspend fun probe() { probes++ }
        override suspend fun register(details: PicacgRegistration) { registrations++; usernames.add(details.username) }
        override suspend fun signIn(email: String, password: CharArray): SessionCandidate {
            logins++; usernames.add(email); return SessionCandidate(CredentialKind.USER_TOKEN, "fixture-token".toByteArray())
        }
        override suspend fun profile(candidate: SessionCandidate) = "注册测试账号"
    }
    private class Fixture(val scope: CoroutineScope, val api: Api = Api(), val store: Store = Store()) {
        val engine = NetworkEngine(); val sessions = SessionCoordinator(store, engine)
        val controller = PicacgAccountController(sessions, engine, scope, {}) { api }
        suspend fun done() { withTimeout(5000) { controller.state.first { !it.busy } }; yield() }
        fun record() = PicacgRegistration.decode(store.data.getValue("registration.picacg"))
    }

    @Test fun zeroInputRegistersPersistsAndRestartsWithAutomaticSessionRecovery() = runBlocking {
        val f = Fixture(this)
        f.controller.registerOneClick(); f.done()
        assertEquals(1, f.api.registrations); assertEquals(1, f.api.logins)
        assertEquals(AccountStatus.AUTHENTICATED, f.sessions.state.value["picacg"]?.status)
        assertTrue(f.controller.registrationResult.value)
        f.record().use { record ->
            assertEquals(RegistrationPhase.REGISTERED, record.phase)
            f.sessions.rememberedLogin("picacg")!!.use { assertEquals(record.username, it.username); assertArrayEquals(record.password, it.password) }
        }
        val restarted = Fixture(this, f.api, f.store)
        restarted.controller.restore(); restarted.done()
        assertEquals(AccountStatus.AUTHENTICATED, restarted.sessions.state.value["picacg"]?.status)
        assertEquals(1, f.api.logins) // Valid token restoration does not resubmit a password.
        restarted.controller.forgetPassword(); restarted.done()
        assertNull(restarted.store.data["registration.picacg"])
        assertNull(restarted.sessions.rememberedLogin("picacg"))
    }

    @Test fun registeredButThrottledLoginRetainsPasswordForAutomaticRecovery() = runBlocking {
        var throttled = true
        val api = object : Api() {
            override suspend fun signIn(email: String, password: CharArray): SessionCandidate {
                if (throttled) throw PicacgFailure(PicacgFailureKind.RATE_LIMITED)
                return super.signIn(email, password)
            }
        }
        val f = Fixture(this, api); f.controller.registerOneClick(); f.done()
        assertEquals(RegistrationPhase.REGISTERED, f.controller.registration.value.phase)
        assertEquals(AccountStatus.NEEDS_VALIDATION, f.sessions.state.value["picacg"]?.status)
        f.sessions.rememberedLogin("picacg")!!.close()
        assertTrue(f.controller.state.value.message!!.contains("频繁"))
        throttled = false
        val restarted = Fixture(this, api, f.store)
        restarted.controller.restore(); restarted.done()
        assertEquals(AccountStatus.AUTHENTICATED, restarted.sessions.state.value["picacg"]?.status)
        assertEquals(1, api.registrations)
    }

    @Test fun uncertainSubmissionResumesSameAccountWithoutAnotherRegistration() = runBlocking {
        val f = Fixture(this, object : Api() {
            override suspend fun register(details: PicacgRegistration) { super.register(details); throw IOException("fixture response lost") }
        })
        f.controller.registerOneClick(); f.done()
        assertEquals(RegistrationPhase.SUBMITTED, f.controller.registration.value.phase)
        val restarted = Fixture(this, f.api, f.store)
        restarted.controller.registerOneClick(); restarted.done()
        assertEquals(1, f.api.registrations); assertEquals(1, f.api.usernames.distinct().size)
        assertEquals(AccountStatus.AUTHENTICATED, restarted.sessions.state.value["picacg"]?.status)
    }

    @Test fun retryFromSavedUnvalidatedTokenKeepsSameAccountWithoutRegisteringAgain() = runBlocking {
        val f = Fixture(this); f.controller.registerOneClick(); f.done()
        val restarted = Fixture(this, f.api, f.store)
        restarted.controller.registerOneClick(); restarted.done()
        assertEquals(1, f.api.registrations); assertEquals(2, f.api.logins)
        assertEquals(AccountStatus.AUTHENTICATED, restarted.sessions.state.value["picacg"]?.status)
        assertEquals(1, f.api.usernames.distinct().size)
    }

    @Test fun aConfirmedMissingAccountResubmitsOnlyItsOriginalInformation() = runBlocking {
        var first = true
        val f = Fixture(this, object : Api() {
            override suspend fun register(details: PicacgRegistration) {
                super.register(details)
                if (first) throw IOException("fixture offline")
            }
            override suspend fun signIn(email: String, password: CharArray): SessionCandidate {
                if (first) { first = false; throw PicacgFailure(PicacgFailureKind.CREDENTIALS) }
                return super.signIn(email, password)
            }
        })
        f.controller.registerOneClick(); f.done()
        f.controller.registerOneClick(); f.done()
        assertEquals(2, f.api.registrations); assertEquals(1, f.api.usernames.distinct().size)
        assertEquals(AccountStatus.AUTHENTICATED, f.sessions.state.value["picacg"]?.status)
    }

    @Test fun duplicateTapsAndLogoutRejectLateRegistration() = runBlocking {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val ended = CompletableDeferred<Unit>()
        val f = Fixture(this, object : Api() {
            override suspend fun register(details: PicacgRegistration) {
                super.register(details)
                withContext(NonCancellable) { started.complete(Unit); release.await(); ended.complete(Unit) }
            }
        })
        f.controller.registerOneClick(); started.await()
        repeat(3) { f.controller.registerOneClick() }
        assertEquals(1, f.api.registrations)
        f.controller.logout(); f.done(); release.complete(Unit); ended.await(); delay(30)
        assertTrue(f.store.data.isEmpty()); assertEquals(0, f.api.logins)
        assertEquals(AccountStatus.ANONYMOUS, f.sessions.state.value["picacg"]?.status)
    }

    @Test fun networkChangeDuringProbeNeverSendsRegistration() = runBlocking {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val f = Fixture(this, object : Api() { override suspend fun probe() { started.complete(Unit); release.await() } })
        f.controller.registerOneClick(); started.await()
        f.engine.change(NetworkProfile.FollowSystem); f.sessions.networkChanged(); release.complete(Unit); f.done()
        assertEquals(0, f.api.registrations); assertEquals(0, f.api.logins)
        f.record().use { assertEquals(RegistrationPhase.PREPARED, it.phase) }
    }

    @Test fun storageFailureOrExistingAccountCannotSendRegistration() = runBlocking {
        val f = Fixture(this); f.store.failWrite = true
        f.controller.registerOneClick(); f.done()
        assertEquals(0, f.api.probes); assertEquals(0, f.api.registrations)
        f.store.failWrite = false
        f.controller.login("fixture", "fixture-password".toCharArray(), true); f.done()
        val old = f.store.data.getValue("session.picacg").copyOf()
        f.controller.registerOneClick(); f.done()
        assertEquals(0, f.api.registrations); assertArrayEquals(old, f.store.data["session.picacg"])
    }
}
