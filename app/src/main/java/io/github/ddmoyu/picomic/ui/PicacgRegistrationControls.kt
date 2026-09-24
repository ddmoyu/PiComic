package io.github.ddmoyu.picomic.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ddmoyu.picomic.auth.AccountStatus
import io.github.ddmoyu.picomic.source.picacg.PicacgAccountController
import io.github.ddmoyu.picomic.source.picacg.RegistrationPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun PicacgRegistrationControls(
    controller: PicacgAccountController,
    networkReady: Boolean,
    fill: (String, String) -> Unit
) {
    val registration by controller.registration.collectAsStateWithLifecycle()
    val operation by controller.state.collectAsStateWithLifecycle()
    val accounts by controller.accounts.collectAsStateWithLifecycle()
    val remembered by controller.rememberedAccounts.collectAsStateWithLifecycle()
    val showResult by controller.registrationResult.collectAsStateWithLifecycle()
    val status = accounts[controller.sourceId]?.status ?: AccountStatus.ANONYMOUS
    val saved = remembered[controller.sourceId]
    val currentFill by rememberUpdatedState(fill)
    LaunchedEffect(controller, operation.busy, status, saved) {
        if (!operation.busy) controller.refreshRegistration()
    }
    LaunchedEffect(registration.revision, operation.busy) {
        if (!operation.busy && showResult && registration.phase != null) {
            try { controller.withRegistration { currentFill(it.username, String(it.password)) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* The controller reports storage failures. */ }
        }
    }
    if (status != AccountStatus.AUTHENTICATED &&
        (registration.phase != null || (saved == null && status == AccountStatus.ANONYMOUS))) {
        OutlinedButton(onClick = controller::registerOneClick,
            enabled = registration.ready && networkReady && !operation.busy,
            modifier = Modifier.fillMaxWidth().testTag("picacg-register")) {
            Text(if (registration.phase == null) "一键注册" else "继续注册并登录")
        }
    }
    if (registration.phase != null) {
        Text(if (registration.phase == RegistrationPhase.REGISTERED)
            "注册账号已加密保存，后续自动登录。" else "已保存生成的账号资料，可继续完成注册和登录。",
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = controller::showRegistrationResult, enabled = !operation.busy,
            modifier = Modifier.fillMaxWidth()) { Text("查看注册资料") }
    }
    if (showResult && registration.phase != null && !operation.busy) {
        RegistrationResultDialog(controller, registration.phase == RegistrationPhase.REGISTERED)
    }
}

@Composable private fun RegistrationResultDialog(controller: PicacgAccountController, registered: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var details by remember(controller) { mutableStateOf("") }
    var visible by remember(controller) { mutableStateOf(false) }
    var copied by remember(controller) { mutableStateOf(false) }
    var error by remember(controller) { mutableStateOf<String?>(null) }
    LaunchedEffect(controller, visible) {
        try { controller.withRegistration { details = it.displayText(visible) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "无法读取注册资料，请重试" }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        details = ""
        controller.dismissRegistrationResult()
    }
    DisposableEffect(controller) { onDispose { details = "" } }
    AlertDialog(onDismissRequest = controller::dismissRegistrationResult,
        modifier = Modifier.testTag("registration-result"),
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(if (registered) "注册成功 · 账号资料" else "注册资料 · 等待确认") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(details, modifier = Modifier.testTag("registration-details"), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { visible = !visible }) { Text(if (visible) "隐藏密码" else "显示密码") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = details.isNotEmpty(), onClick = {
                scope.launch {
                    try {
                        controller.withRegistration { record ->
                            val clip = ClipData.newPlainText("哔咔注册资料", record.displayText())
                            if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply {
                                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                            }
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                            copied = true
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "复制失败，请重试" }
                }
            }) { Text(if (copied) "已复制" else "一键复制") }
        },
        dismissButton = { TextButton(onClick = controller::dismissRegistrationResult) { Text("完成") } }
    )
}
