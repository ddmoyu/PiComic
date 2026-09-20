package io.github.ddmoyu.picomic.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** All request categories share this routing generation, never source credentials. */
class NetworkEngine(initialProfile: NetworkProfile = NetworkProfile.FollowSystem) : Call.Factory {
    private val lock = Any()
    @Volatile var status = NetworkStatus(0, initialProfile)
        private set
    private var client = build(status)
    private val mutableGeneration = MutableStateFlow(0L)
    val generations = mutableGeneration.asStateFlow()

    fun change(profile: NetworkProfile): NetworkStatus = synchronized(lock) {
        val next = NetworkStatus(status.generation + 1, profile)
        val replacement = build(next)
        val previous = client
        status = next
        client = replacement
        previous.dispatcher.cancelAll()
        previous.connectionPool.evictAll()
        mutableGeneration.value = next.generation
        next
    }

    fun <T> withGeneration(expected: Long, action: () -> T): T = synchronized(lock) {
        if (expected != status.generation) throw StaleNetworkException()
        action()
    }

    override fun newCall(request: Request): Call = synchronized(lock) { client.newCall(request) }

    /** An isolated source/candidate client; redirects are surfaced to the adapter for explicit handling. */
    fun scopedClient(origins: Set<String>, cookieJar: CookieJar = CookieJar.NO_COOKIES): OkHttpClient = synchronized(lock) {
        require(origins.isNotEmpty())
        client.newBuilder().cookieJar(cookieJar).addInterceptor { chain ->
            if (chain.request().url.origin() !in origins) throw IOException("请求地址不在来源信任范围内")
            chain.proceed(chain.request())
        }.build()
    }

    private fun build(snapshot: NetworkStatus): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            // In particular, never silently replay login POSTs or forward tokens on redirects.
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
            .addInterceptor { chain ->
                withGeneration(snapshot.generation) { }
                val response = chain.proceed(chain.request())
                try { withGeneration(snapshot.generation) { response } }
                catch (error: StaleNetworkException) { response.close(); throw error }
            }
        val profile = snapshot.profile
        if (profile is NetworkProfile.HttpProxy) {
            val host = profile.host.removeSurrounding("[", "]")
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, profile.port)))
            profile.credentials?.let { credentials ->
                builder.proxyAuthenticator { route, response ->
                    val address = route?.proxy?.address() as? InetSocketAddress
                    if (address?.hostString != host || address.port != profile.port ||
                        response.request.header("Proxy-Authorization") != null) null
                    else response.request.newBuilder()
                        .header("Proxy-Authorization", Credentials.basic(credentials.username, credentials.password)).build()
                }
            }
        }
        // FollowSystem deliberately leaves the platform ProxySelector and VPN routing intact.
        return builder.build()
    }
}

class StaleNetworkException : IOException("网络设置已更改，请重试")
fun HttpUrl.origin() = "$scheme://${if (':' in host) "[$host]" else host}:$port"

suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}
