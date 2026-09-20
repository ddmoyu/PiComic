package io.github.ddmoyu.picomic.auth

import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.network.NetworkEngine
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CheckInRunnerTest {
    private class Secrets : SecretStore {
        val data = mutableMapOf<String, ByteArray>()
        override fun read(key: String) = data[key]?.copyOf()
        override fun write(key: String, value: ByteArray) { data[key] = value.copyOf() }
        override fun remove(key: String) { data.remove(key) }
    }
    private class Journal : CheckInJournal {
        val data = mutableMapOf<String, Long>()
        override fun number(key: String) = data[key] ?: 0L
        override fun save(values: Map<String, Long>) { data.putAll(values) }
    }
    private suspend fun login(s: SessionCoordinator, id: String = "one") {
        s.validateAndCommit(s.begin("picacg"), SessionCandidate(CredentialKind.USER_TOKEN, "fixture".toByteArray())) { ValidationResult.Verified("账号", id) }
    }
    @Test fun successDeduplicatesByAccountAndDay() = runBlocking {
        val sessions = SessionCoordinator(Secrets(), NetworkEngine()); val journal = Journal()
        var now = 1_700_000_000_000L; var calls = 0
        val runner = CheckInRunner(sessions, journal, { now }) { _, _ -> calls++; CheckInOutcome.DONE }
        runner.run(Source.PICACG, true); assertEquals(0, calls)
        login(sessions); runner.run(Source.PICACG, true); runner.run(Source.PICACG, true); assertEquals(1, calls)
        login(sessions, "two"); runner.run(Source.PICACG, true); assertEquals(2, calls)
        now += 86_400_000; runner.run(Source.PICACG, false); assertEquals(3, calls)
        assertEquals(3, journal.data.keys.count { it.startsWith("done.") })
    }
    @Test fun unconfirmedAttemptsHaveCooldownAndAutomaticBudgetButNeverMarkSuccess() = runBlocking {
        val sessions = SessionCoordinator(Secrets(), NetworkEngine()); login(sessions); val journal = Journal()
        var now = 1_700_000_000_000L; var calls = 0
        val runner = CheckInRunner(sessions, journal, { now }) { _, _ -> calls++; throw ContentFailure(ContentFailureKind.NETWORK, "网络中断") }
        repeat(3) { runCatching { runner.run(Source.PICACG, false) }; runner.run(Source.PICACG, true); now += 60_001 }
        assertEquals(3, calls); runner.run(Source.PICACG, false); assertEquals(3, calls)
        runCatching { runner.run(Source.PICACG, true) }; assertEquals(4, calls)
        assertTrue(journal.data.keys.none { it.startsWith("done.") })
    }
    @Test fun logoutDuringSlowResponseCannotWriteSuccess() = runBlocking {
        val sessions = SessionCoordinator(Secrets(), NetworkEngine()); login(sessions); val journal = Journal()
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val runner = CheckInRunner(sessions, journal) { _, _ -> withContext(NonCancellable) { started.complete(Unit); finish.await() }; CheckInOutcome.DONE }
        val request = async { runCatching { runner.run(Source.PICACG, true) } }
        started.await(); sessions.logout("picacg"); finish.complete(Unit)
        assertTrue(withTimeout(3000) { request.await() }.exceptionOrNull() is CancellationException)
        assertTrue(journal.data.keys.none { it.startsWith("done.") })
    }
}
