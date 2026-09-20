package io.github.ddmoyu.picomic.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import io.github.ddmoyu.picomic.source.eh.EhConfiguration
import kotlinx.coroutines.*

@Composable fun EhConfigurationSettings(vm: AppViewModel) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<EhConfiguration?>(null) }; var error by remember { mutableStateOf<String?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) scope.launch {
        try {
            pending = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(4097); var count = 0
                while (count < buffer.size) { val read = input.read(buffer, count, buffer.size - count); if (read < 0) break; count += read }
                EhConfiguration.decode(buffer.copyOf(count))
            } ?: error("文件不可读") }
            error = null
        } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "配置无效或版本不支持，请使用不含 Cookie 的设置文件（最多 4 KB）" }
    } }
    SettingRow("导入配置文件", "预览站点、原图、警告和副标题设置", onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
    error?.let { Note(it) }
    pending?.let { configuration -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("应用 EH 配置？") }, text = {
        Text("站点：${configuration.site}\n原图：${if (configuration.original) "优先" else "普通图片"}\n内容警告：${if (configuration.ignoreWarning) "跳过可忽略提示" else "保留"}\n副标题：${if (configuration.subtitle) "优先" else "普通标题"}\n\n站点权限仍需平台验证。")
    }, confirmButton = { TextButton(onClick = { configuration.preferences().forEach(vm::preference); pending = null }) { Text("应用") } }, dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } }) }
}
