package io.github.ddmoyu.picomic.source.jm

import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.network.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class JmClient internal constructor(engine: NetworkEngine, private val base: HttpUrl, allowedOrigins: Set<String>, private val now: () -> Long = { System.currentTimeMillis() / 1000 }) : PasswordAuthApi {
    internal constructor(engine: NetworkEngine, base: HttpUrl, now: () -> Long = { System.currentTimeMillis() / 1000 }) : this(engine, base, JmProtocol.apiCandidates.map { it.origin() }.toSet(), now)
    constructor(engine: NetworkEngine) : this(engine, JmProtocol.api)
    init { require(base.origin() in allowedOrigins && base.isHttps && base.port == 443 && base.encodedPath == "/" && base.query == null && base.fragment == null && base.username.isEmpty() && base.password.isEmpty() || (base.host in setOf("localhost", "127.0.0.1") && base.encodedPath == "/")) }
    private val cookies = SourceCookieJar(setOf(base.origin()))
    private val client = engine.scopedClient(setOf(base.origin()), cookies)
    private var installed = false
    fun install(candidate: SessionCandidate) {
        if (installed || candidate.kind != CredentialKind.COOKIE) throw JmProtocol.malformed()
        val data = parseObject(candidate.value.toString(Charsets.UTF_8))
        if (data.optString("profile") != JmProtocol.ID) throw JmProtocol.malformed()
        if (data.optString("origin") != base.origin()) throw ContentFailure(ContentFailureKind.EXPIRED, "JM 线路已变化，需要在当前线路恢复登录")
        JmProtocol.id(data.optString("uid"))
        val entries = data.optJSONArray("cookies") ?: throw JmProtocol.malformed()
        if (entries.length() !in 1..128) throw JmProtocol.malformed()
        val values = (0 until entries.length()).map { Cookie.parse(base, entries.getString(it)) ?: throw JmProtocol.malformed() }
        if (values.any { it.domain != base.host }) throw JmProtocol.malformed()
        cookies.saveFromResponse(base, values)
        if (cookies.loadForRequest(base).none { it.name == "AVS" }) throw ContentFailure(ContentFailureKind.EXPIRED, "JM 会话已失效，请重新登录")
        installed = true
    }
    override suspend fun probe() {
        val settings = get("setting")
        if (settings.optString("version") != JmProtocol.VERSION) throw JmProtocol.malformed()
        // API authentication does not depend on the image route. JmSource validates it before use.
    }
    override suspend fun signIn(email: String, password: CharArray): SessionCandidate {
        require(email.isNotBlank() && email.length <= 320 && password.isNotEmpty() && password.size <= 1024)
        val body = FormBody.Builder().add("username", email).add("password", String(password)).build()
        val user = request("login", body = body) as? JSONObject ?: throw JmProtocol.malformed()
        val uid = JmProtocol.id(user.optString("uid"))
        val name = user.optString("username").takeIf { it.isNotBlank() && it.length <= 200 && it.none(Char::isISOControl) } ?: throw JmProtocol.malformed()
        val saved = cookies.loadForRequest(base)
        if (saved.none { it.name == "AVS" }) throw JmProtocol.malformed()
        val value = JSONObject().put("origin", base.origin()).put("profile", JmProtocol.ID).put("uid", uid).put("name", name)
            .put("cookies", JSONArray(saved.map { cookie -> cookie.toString() })).toString().toByteArray()
        return SessionCandidate(CredentialKind.COOKIE, value)
    }
    override suspend fun profile(candidate: SessionCandidate) = validate(candidate).displayName
    suspend fun checkIn(candidate: SessionCandidate): CheckInOutcome {
        check(installed)
        val uid = JmProtocol.id(parseObject(candidate.value.toString(Charsets.UTF_8)).optString("uid"))
        val daily = get("daily", mapOf("user_id" to uid))
        val id = daily.optString("daily_id").takeIf { it.matches(Regex("[0-9]{1,12}")) } ?: throw JmProtocol.malformed()
        val result = request("daily_chk", body = FormBody.Builder().add("user_id", uid).add("daily_id", id).build()) as? JSONObject ?: throw JmProtocol.malformed()
        val message = result.optString("msg").trim().trimEnd('。', '!', '！')
        return when (message) {
            "签到成功", "簽到成功" -> CheckInOutcome.DONE
            "今日已签到", "今日已簽到", "今天已签到", "今天已簽到", "已經簽到", "已经签到" -> CheckInOutcome.ALREADY
            else -> throw ContentFailure(ContentFailureKind.PARSE, "JM 未返回可确认的签到结果，未记录成功；请查看平台账号")
        }
    }
    override suspend fun validate(candidate: SessionCandidate): ValidationResult.Verified {
        if (!installed) install(candidate)
        // Authenticated GET /login returns the account; a login POST alone is never enough.
        val user = get("login")
        val saved = parseObject(candidate.value.toString(Charsets.UTF_8))
        if (JmProtocol.id(user.optString("uid")) != saved.optString("uid")) throw JmProtocol.malformed()
        val name = user.optString("username").takeIf { it.isNotBlank() && it.length <= 200 && it.none(Char::isISOControl) } ?: throw JmProtocol.malformed()
        return ValidationResult.Verified(name, saved.optString("uid"))
    }
    suspend fun get(path: String, query: Map<String, String> = emptyMap()): JSONObject = request(path, query) as? JSONObject ?: throw JmProtocol.malformed()
    suspend fun html(path: String, query: Map<String, String>): String = raw(path, query, null).second.toString(Charsets.UTF_8)
    private suspend fun request(path: String, query: Map<String, String> = emptyMap(), body: RequestBody? = null): Any {
        val (time, bytes) = raw(path, query, body)
        val envelope = parseObject(bytes.toString(Charsets.UTF_8))
        when (envelope.optInt("code")) {
            401 -> throw ContentFailure(if (body != null && !installed) ContentFailureKind.LOGIN else ContentFailureKind.EXPIRED, "JM 登录未通过或会话已失效")
            200 -> Unit
            else -> throw JmProtocol.malformed()
        }
        val encoded = envelope.opt("data") as? String ?: throw JmProtocol.malformed()
        return try { JSONTokener(JmProtocol.decode(time, encoded)).nextValue() } catch (error: ContentFailure) { throw error } catch (_: Exception) { throw JmProtocol.malformed() }
    }
    private suspend fun raw(path: String, query: Map<String, String>, body: RequestBody?): Pair<Long, ByteArray> {
        val time = now()
        val url = base.newBuilder().addPathSegments(path).apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val request = Request.Builder().url(url).header("token", JmProtocol.token(time)).header("tokenparam", "$time,${JmProtocol.VERSION}")
            .header("User-Agent", JmProtocol.USER_AGENT).method(if (body == null) "GET" else "POST", body).build()
        val bytes = client.newCall(request).readBounded { response ->
            when (response.code) {
                200 -> Unit
                401 -> throw ContentFailure(if (body != null && !installed) ContentFailureKind.LOGIN else ContentFailureKind.EXPIRED, "JM 登录未通过或会话已失效")
                429 -> throw ContentFailure(ContentFailureKind.LIMIT, "JM 请求过于频繁，请稍后重试")
                in 300..399 -> throw ContentFailure(ContentFailureKind.NETWORK, "JM 线路发生跳转，已停止请求")
                else -> throw ContentFailure(ContentFailureKind.NETWORK, "JM 服务暂时不可用，请稍后重试")
            }
        }
        return time to bytes
    }
    private fun parseObject(value: String) = try { JSONObject(value) } catch (_: Exception) { throw JmProtocol.malformed() }
}
