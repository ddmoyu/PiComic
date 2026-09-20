package io.github.ddmoyu.picomic

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.ddmoyu.picomic.ui.PiComicApp
import io.github.ddmoyu.picomic.data.ThemeMode

class MainActivity : ComponentActivity() {
    val downloadsRequest = kotlinx.coroutines.flow.MutableStateFlow(0)
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (intent.getBooleanExtra("openDownloads", false)) downloadsRequest.value++
    }
    var readerVolumeAction: ((Boolean) -> Unit)? = null
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val action = readerVolumeAction
        if (action != null && keyCode in setOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP)) {
            if (event.repeatCount == 0) action(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (readerVolumeAction != null && keyCode in setOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP)) return true
        return super.onKeyUp(keyCode, event)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        val preferences = getSharedPreferences("picomic_ui", 0)
        val mode = ThemeMode.fromPreferences(listOf("themeMode", "dark").mapNotNull { key ->
            preferences.getString("pref.$key", null)?.let { key to it }
        }.toMap())
        setTheme(when (mode) {
            ThemeMode.SYSTEM -> R.style.Theme_PiComic
            ThemeMode.LIGHT -> R.style.Theme_PiComic_Light
            ThemeMode.DARK -> R.style.Theme_PiComic_Dark
        })
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) io.github.ddmoyu.picomic.data.EventLog.get(this).record(io.github.ddmoyu.picomic.data.EventCode.APP_START)
        enableEdgeToEdge()
        if (intent.getBooleanExtra("openDownloads", false)) downloadsRequest.value++
        setContent { PiComicApp() }
    }
}
