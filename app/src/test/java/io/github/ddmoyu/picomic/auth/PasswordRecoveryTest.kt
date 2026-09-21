package io.github.ddmoyu.picomic.auth

import io.github.ddmoyu.picomic.data.Source
import org.junit.Assert.*
import org.junit.Test

class PasswordRecoveryTest {
    @Test fun accountPlatformsUseHttpsWithoutCredentials() {
        val pages = Source.entries.mapNotNull { passwordRecoveryPage(it) }
        assertEquals(5, pages.size)
        assertNull(passwordRecoveryPage(Source.HITOMI))
        pages.forEach { page ->
            assertTrue(page.url.isHttps)
            assertEquals(443, page.url.port)
            assertEquals("", page.url.username)
            assertEquals("", page.url.password)
        }
        assertEquals("/reset-password/", passwordRecoveryPage(Source.NHENTAI)!!.url.encodedPath)
        assertEquals("10", passwordRecoveryPage(Source.EHENTAI)!!.url.queryParameter("CODE"))
    }

    @Test fun wnacgUsesTheSelectedTrustedDomain() {
        val page = passwordRecoveryPage(Source.HTCOMIC, "www.wn10.cfd")!!
        assertEquals("www.wn10.cfd", page.url.host)
        assertEquals("getpass", page.url.queryParameter("act"))
        assertThrows(IllegalArgumentException::class.java) { passwordRecoveryPage(Source.HTCOMIC, "untrusted.example") }
    }
}
