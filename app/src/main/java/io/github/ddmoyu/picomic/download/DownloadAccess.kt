package io.github.ddmoyu.picomic.download

import android.content.Context
import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.network.NetworkRepository
import io.github.ddmoyu.picomic.source.jm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File

interface DownloadAccess {
    suspend fun partition(source: Source): String
    suspend fun pages(task: DownloadTask): List<PageRef>
    suspend fun fetch(task: DownloadTask, page: DownloadPage, destination: File)
}

class SourceDownloadAccess(context: Context, private val content: ContentRepository) : DownloadAccess {
    private val network = NetworkRepository.get(context)
    private val transfer = RangeTransfer(network)
    override suspend fun partition(source: Source) = content.run(source) { _, partition -> partition }
    private suspend fun <T> run(task: DownloadTask, block: suspend (ComicSource) -> T): T = content.run(task.key().source) { source, partition ->
        if (partition != task.accountPartition) throw ContentFailure(ContentFailureKind.LOGIN, "请恢复创建任务时的账号或匿名模式后继续下载")
        block(source)
    }
    override suspend fun pages(task: DownloadTask): List<PageRef> = run(task) { source ->
        val chapter = source.details(task.comic.id).chapters.firstOrNull { it.id == task.chapterId }
            ?: throw ContentFailure(ContentFailureKind.NOT_FOUND, "章节已变化，请重新选择下载章节")
        source.pages(task.comic.id, chapter)
    }
    override suspend fun fetch(task: DownloadTask, page: DownloadPage, destination: File) = imageGate.withPermit {
        run(task) { source ->
            val ref = PageManifest.decode(page.reference)
            if (ref.resolver != null) destination.writeBytes(source.image(ref))
            else {
                val url = ref.url.toHttpUrl()
                require(url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null)
                val request = Request.Builder().url(url)
                when (task.key().source) {
                    Source.JMCOMIC -> { JmProtocol.imageHost("${url.scheme}://${url.host}/"); request.header("User-Agent", JmProtocol.USER_AGENT) }
                    Source.NHENTAI -> require(url.host.matches(Regex("i[1-9][0-9]?\\.nhentai\\.net")))
                    Source.PICACG -> Unit // HTTPS fileServer was supplied by the authenticated content response; no token/cookie is forwarded.
                    else -> throw ContentFailure(ContentFailureKind.PARSE, "下载图片解析信息缺失")
                }
                transfer.fetch(request.build(), destination, if (ref.jm != null) 24L * 1024 * 1024 else 32L * 1024 * 1024)
                ref.jm?.let { rule ->
                    val bytes = destination.readBytes()
                    // Same single-decoder permit as online reading; offline files contain the reconstructed original.
                    JmImageFetcher.process(bytes, rule, destination)
                }
            }
            currentCoroutineContext().ensureActive()
        }
    }
    companion object { private val imageGate = Semaphore(2) }
}
