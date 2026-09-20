package io.github.ddmoyu.picomic.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretStore {
    fun read(key: String): ByteArray?
    fun write(key: String, value: ByteArray)
    fun remove(key: String)
}

/** Call on IO. Atomic ciphertext files are excluded from both cloud and device-transfer backup. */
class KeystoreSecretStore(context: Context, private val alias: String = "picomic.sessions.v1") : SecretStore {
    private val directory = File(context.noBackupFilesDir, "credentials")
    private fun file(key: String): AtomicFile {
        require(key.matches(Regex("[a-zA-Z0-9_.-]{1,80}")))
        check(directory.isDirectory || directory.mkdirs()) { "无法创建凭据目录" }
        return AtomicFile(File(directory, "$key.enc"))
    }
    private fun secretKey(): SecretKey = synchronized(keyLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return@synchronized it }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    @Synchronized override fun write(key: String, value: ByteArray) {
        require(value.size <= 64 * 1024)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(value)
        val target = file(key)
        val output = target.startWrite()
        try {
            output.write(byteArrayOf(1, cipher.iv.size.toByte()))
            output.write(cipher.iv)
            output.write(encrypted)
            target.finishWrite(output)
        } catch (error: Exception) { target.failWrite(output); throw error }
    }
    @Synchronized override fun read(key: String): ByteArray? {
        val target = file(key)
        val bytes = try { target.openRead().use { input ->
            require(target.baseFile.length() <= 64 * 1024 + 30) { "会话密文过大" }
            input.readBytes()
        } }
        catch (_: java.io.FileNotFoundException) { return null }
        require(bytes.size in 30..(64 * 1024 + 30) && bytes[0] == 1.toByte() && bytes[1] == 12.toByte()) { "会话密文格式无效，请重新登录" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(2, 14)))
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(bytes, 14, bytes.size - 14)
    }
    @Synchronized override fun remove(key: String) { file(key).delete(); check(!file(key).baseFile.exists()) }
    companion object { private val keyLock = Any() }
}
