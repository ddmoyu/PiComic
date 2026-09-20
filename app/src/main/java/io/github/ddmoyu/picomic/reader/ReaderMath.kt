package io.github.ddmoyu.picomic.reader

object ReaderMath {
    fun prefetchPages(current: Int, count: Int, total: Int): List<Int> {
        if (current !in 1..total) return emptyList()
        return ((current + 1)..minOf(total, current + count.coerceIn(1, 10))).toList() +
            if (current > 1) listOf(current - 1) else emptyList()
    }

    fun ratio(offset: Int, height: Int): Float = if (height > 0) (offset.toFloat() / height).coerceIn(0f, .99999f) else 0f
    fun offset(ratio: Float, height: Int): Int = ((ratio.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, .99999f) * height.coerceAtLeast(0)).toInt()
    fun intervalMillis(value: String) = (value.substringBefore(' ').toLongOrNull() ?: 5L).coerceIn(2, 60) * 1000L
}
