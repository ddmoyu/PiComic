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
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.ddmoyu.picomic.network.NetworkRepository
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Precision
import com.github.panpf.zoomimage.rememberCoilZoomState
import com.github.panpf.zoomimage.compose.zoom.ZoomableState
import com.github.panpf.zoomimage.zoom.GestureType
import androidx.compose.ui.geometry.Offset
import io.github.ddmoyu.picomic.ui.readerPages
import io.github.ddmoyu.picomic.content.ContentFailure
import io.github.ddmoyu.picomic.content.ContentFailureKind
import kotlinx.coroutines.channels.Channel
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

data class ReaderPage(val id: String, val model: Any, val width: Int, val height: Int, val dimensionsKnown: Boolean = true)

object ReaderImages {
    @Volatile private var loader: ImageLoader? = null
    fun loader(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext)
            .diskCache { ManagedImageCache.get(context.applicationContext) }
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) add(coil3.gif.AnimatedImageDecoder.Factory())
                else add(coil3.gif.GifDecoder.Factory())
                add(SourceImageFetcher.Factory())
                add(SourceImageFetcher.ImageKeyer())
                add(io.github.ddmoyu.picomic.source.jm.JmImageFetcher.Factory(context.applicationContext))
                add(io.github.ddmoyu.picomic.source.jm.JmImageFetcher.ImageKeyer())
                add(OkHttpNetworkFetcherFactory(callFactory = { NetworkRepository.get(context) }))
            }
            .build().also { loader = it }
    }

    fun demoPages(source: String, comic: Int, chapter: Int, count: Int) = (1..count).map {
        ReaderPage("$source/$comic/$chapter/$it", readerPages[(it - 1) % readerPages.size], 640, 930)
    }

    fun request(context: Context, page: ReaderPage, width: Int): ImageRequest {
        val targetWidth = width.coerceIn(1, 1600)
        val targetHeight = (targetWidth.toLong() * page.height / page.width).coerceIn(1, 4096).toInt()
        return ImageRequest.Builder(context).data(page.model)
            .size(targetWidth, targetHeight).precision(Precision.INEXACT)
            .memoryCacheKey("reader/${page.id}/${(page.model as? SourceImage)?.key.orEmpty()}/$targetWidth").build()
    }
}

@Composable fun ReaderImage(
    page: ReaderPage, description: String, width: Int, modifier: Modifier = Modifier,
    zoomable: Boolean = false, doubleTap: Boolean = true, active: Boolean = true,
    onZoomState: (ZoomableState?) -> Unit = {}, onTap: (Offset) -> Unit = {},
    onLongPress: ((ZoomableState, Offset) -> Unit)? = null,
    onDimensions: (Int, Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val loader = remember { ReaderImages.loader(context) }
    var retry by remember(page.id) { mutableIntStateOf(0) }
    var ordinary by remember(page.id) { mutableStateOf(false) }
    val request = remember(page, width, retry, ordinary) {
        val model = page.model as? SourceImage
        val effective = if (ordinary && model != null) page.copy(model = model.copy(page = model.page.copy(resolver = "eh"))) else page
        ReaderImages.request(context, effective, width)
    }
    var failed by remember(request, retry) { mutableStateOf<Throwable?>(null) }
    var loading by remember(request, retry) { mutableStateOf(true) }
    // A memory-cache result can be delivered while an image painter is composing.
    // Apply callbacks from an effect so they never write the same snapshot inside and outside composition.
    val events = remember(request, retry) { Channel<AsyncImagePainter.State>(Channel.CONFLATED) }
    val dimensionsCallback by rememberUpdatedState(onDimensions)
    LaunchedEffect(events) {
        for (result in events) {
            loading = result is AsyncImagePainter.State.Empty || result is AsyncImagePainter.State.Loading
            failed = (result as? AsyncImagePainter.State.Error)?.result?.throwable
            (result as? AsyncImagePainter.State.Success)?.result?.image?.let { image ->
                if (image.width > 0 && image.height > 0) dimensionsCallback(image.width, image.height)
            }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (zoomable) key(retry) {
            val state = rememberCoilZoomState()
            SideEffect { state.zoomable.setDisabledGestureTypes(if (doubleTap) 0 else GestureType.DOUBLE_TAP_SCALE or GestureType.ONE_FINGER_SCALE) }
            DisposableEffect(state, active) {
                if (active) onZoomState(state.zoomable)
                onDispose { if (active) onZoomState(null) }
            }
            LaunchedEffect(active) { if (!active) state.zoomable.reset() }
            ReaderZoomImage(
                request = request, loader = loader, description = description, state = state,
                onTap = onTap, onLongPress = onLongPress?.let { callback -> { point -> callback(state.zoomable, point) } },
                onResult = { events.trySend(it) }
            )
        } else key(request, retry) { AsyncImage(
            model = request, imageLoader = loader, contentDescription = description,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillWidth,
            onLoading = { events.trySend(it) }, onError = { events.trySend(it) }, onSuccess = { events.trySend(it) }
        ) }
        if (loading) CircularProgressIndicator(Modifier.size(32.dp).semantics { contentDescription = "图片正在加载" }, strokeWidth = 3.dp)
        failed?.let { error -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
            Text(if (error is ContentFailure) error.message.orEmpty() else "图片加载失败")
            TextButton(onClick = {
                if ((error as? ContentFailure)?.kind == ContentFailureKind.QUOTA) (page.model as? SourceImage)?.repository?.ehImages?.retry()
                failed = null; retry++
            }) { Text(if ((error as? ContentFailure)?.kind == ContentFailureKind.QUOTA) "已恢复额度，重新加载" else "重试") }
            if ((page.model as? SourceImage)?.page?.resolver == "eh-original" && !ordinary && (error as? ContentFailure)?.kind != ContentFailureKind.QUOTA)
                TextButton(onClick = { ordinary = true; failed = null; retry++ }) { Text("加载普通图片") }
        } }
    }
}
