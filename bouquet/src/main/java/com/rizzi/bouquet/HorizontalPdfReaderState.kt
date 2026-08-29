package com.rizzi.bouquet

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * State of a [HorizontalPdfReader]: one page at a time, swiped like a book.
 *
 * @param initialPage page shown first (0-based).
 */
public class HorizontalPdfReaderState(
    source: PdfSource,
    config: PdfReaderConfig = PdfReaderConfig(),
    initialPage: Int = 0,
) : PdfReaderState(source, config) {

    @Deprecated(
        "Use the primary constructor with PdfReaderConfig",
        ReplaceWith("HorizontalPdfReaderState(source, PdfReaderConfig(zoomEnabled = isZoomEnable))"),
    )
    public constructor(
        resource: PdfSource,
        isZoomEnable: Boolean,
        isAccessibleEnable: Boolean = false,
    ) : this(resource, PdfReaderConfig(zoomEnabled = isZoomEnable))

    /** The underlying pager state, exposed for advanced use. */
    public val pagerState: PagerState = PagerState(currentPage = initialPage) { pageCount }

    override val currentPage: Int
        get() = pagerState.currentPage

    override val isScrolling: Boolean
        get() = pagerState.isScrollInProgress || isPanning

    override val canScrollForward: Boolean
        get() = pagerState.canScrollForward

    override val canScrollBackward: Boolean
        get() = pagerState.canScrollBackward

    override suspend fun scrollToPage(page: Int) {
        pagerState.scrollToPage(page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
    }

    override suspend fun animateScrollToPage(page: Int) {
        pagerState.animateScrollToPage(page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
    }

    override val scrollableState: ScrollableState
        get() = pagerState

    override fun resetScroll() {
        pagerState.requestScrollToPage(0)
    }

    public companion object {
        /** Saver used by [rememberHorizontalPdfReaderState]; keeps source and current page. */
        public fun Saver(config: PdfReaderConfig): Saver<HorizontalPdfReaderState, *> = listSaver(
            save = { listOf(it.source, it.pagerState.currentPage) },
            restore = {
                HorizontalPdfReaderState(
                    source = it[0] as PdfSource,
                    config = config,
                    initialPage = it[1] as Int,
                )
            },
        )
    }
}

/**
 * Creates and remembers a [HorizontalPdfReaderState] that survives configuration changes and
 * process death. When [source] changes, the new document is loaded.
 */
@Composable
public fun rememberHorizontalPdfReaderState(
    source: PdfSource,
    config: PdfReaderConfig = PdfReaderConfig(),
    initialPage: Int = 0,
): HorizontalPdfReaderState {
    val state = rememberSaveable(saver = HorizontalPdfReaderState.Saver(config)) {
        HorizontalPdfReaderState(source, config, initialPage)
    }
    LaunchedEffect(state, source) { state.load(source) }
    return state
}

@Deprecated(
    "Use rememberHorizontalPdfReaderState(source, PdfReaderConfig(...))",
    ReplaceWith("rememberHorizontalPdfReaderState(resource, PdfReaderConfig(zoomEnabled = isZoomEnable))"),
)
@Composable
public fun rememberHorizontalPdfReaderState(
    resource: PdfSource,
    isZoomEnable: Boolean,
    isAccessibleEnable: Boolean = false,
): HorizontalPdfReaderState = rememberHorizontalPdfReaderState(
    source = resource,
    config = PdfReaderConfig(zoomEnabled = isZoomEnable),
)
