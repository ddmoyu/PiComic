package io.github.ddmoyu.picomic.auth

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import io.github.ddmoyu.picomic.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Built-in, source-verified contracts only. No platform contract is enabled until live validation. */
data class WebCookieScope(val name: String, val path: String, val domain: String? = null, val url: HttpUrl? = null)
class WebLoginSpec(val startUrl: HttpUrl, val allowedOrigins: Set<String>,
                   val cookieUrl: HttpUrl, val cookieScopes: List<WebCookieScope>, val anyCookie: Boolean = false, val optionalCookies: Set<String> = emptySet(), val userAgent: String? = null) {
    val acceptedCookies = cookieScopes.map { it.name }.toSet()
    val requiredCookies = acceptedCookies - optionalCookies
    init {
        require(startUrl.isHttps && cookieUrl.isHttps)
        require(startUrl.username.isEmpty() && startUrl.password.isEmpty() && cookieUrl.username.isEmpty() && cookieUrl.password.isEmpty())
        require(startUrl.origin() in allowedOrigins && cookieUrl.origin() in allowedOrigins)
        require(allowedOrigins.all { origin -> origin.toHttpUrlOrNull()?.let { it.isHttps && it.origin() == origin } == true })
        require(requiredCookies.isNotEmpty() && requiredCookies.all { it.matches(Regex("[a-zA-Z0-9_-]+")) })
        require(optionalCookies.all { it in acceptedCookies && it.matches(Regex("[a-zA-Z0-9_-]+")) })
        require(cookieScopes.all { cookie ->
            (cookie.url == null || allows(cookie.url.toString())) &&
            cookie.path.startsWith('/') && cookie.path.none { it.isISOControl() || it == ';' } &&
                (cookie.domain == null || cookie.domain.matches(Regex("[a-zA-Z0-9.-]+")) &&
                    ((cookie.url ?: cookieUrl).host == cookie.domain.removePrefix(".") || (cookie.url ?: cookieUrl).host.endsWith(".${cookie.domain.removePrefix(".")}")))
        })
    }
    fun allows(url: String?) = url?.toHttpUrlOrNull()?.let { it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.origin() in allowedOrigins } == true
    fun candidate(raw: String?): SessionCandidate? {
        if (raw == null || raw.length > 64 * 1024) return null
        val values = raw.orEmpty().split(';').mapNotNull {
            val split = it.trim().split('=', limit = 2)
            if (split.size == 2 && split[0] in acceptedCookies && split[1].isNotBlank() && split[1].none(Char::isISOControl)) split[0] to split[1] else null
        }.toMap()
        if (if (anyCookie) values.isEmpty() else !values.keys.containsAll(requiredCookies)) return null
        val bytes = values.keys.sorted().joinToString("; ") { "$it=${values.getValue(it)}" }.toByteArray()
        return if (bytes.size <= 48 * 1024) SessionCandidate(CredentialKind.COOKIE, bytes) else null
    }
}

/** Serializes the process-wide WebView proxy. No page is loaded before the override callback. */
object WebLoginRuntime {
    private val lease = Mutex()
    /** Recovery pages share the proxy lease but never extract or replace app credentials. */
    suspend fun <T> withPage(network: NetworkEngine, error: (String) -> Unit,
                            block: suspend (() -> Boolean) -> T): T = lease.withLock {
        withContext(Dispatchers.Main.immediate) {
            val snapshot = network.status
            try {
                withContext(NonCancellable) { applyWebProxy(snapshot.profile) }
                ensureActive()
                check(snapshot.generation == network.status.generation) { "网络设置已更改，请重新打开页面" }
                coroutineScope {
                    val pageScope = this
                    val watcher = launch {
                        network.generations.first { it != snapshot.generation }
                        error("网络设置已更改，请重新加载页面")
                        pageScope.cancel("网络设置已更改")
                    }
                    try { block { snapshot.generation == network.status.generation } } finally { watcher.cancel() }
                }
            } finally {
                withContext(NonCancellable) { applyWebProxy(NetworkProfile.FollowSystem) }
            }
        }
    }
    suspend fun <T> withLogin(context: Context, spec: WebLoginSpec, network: NetworkEngine,
                              isCurrent: () -> Boolean, candidate: (SessionCandidate) -> Unit,
                              error: (String) -> Unit, block: suspend (ControlledLoginView) -> T): T = lease.withLock {
        withContext(Dispatchers.Main.immediate) {
            val snapshot = network.status
            var view: ControlledLoginView? = null
            try {
                // Complete a pending process-wide change even when cancellation races its callback.
                withContext(NonCancellable) { applyWebProxy(snapshot.profile); clearCookies(spec) }
                ensureActive()
                check(isCurrent() && snapshot.generation == network.status.generation) { "登录已失效" }
                val controlled = ControlledLoginView(context, spec, { isCurrent() && network.status.generation == snapshot.generation }, candidate, error)
                view = controlled
                coroutineScope {
                    val loginScope = this
                    val watcher = launch {
                        network.generations.first { it != snapshot.generation }
                        error("网络设置已更改，请重新登录")
                        loginScope.cancel("网络设置已更改")
                    }
                    try { block(controlled) } finally { watcher.cancel() }
                }
            } finally {
                view?.dispose()
                withContext(NonCancellable) { clearCookies(spec); applyWebProxy(NetworkProfile.FollowSystem) }
            }
        }
    }

    private suspend fun clearCookies(spec: WebLoginSpec) {
        val manager = CookieManager.getInstance()
        spec.cookieScopes.forEach { cookie ->
            val domain = cookie.domain?.let { "; Domain=$it" }.orEmpty()
            suspendCoroutine { continuation ->
                manager.setCookie((cookie.url ?: spec.cookieUrl).toString(), "${cookie.name}=; Max-Age=0; Path=${cookie.path}$domain; Secure") {
                    continuation.resume(Unit)
                }
            }
        }
        withContext(Dispatchers.IO) { manager.flush() }
        WebStorage.getInstance().deleteOrigin(spec.startUrl.origin())
        WebStorage.getInstance().deleteOrigin(spec.cookieUrl.origin())
    }

    internal suspend fun applyWebProxy(profile: NetworkProfile) {
        // Android's HTTP-auth callback does not reliably distinguish origin vs proxy challenges.
        check(profile !is NetworkProfile.HttpProxy || profile.credentials == null) {
            "网页登录暂不支持带认证的 HTTP 代理，请使用系统 VPN / 代理"
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            val controller = ProxyController.getInstance()
            suspendCoroutine { continuation ->
                val executor = java.util.concurrent.Executor { it.run() }
                val done = Runnable { continuation.resume(Unit) }
                if (profile is NetworkProfile.HttpProxy) {
                    val config = ProxyConfig.Builder().addProxyRule("http://${profile.authority()}")
                        .removeImplicitRules().build()
                    controller.setProxyOverride(config, executor, done)
                } else controller.clearProxyOverride(executor, done)
            }
        } else check(profile == NetworkProfile.FollowSystem) { "当前 WebView 不支持自定义代理，请使用系统 VPN / 代理" }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor") // Trusted site JS; programmatic construction requires a verified contract.
class ControlledLoginView internal constructor(
    context: Context, private val spec: WebLoginSpec, private val isCurrent: () -> Boolean,
    private val candidate: (SessionCandidate) -> Unit, private val report: (String) -> Unit
) : WebView(context) {
    private var disposed = false
    private var lastCandidate: ByteArray? = null
    private var pollsRemaining = 0
    private val detection = object : Runnable {
        override fun run() {
            if (disposed || !isCurrent() || pollsRemaining-- <= 0) return
            checkSession()
            postDelayed(this, 1000)
        }
    }
    init {
        settings.javaScriptEnabled = true
        spec.userAgent?.let { settings.userAgentString = it }
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !isCurrent() || !spec.allows(request.url.toString())
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (isCurrent() && spec.allows(request.url.toString())) null
                else WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (!isCurrent() || !spec.allows(url)) { stopLoading(); report("登录页面已失效或跳转地址不受信任") }
            }
            override fun onPageFinished(view: WebView, url: String?) {
                if (spec.allows(url)) { checkSession(); removeCallbacks(detection); pollsRemaining = 30; postDelayed(detection, 1000) }
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) report("登录页面加载失败，请检查网络后重试")
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel(); report("登录页面证书验证失败")
            }
            override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
                handler.cancel(); report("网页认证未完成，请检查代理或平台登录方式")
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                dispose(); report("登录页面进程已退出，请重新打开"); return true
            }
        }
    }
    fun open() { check(!disposed && isCurrent()); loadUrl(spec.startUrl.toString()) }
    /** Page completion is only a detection trigger; the coordinator must still validate the candidate. */
    fun checkSession() {
        if (disposed || !isCurrent() || !spec.allows(url)) return
        val value = spec.candidate(CookieManager.getInstance().getCookie(spec.cookieUrl.toString())) ?: return
        if (lastCandidate?.contentEquals(value.value) == true) { value.value.fill(0); return }
        lastCandidate?.fill(0)
        lastCandidate = value.value.copyOf()
        candidate(value)
    }
    fun retryDetection() { lastCandidate?.fill(0); lastCandidate = null; checkSession() }
    fun dispose() {
        if (disposed) return
        disposed = true
        removeCallbacks(detection)
        lastCandidate?.fill(0); lastCandidate = null
        stopLoading()
        (parent as? android.view.ViewGroup)?.removeView(this)
        destroy()
    }
}
