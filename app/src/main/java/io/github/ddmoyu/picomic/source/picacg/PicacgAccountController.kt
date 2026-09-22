package io.github.ddmoyu.picomic.source.picacg

import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.network.NetworkEngine
import kotlinx.coroutines.CoroutineScope

typealias PicacgLoginState = PasswordLoginState
typealias PicacgAuthApi = PasswordAuthApi

class PicacgAccountController(sessions: SessionCoordinator, engine: NetworkEngine, scope: CoroutineScope,
    awaitNetwork: suspend () -> Unit, recovery: PasswordSessionRecovery? = null,
    apiFactory: () -> PicacgAuthApi = { PicacgClient(engine) }) :
    PasswordAccountController(SOURCE, "哔咔", sessions, engine, scope, awaitNetwork, recovery, { apiFactory() }) {
    companion object { const val SOURCE = "picacg" }
}
