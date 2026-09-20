package io.github.ddmoyu.picomic

import io.github.ddmoyu.picomic.source.html.*
import io.github.ddmoyu.picomic.source.ht.HtClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class HtmlSupportTest {
    @Test fun trailingCommaNormalizationNeverRewritesStrings() {
        assertEquals("""{"note":",]", "array":["x"]}""", withoutTrailingCommas("""{"note":",]", "array":["x",],}"""))
        assertTrue(runCatching { jsonArgument("f(" + "{".repeat(65) + "}".repeat(65) + ")", "f(", "fixture") }.isFailure)
    }
    @Test fun jsonScannerRespectsStringsAndRequiresClosingCall() {
        val json = """{"note":"} [ \\\"", "nested":{"a":[1,2]}}"""
        assertEquals(json, jsonArgument("callback($json);", "callback(", "fixture"))
        for (bad in listOf("callback({x:1} + evil())", "callback({\"a\":\"unterminated}")) assertTrue(runCatching { jsonArgument(bad, "callback(", "fixture") }.isFailure)
    }
    @Test fun imageTrustRejectsDomainSuffixTricksPortsAndUserInfo() {
        HtClient.validateImage("https://img5.wnimg2.cfd/image.webp".toHttpUrl())
        for (bad in listOf("https://img5.wnimg2.cfd.evil.test/x", "https://img5.wnimg2.cfd:8443/x", "https://user@img5.wnimg2.cfd/x", "http://img5.wnimg2.cfd/x")) assertTrue(runCatching { HtClient.validateImage(bad.toHttpUrl()) }.isFailure)
    }
}
