package io.github.ddmoyu.picomic.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ddmoyu.picomic.data.EventLog
import kotlinx.coroutines.*

@Composable fun LogSettings() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val log = remember { EventLog.get(context) }; val events by log.events.collectAsStateWithLifecycle(); val error by log.error.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> if (uri != null) scope.launch {
        try {
            val bytes = log.export()
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("无法写入") }
            message = "诊断事件已导出"
        } catch (e: CancellationException) { throw e } catch (_: Exception) { message = "日志导出失败，请检查文件位置" }
    } }
    Note("保留最近 300 条操作事件。不采集账号、密码、Cookie、请求地址或漫画正文。")
    SettingRow("导出日志", "导出当前诊断事件", Glyph.Download, onClick = { save.launch("PiComic-events-${java.time.LocalDate.now()}.txt") })
    error?.let { Note(it) }; message?.let { Note(it) }
    if (events.isEmpty()) Note("暂无事件")
    events.asReversed().forEach { event -> SettingRow(event.code.summary,
        "${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(event.time))} · ${event.code.level} · ${event.source?.shortTitle ?: event.code.area}", onClick = {}) }
}
