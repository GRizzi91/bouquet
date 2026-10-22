package com.rizzi.composepdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.rizzi.bouquet.PdfSource
import com.rizzi.composepdf.ui.theme.BouquetSampleTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BouquetSampleTheme {
                BouquetSampleApp()
            }
        }
    }
}

/** Which reader the sample opens documents with. */
enum class ReaderLayout { Vertical, Horizontal }

@Composable
private fun BouquetSampleApp() {
    var source: PdfSource? by rememberSaveable { mutableStateOf(null) }
    var layout: ReaderLayout by rememberSaveable { mutableStateOf(ReaderLayout.Vertical) }
    var textExtraction: Boolean by rememberSaveable { mutableStateOf(false) }

    val current = source
    if (current == null) {
        HomeScreen(
            layout = layout,
            onLayoutChange = { layout = it },
            textExtraction = textExtraction,
            onTextExtractionChange = { textExtraction = it },
            onOpen = { source = it },
        )
    } else {
        BackHandler { source = null }
        ReaderScreen(
            source = current,
            layout = layout,
            textExtraction = textExtraction,
            onBack = { source = null },
        )
    }
}
