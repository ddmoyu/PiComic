package io.github.ddmoyu.picomic.download

import android.util.AtomicFile
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Resume only an exact URL with a strong ETag. Unsupported ranges restart this page. */
class RangeTransfer(private val calls: Call.Factory) {
    suspend fun fetch(request: Request, destination: File, limit: Long = 32L * 1024 * 1024) {
        val metadata = AtomicFile(File(destination.path + ".resume"))
        val urlKey = digest(request.url.toString().toByteArray())
        val saved = runCatching { JSONObject(metadata.openRead().use { it.readBytes().toString(Charsets.UTF_8) }) }.getOrNull()
        val tag = saved?.optString("etag")?.takeIf { strong(it) && saved.optString("url") == urlKey }
        val total = saved?.optLong("total", -1) ?: -1
        val offset = destination.length().takeIf { tag != null && total in 1..limit && it in 1 until total } ?: 0
        val next = request.newBuilder().header("Accept-Encoding", "identity").apply {
            if (offset > 0) { header("Range", "bytes=$offset-"); header("If-Range", tag!!) }
        }.build()
        val call = calls.newCall(next)
        val drained = CompletableDeferred<Unit>()
        try { suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            try { call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    try { if (!continuation.isCancelled) continuation.resumeWithException(e) } finally { drained.complete(Unit) }
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use { reply ->
                            if (reply.code !in setOf(200, 206)) throw IOException("图片下载失败（HTTP ${reply.code}）")
                            if (reply.header("Content-Encoding")?.let { it != "identity" } == true) throw IOException("图片传输编码不支持断点验证")
                            val append = reply.code == 206
                            val range = reply.header("Content-Range")?.let { Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(it) }
                            val expected = if (append) {
                                val values = range?.groupValues?.drop(1)?.map { it.toLongOrNull() ?: -1 }
                                if (offset <= 0 || values == null || values[0] != offset || values[1] != total - 1 || values[2] != total || reply.header("ETag") != tag) {
                                    metadata.delete(); destination.delete()
                                    throw IOException("图片版本或断点已变化，请重试该页")
                                }
                                total
                            } else reply.body.contentLength()
                            if (expected > limit) throw IOException("图片超过下载大小上限")
                            val etag = reply.header("ETag")?.takeIf(::strong)
                            if (etag != null && expected in 1..limit && (append || reply.header("Accept-Ranges") == "bytes")) {
                                val out = metadata.startWrite()
                                try { out.write(JSONObject().put("url", urlKey).put("etag", etag).put("total", expected).toString().toByteArray()); metadata.finishWrite(out) }
                                catch (e: Exception) { metadata.failWrite(out); throw e }
                            } else metadata.delete()
                            FileOutputStream(destination, append).use { out ->
                                reply.body.byteStream().use { input ->
                                    val buffer = ByteArray(64 * 1024); var count = if (append) offset else 0
                                    while (true) {
                                        if (call.isCanceled()) throw IOException("下载已暂停")
                                        val read = input.read(buffer); if (read < 0) break
                                        count += read
                                        if (count > limit || expected >= 0 && count > expected) throw IOException("图片传输长度异常")
                                        out.write(buffer, 0, read)
                                    }
                                    if (count == 0L || expected >= 0 && count != expected) throw IOException("图片尚未传输完整")
                                }
                                out.fd.sync()
                            }
                            metadata.delete()
                        }
                        continuation.resume(Unit)
                    } catch (e: Exception) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
                    finally { drained.complete(Unit) }
                }
            }) } catch (e: Exception) { drained.complete(Unit); throw e }
        } } finally { withContext(NonCancellable) { drained.await() } }
    }
    private fun strong(tag: String) = tag.length in 2..512 && tag.startsWith('"') && tag.endsWith('"') && tag.none { it.isISOControl() }
}
