package io.github.ddmoyu.picomic.backup

import io.github.ddmoyu.picomic.content.*
import org.json.JSONArray

data class TransferPreference(val key: String, val value: String, val updatedAt: Long, val deleted: Boolean = false)
data class BackupData(
    val comics: List<SavedComic> = emptyList(), val favorites: List<FavoriteEntity> = emptyList(),
    val progress: List<ContentProgressEntity> = emptyList(), val workPreferences: List<WorkPreferenceEntity> = emptyList(),
    val preferences: List<TransferPreference> = emptyList()
)
data class BackupDocument(val deviceId: String, val revision: Long, val exportedAt: Long, val data: BackupData)

/** Explicit portable settings contract. Endpoints, account modes, paths and credentials cannot enter it. */
object PortablePreferences {
    private val flags = setOf("volume", "keepAwake", "doubleTap", "longPress", "dark", "pureBlack", "highRefresh", "unknownLanguage", "downloadWifi", "checkOnStart", "pica.avatar", "pica.checkin", "jm.checkin", "eh.original", "eh.subtitle", "eh.warning")
    private val options = mapOf(
        "themeMode" to io.github.ddmoyu.picomic.data.ThemeMode.entries.map { it.label }.toSet(),
        "readingMode" to setOf("纵向连续", "从左向右", "从右向左"),
        "readerBackground" to setOf("深灰", "纯黑", "米白"), "readerOrientation" to setOf("跟随系统", "竖屏", "横屏"),
        "readerBrightness" to (setOf("跟随系统") + (0..100).map { it.toString() }),
        "autoInterval" to setOf("2 秒", "3 秒", "5 秒", "10 秒", "15 秒", "30 秒", "60 秒"),
        "preload" to (1..10).map { "$it 张" }.toSet(), "parallel" to (1..6).map { it.toString() }.toSet(),
        "cache" to setOf("250 MB", "500 MB", "1 GB", "2 GB"),
        "pica.search" to setOf("新到旧", "旧到新", "最多喜欢"), "pica.favorite" to setOf("新到旧", "旧到新"),
        "jm.favorite" to setOf("最新收藏", "最早收藏", "最近更新")
    )
    val workKeys = setOf("readingMode", "readerBackground", "readerOrientation", "readerBrightness")
    fun allowed(key: String) = key in flags || key in options || key in setOf("filter.keywords", "filter.languages", "source.order", "source.enabled")
    fun valid(key: String, value: String): Boolean = when {
        key in flags -> value in setOf("true", "false")
        key in options -> value in options.getValue(key)
        key in setOf("filter.keywords", "filter.languages", "source.order", "source.enabled") -> runCatching {
            val array = JSONArray(value)
            val list = (0 until array.length()).map { array.get(it) as? String ?: error("invalid") }
            list.distinct().size == list.size && when (key) {
                "filter.keywords" -> list.size <= 100 && list.all { it.isNotBlank() && it.length <= 80 && it.none(Char::isISOControl) }
                "filter.languages" -> list.all { it in setOf("Chinese", "English", "Japanese") }
                else -> list.size <= 64 && list.all { it.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,63}")) }
            }
        }.getOrDefault(false)
        else -> false
    }
}
