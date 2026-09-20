package io.github.ddmoyu.picomic.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

@Composable fun AppRefreshRateEffect(enabled: Boolean) {
    val view = LocalView.current
    val activity = view.context.activity()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(activity, view, lifecycle, enabled) {
        val window = activity?.window
        val previous = window?.attributes?.preferredDisplayModeId ?: 0
        fun apply(active: Boolean) {
            val display = view.display
            val current = display?.mode
            val target = if (active && enabled && current != null) display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate }?.modeId ?: previous else previous
            window?.attributes = window.attributes.apply { preferredDisplayModeId = target }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) apply(true)
            if (event == Lifecycle.Event.ON_PAUSE) apply(false)
        }
        lifecycle.addObserver(observer); apply(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycle.removeObserver(observer); apply(false) }
    }
}

@Composable fun ReaderDisplayEffect(brightness: String, orientation: String) {
    val activity = LocalView.current.context.activity()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(activity, lifecycle, brightness, orientation) {
        val window = activity?.window
        val oldBrightness = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        // The app's non-reader baseline follows the system, including after an Activity recreation.
        val oldOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val value = brightness.toIntOrNull()?.coerceIn(1, 100)?.div(100f) ?: oldBrightness
        val requested = when (orientation) { "竖屏" -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT; "横屏" -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE; else -> oldOrientation }
        fun apply(active: Boolean) {
            window?.attributes = window.attributes.apply { screenBrightness = if (active) value else oldBrightness }
            if (activity?.requestedOrientation != if (active) requested else oldOrientation)
                activity?.requestedOrientation = if (active) requested else oldOrientation
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) apply(true)
            if (event == Lifecycle.Event.ON_PAUSE) window?.attributes = window.attributes.apply { screenBrightness = oldBrightness }
        }
        lifecycle.addObserver(observer); apply(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycle.removeObserver(observer); if (activity?.isChangingConfigurations != true) apply(false) }
    }
}
