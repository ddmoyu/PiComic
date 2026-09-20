package io.github.ddmoyu.picomic.source.jm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import io.github.ddmoyu.picomic.content.JmImageRule
import io.github.ddmoyu.picomic.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException

data class JmImage(val url: String, val rule: JmImageRule, val partition: String) {
    val key: String get() = "jm-image/$partition/${rule.photoId}/${rule.scrambleId}/${rule.filename}/${rule.alreadyDecoded}/${rule.version}"
}
/** Reconstruct before Coil downsamples or ZoomImage reads tiles. Only complete files enter the cache. */
class JmImageFetcher(private val context: Context, private val data: JmImage, private val loader: ImageLoader) : Fetcher {
    override suspend fun fetch() = withContext(Dispatchers.IO) {
        require(data.rule.version == JmStrips.VERSION)
        val cache = loader.diskCache ?: throw IOException("图片缓存不可用")
        gate.withPermit {
            currentCoroutineContext().ensureActive()
            cache.openSnapshot(data.key)?.let { return@withPermit SourceFetchResult(ImageSource(it.data, FileSystem.SYSTEM, data.key, it), null, DataSource.DISK) }
            val url = data.url.toHttpUrl()
            JmProtocol.imageHost("${url.scheme}://${url.host}/")
            require(url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null)
            val bytes = NetworkRepository.get(context).newCall(Request.Builder().url(url).header("User-Agent", JmProtocol.USER_AGENT).build()).readBounded(24 * 1024 * 1024) {
                if (it.code != 200) throw IOException("JM 图片暂时不可用")
            }
            val temp = File.createTempFile("jm-", ".image", context.cacheDir)
            try {
                val count = JmStrips.count(data.rule.photoId, data.rule.scrambleId, data.rule.filename, data.rule.alreadyDecoded)
                if (count == 0) temp.writeBytes(bytes) else reconstruct(bytes, count, temp)
                currentCoroutineContext().ensureActive()
                val editor = cache.openEditor(data.key)
                if (editor != null) {
                    try {
                        FileSystem.SYSTEM.write(editor.data) { temp.inputStream().use { stream -> val buffer = ByteArray(8192); while (true) { val size = stream.read(buffer); if (size < 0) break; write(buffer, 0, size) } } }
                        currentCoroutineContext().ensureActive()
                        val snapshot = editor.commitAndOpenSnapshot() ?: throw IOException("无法保存图片缓存")
                        SourceFetchResult(ImageSource(snapshot.data, FileSystem.SYSTEM, data.key, snapshot), null, DataSource.NETWORK)
                    } catch (e: Exception) { editor.abort(); throw e }
                } else {
                    // A cache editor collision must not expose a partial file.
                    val retained = File.createTempFile("jm-ready-", ".image", context.cacheDir)
                    temp.copyTo(retained, overwrite = true)
                    SourceFetchResult(ImageSource(retained.toOkioPath(), FileSystem.SYSTEM, closeable = AutoCloseable { retained.delete() }), null, DataSource.NETWORK)
                }
            } finally { temp.delete() }
        }
    }
    class Factory(private val context: Context) : Fetcher.Factory<JmImage> {
        override fun create(data: JmImage, options: Options, imageLoader: ImageLoader) = JmImageFetcher(context, data, imageLoader)
    }
    class ImageKeyer : Keyer<JmImage> { override fun key(data: JmImage, options: Options) = data.key }
    companion object {
        // Bound full-resolution decoding across all reading surfaces.
        private val gate = Semaphore(1)
        suspend fun process(bytes: ByteArray, rule: JmImageRule, destination: File) = gate.withPermit {
            require(rule.version == JmStrips.VERSION)
            val count = JmStrips.count(rule.photoId, rule.scrambleId, rule.filename, rule.alreadyDecoded)
            if (count == 0) destination.writeBytes(bytes) else reconstruct(bytes, count, destination)
        }
        internal suspend fun reconstruct(bytes: ByteArray, count: Int, destination: File) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 16_000_000) throw IOException("JM 图片尺寸超过处理上限")
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IOException("JM 图片无法解码")
            var result: Bitmap? = null
            try {
                result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result)
                val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
                for (strip in JmStrips.layout(bitmap.height, count)) {
                    currentCoroutineContext().ensureActive()
                    canvas.drawBitmap(bitmap, Rect(0, strip.sourceY, bitmap.width, strip.sourceY + strip.height), Rect(0, strip.targetY, bitmap.width, strip.targetY + strip.height), paint)
                }
                destination.outputStream().use { if (!result.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("JM 图片保存失败") }
            } finally { result?.recycle(); bitmap.recycle() }
        }
    }
}
