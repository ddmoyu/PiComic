package io.github.ddmoyu.picomic.backup

import io.github.ddmoyu.picomic.network.*
import kotlinx.coroutines.CancellationException
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.util.UUID

enum class WebDavError { AUTH, DENIED, NOT_FOUND, CONFLICT, PROTOCOL, NETWORK }
class WebDavFailure(val kind: WebDavError, message: String) : IOException(message)
data class WebDavTest(val writable: Boolean, val message: String)
data class RemoteBackup(val document: BackupDocument?, val etag: String?)

class WebDavClient internal constructor(private val engine: NetworkEngine, private val base: HttpUrl, private val authorization: String) {
    private val generation = engine.status.generation
    private val calls = engine.scopedClient(setOf(base.origin()))
    private val resource = base.newBuilder().addPathSegment("picomic-user-data.json").build()
    private data class Reply(val code: Int, val etag: String?, val bytes: ByteArray)
    private suspend fun request(url: HttpUrl, method: String, body: ByteArray? = null, headers: Map<String, String> = emptyMap(), limit: Long = BackupCodec.MAX_BYTES.toLong()): Reply {
        require(url.origin() == base.origin() && url.encodedPath.startsWith(base.encodedPath))
        engine.withGeneration(generation) { }
        val request = Request.Builder().url(url).header("Authorization", authorization).header("User-Agent", "PiComic").apply {
            headers.forEach { (name, value) -> header(name, value) }
            method(method, body?.toRequestBody((if (method == "PROPFIND") "application/xml; charset=utf-8" else "application/json; charset=utf-8").toMediaType()))
        }.build()
        var code = 0; var etag: String? = null
        val bytes = calls.newCall(request).readBounded(limit) { code = it.code; etag = it.header("ETag"); when (it.code) {
            401 -> throw WebDavFailure(WebDavError.AUTH, "WebDAV 账号或密码无效，请重新保存并测试")
            in 300..399 -> throw WebDavFailure(WebDavError.PROTOCOL, "同步地址发生跳转，请填写最终 HTTPS 目录后重新测试")
            429 -> throw WebDavFailure(WebDavError.NETWORK, "WebDAV 请求过于频繁，请稍后重试")
        } }
        engine.withGeneration(generation) { }
        return Reply(code, etag, bytes)
    }
    suspend fun test(): WebDavTest {
        val xml = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>""".toByteArray()
        val response = request(base, "PROPFIND", xml, mapOf("Depth" to "0"), 1024 * 1024)
        when (response.code) {
            403 -> throw WebDavFailure(WebDavError.DENIED, "WebDAV 目录无读取权限")
            404 -> throw WebDavFailure(WebDavError.NOT_FOUND, "同步目录不存在，请先在服务端创建目录")
            207 -> if (!collection(response.bytes)) throw WebDavFailure(WebDavError.PROTOCOL, "地址不是可验证的 WebDAV 目录")
            else -> throw WebDavFailure(WebDavError.PROTOCOL, "服务器不支持目录权限探测（HTTP ${response.code}）")
        }
        return conditionalProbe()
    }
    suspend fun read(): RemoteBackup {
        val response = request(resource, "GET")
        return when (response.code) {
            404 -> RemoteBackup(null, null)
            200 -> {
                val document = try { BackupCodec.decode(response.bytes) } catch (_: Exception) { throw WebDavFailure(WebDavError.PROTOCOL, "远端数据无效或版本不支持，未修改本地或远端数据") }
                RemoteBackup(document, response.etag?.takeIf(::strong))
            }
            403 -> throw WebDavFailure(WebDavError.DENIED, "没有读取远端备份的权限")
            else -> throw WebDavFailure(WebDavError.NETWORK, "读取同步数据失败（HTTP ${response.code}）")
        }
    }
    suspend fun upload(bytes: ByteArray, previous: RemoteBackup): String {
        require(bytes.size <= BackupCodec.MAX_BYTES)
        if (previous.document != null && previous.etag == null) throw WebDavFailure(WebDavError.PROTOCOL, "远端未提供可靠版本标识，仅允许读取导入")
        val condition = if (previous.document == null) mapOf("If-None-Match" to "*") else mapOf("If-Match" to previous.etag!!)
        val result = request(resource, "PUT", bytes, condition, 64 * 1024)
        if (result.code == 412) throw WebDavFailure(WebDavError.CONFLICT, "远端已被其他设备更新，请重新预览；本地合并结果已保留")
        if (result.code in setOf(403, 405, 409, 423, 507)) throw WebDavFailure(WebDavError.DENIED, "同步目录目前不可写；本地数据已保留，可恢复权限后重新预览")
        if (result.code !in setOf(200, 201, 204)) throw WebDavFailure(WebDavError.NETWORK, "上传未完成（HTTP ${result.code}），请重新预览确认远端状态")
        // Verify the persisted resource rather than claiming success from a PUT status alone.
        val verified = request(resource, "GET")
        if (verified.code != 200 || !verified.bytes.contentEquals(bytes) || !strong(verified.etag.orEmpty()))
            throw WebDavFailure(WebDavError.CONFLICT, "远端写入后的内容或版本已变化，请重新预览")
        return verified.etag!!
    }
    private suspend fun conditionalProbe(): WebDavTest {
        val id = UUID.randomUUID().toString()
        val probe = base.newBuilder().addPathSegment(".picomic-check-$id.json").build()
        val marker = JSONObject().put("picomicProbe", id).toString().toByteArray()
        var owned = false; var safe = false; var cleanup = true
        try {
            val created = request(probe, "PUT", marker, mapOf("If-None-Match" to "*"), 64 * 1024)
            if (created.code !in setOf(200, 201, 204)) return WebDavTest(false, "目录可读取，但没有可靠写入权限；可使用只读导入")
            owned = true
            val first = request(probe, "GET", limit = 64 * 1024)
            if (first.code != 200 || !first.bytes.contentEquals(marker) || !strong(first.etag.orEmpty())) return WebDavTest(false, "目录不提供可靠版本标识；仅允许只读导入")
            // Do not test against the user's actual backup. A unique disposable probe isolates broken servers.
            val changed = JSONObject().put("picomicProbe", id).put("conditionalTest", true).toString().toByteArray()
            val createAgain = request(probe, "PUT", changed, mapOf("If-None-Match" to "*"), 64 * 1024)
            val wrongVersion = request(probe, "PUT", changed, mapOf("If-Match" to "\"picomic-never-$id\""), 64 * 1024)
            val after = request(probe, "GET", limit = 64 * 1024)
            safe = createAgain.code == 412 && wrongVersion.code == 412 && after.code == 200 && after.bytes.contentEquals(marker) && after.etag == first.etag
        } finally {
            if (owned) try {
                val current = request(probe, "GET", limit = 64 * 1024)
                val ownMarker = runCatching { JSONObject(current.bytes.toString(Charsets.UTF_8)).optString("picomicProbe") == id }.getOrDefault(false)
                cleanup = current.code == 404 || (current.code == 200 && ownMarker && strong(current.etag.orEmpty()) && request(probe, "DELETE", headers = mapOf("If-Match" to current.etag!!), limit = 64 * 1024).code in setOf(200, 204, 404))
            } catch (e: CancellationException) { throw e } catch (_: Exception) { cleanup = false }
        }
        return WebDavTest(safe && cleanup, when { !cleanup -> "测试文件未能清理，写入暂未启用；请检查目录权限后重试"; safe -> "连接成功，已验证条件写入，可预览同步"; else -> "服务端不可靠地执行版本条件；仅允许只读导入" })
    }
    private fun collection(bytes: ByteArray): Boolean {
        val text = bytes.toString(Charsets.UTF_8)
        if (Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(text)) return false
        val parser = android.util.Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(text.reader())
        var event = parser.eventType; var responseDepth = -1; var href: String? = null; var isCollection = false
        var propertyDepth = -1; var propertySuccess = false; var propertyCollection = false
        var found = false; var count = 0
        while (event != XmlPullParser.END_DOCUMENT) {
            if (++count > 20000 || parser.depth > 16) return false
            if (event == XmlPullParser.START_TAG && parser.namespace == "DAV:") when (parser.name) {
                "response" -> { responseDepth = parser.depth; href = null; isCollection = false }
                "propstat" -> if (responseDepth >= 0) { propertyDepth = parser.depth; propertySuccess = false; propertyCollection = false }
                "href" -> if (responseDepth >= 0) href = parser.nextText()
                "status" -> if (propertyDepth >= 0) propertySuccess = Regex("HTTP/[0-9.]+ 200(?: .*)?").matches(parser.nextText().trim())
                "collection" -> if (propertyDepth >= 0) propertyCollection = true
            }
            if (event == XmlPullParser.END_TAG && parser.name == "propstat" && parser.depth == propertyDepth) {
                isCollection = isCollection || propertySuccess && propertyCollection
                propertyDepth = -1
            }
            if (event == XmlPullParser.END_TAG && parser.name == "response" && parser.depth == responseDepth) {
                val url = href?.let { base.resolve(it) }
                if (url?.origin() == base.origin() && url.encodedPath.trimEnd('/') == base.encodedPath.trimEnd('/') && isCollection) found = true
                responseDepth = -1
            }
            event = parser.next()
        }
        return found
    }
    companion object {
        fun endpoint(value: String): HttpUrl = value.trim().toHttpUrl().also {
            require(it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null && it.encodedPath.endsWith('/')) { "请填写以 / 结尾的 HTTPS 目录地址，不包含账号或查询参数" }
        }
        fun create(engine: NetworkEngine, url: String, username: String, password: String) = WebDavClient(engine, endpoint(url), Credentials.basic(username, password, Charsets.UTF_8))
        private fun strong(value: String) = value.length in 2..512 && value.startsWith('"') && value.endsWith('"') && value.none(Char::isISOControl)
    }
}
