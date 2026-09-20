@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.ddmoyu.picomic.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.download.*
import io.github.ddmoyu.picomic.reader.ReaderPage
import kotlinx.coroutines.*

@Composable fun DownloadSelection(detail: ComicDetails, vm: AppViewModel, close: () -> Unit) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = { Text("选择下载章节") }, text = {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(selected.size == detail.chapters.size, { checked -> selected = if (checked) detail.chapters.map { it.id }.toSet() else emptySet() }, enabled = !busy)
                Text("全选 · 已选 ${selected.size} 话")
            }
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(detail.chapters, key = { it.id }) { chapter -> Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { selected = if (chapter.id in selected) selected - chapter.id else selected + chapter.id }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(chapter.id in selected, null); Text(chapter.title, Modifier.weight(1f))
                } }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = selected.isNotEmpty() && !busy, onClick = {
        busy = true; error = null
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        scope.launch {
            try {
                vm.downloadsRepository.enqueue(detail.summary, detail.chapters.filter { it.id in selected })
                DownloadService.scheduleMaintenance(context); DownloadService.start(context); close()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = contentError(e) } finally { busy = false }
        }
    }) { Text(if (busy) "正在加入" else "加入下载") } }, dismissButton = { TextButton(enabled = !busy, onClick = close) { Text("取消") } })
}

@Composable fun DownloadManagerScreen(vm: AppViewModel, read: (String) -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val tasks by vm.downloadsRepository.tasks.collectAsStateWithLifecycle()
    val running by vm.downloadsRepository.running.collectAsStateWithLifecycle()
    val repositoryError by vm.downloadsRepository.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }; var deleting by remember { mutableStateOf<DownloadTask?>(null) }
    fun perform(action: suspend () -> Unit) { scope.launch { try { action(); error = null } catch (e: CancellationException) { throw e } catch (e: Exception) { error = contentError(e) } } }
    if (tasks.isEmpty()) EmptyState(if (repositoryError == null) "暂无下载任务" else "下载记录不可用", repositoryError ?: "在作品详情选择章节，即可下载后离线阅读。", Glyph.Download)
    else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        error?.let { item { Note(it) } }
        repositoryError?.let { item { Note(it) } }
        item { TextButton(onClick = { perform { vm.downloadsRepository.stop() } }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("暂停全部") } }
        items(tasks, key = { it.id }) { task ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(task.displayTitle(ui.enabled("eh.subtitle")), style = MaterialTheme.typography.titleMedium)
                Text("${task.key().source.shortTitle} · ${task.chapterTitle}", style = MaterialTheme.typography.bodyMedium)
                Text("${downloadLabel(task.state)} · ${task.completed} / ${task.total.takeIf { it > 0 }?.toString() ?: "—"} 页")
                if (task.total > 0) LinearProgressIndicator(progress = { task.completed.toFloat() / task.total }, modifier = Modifier.fillMaxWidth())
                task.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (task.state == DownloadState.COMPLETED.name) TextButton(onClick = { read(task.id) }) { Text("离线阅读") }
                    else if (running && task.state in DownloadRepository.ACTIVE) TextButton(onClick = { perform { vm.downloadsRepository.pause(task.id) } }) { Text("暂停") }
                    else if (task.state != DownloadState.DELETING.name) TextButton(onClick = { perform {
                        if (task.state == DownloadState.WAITING_QUOTA.name) vm.content.ehImages.retry()
                        vm.downloadsRepository.resume(task.id); DownloadService.start(context)
                    } }) { Text(if (task.state == DownloadState.WAITING_QUOTA.name) "额度恢复后继续" else "继续下载") }
                    TextButton(onClick = { deleting = task }) { Text(if (task.state == DownloadState.DELETING.name) "重试删除" else "删除") }
                }
            }
            HorizontalDivider()
        }
    }
    deleting?.let { task -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除下载？") }, text = { Text("删除「${task.chapterTitle}」的任务和已下载文件，收藏与阅读记录仍会保留。") },
        confirmButton = { TextButton(onClick = { deleting = null; perform { vm.downloadsRepository.remove(task.id) } }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
}
private fun downloadLabel(value: String) = when (value) {
    "QUEUED" -> "等待下载"; "RESOLVING" -> "获取章节"; "DOWNLOADING" -> "正在下载"; "PROCESSING" -> "处理并校验"; "COMPLETED" -> "下载完成"
    "PAUSED" -> "已暂停"; "WAITING_AUTH" -> "等待原账号登录"; "WAITING_NETWORK" -> "等待网络"; "WAITING_STORAGE" -> "检查下载目录"
    "WAITING_QUOTA" -> "等待额度恢复"; "DELETING" -> "删除待完成"; else -> "下载失败"
}

@Composable fun DownloadLocationSettings(ui: UiState, vm: AppViewModel) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            vm.downloadsRepository.storage.checkTarget(uri.toString())
            vm.preference("downloadTarget", uri.toString()); error = null
        } catch (_: Exception) { error = "目录没有授予持久读写权限，请重新选择" }
    }
    val target = ui.pref("downloadTarget", DownloadStorage.INTERNAL)
    SettingRow("设置下载目录", if (target == DownloadStorage.INTERNAL) "应用私有目录 · 卸载时清除" else "系统授权目录 · ${Uri.parse(target).lastPathSegment.orEmpty()}", Glyph.Folder, onClick = { choose.launch(null) })
    if (target != DownloadStorage.INTERNAL) SettingRow("新任务使用应用私有目录", "已有任务继续使用原目录", onClick = { vm.preference("downloadTarget", DownloadStorage.INTERNAL) })
    Note("目录变更仅用于新任务。重新选择原目录可恢复丢失的授权。")
    error?.let { Note(it) }
    PreferenceChoice("下载并行", "parallel", (1..6).map { it.toString() }, ui, vm, "3")
    PreferenceToggle("仅 Wi-Fi 下载", "downloadWifi", ui, vm, "关闭时允许使用移动网络；开启后在页边界暂停")
}

@Composable fun OfflineReaderScreen(id: String, ui: UiState, vm: AppViewModel, back: () -> Unit) {
    var selected by rememberSaveable(id) { mutableStateOf(id) }
    var chapter by remember(selected) { mutableStateOf<OfflineChapter?>(null) }
    var history by remember(selected) { mutableStateOf<ContentProgress?>(null) }
    var error by remember(selected) { mutableStateOf<String?>(null) }
    val library by vm.library.state.collectAsStateWithLifecycle()
    LaunchedEffect(selected) {
        try {
            val loaded = vm.downloadsRepository.offline(selected)
            history = vm.library.awaitReady().progress.firstOrNull { it.key == loaded.task.key() && it.chapterId == loaded.task.chapterId }
            chapter = loaded
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = contentError(e) }
    }
    val current = chapter
    if (current == null) {
        if (error == null) CircularProgressIndicator(Modifier.padding(32.dp))
        else Column(Modifier.padding(20.dp)) { Text(error!!); TextButton(onClick = back) { Text("返回下载管理") } }
    } else key(selected) {
        val task = current.task; val pages = current.pages
        val initial = history?.let { saved -> pages.indexOfFirst { it.pageId == saved.pageId }.takeIf { it >= 0 } ?: (saved.page - 1).coerceIn(pages.indices) } ?: 0
        val book = remember(current, ui.enabled("eh.subtitle")) { ReaderBook("offline/${task.id}", task.displayTitle(ui.enabled("eh.subtitle")), current.chapters.map { it.chapterTitle }, pages.size, true) {
            pages.map { ReaderPage("offline/${task.id}/${it.pageId}/${it.checksum}", Uri.parse(it.uri!!), it.width!!, it.height!!) }
        } }
        ReaderSurface(book, current.chapters.indexOfFirst { it.id == selected } + 1, initial + 1, history?.offset ?: 0f,
            ui.copy(preferences = ui.preferences + library.preferences[task.key()].orEmpty()), vm, back,
            onRecord = { position -> vm.library.record(task.comic.summary(), ContentProgress(task.key(), task.chapterId, pages[position.page - 1].pageId, position.page, position.offsetRatio, position.mode)) },
            onChapter = { selected = current.chapters[it - 1].id },
            onReadingPreference = { name, value -> vm.library.preference(task.key(), name, value) },
            resetPreferences = { listOf("readingMode", "readerBackground", "readerOrientation", "readerBrightness").forEach { vm.library.preference(task.key(), it, null) } })
    }
}

@Composable fun CacheSettings(ui: UiState, vm: AppViewModel) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var used by remember { mutableLongStateOf(0) }; var pending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }; var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(ui.pref("cache", "500 MB")) {
        try {
            io.github.ddmoyu.picomic.reader.ManagedImageCache.setQuota(context, ui.pref("cache", "500 MB"))
            while (true) {
                val snapshot = withContext(Dispatchers.IO) { io.github.ddmoyu.picomic.reader.ManagedImageCache.get(context).let { it.size to it.pending } }
                used = snapshot.first; pending = snapshot.second; delay(1000)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "缓存回收失败，请重试" }
    }
    PreferenceChoice("缓存大小限制", "cache", listOf("250 MB", "500 MB", "1 GB", "2 GB"), ui, vm, "500 MB",
        "${String.format(java.util.Locale.ROOT, "%.2f", used / 1_000_000.0)} MB / ${ui.pref("cache", "500 MB")}")
    SettingRow("清除缓存", if (pending) "等待正在使用的图片释放后回收" else "不影响收藏、下载和更新安装包", Glyph.Trash, onClick = { confirm = true })
    error?.let { Note(it) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("清除图片缓存？") }, text = { Text("在线图片再次阅读时需要重新加载。已下载章节仍可离线阅读。") }, confirmButton = {
        TextButton(onClick = { confirm = false; scope.launch { try { io.github.ddmoyu.picomic.reader.ManagedImageCache.clear(context); error = null } catch (_: Exception) { error = "缓存清理失败，请重试" } } }) { Text("清除") }
    }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
}
