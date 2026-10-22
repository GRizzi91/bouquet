package com.rizzi.bouquet.text

import android.content.Context
import com.rizzi.bouquet.PdfTextDocument
import com.rizzi.bouquet.PdfTextExtractor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * [PdfTextExtractor] backed by [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android).
 *
 * Text is extracted lazily, one page at a time, the first time it is requested.
 */
public class PdfBoxTextExtractor : PdfTextExtractor {

    override suspend fun open(
        context: Context,
        password: String?,
        openStream: () -> InputStream,
    ): PdfTextDocument = withContext(Dispatchers.IO) {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
        val document = openStream().use { stream ->
            if (password != null) PDDocument.load(stream, password) else PDDocument.load(stream)
        }
        PdfBoxTextDocument(document)
    }
}

private class PdfBoxTextDocument(
    private val document: PDDocument,
) : PdfTextDocument {

    private val mutex = Mutex()
    private val stripper = PDFTextStripper()
    private val cache = HashMap<Int, String>()

    override val pageCount: Int
        get() = document.numberOfPages

    override suspend fun pageText(pageIndex: Int): String {
        require(pageIndex in 0 until pageCount) { "Page $pageIndex out of 0 until $pageCount" }
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                cache.getOrPut(pageIndex) {
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    stripper.getText(document).trim()
                }
            }
        }
    }

    override fun close() {
        document.close()
    }
}
