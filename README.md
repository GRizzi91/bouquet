# Bouquet — Jetpack Compose PDF reader

[![Maven Central](https://img.shields.io/maven-central/v/io.github.grizzi91/bouquet)](https://central.sonatype.com/artifact/io.github.grizzi91/bouquet)
[![CI](https://github.com/GRizzi91/bouquet/actions/workflows/ci.yml/badge.svg)](https://github.com/GRizzi91/bouquet/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE.md)

Bouquet renders PDF documents in Jetpack Compose using the platform
[`PdfRenderer`](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer) —
no native libraries, no WebView. Pages are rendered lazily, cached within a memory budget and
re-rendered crisp at the zoom level you are looking at.

- **Two layouts**: vertical scrolling column or horizontal pager
- **Six sources**: content/file URI, `File`, remote URL (with headers), `res/raw`, `assets`, Base64
- **Gestures**: pinch to zoom around your fingers, double tap to zoom, pan; scroll keeps working while zoomed
- **State you can drive**: `currentPage`, `scrollToPage()`, `zoom`, `load()` another document, `retry()`
- **Customisable**: page spacing, content padding, loading / error / placeholder slots, single-page thumbnails
- **Accessible**: every page has a content description; plug in `bouquet-text` to describe pages with their actual text
- **Safe**: bitmaps are never recycled under the UI, oversized bitmaps are capped, downloads are cancellable and atomic

> Migrating from 1.x? Read [MIGRATION.md](MIGRATION.md). The 1.x names still compile with deprecation warnings.

## Setup

```kotlin
dependencies {
    implementation("io.github.grizzi91:bouquet:2.0.0")

    // Optional: text extraction (PDFBox) for TalkBack descriptions and search
    implementation("io.github.grizzi91:bouquet-text:2.0.0")
}
```

Requirements: `minSdk 23`, `compileSdk 37`, Jetpack Compose. Remote documents need the `INTERNET`
permission in your manifest.

## Quick start

```kotlin
@Composable
fun Report(url: String) {
    val state = rememberVerticalPdfReaderState(
        source = PdfSource.Url(url),
    )
    VerticalPdfReader(
        state = state,
        modifier = Modifier.fillMaxSize(),
    )
}
```

Swap `rememberVerticalPdfReaderState` / `VerticalPdfReader` for
`rememberHorizontalPdfReaderState` / `HorizontalPdfReader` to page through the document one page at
a time.

The state survives configuration changes and process death. When `source` changes, the new
document is loaded automatically.

You can also own the state yourself, for example in a `ViewModel`:

```kotlin
class ReportViewModel : ViewModel() {
    val reader = VerticalPdfReaderState(PdfSource.Url("https://…/report.pdf"))
}
```

The document is opened when a reader using the state enters the composition and released when the
last one leaves, so holding the state longer than the screen costs nothing.

## Sources

| Source | Notes |
|---|---|
| `PdfSource.Uri(uri)` | `content://` or `file://` URI, read in place through the `ContentResolver`. Take a persistable permission if you got it from the document picker. |
| `PdfSource.File(file)` | Any file the app can read. |
| `PdfSource.Url(url, headers)` | Downloaded once into the app cache and reused; progress is reported in `loadState`. |
| `PdfSource.RawRes(R.raw.doc)` | A resource in `res/raw`. |
| `PdfSource.Asset("docs/a.pdf")` | A file in the `assets` folder. |
| `PdfSource.Base64(data)` | A Base64 string. |

## Configuration

Pass a `PdfReaderConfig` when creating the state:

```kotlin
val state = rememberVerticalPdfReaderState(
    source = PdfSource.RawRes(R.raw.manual),
    config = PdfReaderConfig(
        maxZoom = 6f,
        doubleTapZoom = 3f,
        textExtractor = PdfBoxTextExtractor(),      // from bouquet-text
        downloader = OkHttpPdfDownloader(myOkHttpClient),
        cachePolicy = PdfCachePolicy.DeleteOnClose,
        bitmapConfig = Bitmap.Config.RGB_565,       // half the memory
    ),
)
```

| Option | Default | Purpose |
|---|---|---|
| `zoomEnabled` | `true` | Pinch / double-tap zoom. Toggle later with `state.zoomEnabled`. |
| `minZoom` / `maxZoom` | `1f` / `4f` | Zoom range. Pages are re-rendered at the current zoom (capped at 20 MP per page). |
| `doubleTapZoom` | `2.5f` | Zoom applied by a double tap. |
| `doubleTapEnabled` | `true` | |
| `password` | `null` | For encrypted PDFs. Needs Android 15+; older devices report `PdfException.Unsupported`. |
| `textExtractor` | `null` | Text of each page for accessibility. `null` uses "Page N of M". |
| `downloader` | `OkHttpPdfDownloader()` | Implement `PdfDownloader` for a custom HTTP stack. |
| `cachePolicy` | `Keep` | Keep the local copy for instant reopening, or delete it when the reader is disposed. |
| `bitmapConfig` | `ARGB_8888` | `RGB_565` halves memory use. |
| `memoryBudgetBytes` | ¼ of the heap (32–256 MB) | Size of the in-memory page cache. |
| `prefetchPages` | `1` | Pages rendered ahead of the visible ones (horizontal reader). |

## Reading and driving the state

```kotlin
when (val load = state.loadState) {
    PdfLoadState.Idle -> {}
    is PdfLoadState.Downloading -> LinearProgressIndicator(progress = { load.progress ?: 0f })
    PdfLoadState.Opening -> CircularProgressIndicator()
    is PdfLoadState.Loaded -> Text("${state.currentPage + 1} / ${state.pageCount}")
    is PdfLoadState.Error -> Button(onClick = state::retry) { Text("Retry") }
}

scope.launch { state.animateScrollToPage(4) }   // 0-based
scope.launch { state.animateZoom(2f) }
state.resetZoom()
state.load(PdfSource.File(otherFile))            // replace the document

state.canScrollForward   // false on the last page
state.isScrolling
state.file               // local file for sharing (null for content:// sources: share the URI)
```

`PdfException` is a sealed hierarchy — `Network`, `NotFound`, `Corrupted`, `PasswordRequired`,
`Unsupported`, `Unknown` — so you can show the right message.

## Customising the readers

```kotlin
VerticalPdfReader(
    state = state,
    modifier = Modifier.fillMaxSize().background(Color(0xFFEEEEEE)),
    contentPadding = PaddingValues(16.dp),
    pageSpacing = 12.dp,
    loading = { CircularProgressIndicator(Modifier.align(Alignment.Center)) },
    error = { e -> Text(e.message ?: "Error", Modifier.align(Alignment.Center)) },
    pagePlaceholder = { Box(Modifier.fillMaxSize().background(Color.White)) },
)
```

### A single page (thumbnails, previews)

```kotlin
PdfPage(state = state, pageIndex = 0, modifier = Modifier.width(96.dp))
```

Readers and `PdfPage`s can share one state; the document stays open while any of them is shown.

### Inside a scrolling parent

A `HorizontalPdfReader` placed in a `LazyColumn` (unbounded height) wraps to the height of its
first page. A `VerticalPdfReader` needs a bounded height — give it one with `Modifier.height()` or
`fillParentMaxHeight()` — because two vertical scrollables cannot nest.

## Accessibility and text

Android cannot extract text from a PDF, so by default TalkBack announces "Page 3 of 12" for each
page. Add `bouquet-text` and set `textExtractor = PdfBoxTextExtractor()` to announce the page
text instead. The core artifact stays free of PDFBox and BouncyCastle.

You can implement `PdfTextExtractor` yourself to plug in another engine, or reuse the extractor
for search.

## Cache

Downloaded, decoded and bundled documents live in `cacheDir/bouquet/` under a deterministic name,
so reopening a source is instant and never duplicates files. Purge with:

```kotlin
Bouquet.clearCache(context)
```

## Limitations

- `PdfRenderer` does not draw annotations, form fields or signature appearances.
- In `HorizontalPdfReader` pages do not turn while zoomed in; double tap (or `resetZoom()`) first.
- Encrypted PDFs require Android 15 or newer.

## Sample

The [`sample`](sample) app shows every source, both layouts, thumbnails with `PdfPage`, go-to-page,
switching documents at runtime, sharing and the `bouquet-text` extractor.

## License

```
Copyright 2022 Graziano Rizzi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
