package io.github.ddmoyu.picomic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.contentError
import io.github.ddmoyu.picomic.network.origin
import io.github.ddmoyu.picomic.source.eh.EhClient
import io.github.ddmoyu.picomic.source.html.BROWSER_AGENT
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl

@Composable fun EhLoginScreen(vm: AppViewModel, openRecovery: () -> Unit, openNetwork: () -> Unit) {
    val controller = vm.ehAccount
    val operation by controller.state.collectAsStateWithLifecycle()
    val accounts by controller.accounts.collectAsStateWithLifecycle()
    val network by vm.network.state.collectAsStateWithLifecycle()
    val revisions by vm.network.sessions.changes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var member by remember { mutableStateOf("") }; var hash by remember { mutableStateOf("") }; var igneous by remember { mutableStateOf("") }
    var web by remember { mutableStateOf<Boolean?>(null) }
    var permission by remember(revisions["ehentai"]) { mutableStateOf<String?>(null) }
    var permissionJob by remember { mutableStateOf<Job?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { member = ""; hash = ""; igneous = ""; web = null; controller.cancel(); permissionJob?.cancel() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.cancel(); permissionJob?.cancel() }
    }
    if (web != null) { EhWebLogin(vm, web!!) { web = null }; return }
    val account = accounts[controller.sourceId] ?: AccountState()
    val scroll = rememberScrollState()
    LoginSuccessFeedback(operation.loginSucceeded, "EH / EX", account, scroll, controller::dismissLoginSuccess)
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("EH / EX", style = MaterialTheme.typography.headlineSmall)
        AccountStatusCard(account)
        Text("EH 会话与 EX 访问权限分别验证。登录后可继续阅读原站画廊。")
        Button(onClick = { web = false }, enabled = network.ready && !operation.busy, modifier = Modifier.fillMaxWidth()) { Text("打开 EH 网页登录") }
        OutlinedButton(onClick = { web = true }, enabled = network.ready && !operation.busy, modifier = Modifier.fillMaxWidth()) { Text("打开 EX 网页验证") }
        TextButton(onClick = { member = ""; hash = ""; igneous = ""; controller.cancel(); openRecovery() }, enabled = !operation.busy) { Text("忘记密码") }
        Text("手动导入 Cookie", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(member, { member = it.take(12) }, Modifier.fillMaxWidth(), singleLine = true, enabled = !operation.busy, label = { Text("ipb_member_id") })
        OutlinedTextField(hash, { hash = it.take(4096) }, Modifier.fillMaxWidth(), singleLine = true, enabled = !operation.busy, label = { Text("ipb_pass_hash") }, visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(igneous, { igneous = it.take(4096) }, Modifier.fillMaxWidth(), singleLine = true, enabled = !operation.busy, label = { Text("igneous（EX 可选）") }, visualTransformation = PasswordVisualTransformation())
        Button(onClick = {
            val raw = "ipb_member_id=${member.trim()}; ipb_pass_hash=${hash.trim()}" + if (igneous.isBlank()) "" else "; igneous=${igneous.trim()}"
            member = ""; hash = ""; igneous = ""
            controller.submit(SessionCandidate(CredentialKind.COOKIE, raw.toByteArray()))
        }, enabled = network.ready && !operation.busy && member.isNotBlank() && hash.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("验证 EH 会话并保存") }
        if (operation.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = controller::cancel) { Text("取消当前操作") } }
        operation.message?.let { Text(it) }
        OutlinedButton(onClick = controller::restore, enabled = network.ready && !operation.busy) { Text("验证已保存的会话") }
        OutlinedButton(onClick = {
            permissionJob?.cancel()
            permission = "正在验证 EX 权限"
            permissionJob = scope.launch {
                try {
                    vm.network.awaitReady()
                    val lease = vm.network.sessions.lease("ehentai")
                    try { vm.network.sessions.useLease(lease) { EhClient(vm.network.engine, lease.candidate).checkExAccess() } }
                    finally { lease.close() }
                    permission = "EX 访问权限验证通过"
                } catch (e: CancellationException) { throw e } catch (e: Exception) { permission = contentError(e) }
            }
        }, enabled = network.ready && !operation.busy && accounts["ehentai"]?.status == AccountStatus.AUTHENTICATED) { Text("单独验证 EX 权限") }
        permission?.let { Text(it) }
        TextButton(onClick = controller::logout, enabled = !operation.busy) { Text("退出 EH / EX 账号") }
        TextButton(onClick = openNetwork) { Text("设置代理") }
    }
}

@Composable private fun EhWebLogin(vm: AppViewModel, ex: Boolean, close: () -> Unit) {
    val context = LocalContext.current
    val controller = vm.ehAccount
    val operation by controller.state.collectAsStateWithLifecycle()
    val revisions by vm.network.sessions.changes.collectAsStateWithLifecycle()
    val initial = remember { vm.network.sessions.changes.value["ehentai"] }
    var view by remember { mutableStateOf<ControlledLoginView?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(revisions) { if (revisions["ehentai"] != initial) close() }
    LaunchedEffect(ex) {
        try {
            vm.network.awaitReady()
            val forum = "https://forums.e-hentai.org/".toHttpUrl()
            val cookieUrl = if (ex) EhClient.EX else EhClient.EH
            val names = listOf("ipb_member_id", "ipb_pass_hash", "igneous")
            val scopes = names.flatMap { name ->
                listOf(WebCookieScope(name, "/"), WebCookieScope(name, "/", ".${cookieUrl.host}")) +
                    if (!ex) listOf(WebCookieScope(name, "/", url = forum)) else emptyList()
            }
            val spec = WebLoginSpec(if (ex) EhClient.EX else forum.resolve("index.php?act=Login&CODE=00")!!,
                setOf(EhClient.EH.origin(), EhClient.EX.origin(), forum.origin(), "https://ehgt.org:443", "https://challenges.cloudflare.com:443"), cookieUrl, scopes,
                optionalCookies = setOf("igneous"), userAgent = BROWSER_AGENT)
            WebLoginRuntime.withLogin(context, spec, vm.network.engine,
                isCurrent = { vm.network.sessions.changes.value["ehentai"] == initial },
                candidate = { if (!controller.state.value.busy) controller.submit(it) else it.value.fill(0) }, error = { error = it }) { controlled ->
                view = controlled; controlled.open(); awaitCancellation()
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "网页登录无法启动，请检查代理设置或手动导入 Cookie" }
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
