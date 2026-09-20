package io.github.ddmoyu.picomic.auth

import io.github.ddmoyu.picomic.data.Source
import org.junit.Assert.*
import org.junit.Test

class PasswordRecoveryTest {
    @Test fun accountPlatformsUseHttpsAndRejectOtherOriginsAndSchemes() {
        val pages = Source.entries.mapNotNull { passwordRecoveryPage(it) }
        assertEquals(5, pages.size)
        assertNull(passwordRecoveryPage(Source.HITOMI))
        pages.forEach { page ->
            assertTrue(page.allows(page.url.toString()))
            assertFalse(page.allows(page.url.toString().replace("https:", "http:")))
            assertFalse(page.allows("https://${page.url.host}.example.org/"))
            assertFalse(page.allows("https://user:secret@${page.url.host}/"))
            assertFalse(page.allows("javascript:alert(1)"))
            assertFalse(page.allows("file:///data/data/test"))
            assertFalse(page.allows("intent://other-app"))
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
