package io.github.ddmoyu.picomic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.ddmoyu.picomic.auth.AccountState
import io.github.ddmoyu.picomic.auth.AccountStatus
import io.github.ddmoyu.picomic.auth.PasswordAccountController
import io.github.ddmoyu.picomic.source.picacg.PicacgAccountController
import kotlinx.coroutines.CancellationException

@Composable fun PicacgLoginScreen(controller: PasswordAccountController, networkReady: Boolean, showAvatarFrame: Boolean = true, openRecovery: () -> Unit, openNetwork: () -> Unit) {
    val operation by controller.state.collectAsStateWithLifecycle()
    val accounts by controller.accounts.collectAsStateWithLifecycle()
    val remembered by controller.rememberedAccounts.collectAsStateWithLifecycle()
    val savedAccount = remembered[controller.sourceId]
    val account = accounts[controller.sourceId] ?: AccountState()
    val scroll = rememberScrollState()
    val picacg = controller as? PicacgAccountController
    val registrationResult = picacg?.registrationResult?.collectAsStateWithLifecycle()?.value == true
    LoginSuccessFeedback(operation.loginSucceeded && !registrationResult, controller.title, account, scroll, controller::dismissLoginSuccess)
    var email by remember(controller) { mutableStateOf("") }
    var password by remember(controller) { mutableStateOf("") }
    var rememberPassword by remember(controller) { mutableStateOf(true) }
    var edited by remember(controller) { mutableStateOf(false) }
    var fillError by remember(controller) { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(controller, lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var foregroundEntry by remember(controller, lifecycle) { mutableIntStateOf(0) }
    LaunchedEffect(controller, savedAccount, foreground, foregroundEntry) {
        if (!foreground || edited) return@LaunchedEffect
        try {
            controller.fillRememberedLogin { saved ->
                // A delayed read must not undo typing/clearing or attach an old password to a new account.
                if (foreground && !edited && (email.isEmpty() || email == saved.username)) {
                    email = saved.username
                    password = String(saved.password)
                    fillError = null
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { fillError = "无法读取已保存的账号密码，请手动输入" }
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            // START/STOP can occur within one Compose frame; the counter prevents a missed reload.
            if (event == Lifecycle.Event.ON_START) { foreground = true; foregroundEntry++ }
            if (event == Lifecycle.Event.ON_STOP) {
                foreground = false; password = ""; edited = false; controller.cancel()
                picacg?.dismissRegistrationResult()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.cancel(); picacg?.dismissRegistrationResult() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val profile = account.profile
        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            AppIcon(Glyph.User, modifier = Modifier.size(42.dp), color = MaterialTheme.colorScheme.primary)
            profile?.avatar?.let { coil3.compose.AsyncImage(it, "账号头像", imageLoader = io.github.ddmoyu.picomic.reader.ReaderImages.loader(LocalContext.current), modifier = Modifier.size(60.dp).clip(CircleShape), contentScale = ContentScale.Crop) }
            if (showAvatarFrame) profile?.frame?.let { coil3.compose.AsyncImage(it, "平台头像框", imageLoader = io.github.ddmoyu.picomic.reader.ReaderImages.loader(LocalContext.current), modifier = Modifier.fillMaxSize()) }
        }
        Text(controller.title, style = MaterialTheme.typography.headlineSmall)
        AccountStatusCard(account)
        profile?.level?.let { Text("等级 $it" + profile.title?.takeIf(String::isNotBlank)?.let { title -> " · $title" }.orEmpty(), style = MaterialTheme.typography.bodySmall) }
        Text("使用${controller.title}账号登录。默认记住账号密码，点击登录即加密保存，无论成功或失败，再次打开时自动填充。", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(email, { edited = true; email = it.take(320); password = "" }, Modifier.fillMaxWidth(), enabled = !operation.busy,
            label = { Text("账号 / 邮箱") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            trailingIcon = { if (email.isNotEmpty()) IconAction(Glyph.Close, "清空账号", !operation.busy) { edited = true; email = ""; password = "" } })
        key(controller) {
            PasswordField(password, { edited = true; password = it.take(1024) }, Modifier.fillMaxWidth(), enabled = !operation.busy)
        }
        fillError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { password = ""; controller.cancel(); openRecovery() }, enabled = !operation.busy,
            modifier = Modifier.align(Alignment.End)) { Text("忘记密码") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(rememberPassword, { rememberPassword = it }, enabled = !operation.busy)
            Text("记住账号密码（加密保存）")
        }
        Button(onClick = { controller.login(email, password.toCharArray(), rememberPassword) },
            enabled = networkReady && !operation.busy && email.isNotBlank() && password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(if (account.status == AccountStatus.AUTHENTICATED) "重新登录" else "登录并验证")
        }
        if (picacg != null) PicacgRegistrationControls(picacg, networkReady) { username, secret ->
            edited = true; email = username; password = secret; rememberPassword = true
        }
        if (savedAccount != null) {
            OutlinedButton(onClick = controller::loginSaved, enabled = networkReady && !operation.busy, modifier = Modifier.fillMaxWidth()) { Text("使用已保存账号登录") }
            TextButton(onClick = { edited = true; password = ""; controller.forgetPassword() }, enabled = !operation.busy, modifier = Modifier.fillMaxWidth()) { Text("忘记已保存的密码") }
        }
        if (operation.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = { password = ""; controller.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("取消当前操作") }
        }
        operation.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
        if (!networkReady) Text("网络设置尚未就绪，请先检查设置代理。", color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = controller::probe, enabled = networkReady && !operation.busy, modifier = Modifier.fillMaxWidth()) { Text("测试${controller.title}连接") }
        if (account.status in setOf(AccountStatus.AUTHENTICATED, AccountStatus.NEEDS_VALIDATION)) {
            OutlinedButton(onClick = controller::restore, enabled = networkReady && !operation.busy, modifier = Modifier.fillMaxWidth()) { Text("验证已保存的会话") }
        }
        TextButton(onClick = { edited = true; email = ""; password = ""; controller.logout() }, enabled = !operation.busy, modifier = Modifier.fillMaxWidth()) { Text("清除本地账号") }
        TextButton(onClick = { password = ""; controller.cancel(); openNetwork() }, modifier = Modifier.fillMaxWidth()) { Text("设置代理") }
        Text("匿名可用范围和账号权限由平台决定。会话失效后可重新登录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
}
