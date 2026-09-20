package io.github.ddmoyu.picomic.reader

object ReaderMath {
    fun prefetchPages(current: Int, count: Int, total: Int, lastVisible: Int = current): List<Int> {
        if (current !in 1..total) return emptyList()
        val end = lastVisible.coerceIn(current, total)
        return ((end + 1)..minOf(total, end + count.coerceIn(1, 10))).toList() +
            if (current > 1) listOf(current - 1) else emptyList()
    }

    fun retryPages(visible: Set<Int>, count: Int, total: Int): List<Int> {
        val valid = visible.filter { it in 1..total }.sorted()
        if (valid.isEmpty()) return emptyList()
        return valid + ((valid.last() + 1)..minOf(total, valid.last() + count.coerceIn(1, 10))).toList()
    }

    fun visibleCenter(itemTop: Int, itemHeight: Int, viewportTop: Int, viewportBottom: Int): Int {
        val top = maxOf(itemTop, viewportTop)
        val bottom = minOf(itemTop + itemHeight, viewportBottom)
        return ((top + bottom) / 2 - itemTop).coerceIn(0, itemHeight.coerceAtLeast(0))
    }

    fun ratio(offset: Int, height: Int): Float = if (height > 0) (offset.toFloat() / height).coerceIn(0f, .99999f) else 0f
    fun offset(ratio: Float, height: Int): Int = ((ratio.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, .99999f) * height.coerceAtLeast(0)).toInt()
    fun intervalMillis(value: String) = (value.substringBefore(' ').toLongOrNull() ?: 5L).coerceIn(2, 60) * 1000L
}
