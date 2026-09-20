package io.github.ddmoyu.picomic.update

/** Reuse only metadata that passed the same protocol validation when read from disk. */
object UpdateChecker {
    suspend fun check(client: GitHubUpdateClient, store: UpdateStore): UpdateBundle {
        val cache = runCatching { store.read("cache.json") }.getOrNull()
        var reply = client.release(etag = cache?.etag, etagRoute = cache?.route ?: UpdateRoute.GITHUB.key)
        if (reply.bytes == null && (cache == null || cache.etag == null || cache.route != reply.route)) {
            reply = client.release()
            // An unconditional retry must return a body even if it returns to the cached route.
            if (reply.bytes == null) throw UpdateFailure("更新服务返回了无效缓存响应")
        }
        val bundle = if (reply.bytes == null) cache?.bundle ?: throw UpdateFailure("更新服务返回了无效缓存响应") else client.bundle(reply.bytes)
        store.write("cache.json", UpdateStore.Saved(bundle, etag = reply.etag ?: if (reply.bytes == null) cache?.etag else null, route = reply.route))
        return bundle
    }
}
