package io.github.ddmoyu.picomic.source.jm

import io.github.ddmoyu.picomic.content.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** A coherent cookie-session profile, verified against /setting on 2026-09-20. */
object JmProtocol {
    const val VERSION = "1.8.2"
    const val ID = "jm-cookie-1.8.2-v1"
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/114.0.5735.196 Mobile Safari/537.36"
    val api = "https://www.cdnhjk.net/".toHttpUrl()
    val apiCandidates = listOf(api, "https://www.cdntwice.org/".toHttpUrl(), "https://www.cdnsha.org/".toHttpUrl(), "https://www.cdnaspa.cc/".toHttpUrl(), "https://www.cdnntr.cc/".toHttpUrl())
    // Public protocol constant, not an account credential.
    private const val SECRET = "185Hcomic3PAPP7R"
    fun md5(value: String) = MessageDigest.getInstance("MD5").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    fun token(timestamp: Long) = md5("$timestamp$SECRET")
    fun decode(timestamp: Long, encoded: String): String = try {
        val bytes = Base64.getMimeDecoder().decode(encoded)
        require(bytes.isNotEmpty() && bytes.size % 16 == 0)
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(token(timestamp).toByteArray(Charsets.US_ASCII), "AES"))
        cipher.doFinal(bytes).toString(Charsets.UTF_8)
    } catch (_: Exception) { throw malformed() }

    fun imageHost(value: String): HttpUrl {
        val url = runCatching { value.toHttpUrl() }.getOrNull() ?: throw malformed()
        if (url.scheme != "https" || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null || url.encodedPath != "/" ||
            !url.host.matches(Regex("cdn-[a-z0-9-]+\\.jmapiproxy[1-4]\\.cc"))) throw malformed()
        return url
    }
    fun id(value: String): String = value.takeIf { it.matches(Regex("[1-9][0-9]{0,11}")) } ?: throw malformed()
    fun filename(value: String): String = value.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,160}\\.(jpg|jpeg|png|webp|gif)", RegexOption.IGNORE_CASE)) } ?: throw malformed()
    fun malformed() = ContentFailure(ContentFailureKind.PARSE, "JM 响应格式发生变化，请等待来源更新")
}

/** Row mapping at original resolution. The final source strip contains the remainder. */
object JmStrips {
    const val VERSION = 1
    fun count(photo: Long, scramble: Long, filename: String, speed: Boolean = false): Int {
        require(photo > 0 && scramble > 0)
        if (speed || filename.endsWith(".gif", true) || photo < scramble) return 0
        if (photo < 268850) return 10
        val last = JmProtocol.md5("$photo${filename.substringBeforeLast('.')}").last().code
        return 2 * (last % if (photo >= 421926) 8 else 10) + 2
    }
    data class Strip(val sourceY: Int, val targetY: Int, val height: Int)
    fun layout(height: Int, count: Int): List<Strip> {
        require(height > 0 && count in 0..20)
        if (count == 0) return listOf(Strip(0, 0, height))
        require(height >= count)
        val size = height / count
        val remainder = height % count
        var target = 0
        return (count - 1 downTo 0).map { index ->
            val rows = size + if (index == count - 1) remainder else 0
            Strip(index * size, target, rows).also { target += rows }
        }
    }
}
