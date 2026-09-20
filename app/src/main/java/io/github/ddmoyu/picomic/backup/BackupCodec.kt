package io.github.ddmoyu.picomic.backup

import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.download.digest
import org.json.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

object BackupCodec {
    const val MAX_BYTES = 10 * 1024 * 1024
    private const val MAX_ROWS = 20000
    fun read(input: InputStream): BackupDocument = input.use {
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val size = it.read(buffer); if (size < 0) break; require(output.size() + size <= MAX_BYTES) { "备份超过 10 MB 上限" }; output.write(buffer, 0, size) }
        decode(output.toByteArray())
    }
    fun decode(bytes: ByteArray, now: Long = System.currentTimeMillis()): BackupDocument {
        require(bytes.size in 1..MAX_BYTES) { "备份为空或超过 10 MB 上限" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        depth(text)
        val parser = JSONTokener(text)
        val root = parser.nextValue() as? JSONObject ?: error("备份根结构无效")
        require(parser.nextClean() == '\u0000') { "备份包含多余内容" }
        fields(root, setOf("schemaVersion", "deviceId", "revision", "exportedAt", "data"))
        require(integer(root, "schemaVersion", 1, 1) == 1L) { "不支持此备份版本" }
        val device = string(root, "deviceId", 64)
        require(runCatching { java.util.UUID.fromString(device).toString() == device }.getOrDefault(false)) { "备份设备标识无效" }
        val revision = integer(root, "revision", 0, Long.MAX_VALUE - 1)
        val exported = integer(root, "exportedAt", 0, now + 86_400_000)
        val data = root.getJSONObject("data")
        fields(data, setOf("comics", "favorites", "progress", "workPreferences", "preferences"))
        fun rows(name: String) = data.getJSONArray(name).let { array -> require(array.length() <= MAX_ROWS) { "备份条目过多" }; (0 until array.length()).map { array.getJSONObject(it) } }
        val comics = rows("comics").map { item ->
            fields(item, setOf("source", "id", "title", "author", "tags", "language", "chapterCount", "pageCount"))
            val tags = item.getJSONArray("tags"); require(tags.length() <= 1000)
            for (i in 0 until tags.length()) require(tags.get(i) is String && tags.getString(i).length <= 500)
            SavedComic(source(item), id(item), metadata(item, "title"), metadata(item, "author", true), null, tags.toString(),
                nullableString(item, "language", 40), nullableInt(item, "chapterCount"), nullableInt(item, "pageCount"))
        }
        val favorites = rows("favorites").map { item ->
            fields(item, setOf("source", "id", "updatedAt", "deleted"))
            FavoriteEntity(source(item), id(item), time(item, now), boolean(item, "deleted"))
        }
        val progress = rows("progress").map { item ->
            fields(item, setOf("source", "id", "chapterId", "pageId", "page", "offset", "mode", "updatedAt", "deleted"))
            val offset = (item.get("offset") as? Number)?.toDouble() ?: error("阅读偏移无效")
            require(offset.isFinite() && offset >= 0 && offset < 1)
            val mode = string(item, "mode", 32); require(PortablePreferences.valid("readingMode", mode))
            ContentProgressEntity(source(item), id(item), string(item, "chapterId", 512), string(item, "pageId", 512), integer(item, "page", 1, 100000).toInt(),
                offset.toFloat().coerceAtMost(.99999f), mode, time(item, now), boolean(item, "deleted"))
        }
        val work = rows("workPreferences").map { item ->
            fields(item, setOf("source", "id", "name", "value", "updatedAt", "deleted"))
            val name = string(item, "name", 64); val value = string(item, "value", 256, true); val deleted = boolean(item, "deleted")
            require(name in PortablePreferences.workKeys && (deleted && value.isEmpty() || PortablePreferences.valid(name, value))) { "作品偏好无效" }
            WorkPreferenceEntity(source(item), id(item), name, value, time(item, now), deleted)
        }
        val preferences = rows("preferences").map { item ->
            fields(item, setOf("key", "value", "updatedAt", "deleted"))
            val key = string(item, "key", 64); val value = string(item, "value", 20000, true); val deleted = boolean(item, "deleted")
            require(PortablePreferences.allowed(key) && (deleted && value.isEmpty() || PortablePreferences.valid(key, value))) { "备份包含不支持或敏感的设置项" }
            TransferPreference(key, value, time(item, now), deleted)
        }
        require(comics.size + favorites.size + progress.size + work.size + preferences.size <= MAX_ROWS) { "备份总条目超过上限" }
        unique(comics) { pair(it.source, it.id) }; unique(favorites) { pair(it.source, it.id) }; unique(progress) { pair(it.source, it.id) }
        unique(work) { pair(it.source, it.id) + ":${it.name}" }; unique(preferences) { it.key }
        val keys = comics.map { pair(it.source, it.id) }.toSet()
        require(favorites.all { pair(it.source, it.id) in keys } && progress.all { pair(it.source, it.id) in keys } && work.all { pair(it.source, it.id) in keys }) { "备份记录缺少作品资料" }
        return BackupDocument(device, revision, exported, BackupData(comics, favorites, progress, work, preferences))
    }
    fun encode(document: BackupDocument): ByteArray = JSONObject().put("schemaVersion", 1).put("deviceId", document.deviceId).put("revision", document.revision)
        .put("exportedAt", document.exportedAt).put("data", json(document.data)).toString().toByteArray(Charsets.UTF_8).also {
            require(it.size <= MAX_BYTES) { "备份超过 10 MB 上限" }
        }
    fun fingerprint(data: BackupData) = digest(json(data).toString().toByteArray(Charsets.UTF_8))
    private fun json(data: BackupData): JSONObject {
        fun base(source: String, id: String) = JSONObject().put("source", source).put("id", id)
        fun stamp(item: JSONObject, time: Long, deleted: Boolean) = item.put("updatedAt", time).put("deleted", deleted)
        return JSONObject()
            .put("comics", JSONArray(data.comics.sortedBy { pair(it.source, it.id) }.map { base(it.source, it.id).put("title", it.title).put("author", it.author)
                .put("tags", JSONArray(it.tags)).put("language", it.language ?: JSONObject.NULL).put("chapterCount", it.chapterCount ?: JSONObject.NULL).put("pageCount", it.pageCount ?: JSONObject.NULL) }))
            .put("favorites", JSONArray(data.favorites.sortedBy { pair(it.source, it.id) }.map { stamp(base(it.source, it.id), it.updatedAt, it.deleted) }))
            .put("progress", JSONArray(data.progress.sortedBy { pair(it.source, it.id) }.map { stamp(base(it.source, it.id), it.updatedAt, it.deleted).put("chapterId", it.chapterId)
                .put("pageId", it.pageId).put("page", it.page).put("offset", it.offset.toDouble()).put("mode", it.mode) }))
            .put("workPreferences", JSONArray(data.workPreferences.sortedBy { pair(it.source, it.id) + ":" + it.name }.map { stamp(base(it.source, it.id), it.updatedAt, it.deleted).put("name", it.name).put("value", it.value) }))
            .put("preferences", JSONArray(data.preferences.filter { PortablePreferences.allowed(it.key) }.sortedBy { it.key }.map { stamp(JSONObject().put("key", it.key), it.updatedAt, it.deleted).put("value", it.value) }))
    }
    private fun source(item: JSONObject) = string(item, "source", 64).also { require(it.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,63}"))) }
    private fun id(item: JSONObject) = string(item, "id", 512)
    private fun time(item: JSONObject, now: Long) = integer(item, "updatedAt", 0, now + 86_400_000)
    private fun boolean(item: JSONObject, key: String) = item.get(key) as? Boolean ?: error("备份布尔字段无效")
    private fun nullableInt(item: JSONObject, key: String): Int? = if (item.isNull(key)) null else integer(item, key, 1, 100000).toInt()
    private fun nullableString(item: JSONObject, key: String, max: Int): String? = if (item.isNull(key)) null else string(item, key, max)
    private fun integer(item: JSONObject, key: String, min: Long, max: Long): Long {
        val value = item.get(key); require(value is Int || value is Long) { "备份整数字段无效" }
        return (value as Number).toLong().also { require(it in min..max) { "备份数值超出范围" } }
    }
    private fun string(item: JSONObject, key: String, max: Int, empty: Boolean = false): String {
        val value = item.get(key) as? String ?: error("备份文本字段无效")
        require(value.length <= max && (empty || value.isNotBlank()) && value.none(Char::isISOControl)) { "备份文本字段无效" }
        return value
    }
    private fun metadata(item: JSONObject, key: String, empty: Boolean = false): String {
        val value = item.get(key) as? String ?: error("作品文本字段无效")
        require(value.length <= 20000 && (empty || value.isNotBlank()) && value.none { it.isISOControl() && it !in "\r\n\t" }) { "作品文本字段无效" }
        return value
    }
    private fun fields(item: JSONObject, keys: Set<String>) { require(item.keys().asSequence().toSet() == keys) { "备份字段缺失或包含未知内容" } }
    private fun <T> unique(items: List<T>, key: (T) -> String) { require(items.map(key).distinct().size == items.size) { "备份包含重复记录" } }
    private fun depth(text: String) {
        var depth = 0; var quoted = false; var escaped = false
        for (char in text) {
            if (quoted) { if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false }
            else when (char) { '"' -> quoted = true; '{', '[' -> { depth++; require(depth <= 16) { "备份结构嵌套过深" } }; '}', ']' -> { depth--; require(depth >= 0) } }
        }
        require(!quoted && depth == 0) { "备份内容不完整" }
    }
    internal fun pair(source: String, id: String) = "$source:${id.length}:$id"
}
