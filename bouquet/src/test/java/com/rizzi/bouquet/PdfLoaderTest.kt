package com.rizzi.bouquet

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.rizzi.bouquet.internal.PdfLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfLoaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val config = PdfReaderConfig()
    private val payload = "%PDF-1.4 not really a pdf but enough for the loader".toByteArray()

    @Before
    fun cleanCache() {
        PdfLoader.clearCache(context)
    }

    @Test
    fun `base64 source is materialised once and reused`() = runBlocking {
        val source = PdfSource.Base64(Base64.encodeToString(payload, Base64.NO_WRAP))

        val first = PdfLoader.open(context, source, config) {}
        first.fileDescriptor.close()
        val file = first.file!!
        assertTrue(first.isCacheCopy)
        assertTrue(file.path.startsWith(PdfLoader.cacheDir(context).path))
        assertTrue(file.readBytes().contentEquals(payload))
        assertFalse(File(file.path + ".part").exists())

        val second = PdfLoader.open(context, source, config) {}
        second.fileDescriptor.close()
        assertEquals(file, second.file)
        assertEquals(1, PdfLoader.cacheDir(context).listFiles()!!.size)
    }

    @Test
    fun `file source is opened in place`() = runBlocking {
        val file = File(context.cacheDir, "direct.pdf").apply { writeBytes(payload) }

        val opened = PdfLoader.open(context, PdfSource.File(file), config) {}
        opened.fileDescriptor.close()

        assertEquals(file, opened.file)
        assertFalse(opened.isCacheCopy)
        assertTrue(opened.openStream().use { it.readBytes() }.contentEquals(payload))
    }

    @Test
    fun `missing file reports NotFound`() = runBlocking {
        val source = PdfSource.File(File(context.cacheDir, "nope.pdf"))
        try {
            PdfLoader.open(context, source, config) {}
            fail("Expected PdfException.NotFound")
        } catch (e: PdfException.NotFound) {
            assertEquals(source, e.source)
        }
    }

    @Test
    fun `invalid base64 reports Corrupted`() = runBlocking {
        try {
            PdfLoader.open(context, PdfSource.Base64("@@@ not base64 @@@"), config) {}
            fail("Expected PdfException.Corrupted")
        } catch (_: PdfException.Corrupted) {
        }
    }

    @Test
    fun `clearCache removes materialised files`() = runBlocking {
        val opened = PdfLoader.open(context, PdfSource.Base64(Base64.encodeToString(payload, Base64.NO_WRAP)), config) {}
        opened.fileDescriptor.close()
        assertTrue(PdfLoader.cacheDir(context).exists())

        assertTrue(Bouquet.clearCache(context))
        assertFalse(PdfLoader.cacheDir(context).exists())
    }
}
