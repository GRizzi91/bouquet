package com.rizzi.bouquet

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Default slot contents used by [VerticalPdfReader], [HorizontalPdfReader] and [PdfPage].
 *
 * They deliberately depend on no design system; override the slots to match yours.
 */
public object PdfReaderDefaults {

    /** Neutral colour used by the default slots. */
    public val Accent: Color = Color(0xFF6B6B6B)

    /** Fills the reader with a thin progress bar at the top, determinate while downloading. */
    @Composable
    public fun Loading(loadState: PdfLoadState, modifier: Modifier = Modifier, color: Color = Accent) {
        val progress = (loadState as? PdfLoadState.Downloading)?.progress
        Box(modifier = modifier.fillMaxSize()) {
            ProgressBar(
                progress = progress,
                color = color,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(3.dp),
            )
        }
    }

    /** Fills the reader with the error message, centred. */
    @Composable
    public fun Error(exception: PdfException, modifier: Modifier = Modifier, color: Color = Accent) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BasicText(
                text = exception.message ?: exception.javaClass.simpleName,
                modifier = Modifier.padding(24.dp),
                style = TextStyle(color = color, fontSize = 14.sp, textAlign = TextAlign.Center),
            )
        }
    }

    /** A white sheet standing in for a page that has not been rendered yet. */
    @Composable
    public fun PagePlaceholder(modifier: Modifier = Modifier, color: Color = Color.White) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(color),
        )
    }

    /**
     * Horizontal progress bar. [progress] in `0f..1f`, or `null` for an indeterminate sweep.
     */
    @Composable
    public fun ProgressBar(progress: Float?, modifier: Modifier = Modifier, color: Color = Accent) {
        val transition = rememberInfiniteTransition(label = "bouquet-progress")
        val sweep by transition.animateFloat(
            initialValue = -0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
            label = "bouquet-progress-sweep",
        )
        Canvas(modifier = modifier) {
            drawRect(color = color.copy(alpha = 0.2f))
            if (progress != null) {
                drawRect(color = color, size = Size(size.width * progress.coerceIn(0f, 1f), size.height))
            } else {
                val segment = size.width * 0.3f
                val start = (size.width * sweep).coerceIn(0f, size.width)
                val end = (size.width * sweep + segment).coerceIn(0f, size.width)
                drawRect(color = color, topLeft = Offset(start, 0f), size = Size(end - start, size.height))
            }
        }
    }
}
