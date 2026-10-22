package com.rizzi.bouquet

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rizzi.bouquet.internal.PdfDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfDocumentTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun openSample(): PdfDocument = runBlocking {
        val file = File(context.cacheDir, "sample-${System.nanoTime()}.pdf")
        context.assets.open("lorem_ipsum.pdf").use { input -> file.outputStream().use { input.copyTo(it) } }
        PdfDocument.open(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), null, null)
    }

    @Test
    fun opensAndReportsPages() = runBlocking {
        val document = openSample()
        try {
            assertTrue(document.pageCount > 0)
            val size = document.pageSize(0)
            assertTrue(size.width > 0 && size.height > 0)
            assertEquals(size, document.pageSizes[0])
        } finally {
            document.close()
        }
    }

    @Test
    fun rendersNonBlankPage() = runBlocking {
        val document = openSample()
        try {
            val bitmap = document.render(0, 300, 420, Bitmap.Config.ARGB_8888)
            assertEquals(300, bitmap.width)
            assertEquals(420, bitmap.height)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("page rendered fully white", pixels.any { it != Color.WHITE })
        } finally {
            document.close()
        }
    }

    @Test
    fun renderingAfterCloseFails() = runBlocking {
        val document = openSample()
        document.close()
        try {
            document.render(0, 10, 10, Bitmap.Config.ARGB_8888)
            fail("Expected IllegalStateException")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun corruptedFileIsReported() = runBlocking {
        val file = File(context.cacheDir, "corrupted-${System.nanoTime()}.pdf").apply { writeText("not a pdf") }
        try {
            PdfDocument.open(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), null, null)
            fail("Expected PdfException.Corrupted")
        } catch (_: PdfException.Corrupted) {
        }
    }
}
