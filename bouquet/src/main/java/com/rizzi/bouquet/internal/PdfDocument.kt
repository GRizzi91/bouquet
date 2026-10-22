package com.rizzi.bouquet.internal

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import com.rizzi.bouquet.PdfException
import com.rizzi.bouquet.PdfTextDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Thread-safe wrapper around [PdfRenderer].
 *
 * `PdfRenderer` must be driven from one thread at a time, so every access goes through
 * [mutex]. Rendering happens on [Dispatchers.Default].
 */
internal class PdfDocument private constructor(
    private val fileDescriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val textDocument: PdfTextDocument?,
) {
    val pageCount: Int = renderer.pageCount

    /** Page sizes in PDF points, filled lazily. Observable from composition. */
    val pageSizes: SnapshotStateList<IntSize?> = mutableStateListOf<IntSize?>().apply {
        repeat(pageCount) { add(null) }
    }

    /** Width / height of the first page whose size is known, or A4 portrait. */
    val defaultAspectRatio: Float
        get() {
            val known = pageSizes.firstOrNull { it != null && it.height > 0 } ?: return A4_ASPECT_RATIO
            return known.width.toFloat() / known.height
        }

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var closed = false

    /** Size in PDF points of the page at [index]. */
    suspend fun pageSize(index: Int): IntSize {
        pageSizes[index]?.let { return it }
        return mutex.withLock {
            pageSizes[index] ?: readPageSizeLocked(index)
        }
    }

    /** Reads every page size in the background so the layout stops jumping as pages appear. */
    fun preloadPageSizes() {
        scope.launch {
            for (index in 0 until pageCount) {
                if (closed) return@launch
                if (pageSizes[index] != null) continue
                mutex.withLock {
                    if (!closed && pageSizes[index] == null) readPageSizeLocked(index)
                }
            }
        }
    }

    /**
     * Renders page [index] into a new bitmap of [width] x [height] pixels.
     *
     * @throws IllegalStateException if the document has been closed.
     */
    suspend fun render(index: Int, width: Int, height: Int, config: Bitmap.Config): Bitmap =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                check(!closed) { "Document is closed" }
                ensureActive()
                val bitmap = createBitmap(width, height, config)
                bitmap.eraseColor(Color.WHITE)
                renderer.openPage(index).use { page ->
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
                bitmap
            }
        }

    /** Text of page [index] when a text extractor is configured, `null` otherwise. */
    suspend fun pageText(index: Int): String? = textDocument?.pageText(index)

    /**
     * Releases the renderer once in-flight work under [mutex] completes. Safe to call more than
     * once and from any thread.
     */
    fun close() {
        if (closed) return
        closed = true
        // Take the lock so we never close while a page is rendering.
        CoroutineScope(Dispatchers.Default).launch {
            mutex.withLock {
                runCatching { renderer.close() }
                runCatching { fileDescriptor.close() }
                runCatching { textDocument?.close() }
            }
            scope.cancel()
        }
    }

    private fun readPageSizeLocked(index: Int): IntSize {
        check(!closed) { "Document is closed" }
        val size = renderer.openPage(index).use { IntSize(it.width, it.height) }
        pageSizes[index] = size
        return size
    }

    companion object {
        private const val A4_ASPECT_RATIO = 0.7071f

        /**
         * Opens [fileDescriptor]; on failure the descriptor is closed and a [PdfException] thrown.
         */
        suspend fun open(
            fileDescriptor: ParcelFileDescriptor,
            password: String?,
            textDocument: PdfTextDocument?,
        ): PdfDocument = withContext(Dispatchers.IO) {
            val renderer = try {
                when {
                    password == null -> PdfRenderer(fileDescriptor)
                    supportsLoadParams() -> openWithPassword(fileDescriptor, password)
                    else -> throw PdfException.Unsupported(
                        "Password-protected PDFs require Android 15 or newer",
                    )
                }
            } catch (e: SecurityException) {
                fileDescriptor.closeQuietly()
                throw PdfException.PasswordRequired(e)
            } catch (e: IOException) {
                fileDescriptor.closeQuietly()
                throw PdfException.Corrupted(e)
            } catch (e: PdfException) {
                fileDescriptor.closeQuietly()
                throw e
            } catch (e: Exception) {
                fileDescriptor.closeQuietly()
                throw PdfException.Unknown(e)
            }
            PdfDocument(fileDescriptor, renderer, textDocument)
        }

        private fun supportsLoadParams(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        private fun openWithPassword(fileDescriptor: ParcelFileDescriptor, password: String): PdfRenderer =
            PdfRenderer(fileDescriptor, LoadParams.Builder().setPassword(password).build())

        private fun ParcelFileDescriptor.closeQuietly() {
            runCatching { close() }
        }
    }
}
