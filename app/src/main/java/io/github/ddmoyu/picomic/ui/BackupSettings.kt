package io.github.ddmoyu.picomic.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.unit.dp
import io.github.ddmoyu.picomic.backup.*
import io.github.ddmoyu.picomic.auth.AccountSlots
import kotlinx.coroutines.*

@Composable fun BackupSettings(vm: AppViewModel) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ConfigImportPreview?>(null) }
    var exported by remember { mutableStateOf<ByteArray?>(null) }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var passwordAction by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { preview?.close(); exported?.fill(0); pending?.fill(0) } }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { preview?.close(); preview = null }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = exported; exported = null
        if (uri != null && bytes != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() } ?: error("无法写入备份文件") }
                message = "配置已加密导出，请妥善保存备份密码"
            } catch (e: CancellationException) { throw e } catch (_: Exception) { message = "备份写入失败，请检查目录与可用空间" } finally { busy = false; bytes.fill(0) }
        } else { bytes?.fill(0); if (uri != null) message = "导出已中断，请重试" }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; message = null
            try {
                val bytes = withContext(Dispatchers.IO) { BackupEncryption.read(context.contentResolver.openInputStream(uri) ?: error("无法读取文件")) }
                if (BackupEncryption.isEncrypted(bytes)) { pending?.fill(0); pending = bytes; passwordAction = "import" }
                else try { preview?.close(); preview = vm.backups.previewConfig(bytes) } finally { bytes.fill(0) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { message = "文件无效、版本不支持或超过大小上限，原数据未改动" } finally { busy = false }
        }
    }
    SettingRow("导入配置与账号", if (busy) "正在处理" else "输入备份密码，预览后导入；兼容旧版 JSON", onClick = { if (!busy) { message = null; open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } })
    SettingRow("导出配置与账号", "密码加密：收藏、阅读记录、偏好和已保存的账号", onClick = { if (!busy) { message = null; passwordAction = "export" } })
    message?.let { Note(it) }
    passwordAction?.let { action -> BackupPasswordDialog(action == "export", busy, message,
        close = { passwordAction = null; pending?.fill(0); pending = null; message = null },
        confirm = { password, includeAccounts -> scope.launch {
            busy = true; message = null
            try {
                if (action == "export") {
                    exported = vm.backups.exportEncrypted(password, includeAccounts)
                    passwordAction = null
                    save.launch("PiComic-${java.time.LocalDate.now()}.picomic")
                } else {
                    val bytes = pending ?: error("请重新选择备份文件")
                    preview?.close(); preview = vm.backups.previewConfig(bytes, password)
                    bytes.fill(0); pending = null; passwordAction = null
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = if (action == "import") "密码错误、文件损坏或版本不支持，原数据未改动" else "无法生成加密备份，请检查本机数据与存储空间" }
            finally { password.fill('\u0000'); busy = false }
        }.invokeOnCompletion { password.fill('\u0000') } }) }
    preview?.let { proposed ->
        var selected by remember(proposed) { mutableStateOf(proposed.accounts.filterNot { it.hasLocal }.map { it.source }.toSet()) }
        BackupMergeDialog(proposed.data, busy, { proposed.close(); preview = null }, { choices ->
        val work = proposed.copyForApply()
        val accountSources = selected
        scope.launch {
            busy = true
            try {
                val result = vm.backups.applyConfig(work, choices, accountSources)
                message = "配置已合并；导入 ${result.restored} 个账号，保留本机 ${result.skipped} 项账号选择。" +
                    if (result.failed.isEmpty()) "导入的会话待验证，可前往账号管理验证或使用已保存账号登录。" else "以下账号未导入，请重试：${result.failed.joinToString("、")}"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "导入未完成，请检查本机数据后重新预览" }
            finally { work.close(); proposed.close(); preview = null; busy = false }
        }.invokeOnCompletion { work.close() }
    }, action = "导入配置", extraContent = {
        if (proposed.accounts.isNotEmpty()) {
            Text("账号", style = MaterialTheme.typography.titleSmall)
            Text("勾选将替换该来源的本机账号。会话需重新验证，过期时可使用已保存账号登录。", style = MaterialTheme.typography.bodySmall)
            proposed.accounts.forEach { account ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { selected = if (account.source in selected) selected - account.source else selected + account.source }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(account.source in selected, null, enabled = !busy)
                    Column(Modifier.weight(1f)) {
                        Text("${AccountSlots.titles.getValue(account.source)} · ${account.displayName}")
                        Text((if (account.hasPassword) "含加密保存的账号密码" else "仅登录会话") + if (account.hasLocal) " · 本机已有账号" else "", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }) }
}

@Composable internal fun BackupPasswordDialog(exporting: Boolean, busy: Boolean, error: String?, close: () -> Unit, confirm: (CharArray, Boolean) -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var accounts by remember { mutableStateOf(true) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { password = ""; repeated = "" }
    AlertDialog(onDismissRequest = { if (!busy) close() },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(if (exporting) "加密导出" else "解密备份") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (exporting) "设置 8–128 位备份密码，换手机导入时使用。忘记密码将无法恢复此备份。" else "输入导出时设置的备份密码。解密成功后可预览，尚不会修改本机数据。")
            OutlinedTextField(password, { password = it.take(128) }, label = { Text("备份密码") }, enabled = !busy, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            if (exporting) {
                OutlinedTextField(repeated, { repeated = it.take(128) }, label = { Text("再次输入备份密码") }, enabled = !busy, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(accounts, { accounts = it }, enabled = !busy); Text("包含登录会话及已记住的账号密码") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(enabled = !busy && password.length in 8..128 && (!exporting || password == repeated), onClick = {
            val secret = password.toCharArray(); password = ""; repeated = ""; confirm(secret, accounts)
        }) { Text(if (exporting) "加密并选择保存位置" else "解密并预览") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { password = ""; repeated = ""; close() }) { Text("取消") } })
}

@Composable fun BackupMergeDialog(preview: BackupPreview, busy: Boolean, close: () -> Unit, apply: (Map<String, MergeSide>) -> Unit, action: String = "合并数据", extraContent: @Composable () -> Unit = {}) {
    var choices by remember(preview) { mutableStateOf(emptyMap<String, MergeSide>()) }
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text("数据合并预览") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            extraContent()
            Text("新增 ${preview.additions} 项，${preview.conflicts.size} 项需要选择。")
            if (preview.unknownSources.isNotEmpty()) Text("未知来源记录会保留，暂不启用：${preview.unknownSources.joinToString("、")}", style = MaterialTheme.typography.bodySmall)
            if (preview.conflicts.isEmpty()) Text("相同记录保留更新的时间；删除记录随备份保存。")
            else {
                Row {
                    TextButton(enabled = !busy, onClick = { choices = preview.conflicts.associate { it.key to MergeSide.LOCAL } }) { Text("全部保留本地") }
                    TextButton(enabled = !busy, onClick = { choices = preview.conflicts.associate { it.key to MergeSide.INCOMING } }) { Text("全部使用导入") }
                }
                LazyColumn(Modifier.heightIn(max = 330.dp)) { items(preview.conflicts, key = { it.key }) { conflict ->
                    Text(conflict.title, style = MaterialTheme.typography.titleSmall)
                    listOf(MergeSide.LOCAL to "本地：${conflict.local}", MergeSide.INCOMING to "导入：${conflict.incoming}").forEach { (side, text) ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { choices = choices + (conflict.key to side) }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(choices[conflict.key] == side, null, enabled = !busy); Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                } }
            }
        }
    }, confirmButton = { TextButton(enabled = !busy && preview.conflicts.all { it.key in choices }, onClick = { apply(choices) }) { Text(if (busy) "正在处理" else action) } },
        dismissButton = { TextButton(enabled = !busy, onClick = close) { Text("取消") } })
}
