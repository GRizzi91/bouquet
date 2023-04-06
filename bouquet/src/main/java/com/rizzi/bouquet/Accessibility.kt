package com.rizzi.bouquet

import android.content.Context
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream

/**
 * Retrieves text to be used for page content description.
 *
 * Uses bundled PDFBox library as Android OS does not have a means of stripping text from PDFs.
 */
internal suspend fun getTextByPage(
    context: Context,
    parcelFileDescriptor: ParcelFileDescriptor
): List<String> = withContext(Dispatchers.IO) {
    if (!PDFBoxResourceLoader.isReady()) {
        PDFBoxResourceLoader.init(context)
    }
    val document = PDDocument.load(FileInputStream(parcelFileDescriptor.fileDescriptor))
    val textByPage = mutableListOf<String>()
    val textStripper = PDFTextStripper()
    for (index in 1..document.pages.count) {
        textStripper.startPage = index
        textStripper.endPage = index
        textByPage.add(textStripper.getText(document))
    }
    textByPage
}
