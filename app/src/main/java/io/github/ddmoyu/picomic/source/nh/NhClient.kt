package io.github.ddmoyu.picomic.source.nh

import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.network.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

class NhClient internal constructor(engine: NetworkEngine, private val base: HttpUrl, private val candidate: SessionCandidate? = null) {
    constructor(engine: NetworkEngine, candidate: SessionCandidate? = null) : this(engine, API, candidate)
    init { require(base == API || (base.host in setOf("localhost", "127.0.0.1") && base.encodedPath == "/")) }
    private val client = engine.scopedClient(setOf(base.origin()))
    suspend fun get(path: List<String>, query: Map<String, String> = emptyMap()): JSONObject {
        val url = base.newBuilder().apply { path.forEach(::addPathSegment); query.forEach { (name, value) -> addQueryParameter(name, value) } }.build()
        val request = Request.Builder().url(url).header("Accept", "application/json").header("User-Agent", "PiComic/0.3.0 (+https://github.com/ddmoyu/PiComic)")
        candidate?.let {
            val value = it.value.toString(Charsets.UTF_8)
            if (value.length !in 1..16384 || value.any { char -> char.code !in 33..126 }) throw malformed()
            val scheme = when (it.kind) { CredentialKind.API_KEY -> "Key"; CredentialKind.USER_TOKEN -> "User"; else -> throw malformed() }
            request.header("Authorization", "$scheme $value")
        }
        val bytes = client.newCall(request.build()).readBounded { response ->
            when (response.code) {
                200 -> Unit
                401 -> throw ContentFailure(if (candidate == null) ContentFailureKind.LOGIN else ContentFailureKind.EXPIRED, "nhentai 需要验证账号或 API Key")
                403 -> throw ContentFailure(ContentFailureKind.LOGIN, "nhentai 拒绝访问，可能需要网页验证或更高权限")
                404 -> throw ContentFailure(ContentFailureKind.NOT_FOUND, "nhentai 内容已删除或不可访问")
                429 -> throw ContentFailure(ContentFailureKind.LIMIT, "nhentai 请求过于频繁，请稍后重试")
                in 300..399 -> throw ContentFailure(ContentFailureKind.NETWORK, "nhentai 请求发生跳转，已停止转发凭据")
                else -> throw ContentFailure(ContentFailureKind.NETWORK, "nhentai 服务暂时不可用")
            }
        }
        return try { JSONObject(bytes.toString(Charsets.UTF_8)) } catch (_: Exception) { throw malformed() }
    }
    suspend fun validate(): ValidationResult.Verified {
        require(candidate != null)
        val data = get(listOf("user"))
        val id = id(data.optString("id"))
        val name = data.optString("username").takeIf { it.isNotBlank() && it.length <= 200 && it.none(Char::isISOControl) } ?: throw malformed()
        return ValidationResult.Verified(name, id)
    }
    companion object {
        val API = "https://nhentai.net/api/v2/".toHttpUrl()
        fun malformed() = ContentFailure(ContentFailureKind.PARSE, "nhentai 响应结构发生变化，请等待来源更新")
        fun id(value: String) = value.takeIf { it.matches(Regex("[1-9][0-9]{0,11}")) } ?: throw malformed()
    }
}
