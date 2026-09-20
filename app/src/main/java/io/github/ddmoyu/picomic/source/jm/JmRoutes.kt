package io.github.ddmoyu.picomic.source.jm

import android.content.Context
import io.github.ddmoyu.picomic.network.NetworkRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import io.github.ddmoyu.picomic.network.origin

data class JmRouteState(val selected: String, val trusted: Set<String>, val available: Set<String> = emptySet(), val busy: Boolean = false, val message: String? = null)
/** Built-ins and publisher candidates must use the same profile; cookies remain bound to one origin. */
class JmRoutes(context: Context, private val network: NetworkRepository, private val scope: CoroutineScope, private val automatic: () -> Boolean) {
    private val preferences = context.getSharedPreferences("jm_routes", 0)
    private val saved = preferences.getStringSet("trusted", emptySet()).orEmpty().filter(JmDomainFeed::validHost)
        .sortedByDescending { it == preferences.getString("selected", null) }.take(24).toSet() + JmProtocol.apiCandidates.map { it.host }
    private val initial = preferences.getString("selected", null)?.takeIf { it in saved } ?: JmProtocol.api.host
    private val mutable = MutableStateFlow(JmRouteState(initial, saved))
    val state = mutable.asStateFlow()
    private var refresh: Job? = null
    fun select(host: String) {
        require(host in state.value.trusted)
        refresh?.cancel()
        mutable.value = mutable.value.copy(selected = host, busy = false, message = "已选择线路；已有账号只在原线路有效")
        preferences.edit().putString("selected", host).apply()
    }
    fun refresh() {
        if (refresh?.isActive == true) return
        mutable.value = mutable.value.copy(busy = true, message = null)
        refresh = scope.launch {
            try {
                network.awaitReady()
                val generation = network.engine.status.generation
                val discovered = try { JmDomainFeed.fetch(network.engine) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
                network.engine.withGeneration(generation) { }
                val available = mutableSetOf<String>()
                // Anonymous, bounded probes. Never replay login or send an account to a candidate.
                for (host in (discovered + state.value.selected + state.value.trusted).distinct().take(16)) {
                    val base = "https://$host/".toHttpUrl()
                    try { withTimeout(15_000) { JmClient(network.engine, base, setOf(base.origin())).probe() }; available += base.host }
                    catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { }
                    network.engine.withGeneration(generation) { }
                }
                val trusted = (listOf(state.value.selected) + available + state.value.trusted).distinct().take(24).toSet()
                preferences.edit().putStringSet("trusted", trusted).apply()
                mutable.value = mutable.value.copy(trusted = trusted, available = available, message = if (available.isEmpty()) "线路验证失败，保留原选择" else "已验证 ${available.size} 条 API 线路" + if (discovered.isEmpty()) "；发布列表暂时不可用，使用已知线路" else "；已检查发布列表")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(message = "线路验证失败，保留原选择") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    private fun trustedClient(base: HttpUrl) = JmClient(network.engine, base, state.value.trusted.map { "https://$it:443" }.toSet())
    fun client() = trustedClient(selected())
    suspend fun loginClient(): JmClient {
        network.awaitReady()
        val first = selected()
        if (!automatic()) return trustedClient(first)
        val generation = network.engine.status.generation
        for (base in listOf(first) + state.value.trusted.map { "https://$it/".toHttpUrl() }.filterNot { it == first }) {
            val client = trustedClient(base)
            try { client.probe() }
            catch (e: CancellationException) { throw e } catch (_: Exception) { continue }
            network.engine.withGeneration(generation) { }
            if (selected() != first) throw CancellationException("线路已更改")
            if (base != first) select(base.host)
            return client
        }
        throw java.io.IOException("没有可用的 JM API 线路")
    }
    private fun selected(): HttpUrl = "https://${mutable.value.selected}/".toHttpUrl()
}
