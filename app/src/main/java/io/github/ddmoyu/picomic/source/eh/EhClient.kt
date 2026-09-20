package io.github.ddmoyu.picomic.source.eh

import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.network.*
import io.github.ddmoyu.picomic.source.html.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

/** EH and EX are an explicit account group. Image hosts never receive this group's cookies. */
class EhClient internal constructor(private val engine: NetworkEngine, val eh: HttpUrl, val ex: HttpUrl, private val candidate: SessionCandidate? = null, private val ignoreWarning: Boolean = false) {
    constructor(engine: NetworkEngine, candidate: SessionCandidate? = null, ignoreWarning: Boolean = false) : this(engine, EH, EX, candidate, ignoreWarning)
    init { require((eh == EH && ex == EX) || (eh.host in setOf("localhost", "127.0.0.1") && ex.host in setOf("localhost", "127.0.0.1"))) }
    private val cookies = candidate?.let(::cookieValues).orEmpty()
    val accountKey get() = cookies["ipb_member_id"] ?: "anonymous"
    private val client = engine.scopedClient(setOf(eh.origin(), ex.origin()))
    fun site(exhentai: Boolean) = if (exhentai) ex else eh
    fun request(url: HttpUrl): Request {
        if (url.origin() !in setOf(eh.origin(), ex.origin())) throw parseChanged(TITLE)
        val values = cookies.filterKeys { it != "igneous" || url.origin() == ex.origin() }.toMutableMap()
        if (ignoreWarning) values["nw"] = "1"
        return Request.Builder().url(url).header("User-Agent", BROWSER_AGENT).apply {
            if (values.isNotEmpty()) header("Cookie", values.map { "${it.key}=${it.value}" }.joinToString("; "))
        }.build()
    }
    suspend fun document(url: HttpUrl): Document {
        val exRequest = url.origin() == ex.origin()
        val bytes = client.newCall(request(url)).readBounded { response ->
            when (response.code) {
                200 -> if (response.header("Content-Type").orEmpty().startsWith("image/")) throw denied()
                401 -> throw login(exRequest)
                403 -> throw denied()
                404, 410 -> throw ContentFailure(ContentFailureKind.NOT_FOUND, "画廊已删除或不可访问")
                429 -> throw ContentFailure(ContentFailureKind.LIMIT, "EH/EX 请求过于频繁，请稍后再试")
                509 -> throw quota()
                in 300..399 -> {
                    val target = response.header("Location").orEmpty()
                    if (target.contains("bounce_login.php") || target.contains("forums.e-hentai.org")) throw login(exRequest)
                    throw ContentFailure(ContentFailureKind.NETWORK, "EH/EX 返回了未确认的跳转")
                }
                else -> throw ContentFailure(ContentFailureKind.NETWORK, "EH/EX 服务暂时不可用")
            }
        }
        if (bytes.isEmpty()) throw denied()
        val page = parseHtml(bytes, url, TITLE)
        val text = page.text()
        if (text.contains("exceeded your image", true) || text.contains("Image limit exceeded", true)) throw quota()
        if (text.contains("temporarily banned", true)) throw ContentFailure(ContentFailureKind.LIMIT, "平台暂时限制当前网络访问，请稍后重试")
        if (page.title().contains("Content Warning", true) || (text.contains("Content Warning") && text.contains("Never Warn Me Again"))) throw ContentFailure(ContentFailureKind.WARNING, "画廊含站内内容提示，可在来源设置中选择是否忽略警告")
        return page
    }
    suspend fun validate(): ValidationResult.Verified {
        if (candidate == null) throw login(false)
        val page = document(eh.resolve("uconfig.php")!!)
        if (page.select("select[name=profile_set] option").isEmpty()) throw ContentFailure(ContentFailureKind.EXPIRED, "未确认 EH 会话，请重新登录")
        val id = cookies.getValue("ipb_member_id")
        return ValidationResult.Verified("EH 账号 $id", id)
    }
    suspend fun checkExAccess() {
        val page = document(ex.resolve("uconfig.php")!!)
        if (page.select("select[name=profile_set] option").isEmpty()) throw denied()
    }
    suspend fun image(url: HttpUrl, referer: HttpUrl, original: Boolean): ByteArray {
        val own = url.origin() in setOf(eh.origin(), ex.origin())
        if (own && original && url.encodedPath == "/fullimg.php") {
            var location: String? = null
            val bytes = client.newCall(request(url)).readBounded(48 * 1024 * 1024) { response ->
                when (response.code) {
                    200 -> if (!response.header("Content-Type").orEmpty().startsWith("image/")) throw originalUnavailable()
                    301, 302, 303, 307, 308 -> { location = response.header("Location") ?: throw originalUnavailable() }
                    509 -> throw quota()
                    else -> throw originalUnavailable()
                }
            }
            return if (location != null) image(secureUrl(url, location!!, TITLE), referer, false) else bytes
        }
        validateImage(url)
        return engine.newCall(Request.Builder().url(url).header("User-Agent", BROWSER_AGENT).header("Referer", referer.toString()).build()).readBounded(48 * 1024 * 1024) { response ->
            if (response.code == 509) throw quota()
            if (response.code != 200 || !response.header("Content-Type").orEmpty().startsWith("image/")) throw ContentFailure(ContentFailureKind.NETWORK, "正文图片地址已失效，请重试以重新解析")
        }
    }
    private fun login(exRequest: Boolean) = if (exRequest) denied() else ContentFailure(if (candidate == null) ContentFailureKind.LOGIN else ContentFailureKind.EXPIRED, "EH 会话未验证或已失效，请重新登录")
    companion object {
        const val TITLE = "EH/EX"
        val EH = "https://e-hentai.org/".toHttpUrl()
        val EX = "https://exhentai.org/".toHttpUrl()
        fun denied() = ContentFailure(ContentFailureKind.ACCESS_DENIED, "EX 或当前画廊无访问权限，EH 会话仍保留；可手动切换 EH 浏览其他作品")
        fun quota() = ContentFailure(ContentFailureKind.QUOTA, "EH/EX 图片额度不足，已停止正文请求；恢复额度后可手动重试")
        fun originalUnavailable() = ContentFailure(ContentFailureKind.ORIGINAL_UNAVAILABLE, "原图暂不可用，可选择加载普通图片")
        fun cookieValues(candidate: SessionCandidate): Map<String, String> {
            if (candidate.kind != CredentialKind.COOKIE) throw parseChanged(TITLE)
            val allowed = setOf("ipb_member_id", "ipb_pass_hash", "igneous")
            val pairs = candidate.value.toString(Charsets.UTF_8).split(';').map { it.trim().split('=', limit = 2) }
            if (pairs.any { it.size != 2 || it[0] !in allowed || it[1].isEmpty() || it[1].length > 4096 || it[1].any { c -> c.code !in 33..126 || c in ";," } } || pairs.map { it[0] }.distinct().size != pairs.size) throw ContentFailure(ContentFailureKind.LOGIN, "Cookie 字段格式无效")
            val values = pairs.associate { it[0] to it[1] }
            positiveId(values["ipb_member_id"].orEmpty(), TITLE)
            if (values["ipb_pass_hash"].isNullOrEmpty()) throw ContentFailure(ContentFailureKind.LOGIN, "缺少 ipb_pass_hash")
            return values
        }
        fun validateImage(url: HttpUrl) {
            if (!url.isHttps || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null ||
                !(url.host == "ehgt.org" || url.host.endsWith(".ehgt.org") || url.host.endsWith(".hath.network") || url.host == "exhentai.org")) throw parseChanged(TITLE)
            if (url.encodedPath.contains("509.gif") || url.encodedPath.contains("509s.gif")) throw quota()
        }
    }
}
