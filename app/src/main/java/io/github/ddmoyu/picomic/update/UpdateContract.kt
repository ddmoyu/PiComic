package io.github.ddmoyu.picomic.update

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.*
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

class UpdateFailure(message: String, val cooldownUntil: Long = 0, val retryable: Boolean = false) : IOException(message)
data class ReleaseChannel(val owner: String, val repo: String) {
    init { require(owner.matches(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}")) && repo.matches(Regex("[A-Za-z0-9_][A-Za-z0-9_.-]{0,99}")) && repo !in setOf(".", "..")) }
    val api = "https://api.github.com/repos/$owner/$repo/releases/"
    val page = "https://github.com/$owner/$repo/releases/"
}
data class ReleaseAsset(val id: Long, val name: String, val size: Long, val url: HttpUrl, val digest: String?)
data class ReleaseInfo(val id: Long, val tag: String, val page: String, val published: String, val notes: String, val assets: List<ReleaseAsset>)
data class UpdateArtifact(val asset: ReleaseAsset, val abis: List<String>, val minSdk: Int, val sha256: String)
data class UpdateBundle(val release: ReleaseInfo, val versionName: String, val versionCode: Long, val artifacts: List<UpdateArtifact>, val releaseJson: String, val manifestJson: String) {
    fun select(abis: List<String>, sdk: Int): UpdateArtifact? = abis.firstNotNullOfOrNull { abi -> artifacts.firstOrNull { sdk >= it.minSdk && abi in it.abis } }
    fun identity(artifact: UpdateArtifact) = "${release.id}:${artifact.asset.id}:$versionCode:${artifact.sha256}"
}
object UpdateContract {
    const val MAX_APK = 512L * 1024 * 1024
    val knownAbis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    fun json(bytes: ByteArray, limit: Int): JSONObject {
        require(bytes.size in 1..limit)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        // Bound nesting before JSONTokener recursion.
        var depth = 0; var quoted = false; var escaped = false
        text.forEach { c -> if (quoted) { if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false }
            else when(c) { '"' -> quoted = true; '{', '[' -> { depth++; require(depth <= 12) }; '}', ']' -> { depth--; require(depth >= 0) } } }
        require(depth == 0 && !quoted)
        val parser = JSONTokener(text); val root = parser.nextValue() as? JSONObject ?: error("无效对象")
        require(parser.nextClean() == '\u0000'); return root
    }
    private fun text(obj: JSONObject, key: String, max: Int = 200): String = (obj.get(key) as? String ?: error("字段类型无效")).also { require(it.isNotEmpty() && it.length <= max && it.none(Char::isISOControl)) }
    private fun integer(obj: JSONObject, key: String, max: Long = Long.MAX_VALUE): Long {
        val value = obj.get(key); require(value is Long || value is Int)
        return (value as Number).toLong().also { require(it in 1..max) }
    }
    private fun fields(obj: JSONObject, keys: Set<String>) { require(obj.keys().asSequence().toSet() == keys) }
    private fun secure(url: HttpUrl) { require(url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null) }
    fun release(bytes: ByteArray, channel: ReleaseChannel): ReleaseInfo {
        val obj = json(bytes, 1024 * 1024)
        require(obj.get("draft") == false && obj.get("prerelease") == false)
        val id = integer(obj, "id"); val tag = text(obj, "tag_name", 100)
        require(tag.matches(Regex("v[0-9][A-Za-z0-9.+-]{0,98}")))
        require(text(obj, "url", 512) == channel.api + id)
        val page = text(obj, "html_url", 512).toHttpUrl(); secure(page)
        require(page.toString() == channel.page + "tag/" + tag)
        val published = text(obj, "published_at", 40); java.time.Instant.parse(published)
        val notes = obj.opt("body").let { if (it == JSONObject.NULL || it == null) "" else it as? String ?: error("更新说明无效") }; require(notes.length <= 65536)
        val rows = obj.getJSONArray("assets"); require(rows.length() in 1..100)
        val assets = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index); require(row.get("state") == "uploaded")
            val name = text(row, "name", 160); require(name.matches(Regex("[A-Za-z0-9_][A-Za-z0-9_.-]*")))
            val url = text(row, "browser_download_url", 1024).toHttpUrl(); secure(url)
            require(url.toString() == channel.page + "download/$tag/$name")
            val digest = row.opt("digest").let { if (it == null || it == JSONObject.NULL) null else it as? String ?: error("摘要格式无效") }
            if (digest != null) require(digest.matches(Regex("sha256:[0-9a-fA-F]{64}")))
            ReleaseAsset(integer(row, "id"), name, integer(row, "size", MAX_APK), url, digest?.lowercase())
        }
        require(assets.map { it.id }.distinct().size == assets.size && assets.map { it.name }.distinct().size == assets.size)
        return ReleaseInfo(id, tag, page.toString(), published, notes, assets)
    }
    fun bundle(releaseBytes: ByteArray, manifestBytes: ByteArray, channel: ReleaseChannel, packageName: String): UpdateBundle {
        val release = release(releaseBytes, channel); val manifestAsset = release.assets.single { it.name == "picomic-update.json" }
        require(manifestAsset.size == manifestBytes.size.toLong() && manifestBytes.size <= 65536)
        manifestAsset.digest?.let { require(it == "sha256:" + hash(manifestBytes)) }
        val root = json(manifestBytes, 65536)
        fields(root, setOf("schemaVersion", "tag", "versionName", "versionCode", "packageName", "channel", "artifacts"))
        require(integer(root, "schemaVersion") == 1L && text(root, "tag") == release.tag && text(root, "channel") == "stable" && text(root, "packageName") == packageName)
        val version = text(root, "versionName", 80); require(release.tag == "v$version")
        val code = integer(root, "versionCode"); val array = root.getJSONArray("artifacts"); require(array.length() in 1..8)
        val artifacts = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index); fields(item, setOf("assetName", "abis", "minSdk", "sizeBytes", "sha256"))
            val name = text(item, "assetName", 160); require(name.endsWith(".apk"))
            val asset = release.assets.single { it.name == name }; require(integer(item, "sizeBytes", MAX_APK) == asset.size)
            val hash = text(item, "sha256", 64).lowercase(); require(hash.matches(Regex("[0-9a-f]{64}")))
            asset.digest?.let { require(it == "sha256:$hash") }
            val abiRows = item.getJSONArray("abis"); require(abiRows.length() in 1..4)
            val abis = (0 until abiRows.length()).map { abiRows.get(it) as? String ?: error("ABI 格式无效") }
            require(abis.toSet().size == abis.size && abis.all { it in knownAbis })
            UpdateArtifact(asset, abis, integer(item, "minSdk", 1000).toInt(), hash)
        }
        require(artifacts.map { it.asset.id }.distinct().size == artifacts.size)
        return UpdateBundle(release, version, code, artifacts, releaseBytes.toString(Charsets.UTF_8), manifestBytes.toString(Charsets.UTF_8))
    }
    fun hash(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun assetTarget(url: HttpUrl): Boolean = url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
        url.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com")
}
