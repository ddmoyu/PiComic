package io.github.ddmoyu.picomic.reader

import coil3.ImageLoader
import coil3.decode.*
import coil3.fetch.*
import coil3.key.Keyer
import coil3.request.Options
import io.github.ddmoyu.picomic.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import java.security.MessageDigest

data class SourceImage(val comic: ComicKey, val page: PageRef, val partition: String, val repository: ContentRepository, val variant: String = "") {
    val key = "source-image/" + MessageDigest.getInstance("SHA-256").digest("${comic.stable}/${page.id}/$partition/$variant/${page.resolver}/${page.url}".toByteArray()).joinToString("") { "%02x".format(it) }
}
/** Resolve and fetch within the same session lease. The cache never contains account credentials. */
class SourceImageFetcher(private val data: SourceImage, private val loader: ImageLoader) : Fetcher {
    override suspend fun fetch() = withContext(Dispatchers.IO) {
        val cache = loader.diskCache
        cache?.openSnapshot(data.key)?.let { return@withContext SourceFetchResult(ImageSource(it.data, okio.FileSystem.SYSTEM, data.key, it), null, DataSource.DISK) }
        val bytes = data.repository.run(data.comic.source) { source, partition ->
            if (partition != data.partition) throw ContentFailure(ContentFailureKind.LOGIN, "账号已更改，请重新加载章节")
            source.image(data.page)
        }
        val editor = cache?.openEditor(data.key)
        if (editor != null) {
            try {
                okio.FileSystem.SYSTEM.write(editor.data) { write(bytes) }
                val snapshot = editor.commitAndOpenSnapshot() ?: throw java.io.IOException("无法保存图片缓存")
                SourceFetchResult(ImageSource(snapshot.data, okio.FileSystem.SYSTEM, data.key, snapshot), null, DataSource.NETWORK)
            } catch (e: Exception) { editor.abort(); throw e }
        } else SourceFetchResult(ImageSource(Buffer().write(bytes), okio.FileSystem.SYSTEM), null, DataSource.NETWORK)
    }
    class Factory : Fetcher.Factory<SourceImage> { override fun create(data: SourceImage, options: Options, imageLoader: ImageLoader) = SourceImageFetcher(data, imageLoader) }
    class ImageKeyer : Keyer<SourceImage> { override fun key(data: SourceImage, options: Options) = data.key }
}
