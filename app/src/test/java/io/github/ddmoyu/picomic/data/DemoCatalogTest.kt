package io.github.ddmoyu.picomic.data

import org.junit.Assert.*
import org.junit.Test

class DemoCatalogTest {
    @Test fun keywordFilteringNormalizesCaseAndFullWidthCharacters() {
        assertEquals(DemoCatalog.filter("青空","全部",emptyList(),emptySet()).single().id,0)
        assertTrue(DemoCatalog.filter("雨后","全部",listOf("青空"),emptySet()).isEmpty())
        assertEquals("abc",DemoCatalog.normalize(" ＡＢＣ "))
    }
    @Test fun languageAndCategoryFiltersComposeWithoutChangingTheCatalog() {
        val original=DemoCatalog.comics.toList()
        assertEquals(listOf(2),DemoCatalog.filter("","冒险",emptyList(),setOf("Japanese")).map { it.id })
        assertEquals(original,DemoCatalog.comics)
        assertEquals(9,DemoCatalog.filter("","全部",emptyList(),emptySet()).size)
    }
    @Test fun booksAreIsolatedBySource() {
        assertNotEquals(DemoCatalog.key(Source.PICACG,0),DemoCatalog.key(Source.JMCOMIC,0))
        assertTrue(Source.entries.all { it.categories.isNotEmpty() })
    }
}
