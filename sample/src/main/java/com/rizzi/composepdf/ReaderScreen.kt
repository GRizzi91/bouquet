package com.rizzi.composepdf

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.rizzi.bouquet.HorizontalPdfReader
import com.rizzi.bouquet.PdfException
import com.rizzi.bouquet.PdfLoadState
import com.rizzi.bouquet.PdfReaderConfig
import com.rizzi.bouquet.PdfReaderState
import com.rizzi.bouquet.PdfSource
import com.rizzi.bouquet.VerticalPdfReader
import com.rizzi.bouquet.rememberHorizontalPdfReaderState
import com.rizzi.bouquet.rememberVerticalPdfReaderState
import com.rizzi.bouquet.text.PdfBoxTextExtractor
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    source: PdfSource,
    layout: ReaderLayout,
    textExtraction: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = remember(textExtraction) {
        PdfReaderConfig(
            textExtractor = if (textExtraction) PdfBoxTextExtractor() else null,
            maxZoom = 5f,
        )
    }

    // Both readers share one state type hierarchy; pick the one matching the layout.
    val state: PdfReaderState = when (layout) {
        ReaderLayout.Vertical -> rememberVerticalPdfReaderState(source, config)
        ReaderLayout.Horizontal -> rememberHorizontalPdfReaderState(source, config)
    }

    var showGoToPage by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val remoteUrl = stringResource(R.string.pdf_url)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        Text(
                            text = when (val load = state.loadState) {
                                is PdfLoadState.Loaded -> "Page ${state.currentPage + 1} of ${state.pageCount}"
                                is PdfLoadState.Downloading -> "Downloading ${((load.progress ?: 0f) * 100).toInt()}%"
                                PdfLoadState.Opening -> "Opening…"
                                is PdfLoadState.Error -> "Error"
                                PdfLoadState.Idle -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { share(context, state) }, enabled = state.isLoaded) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Go to page…") },
                            onClick = { showMenu = false; showGoToPage = true },
                            enabled = state.isLoaded,
                        )
                        DropdownMenuItem(
                            text = { Text("Reset zoom") },
                            onClick = { showMenu = false; state.resetZoom() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.zoomEnabled) "Disable zoom" else "Enable zoom") },
                            onClick = { showMenu = false; state.zoomEnabled = !state.zoomEnabled },
                        )
                        DropdownMenuItem(
                            text = { Text("Load the remote document instead") },
                            onClick = { showMenu = false; state.load(PdfSource.Url(remoteUrl)) },
                        )
                        DropdownMenuItem(
                            text = { Text("Load the bundled document instead") },
                            onClick = { showMenu = false; state.load(PdfSource.RawRes(R.raw.lorem_ipsum)) },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val readerModifier = Modifier.fillMaxSize()
            when (state) {
                is com.rizzi.bouquet.VerticalPdfReaderState -> VerticalPdfReader(
                    state = state,
                    modifier = readerModifier,
                    pageSpacing = 8.dp,
                    error = { RetryableError(it, state) },
                )
                is com.rizzi.bouquet.HorizontalPdfReaderState -> HorizontalPdfReader(
                    state = state,
                    modifier = readerModifier,
                    pageSpacing = 16.dp,
                    error = { RetryableError(it, state) },
                )
            }

            val progress = state.downloadProgress
            if (state.loadState is PdfLoadState.Downloading) {
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (state.zoom > 1.01f) {
                SuggestionChip(
                    onClick = { state.resetZoom() },
                    label = { Text("Zoom %.1f× · tap to reset".format(state.zoom)) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
    }

    if (showGoToPage) {
        GoToPageDialog(
            pageCount = state.pageCount,
            onDismiss = { showGoToPage = false },
            onGo = { page ->
                showGoToPage = false
                scope.launch { state.animateScrollToPage(page) }
            },
        )
    }
}

@Composable
private fun RetryableError(exception: PdfException, state: PdfReaderState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (exception) {
                is PdfException.Network -> "Download failed" + (exception.statusCode?.let { " (HTTP $it)" } ?: "")
                is PdfException.NotFound -> "The document could not be found"
                is PdfException.Corrupted -> "This file is not a valid PDF"
                is PdfException.PasswordRequired -> "This PDF is password protected"
                is PdfException.Unsupported, is PdfException.Unknown -> exception.message ?: "Something went wrong"
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = exception.cause?.message ?: exception.message ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Button(onClick = { state.retry() }) { Text("Retry") }
    }
}

@Composable
private fun GoToPageDialog(pageCount: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val page = text.toIntOrNull()?.minus(1)
    val valid = page != null && page in 0 until pageCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit) },
                label = { Text("1 – $pageCount") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { onGo(page!!) }, enabled = valid) { Text("Go") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun share(context: Context, state: PdfReaderState) {
    val uri = when (val source = state.source) {
        is PdfSource.Uri -> source.uri
        else -> state.file?.let { file: File ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    } ?: return
    val intent = ShareCompat.IntentBuilder(context)
        .setType("application/pdf")
        .setStream(uri)
        .createChooserIntent()
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(intent)
}
