package io.github.ddmoyu.picomic.source.picacg

import io.github.ddmoyu.picomic.auth.CredentialKind
import io.github.ddmoyu.picomic.auth.SessionCandidate
import io.github.ddmoyu.picomic.network.NetworkEngine
import io.github.ddmoyu.picomic.network.origin
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


enum class PicacgFailureKind { CREDENTIALS, EXPIRED, ACCESS_DENIED, RATE_LIMITED, REDIRECT, RESPONSE, SERVER }
class PicacgFailure(val kind: PicacgFailureKind, val retryAfterSeconds: Long? = null) : IOException(when (kind) {
    PicacgFailureKind.CREDENTIALS -> "登录未通过，请检查账号和密码"
    PicacgFailureKind.EXPIRED -> "会话已失效，请重新登录"
    PicacgFailureKind.ACCESS_DENIED -> "平台拒绝访问或要求额外验证，请稍后重试"
    PicacgFailureKind.RATE_LIMITED -> retryAfterSeconds?.let { "请求过于频繁，请在 $it 秒后重试" } ?: "请求过于频繁，请稍后重试"
    PicacgFailureKind.REDIRECT -> "平台地址发生变化，已停止请求，请等待来源更新"
    PicacgFailureKind.RESPONSE -> "平台响应格式不符合预期，请稍后重试"
    PicacgFailureKind.SERVER -> "平台服务暂时不可用，请稍后重试"
})

/** Fixed trusted API origin. A client is bound to one network generation for the entire login. */
class PicacgClient internal constructor(engine: NetworkEngine, private val base: HttpUrl) : PicacgAuthApi {
    constructor(engine: NetworkEngine) : this(engine, PicacgProtocol.api)
    init {
        // Loopback is available only through the internal constructor for fixture tests.
        require(base == PicacgProtocol.api || (base.host in setOf("localhost", "127.0.0.1") && base.encodedPath == "/"))
        require(base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null)
    }
    private val client = engine.scopedClient(setOf(base.origin()))

    override suspend fun probe() {
        val reply = execute(request("users/profile", "GET"), allowAnonymous = true)
        if (reply.code != 401 || reply.json.optInt("code") != 401 || reply.json.optString("error") != "1005" ||
            reply.json.optString("message") != "unauthorized") throw PicacgFailure(PicacgFailureKind.RESPONSE)
    }

    override suspend fun signIn(email: String, password: CharArray): SessionCandidate {
        require(email.isNotBlank() && email.length <= 320 && password.isNotEmpty() && password.size <= 1024)
        val bytes = JSONObject().put("email", email).put("password", String(password)).toString().toByteArray(Charsets.UTF_8)
        try {
            val body = bytes.toRequestBody("application/json; charset=utf-8".toMediaType())
            val result = execute(request("auth/sign-in", "POST", body), signingIn = true).json
            val token = result.optJSONObject("data")?.opt("token") as? String ?: throw PicacgFailure(PicacgFailureKind.RESPONSE)
            if (!validToken(token)) throw PicacgFailure(PicacgFailureKind.RESPONSE)
            return SessionCandidate(CredentialKind.USER_TOKEN, token.toByteArray(Charsets.UTF_8))
        } finally { bytes.fill(0) }
    }

    override suspend fun profile(candidate: SessionCandidate) = validate(candidate).displayName
    suspend fun checkIn(candidate: SessionCandidate): io.github.ddmoyu.picomic.auth.CheckInOutcome {
        require(candidate.kind == CredentialKind.USER_TOKEN)
        val token = candidate.value.toString(Charsets.UTF_8)
        fun punched(user: JSONObject): Boolean {
            val id = user.optString("_id")
            if (id.isBlank() || candidate.accountId?.let { it != id } == true) throw PicacgFailure(PicacgFailureKind.EXPIRED)
            return user.opt("isPunched") as? Boolean ?: throw PicacgFailure(PicacgFailureKind.RESPONSE)
        }
        suspend fun user() = execute(request("users/profile", "GET", token = token)).json.optJSONObject("data")?.optJSONObject("user") ?: throw PicacgFailure(PicacgFailureKind.RESPONSE)
        if (punched(user())) return io.github.ddmoyu.picomic.auth.CheckInOutcome.ALREADY
        execute(request("users/punch-in", "POST", ByteArray(0).toRequestBody("application/json".toMediaType()), token))
        if (!punched(user())) throw PicacgFailure(PicacgFailureKind.RESPONSE)
        return io.github.ddmoyu.picomic.auth.CheckInOutcome.DONE
    }
    override suspend fun validate(candidate: SessionCandidate): io.github.ddmoyu.picomic.auth.ValidationResult.Verified {
        if (candidate.kind != CredentialKind.USER_TOKEN) throw PicacgFailure(PicacgFailureKind.RESPONSE)
        val token = candidate.value.toString(Charsets.UTF_8)
        if (!validToken(token)) throw PicacgFailure(PicacgFailureKind.RESPONSE)
        val user = execute(request("users/profile", "GET", token = token)).json.optJSONObject("data")?.optJSONObject("user")
            ?: throw PicacgFailure(PicacgFailureKind.RESPONSE)
        val id = (user.opt("_id") as? String)?.trim().orEmpty()
        val name = (user.opt("name") as? String)?.trim().orEmpty()
        if (id.isEmpty() || id == "null" || name.isEmpty() || name == "null" || name.length > 200 || name.any { it.isISOControl() })
            throw PicacgFailure(PicacgFailureKind.RESPONSE)
        fun mediaUrl(value: String?): String? {
            val url = value?.toHttpUrlOrNull() ?: return null
            return url.takeIf { it.isHttps && it.port == 443 && it.username.isEmpty() && it.password.isEmpty() && it.fragment == null &&
                (it.host == "picacomic.com" || it.host.endsWith(".picacomic.com") || it.host == "picacg.com" || it.host.endsWith(".picacg.com")) }?.toString()
        }
        val avatar = user.optJSONObject("avatar")?.let { item ->
            val path = item.optString("path")
            if (path.isBlank() || path.startsWith('/') || path.contains('\\') || path.contains('%') || path.split('/').any { it in setOf(".", "..") }) null
            else mediaUrl(item.optString("fileServer").trimEnd('/') + "/static/" + path)
        }
        val profile = io.github.ddmoyu.picomic.auth.AccountProfile(avatar, mediaUrl(user.opt("character") as? String),
            (user.opt("level") as? Int)?.takeIf { it in 0..100000 }, (user.opt("title") as? String)?.take(200)?.takeIf { it.none(Char::isISOControl) })
        return io.github.ddmoyu.picomic.auth.ValidationResult.Verified(name, id, profile)
    }

    internal suspend fun content(path: List<String>, query: Map<String, String>, token: SessionCandidate, body: JSONObject? = null): JSONObject {
        require(token.kind == CredentialKind.USER_TOKEN)
        val value = token.value.toString(Charsets.UTF_8)
        if (!validToken(value)) throw PicacgFailure(PicacgFailureKind.RESPONSE)
        val url = base.newBuilder().apply { path.forEach(::addPathSegment); query.forEach { (name, value) -> addQueryParameter(name, value) } }.build()
        val method = if (body == null) "GET" else "POST"
        val request = PicacgProtocol.headers(Request.Builder().url(url).method(method, body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())), url, method, value).build()
        return execute(request).json.optJSONObject("data") ?: throw PicacgFailure(PicacgFailureKind.RESPONSE)
    }

    private fun request(path: String, method: String, body: RequestBody? = null, token: String? = null): Request {
        val url = base.newBuilder().addPathSegments(path).build()
        return PicacgProtocol.headers(Request.Builder().url(url).method(method, body), url, method, token).build()
    }
    private class Reply(val code: Int, val json: JSONObject)

    // Keep cancellation attached until the bounded body is fully read, including a slow response body.
    private suspend fun execute(request: Request, allowAnonymous: Boolean = false, signingIn: Boolean = false): Reply =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val reply = response.use {
                            val code = it.code
                            val retry = it.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 86400)
                            when {
                                code in 300..399 -> throw PicacgFailure(PicacgFailureKind.REDIRECT)
                                code == 429 -> throw PicacgFailure(PicacgFailureKind.RATE_LIMITED, retry)
                                code == 403 -> throw PicacgFailure(PicacgFailureKind.ACCESS_DENIED)
                                code >= 500 -> throw PicacgFailure(PicacgFailureKind.SERVER)
                                signingIn && code in setOf(400, 401) -> throw PicacgFailure(PicacgFailureKind.CREDENTIALS)
                                code == 401 && !allowAnonymous -> throw PicacgFailure(PicacgFailureKind.EXPIRED)
                                code != 200 && !(allowAnonymous && code == 401) -> throw PicacgFailure(PicacgFailureKind.RESPONSE)
                            }
                            val source = it.body.source()
                            if (source.request(256 * 1024L + 1)) throw PicacgFailure(PicacgFailureKind.RESPONSE)
                            val json = try { JSONObject(source.readUtf8()) } catch (_: Exception) { throw PicacgFailure(PicacgFailureKind.RESPONSE) }
                            val business = json.optInt("code", -1)
                            if (!allowAnonymous) when {
                                business == 401 -> throw PicacgFailure(if (signingIn) PicacgFailureKind.CREDENTIALS else PicacgFailureKind.EXPIRED)
                                business == 403 -> throw PicacgFailure(PicacgFailureKind.ACCESS_DENIED)
                                business == 429 -> throw PicacgFailure(PicacgFailureKind.RATE_LIMITED, retry)
                                signingIn && business == 400 -> throw PicacgFailure(PicacgFailureKind.CREDENTIALS)
                                business != 200 || json.optString("message") != "success" -> throw PicacgFailure(PicacgFailureKind.RESPONSE)
                            }
                            Reply(code, json)
                        }
                        continuation.resume(reply)
                    } catch (error: Exception) { if (!continuation.isCancelled) continuation.resumeWithException(error) }
                }
            })
        }
    private fun validToken(token: String) = token.length in 1..16384 && token != "null" && token.all { it.code in 33..126 }
}
