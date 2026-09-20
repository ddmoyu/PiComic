package io.github.ddmoyu.picomic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ddmoyu.picomic.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import kotlinx.coroutines.launch
import io.github.ddmoyu.picomic.network.NetworkRepository
import io.github.ddmoyu.picomic.source.picacg.PicacgAccountController
import io.github.ddmoyu.picomic.content.ContentLibrary
import io.github.ddmoyu.picomic.content.ContentRepository
import io.github.ddmoyu.picomic.content.ContentListController

data class UiState(
    val source: Source = Source.PICACG,
    val preferences: Map<String, String> = emptyMap(),
    val favorites: Set<String> = emptySet(),
    val history: List<ReadingPosition> = emptyList(),
    val historyReady: Boolean = false,
    val historyError: String? = null,
    val downloads: List<DemoDownload> = emptyList(),
    val queries: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val languages: Set<String> = emptySet()
) {
    fun pref(key: String, default: String = "") = preferences[key] ?: default
    fun enabled(key: String, default: Boolean = false) = pref(key, default.toString()).toBoolean()
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    // UI projection; portable preferences are reconciled with Room. Credentials use a separate store.
    private val store = application.getSharedPreferences("picomic_ui", 0)
    private val portablePreferences = io.github.ddmoyu.picomic.backup.PreferenceBridge.get(application)
    val backups = io.github.ddmoyu.picomic.backup.BackupRepository(application)
    val webdav = io.github.ddmoyu.picomic.backup.WebDavController(application, viewModelScope, backups)
    private fun list(key: String): List<String> = runCatching {
        val array = JSONArray(store.getString(key, "[]")); List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())
    private val mutable = MutableStateFlow(UiState(
        source = runCatching { Source.valueOf(store.getString("source", "PICACG")!!) }.getOrDefault(Source.PICACG),
        preferences = store.all.filterKeys { it.startsWith("pref.") }.mapKeys { it.key.removePrefix("pref.") }.mapValues { it.value.toString() },
        favorites = list("favorites").toSet(), queries = list("queries"), keywords = list("keywords"), languages = list("languages").toSet()
    ))
    val state = mutable.asStateFlow()
    val network = NetworkRepository.get(application)
    val updates = io.github.ddmoyu.picomic.update.UpdateRepository.get(application)
    private val runtime = io.github.ddmoyu.picomic.content.ContentRuntime.get(application)
    val jmRoutes = runtime.jmRoutes
    val htRoutes = runtime.htRoutes
    var content = runtime.repository
        internal set
    val library = ContentLibrary.get(application)
    var downloadsRepository = io.github.ddmoyu.picomic.download.DownloadRepository.get(application)
        internal set
    val downloadTab = MutableStateFlow(0)
    private val lists = mutableMapOf<String, ContentListController>()
    fun contentList(key: String) = lists.getOrPut(key) {
        ContentListController(viewModelScope) { source, query -> content.run(source) { adapter, _ -> adapter.search(query) } }
    }
    val picacgAccount = PicacgAccountController(network.sessions, network.engine, viewModelScope, network::awaitReady)
    val jmAccount = io.github.ddmoyu.picomic.auth.PasswordAccountController("jmcomic", "JM", network.sessions, network.engine, viewModelScope, network::awaitReady) { jmRoutes.loginClient() }
    val htAccount = io.github.ddmoyu.picomic.auth.PasswordAccountController("htcomic", "绅士漫画", network.sessions, network.engine, viewModelScope, network::awaitReady) { htRoutes.client() }
    val ehAccount = io.github.ddmoyu.picomic.auth.CredentialAccountController("ehentai", network.sessions, viewModelScope, network::awaitReady) { io.github.ddmoyu.picomic.source.eh.EhClient(network.engine, it).validate() }
    val nhKeyAccount = io.github.ddmoyu.picomic.auth.CredentialAccountController("nhentai_key", network.sessions, viewModelScope, network::awaitReady) { io.github.ddmoyu.picomic.source.nh.NhClient(network.engine, it).validate() }
    val nhWebAccount = io.github.ddmoyu.picomic.auth.CredentialAccountController("nhentai_web", network.sessions, viewModelScope, network::awaitReady) { io.github.ddmoyu.picomic.source.nh.NhClient(network.engine, it).validate() }
    fun nhSessionId(): String? = when (state.value.pref("nh.auth", "匿名")) { "API Key" -> "nhentai_key"; "网页会话" -> "nhentai_web"; else -> null }
    val readingProgress = ReadingProgressRepository.get(application)
    val checkins = io.github.ddmoyu.picomic.auth.CheckInController(application, viewModelScope) { source ->
        state.value.enabled(if (source == Source.PICACG) "pica.checkin" else "jm.checkin")
    }
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key?.startsWith("pref.") == true || key in setOf("keywords", "languages")) mutable.update { it.copy(
            preferences = store.all.filterKeys { name -> name.startsWith("pref.") }.mapKeys { entry -> entry.key.removePrefix("pref.") }.mapValues { entry -> entry.value.toString() },
            keywords = list("keywords"), languages = list("languages").toSet()) }
    }
    init {
        store.registerOnSharedPreferenceChangeListener(preferenceListener)
        picacgAccount.restore()
        jmAccount.restore()
        htAccount.restore()
        ehAccount.restore()
        nhKeyAccount.restore(); nhWebAccount.restore()
        viewModelScope.launch {
            readingProgress.state.collect { progress ->
                mutable.update { it.copy(history = progress.positions, historyReady = progress.loaded, historyError = progress.error) }
            }
        }
    }
    fun source(source: Source) { mutable.update { it.copy(source = source) }; store.edit().putString("source", source.name).apply() }
    fun preference(key: String, value: String) {
        mutable.update { it.copy(preferences = it.preferences + (key to value)) }
        store.edit().putString("pref.$key", value).apply()
        portablePreferences.save(key, value)
        if (key in setOf("pica.checkin", "jm.checkin")) checkins.refresh()
    }
    fun toggle(key: String, default: Boolean = false) = preference(key, (!state.value.enabled(key, default)).toString())
    fun favorite(source: Source, comic: Comic) {
        val key = DemoCatalog.key(source, comic.id)
        mutable.update { it.copy(favorites = if (key in it.favorites) it.favorites - key else it.favorites + key) }
        saveList("favorites", state.value.favorites.toList())
    }
    fun record(source: Source, comicId: Int, chapter: Int, page: Int, offsetRatio: Float = 0f, mode: String = state.value.pref("readingMode", "纵向连续")) =
        readingProgress.save(ReadingPosition(source, comicId, chapter, page, offsetRatio, mode))
    fun query(value: String) {
        val text = value.trim(); if (text.isBlank()) return
        mutable.update { it.copy(queries = (listOf(text) + it.queries.filterNot { q -> q == text }).take(12)) }
        saveList("queries", state.value.queries)
    }
    fun clearQueries() { mutable.update { it.copy(queries = emptyList()) }; saveList("queries", emptyList()) }
    fun keyword(value: String) {
        val text = value.trim().take(80)
        if (text.isBlank() || state.value.keywords.any { DemoCatalog.normalize(it) == DemoCatalog.normalize(text) } || state.value.keywords.size >= 100) return
        mutable.update { it.copy(keywords = it.keywords + text) }; saveList("keywords", state.value.keywords)
    }
    fun removeKeyword(value: String) { mutable.update { it.copy(keywords = it.keywords - value) }; saveList("keywords", state.value.keywords) }
    fun language(value: String) {
        mutable.update { it.copy(languages = if (value in it.languages) it.languages - value else it.languages + value) }
        saveList("languages", state.value.languages.toList())
    }
    fun download(source: Source, comicId: Int, chapter: Int) {
        if (state.value.downloads.any { it.source == source && it.comicId == comicId && it.chapter == chapter }) return
        mutable.update { it.copy(downloads = it.downloads + DemoDownload(source, comicId, chapter)) }
    }
    fun pause(item: DemoDownload) { mutable.update { it.copy(downloads = it.downloads.map { d -> if (d == item) d.copy(paused = !d.paused) else d }) } }
    fun remove(item: DemoDownload) { mutable.update { it.copy(downloads = it.downloads - item) } }
    private fun saveList(key: String, values: List<String>) {
        val json = JSONArray(values).toString(); store.edit().putString(key, json).apply()
        if (key in setOf("keywords", "languages")) portablePreferences.save("filter.$key", json)
    }
    override fun onCleared() { store.unregisterOnSharedPreferenceChangeListener(preferenceListener); super.onCleared() }
}
