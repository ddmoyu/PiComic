package io.github.ddmoyu.picomic.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cancellation remains connected through body consumption; never includes response data in errors. */
suspend fun Call.readBounded(limit: Long = 2 * 1024 * 1024, check: (Response) -> Unit = { }): ByteArray =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        check(it)
                        val source = it.body.source()
                        if (source.request(limit + 1)) throw IOException("响应内容超过允许大小")
                        source.readByteArray()
                    }
                    continuation.resume(bytes)
                } catch (e: Exception) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
            }
        })
    }
