package com.rizzi.bouquet

import com.rizzi.bouquet.internal.PdfLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheNamingTest {

    @Test
    fun `sha256 matches the reference vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            PdfLoader.sha256("abc"),
        )
    }

    @Test
    fun `cache file names are deterministic and filesystem safe`() {
        val name = PdfLoader.cacheFileName("url", "https://example.com/some file?x=1&y=2")
        assertEquals(name, PdfLoader.cacheFileName("url", "https://example.com/some file?x=1&y=2"))
        assertTrue(name.matches(Regex("url-[0-9a-f]{64}\\.pdf")))
    }

    @Test
    fun `different sources get different names`() {
        assertNotEquals(
            PdfLoader.cacheFileName("url", "https://example.com/a.pdf"),
            PdfLoader.cacheFileName("url", "https://example.com/b.pdf"),
        )
        assertNotEquals(
            PdfLoader.cacheFileName("url", "same"),
            PdfLoader.cacheFileName("b64", "same"),
        )
    }
}
