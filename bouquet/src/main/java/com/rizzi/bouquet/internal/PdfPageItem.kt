package com.rizzi.bouquet.internal

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import com.rizzi.bouquet.PdfReaderState
import com.rizzi.bouquet.R
import kotlinx.coroutines.CancellationException

/** Largest bitmap we allow per page: 20 MP, i.e. 80 MB in ARGB_8888, under the 100 MB canvas limit. */
private const val MAX_RENDER_PIXELS = 20_000_000L

/** Keeps bitmaps below common GPU texture limits. */
private const val MAX_RENDER_DIMENSION = 8192

/**
 * One page of the document, laid out at [layoutSize] pixels and rendered at the sharpness the
 * current zoom asks for.
 */
@Composable
internal fun PdfPageItem(
    state: PdfReaderState,
    document: PdfDocument,
    pageIndex: Int,
    layoutSize: IntSize,
    placeholder: @Composable (pageIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dpSize = with(density) { DpSize(layoutSize.width.toDp(), layoutSize.height.toDp()) }

    // While the user pinches we keep drawing whatever we have, scaled by the graphics layer;
    // the crisp render happens when the gesture ends.
    val renderZoom = if (state.isTransforming) null else ZoomMath.renderBucket(state.zoom)
    val renderSize = remember(layoutSize, renderZoom) {
        renderZoom?.let { ZoomMath.renderSize(layoutSize, it, MAX_RENDER_PIXELS, MAX_RENDER_DIMENSION) }
    }

    var bitmap: Bitmap? by remember(document, pageIndex) {
        mutableStateOf(state.bitmapCache.latestForPage(pageIndex))
    }

    LaunchedEffect(document, pageIndex, renderSize) {
        val target = renderSize ?: return@LaunchedEffect
        if (target == IntSize.Zero) return@LaunchedEffect
        val key = PageBitmapCache.Key(pageIndex, target.width, target.height)
        state.bitmapCache.get(key)?.let {
            bitmap = it
            return@LaunchedEffect
        }
        if (bitmap == null) bitmap = state.bitmapCache.latestForPage(pageIndex)
        try {
            bitmap = state.bitmapCache.getOrRender(key) {
                document.render(pageIndex, target.width, target.height, state.config.bitmapConfig)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Document closed underneath us or out of memory: keep showing the placeholder /
            // previous render instead of crashing the app.
        }
    }

    val description = rememberPageDescription(state, document, pageIndex)

    Box(
        modifier = modifier
            .size(dpSize)
            .semantics { contentDescription = description },
    ) {
        val current = bitmap
        if (current != null && !current.isRecycled) {
            val image = remember(current) { current.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
            )
        } else {
            placeholder(pageIndex)
        }
    }
}

@Composable
private fun rememberPageDescription(state: PdfReaderState, document: PdfDocument, pageIndex: Int): String {
    val fallback = stringResource(R.string.bouquet_page_description, pageIndex + 1, document.pageCount)
    if (state.config.textExtractor == null) return fallback
    val text by produceState<String?>(initialValue = null, document, pageIndex) {
        value = runCatching { document.pageText(pageIndex) }.getOrNull()
    }
    return text?.takeIf { it.isNotBlank() } ?: fallback
}

/**
 * Size, in pixels, at which page [pageIndex] should be laid out so it fits [fitWidth] and,
 * when given, [fitHeight], preserving the page aspect ratio.
 */
@Composable
internal fun rememberPageLayoutSize(
    document: PdfDocument,
    pageIndex: Int,
    fitWidth: Int,
    fitHeight: Int?,
): IntSize {
    val pageSize = document.pageSizes.getOrNull(pageIndex)
    LaunchedEffect(document, pageIndex) {
        if (document.pageSizes.getOrNull(pageIndex) == null) {
            runCatching { document.pageSize(pageIndex) }
        }
    }
    val aspect = pageSize
        ?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { it.width.toFloat() / it.height }
        ?: document.defaultAspectRatio
    return fitPage(aspect, fitWidth, fitHeight)
}

internal fun fitPage(aspectRatio: Float, fitWidth: Int, fitHeight: Int?): IntSize {
    val width = fitWidth.coerceAtLeast(1)
    val heightForWidth = (width / aspectRatio).toInt().coerceAtLeast(1)
    if (fitHeight == null || heightForWidth <= fitHeight) return IntSize(width, heightForWidth)
    val height = fitHeight.coerceAtLeast(1)
    return IntSize((height * aspectRatio).toInt().coerceAtLeast(1), height)
}
