package io.github.ddmoyu.picomic

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.backup.*
import io.github.ddmoyu.picomic.network.NetworkEngine
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EncryptedBackupTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun document() = BackupCodec.encode(BackupDocument("00000000-0000-4000-8000-000000000001", 1, 100,
        BackupData(preferences = listOf(TransferPreference("themeMode", "跟随系统", 100)))))
    private fun record() = StoredAccount("夹具账号", "fixture-id", SessionCandidate(CredentialKind.COOKIE, "fixture-cookie".toByteArray()),
        RememberedLogin("fixture-user", "fixture-password".toCharArray())).use(StoredAccountCodec::encode)
    @Test fun unicodePasswordDecryptsIndependentPortableVectorOnAndroid() {
        val encrypted = Base64.getDecoder().decode("UElDT01JQwABAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaG0Qyzk6NwQOCPtxCoLI7b168uPviAYimogqTVtlw42q0")
        assertEquals("portable-fixture", BackupEncryption.decrypt(encrypted, "fixture-口令-2026".toCharArray()).decodeToString())
    }
    @Test fun encryptedExportMovesAccountBetweenIndependentKeystoreKeys() = runBlocking {
        val id = UUID.randomUUID().toString()
        val root = File(context.cacheDir, "encrypted-backup-$id").apply { mkdirs() }
        val aliases = listOf("picomic.backup-test.$id.first", "picomic.backup-test.$id.second")
        fun isolated(name: String) = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = File(root, name).apply { mkdirs() } }
        val firstContext = isolated("first"); val secondContext = isolated("second")
        val firstStore = KeystoreSecretStore(firstContext, aliases[0]); val secondStore = KeystoreSecretStore(secondContext, aliases[1])
        val first = SessionCoordinator(firstStore, NetworkEngine()); val second = SessionCoordinator(secondStore, NetworkEngine())
        try {
            AccountTransfer("jmcomic", record()).use { first.importAccount(it, "absent") }
            val encryptedAtRest = File(firstContext.noBackupFilesDir, "credentials/session.jmcomic.enc").readBytes()
            assertFalse(encryptedAtRest.decodeToString().contains("fixture-cookie"))
            assertFalse(encryptedAtRest.decodeToString().contains("fixture-user"))
            // A raw local ciphertext file is bound to its original key and cannot be migrated alone.
            assertTrue(runCatching { KeystoreSecretStore(firstContext, aliases[1]).read("session.jmcomic") }.isFailure)
            val transfers = first.exportAccounts()
            val plaintext = try { ConfigBackupCodec.encode(document(), transfers) } finally { transfers.forEach(AccountTransfer::close) }
            val password = "fixture-口令-2026".toCharArray()
            val encrypted = try { BackupEncryption.encrypt(plaintext, password) } finally { plaintext.fill(0) }
            assertFalse(encrypted.decodeToString().contains("fixture-user")); assertFalse(encrypted.decodeToString().contains("fixture-cookie"))
            assertTrue(runCatching { BackupEncryption.decrypt(encrypted, "incorrect-fixture".toCharArray()) }.isFailure)
            assertEquals("absent", second.accountFingerprint("jmcomic"))
            val decrypted = BackupEncryption.decrypt(encrypted, password)
            val backup = try { ConfigBackupCodec.decode(decrypted) } finally { decrypted.fill(0); password.fill('\u0000') }
            backup.use {
                assertEquals("跟随系统", it.data.preferences.single().value)
                second.importAccount(it.accounts.single(), "absent")
            }
            assertEquals(AccountStatus.NEEDS_VALIDATION, second.state.value["jmcomic"]!!.status)
            second.rememberedLogin("jmcomic")!!.use { assertArrayEquals("fixture-password".toCharArray(), it.password) }
            secondStore.read("session.jmcomic")!!.let { bytes ->
                try { StoredAccountCodec.decode(bytes).use { assertEquals("fixture-cookie", it.candidate!!.value.decodeToString()) } }
                finally { bytes.fill(0) }
            }
            val destinationCiphertext = File(secondContext.noBackupFilesDir, "credentials/session.jmcomic.enc").readBytes()
            assertFalse(destinationCiphertext.contentEquals(encryptedAtRest))
        } finally {
            root.deleteRecursively()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); aliases.forEach(::deleteEntry) }
        }
    }
    @Test fun configRejectsDuplicateUnknownSlotsAndTrailingOrTruncatedData() {
        val account = record(); val data = document()
        fun bundle(sources: List<String>) = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.writeInt(1); out.writeInt(data.size); out.write(data); out.writeInt(sources.size)
            sources.forEach { out.writeUTF(it); out.writeInt(account.size); out.write(account) }
        } }.toByteArray()
        val valid = bundle(listOf("jmcomic"))
        for (invalid in listOf(bundle(listOf("jmcomic", "jmcomic")), bundle(listOf("unknown")), valid + 0, valid.copyOf(valid.size - 1))) {
            assertTrue(runCatching { ConfigBackupCodec.decode(invalid) }.isFailure)
        }
        ConfigBackupCodec.decode(valid).use { assertEquals("jmcomic", it.accounts.single().source) }
        account.fill(0)
    }
    @Test fun closingPreviewCannotEraseTheConfirmedOperationsCredentials() {
        val preview = ConfigImportPreview(BackupMerge.preview(BackupData(), BackupData()),
            listOf(AccountImportPreview(AccountTransfer("jmcomic", record()), "absent", "夹具账号", true)))
        val work = preview.copyForApply()
        preview.close()
        assertTrue(preview.accounts.single().transfer.bytes.all { it == 0.toByte() })
        work.use { StoredAccountCodec.decode(it.accounts.single().transfer.bytes).use { account -> assertEquals("fixture-user", account.login!!.username) } }
        assertTrue(work.accounts.single().transfer.bytes.all { it == 0.toByte() })
    }
}
