package io.github.ddmoyu.picomic

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.ddmoyu.picomic.ui.PiComicApp

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
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) io.github.ddmoyu.picomic.data.EventLog.get(this).record(io.github.ddmoyu.picomic.data.EventCode.APP_START)
        enableEdgeToEdge()
        if (intent.getBooleanExtra("openDownloads", false)) downloadsRequest.value++
        setContent { PiComicApp() }
    }
}
