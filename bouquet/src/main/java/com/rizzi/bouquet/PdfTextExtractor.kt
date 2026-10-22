package com.rizzi.bouquet

import android.content.Context
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.InputStream

/**
 * Extracts the text of PDF pages so it can be exposed to accessibility services
 * (TalkBack reads it as the page's content description) or used for search.
 *
 * The core library does not ship an implementation: Android's [android.graphics.pdf.PdfRenderer]
 * cannot extract text. Add the `io.github.grizzi91:bouquet-text` artifact and use
 * `PdfBoxTextExtractor`, or provide your own.
 */
public interface PdfTextExtractor {

    /**
     * Opens the document for text extraction.
     *
     * @param context an application or activity context, for implementations that need it.
     * @param password password of an encrypted document, or `null`.
     * @param openStream opens a fresh stream over the whole PDF file. The extractor owns the
     *   returned stream and must close it.
     */
    public suspend fun open(
        context: Context,
        password: String?,
        openStream: () -> InputStream,
    ): PdfTextDocument
}

/**
 * A document opened by a [PdfTextExtractor]. Must be closed when no longer needed.
 */
public interface PdfTextDocument : Closeable {

    /** Number of pages in the document. */
    public val pageCount: Int

    /** Returns the text of the page at [pageIndex] (0-based), or an empty string if none. */
    public suspend fun pageText(pageIndex: Int): String
}

/**
 * Reads the text of every page through [extractor], or returns an empty list when no extractor
 * is configured.
 */
internal suspend fun extractTextByPage(
    context: Context,
    extractor: PdfTextExtractor?,
    fileDescriptor: ParcelFileDescriptor,
): List<String> {
    if (extractor == null) return emptyList()
    return withContext(Dispatchers.IO) {
        extractor.open(context, password = null) {
            ParcelFileDescriptor.AutoCloseInputStream(
                ParcelFileDescriptor.dup(fileDescriptor.fileDescriptor)
            )
        }.use { document ->
            List(document.pageCount) { document.pageText(it) }
        }
    }
}
