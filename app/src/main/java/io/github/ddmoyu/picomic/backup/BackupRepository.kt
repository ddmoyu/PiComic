package io.github.ddmoyu.picomic.backup

import android.content.Context
import io.github.ddmoyu.picomic.content.ContentDatabase
import io.github.ddmoyu.picomic.content.ContentLibrary
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.util.UUID
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.network.NetworkRepository

class BackupRepository(context: Context) {
    private val appContext = context.applicationContext
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
    suspend fun exportEncrypted(password: CharArray, includeAccounts: Boolean): ByteArray = withContext(Dispatchers.IO) {
        require(password.size in 8..128)
        val data = encode(snapshot())
        val accounts = mutableListOf<AccountTransfer>()
        try {
            if (includeAccounts) accounts += NetworkRepository.get(appContext).sessions.exportAccounts()
            val plaintext = ConfigBackupCodec.encode(data, accounts)
            try { BackupEncryption.encrypt(plaintext, password).also { log.record(io.github.ddmoyu.picomic.data.EventCode.BACKUP_EXPORTED) } }
            finally { plaintext.fill(0) }
        } finally { data.fill(0); accounts.forEach(AccountTransfer::close) }
    }
    suspend fun previewConfig(bytes: ByteArray, password: CharArray? = null): ConfigImportPreview = withContext(Dispatchers.IO) {
        if (!BackupEncryption.isEncrypted(bytes)) return@withContext ConfigImportPreview(preview(BackupCodec.decode(bytes).data), emptyList())
        val plaintext = BackupEncryption.decrypt(bytes, password ?: error("请输入备份密码"))
        val incoming = try { ConfigBackupCodec.decode(plaintext) } finally { plaintext.fill(0) }
        try {
            val sessions = NetworkRepository.get(appContext).sessions
            val accounts = incoming.accounts.map { transfer ->
                StoredAccountCodec.decode(transfer.bytes).use { account ->
                    AccountImportPreview(transfer, sessions.accountFingerprint(transfer.source), account.displayName, account.login != null)
                }
            }
            ConfigImportPreview(preview(incoming.data), accounts)
        } catch (e: Exception) { incoming.close(); throw e }
    }
    suspend fun applyConfig(preview: ConfigImportPreview, choices: Map<String, MergeSide>, accountSources: Set<String>): ConfigImportResult = withContext(Dispatchers.IO) {
        require(accountSources.all { source -> preview.accounts.any { it.source == source } })
        val sessions = NetworkRepository.get(appContext).sessions
        val selected = preview.accounts.filter { it.source in accountSources }
        // Reject a stale preview before applying any ordinary settings.
        selected.forEach { check(sessions.accountFingerprint(it.source) == it.fingerprint) { "本机账号已变化，请重新预览" } }
        ensureActive()
        // Finish the confirmed local writes even if the settings screen is subsequently closed.
        withContext(NonCancellable) {
            apply(preview.data, choices)
            val failures = mutableListOf<String>()
            for (account in selected) {
                try { sessions.importAccount(account.transfer, account.fingerprint) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { failures += AccountSlots.titles.getValue(account.source) }
            }
            // Room data and separate Keystore records cannot share one transaction; report each failed account.
            ConfigImportResult(selected.size - failures.size, preview.accounts.size - selected.size, failures)
        }
    }
    companion object { private val mutex = Mutex() }
}
