package io.github.ddmoyu.picomic.reader

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.rememberCoilZoomState
import com.github.panpf.zoomimage.compose.zoom.ZoomableState
import com.github.panpf.zoomimage.zoom.GestureType
import androidx.compose.ui.geometry.Offset
import io.github.ddmoyu.picomic.ui.readerPages

data class ReaderPage(val id: String, val model: Any, val width: Int, val height: Int)

object ReaderImages {
    @Volatile private var loader: ImageLoader? = null
    fun loader(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext).build().also { loader = it }
    }

    fun demoPages(source: String, comic: Int, chapter: Int, count: Int) = (1..count).map {
        ReaderPage("$source/$comic/$chapter/$it", readerPages[(it - 1) % readerPages.size], 640, 930)
    }

    fun request(context: Context, page: ReaderPage, width: Int): ImageRequest {
        val targetWidth = width.coerceIn(1, 1600)
        val targetHeight = (targetWidth.toLong() * page.height / page.width).coerceIn(1, 4096).toInt()
        return ImageRequest.Builder(context).data(page.model)
            .size(targetWidth, targetHeight).precision(Precision.INEXACT)
            .memoryCacheKey("reader/${page.id}/$targetWidth").build()
    }
}

@Composable fun ReaderImage(
    page: ReaderPage, description: String, width: Int, modifier: Modifier = Modifier,
    zoomable: Boolean = false, doubleTap: Boolean = true, active: Boolean = true,
    onZoomState: (ZoomableState?) -> Unit = {}, onTap: () -> Unit = {},
    onLongPress: ((ZoomableState, Offset) -> Unit)? = null
) {
    val context = LocalContext.current
    val loader = remember { ReaderImages.loader(context) }
    var retry by remember(page.id) { mutableIntStateOf(0) }
    var failed by remember(page.id, retry) { mutableStateOf(false) }
    val request = remember(page, width, retry) { ReaderImages.request(context, page, width) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (zoomable) {
            val state = rememberCoilZoomState()
            SideEffect { state.zoomable.setDisabledGestureTypes(if (doubleTap) 0 else GestureType.DOUBLE_TAP_SCALE or GestureType.ONE_FINGER_SCALE) }
            DisposableEffect(state, active) {
                if (active) onZoomState(state.zoomable)
                onDispose { if (active) onZoomState(null) }
            }
            LaunchedEffect(active) { if (!active) state.zoomable.reset() }
            CoilZoomAsyncImage(
                model = request, imageLoader = loader, contentDescription = description,
                modifier = Modifier.fillMaxSize(), zoomState = state, scrollBar = null,
                onTap = { onTap() }, onLongPress = onLongPress?.let { callback -> { point -> callback(state.zoomable, point) } },
                onError = { failed = true }, onSuccess = { failed = false }
            )
        } else AsyncImage(
            model = request, imageLoader = loader, contentDescription = description,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillWidth,
            onError = { failed = true }, onSuccess = { failed = false }
        )
        if (failed) TextButton(onClick = { failed = false; retry++ }) { Text("图片加载失败，点击重试") }
    }
}
