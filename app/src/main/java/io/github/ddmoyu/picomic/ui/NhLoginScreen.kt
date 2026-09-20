package io.github.ddmoyu.picomic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.network.origin
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl

@Composable fun NhLoginScreen(ui: UiState, vm: AppViewModel, openNetwork: () -> Unit) {
    var secret by remember { mutableStateOf("") }
    var web by remember { mutableStateOf(false) }
    val mode = ui.pref("nh.auth", "匿名")
    val controller = if (mode == "API Key") vm.nhKeyAccount else vm.nhWebAccount
    val operation by controller.state.collectAsStateWithLifecycle()
    val accounts by controller.accounts.collectAsStateWithLifecycle()
    val network by vm.network.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { secret = ""; web = false; controller.cancel() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.cancel() }
    }
    if (web) { NhWebLogin(vm, { web = false }); return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("nhentai", style = MaterialTheme.typography.headlineSmall)
        Text("选择本次使用的认证方式。网页会话和 API Key 分开加密保存，权限由平台决定。")
        listOf("匿名", "API Key", "网页会话").forEach { value ->
            Row { RadioButton(mode == value, onClick = { secret = ""; controller.cancel(); vm.preference("nh.auth", value) }); Text(value, Modifier.padding(top = 12.dp)) }
        }
        if (mode != "匿名") {
            Text((accounts[controller.sourceId] ?: AccountState()).label())
            if (mode == "网页会话") Button(onClick = { web = true }, enabled = network.ready && !operation.busy, modifier = Modifier.fillMaxWidth()) { Text("打开网页登录") }
            OutlinedTextField(secret, { secret = it.take(16384) }, Modifier.fillMaxWidth(), singleLine = true, enabled = !operation.busy,
                label = { Text(if (mode == "API Key") "API Key" else "手动导入 User token") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Button(onClick = {
                val bytes = secret.trim().toByteArray(); secret = ""
                if (bytes.isNotEmpty()) controller.submit(SessionCandidate(if (mode == "API Key") CredentialKind.API_KEY else CredentialKind.USER_TOKEN, bytes))
            }, enabled = network.ready && !operation.busy && secret.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("验证并保存") }
            if (operation.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = controller::cancel) { Text("取消当前操作") } }
            operation.message?.let { Text(it) }
            OutlinedButton(onClick = controller::restore, enabled = network.ready && !operation.busy) { Text("验证已保存的凭据") }
            TextButton(onClick = controller::logout, enabled = !operation.busy) { Text(if (mode == "API Key") "清除 API Key" else "清除网页会话") }
        } else Text("匿名浏览可用内容。遇到平台认证或权限限制时，可在此选择账号认证。")
        TextButton(onClick = { secret = ""; controller.cancel(); openNetwork() }) { Text("设置代理") }
    }
}

@Composable private fun NhWebLogin(vm: AppViewModel, close: () -> Unit) {
    val context = LocalContext.current
    var view by remember { mutableStateOf<ControlledLoginView?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val controller = vm.nhWebAccount
    val operation by controller.state.collectAsStateWithLifecycle()
    val revisions by vm.network.sessions.changes.collectAsStateWithLifecycle()
    val initialRevision = remember { vm.network.sessions.changes.value[controller.sourceId] }
    LaunchedEffect(revisions) { if (revisions[controller.sourceId] != initialRevision) close() }
    LaunchedEffect(Unit) {
        try {
            vm.network.awaitReady()
            val site = "https://nhentai.net/".toHttpUrl()
            val names = listOf("access_token", "__Secure-access_token", "__Host-access_token")
            val scopes = names.flatMap { name -> listOf(WebCookieScope(name, "/")) + if (name.startsWith("__Host-")) emptyList() else listOf(WebCookieScope(name, "/", ".nhentai.net")) }
            val spec = WebLoginSpec(site.newBuilder().addPathSegment("login").build(), setOf(site.origin(), "https://challenges.cloudflare.com:443"), site, scopes, anyCookie = true)
            WebLoginRuntime.withLogin(context, spec, vm.network.engine,
                isCurrent = { vm.network.sessions.changes.value[controller.sourceId] == initialRevision },
                candidate = { cookie ->
                    try {
                        val fields = cookie.value.toString(Charsets.UTF_8).split(';').map { it.trim().split('=', limit = 2) }
                        val token = names.firstNotNullOfOrNull { name -> fields.firstOrNull { it.size == 2 && it[0] == name }?.get(1) }
                        if (token != null && !controller.state.value.busy) controller.submit(SessionCandidate(CredentialKind.USER_TOKEN, token.toByteArray()))
                    } finally { cookie.value.fill(0) }
                }, error = { error = it }) { controlled -> view = controlled; controlled.open(); awaitCancellation() }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "网页登录无法启动，请检查网络设置或使用手动导入" }
        finally { view = null }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            TextButton(onClick = { controller.cancel(); close() }) { Text("关闭网页登录") }
            TextButton(onClick = { view?.retryDetection() }, enabled = !operation.busy) { Text("重新检测会话") }
        }
        (error ?: operation.message)?.let { Text(it, Modifier.padding(12.dp)) }
        if (operation.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        view?.let { controlled -> AndroidView(factory = { controlled }, modifier = Modifier.weight(1f).fillMaxWidth()) }
    }
}
