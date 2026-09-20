package io.github.ddmoyu.picomic.source.ht

import android.content.Context
import io.github.ddmoyu.picomic.network.*
import io.github.ddmoyu.picomic.source.html.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

data class HtRouteState(val selected: String, val trusted: Set<String>, val available: Set<String> = emptySet(), val busy: Boolean = false, val message: String? = null)
class HtRoutes(context: Context, private val network: NetworkRepository, private val scope: CoroutineScope) {
    private val store = context.getSharedPreferences("ht_routes", 0)
    private val saved = store.getStringSet("trusted", emptySet()).orEmpty().filter(::validHost).toSet() + BUILT_IN
    private val mutable = MutableStateFlow(HtRouteState(store.getString("selected", null)?.takeIf { it in saved } ?: "www.wn10.shop", saved))
    val state = mutable.asStateFlow()
    private var task: Job? = null
    fun select(host: String) {
        require(host in state.value.trusted)
        task?.cancel()
        mutable.value = state.value.copy(selected = host, busy = false, message = "已选择域名；原账号会话不会发送到新域名")
        store.edit().putString("selected", host).apply()
    }
    fun client() = HtClient(network.engine, "https://${state.value.selected}/".toHttpUrl(), state.value.trusted.map { "https://$it:443" }.toSet())
    fun refresh() {
        if (task?.isActive == true) return
        mutable.value = state.value.copy(busy = true, message = null)
        task = scope.launch {
            try {
                network.awaitReady()
                val generation = network.engine.status.generation
                val discovered = mutableSetOf<String>()
                for (address in listOf("https://wnacg01.link/", "https://wnacg02.link/")) {
                    try {
                        val url = address.toHttpUrl()
                        val bytes = network.newCall(Request.Builder().url(url).header("User-Agent", BROWSER_AGENT).build()).readBounded { require(it.code == 200) }
                        parseHtml(bytes, url, HtClient.TITLE).select(".content a[href]").forEach { anchor ->
                            runCatching { secureUrl(url, anchor.attr("href"), HtClient.TITLE) }.getOrNull()?.takeIf { it.encodedPath == "/" && it.query == null && validHost(it.host) }?.let { discovered += it.host }
                        }
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                    if (discovered.isNotEmpty()) break
                }
                val candidates = (listOf(state.value.selected) + discovered + state.value.trusted).distinct().take(12)
                val available = mutableSetOf<String>()
                for (host in candidates) {
                    try { HtClient(network.engine, "https://$host/".toHttpUrl(), setOf("https://$host:443")).probe(); available += host }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { }
                    network.engine.withGeneration(generation) { }
                }
                val trusted = state.value.trusted + available
                store.edit().putStringSet("trusted", trusted).apply()
                mutable.value = state.value.copy(trusted = trusted, available = available, message = if (available.isEmpty()) "域名验证失败，保留旧设置" else "已匿名验证 ${available.size} 个域名，可手动选择")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = state.value.copy(message = "域名验证失败，保留旧设置") }
            finally { mutable.value = state.value.copy(busy = false) }
        }
    }
    companion object {
        private val BUILT_IN = setOf("www.wnacg.com", "www.wn10.shop", "www.wn10.cfd")
        internal fun validHost(host: String) = host in BUILT_IN || host.matches(Regex("www\\.wn[0-9]{1,3}\\.(shop|cfd)"))
    }
}
