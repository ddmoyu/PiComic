package io.github.ddmoyu.picomic.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ddmoyu.picomic.backup.*
import kotlinx.coroutines.*

@Composable fun BackupSettings(vm: AppViewModel) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var exported by remember { mutableStateOf<ByteArray?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val bytes = exported; exported = null
        if (uri != null && bytes != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() } ?: error("无法写入备份文件") }
                message = "用户数据已导出"
            } catch (e: CancellationException) { throw e } catch (_: Exception) { message = "备份写入失败，请检查目录与可用空间" } finally { busy = false; bytes.fill(0) }
        } else bytes?.fill(0)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; message = null
            try {
                preview = withContext(Dispatchers.IO) { vm.backups.preview(context.contentResolver.openInputStream(uri) ?: error("无法读取文件")) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { message = "文件无效、版本不支持或超过 10 MB，原数据未改动" } finally { busy = false }
        }
    }
    SettingRow("导入用户数据", if (busy) "正在处理" else "选择备份，预览差异后合并", onClick = { if (!busy) open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
    SettingRow("导出用户数据", "收藏、阅读记录、内容筛选和阅读偏好", onClick = { if (!busy) scope.launch {
        busy = true; message = null
        try { exported = vm.backups.export(); save.launch("PiComic-${java.time.LocalDate.now()}.json") }
        catch (e: CancellationException) { throw e } catch (_: Exception) { message = "无法生成备份，请检查存储空间或数据大小" } finally { busy = false }
    } })
    message?.let { Note(it) }
    preview?.let { proposed -> BackupMergeDialog(proposed, busy, { preview = null }, { choices ->
        scope.launch {
            busy = true
            try { vm.backups.apply(proposed, choices); preview = null; message = "用户数据已合并" }
            catch (e: CancellationException) { throw e } catch (_: Exception) { preview = null; message = "合并未完成，本地可能已变化，请重新预览" } finally { busy = false }
        }
    }) }
}

@Composable fun BackupMergeDialog(preview: BackupPreview, busy: Boolean, close: () -> Unit, apply: (Map<String, MergeSide>) -> Unit, action: String = "合并数据") {
    var choices by remember(preview) { mutableStateOf(emptyMap<String, MergeSide>()) }
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text("数据合并预览") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
