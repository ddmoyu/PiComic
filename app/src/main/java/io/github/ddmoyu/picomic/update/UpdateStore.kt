package io.github.ddmoyu.picomic.update

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/** Cached/task metadata is revalidated against this build's channel, package and protocol on every load. */
class UpdateStore(private val directory: File, private val channel: ReleaseChannel, private val packageName: String) {
    init { check(directory.isDirectory || directory.mkdirs()) }
    data class Saved(val bundle: UpdateBundle, val artifactId: Long? = null, val etag: String? = null)
    private fun file(name: String) = AtomicFile(File(directory, name))
    fun read(name: String): Saved? {
        val file = file(name); if (!file.baseFile.exists()) return null
        require(file.baseFile.length() <= 2 * 1024 * 1024)
        val root = UpdateContract.json(file.openRead().use { it.readBytes() }, 2 * 1024 * 1024)
        require(root.get("schemaVersion") == 1)
        val bundle = UpdateContract.bundle(root.getString("release").toByteArray(), root.getString("manifest").toByteArray(), channel, packageName)
        val id = if (root.isNull("assetId")) null else root.getLong("assetId").also { require(bundle.artifacts.any { artifact -> artifact.asset.id == it }) }
        val etag = if (root.isNull("etag")) null else root.getString("etag").also { require(it.length <= 512 && it.none(Char::isISOControl)) }
        return Saved(bundle, id, etag)
    }
    fun write(name: String, saved: Saved) {
        val bytes = JSONObject().put("schemaVersion", 1).put("release", saved.bundle.releaseJson).put("manifest", saved.bundle.manifestJson)
            .put("assetId", saved.artifactId ?: JSONObject.NULL).put("etag", saved.etag ?: JSONObject.NULL).toString().toByteArray()
        val file = file(name); val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) } catch (e: Exception) { file.failWrite(output); throw e }
    }
    fun delete(name: String) = file(name).delete()
}
