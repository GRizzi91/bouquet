# Changelog

## 2.0.0 — 2026-08-29

A rewrite of the rendering engine and a clean-up of the public API. See [MIGRATION.md](MIGRATION.md).

### Fixed
- Crash `Canvas: trying to use a recycled bitmap` when scrolling fast (#61): bitmaps are no
  longer recycled under the UI; an LRU cache with a byte budget owns them.
- Crash `Canvas: trying to draw too large bitmap` and `HorizontalPDFReader` not working inside a
  `LazyColumn` (#59, #43): unbounded constraints fall back to finite sizes and per-page bitmaps are
  capped at 20 MP.
- Setting a new document did not reload (#38): `state.load(source)` and a source-keyed effect.
- Temporary files piling up in `cacheDir` (#57): deterministic cache names, `.part` downloads,
  `Bouquet.clearCache()`, `PdfCachePolicy.DeleteOnClose`; content URIs are read in place.
- `isScrolling` false while panning zoomed content (#33).
- Download progress counted buffer size instead of bytes read and could exceed 100%.
- Renderer could be closed while a page was rendering; closing now waits for the render lock.
- Lint `TrustAllX509TrustManager` / BouncyCastle conflicts from PDFBox (#42): PDFBox moved to the
  optional `bouquet-text` artifact.

### Added
- Pinch to zoom around the fingers, with pages re-rendered sharp at the new zoom (#46, #55, PR #50).
- `scrollToPage()` / `animateScrollToPage()` (#53).
- `canScrollForward` / `canScrollBackward` (#45, PR #49).
- `contentPadding`, `pageSpacing` and `loading` / `error` / `pagePlaceholder` slots (#41, #54).
- `PdfPage` to render a single page, e.g. a thumbnail (#40).
- `PdfSource.File`, `PdfSource.Asset`; `PdfSource.Url` accepts a `Map` of headers.
- `PdfLoadState` and a typed `PdfException` hierarchy.
- Password-protected PDFs on Android 15+ via `PdfReaderConfig.password` (#58).
- `PdfDownloader` interface with an injectable `OkHttpClient`; `PdfTextExtractor` interface.
- `RGB_565` rendering option and configurable memory budget (#47).
- Page content descriptions ("Page N of M") when no text extractor is configured.
- KDoc on the whole public API, unit and instrumented tests, GitHub Actions CI.

### Changed
- Toolchain: AGP 9.3, Gradle 9.7, Kotlin 2.4, Compose BOM 2026.08; minSdk 23, compileSdk 37.
- Accompanist Pager replaced by `androidx.compose.foundation.pager` (#51).
- Retrofit replaced by plain OkHttp.
- API renamed to Kotlin/Compose conventions (`PdfSource`, `VerticalPdfReader`, …); `currentPage`
  is 0-based. 1.x names remain as deprecated shims.
- Published to Maven Central through the Central Portal; `bouquet` and `bouquet-text` artifacts.

## 1.1.2 — 2023-05-22
- Improve memory management.

## 1.1.1 — 2023-05-14
- Fix zoom activation flag.

## 1.1.0 — 2023-05-14
- Asset resource type, double tap to zoom, fix crash on rotation.

## 1.0.2 — 2023-04-17
- Migrate download to Retrofit, fix loading dispatchers.

## 1.0.1 — 2023-03-08
- Various bug fixes, TalkBack support.

## 1.0.0 — 2022-10-22
- First release.
