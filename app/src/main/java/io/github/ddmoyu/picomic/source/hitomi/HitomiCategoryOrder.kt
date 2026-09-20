package io.github.ddmoyu.picomic.source.hitomi

import io.github.ddmoyu.picomic.content.CategorySorts
import io.github.ddmoyu.picomic.data.Source

/** Paths used by the site's nozomi_address_from_state, without its optional compression prefix. */
internal object HitomiCategoryOrder {
    fun path(type: String?, language: String, sort: String): List<String> {
        val value = CategorySorts.resolve(Source.HITOMI, sort).value
        val order = when (value) {
            "published" -> listOf("date", "published")
            "today", "week", "month", "year" -> listOf("popular", value)
            else -> emptyList()
        }
        return if (type != null) listOf("type") + order + "$type-$language.nozomi"
        else if (order.isEmpty()) listOf("index-$language.nozomi")
        else listOf(order[0], "${order[1]}-$language.nozomi")
    }
}
