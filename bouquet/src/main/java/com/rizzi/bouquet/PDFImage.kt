package com.rizzi.bouquet

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale

@Composable
internal fun PdfImage(
    dimension: () -> Dimension,
    graphicsLayerData: () -> GraphicsLayerData,
    bitmap: () -> ImageBitmap,
    contentDescription: String = "",
) {
    Image(
        bitmap = bitmap(),
        contentDescription = contentDescription,
        contentScale = ContentScale.None,
        modifier = Modifier
            .fillMaxWidth()
    )
}
