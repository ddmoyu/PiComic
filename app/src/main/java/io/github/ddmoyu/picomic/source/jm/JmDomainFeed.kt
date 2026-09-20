package io.github.ddmoyu.picomic.source.jm

import io.github.ddmoyu.picomic.network.*
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Publisher data is only a candidate list; JmRoutes probes the current protocol before trusting it. */
object JmDomainFeed {
    const val URL = "https://rup4a04-c02.tos-cn-hongkong.bytepluses.com/newsvr-2025.txt"
    private const val DOMAIN_KEY = "diosfjckwpqpdfjkvnqQjsik" // Public publisher protocol constant.
    fun validHost(host: String) = host.matches(Regex("www\\.cdn[a-z0-9]{2,24}\\.(net|org|cc|club|me|com)"))
    fun decode(bytes: ByteArray): List<String> {
        require(bytes.size in 1..65536)
        val encrypted = Base64.getDecoder().decode(bytes.toString(Charsets.US_ASCII).trim())
        require(encrypted.isNotEmpty() && encrypted.size % 16 == 0)
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(JmProtocol.md5(DOMAIN_KEY).toByteArray(Charsets.US_ASCII), "AES"))
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(cipher.doFinal(encrypted))).toString()
        require(text.count { it == '[' || it == '{' } <= 64)
        val parser = JSONTokener(text); val root = parser.nextValue() as? JSONObject ?: throw JmProtocol.malformed()
        require(parser.nextClean() == '\u0000')
        val servers = root.getJSONArray("Server"); require(servers.length() in 1..32)
        return (0 until servers.length()).map { servers.get(it) as? String ?: throw JmProtocol.malformed() }
            .filter(::validHost).distinct().take(8).also { require(it.isNotEmpty()) }
    }
    suspend fun fetch(engine: NetworkEngine): List<String> {
        val bytes = engine.scopedClient(setOf("https://rup4a04-c02.tos-cn-hongkong.bytepluses.com:443"))
            .newCall(Request.Builder().url(URL).header("User-Agent", JmProtocol.USER_AGENT).build()).readBounded(65536) { require(it.code == 200) }
        return decode(bytes)
    }
}
