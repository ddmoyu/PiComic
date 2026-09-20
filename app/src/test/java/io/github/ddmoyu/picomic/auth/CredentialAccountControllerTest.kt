package io.github.ddmoyu.picomic.auth

import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.network.NetworkEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class CredentialAccountControllerTest {
    private class Store : SecretStore {
        val values = mutableMapOf<String, ByteArray>()
        override fun read(key: String) = values[key]?.copyOf()
        override fun write(key: String, value: ByteArray) { values[key] = value.copyOf() }
        override fun remove(key: String) { values.remove(key) }
    }
    private fun token() = SessionCandidate(CredentialKind.USER_TOKEN, "fixture-secret".toByteArray())
    @Test fun explicitLoginAcknowledgesOnceButSessionRestorationStaysQuiet() = runBlocking {
        val store = Store(); val sessions = SessionCoordinator(store, NetworkEngine())
        val controller = CredentialAccountController("nhentai_web", sessions, this, {}) { ValidationResult.Verified("测试账号") }
        controller.submit(token()); controller.state.first { !it.busy }
        assertTrue(controller.state.value.loginSucceeded)
        controller.dismissLoginSuccess(); assertFalse(controller.state.value.loginSucceeded)
        controller.restore(); controller.state.first { !it.busy }
        assertFalse(controller.state.value.loginSucceeded)
        controller.submit(token()); controller.state.first { !it.busy }
        assertTrue(controller.state.value.loginSucceeded)
        controller.logout(); controller.state.first { !it.busy }
        assertFalse(controller.state.value.loginSucceeded)
    }
    @Test fun rejectedCandidateDoesNotReplaceSavedKeyAndIsZeroed() = runBlocking {
        val store = Store(); val sessions = SessionCoordinator(store, NetworkEngine())
        sessions.validateAndCommit(sessions.begin("nhentai_key"), token()) { ValidationResult.Verified("旧账号", "42") }
        val original = store.values.getValue("session.nhentai_key").copyOf()
        val controller = CredentialAccountController("nhentai_key", sessions, this, {}) { throw ContentFailure(ContentFailureKind.EXPIRED, "无效凭据") }
        val candidate = token(); controller.submit(candidate); controller.state.first { !it.busy }
        assertArrayEquals(original, store.values["session.nhentai_key"])
        assertTrue(candidate.value.all { it == 0.toByte() })
        assertEquals(AccountStatus.AUTHENTICATED, sessions.state.value["nhentai_key"]?.status)
        assertFalse(controller.state.value.loginSucceeded)
    }
    @Test fun clearingWebSessionRetainsIndependentKey() = runBlocking {
        val store = Store(); val sessions = SessionCoordinator(store, NetworkEngine())
        val key = CredentialAccountController("nhentai_key", sessions, this, {}) { ValidationResult.Verified("Key账号", "42") }
        val web = CredentialAccountController("nhentai_web", sessions, this, {}) { ValidationResult.Verified("网页账号", "43") }
        key.submit(token()); key.state.first { !it.busy }; web.submit(token()); web.state.first { !it.busy }
        web.logout(); web.state.first { !it.busy }
        assertEquals(setOf("session.nhentai_key"), store.values.keys)
    }
    @Test fun restoredCredentialIsValidatedAndExpiredCredentialRemoved() = runBlocking {
        val store = Store(); val sessions = SessionCoordinator(store, NetworkEngine())
        sessions.validateAndCommit(sessions.begin("nhentai_web"), token()) { ValidationResult.Verified("账号", "42") }
        val controller = CredentialAccountController("nhentai_web", sessions, this, {}) { throw ContentFailure(ContentFailureKind.EXPIRED, "已失效") }
        controller.restore(); controller.state.first { !it.busy }
        assertEquals(AccountStatus.EXPIRED, sessions.state.value["nhentai_web"]?.status)
        assertTrue(store.values.isEmpty())
    }
    @Test fun cancelWhileWaitingForNetworkClearsCandidateAndNeverValidates() = runBlocking {
        val store = Store(); val sessions = SessionCoordinator(store, NetworkEngine())
        var calls = 0
        val controller = CredentialAccountController("nhentai_web", sessions, this, { awaitCancellation() }) { calls++; ValidationResult.Verified("不应验证") }
        val candidate = token(); controller.submit(candidate); yield(); controller.cancel(); yield()
        assertEquals(0, calls); assertTrue(store.values.isEmpty()); assertTrue(candidate.value.all { it == 0.toByte() })
    }
}
