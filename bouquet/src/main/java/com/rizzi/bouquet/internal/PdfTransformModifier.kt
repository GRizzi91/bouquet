package com.rizzi.bouquet.internal

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.rizzi.bouquet.PdfReaderState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pinch-to-zoom, double-tap zoom and panning of zoomed content.
 *
 * Multi-touch and single-finger pans while zoomed are intercepted in the
 * [PointerEventPass.Initial] pass so the underlying list never sees them; a single finger at
 * minimum zoom is left alone, so the list scrolls and flings natively.
 */
internal fun Modifier.pdfTransform(state: PdfReaderState, orientation: Orientation): Modifier = this
    .pointerInput(state, orientation) { detectPinchAndPan(state, orientation) }
    .pointerInput(state) {
        coroutineScope {
            detectTapGestures(
                onDoubleTap = { position -> launch { state.toggleZoom(position) } },
            )
        }
    }
    .graphicsLayer {
        val zoom = state.zoom
        scaleX = zoom
        scaleY = zoom
        translationX = state.offset.x
        translationY = state.offset.y
        transformOrigin = TransformOrigin(0f, 0f)
    }

private suspend fun PointerInputScope.detectPinchAndPan(state: PdfReaderState, orientation: Orientation) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val touchSlop = viewConfiguration.touchSlop
        var zooming = false
        var panning = false
        var slopAccumulator = Offset.Zero
        var zoomAccumulator = 1f

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break

            val zoomed = ZoomMath.isZoomed(state.zoom, state.config.minZoom)

            if (pressed.size >= 2) {
                if (!state.zoomEnabled && !zoomed) continue
                val zoomChange = event.calculateZoom()
                val pan = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = true)

                if (!zooming) {
                    zoomAccumulator *= zoomChange
                    slopAccumulator += pan
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1f - zoomAccumulator) * centroidSize
                    if (zoomMotion > touchSlop || slopAccumulator.getDistance() > touchSlop) {
                        zooming = true
                        panning = false
                        state.isPanning = false
                        state.isTransforming = true
                    }
                }
                if (zooming) {
                    if (state.zoomEnabled) state.applyPinch(zoomChange, pan, centroid) else state.applyPan(pan)
                    event.changes.forEach { it.consume() }
                }
            } else if (zoomed || zooming) {
                // One finger left: pan the zoomed content and hand overflow to the list.
                if (zooming) {
                    zooming = false
                    state.isTransforming = false
                    panning = true
                    state.isPanning = true
                }
                val change = pressed.first()
                val pan = change.positionChange()
                if (!panning) {
                    slopAccumulator += pan
                    if (slopAccumulator.getDistance() > touchSlop) {
                        panning = true
                        state.isPanning = true
                    }
                }
                if (panning) {
                    val overflow = state.applyPan(pan)
                    when (orientation) {
                        Orientation.Vertical -> if (overflow.y != 0f) {
                            state.scrollableState.dispatchRawDelta(-overflow.y / state.zoom)
                        }
                        Orientation.Horizontal -> Unit // Pages do not turn while zoomed.
                    }
                    change.consume()
                }
            }
        }

        if (zooming || panning) state.endGesture()
    }
}
