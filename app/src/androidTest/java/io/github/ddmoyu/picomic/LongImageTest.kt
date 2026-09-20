package io.github.ddmoyu.picomic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.CoilZoomState
import com.github.panpf.zoomimage.rememberCoilZoomState
import io.github.ddmoyu.picomic.reader.ReaderImages
import io.github.ddmoyu.picomic.reader.ReaderPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class LongImageTest {
    @get:Rule val ui = createComposeRule()
    @Test fun longLocalImageUsesRegionTiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "reader-long-test.png")
        val bitmap = Bitmap.createBitmap(640, 16000, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.DKGRAY; textSize = 48f }
        for (i in 0..30) canvas.drawText("PiComic original test ${i + 1}", 20f, i * 500f + 80f, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        lateinit var state: CoilZoomState
        lateinit var scope: CoroutineScope
        try {
            ui.setContent {
                state = rememberCoilZoomState()
                scope = rememberCoroutineScope()
                CoilZoomAsyncImage(
                    model = ReaderImages.request(context, ReaderPage("long-test", file, 640, 16000), 1080),
                    imageLoader = ReaderImages.loader(context), contentDescription = "原创长图测试", modifier = Modifier.fillMaxSize(), zoomState = state
                )
            }
            ui.waitUntil(15000) { state.subsampling.ready }
            ui.runOnIdle {
                assertTrue(state.subsampling.tileGridSizeMap.isNotEmpty())
                scope.launch { state.zoomable.scale(state.zoomable.mediumScale) }
            }
            ui.waitUntil(5000) { state.zoomable.userTransform.scaleX > 1.02f }
            assertNotNull(state.subsampling.imageInfo)
        } finally { file.delete() }
    }
}
