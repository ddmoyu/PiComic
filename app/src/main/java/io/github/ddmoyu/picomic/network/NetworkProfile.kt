package io.github.ddmoyu.picomic.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ProxyCredentials(val username: String, val password: String) {
    init {
        require(username.isNotBlank() && ':' !in username && username.none { it.isISOControl() }) { "代理用户名无效" }
        require(password.none { it.isISOControl() }) { "代理口令无效" }
    }
    override fun toString() = "ProxyCredentials([redacted])"
}

sealed interface NetworkProfile {
    data object FollowSystem : NetworkProfile
    data class HttpProxy(val host: String, val port: Int, val credentials: ProxyCredentials? = null) : NetworkProfile {
        init {
            require(port in 1..65535) { "端口须为 1–65535" }
            require(host.isNotBlank() && host == host.trim() && host.none { it.isWhitespace() || it in "/\\@?#" }) { "请输入主机名或 IP，不包含协议和路径" }
            val url = "http://${authority()}/".toHttpUrlOrNull()
            require(url != null && url.username.isEmpty() && url.password.isEmpty()) { "代理主机无效" }
        }
        fun authority() = "${if (':' in host && !host.startsWith('[')) "[$host]" else host}:$port"
    }
}

data class NetworkStatus(val generation: Long, val profile: NetworkProfile)

fun NetworkProfile.label(): String = when (this) {
    NetworkProfile.FollowSystem -> "跟随系统"
    is NetworkProfile.HttpProxy -> "HTTP 代理 · ${authority()}"
}
