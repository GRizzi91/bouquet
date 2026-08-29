package com.rizzi.bouquet

import android.content.Context
import com.rizzi.bouquet.internal.PdfLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Library-wide utilities. */
public object Bouquet {

    /** Directory under the app cache where downloaded, decoded and bundled documents are kept. */
    public fun cacheDir(context: Context): File = PdfLoader.cacheDir(context)

    /**
     * Deletes every document Bouquet materialised in the app cache. Documents currently open
     * keep working until their reader is disposed.
     */
    public suspend fun clearCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        PdfLoader.clearCache(context)
    }
}
