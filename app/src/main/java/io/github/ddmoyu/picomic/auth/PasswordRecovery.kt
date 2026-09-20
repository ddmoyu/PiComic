package io.github.ddmoyu.picomic.auth

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.source.ht.HtRoutes
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayInputStream

internal data class RecoveryPage(val url: HttpUrl, val hosts: Set<String>, val hint: String? = null) {
    init { require(url.isHttps && url.port == 443 && url.host in hosts && url.username.isEmpty() && url.password.isEmpty()) }
    fun allows(value: String?) = value?.toHttpUrlOrNull()?.let {
        it.isHttps && it.port == 443 && it.host in hosts && it.username.isEmpty() && it.password.isEmpty()
    } == true
}

internal fun passwordRecoveryPage(source: Source, htHost: String = "www.wn10.shop"): RecoveryPage? = when (source) {
    // No verified independent reset URL: use the platform's account entry instead of guessing one.
    Source.PICACG -> RecoveryPage("https://manhuabika.com/plogin/".toHttpUrl(), setOf("manhuabika.com", "manhuapica.com"),
        "若哔咔网页未提供找回密码选项，请使用官方客户端找回。")
    Source.JMCOMIC -> RecoveryPage("https://18comic.vip/login".toHttpUrl(), setOf("18comic.vip", "www.18comic.vip"),
        "请在平台账号页面选择「忘记密码」，并按网页提示完成验证。")
    Source.EHENTAI -> RecoveryPage("https://forums.e-hentai.org/index.php?act=Reg&CODE=10".toHttpUrl(), setOf("forums.e-hentai.org"))
    Source.NHENTAI -> RecoveryPage("https://nhentai.net/reset-password/".toHttpUrl(), setOf("nhentai.net"))
    Source.HTCOMIC -> {
        require(HtRoutes.validHost(htHost))
        RecoveryPage("https://$htHost/?ctl=users&act=getpass".toHttpUrl(), setOf(htHost))
    }
    Source.HITOMI -> null
}

/** A browser-only recovery flow. No native credentials, cookie extraction or Javascript bridge. */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
internal class PasswordRecoveryView(
    context: Context, private val page: RecoveryPage, private val isCurrent: () -> Boolean,
    private val report: (String) -> Unit, private val progress: (Int) -> Unit,
    private val navigated: () -> Unit,
    private val pageResponse: (WebResourceRequest) -> WebResourceResponse? = { null },
) : WebView(context) {
    private var disposed = false
    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { if (!disposed) progress(newProgress) }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val allowed = isCurrent() && if (request.isForMainFrame) page.allows(request.url.toString()) else https(request.url.toString())
                if (!allowed && request.isForMainFrame) report("此链接不属于当前平台的账号页面")
                return !allowed
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (isCurrent() && https(request.url.toString()) && (!request.isForMainFrame || page.allows(request.url.toString()))) pageResponse(request)
                else WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (!isCurrent() || !page.allows(url)) { stopLoading(); report("页面地址已失效，请重新打开") }
                else { progress(0); navigated() }
            }
            override fun onPageFinished(view: WebView, url: String?) { if (!disposed) { progress(100); navigated() } }
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) { if (!disposed) navigated() }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) { progress(100); report("页面加载失败，请检查网络后重试") }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) report(if (response.statusCode in setOf(403, 429)) "平台限制了请求；如有网页验证，请完成验证，或稍后重试" else "页面暂不可用（HTTP ${response.statusCode}），请稍后重试")
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel(); progress(100); report("页面证书验证失败，请稍后重试")
            }
            override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
                handler.cancel(); report("网页认证未完成，请检查代理设置")
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                dispose(); report("页面进程已退出，请重新加载"); return true
            }
        }
    }
    fun open() { check(!disposed && isCurrent()); loadUrl(page.url.toString()) }
    fun back(): Boolean = !disposed && canGoBack() && run { goBack(); true }
    fun hasHistory() = !disposed && canGoBack()
    fun dispose() {
        if (disposed) return
        disposed = true
        stopLoading()
        (parent as? android.view.ViewGroup)?.removeView(this)
        destroy()
    }
    private fun https(value: String) = value.toHttpUrlOrNull()?.let { it.isHttps && it.username.isEmpty() && it.password.isEmpty() } == true
}
