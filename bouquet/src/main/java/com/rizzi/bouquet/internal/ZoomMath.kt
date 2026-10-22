package com.rizzi.bouquet.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.min

/**
 * Geometry of the zoom transform.
 *
 * The content is scaled by `zoom` around its top-left corner and translated by `offset`, so a
 * content point `p` shows up on screen at `p * zoom + offset`.
 */
internal object ZoomMath {

    private const val EPSILON = 0.01f

    fun isZoomed(zoom: Float, minZoom: Float): Boolean = zoom > minZoom + EPSILON

    /**
     * Offset that keeps the content point currently under [pivot] fixed on screen while the zoom
     * changes from [oldZoom] to [newZoom].
     */
    fun zoomAround(oldZoom: Float, newZoom: Float, oldOffset: Offset, pivot: Offset): Offset {
        if (oldZoom == 0f) return Offset.Zero
        val contentPoint = (pivot - oldOffset) / oldZoom
        return pivot - contentPoint * newZoom
    }

    /**
     * Keeps the scaled content covering the whole [viewport]: no gap may appear on any side.
     * With `zoom <= 1` the only valid offset is zero.
     */
    fun clampOffset(offset: Offset, zoom: Float, viewport: IntSize): Offset {
        val minX = min(viewport.width * (1f - zoom), 0f)
        val minY = min(viewport.height * (1f - zoom), 0f)
        return Offset(offset.x.coerceIn(minX, 0f), offset.y.coerceIn(minY, 0f))
    }

    /** Rounds [zoom] to the nearest quarter so small pinch differences share a render. */
    fun renderBucket(zoom: Float): Float = (Math.round(zoom * 4f) / 4f).coerceAtLeast(0.25f)

    /**
     * Size in pixels at which a page laid out at [layout] should be rendered for [zoom], capped
     * so the bitmap never exceeds [maxPixels] and never grows beyond [maxDimension] on a side.
     */
    fun renderSize(layout: IntSize, zoom: Float, maxPixels: Long, maxDimension: Int): IntSize {
        if (layout.width <= 0 || layout.height <= 0) return IntSize.Zero
        var scale = zoom.coerceAtLeast(0.25f)
        val pixels = layout.width.toLong() * layout.height.toLong()
        val pixelCap = kotlin.math.sqrt(maxPixels.toDouble() / pixels).toFloat()
        val dimensionCap = min(maxDimension / layout.width.toFloat(), maxDimension / layout.height.toFloat())
        scale = minOf(scale, pixelCap, dimensionCap)
        return IntSize(
            (layout.width * scale).toInt().coerceAtLeast(1),
            (layout.height * scale).toInt().coerceAtLeast(1),
        )
    }

    fun approximately(a: Float, b: Float): Boolean = abs(a - b) < EPSILON
}
