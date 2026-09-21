package io.github.ddmoyu.picomic.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.ddmoyu.picomic.auth.passwordRecoveryPage
import io.github.ddmoyu.picomic.data.Source

internal fun openPasswordRecovery(context: Context, source: Source, htHost: String, notice: (String) -> Unit) {
    val page = passwordRecoveryPage(source, htHost) ?: return
    try {
        // Leave browser selection to Android; never attach account data or a WebView session.
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page.url.toString())).addCategory(Intent.CATEGORY_BROWSABLE))
        page.hint?.let(notice)
    } catch (_: ActivityNotFoundException) {
        notice("未找到可用浏览器，请先安装或启用浏览器")
    } catch (_: SecurityException) {
        notice("无法打开浏览器，请检查系统设置后重试")
    }
}
