package io.github.ddmoyu.picomic.source.ht

import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.network.*
import io.github.ddmoyu.picomic.source.html.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document

class HtClient internal constructor(private val engine: NetworkEngine, val base: HttpUrl, trusted: Set<String>) : PasswordAuthApi {
    constructor(engine: NetworkEngine) : this(engine, BASE, setOf(BASE.origin()))
    init { require(base.origin() in trusted && base.encodedPath == "/" && (base.isHttps || base.host in setOf("localhost", "127.0.0.1"))) }
    private val jar = SourceCookieJar(setOf(base.origin()))
    private val client = engine.scopedClient(setOf(base.origin()), jar)
    private var authenticated = false
    suspend fun text(url: HttpUrl) = request(url).toString(Charsets.UTF_8)
    suspend fun document(url: HttpUrl): Document = parseHtml(request(url), url, TITLE).also {
        if (it.selectFirst("#login_form") != null) throw ContentFailure(if (authenticated) ContentFailureKind.EXPIRED else ContentFailureKind.LOGIN, "绅士漫画需要登录后访问")
    }
    private suspend fun request(url: HttpUrl, body: RequestBody? = null): ByteArray {
        val builder = Request.Builder().url(url).header("User-Agent", BROWSER_AGENT).header("Referer", base.toString())
        if (body != null) builder.post(body).header("X-Requested-With", "XMLHttpRequest")
        return client.newCall(builder.build()).readBounded { response ->
            when (response.code) {
                200 -> Unit
                401 -> throw ContentFailure(if (authenticated) ContentFailureKind.EXPIRED else ContentFailureKind.LOGIN, "绅士漫画会话需要重新验证")
                403 -> throw ContentFailure(ContentFailureKind.LOGIN, "绅士漫画拒绝访问，请检查账号权限或网页验证")
                404 -> throw ContentFailure(ContentFailureKind.NOT_FOUND, "该作品或页面不存在")
                429 -> throw ContentFailure(ContentFailureKind.LIMIT, "请求过于频繁，请稍后重试")
                else -> throw ContentFailure(ContentFailureKind.NETWORK, "绅士漫画线路不可用或发生跳转，请重新验证域名")
            }
        }
    }
    override suspend fun probe() {
        val page = document(base.newBuilder().addPathSegment("search").addPathSegment("").addQueryParameter("q", "PiComic_connection_test_no_result").build())
        if (page.selectFirst(".gallary_wrap") == null || page.selectFirst("form input[name=q], .search-query") == null) throw parseChanged(TITLE)
    }
    override suspend fun signIn(username: String, password: CharArray): SessionCandidate {
        val bytes = request(base.resolve("users-check_login.html")!!, FormBody.Builder().add("login_name", username).add("login_pass", String(password)).add("remember_pass", "1").build())
        val response = try { JSONObject(bytes.toString(Charsets.UTF_8)) } catch (_: Exception) { throw parseChanged(TITLE) }
        if (response.opt("ret") != true) throw ContentFailure(ContentFailureKind.LOGIN, "绅士漫画登录未通过，请检查账号密码或网页验证")
        val cookies = jar.loadForRequest(base)
        if (cookies.isEmpty()) throw parseChanged(TITLE)
        val value = JSONObject().put("origin", base.origin()).put("name", username).put("cookies", JSONArray(cookies.map { it.toString() })).toString()
        return SessionCandidate(CredentialKind.COOKIE, value.toByteArray())
    }
    fun install(candidate: SessionCandidate): String {
        if (candidate.kind != CredentialKind.COOKIE) throw parseChanged(TITLE)
        val data = try { JSONObject(candidate.value.toString(Charsets.UTF_8)) } catch (_: Exception) { throw parseChanged(TITLE) }
        if (data.optString("origin") != base.origin()) throw ContentFailure(ContentFailureKind.LOGIN, "域名已更改，请在当前域名重新登录；原会话仍保留")
        val name = data.optString("name").takeIf { it.isNotBlank() && it.length <= 200 && it.none(Char::isISOControl) } ?: throw parseChanged(TITLE)
        val cookies = data.optJSONArray("cookies") ?: throw parseChanged(TITLE)
        if (cookies.length() !in 1..64) throw parseChanged(TITLE)
        jar.clear()
        jar.saveFromResponse(base, (0 until cookies.length()).map { Cookie.parse(base, cookies.getString(it)) ?: throw parseChanged(TITLE) })
        if (jar.loadForRequest(base).isEmpty()) throw parseChanged(TITLE)
        authenticated = true
        return name
    }
    override suspend fun profile(candidate: SessionCandidate) = validate(candidate).displayName
    override suspend fun validate(candidate: SessionCandidate): ValidationResult.Verified {
        val name = install(candidate)
        val page = document(base.resolve("users-users_fav.html")!!)
        // A success code or cookie alone is insufficient: require the protected favorites UI and logout action.
        if (page.selectFirst(".fav_nav") == null || page.selectFirst("a[href*=users-logout]") == null) throw ContentFailure(ContentFailureKind.LOGIN, "未确认绅士漫画账号状态，请重新登录")
        return ValidationResult.Verified(name, name.lowercase(java.util.Locale.ROOT))
    }
    suspend fun image(url: HttpUrl): ByteArray {
        validateImage(url)
        return engine.newCall(Request.Builder().url(url).header("Referer", base.toString()).header("User-Agent", BROWSER_AGENT).build()).readBounded(32 * 1024 * 1024) {
            if (it.code != 200 || it.header("Content-Type").orEmpty().substringBefore(';').let { type -> type.isNotEmpty() && !type.startsWith("image/") && type != "application/octet-stream" }) throw ContentFailure(ContentFailureKind.NETWORK, "绅士漫画图片暂时不可用")
        }
    }
    companion object {
        val BASE = "https://www.wnacg.com/".toHttpUrl()
        const val TITLE = "绅士漫画"
        fun validateImage(url: HttpUrl) {
            if (!url.isHttps || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null ||
                !(url.host.endsWith(".wnacg.com") || url.host.matches(Regex("[a-z0-9-]+\\.(wnacg\\.(org|me|ru|xyz)|wnacgdata\\.(com|org)|wnacgimg\\.(com|xyz)|wnimg[0-9]+\\.cfd)")))) throw parseChanged(TITLE)
        }
    }
}
