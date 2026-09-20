package io.github.ddmoyu.picomic.content

import io.github.ddmoyu.picomic.data.Source
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ContentListState(
    val items: List<ComicSummary> = emptyList(), val loading: Boolean = false, val error: String? = null,
    val needsLogin: Boolean = false, val nextPage: Int? = null, val loaded: Boolean = false, val nextCursor: String? = null
)
/** A list owns its request generation; replacement/cancellation cannot append stale results. */
class ContentListController(
    private val scope: CoroutineScope,
    private val fetch: suspend (Source, ContentQuery) -> ContentPage<ComicSummary>
) {
    private val mutable = MutableStateFlow(ContentListState())
    val state = mutable.asStateFlow()
    private var sequence = 0L
    private var job: Job? = null
    private var source: Source? = null
    private var query = ContentQuery()
    private var context = ""
    fun load(source: Source, query: ContentQuery, force: Boolean = false, context: String = "") {
        if (!force && this.source == source && this.query == query && this.context == context && (state.value.loaded || state.value.loading)) return
        this.context = context
        this.source = source; this.query = query.copy(page = 1)
        request(append = false)
    }
    fun more() { if (!state.value.loading && state.value.nextPage != null) request(append = true) }
    fun retry() { request(append = state.value.items.isNotEmpty()) }
    fun cancel() { sequence++; job?.cancel(); job = null; mutable.value = mutable.value.copy(loading = false) }
    private fun request(append: Boolean) {
        val activeSource = source ?: return
        val page = if (append) state.value.nextPage ?: return else 1
        cancel(); val id = sequence
        mutable.value = if (append) state.value.copy(loading = true, error = null) else ContentListState(loading = true)
        job = scope.launch {
            try {
                val reply = fetch(activeSource, query.copy(page = page, cursor = if (append) state.value.nextCursor else null))
                ensureActive()
                if (id != sequence) return@launch
                val next = reply.nextPage
                if (next != null && (next <= page || next > 10000)) throw ContentFailure(ContentFailureKind.PARSE, "平台分页格式异常")
                if (reply.items.any { it.key.source != activeSource }) throw ContentFailure(ContentFailureKind.PARSE, "来源数据不一致")
                mutable.value = ContentListState(((if (append) state.value.items else emptyList()) + reply.items).distinctBy { it.key },
                    nextPage = next, loaded = true, nextCursor = reply.nextCursor)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (id == sequence) mutable.value = mutable.value.copy(loading = false,
                error = contentError(error), needsLogin = error is ContentFailure && error.kind in setOf(ContentFailureKind.LOGIN, ContentFailureKind.EXPIRED)) }
            finally { if (id == sequence) mutable.value = mutable.value.copy(loading = false) }
        }
    }
}
