package io.github.ddmoyu.picomic.auth

import io.github.ddmoyu.picomic.data.Source
import io.github.ddmoyu.picomic.source.ht.HtRoutes
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal data class RecoveryPage(val url: HttpUrl, val hint: String? = null) {
    init { require(url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty()) }
}

internal fun passwordRecoveryPage(source: Source, htHost: String = "www.wn10.shop"): RecoveryPage? = when (source) {
    // No verified independent reset URL: use the platform's account entry instead of guessing one.
    Source.PICACG -> RecoveryPage("https://manhuabika.com/plogin/".toHttpUrl(),
        "若哔咔网页未提供找回密码选项，请使用官方客户端找回。")
    Source.JMCOMIC -> RecoveryPage("https://18comic.vip/login".toHttpUrl(),
        "请在平台账号页面选择「忘记密码」，并按网页提示完成验证。")
    Source.EHENTAI -> RecoveryPage("https://forums.e-hentai.org/index.php?act=Reg&CODE=10".toHttpUrl())
    Source.NHENTAI -> RecoveryPage("https://nhentai.net/reset-password/".toHttpUrl())
    Source.HTCOMIC -> {
        require(HtRoutes.validHost(htHost))
        RecoveryPage("https://$htHost/?ctl=users&act=getpass".toHttpUrl())
    }
    Source.HITOMI -> null
}
