package io.github.ddmoyu.picomic.backup

import io.github.ddmoyu.picomic.content.*
import io.github.ddmoyu.picomic.data.Source

enum class MergeSide { LOCAL, INCOMING }
data class MergeConflict(val key: String, val title: String, val local: String, val incoming: String)
data class BackupPreview(val local: BackupData, val incoming: BackupData, val fingerprint: String, val conflicts: List<MergeConflict>, val additions: Int, val unknownSources: Set<String>)

/** Different progress positions and deletion states require an explicit choice, never a max-page union. */
object BackupMerge {
    fun preview(local: BackupData, incoming: BackupData): BackupPreview {
        val left = records(local); val right = records(incoming)
        val titles = (incoming.comics + local.comics).associate { BackupCodec.pair(it.source, it.id) to it.title }
        val conflicts = right.values.mapNotNull { remote ->
            val current = left[remote.key] ?: return@mapNotNull null
            if (current.meaning == remote.meaning) null else MergeConflict(remote.key, remote.label(titles), current.description, remote.description)
        }
        return BackupPreview(local, incoming, BackupCodec.fingerprint(local), conflicts, right.keys.count { it !in left },
            incoming.comics.map { it.source }.filterNot { name -> Source.entries.any { it.name == name } }.toSet())
    }
    fun resolve(preview: BackupPreview, choices: Map<String, MergeSide>, now: Long = System.currentTimeMillis()): BackupData {
        require(preview.conflicts.all { it.key in choices }) { "请先选择所有冲突项" }
        val left = records(preview.local); val right = records(preview.incoming)
        val resolved = (left.keys + right.keys).map { key ->
            val a = left[key]; val b = right[key]
            when {
                a == null -> b!!
                b == null -> a
                a.meaning == b.meaning -> if (b.updatedAt > a.updatedAt) b else a
                else -> (if (choices.getValue(key) == MergeSide.LOCAL) a else b).at(maxOf(now, a.updatedAt, b.updatedAt) + 1)
            }
        }
        // Existing metadata (including device-local cover references) is retained for existing works.
        val comics = (preview.incoming.comics + preview.local.comics).associateBy { BackupCodec.pair(it.source, it.id) }.values.toList()
        return BackupData(comics, resolved.mapNotNull { it.value as? FavoriteEntity }, resolved.mapNotNull { it.value as? ContentProgressEntity },
            resolved.mapNotNull { it.value as? WorkPreferenceEntity }, resolved.mapNotNull { it.value as? TransferPreference })
    }
    private data class Record(val key: String, val comic: String?, val kind: String, val value: Any, val meaning: Any, val updatedAt: Long, val description: String) {
        fun label(titles: Map<String, String>) = (comic?.let { titles[it] ?: it } ?: key.removePrefix("setting/")) + " · " + kind
        fun at(time: Long): Record = when (val row = value) {
            is FavoriteEntity -> record(row.copy(updatedAt = time))
            is ContentProgressEntity -> record(row.copy(updatedAt = time))
            is WorkPreferenceEntity -> record(row.copy(updatedAt = time))
            is TransferPreference -> record(row.copy(updatedAt = time))
            else -> error("不支持的数据记录")
        }
    }
    private fun record(value: FavoriteEntity): Record {
        val comic = BackupCodec.pair(value.source, value.id)
        return Record("favorite/$comic", comic, "收藏", value, value.copy(updatedAt = 0), value.updatedAt, if (value.deleted) "已取消收藏" else "已收藏")
    }
    private fun record(value: ContentProgressEntity): Record {
        val comic = BackupCodec.pair(value.source, value.id)
        return Record("progress/$comic", comic, "阅读记录", value, value.copy(updatedAt = 0), value.updatedAt, if (value.deleted) "已删除" else "${value.chapterId} · 第 ${value.page} 页 · ${value.mode}")
    }
    private fun record(value: WorkPreferenceEntity): Record {
        val comic = BackupCodec.pair(value.source, value.id)
        return Record("work/$comic/${value.name}", comic, value.name, value, value.copy(updatedAt = 0), value.updatedAt, if (value.deleted) "跟随全局" else value.value)
    }
    private fun record(value: TransferPreference) = Record("setting/${value.key}", null, "偏好", value, value.copy(updatedAt = 0), value.updatedAt, if (value.deleted) "恢复默认" else value.value)
    private fun records(data: BackupData): Map<String, Record> = (data.favorites.map(::record) + data.progress.map(::record) + data.workPreferences.map(::record) + data.preferences.filter { PortablePreferences.allowed(it.key) }.map(::record)).associateBy { it.key }
}
