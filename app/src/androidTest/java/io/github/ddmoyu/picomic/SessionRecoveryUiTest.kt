package io.github.ddmoyu.picomic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.source.picacg.*
import io.github.ddmoyu.picomic.ui.AppViewModel
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

class SessionRecoveryUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(ui.activity)[AppViewModel::class.java]
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var recovery: PasswordSessionRecovery
    private val logins = AtomicInteger()
    private val started = CompletableDeferred<Unit>()
    private val finish = CompletableDeferred<Unit>()
    private fun prepare(reject: Boolean = false) {
        ui.runOnIdle { vm.picacgAccount.cancel(); vm.jmAccount.cancel(); vm.htAccount.cancel(); vm.preference("debugDemo", "false"); vm.source(Source.PICACG) }
        recovery = PasswordSessionRecovery("picacg", vm.network.sessions, vm.network.engine, scope, vm.network::awaitReady) {
            object : PasswordAuthApi {
                override suspend fun probe() = Unit
                override suspend fun profile(candidate: SessionCandidate) = "测试账号"
                override suspend fun validate(candidate: SessionCandidate) = ValidationResult.Verified("测试账号", "recovery-test")
                override suspend fun signIn(email: String, password: CharArray): SessionCandidate {
                    logins.incrementAndGet(); started.complete(Unit); finish.await()
                    if (reject) throw PicacgFailure(PicacgFailureKind.CREDENTIALS)
                    return SessionCandidate(CredentialKind.USER_TOKEN, "new-session".toByteArray())
                }
            }
        }
        ui.runOnIdle {
            vm.content = ContentRepository(vm.network, passwordSessions = mapOf("picacg" to recovery)) { candidate ->
                val old = candidate.value.toString(Charsets.UTF_8) == "old-session"
                object : ComicSource {
                    override val source = Source.PICACG
                    override suspend fun search(query: ContentQuery): ContentPage<ComicSummary> {
                        if (old) throw PicacgFailure(PicacgFailureKind.EXPIRED)
                        return ContentPage(listOf(ComicSummary(ComicKey(Source.PICACG, "recovery-ui"), "自动恢复测试作品", "测试作者",
                            "android.resource://${ui.activity.packageName}/${R.drawable.cover_0}")))
                    }
                    override suspend fun categories() = emptyList<String>()
                    override suspend fun details(id: String): ComicDetails = error("unused")
                    override suspend fun pages(comicId: String, chapter: Chapter) = emptyList<PageRef>()
                }
            }
        }
        runBlocking {
            vm.network.awaitReady(); vm.network.sessions.logout("picacg")
            RememberedLogin("fixture", "password".toCharArray()).use {
                vm.network.sessions.validateAndCommit(vm.network.sessions.begin("picacg"), SessionCandidate(CredentialKind.USER_TOKEN, "old-session".toByteArray()), PasswordRetention.Remember(it)) {
                    ValidationResult.Verified("测试账号", "recovery-test")
                }
            }
        }
        ui.waitUntil(5000) { started.isCompleted }
    }
    @After fun cleanup() {
        if (::recovery.isInitialized) recovery.cancel()
        scope.cancel()
        runBlocking { vm.network.sessions.logout("picacg") }
    }
    @Test fun expiredListRecoversAndDisplaysContentWithoutManualLogin() {
        prepare(); finish.complete(Unit)
        ui.waitUntil(10000) { ui.onAllNodesWithText("自动恢复测试作品").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("管理账号").assertDoesNotExist()
        assertEquals(1, logins.get())
    }
    @Test fun wrongSavedPasswordShowsAccountActionAfterOneAttempt() {
        prepare(reject = true); finish.complete(Unit)
        ui.waitUntil(10000) { ui.onAllNodesWithText("管理账号").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("自动恢复测试作品").assertDoesNotExist()
        ui.waitForIdle(); assertEquals(1, logins.get())
    }
}
