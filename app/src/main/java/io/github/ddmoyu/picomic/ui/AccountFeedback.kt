package io.github.ddmoyu.picomic.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ddmoyu.picomic.auth.AccountState
import io.github.ddmoyu.picomic.auth.AccountStatus

internal fun AccountState.label(): String = when (status) {
    AccountStatus.ANONYMOUS -> "未登录"
    AccountStatus.AUTHENTICATING -> "正在验证账号"
    AccountStatus.AUTHENTICATED -> "已登录 · ${displayName?.takeIf(String::isNotBlank) ?: "账号已验证"}"
    AccountStatus.NEEDS_VALIDATION -> "会话待验证"
    AccountStatus.EXPIRED -> "登录已失效，请重新登录"
}

@Composable internal fun AccountStatusCard(account: AccountState) {
    val authenticated = account.status == AccountStatus.AUTHENTICATED
    val expired = account.status == AccountStatus.EXPIRED
    Surface(Modifier.fillMaxWidth().testTag("account-status").semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = when { authenticated -> MaterialTheme.colorScheme.primaryContainer; expired -> MaterialTheme.colorScheme.errorContainer; else -> MaterialTheme.colorScheme.surfaceContainerHigh },
        contentColor = when { authenticated -> MaterialTheme.colorScheme.onPrimaryContainer; expired -> MaterialTheme.colorScheme.onErrorContainer; else -> MaterialTheme.colorScheme.onSurface }) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(if (authenticated) Glyph.Check else if (expired) Glyph.Info else Glyph.User, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(account.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(when (account.status) {
                    AccountStatus.AUTHENTICATED -> "会话已加密保存，下次打开可继续使用"
                    AccountStatus.AUTHENTICATING -> "正在确认登录状态，请稍候"
                    AccountStatus.NEEDS_VALIDATION -> "已保存账号，联网验证后可继续使用"
                    AccountStatus.EXPIRED -> "请重新登录以恢复账号访问"
                    AccountStatus.ANONYMOUS -> "登录成功后会在这里显示账号信息"
                }, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Explicit login/import only; restoring a stored session never creates this acknowledgement. */
@Composable internal fun LoginSuccessFeedback(show: Boolean, title: String, account: AccountState, scroll: ScrollState, dismiss: () -> Unit) {
    if (!show || account.status != AccountStatus.AUTHENTICATED) return
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.clearFocus(); keyboard?.hide(); scroll.scrollTo(0) }
    AlertDialog(onDismissRequest = dismiss, modifier = Modifier.testTag("login-success-dialog"),
        icon = { AppIcon(Glyph.Check, modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary) },
        title = { Text("登录成功") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(account.label(), fontWeight = FontWeight.Bold)
            Text("会话已加密保存，可以返回浏览和阅读。")
        } },
        confirmButton = { Button(onClick = dismiss) { Text("知道了") } })
}

@Composable internal fun AccountSettingRow(account: AccountState, subtitle: String, onClick: () -> Unit) {
    val authenticated = account.status == AccountStatus.AUTHENTICATED
    SettingRow(account.label(), subtitle, if (authenticated) Glyph.Check else Glyph.User, onClick = onClick,
        trailing = { if (authenticated) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.small) {
                Text("已登录", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
        } else Text("管理", color = MaterialTheme.colorScheme.primary) })
}
