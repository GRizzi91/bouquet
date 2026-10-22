package com.rizzi.bouquet

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.rizzi.bouquet.internal.PageBitmapCache
import com.rizzi.bouquet.internal.PdfDocument
import com.rizzi.bouquet.internal.PdfLoader
import com.rizzi.bouquet.internal.ZoomMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

/**
 * State shared by [VerticalPdfReader] and [HorizontalPdfReader].
 *
 * Create it with [rememberVerticalPdfReaderState] / [rememberHorizontalPdfReaderState], or hold
 * it in a `ViewModel` and pass it to the reader. The document is opened when a reader (or a
 * [PdfPage]) using this state enters the composition and released when the last one leaves.
 */
@Stable
public sealed class PdfReaderState(
    initialSource: PdfSource,
    public val config: PdfReaderConfig,
) {
    /** The document currently shown. Change it with [load]. */
    public var source: PdfSource by mutableStateOf(initialSource)
        private set

    /** Where the document is in its loading lifecycle. */
    public var loadState: PdfLoadState by mutableStateOf(PdfLoadState.Idle)
        internal set

    /** `true` once pages can be rendered. */
    public val isLoaded: Boolean
        get() = loadState is PdfLoadState.Loaded

    /** The failure reported by the last load attempt, if any. */
    public val error: PdfException?
        get() = (loadState as? PdfLoadState.Error)?.exception

    /** Download progress in `0f..1f` while [loadState] is [PdfLoadState.Downloading], otherwise `null`. */
    public val downloadProgress: Float?
        get() = (loadState as? PdfLoadState.Downloading)?.progress

    /** Number of pages of the open document, `0` until loaded. */
    public val pageCount: Int
        get() = (loadState as? PdfLoadState.Loaded)?.pageCount ?: 0

    /**
     * Local file backing the document, useful for sharing.
     *
     * `null` for `content://` URIs (share the URI itself) and until the document is loaded.
     */
    public var file: File? by mutableStateOf(null)
        internal set

    /** Current zoom factor; `1f` means the page fits the reader. */
    public var zoom: Float by mutableFloatStateOf(config.minZoom)
        internal set

    /** Enables or disables zoom gestures at runtime. */
    public var zoomEnabled: Boolean by mutableStateOf(config.zoomEnabled)

    /** `true` while a pinch gesture is in progress. Pages are re-rendered when it ends. */
    public var isTransforming: Boolean by mutableStateOf(false)
        internal set

    /** Index (0-based) of the page considered current. */
    public abstract val currentPage: Int

    /** `true` while the content is being scrolled or panned. */
    public abstract val isScrolling: Boolean

    /** `true` if the reader can still move towards the last page. */
    public abstract val canScrollForward: Boolean

    /** `true` if the reader can still move towards the first page. */
    public abstract val canScrollBackward: Boolean

    /** Jumps to [page] (0-based, clamped to the document). */
    public abstract suspend fun scrollToPage(page: Int)

    /** Smoothly scrolls to [page] (0-based, clamped to the document). */
    public abstract suspend fun animateScrollToPage(page: Int)

    /**
     * Replaces the document. The current one is released and [source] is opened as soon as a
     * reader is in the composition. Scroll position and zoom are reset.
     */
    public fun load(source: PdfSource) {
        if (source == this.source && error == null) return
        this.source = source
        resetZoom()
        resetScroll()
        loadGeneration++
    }

    /** Retries opening [source] after an [error]. */
    public fun retry() {
        loadGeneration++
    }

    /**
     * Sets the zoom immediately.
     *
     * @param pivot point of the reader, in pixels, that should stay still. Defaults to the centre.
     */
    public fun setZoom(zoom: Float, pivot: Offset? = null) {
        applyZoom(zoom.coerceIn(config.minZoom, config.maxZoom), pivot ?: viewportCenter())
        snapIfUnzoomed()
    }

    /** Animates the zoom to [zoom]; see [setZoom]. */
    public suspend fun animateZoom(zoom: Float, pivot: Offset? = null) {
        val target = zoom.coerceIn(config.minZoom, config.maxZoom)
        val anchor = pivot ?: viewportCenter()
        isTransforming = true
        try {
            Animatable(this.zoom).animateTo(target, tween(ZOOM_ANIMATION_MS)) {
                applyZoom(value, anchor)
            }
        } finally {
            isTransforming = false
            snapIfUnzoomed()
        }
    }

    /** Returns to [PdfReaderConfig.minZoom]. */
    public fun resetZoom() {
        zoom = config.minZoom
        offset = Offset.Zero
    }

    // ---------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------

    /** Translation of the zoomed content, in pixels, with the origin at the top-left corner. */
    internal var offset: Offset by mutableStateOf(Offset.Zero)

    /** `true` while a single-finger pan of zoomed content is in progress. */
    internal var isPanning: Boolean by mutableStateOf(false)

    /** Size of the reader, kept up to date by the composable. */
    internal var viewportSize: IntSize by mutableStateOf(IntSize.Zero)

    internal var document: PdfDocument? by mutableStateOf(null)
        private set

    internal val bitmapCache: PageBitmapCache = PageBitmapCache(config.memoryBudgetBytes)

    internal var loadGeneration: Int by mutableIntStateOf(0)
        private set

    internal abstract val scrollableState: ScrollableState

    internal abstract fun resetScroll()

    private val loadMutex = Mutex()
    private var loadedSource: PdfSource? = null
    private var loadedGeneration = -1
    private var loadedIsCacheCopy = false
    private var attachCount = 0

    internal fun attach() {
        attachCount++
    }

    internal fun detach() {
        attachCount--
        if (attachCount <= 0) {
            attachCount = 0
            closeDocument()
        }
    }

    /** Opens [source] unless it is already open. Safe to call repeatedly. */
    internal suspend fun ensureLoaded(context: Context) {
        loadMutex.withLock {
            val source = source
            val generation = loadGeneration
            if (document != null && loadedSource == source && loadedGeneration == generation) return

            closeDocument()
            loadState = if (source is PdfSource.Url) PdfLoadState.Downloading(null) else PdfLoadState.Opening

            var opened: PdfLoader.OpenedPdf? = null
            try {
                opened = PdfLoader.open(context, source, config) { progress ->
                    loadState = PdfLoadState.Downloading(progress)
                }
                loadState = PdfLoadState.Opening
                val textDocument = config.textExtractor?.let { extractor ->
                    runCatching { extractor.open(context, config.password, opened.openStream) }.getOrNull()
                }
                val document = PdfDocument.open(opened.fileDescriptor, config.password, textDocument)
                if (attachCount <= 0) {
                    // The reader left the composition while we were opening.
                    document.close()
                    loadState = PdfLoadState.Idle
                    return
                }
                this.document = document
                file = opened.file
                loadedSource = source
                loadedGeneration = generation
                loadedIsCacheCopy = opened.isCacheCopy
                loadState = PdfLoadState.Loaded(document.pageCount)
                document.preloadPageSizes()
            } catch (e: CancellationException) {
                opened?.fileDescriptor?.let { runCatching { it.close() } }
                throw e
            } catch (e: Throwable) {
                opened?.fileDescriptor?.let { runCatching { it.close() } }
                loadState = PdfLoadState.Error(e.toPdfException(source))
            }
        }
    }

    private fun closeDocument() {
        val current = document ?: return
        document = null
        current.close()
        bitmapCache.clear()
        if (config.cachePolicy == PdfCachePolicy.DeleteOnClose && loadedIsCacheCopy) {
            file?.delete()
        }
        file = null
        loadedSource = null
        loadedGeneration = -1
        if (loadState !is PdfLoadState.Error) loadState = PdfLoadState.Idle
    }

    /** Called during a pinch: [zoomChange] is the factor since the previous event. */
    internal fun applyPinch(zoomChange: Float, pan: Offset, centroid: Offset) {
        val newZoom = (zoom * zoomChange).coerceIn(config.minZoom, config.maxZoom)
        val newOffset = ZoomMath.zoomAround(zoom, newZoom, offset, centroid) + pan
        zoom = newZoom
        offset = ZoomMath.clampOffset(newOffset, newZoom, viewportSize)
    }

    /**
     * Pans zoomed content by [pan] and returns the part of the movement that could not be
     * applied because the content edge was reached.
     */
    internal fun applyPan(pan: Offset): Offset {
        val desired = offset + pan
        val clamped = ZoomMath.clampOffset(desired, zoom, viewportSize)
        offset = clamped
        return desired - clamped
    }

    internal fun endGesture() {
        isTransforming = false
        isPanning = false
        snapIfUnzoomed()
    }

    internal fun onViewportSizeChanged(size: IntSize) {
        if (size == viewportSize) return
        viewportSize = size
        offset = ZoomMath.clampOffset(offset, zoom, size)
    }

    internal suspend fun toggleZoom(pivot: Offset) {
        if (!zoomEnabled || !config.doubleTapEnabled) return
        if (ZoomMath.isZoomed(zoom, config.minZoom)) {
            animateZoom(config.minZoom, pivot)
        } else {
            animateZoom(config.doubleTapZoom, pivot)
        }
    }

    private fun applyZoom(newZoom: Float, pivot: Offset) {
        val newOffset = ZoomMath.zoomAround(zoom, newZoom, offset, pivot)
        zoom = newZoom
        offset = ZoomMath.clampOffset(newOffset, newZoom, viewportSize)
    }

    private fun snapIfUnzoomed() {
        if (!ZoomMath.isZoomed(zoom, config.minZoom)) resetZoom()
    }

    private fun viewportCenter() = Offset(viewportSize.width / 2f, viewportSize.height / 2f)

    private companion object {
        const val ZOOM_ANIMATION_MS = 250
    }
}

private fun Throwable.toPdfException(source: PdfSource): PdfException = when (this) {
    is PdfException -> this
    is SecurityException -> PdfException.PasswordRequired(this)
    is IOException -> when (source) {
        is PdfSource.Url -> PdfException.Network(source.url, null, this)
        else -> PdfException.Corrupted(this)
    }
    else -> PdfException.Unknown(this)
}
