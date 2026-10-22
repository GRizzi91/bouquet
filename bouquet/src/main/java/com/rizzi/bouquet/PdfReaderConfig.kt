package com.rizzi.bouquet

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.rizzi.bouquet.network.OkHttpPdfDownloader
import com.rizzi.bouquet.network.PdfDownloader

/**
 * Options that do not change while a document is open. Pass it when creating a [PdfReaderState].
 *
 * @param zoomEnabled whether pinch and double-tap zoom are active. Can be toggled later through
 *   [PdfReaderState.zoomEnabled].
 * @param minZoom smallest zoom factor; `1f` means "page fits the reader".
 * @param maxZoom largest zoom factor reachable with gestures.
 * @param doubleTapZoom zoom applied by a double tap when the page is not zoomed.
 * @param doubleTapEnabled whether a double tap toggles the zoom.
 * @param password password of an encrypted document. Requires Android 15 or newer; on older devices the reader reports [PdfException.Unsupported].
 * @param textExtractor provides the text of each page for accessibility services. `null`
 *   falls back to a "Page N of M" description. See the `bouquet-text` artifact.
 * @param downloader used for [PdfSource.Url]. Replace it to customise the HTTP client.
 * @param cachePolicy what happens to the local copy of a downloaded / decoded document once the
 *   reader is disposed.
 * @param bitmapConfig pixel format of rendered pages. [Bitmap.Config.RGB_565] halves memory
 *   use at the cost of colour depth.
 * @param memoryBudgetBytes upper bound of the in-memory page bitmap cache.
 * @param prefetchPages how many pages around the current one are rendered ahead of time.
 */
@Immutable
public data class PdfReaderConfig(
    val zoomEnabled: Boolean = true,
    val minZoom: Float = 1f,
    val maxZoom: Float = 4f,
    val doubleTapZoom: Float = 2.5f,
    val doubleTapEnabled: Boolean = true,
    val password: String? = null,
    val textExtractor: PdfTextExtractor? = null,
    val downloader: PdfDownloader = OkHttpPdfDownloader(),
    val cachePolicy: PdfCachePolicy = PdfCachePolicy.Keep,
    val bitmapConfig: Bitmap.Config = Bitmap.Config.ARGB_8888,
    val memoryBudgetBytes: Long = defaultMemoryBudget(),
    val prefetchPages: Int = 1,
) {
    init {
        require(minZoom > 0f) { "minZoom must be positive" }
        require(maxZoom >= minZoom) { "maxZoom must be >= minZoom" }
        require(doubleTapZoom in minZoom..maxZoom) { "doubleTapZoom must be within minZoom..maxZoom" }
        require(prefetchPages >= 0) { "prefetchPages must be >= 0" }
        require(memoryBudgetBytes > 0) { "memoryBudgetBytes must be positive" }
    }

    public companion object {
        /** A quarter of the heap, clamped to 32..256 MB. */
        public fun defaultMemoryBudget(): Long =
            (Runtime.getRuntime().maxMemory() / 4).coerceIn(32L * 1024 * 1024, 256L * 1024 * 1024)
    }
}

/** Lifetime of the local copy Bouquet keeps for downloaded, decoded or bundled documents. */
public enum class PdfCachePolicy {
    /** Keep the file in the app cache so the next open is instant. Use [Bouquet.clearCache] to purge. */
    Keep,

    /** Delete the copy as soon as the reader is disposed. */
    DeleteOnClose,
}
