/* Uses the rendering and subsampling integration described by ZoomImage's Apache-2.0 adapter.
 * Upstream attribution: Copyright (C) 2024 panpf <panpfpanpf@outlook.com>; Copyright 2023 Coil Contributors.
 * PiComic modification: serialize configuration and load-state changes after Compose snapshot apply.
 * License: app/src/main/assets/licenses/Apache-2.0.txt
 */
package io.github.ddmoyu.picomic.reader

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.github.panpf.zoomimage.CoilZoomState
import com.github.panpf.zoomimage.coil.CoilTileImageCache
import com.github.panpf.zoomimage.compose.internal.BaseZoomImage
import com.github.panpf.zoomimage.compose.subsampling.subsampling
import com.github.panpf.zoomimage.compose.zoom.zoom
import com.github.panpf.zoomimage.compose.zoom.zooming
import com.github.panpf.zoomimage.subsampling.SubsamplingImageGenerateResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.collectLatest

/** The Coil/ZoomImage 1.5 adapter mutates zoom state during composition and from immediate callbacks.
 * Keep those transitions after snapshot apply, including synchronous memory-cache hits.
 * Uses ZoomImage's public gesture, tile cache, generator and rendering APIs; no image downscaling fork.
 */
@Composable internal fun ReaderZoomImage(request: ImageRequest, loader: ImageLoader, description: String,
    state: CoilZoomState, onTap: (Offset) -> Unit, onLongPress: ((Offset) -> Unit)?,
    onResult: (AsyncImagePainter.State) -> Unit) {
    val context = LocalContext.current
    val direction = LocalLayoutDirection.current
    val tileCache = remember(loader) { CoilTileImageCache(loader) }
    val updates = remember(request, loader, state) { Channel<AsyncImagePainter.State>(Channel.CONFLATED) }
    val latestResult by rememberUpdatedState(onResult)
    // Coil 3.4 restarts an existing painter synchronously from its input setter during composition.
    // A width/model change must create a new painter; its first load starts after it is remembered.
    val painter = key(request, loader) {
        rememberAsyncImagePainter(request, loader, contentScale = ContentScale.Fit, onState = { updates.trySend(it) })
    }
    SideEffect {
        state.zoomable.setContentScale(ContentScale.Fit)
        state.zoomable.setAlignment(Alignment.Center)
        state.zoomable.setLayoutDirection(direction)
        state.subsampling.setTileImageCache(tileCache)
    }
    LaunchedEffect(updates) {
        updates.consumeAsFlow().collectLatest { result ->
            val size = result.painter?.intrinsicSize
            val valid = size != null && size.width.isFinite() && size.height.isFinite() && size.width > 0 && size.height > 0
            state.zoomable.setContentSize(if (valid) IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)) else IntSize.Zero)
            val generated = if (result is AsyncImagePainter.State.Success) state.subsamplingImageGenerators.firstNotNullOfOrNull {
                it.generateImage(context, loader, result.result, result.painter)
            } else null
            state.setSubsamplingImage((generated as? SubsamplingImageGenerateResult.Success)?.subsamplingImage)
            latestResult(result)
        }
    }
    Box(Modifier.fillMaxSize()) {
        BaseZoomImage(painter, description, Modifier.matchParentSize().zoom(state.zoomable, userSetupContentSize = true, onTap = onTap, onLongPress = onLongPress),
            clipToBounds = false, keepContentNoneStartOnDraw = true)
        Box(Modifier.matchParentSize().zooming(state.zoomable, firstScaleByContentSize = true).subsampling(state.zoomable, state.subsampling))
    }
}
