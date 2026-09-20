package io.github.ddmoyu.picomic.download

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract as Documents
import io.github.ddmoyu.picomic.content.ContentFailure
import io.github.ddmoyu.picomic.content.ContentFailureKind
import kotlinx.coroutines.*
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class DownloadStorage(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val root = File(context.filesDir, "downloads")
    data class ImageInfo(val mime: String, val width: Int, val height: Int)
    suspend fun write(task: DownloadTask, page: DownloadPage, ready: File): DownloadPage = withContext(Dispatchers.IO) {
        validate(task, page)
        val info = inspect(ready)
        val size = ready.length(); val checksum = sha(ready.inputStream())
        val uri = if (task.target == INTERNAL) {
            val directory = directory(task)
            check(directory.isDirectory || directory.mkdirs()) { "无法创建下载目录" }
            val part = File(directory, page.fileName + ".part")
            try {
                FileOutputStream(part).use { out -> ready.inputStream().use { copy(it, out) }; out.fd.sync() }
                currentCoroutineContext().ensureActive()
                val final = File(directory, page.fileName)
                Files.move(part.toPath(), final.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                Uri.fromFile(final)
            } finally { part.delete() }
        } else {
            val folder = safFolder(task, create = true) ?: throw IOException("无法创建授权目录")
            child(folder, page.fileName + ".part")?.let { if (!Documents.deleteDocument(resolver, it)) throw IOException("无法清理旧下载断点") }
            val part = Documents.createDocument(resolver, folder, "application/octet-stream", page.fileName + ".part") ?: throw IOException("无法创建临时下载文件")
            try {
                resolver.openOutputStream(part, "wt")?.use { out -> ready.inputStream().use { copy(it, out) } } ?: throw IOException("无法写入授权目录")
                if (sha(resolver.openInputStream(part) ?: throw IOException("无法验证下载文件")) != checksum) throw IOException("下载文件校验失败")
                currentCoroutineContext().ensureActive()
                child(folder, page.fileName)?.let { previous -> if (!Documents.deleteDocument(resolver, previous)) throw IOException("无法替换损坏的下载文件") }
                val renamed = Documents.renameDocument(resolver, part, page.fileName) ?: throw IOException("此目录不支持完成文件重命名，请更换目录")
                val final = child(folder, page.fileName)
                if (final == null || Documents.getDocumentId(final) != Documents.getDocumentId(renamed)) {
                    runCatching { Documents.deleteDocument(resolver, renamed) }
                    throw IOException("此目录修改了文件名，请更换目录")
                }
                renamed
            } catch (e: Exception) { runCatching { Documents.deleteDocument(resolver, part) }; throw e }
        }
        page.copy(uri = uri.toString(), size = size, checksum = checksum, mime = info.mime, width = info.width, height = info.height)
    }
    suspend fun verify(task: DownloadTask, page: DownloadPage): Boolean = withContext(Dispatchers.IO) {
        if (!page.complete) return@withContext false
        val uri = ownedUri(task, page) ?: return@withContext false
        try {
            if (task.target == INTERNAL && File(uri.path!!).length() != page.size) return@withContext false
            sha(resolver.openInputStream(uri) ?: return@withContext false) == page.checksum
        } catch (e: SecurityException) { throw e } catch (e: CancellationException) { throw e } catch (_: IOException) { false }
    }
    suspend fun remove(task: DownloadTask, pages: List<DownloadPage>) = withContext(Dispatchers.IO) {
        validate(task)
        if (task.target == INTERNAL) {
            val directory = directory(task)
            if (!directory.exists()) return@withContext
            // Only generated file names are owned by this task; never recursively delete the selected user directory.
            directory.listFiles().orEmpty().forEach { file ->
                if (file.isFile && file.name.matches(Regex("[a-f0-9]{64}\\.image(?:\\.part)?")) && !file.delete()) throw IOException("部分下载文件未能删除")
            }
            if (directory.listFiles().orEmpty().isEmpty() && !directory.delete()) throw IOException("下载目录未能删除")
        } else {
            val folder = safFolder(task, create = false) ?: return@withContext
            val known = pages.flatMap { listOf(it.fileName, it.fileName + ".part") }.toSet()
            children(folder).filter { it.second in known }.forEach { (uri, _) -> if (!Documents.deleteDocument(resolver, uri)) throw IOException("部分下载文件未能删除") }
            if (children(folder).isEmpty() && !Documents.deleteDocument(resolver, folder)) throw IOException("下载目录未能删除")
        }
    }
    fun ownedUri(task: DownloadTask, page: DownloadPage): Uri? {
        validate(task, page)
        val saved = page.uri?.let(Uri::parse) ?: return null
        if (task.target == INTERNAL) {
            val expected = File(directory(task), page.fileName)
            if (saved != Uri.fromFile(expected)) throw SecurityException("下载文件位置与清单不一致")
            return saved.takeIf { expected.isFile }
        }
        val folder = safFolder(task, create = false) ?: return null
        val current = child(folder, page.fileName) ?: return null
        if (Documents.getDocumentId(saved) != Documents.getDocumentId(current) || saved.authority != current.authority) throw SecurityException("授权下载文件位置与清单不一致")
        return current
    }
    fun checkTarget(target: String) {
        if (target == INTERNAL) return
        val tree = Uri.parse(target)
        if (tree.scheme != "content" || !Documents.isTreeUri(tree) || resolver.persistedUriPermissions.none { it.uri == tree && it.isReadPermission && it.isWritePermission })
            throw SecurityException("下载目录授权已失效，请重新选择原目录")
    }
    private fun safFolder(task: DownloadTask, create: Boolean): Uri? {
        checkTarget(task.target)
        val tree = Uri.parse(task.target)
        var folder = Documents.buildDocumentUriUsingTree(tree, Documents.getTreeDocumentId(tree))
        for (name in listOf("PiComic", task.id)) {
            folder = child(folder, name) ?: if (create) Documents.createDocument(resolver, folder, Documents.Document.MIME_TYPE_DIR, name) ?: throw IOException("无法创建下载目录") else return null
        }
        return folder
    }
    private fun child(parent: Uri, name: String) = children(parent).firstOrNull { it.second == name }?.first
    private fun children(parent: Uri): List<Pair<Uri, String>> {
        val uri = Documents.buildChildDocumentsUriUsingTree(parent, Documents.getDocumentId(parent))
        return resolver.query(uri, arrayOf(Documents.Document.COLUMN_DOCUMENT_ID, Documents.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) { if (size > 25000) throw IOException("下载目录文件数量异常"); add(Documents.buildDocumentUriUsingTree(parent, cursor.getString(0)) to cursor.getString(1)) } }
        } ?: throw IOException("无法读取授权目录")
    }
    private fun directory(task: DownloadTask): File {
        validate(task)
        val directory = File(root, task.id).canonicalFile
        if (directory.parentFile != root.canonicalFile) throw SecurityException("下载路径不在应用目录内")
        return directory
    }
    private fun validate(task: DownloadTask, page: DownloadPage? = null) {
        require(task.id.matches(Regex("[a-f0-9]{64}")))
        require(page == null || page.taskId == task.id)
    }
    private suspend fun copy(input: InputStream, output: OutputStream) {
        val bytes = ByteArray(64 * 1024)
        while (true) { currentCoroutineContext().ensureActive(); val read = input.read(bytes); if (read < 0) break; output.write(bytes, 0, read) }
    }
    private suspend fun sha(input: InputStream): String = input.use {
        val digest = MessageDigest.getInstance("SHA-256"); val bytes = ByteArray(64 * 1024)
        while (true) { currentCoroutineContext().ensureActive(); val read = it.read(bytes); if (read < 0) break; digest.update(bytes, 0, read) }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
    companion object {
        const val INTERNAL = "internal"
        fun inspect(file: File): ImageInfo {
            if (file.length() !in 1..64L * 1024 * 1024) throw IOException("下载图片大小异常")
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val mime = options.outMimeType?.takeIf { it in setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/avif") }
                ?: throw ContentFailure(ContentFailureKind.UNSUPPORTED, "图片格式损坏或当前系统不支持，未计为下载完成")
            if (options.outWidth !in 1..100000 || options.outHeight !in 1..100000) throw IOException("下载图片尺寸无效")
            return ImageInfo(mime, options.outWidth, options.outHeight)
        }
    }
}
