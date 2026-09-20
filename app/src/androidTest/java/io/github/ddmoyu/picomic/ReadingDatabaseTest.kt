package io.github.ddmoyu.picomic

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ReadingDatabaseTest {
    @Test fun positionsSurviveReopenAndStayIsolatedBySource() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-progress-${UUID.randomUUID()}.db"
        fun repository() = ReadingProgressRepository(Room.databaseBuilder(context, ReadingDatabase::class.java, name).build())
        var store = repository()
        try {
            for (page in 1..24) store.save(ReadingPosition(Source.PICACG, 0, 3, page, .375f))
            store.save(ReadingPosition(Source.HITOMI, 0, 1, 2, .25f))
            store.flush()
            store.close()
            store = repository()
            store.flush()
            val loaded = store.state.value
            assertTrue(loaded.loaded)
            assertNull(loaded.error)
            assertEquals(2, loaded.positions.size)
            val pica = loaded.positions.single { it.source == Source.PICACG }
            assertEquals(24, pica.page)
            assertEquals(3, pica.chapter)
            assertEquals(.375f, pica.offsetRatio, .00001f)
            assertEquals(2, loaded.positions.single { it.source == Source.HITOMI }.page)
        } finally { store.close(); context.deleteDatabase(name) }
    }
}
