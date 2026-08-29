package com.rizzi.bouquet

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * State of a [VerticalPdfReader]: pages stacked in a scrolling column.
 *
 * @param initialPage page shown first (0-based).
 */
public class VerticalPdfReaderState(
    source: PdfSource,
    config: PdfReaderConfig = PdfReaderConfig(),
    initialPage: Int = 0,
    initialPageOffset: Int = 0,
) : PdfReaderState(source, config) {

    @Deprecated(
        "Use the primary constructor with PdfReaderConfig",
        ReplaceWith("VerticalPdfReaderState(source, PdfReaderConfig(zoomEnabled = isZoomEnable))"),
    )
    public constructor(
        resource: PdfSource,
        isZoomEnable: Boolean,
        isAccessibleEnable: Boolean = false,
    ) : this(resource, PdfReaderConfig(zoomEnabled = isZoomEnable))

    /** The underlying list state, exposed for advanced use (e.g. observing `layoutInfo`). */
    public val listState: LazyListState = LazyListState(initialPage, initialPageOffset)

    /**
     * The page covering the vertical centre of the reader, or the first visible page when the
     * document is shorter than the reader.
     */
    override val currentPage: Int
        get() {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) return listState.firstVisibleItemIndex
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            return visible.firstOrNull { center >= it.offset && center < it.offset + it.size }?.index
                ?: visible.first().index
        }

    override val isScrolling: Boolean
        get() = listState.isScrollInProgress || isPanning

    override val canScrollForward: Boolean
        get() = listState.canScrollForward

    override val canScrollBackward: Boolean
        get() = listState.canScrollBackward

    override suspend fun scrollToPage(page: Int) {
        listState.scrollToItem(page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
    }

    override suspend fun animateScrollToPage(page: Int) {
        listState.animateScrollToItem(page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
    }

    override val scrollableState: ScrollableState
        get() = listState

    override fun resetScroll() {
        listState.requestScrollToItem(0)
    }

    public companion object {
        /** Saver used by [rememberVerticalPdfReaderState]; keeps source and scroll position. */
        public fun Saver(config: PdfReaderConfig): Saver<VerticalPdfReaderState, *> = listSaver(
            save = {
                listOf(
                    it.source,
                    it.listState.firstVisibleItemIndex,
                    it.listState.firstVisibleItemScrollOffset,
                )
            },
            restore = {
                VerticalPdfReaderState(
                    source = it[0] as PdfSource,
                    config = config,
                    initialPage = it[1] as Int,
                    initialPageOffset = it[2] as Int,
                )
            },
        )
    }
}

/**
 * Creates and remembers a [VerticalPdfReaderState] that survives configuration changes and
 * process death. When [source] changes, the new document is loaded.
 */
@Composable
public fun rememberVerticalPdfReaderState(
    source: PdfSource,
    config: PdfReaderConfig = PdfReaderConfig(),
    initialPage: Int = 0,
): VerticalPdfReaderState {
    val state = rememberSaveable(saver = VerticalPdfReaderState.Saver(config)) {
        VerticalPdfReaderState(source, config, initialPage)
    }
    LaunchedEffect(state, source) { state.load(source) }
    return state
}

@Deprecated(
    "Use rememberVerticalPdfReaderState(source, PdfReaderConfig(...))",
    ReplaceWith("rememberVerticalPdfReaderState(resource, PdfReaderConfig(zoomEnabled = isZoomEnable))"),
)
@Composable
public fun rememberVerticalPdfReaderState(
    resource: PdfSource,
    isZoomEnable: Boolean,
    isAccessibleEnable: Boolean = false,
): VerticalPdfReaderState = rememberVerticalPdfReaderState(
    source = resource,
    config = PdfReaderConfig(zoomEnabled = isZoomEnable),
)
