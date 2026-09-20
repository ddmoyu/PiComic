package io.github.ddmoyu.picomic.source.picacg

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Published application protocol parameters, not a user's credential. See THIRD_PARTY_NOTICES. */
internal object PicacgProtocol {
    const val VERSION = "picacg-2.2.1.3.3.4-v1"
    val api: HttpUrl = "https://picaapi.picacomic.com/".toHttpUrl()
    private const val API_KEY = "C69BAF41DA5ABD1FFEDC6D2FEA56B"
    private const val SIGNING_KEY = "~d}\$Q7\$eIni=V)9\\RK/P.RM4;9[7|@/CA}b~OW!3?EV`:<>M7pddUBL5n|0/*Cn"

    fun signature(url: HttpUrl, method: String, timestamp: String, nonce: String): String {
        val target = url.encodedPath.removePrefix("/") + (url.encodedQuery?.let { "?$it" } ?: "")
        val input = (target + timestamp + nonce + method + API_KEY).lowercase(Locale.ROOT)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SIGNING_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun headers(builder: Request.Builder, url: HttpUrl, method: String, token: String? = null): Request.Builder {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        builder.header("api-key", API_KEY).header("accept", "application/vnd.picacomic.com.v1+json")
            .header("app-channel", "3").header("time", timestamp).header("nonce", nonce)
            .header("signature", signature(url, method, timestamp, nonce))
            .header("app-version", "2.2.1.3.3.4").header("app-uuid", "defaultUuid")
            .header("app-platform", "android").header("app-build-version", "45")
            .header("image-quality", "original").header("user-agent", "okhttp/3.8.1")
            .header("version", "v1.4.1")
        if (token != null) builder.header("authorization", token)
        return builder
    }
}
