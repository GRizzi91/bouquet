package com.rizzi.bouquet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.rizzi.bouquet.internal.ZoomMath
import com.rizzi.bouquet.internal.fitPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomMathTest {

    private val viewport = IntSize(1000, 2000)

    @Test
    fun `offset is zero when not zoomed`() {
        assertEquals(Offset.Zero, ZoomMath.clampOffset(Offset(-300f, -700f), 1f, viewport))
        assertEquals(Offset.Zero, ZoomMath.clampOffset(Offset(250f, 100f), 0.5f, viewport))
    }

    @Test
    fun `offset is clamped so content always covers the viewport`() {
        val clamped = ZoomMath.clampOffset(Offset(-5000f, 400f), 2f, viewport)
        assertEquals(Offset(-1000f, 0f), clamped)

        val inside = ZoomMath.clampOffset(Offset(-400f, -1500f), 2f, viewport)
        assertEquals(Offset(-400f, -1500f), inside)
    }

    @Test
    fun `zooming around a pivot keeps the content under the pivot fixed`() {
        val oldZoom = 1.5f
        val oldOffset = Offset(-120f, -340f)
        val pivot = Offset(400f, 900f)
        val contentUnderPivotBefore = (pivot - oldOffset) / oldZoom

        val newZoom = 3f
        val newOffset = ZoomMath.zoomAround(oldZoom, newZoom, oldOffset, pivot)
        val contentUnderPivotAfter = (pivot - newOffset) / newZoom

        assertEquals(contentUnderPivotBefore.x, contentUnderPivotAfter.x, 0.001f)
        assertEquals(contentUnderPivotBefore.y, contentUnderPivotAfter.y, 0.001f)
    }

    @Test
    fun `zoom to one from the origin resets the offset`() {
        assertEquals(Offset.Zero, ZoomMath.zoomAround(2f, 1f, Offset.Zero, Offset.Zero))
    }

    @Test
    fun `isZoomed tolerates float noise`() {
        assertFalse(ZoomMath.isZoomed(1.005f, 1f))
        assertTrue(ZoomMath.isZoomed(1.2f, 1f))
    }

    @Test
    fun `render bucket rounds to quarters`() {
        assertEquals(1f, ZoomMath.renderBucket(1.1f))
        assertEquals(1.25f, ZoomMath.renderBucket(1.2f))
        assertEquals(2.5f, ZoomMath.renderBucket(2.6f))
        assertEquals(0.25f, ZoomMath.renderBucket(0.01f))
    }

    @Test
    fun `render size scales with zoom`() {
        val size = ZoomMath.renderSize(IntSize(1000, 1400), 2f, maxPixels = 50_000_000, maxDimension = 8192)
        assertEquals(IntSize(2000, 2800), size)
    }

    @Test
    fun `render size is capped by the pixel budget`() {
        val size = ZoomMath.renderSize(IntSize(1000, 1000), 4f, maxPixels = 4_000_000, maxDimension = 8192)
        assertEquals(IntSize(2000, 2000), size)
    }

    @Test
    fun `render size is capped by the maximum dimension`() {
        val size = ZoomMath.renderSize(IntSize(1000, 500), 4f, maxPixels = Long.MAX_VALUE, maxDimension = 2000)
        assertEquals(IntSize(2000, 1000), size)
    }

    @Test
    fun `render size of an empty layout is zero`() {
        assertEquals(IntSize.Zero, ZoomMath.renderSize(IntSize.Zero, 2f, 1, 1))
    }

    @Test
    fun `fitPage fills the width when height is unbounded`() {
        assertEquals(IntSize(1000, 1414), fitPage(0.7071f, 1000, null))
    }

    @Test
    fun `fitPage respects the height when the page would overflow`() {
        val size = fitPage(0.7071f, 1000, 1000)
        assertEquals(1000, size.height)
        assertEquals(707, size.width)
    }

    @Test
    fun `fitPage keeps width-bound layout when the page fits the height`() {
        assertEquals(IntSize(1000, 500), fitPage(2f, 1000, 1000))
    }
}
