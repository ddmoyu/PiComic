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

@Composable fun WebDavSettings(vm: AppViewModel) {
    val controller = vm.webdav; val state by controller.state.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf("") }; var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }; var clear by remember { mutableStateOf(false) }
    LaunchedEffect(state.ready, state.url, state.username) { if (state.ready) { url = state.url; username = state.username } }
    DisposableEffect(Unit) { onDispose { password = ""; controller.cancel() } }
    val edited = url != state.url || username != state.username || password.isNotEmpty()
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("手动同步收藏、阅读记录和偏好。漫画文件、平台账号及下载目录不会上传。")
        OutlinedTextField(url, { url = it.take(2000) }, Modifier.fillMaxWidth(), enabled = !state.busy && state.ready, singleLine = true, label = { Text("HTTPS 同步目录") }, placeholder = { Text("https://example.com/dav/PiComic/") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(username, { username = it.take(256) }, Modifier.fillMaxWidth(), enabled = !state.busy && state.ready, singleLine = true, label = { Text("用户名") })
        OutlinedTextField(password, { password = it.take(4096) }, Modifier.fillMaxWidth(), enabled = !state.busy && state.ready, singleLine = true, label = { Text("密码 / 应用密码") }, visualTransformation = PasswordVisualTransformation(),
            supportingText = { Text(if (state.configured) "地址和用户名不变时可留空保留密码" else "仅在本机加密保存") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Button(enabled = !state.busy && state.ready && url.isNotBlank() && username.isNotBlank(), onClick = { controller.save(url, username, password.toCharArray()); password = "" }) { Text("保存配置") }
        Text("连接测试会在指定目录创建少量临时测试数据，验证写入条件后删除；请先在服务端创建此目录。", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !state.busy && state.configured && !edited, onClick = controller::test) { Text("测试连接") }
            Button(enabled = !state.busy && state.tested && !edited, onClick = controller::preview) { Text(if (state.writable) "预览同步" else "预览只读导入") }
        }
        if (edited && state.configured) Text("配置有改动，保存后需重新测试连接。", style = MaterialTheme.typography.bodySmall)
        if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = controller::cancel) { Text("取消当前操作") } }
        state.message?.let { Text(it) }
        if (state.pending) Text("有待同步数据；请预览后完成同步。", color = MaterialTheme.colorScheme.primary)
        if (state.lastSuccess > 0) Text("上次完整同步：${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(state.lastSuccess))}", style = MaterialTheme.typography.bodySmall)
        if (state.configured) TextButton(enabled = !state.busy, onClick = { clear = true }) { Text("清除本机同步配置") }
    }
    state.preview?.let { proposed -> BackupMergeDialog(proposed, state.busy, controller::dismissPreview, controller::sync, if (state.writable) "合并并同步" else "仅导入本地") }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("清除同步配置？") }, text = { Text("移除本机保存的 WebDAV 账号和密码，保留本地书架与远端文件。") },
        confirmButton = { TextButton(onClick = { clear = false; controller.clear() }) { Text("清除") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("取消") } })
}
