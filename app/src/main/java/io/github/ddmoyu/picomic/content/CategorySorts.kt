package io.github.ddmoyu.picomic.content

import io.github.ddmoyu.picomic.data.Source

data class CategorySort(val value: String, val label: String)

/** Native category orders, not local sorting of the already loaded page. */
object CategorySorts {
    fun options(source: Source): List<CategorySort> = when (source) {
        Source.PICACG -> listOf("dd" to "最新", "da" to "最早", "ld" to "最多喜欢", "vd" to "最多浏览")
        Source.JMCOMIC -> listOf("mr" to "最新", "mv" to "总排行", "mv_m" to "月排行", "mv_w" to "周排行",
            "mv_t" to "日排行", "mp" to "最多图片", "tf" to "最多喜欢")
        Source.NHENTAI -> listOf("date" to "最新", "popular" to "总排行", "popular-month" to "月排行",
            "popular-week" to "周排行", "popular-today" to "日排行")
        Source.HITOMI -> listOf("date_added" to "最新收录", "published" to "最新发布", "today" to "日排行",
            "week" to "周排行", "month" to "月排行", "year" to "年排行", "random" to "随机")
        // These category pages expose chronological browsing; their global rankings are separate pages.
        Source.EHENTAI, Source.HTCOMIC -> listOf("date" to "最新")
    }.map { CategorySort(it.first, it.second) }

    fun resolve(source: Source, value: String) = options(source).firstOrNull { it.value == value } ?: options(source).first()
}
