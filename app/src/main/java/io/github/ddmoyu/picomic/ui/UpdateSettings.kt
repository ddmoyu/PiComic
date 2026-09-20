package io.github.ddmoyu.picomic.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ddmoyu.picomic.BuildConfig
import io.github.ddmoyu.picomic.update.*
import kotlinx.coroutines.launch

@Composable fun UpdateSettings(ui: UiState, vm: AppViewModel) {
    val repo = vm.updates; val state by repo.state.collectAsStateWithLifecycle()
    val context = LocalContext.current; val uri = LocalUriHandler.current; val scope = rememberCoroutineScope()
    var installing by remember { mutableStateOf(false) }; var cancel by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        repo.message(if (context.packageManager.canRequestPackageInstalls()) "安装权限已允许，请点击安装更新" else "尚未允许安装，可稍后重新授权")
    }
    SectionTitle("应用版本")
    SettingRow("当前版本", BuildConfig.VERSION_NAME, onClick = {})
    SettingRow("发布渠道", if (state.configured) "GitHub Releases · 稳定版" else "公开发布渠道尚未配置", onClick = {})
    if (!state.configured) Note("当前为开发构建。配置公开的安装包发布仓库后可检查更新；源码仓库保持私有。")
    if (state.checkedAt > 0) Note("上次成功检查：" + java.time.Instant.ofEpochMilli(state.checkedAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
    PreferenceToggle("启动时检查更新", "checkOnStart", ui, vm, "每天至多自动尝试一次；只提示，不自动下载或安装", enabled = state.configured)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = repo::check, enabled = state.configured && state.initialized && !state.busy && !state.task, modifier = Modifier.fillMaxWidth()) { Text(if (state.phase == UpdatePhase.CHECKING) "正在检查" else "检查更新") }
        state.message?.let { Text(it, color = if (state.phase == UpdatePhase.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        state.bundle?.let { bundle ->
            HorizontalDivider()
            Text("PiComic ${bundle.versionName}", style = MaterialTheme.typography.titleLarge)
            Text("发布于 ${bundle.release.published.substringBefore('T')}", style = MaterialTheme.typography.bodySmall)
            state.artifact?.let { Text("安装包 %.1f MB".format(it.asset.size / 1048576.0)) }
            if (bundle.release.notes.isNotBlank()) Text(bundle.release.notes)
            TextButton(onClick = { runCatching { uri.openUri(bundle.release.page) }.onFailure { repo.message("无法打开浏览器") } }) { Text("查看 GitHub Release") }
            if (state.phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.PAUSED, UpdatePhase.VERIFYING) || state.task && state.phase == UpdatePhase.ERROR) {
                val total = state.artifact?.asset?.size ?: 1
                LinearProgressIndicator(progress = { (state.downloaded.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("%.1f / %.1f MB".format(state.downloaded / 1048576.0, total / 1048576.0))
            }
            if (state.phase in setOf(UpdatePhase.READY, UpdatePhase.WAITING_INSTALL)) {
                Button(enabled = !installing, modifier = Modifier.fillMaxWidth(), onClick = {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        runCatching { permissions.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }.onFailure { repo.message("无法打开安装权限设置") }
                    } else {
                        installing = true
                        scope.launch {
                            try { val intent = repo.installIntent(); context.startActivity(intent); repo.installerOpened() }
                            catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) { repo.message((e as? UpdateFailure)?.message ?: "无法启动系统安装，请检查权限后重试") }
                            finally { installing = false }
                        }
                    }
                }) { Text(if (installing) "正在核对安装包" else "安装更新") }
            } else if (state.phase == UpdatePhase.DOWNLOADING) {
                OutlinedButton(onClick = repo::pause, modifier = Modifier.fillMaxWidth()) { Text("暂停下载") }
            } else if (state.phase == UpdatePhase.VERIFYING) Text("正在校验安装包")
            else if (state.artifact != null && bundle.versionCode > BuildConfig.VERSION_CODE && !state.busy) {
                Button(onClick = repo::download, modifier = Modifier.fillMaxWidth()) { Text(if (state.task) "继续下载" else "下载更新") }
            }
        }
        if (state.task || state.phase == UpdatePhase.ERROR) TextButton(onClick = { cancel = true }, enabled = !installing) { Text("取消更新任务") }
    }
    if (cancel) AlertDialog(onDismissRequest = { cancel = false }, title = { Text("取消更新任务？") }, text = { Text("删除当前更新包和断点，下次可重新检查下载。") },
        confirmButton = { TextButton(onClick = { cancel = false; repo.cancel() }) { Text("取消更新") } }, dismissButton = { TextButton(onClick = { cancel = false }) { Text("保留") } })
}
