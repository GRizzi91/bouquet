package com.rizzi.composepdf

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rizzi.bouquet.PdfPage
import com.rizzi.bouquet.PdfSource
import com.rizzi.bouquet.VerticalPdfReaderState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    layout: ReaderLayout,
    onLayoutChange: (ReaderLayout) -> Unit,
    textExtraction: Boolean,
    onTextExtractionChange: (Boolean) -> Unit,
    onOpen: (PdfSource) -> Unit,
) {
    val context = LocalContext.current
    val remoteUrl = stringResource(R.string.pdf_url)
    val base64 = stringResource(R.string.base64_pdf)

    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onOpen(PdfSource.Uri(uri))
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Open a document")

            SourceCard(
                title = "Bundled raw resource",
                description = "res/raw/lorem_ipsum.pdf, with a live thumbnail of the first page",
                thumbnail = PdfSource.RawRes(R.raw.lorem_ipsum),
                onClick = { onOpen(PdfSource.RawRes(R.raw.lorem_ipsum)) },
            )
            SourceCard(
                title = "Remote URL",
                description = "Downloaded once with a custom header, then served from the cache",
                onClick = { onOpen(PdfSource.Url(remoteUrl, headers = mapOf("X-Sample" to "bouquet"))) },
            )
            SourceCard(
                title = "Local file",
                description = "Pick a PDF with the system document picker",
                onClick = { pickDocument.launch(arrayOf("application/pdf")) },
            )
            SourceCard(
                title = "Base64 string",
                description = "A document embedded as a Base64 string resource",
                onClick = { onOpen(PdfSource.Base64(base64)) },
            )

            SectionTitle("Options")

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ReaderLayout.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = layout == entry,
                        onClick = { onLayoutChange(entry) },
                        shape = SegmentedButtonDefaults.itemShape(index, ReaderLayout.entries.size),
                        label = { Text(entry.name) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Text for TalkBack", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Uses bouquet-text (PDFBox) to describe pages with their text",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(checked = textExtraction, onCheckedChange = onTextExtractionChange)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SourceCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    thumbnail: PdfSource? = null,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (thumbnail != null) {
                val state = remember(thumbnail) { VerticalPdfReaderState(thumbnail) }
                PdfPage(
                    state = state,
                    pageIndex = 0,
                    modifier = Modifier
                        .width(56.dp)
                        .clip(MaterialTheme.shapes.small),
                )
                Spacer(Modifier.width(16.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
