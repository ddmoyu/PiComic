package io.github.ddmoyu.picomic.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Version 1: fixed PBKDF2-HMAC-SHA256 work factor; authenticated header, fresh salt and nonce. */
object BackupEncryption {
    const val MAX_BYTES = 12 * 1024 * 1024
    private val magic = byteArrayOf(0x50, 0x49, 0x43, 0x4f, 0x4d, 0x49, 0x43, 0)
    private const val HEADER = 8 + 1 + 16 + 12
    private const val ITERATIONS = 600_000
    fun isEncrypted(bytes: ByteArray) = bytes.size >= magic.size && magic.indices.all { bytes[it] == magic[it] }
    fun read(input: InputStream): ByteArray = input.use {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = it.read(buffer); if (count < 0) break
            require(result.size() + count <= MAX_BYTES) { "备份超过 12 MB 上限" }
            result.write(buffer, 0, count)
        }
        result.toByteArray()
    }
    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        require(password.size in 8..128) { "备份密码须为 8–128 个字符" }
        require(plaintext.size in 1..(MAX_BYTES - HEADER - 16))
        val random = SecureRandom()
        val header = magic + byteArrayOf(1) + ByteArray(16).also(random::nextBytes) + ByteArray(12).also(random::nextBytes)
        return header + cipher(Cipher.ENCRYPT_MODE, header, password).doFinal(plaintext)
    }
    fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        require(bytes.size in (HEADER + 17)..MAX_BYTES && isEncrypted(bytes) && bytes[8] == 1.toByte()) { "不支持的加密备份格式" }
        require(password.size in 8..128) { "备份密码须为 8–128 个字符" }
        try { return cipher(Cipher.DECRYPT_MODE, bytes.copyOfRange(0, HEADER), password).doFinal(bytes, HEADER, bytes.size - HEADER) }
        catch (_: java.security.GeneralSecurityException) { throw IllegalArgumentException("密码错误或备份文件已损坏，原数据未改动") }
    }
    private fun cipher(mode: Int, header: ByteArray, password: CharArray): Cipher {
        val spec = PBEKeySpec(password, header.copyOfRange(9, 25), ITERATIONS, 256)
        val key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        try {
            return Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, header.copyOfRange(25, HEADER)))
                updateAAD(header)
            }
        } finally { key.fill(0) }
    }
}
