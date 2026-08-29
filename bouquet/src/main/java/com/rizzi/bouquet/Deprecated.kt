@file:Suppress("DEPRECATION", "FunctionName")

package com.rizzi.bouquet

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Source-compatibility shims for 1.x. They will be removed in 3.0.

@Deprecated("Renamed to PdfSource", ReplaceWith("PdfSource"))
public typealias ResourceType = PdfSource

@Deprecated("Renamed to VerticalPdfReader", ReplaceWith("VerticalPdfReader(state, modifier)"))
@Composable
public fun VerticalPDFReader(state: VerticalPdfReaderState, modifier: Modifier) {
    VerticalPdfReader(state = state, modifier = modifier)
}

@Deprecated("Renamed to HorizontalPdfReader", ReplaceWith("HorizontalPdfReader(state, modifier)"))
@Composable
public fun HorizontalPDFReader(state: HorizontalPdfReaderState, modifier: Modifier) {
    HorizontalPdfReader(state = state, modifier = modifier)
}

@Deprecated("Renamed to pageCount", ReplaceWith("pageCount"))
public val PdfReaderState.pdfPageCount: Int
    get() = pageCount

@Deprecated("Renamed to source", ReplaceWith("source"))
public val PdfReaderState.resource: PdfSource
    get() = source

@Deprecated("Renamed to zoom", ReplaceWith("zoom"))
public val PdfReaderState.scale: Float
    get() = zoom

@Deprecated("Renamed to zoomEnabled", ReplaceWith("zoomEnabled"))
public val PdfReaderState.isZoomEnable: Boolean
    get() = zoomEnabled

@Deprecated("Set zoomEnabled directly", ReplaceWith("zoomEnabled = enable"))
public fun PdfReaderState.changeZoomState(enable: Boolean) {
    zoomEnabled = enable
}

@Deprecated(
    "Use downloadProgress (0f..1f) or loadState",
    ReplaceWith("((downloadProgress ?: 0f) * 100).toInt()"),
)
public val PdfReaderState.loadPercent: Int
    get() = ((downloadProgress ?: if (isLoaded) 1f else 0f) * 100).toInt()
