# Migrating from 1.x to 2.0

2.0 is a rewrite of the rendering engine and a clean-up of the public API. Every 1.x symbol still
compiles, marked `@Deprecated` with a `ReplaceWith`, so **Android Studio's "Replace with…"
quick-fix does most of the work**. The shims are removed in 3.0.

## Requirements

| | 1.1.2 | 2.0.0 |
|---|---|---|
| minSdk | 21 | 23 |
| compileSdk | 33 | 37 |
| Compose | 1.4 | BOM 2026.08 (1.12) |
| Extra dependencies pulled in | Accompanist Pager, Retrofit, PDFBox (+ BouncyCastle) | OkHttp only |

## Renamed API

| 1.x | 2.0 |
|---|---|
| `ResourceType` | `PdfSource` |
| `ResourceType.Remote(url, headers: HashMap)` | `PdfSource.Url(url, headers: Map)` |
| `ResourceType.Local(uri)` | `PdfSource.Uri(uri)` |
| `ResourceType.Base64(file)` | `PdfSource.Base64(data)` |
| `ResourceType.Asset(R.raw.x)` | `PdfSource.RawRes(R.raw.x)` — `PdfSource.Asset(path)` now reads from `assets/` |
| `VerticalPDFReader(state, modifier)` | `VerticalPdfReader(state, modifier = …)` |
| `HorizontalPDFReader(state, modifier)` | `HorizontalPdfReader(state, modifier = …)` |
| `rememberVerticalPdfReaderState(resource, isZoomEnable, isAccessibleEnable)` | `rememberVerticalPdfReaderState(source, PdfReaderConfig(zoomEnabled = …, textExtractor = …))` |
| `VerticalPdfReaderState(resource, isZoomEnable, isAccessibleEnable)` | `VerticalPdfReaderState(source, PdfReaderConfig(…))` |
| `state.pdfPageCount` | `state.pageCount` |
| `state.resource` | `state.source` |
| `state.scale` | `state.zoom` |
| `state.isZoomEnable` / `state.changeZoomState(b)` | `state.zoomEnabled` (read/write) |
| `state.loadPercent: Int` | `state.downloadProgress: Float?` or `state.loadState` |
| `state.error: Throwable?` | `state.error: PdfException?` (sealed; also in `loadState`) |
| `state.isLoaded` | unchanged |
| `state.currentPage` (**1-based**) | `state.currentPage` (**0-based**, like every Compose index) |

### Behaviour changes to check

1. **`currentPage` is 0-based.** `"Page ${state.currentPage}"` must become
   `"Page ${state.currentPage + 1}"`. This is the one change the compiler will not flag.
2. **`state.file` can be `null`.** Content URIs are no longer copied into the cache; share the URI
   itself. Downloaded, Base64, raw and asset documents still expose a `File`.
3. **Accessibility text needs the `bouquet-text` artifact.** `isAccessibleEnable = true` used to
   pull PDFBox into every app. Now add `io.github.grizzi91:bouquet-text` and pass
   `textExtractor = PdfBoxTextExtractor()`. Without it pages are described as "Page N of M".
4. **Cache files are reused, not duplicated.** 1.x wrote a new `<timestamp>.pdf` on every open and
   never deleted it. 2.0 keys files by source and offers `Bouquet.clearCache(context)` and
   `PdfCachePolicy.DeleteOnClose`.
5. **Changing document.** Passing a different `source` to `rememberVerticalPdfReaderState` now
   loads it. With a hoisted state call `state.load(newSource)`.
6. **Zoom.** Double tap zooms to `doubleTapZoom` (default 2.5×, was 3×) and pinch-to-zoom is on
   by default; set `PdfReaderConfig(zoomEnabled = false)` to disable both.

## New in 2.0

- `PdfSource.File`, `PdfSource.Asset`
- `PdfLoadState` (`Idle`, `Downloading(progress)`, `Opening`, `Loaded(pageCount)`, `Error(exception)`)
- `PdfException` hierarchy
- `state.scrollToPage()`, `animateScrollToPage()`, `canScrollForward`, `canScrollBackward`
- `state.setZoom()`, `animateZoom()`, `resetZoom()`
- `state.load(source)`, `state.retry()`
- `contentPadding`, `pageSpacing`, `loading`, `error`, `pagePlaceholder` slots
- `PdfPage` for single-page rendering
- `PdfReaderConfig.password` (Android 15+), `downloader`, `cachePolicy`, `bitmapConfig`, `memoryBudgetBytes`
- `Bouquet.clearCache()`
