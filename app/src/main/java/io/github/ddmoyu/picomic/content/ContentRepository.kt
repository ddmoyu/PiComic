package io.github.ddmoyu.picomic.content

import io.github.ddmoyu.picomic.auth.AccountStatus
import io.github.ddmoyu.picomic.auth.SessionCandidate
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.network.NetworkRepository
import io.github.ddmoyu.picomic.network.networkError
import io.github.ddmoyu.picomic.source.picacg.*
import io.github.ddmoyu.picomic.source.jm.*
import io.github.ddmoyu.picomic.source.nh.*
import io.github.ddmoyu.picomic.source.ht.*
import io.github.ddmoyu.picomic.source.eh.*
import io.github.ddmoyu.picomic.source.hitomi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class ContentRepository(private val network: NetworkRepository,
    private val jmImageLine: () -> Int = { 1 },
    private val jmClient: suspend () -> JmClient = { JmClient(network.engine) },
    private val nhSessionId: () -> String? = { null },
    private val htClient: () -> HtClient = { HtClient(network.engine) },
    private val ehSettings: () -> EhSettings = { EhSettings() },
    private val picacgFactory: (SessionCandidate) -> ComicSource = { PicacgSource(PicacgClient(network.engine), it) }) {
    suspend fun categories(source: Source): List<String> {
        SourceCategories.fixed(source)?.let { return it }
        // JM publishes its complete directory anonymously. An expired saved account must not hide it.
        if (source == Source.JMCOMIC) return withContext(Dispatchers.IO) {
            network.awaitReady()
            JmSource(jmClient()).categories()
        }
        return run(source) { adapter, _ -> adapter.categories() }
    }
    suspend fun <T> run(source: Source, action: suspend (ComicSource, String) -> T): T = withContext(Dispatchers.IO) {
        network.awaitReady()
        val sessions = network.sessions
        val sourceId = when (source) { Source.PICACG -> "picacg"; Source.JMCOMIC -> "jmcomic"; Source.HTCOMIC -> "htcomic"; Source.EHENTAI -> "ehentai"; Source.HITOMI -> "hitomi"; else -> nhSessionId() ?: "nhentai.anonymous" }
        if (sessions.state.value[sourceId]?.status == AccountStatus.AUTHENTICATING)
            sessions.state.first { it[sourceId]?.status != AccountStatus.AUTHENTICATING }
        if ((source in setOf(Source.JMCOMIC, Source.HTCOMIC, Source.EHENTAI, Source.HITOMI) && sessions.state.value[sourceId]?.status in setOf(null, AccountStatus.ANONYMOUS)) || sourceId == "nhentai.anonymous") {
            val generation = network.engine.status.generation
            val revision = sessions.changes.value[sourceId]
            val adapter = when(source) { Source.JMCOMIC -> JmSource(jmClient(), jmImageLine()); Source.HTCOMIC -> HtSource(htClient()); Source.EHENTAI -> eh(null); Source.HITOMI -> HitomiSource(hitomi); else -> NhSource(NhClient(network.engine)) }
            val result = action(adapter, "anonymous")
            network.engine.withGeneration(generation) { }
            if (revision != sessions.changes.value[sourceId]) throw ContentFailure(ContentFailureKind.LOGIN, "账号已更改，请重新加载")
            return@withContext result
        }
        if (sessions.state.value[sourceId]?.status != AccountStatus.AUTHENTICATED)
            throw ContentFailure(ContentFailureKind.LOGIN, "请先登录并验证${if (source == Source.PICACG) "哔咔" else source.shortTitle}账号")
        val lease = sessions.lease(sourceId)
        try {
            sessions.useLease(lease) {
                val adapter = when (source) {
                    Source.PICACG -> picacgFactory(lease.candidate)
                    Source.JMCOMIC -> JmSource(jmClient().also { it.install(lease.candidate) }, jmImageLine())
                    Source.HTCOMIC -> HtSource(htClient().also { it.install(lease.candidate) })
                    Source.EHENTAI -> eh(lease.candidate)
                    else -> NhSource(NhClient(network.engine, lease.candidate))
                }
                action(adapter, lease.partition)
            }
        } catch (error: ContentFailure) {
            if (error.kind == ContentFailureKind.EXPIRED) sessions.expire(lease)
            throw error
        } catch (error: PicacgFailure) {
            if (error.kind == PicacgFailureKind.EXPIRED) {
                sessions.expire(lease)
                throw ContentFailure(ContentFailureKind.EXPIRED, "会话已失效，请重新登录")
            }
            throw error
        } catch (error: CancellationException) {
            currentCoroutineContext().ensureActive()
            if (!sessions.isCurrent(lease)) throw ContentFailure(ContentFailureKind.LOGIN, "账号或网络已更改，请重新加载")
            throw error
        } finally { lease.close() }
    }
    val ehImages = EhImageGate()
    private val hitomi = HitomiClient(network.engine)
    private fun eh(candidate: SessionCandidate?): EhSource {
        val settings = ehSettings()
        return EhSource(EhClient(network.engine, candidate, settings.ignoreWarning), settings.ex, settings.original, settings.subtitle, ehImages)
    }
}
data class EhSettings(val ex: Boolean = false, val original: Boolean = false, val subtitle: Boolean = false, val ignoreWarning: Boolean = false)
fun contentError(error: Exception): String = when (error) {
    is ContentFailure, is PicacgFailure -> error.message ?: "请求失败"
    else -> networkError(error)
}
