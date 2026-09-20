package io.github.ddmoyu.picomic

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ddmoyu.picomic.auth.*
import io.github.ddmoyu.picomic.network.*
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NetworkFoundationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun keystoreCiphertextRoundTripsAndRejectsTamperingOrWrongRecord() {
        val key = "test.${UUID.randomUUID()}"
        val other = "$key.other"
        val vault = KeystoreSecretStore(context)
        val file = File(context.noBackupFilesDir, "credentials/$key.enc")
        val otherFile = File(context.noBackupFilesDir, "credentials/$other.enc")
        val payload = "private-fixture-session".toByteArray()
        try {
            vault.write(key, payload)
            val first = file.readBytes()
            assertFalse(String(first).contains("private-fixture-session"))
            assertArrayEquals(payload, KeystoreSecretStore(context).read(key))
            vault.write(key, payload)
            assertFalse(first.contentEquals(file.readBytes()))
            otherFile.writeBytes(file.readBytes())
            assertThrows(Exception::class.java) { vault.read(other) }
            val corrupted = file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            file.writeBytes(corrupted)
            assertThrows(Exception::class.java) { vault.read(key) }
        } finally { vault.remove(key); vault.remove(other) }
        assertNull(vault.read(key))
    }

    @Test fun webViewAndApiBothUseTheConfiguredProxy() = runBlocking {
        MockWebServer().use { proxy ->
            repeat(6) { proxy.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>route fixture</html>")) }
            proxy.start()
            val profile = NetworkProfile.HttpProxy("127.0.0.1", proxy.port)
            val engine = NetworkEngine(profile)
            engine.newCall(Request.Builder().url("http://route-fixture.test/api").build()).execute().close()
            assertTrue(proxy.takeRequest(5, TimeUnit.SECONDS)!!.requestLine.contains("http://route-fixture.test/api"))
            if (!androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.PROXY_OVERRIDE)) {
                val error = withContext(Dispatchers.Main) { runCatching { WebLoginRuntime.applyWebProxy(profile) }.exceptionOrNull() }
                assertTrue(error is IllegalStateException)
                assertTrue(error!!.message!!.contains("不支持自定义代理"))
                assertNull(proxy.takeRequest(200, TimeUnit.MILLISECONDS))
                return@use // Old WebView must reject the custom route, never silently connect directly.
            }
            val loaded = CompletableDeferred<Unit>()
            var browser: WebView? = null
            try {
                withContext(Dispatchers.Main) {
                    WebLoginRuntime.applyWebProxy(profile)
                    browser = WebView(context).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) { loaded.complete(Unit) }
                        }
                        loadUrl("http://route-fixture.test/web")
                    }
                }
                withTimeout(15000) { loaded.await() }
                assertTrue(proxy.takeRequest(5, TimeUnit.SECONDS)!!.requestLine.contains("http://route-fixture.test/web"))
            } finally {
                withContext(Dispatchers.Main + NonCancellable) {
                    browser?.destroy()
                    WebLoginRuntime.applyWebProxy(NetworkProfile.FollowSystem)
                }
            }
        }
    }

    @Test fun controlledWebViewExtractsHttpOnlyCookieAndCleansUpOnClose() = runBlocking {
        val url = "https://login-fixture.example.test/".toHttpUrl()
        val spec = WebLoginSpec(url, setOf(url.origin()), url, listOf(WebCookieScope("fixture_session", "/")))
        val detected = CompletableDeferred<String>()
        WebLoginRuntime.withLogin(context, spec, NetworkEngine(), { true }, {
            detected.complete(String(it.value)); it.value.fill(0)
        }, { throw AssertionError(it) }) { view ->
            val cookiesSet = CompletableDeferred<Unit>()
            CookieManager.getInstance().setCookie(url.toString(), "fixture_session=verified-fixture; Path=/; Secure; HttpOnly") {
                if (it) cookiesSet.complete(Unit) else cookiesSet.completeExceptionally(AssertionError("Fixture cookie rejected"))
            }
            cookiesSet.await()
            assertTrue(CookieManager.getInstance().getCookie(url.toString()).orEmpty().contains("fixture_session="))
            // Give the local HTML a trusted history URL as well as a base URL; otherwise getUrl() is about:blank.
            view.loadDataWithBaseURL(url.toString(), "<html><body>fixture login</body></html>", "text/html", "UTF-8", url.toString())
            val result = withTimeoutOrNull(15000) { detected.await() }
            assertNotNull("Expected automatic candidate from trusted page, actual URL=${view.url}", result)
            assertEquals("fixture_session=verified-fixture", result)
        }
        withContext(Dispatchers.Main) {
            assertFalse(CookieManager.getInstance().getCookie(url.toString()).orEmpty().contains("fixture_session="))
        }
    }

    @Test fun changingNetworkDisposesControlledWebViewBeforeLateCandidateCanCommit() = runBlocking {
        val url = "https://login-fixture.example.test/".toHttpUrl()
        val spec = WebLoginSpec(url, setOf(url.origin()), url, listOf(WebCookieScope("fixture_session", "/")))
        val engine = NetworkEngine()
        val opened = CompletableDeferred<Unit>()
        val job = launch {
            WebLoginRuntime.withLogin(context, spec, engine, { true }, { fail("No session should be detected") }, {}) {
                opened.complete(Unit); awaitCancellation()
            }
        }
        opened.await(); engine.change(NetworkProfile.FollowSystem)
        withTimeout(10000) { job.join() }
        assertTrue(job.isCancelled)
    }
}
