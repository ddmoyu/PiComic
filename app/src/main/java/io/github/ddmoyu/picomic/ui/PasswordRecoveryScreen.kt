package io.github.ddmoyu.picomic.ui

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.data.Source
import kotlinx.coroutines.*

@Composable internal fun PasswordRecoveryScreen(source: Source, vm: AppViewModel, close: () -> Unit) {
    val page = remember(source) { passwordRecoveryPage(source, vm.htRoutes.state.value.selected) }
    if (page == null) { Text("此平台无需账号"); return }
    RecoveryWebPage(page, vm, close)
}

@Composable internal fun RecoveryWebPage(page: RecoveryPage, vm: AppViewModel, close: () -> Unit,
    pageResponse: (WebResourceRequest) -> WebResourceResponse? = { null }) {
    val context = LocalContext.current
    var view by remember(page) { mutableStateOf<PasswordRecoveryView?>(null) }
    var error by remember(page) { mutableStateOf<String?>(null) }
    var progress by remember(page) { mutableIntStateOf(0) }
    var canGoBack by remember(page) { mutableStateOf(false) }
    var retry by remember(page) { mutableIntStateOf(0) }
    LaunchedEffect(page, retry) {
        error = null; progress = 0; canGoBack = false
        try {
            vm.network.awaitReady()
            WebLoginRuntime.withPage(vm.network.engine, { error = it }) { current ->
                val controlled = PasswordRecoveryView(context, page, current, { error = it }, { progress = it }, { canGoBack = view?.hasHistory() == true }, pageResponse)
                try { view = controlled; controlled.open(); awaitCancellation() }
                finally { view = null; canGoBack = false; controlled.dispose() }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "无法打开找回密码页面，请检查网络或代理设置后重试" }
    }
    BackHandler { if (view?.back() != true) close() }
    Column(Modifier.fillMaxSize().testTag("password-recovery")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { view?.back() }, enabled = canGoBack) { Text("网页后退") }
            TextButton(onClick = { retry++ }) { Text("重新加载") }
            TextButton(onClick = close) { Text("返回登录") }
        }
        Text(page.url.host, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        page.hint?.let { Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
        error?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (error == null && progress < 100) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        view?.let { controlled -> key(controlled) {
            AndroidView(factory = { controlled }, modifier = Modifier.weight(1f).fillMaxWidth().testTag("recovery-webview"))
        } }
    }
}
