package io.github.ddmoyu.picomic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ddmoyu.picomic.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable fun NetworkSettings(repository: NetworkRepository) {
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var custom by rememberSaveable(state.generation) { mutableStateOf(state.custom) }
    var host by rememberSaveable(state.generation) { mutableStateOf(state.host) }
    var port by rememberSaveable(state.generation) { mutableStateOf(state.port) }
    // Credential drafts never enter saved instance state or ordinary preferences.
    var username by remember(state.generation) { mutableStateOf("") }
    var password by remember(state.generation) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Note("默认跟随系统 VPN / 代理。保存后立即生效；自定义代理不可用时不会切换为直连。")
    SettingRow("当前连接", state.label, onClick = {})
    listOf(false to "跟随系统", true to "自定义 HTTP 代理").forEach { (value, title) ->
        SettingRow(title, onClick = { custom = value }, trailing = {
            RadioButton(custom == value, onClick = { custom = value }, enabled = !saving)
        })
    }
    if (custom) Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), label = { Text("代理主机") }, singleLine = true, enabled = !saving)
        OutlinedTextField(port, { port = it }, Modifier.fillMaxWidth(), label = { Text("端口") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !saving)
        OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("代理用户名（可选）") }, singleLine = true, enabled = !saving)
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("代理口令（可选）") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !saving)
        if (state.hasCredentials) Text("已保存认证信息。修改代理时请重新填写；留空保存将移除认证。", style = MaterialTheme.typography.bodySmall)
    }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = {
            error = null
            val profile = try {
                if (custom) {
                    require(username.isNotBlank() || password.isEmpty()) { "填写代理口令时也需要用户名" }
                    NetworkProfile.HttpProxy(host.trim(), port.toIntOrNull() ?: throw IllegalArgumentException("端口须为 1–65535"),
                        if (username.isBlank()) null else ProxyCredentials(username, password))
                } else NetworkProfile.FollowSystem
            } catch (invalid: IllegalArgumentException) { error = invalid.message; return@Button }
            saving = true
            scope.launch {
                try { repository.save(profile); username = ""; password = "" }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "设置保存失败，原连接方式保持不变" }
                finally { saving = false }
            }
        }, Modifier.fillMaxWidth(), enabled = !saving) { Text(if (saving) "正在保存…" else "保存网络设置") }
        OutlinedButton(onClick = repository::startTest, Modifier.fillMaxWidth(), enabled = state.ready && !state.busy && !saving) {
            Text(if (state.busy) "正在测试…" else "测试已保存的连接")
        }
        Text("测试访问 api.github.com，仅检查 HTTPS 连接。", style = MaterialTheme.typography.bodySmall)
        (error ?: state.message)?.let { Text(it, color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
