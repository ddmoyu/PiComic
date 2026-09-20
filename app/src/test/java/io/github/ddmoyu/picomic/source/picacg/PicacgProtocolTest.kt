package io.github.ddmoyu.picomic.source.picacg

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PicacgProtocolTest {
    @Test fun signatureMatchesIndependentHmacVectorAndDoesNotDependOnLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("1cb16a70906fdf65fe946f42344a1f2cf532329a90c19e7327087f21c5cd78a1",
                PicacgProtocol.signature("https://picaapi.picacomic.com/users/profile".toHttpUrl(), "GET", "1700000000", "0123456789abcdef0123456789abcdef"))
        } finally { Locale.setDefault(previous) }
    }
    @Test fun signatureIncludesEncodedQueryAndMethodWithoutHostOrLeadingSlash() {
        fun sign(url: String, method: String = "GET") = PicacgProtocol.signature(url.toHttpUrl(), method, "1700000000", "fixture")
        assertEquals(sign("https://picaapi.picacomic.com/TEST?q=%E4%B8%AD&page=2"), sign("https://local.test/TEST?q=%E4%B8%AD&page=2"))
        assertNotEquals(sign("https://local.test/search?page=1"), sign("https://local.test/search?page=2"))
        assertNotEquals(sign("https://local.test/search"), sign("https://local.test/search", "POST"))
        assertNotEquals(sign("https://local.test/search?q=a%2Bb"), sign("https://local.test/search?q=a+b"))
    }
}
