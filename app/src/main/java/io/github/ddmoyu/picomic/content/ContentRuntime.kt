package io.github.ddmoyu.picomic.content

import android.content.Context
import io.github.ddmoyu.picomic.network.NetworkRepository
import io.github.ddmoyu.picomic.source.jm.JmRoutes
import io.github.ddmoyu.picomic.source.ht.HtRoutes
import kotlinx.coroutines.*

/** Reading and foreground downloads share route state and the EH quota gate. */
class ContentRuntime private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = context.getSharedPreferences("picomic_ui", 0)
    private fun pref(key: String, default: String = "") = store.getString("pref.$key", default).orEmpty()
    private val network = NetworkRepository.get(context)
    val jmRoutes = JmRoutes(context, network, scope) { pref("jm.auto", "true").toBoolean() }
    val htRoutes = HtRoutes(context, network, scope)
    val repository = ContentRepository(network,
        jmImageLine = { pref("jm.image", "分流 1").takeLast(1).toIntOrNull() ?: 1 },
        jmClient = { jmRoutes.client() }, htClient = { htRoutes.client() },
        nhSessionId = { when (pref("nh.auth")) { "API Key" -> "nhentai_key"; "网页会话" -> "nhentai_web"; else -> null } },
        ehSettings = { EhSettings(pref("eh.site") == "exhentai.org", pref("eh.original").toBoolean(), pref("eh.subtitle").toBoolean(), pref("eh.warning").toBoolean()) })
    companion object {
        @Volatile private var instance: ContentRuntime? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: ContentRuntime(context.applicationContext).also { instance = it }
        }
    }
}
