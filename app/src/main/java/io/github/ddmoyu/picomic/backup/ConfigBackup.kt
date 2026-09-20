package io.github.ddmoyu.picomic.backup

import io.github.ddmoyu.picomic.auth.*
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class ConfigBackup(val data: BackupData, val accounts: List<AccountTransfer>) : AutoCloseable {
    override fun close() = accounts.forEach(AccountTransfer::close)
    override fun toString() = "ConfigBackup([redacted])"
}

object ConfigBackupCodec {
    fun encode(data: ByteArray, accounts: List<AccountTransfer>): ByteArray {
        require(data.size in 1..BackupCodec.MAX_BYTES && accounts.size <= AccountSlots.titles.size && accounts.map { it.source }.distinct().size == accounts.size)
        return ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.writeInt(1); out.writeInt(data.size); out.write(data); out.writeInt(accounts.size)
            accounts.forEach { transfer ->
                out.writeUTF(transfer.source); out.writeInt(transfer.bytes.size); out.write(transfer.bytes)
            }
        } }.toByteArray()
    }
    fun decode(bytes: ByteArray): ConfigBackup {
        require(bytes.size in 1..BackupEncryption.MAX_BYTES)
        val accounts = mutableListOf<AccountTransfer>()
        try { return DataInputStream(bytes.inputStream()).use { input ->
            require(input.readInt() == 1)
            val length = input.readInt(); require(length in 1..BackupCodec.MAX_BYTES && length <= input.available())
            val dataBytes = ByteArray(length).also(input::readFully)
            val data = try { BackupCodec.decode(dataBytes).data } finally { dataBytes.fill(0) }
            val count = input.readInt(); require(count in 0..AccountSlots.titles.size)
            repeat(count) {
                val source = input.readUTF(); require(source in AccountSlots.titles && accounts.none { it.source == source })
                val size = input.readInt(); require(size in 1..StoredAccountCodec.MAX_BYTES && size <= input.available())
                accounts += AccountTransfer(source, ByteArray(size).also(input::readFully))
            }
            require(input.available() == 0)
            ConfigBackup(data, accounts)
        } } catch (e: Exception) { accounts.forEach(AccountTransfer::close); throw e }
    }
}

class AccountImportPreview(val transfer: AccountTransfer, val fingerprint: String, val displayName: String, val hasPassword: Boolean) {
    val source get() = transfer.source
    val hasLocal get() = fingerprint != "absent"
    override fun toString() = "AccountImportPreview($source, [redacted])"
}
class ConfigImportPreview(val data: BackupPreview, val accounts: List<AccountImportPreview>) : AutoCloseable {
    /** The confirmed operation owns its secrets independently of the dialog lifecycle. */
    fun copyForApply(): ConfigImportPreview {
        val copies = mutableListOf<AccountImportPreview>()
        try {
            accounts.forEach { copies += AccountImportPreview(AccountTransfer(it.source, it.transfer.bytes.copyOf()), it.fingerprint, it.displayName, it.hasPassword) }
            return ConfigImportPreview(data, copies)
        } catch (e: Exception) { copies.forEach { it.transfer.close() }; throw e }
    }
    override fun close() = accounts.forEach { it.transfer.close() }
    override fun toString() = "ConfigImportPreview([redacted])"
}

data class ConfigImportResult(val restored: Int, val skipped: Int, val failed: List<String>)
