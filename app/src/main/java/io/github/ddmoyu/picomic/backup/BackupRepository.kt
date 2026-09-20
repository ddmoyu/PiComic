package io.github.ddmoyu.picomic.backup

import android.content.Context
import io.github.ddmoyu.picomic.content.ContentDatabase
import io.github.ddmoyu.picomic.content.ContentLibrary
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.util.UUID

class BackupRepository(context: Context) {
    private val log = io.github.ddmoyu.picomic.data.EventLog.get(context)
    private val dao = ContentDatabase.get(context).content()
    private val preferences = PreferenceBridge.get(context)
    private val library = ContentLibrary.get(context)
    private val meta = context.getSharedPreferences("backup_identity", 0)
    suspend fun snapshot(): BackupData = withContext(Dispatchers.IO) { library.flush(); preferences.flush(); dao.backupSnapshot() }
    suspend fun export(): ByteArray = withContext(Dispatchers.IO) { encode(snapshot()).also { log.record(io.github.ddmoyu.picomic.data.EventCode.BACKUP_EXPORTED) } }
    suspend fun encode(data: BackupData): ByteArray = withContext(Dispatchers.IO) {
        mutex.withLock {
            val device = meta.getString("device", null) ?: UUID.randomUUID().toString()
            val revision = meta.getLong("revision", 0) + 1
            val bytes = BackupCodec.encode(BackupDocument(device, revision, System.currentTimeMillis(), data))
            BackupCodec.decode(bytes) // Never emit a backup that this version cannot restore.
            check(meta.edit().putString("device", device).putLong("revision", revision).commit()) { "无法保存备份修订号" }
            bytes
        }
    }
    suspend fun preview(input: InputStream): BackupPreview = withContext(Dispatchers.IO) { preview(BackupCodec.read(input).data) }
    suspend fun preview(incoming: BackupData): BackupPreview = withContext(Dispatchers.IO) { BackupMerge.preview(snapshot(), incoming) }
    suspend fun apply(preview: BackupPreview, choices: Map<String, MergeSide>): BackupData = withContext(Dispatchers.IO) {
        library.flush(); preferences.flush()
        val merged = BackupMerge.resolve(preview, choices)
        // Includes library, deletion markers, per-work and portable settings in one transaction.
        dao.applyBackup(preview.fingerprint, merged)
        preferences.refresh()
        log.record(io.github.ddmoyu.picomic.data.EventCode.BACKUP_IMPORTED)
        merged
    }
    companion object { private val mutex = Mutex() }
}
