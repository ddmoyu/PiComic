package io.github.ddmoyu.picomic.backup

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class BackupEncryptionTest {
    // Independently generated with Python hashlib + cryptography, including a Unicode password.
    private val fixture = Base64.getDecoder().decode("UElDT01JQwABAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaG0Qyzk6NwQOCPtxCoLI7b168uPviAYimogqTVtlw42q0")
    private val password = "fixture-口令-2026".toCharArray()
    @Test fun portableVectorMatchesAnIndependentImplementation() {
        assertEquals("portable-fixture", BackupEncryption.decrypt(fixture, password).decodeToString())
    }
    @Test fun exportsUseFreshSaltAndNonceAndHidePlaintext() {
        val plain = "fixture-account-password-token".toByteArray()
        val first = BackupEncryption.encrypt(plain, password)
        val second = BackupEncryption.encrypt(plain, password)
        assertFalse(first.contentEquals(second))
        assertFalse(first.copyOfRange(9, 25).contentEquals(second.copyOfRange(9, 25)))
        assertFalse(first.copyOfRange(25, 37).contentEquals(second.copyOfRange(25, 37)))
        assertFalse(first.decodeToString().contains(plain.decodeToString()))
        assertArrayEquals(plain, BackupEncryption.decrypt(first, password))
        assertArrayEquals(plain, BackupEncryption.decrypt(second, password))
    }
    @Test fun wrongPasswordAndTamperedHeaderCiphertextOrTagCannotDecrypt() {
        assertTrue(runCatching { BackupEncryption.decrypt(fixture, "incorrect-fixture".toCharArray()) }.isFailure)
        for (offset in listOf(9, 25, 37, fixture.lastIndex)) {
            val changed = fixture.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }
            assertTrue("offset $offset", runCatching { BackupEncryption.decrypt(changed, password) }.isFailure)
        }
    }
    @Test fun malformedFilesAndUnboundedInputsAreRejected() {
        for (invalid in listOf(fixture.copyOf(40), fixture + 0, fixture.copyOf().also { it[8] = 2 })) {
            assertTrue(runCatching { BackupEncryption.decrypt(invalid, password) }.isFailure)
        }
        assertTrue(runCatching { BackupEncryption.encrypt(byteArrayOf(1), "short".toCharArray()) }.isFailure)
        assertTrue(runCatching { BackupEncryption.read(ByteArray(BackupEncryption.MAX_BYTES + 1).inputStream()) }.isFailure)
        assertArrayEquals(fixture, BackupEncryption.read(fixture.inputStream()))
    }
}
