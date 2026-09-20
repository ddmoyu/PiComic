package io.github.ddmoyu.picomic

import android.graphics.drawable.Animatable
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import coil3.DrawableImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.github.ddmoyu.picomic.download.DownloadStorage
import io.github.ddmoyu.picomic.reader.ReaderImages
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ImageFormatsTest {
    @Test fun syntheticFormatsDecodeAtExpectedDimensionsAndGifKeepsAnimation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation(); val context = instrumentation.targetContext
        for (name in listOf("synthetic.jpg", "synthetic.png", "synthetic.webp", "synthetic.gif", "animated.webp", "synthetic.avif")) {
            val file = File.createTempFile("format-fixture-", "-" + name, context.cacheDir)
            try {
                instrumentation.context.assets.open("formats/$name").use { input -> file.outputStream().use(input::copyTo) }
                val inspected = runCatching { DownloadStorage.inspect(file) }
                val result = ReaderImages.loader(context).execute(ImageRequest.Builder(context).data(file).size(96, 144).build())
                if (name.endsWith(".avif") && Build.VERSION.SDK_INT < 31) {
                    assertTrue("Older Android must report unsupported AVIF", inspected.isFailure)
                    assertFalse(result is SuccessResult)
                } else {
                    assertEquals(name, 96, inspected.getOrThrow().width); assertEquals(name, 144, inspected.getOrThrow().height)
                    assertTrue("$name: $result", result is SuccessResult)
                    val image = (result as SuccessResult).image
                    assertEquals(name, 96, image.width); assertEquals(name, 144, image.height)
                    if (name.endsWith(".gif") || name == "animated.webp" && Build.VERSION.SDK_INT >= 28)
                        assertTrue("Animation decoder was not selected for $name", image is DrawableImage && image.drawable is Animatable)
                }
            } finally { file.delete() }
        }
    }
}
