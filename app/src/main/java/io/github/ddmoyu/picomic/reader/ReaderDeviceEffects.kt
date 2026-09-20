package io.github.ddmoyu.picomic.reader

import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.ddmoyu.picomic.MainActivity

@Composable fun ReaderDeviceEffects(
    keepAwake: Boolean, volumeKeys: Boolean,
    onTurn: (Boolean) -> Unit, onForeground: (Boolean) -> Unit, save: () -> Unit
) {
    val activity = LocalView.current.context as? MainActivity
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestSave by rememberUpdatedState(save)
    val latestTurn by rememberUpdatedState(onTurn)
    val latestForeground by rememberUpdatedState(onForeground)
    val latestKeepAwake by rememberUpdatedState(keepAwake)
    val latestVolumeKeys by rememberUpdatedState(volumeKeys)
    val handler = remember(activity) { { next: Boolean -> latestTurn(next) } }
    fun applyForeground(active: Boolean) {
        activity?.readerVolumeAction = if (active && latestVolumeKeys) handler else null
        if (active && latestKeepAwake) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    // onPause must release synchronously: Compose can stop producing frames in the background.
    DisposableEffect(activity, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { applyForeground(true); latestForeground(true) }
            if (event == Lifecycle.Event.ON_PAUSE) { applyForeground(false); latestForeground(false); latestSave() }
        }
        lifecycle.addObserver(observer)
        val active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        applyForeground(active); latestForeground(active)
        onDispose {
            lifecycle.removeObserver(observer)
            if (activity?.readerVolumeAction === handler) activity?.readerVolumeAction = null
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            latestSave()
        }
    }
    SideEffect { applyForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
}
