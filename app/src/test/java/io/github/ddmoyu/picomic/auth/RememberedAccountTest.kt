package io.github.ddmoyu.picomic.auth

import io.github.ddmoyu.picomic.network.NetworkEngine
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RememberedAccountTest {
    private class Store : SecretStore {
        val data = mutableMapOf<String, ByteArray>()
        override fun read(key: String) = data[key]?.copyOf()
        override fun write(key: String, value: ByteArray) { data[key] = value.copyOf() }
        override fun remove(key: String) { data.remove(key)?.fill(0) }
    }
    private fun candidate() = SessionCandidate(CredentialKind.COOKIE, "fixture-cookie".toByteArray())
    private suspend fun seed(sessions: SessionCoordinator, user: String = "fixture-user", id: String = "fixture-id") {
        RememberedLogin(user, "fixture-password".toCharArray()).use { login ->
            sessions.validateAndCommit(sessions.begin("jmcomic"), candidate(), PasswordRetention.Remember(login)) { ValidationResult.Verified("夹具账号", id) }
        }
    }
    @Test fun failedReplacementPreservesPasswordAndExpirationRemovesOnlySession() = runBlocking {
        val store = Store(); val sessions = SessionCoordinator(store, NetworkEngine()); seed(sessions)
        val before = store.read("session.jmcomic")!!
        RememberedLogin("wrong-fixture", "wrong-password".toCharArray()).use { login ->
            sessions.validateAndCommit(sessions.begin("jmcomic"), candidate(), PasswordRetention.Remember(login)) { ValidationResult.Rejected("拒绝") }
        }
        assertArrayEquals(before, store.read("session.jmcomic"))
        sessions.lease("jmcomic").use { sessions.expire(it) }
        assertNull(sessions.storedCandidate("jmcomic"))
        sessions.rememberedLogin("jmcomic")!!.use {
            assertEquals("fixture-user", it.username); assertArrayEquals("fixture-password".toCharArray(), it.password)
        }
        assertEquals(AccountStatus.EXPIRED, sessions.state.value["jmcomic"]!!.status)
        sessions.logout("jmcomic")
        assertNull(sessions.rememberedLogin("jmcomic")); assertTrue(store.data.isEmpty()); assertTrue(sessions.rememberedAccounts.value.isEmpty())
    }
    @Test fun verificationPreservesPasswordButAccountChangeCannotInheritIt() = runBlocking {
        val sessions = SessionCoordinator(Store(), NetworkEngine()); seed(sessions)
        val restored = sessions.storedCandidate("jmcomic")!!
        sessions.validateAndCommit(sessions.begin("jmcomic"), restored) { ValidationResult.Verified("修改后的昵称", "fixture-id") }
        sessions.rememberedLogin("jmcomic")!!.use { assertEquals("fixture-user", it.username) }
        sessions.validateAndCommit(sessions.begin("jmcomic"), candidate()) { ValidationResult.Verified("另一个账号", "another-id") }
        assertNull(sessions.rememberedLogin("jmcomic"))
    }
    @Test fun forgettingPasswordKeepsAnAuthenticatedSessionAndItsLease() = runBlocking {
        val sessions = SessionCoordinator(Store(), NetworkEngine()); seed(sessions)
        sessions.lease("jmcomic").use { lease ->
            sessions.forgetPassword("jmcomic")
            assertTrue(sessions.isCurrent(lease)); assertEquals(AccountStatus.AUTHENTICATED, sessions.state.value["jmcomic"]!!.status)
        }
        assertNull(sessions.rememberedLogin("jmcomic")); assertTrue(sessions.rememberedAccounts.value.isEmpty())
    }
    @Test fun importInvalidatesOldLeaseAndRequiresValidationAndRejectsStalePreview() = runBlocking {
        val source = SessionCoordinator(Store(), NetworkEngine()); seed(source, "import-user")
        val destination = SessionCoordinator(Store(), NetworkEngine()); seed(destination, "local-user")
        source.exportAccounts().single().use { transfer ->
            val fingerprint = destination.accountFingerprint("jmcomic")
            destination.lease("jmcomic").use { old ->
                destination.importAccount(transfer, fingerprint)
                assertFalse(destination.isCurrent(old))
            }
            assertEquals(AccountStatus.NEEDS_VALIDATION, destination.state.value["jmcomic"]!!.status)
            assertTrue(runCatching { destination.lease("jmcomic") }.isFailure)
            assertTrue(runCatching { destination.importAccount(transfer, fingerprint) }.isFailure)
            destination.rememberedLogin("jmcomic")!!.use { assertEquals("import-user", it.username) }
        }
    }
    @Test fun passwordOnlyAccountCanMoveToAnotherInstallationWithoutClaimingAuthentication() = runBlocking {
        val source = SessionCoordinator(Store(), NetworkEngine()); seed(source)
        source.lease("jmcomic").use { source.expire(it) }
        val destination = SessionCoordinator(Store(), NetworkEngine())
        source.exportAccounts().single().use { destination.importAccount(it, "absent") }
        assertEquals(AccountStatus.EXPIRED, destination.state.value["jmcomic"]!!.status)
        assertNull(destination.storedCandidate("jmcomic"))
        destination.rememberedLogin("jmcomic")!!.use { assertEquals("fixture-user", it.username) }
    }
    @Test fun logoutPreventsLateCredentialPersistenceEvenIfValidationIgnoresCancellation() = runBlocking {
        val store = Store(); val sessions = SessionCoordinator(store, NetworkEngine())
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        RememberedLogin("fixture-user", "fixture-password".toCharArray()).use { login ->
            val attempt = sessions.begin("jmcomic")
            val job = launch { sessions.validateAndCommit(attempt, candidate(), PasswordRetention.Remember(login)) {
                withContext(NonCancellable) { started.complete(Unit); release.await() }
                ValidationResult.Verified("夹具账号")
            } }
            started.await(); sessions.logout("jmcomic"); release.complete(Unit); job.join()
        }
        assertTrue(store.data.isEmpty()); assertTrue(sessions.rememberedAccounts.value.isEmpty())
    }
    @Test fun oldSessionFormatsRemainReadableAndMalformedRecordsAreRejected() {
        for (version in 1..2) {
            val bytes = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use {
                it.writeInt(version); it.writeUTF("COOKIE"); it.writeUTF("旧账号")
                if (version == 2) it.writeUTF("old-id")
                it.writeInt(7); it.write("fixture".toByteArray())
            } }.toByteArray()
            StoredAccountCodec.decode(bytes).use { account ->
                assertEquals("旧账号", account.displayName); assertNull(account.login)
                assertEquals(if (version == 2) "old-id" else null, account.accountId)
                StoredAccountCodec.decode(StoredAccountCodec.encode(account)).use { assertArrayEquals(account.candidate!!.value, it.candidate!!.value) }
            }
            assertTrue(runCatching { StoredAccountCodec.decode(bytes + 0) }.isFailure)
            assertTrue(runCatching { StoredAccountCodec.decode(bytes.copyOf(bytes.size - 1)) }.isFailure)
        }
        StoredAccount("夹具", null, candidate(), null).use { account ->
            for (slot in listOf("unknown", "picacg", "nhentai_key", "../escape")) {
                assertTrue(runCatching { AccountTransfer(slot, StoredAccountCodec.encode(account)) }.isFailure)
            }
        }
    }
}
