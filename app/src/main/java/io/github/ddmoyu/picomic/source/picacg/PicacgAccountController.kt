package io.github.ddmoyu.picomic.source.picacg

import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.network.NetworkEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

typealias PicacgLoginState = PasswordLoginState
typealias PicacgAuthApi = PasswordAuthApi

data class RegistrationState(val ready: Boolean = false, val phase: RegistrationPhase? = null, val revision: Int = 0)

class PicacgAccountController(sessions: SessionCoordinator, engine: NetworkEngine, scope: CoroutineScope,
    awaitNetwork: suspend () -> Unit, recovery: PasswordSessionRecovery? = null,
    apiFactory: () -> PicacgAuthApi = { PicacgClient(engine) }) :
    PasswordAccountController(SOURCE, "哔咔", sessions, engine, scope, awaitNetwork, recovery, { apiFactory() }) {
    private val registrationMutable = MutableStateFlow(RegistrationState())
    val registration = registrationMutable.asStateFlow()
    private val resultMutable = MutableStateFlow(false)
    val registrationResult = resultMutable.asStateFlow()

    init { scope.launch { refreshRegistration() } }

    fun showRegistrationResult() { resultMutable.value = true }
    fun dismissRegistrationResult() { resultMutable.value = false; dismissLoginSuccess() }
    override suspend fun operationFinished() { refreshRegistration() }

    private suspend fun readRegistration(): PicacgRegistration? = sessions.registrationRecord(SOURCE)?.let { bytes ->
        try { PicacgRegistration.decode(bytes) } finally { bytes.fill(0) }
    }
    suspend fun refreshRegistration() {
        try {
            readRegistration()?.use { record ->
                registrationMutable.value = registrationMutable.value.copy(ready = true, phase = record.phase, revision = registrationMutable.value.revision + 1)
            } ?: run { registrationMutable.value = RegistrationState(ready = true, revision = registrationMutable.value.revision + 1) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            registrationMutable.value = RegistrationState(ready = false)
            mutable.value = mutable.value.copy(message = "注册资料无法读取，请检查本机存储")
        }
    }

    /** Called only by the active form or the user-requested details dialog. */
    suspend fun withRegistration(block: (PicacgRegistration) -> Unit) { readRegistration()?.use(block) }

    fun registerOneClick() {
        if (state.value.busy) return
        start {
            awaitNetwork()
            val record = readRegistration()
            val saved = sessions.rememberedLogin(SOURCE)
            try {
                check(sessions.state.value[SOURCE]?.status != AccountStatus.AUTHENTICATED &&
                    (saved == null || record?.username == saved.username)) { "已有本地账号，请先清除后再注册" }
                if (record == null) check(sessions.accountFingerprint(SOURCE) == "absent") { "已有本地账号，请先清除后再注册" }
            } catch (e: Exception) { record?.close(); throw e }
            finally { saved?.close() }
            (record ?: PicacgRegistration.generate()).use { details ->
                val active = sessions.begin(SOURCE).also { attempt = it }
                suspend fun checkpoint(phase: RegistrationPhase) {
                    val bytes = details.encode(phase)
                    try { sessions.saveRegistration(active, bytes) } finally { bytes.fill(0) }
                    refreshRegistration()
                }
                suspend fun rememberAccount() {
                    RememberedLogin(details.username, details.password.copyOf()).use { login ->
                        sessions.rememberRegisteredLogin(active, details.nickname, login)
                    }
                }
                checkpoint(details.phase)
                resultMutable.value = true
                val api = apiFactory() as? PicacgRegistrationApi ?: error("当前客户端不支持注册")
                api.probe()
                var candidate: SessionCandidate? = null
                try {
                    if (details.phase == RegistrationPhase.SUBMITTED) {
                        // The previous request may have reached the server. Recover that same account first.
                        try { candidate = api.signIn(details.username, details.password) }
                        catch (e: PicacgFailure) { if (e.kind != PicacgFailureKind.CREDENTIALS) throw e }
                    }
                    currentCoroutineContext().ensureActive()
                    check(sessions.isCurrent(active)) { "注册操作已取消" }
                    if (details.phase != RegistrationPhase.REGISTERED && candidate == null) {
                        checkpoint(RegistrationPhase.SUBMITTED)
                        api.register(details)
                        checkpoint(RegistrationPhase.REGISTERED)
                    } else if (candidate != null) checkpoint(RegistrationPhase.REGISTERED)
                    rememberAccount()
                    if (candidate == null) candidate = api.signIn(details.username, details.password)
                    val token = requireNotNull(candidate)
                    RememberedLogin(details.username, details.password.copyOf()).use { login ->
                        sessions.validateAndCommit(active, token, PasswordRetention.Remember(login)) { api.validate(it) }
                    }
                    mutable.value = mutable.value.copy(loginSucceeded = true, message = "注册并登录成功，账号密码和密保资料已加密保存")
                } finally { candidate?.value?.fill(0) }
            }
        }
    }
    companion object { const val SOURCE = "picacg" }
}
