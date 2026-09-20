package io.github.ddmoyu.picomic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

internal fun AccountState.label(): String = when (status) {
    AccountStatus.ANONYMOUS -> "未登录"
    AccountStatus.AUTHENTICATING -> "正在验证账号"
    AccountStatus.AUTHENTICATED -> "已登录 · $displayName"
    AccountStatus.NEEDS_VALIDATION -> "会话待验证"
    AccountStatus.EXPIRED -> "会话已失效"
}

@Composable fun PicacgLoginScreen(controller: PasswordAccountController, networkReady: Boolean, showAvatarFrame: Boolean = true, openNetwork: () -> Unit) {
    val operation by controller.state.collectAsStateWithLifecycle()
    val accounts by controller.accounts.collectAsStateWithLifecycle()
    val remembered by controller.rememberedAccounts.collectAsStateWithLifecycle()
    val savedAccount = remembered[controller.sourceId]
    val account = accounts[controller.sourceId] ?: AccountState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberPassword by remember { mutableStateOf(true) }
    LaunchedEffect(savedAccount) { if (email.isEmpty()) email = savedAccount.orEmpty() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { password = ""; controller.cancel() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.cancel() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val profile = account.profile
        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            AppIcon(Glyph.User, modifier = Modifier.size(42.dp), color = MaterialTheme.colorScheme.primary)
            profile?.avatar?.let { coil3.compose.AsyncImage(it, "账号头像", imageLoader = io.github.ddmoyu.picomic.reader.ReaderImages.loader(LocalContext.current), modifier = Modifier.size(60.dp).clip(CircleShape), contentScale = ContentScale.Crop) }
            if (showAvatarFrame) profile?.frame?.let { coil3.compose.AsyncImage(it, "平台头像框", imageLoader = io.github.ddmoyu.picomic.reader.ReaderImages.loader(LocalContext.current), modifier = Modifier.fillMaxSize()) }
        }
        Text(controller.title, style = MaterialTheme.typography.headlineSmall)
        Text(account.label(), style = MaterialTheme.typography.titleMedium)
        profile?.level?.let { Text("等级 $it" + profile.title?.takeIf(String::isNotBlank)?.let { title -> " · $title" }.orEmpty(), style = MaterialTheme.typography.bodySmall) }
        Text("使用${controller.title}账号登录。验证成功后加密保存会话；开启记住账号密码后，可一键重新登录并加密备份。", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(email, { email = it.take(320) }, Modifier.fillMaxWidth(), enabled = !operation.busy,
            label = { Text("账号 / 邮箱") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(password, { password = it.take(1024) }, Modifier.fillMaxWidth(), enabled = !operation.busy,
            label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(rememberPassword, { rememberPassword = it }, enabled = !operation.busy)
            Text("记住账号密码（加密保存）")
        }
        Button(onClick = { val secret = password.toCharArray(); password = ""; controller.login(email, secret, rememberPassword) },
            enabled = networkReady && !operation.busy && email.isNotBlank() && password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(if (account.status == AccountStatus.AUTHENTICATED) "重新登录" else "登录并验证")
        }
        if (savedAccount != null) {
            OutlinedButton(onClick = controller::loginSaved, enabled = networkReady && !operation.busy, modifier = Modifier.fillMaxWidth()) { Text("使用已保存账号登录") }
            TextButton(onClick = controller::forgetPassword, enabled = !operation.busy, modifier = Modifier.fillMaxWidth()) { Text("忘记已保存的密码") }
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
        TextButton(onClick = { password = ""; controller.logout() }, enabled = !operation.busy, modifier = Modifier.fillMaxWidth()) { Text("清除本地账号") }
        TextButton(onClick = { password = ""; controller.cancel(); openNetwork() }, modifier = Modifier.fillMaxWidth()) { Text("设置代理") }
        Text("匿名可用范围和账号权限由平台决定。会话失效后可重新登录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
}
