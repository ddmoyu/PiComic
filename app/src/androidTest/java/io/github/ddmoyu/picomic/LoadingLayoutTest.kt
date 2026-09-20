package io.github.ddmoyu.picomic

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.ui.ContentLoading
import io.github.ddmoyu.picomic.ui.PiComicTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class LoadingLayoutTest {
    @get:Rule val ui = createComposeRule()

    @Test fun screenConstraintsKeepSpinnerSquareCenteredAndAnimating() {
        var wide by mutableStateOf(false)
        ui.mainClock.autoAdvance = false
        ui.setContent {
            PiComicTheme(false, false) {
                Box(Modifier.size(if (wide) 320.dp else 280.dp, if (wide) 120.dp else 480.dp)
                    .background(MaterialTheme.colorScheme.background).testTag("loading-page"), propagateMinConstraints = true) {
                    ContentLoading()
                }
            }
        }
        fun assertGeometry() {
            val spinner = ui.onNodeWithContentDescription("正在加载")
            spinner.assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
            val bounds = spinner.fetchSemanticsNode().boundsInRoot
            val parent = ui.onNodeWithTag("loading-page").fetchSemanticsNode().boundsInRoot
            assertEquals(parent.center.x, bounds.center.x, 1f)
            assertEquals(parent.center.y, bounds.center.y, 1f)
        }
        ui.mainClock.advanceTimeByFrame(); assertGeometry()
        val before = ui.onNodeWithTag("loading-page").captureToImage().asAndroidBitmap()
        ui.mainClock.advanceTimeBy(320); assertGeometry()
        val after = ui.onNodeWithTag("loading-page").captureToImage().asAndroidBitmap()
        assertFalse("加载圈应该随时间旋转", before.sameAs(after))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "screenshots/loading-centered.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { after.compress(Bitmap.CompressFormat.PNG, 100, it) }
        ui.runOnIdle { wide = true }
        ui.mainClock.advanceTimeByFrame(); assertGeometry()
    }

    @Test fun listFooterKeepsNaturalHeightAndCentersSpinner() {
        ui.setContent {
            LazyColumn(Modifier.width(300.dp)) {
                item { ContentLoading(Modifier.fillMaxWidth().padding(vertical = 20.dp).testTag("loading-footer")) }
            }
        }
        ui.onNodeWithContentDescription("正在加载").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
        val bounds = ui.onNodeWithContentDescription("正在加载").fetchSemanticsNode().boundsInRoot
        val parent = ui.onNodeWithTag("loading-footer").fetchSemanticsNode().boundsInRoot
        assertEquals(parent.center.x, bounds.center.x, 1f)
        assertEquals(parent.center.y, bounds.center.y, 1f)
    }
}
