package com.rizzi.bouquet

import android.net.Uri
import android.os.Parcel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfSourceParcelTest {

    @Test
    fun `every source survives a parcel round trip`() {
        val sources = listOf(
            PdfSource.Uri(Uri.parse("content://com.example/docs/1")),
            PdfSource.File(File("/tmp/a.pdf")),
            PdfSource.Url("https://example.com/a.pdf", mapOf("Authorization" to "Bearer x")),
            PdfSource.RawRes(0x7f0a0001),
            PdfSource.Asset("docs/a.pdf"),
            PdfSource.Base64("JVBERi0xLjQK"),
        )
        for (source in sources) {
            assertEquals(source, roundTrip(source))
        }
    }

    @Test
    fun `base64 toString does not dump the payload`() {
        val source = PdfSource.Base64("A".repeat(10_000))
        assertEquals("Base64(length=10000)", source.toString())
    }

    @Suppress("DEPRECATION")
    private fun roundTrip(source: PdfSource): PdfSource {
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(source, 0)
            parcel.setDataPosition(0)
            return parcel.readParcelable(PdfSource::class.java.classLoader)!!
        } finally {
            parcel.recycle()
        }
    }
}
