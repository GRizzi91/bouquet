package com.rizzi.bouquet

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rizzi.bouquet.internal.PdfDocument
import com.rizzi.bouquet.internal.PdfPageItem
import com.rizzi.bouquet.internal.fitPage
import com.rizzi.bouquet.internal.pdfTransform
import com.rizzi.bouquet.internal.rememberPageLayoutSize

/**
 * Shows the document held by [state] as a vertical, scrolling column of pages that fill the
 * available width.
 *
 * @param contentPadding padding around the whole list of pages.
 * @param pageSpacing gap between consecutive pages.
 * @param loading shown while the document is downloading or opening.
 * @param error shown when the document could not be opened; call [PdfReaderState.retry] from it.
 * @param pagePlaceholder drawn in place of a page until its bitmap is ready.
 */
@Composable
public fun VerticalPdfReader(
    state: VerticalPdfReaderState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSpacing: Dp = 0.dp,
    loading: @Composable BoxScope.(PdfLoadState) -> Unit = { PdfReaderDefaults.Loading(it) },
    error: @Composable BoxScope.(PdfException) -> Unit = { PdfReaderDefaults.Error(it) },
    pagePlaceholder: @Composable (pageIndex: Int) -> Unit = { PdfReaderDefaults.PagePlaceholder() },
) {
    PdfDocumentEffect(state)
    PdfReaderFrame(state, modifier, loading, error) { document, viewport ->
        val layoutDirection = LocalLayoutDirection.current
        val horizontalPadding = with(LocalDensity.current) {
            (contentPadding.calculateStartPadding(layoutDirection) +
                contentPadding.calculateEndPadding(layoutDirection)).roundToPx()
        }
        val pageWidth = (viewport.width - horizontalPadding).coerceAtLeast(1)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pdfTransform(state, Orientation.Vertical),
            state = state.listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(pageSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count = document.pageCount, key = { it }) { index ->
                val size = rememberPageLayoutSize(document, index, fitWidth = pageWidth, fitHeight = null)
                PdfPageItem(state, document, index, size, pagePlaceholder)
            }
        }
    }
}

/**
 * Shows the document held by [state] one page at a time, swiping horizontally. Each page is
 * scaled to fit the reader.
 *
 * Pages do not turn while zoomed in; double tap (or [PdfReaderState.resetZoom]) first.
 *
 * @param contentPadding padding applied to every page.
 * @param pageSpacing gap between consecutive pages.
 * @param loading shown while the document is downloading or opening.
 * @param error shown when the document could not be opened; call [PdfReaderState.retry] from it.
 * @param pagePlaceholder drawn in place of a page until its bitmap is ready.
 */
@Composable
public fun HorizontalPdfReader(
    state: HorizontalPdfReaderState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSpacing: Dp = 0.dp,
    loading: @Composable BoxScope.(PdfLoadState) -> Unit = { PdfReaderDefaults.Loading(it) },
    error: @Composable BoxScope.(PdfException) -> Unit = { PdfReaderDefaults.Error(it) },
    pagePlaceholder: @Composable (pageIndex: Int) -> Unit = { PdfReaderDefaults.PagePlaceholder() },
) {
    PdfDocumentEffect(state)
    PdfReaderFrame(state, modifier, loading, error) { document, viewport ->
        val layoutDirection = LocalLayoutDirection.current
        val density = LocalDensity.current
        val horizontalPadding = with(density) {
            (contentPadding.calculateStartPadding(layoutDirection) +
                contentPadding.calculateEndPadding(layoutDirection)).roundToPx()
        }
        val verticalPadding = with(density) {
            (contentPadding.calculateTopPadding() + contentPadding.calculateBottomPadding()).roundToPx()
        }
        val pageAreaWidth = (viewport.width - horizontalPadding).coerceAtLeast(1)
        val pageAreaHeight = (viewport.height - verticalPadding).coerceAtLeast(1)
        val userScrollEnabled = !state.isTransforming &&
            !com.rizzi.bouquet.internal.ZoomMath.isZoomed(state.zoom, state.config.minZoom)

        HorizontalPager(
            state = state.pagerState,
            modifier = Modifier
                .fillMaxSize()
                .pdfTransform(state, Orientation.Horizontal),
            contentPadding = contentPadding,
            pageSpacing = pageSpacing,
            beyondViewportPageCount = state.config.prefetchPages,
            userScrollEnabled = userScrollEnabled,
            key = { it },
        ) { index ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val size = rememberPageLayoutSize(document, index, fitWidth = pageAreaWidth, fitHeight = pageAreaHeight)
                PdfPageItem(state, document, index, size, pagePlaceholder)
            }
        }
    }
}

/**
 * Renders a single page of the document held by [state], scaled to fit the available width
 * (and height, when bounded). Handy for thumbnails and previews. Several [PdfPage]s and readers
 * can share the same state; the document stays open while any of them is in the composition.
 *
 * @param pageIndex 0-based page to show.
 */
@Composable
public fun PdfPage(
    state: PdfReaderState,
    pageIndex: Int,
    modifier: Modifier = Modifier,
    loading: @Composable BoxScope.(PdfLoadState) -> Unit = { PdfReaderDefaults.Loading(it) },
    error: @Composable BoxScope.(PdfException) -> Unit = { PdfReaderDefaults.Error(it) },
    pagePlaceholder: @Composable (pageIndex: Int) -> Unit = { PdfReaderDefaults.PagePlaceholder() },
) {
    PdfDocumentEffect(state)
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val document = state.document
        when (val loadState = state.loadState) {
            is PdfLoadState.Error -> error(loadState.exception)
            is PdfLoadState.Loaded -> if (document != null && pageIndex in 0 until document.pageCount) {
                val window = LocalWindowInfo.current.containerSize
                val fitWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else window.width
                val fitHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else null
                val size = rememberPageLayoutSize(document, pageIndex, fitWidth, fitHeight)
                PdfPageItem(state, document, pageIndex, size, pagePlaceholder)
            } else {
                loading(loadState)
            }
            else -> loading(loadState)
        }
    }
}

/** Opens the document while a reader is in the composition and releases it afterwards. */
@Composable
private fun PdfDocumentEffect(state: PdfReaderState) {
    val context = LocalContext.current.applicationContext
    DisposableEffect(state) {
        state.attach()
        onDispose { state.detach() }
    }
    LaunchedEffect(state, state.source, state.loadGeneration) {
        state.ensureLoaded(context)
    }
}

/**
 * Common frame: measures the viewport (with sane fallbacks when a dimension is unbounded, e.g.
 * inside a scrolling parent), reports it to the state and switches between loading, error and
 * content.
 */
@Composable
private fun PdfReaderFrame(
    state: PdfReaderState,
    modifier: Modifier,
    loading: @Composable BoxScope.(PdfLoadState) -> Unit,
    error: @Composable BoxScope.(PdfException) -> Unit,
    content: @Composable BoxScope.(document: PdfDocument, viewport: IntSize) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.clipToBounds(), contentAlignment = Alignment.TopCenter) {
        val window = LocalWindowInfo.current.containerSize
        val document = state.document
        val viewport = resolveViewport(constraints, window, document)
        SideEffect { state.onViewportSizeChanged(viewport) }

        val sizeModifier = with(LocalDensity.current) {
            Modifier
                .then(if (constraints.hasBoundedWidth) Modifier else Modifier.width(viewport.width.toDp()))
                .then(if (constraints.hasBoundedHeight) Modifier else Modifier.height(viewport.height.toDp()))
        }

        Box(modifier = sizeModifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (val loadState = state.loadState) {
                is PdfLoadState.Error -> error(loadState.exception)
                is PdfLoadState.Loaded -> if (document != null) content(document, viewport) else loading(loadState)
                else -> loading(loadState)
            }
        }
    }
}

/**
 * Picks a finite viewport. An unbounded width falls back to the window width; an unbounded
 * height (the reader sits in a vertically scrolling parent) becomes the height of the first page
 * at that width, so a horizontal reader wraps its content instead of blowing up.
 */
private fun resolveViewport(constraints: Constraints, window: IntSize, document: PdfDocument?): IntSize {
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else window.width.coerceAtLeast(1)
    val height = when {
        constraints.hasBoundedHeight -> constraints.maxHeight
        document != null -> fitPage(document.defaultAspectRatio, width, null).height
        else -> window.height.coerceAtLeast(1)
    }
    return IntSize(width.coerceAtLeast(1), height.coerceAtLeast(1))
}
