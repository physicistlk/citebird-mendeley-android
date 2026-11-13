# Analysis: Mendeley Android APK + Desktop EXE

Both targets were opened with the Craft Anything MCP (engine 0.6.1, deep mode).

## 1. Android: `mendeley.apk`

| Fact | Value |
|---|---|
| Package | `com.mendeley` |
| Version | 1.20.1 (code 9208) |
| minSdk / targetSdk / compileSdk | 21 / 29 / 29 |
| DEX | 2 files, 15,312 classes, ~115k methods |
| Native | `libpspdfkit.so` for arm64-v8a, armeabi-v7a, x86, x86_64 |
| Signing | v1 + v2, RSA-2048, verified |
| UI | Kotlin + Android Views (XML), AndroidX |
| Permissions | INTERNET, READ/WRITE_EXTERNAL_STORAGE, ACCESS_NETWORK_STATE, SYSTEM_ALERT_WINDOW |

Key components found in the manifest:

- `MendeleyRootActivity` – launcher, also handles `mendeley://sutd`.
- `DocumentActivity` – document/PDF view.
- `DataExtractionPdfActivity` – exported `VIEW`/`SEND` handler for `application/pdf`.
- `DocumentImportHtmlActivity` – exported `SEND` for text/HTML.
- `SyncService`, `SyncJobService`, `IndexService`, `IndexJobService` – background sync/index.
- `MendeleyContentProvider`, `MendeleyFeedContentProvider` (the feed one is **exported**).
- `FeedAppWidgetProvider` + widgets.
- PSPDFKit providers/services (PDF engine), Pendo SDK (`sdk.pendo.io`), Picasso.

What matters for the rewrite:

1. **targetSdk 29 is the main blocker.** API 36 changes that hit this code:
   - `requestLegacyExternalStorage="true"` is ignored → SAF/scoped storage required.
   - `SYSTEM_ALERT_WINDOW` is restricted; the Pendo overlay must go.
   - `org.apache.http.legacy` is removed from the boot classpath in newer targets.
   - Large-screen apps can no longer force orientation/resizability (API 36 ignores it).
   - 16 KB page-size requirement for native libraries on arm64 (Android 15+): any new `.so` must be 16 KB-aligned.
2. **PDF rendering is native (PSPDFKit).** A replacement must either license PSPDFKit, use Pdfium, or use the platform `android.graphics.pdf.PdfRenderer` (used in this scaffold: zero native deps, 16 KB safe, one page in memory).
3. **Large heap is enabled.** The rewrite deliberately keeps `largeHeap=false` and renders one page at a time to stay under 512 MB.
4. **UI is phone-first.** No `sw600dp` resources; single-activity-per-screen. Needs adaptive master-detail.

## 2. Desktop: `mendely desktop 2.150.0.exe`

The 299 MB file is an **NSIS installer** (32-bit PE stub, 92 stub functions). The payload was
extracted with libarchive (NSIS reader) to a 420 MB Electron app:

- Product: **Mendeley Reference Manager 2.150.0** (`@mendeley-internal/desktop-reference-manager`).
- Runtime: Electron (`Mendeley Reference Manager.exe`, `resources/app.asar`, `app.asar.unpacked`).
- Local storage: **better-sqlite3** (`resources/app.asar.unpacked/node_modules/better-sqlite3`).
- Renderer: webpack bundle `dist/renderer.js` (6.7 MB) + React/Redux Toolkit (`@reduxjs/toolkit`).
- Main process: `dist/main.js` (1.9 MB).
- Preload: `dist/preload.js`; 27 IPC channels (12 paired), 4 windows, 57 endpoints, 3 routes.
- PDF: PDF.js worker (`dist/pdf/pdf.worker.js`) plus PSPDFKit Web (`dist/assets/pdf-viewer/webviewer-core.min.js`).
- Sync: server-sent events worker (`dist/sync-server-sent-events.worker.js`), `axios`, `localforage`.
- Update feed: `https://static.mendeley.com/bin/desktop` (electron-updater, publisher Elsevier Inc./Ltd).
- Workers: search index (BM25 + bounded Levenshtein), metadata index, PDF index, CSL citation, export, notebook, files cache.

Feature parity checklist for the tablet app:

- [x] Library list + search (Room + LIKE; FTS5 next).
- [x] Import PDF.
- [x] PDF page view.
- [ ] Metadata editing (title/authors/year/journal/DOI).
- [ ] Collections / folders, tags, favorites.
- [ ] Annotation (highlight, note, ink) persisted per document.
- [ ] Citation style/CSL export.
- [ ] Sync (the desktop uses SSE + REST; the Android app used SyncService/IndexService).
- [ ] Reading state / last page.

## 3. Target device mapping

| Property | Device | Design consequence |
|---|---|---|
| Android 16 / SDK 36 | targetSdk 36, minSdk 26 |
| 1600 × 2560 px @ 340 dpi | ≈ 753 × 1204 dp | `sw600dp` → always expanded; two-pane layout |
| 5.4 GB RAM, budget <512 MB | one-page bitmap cap (2048 px), no largeHeap |
| 256 GB storage | no storage constraints; keep PDFs in app-private `files/library` |
| arm64-v8a | no x86 libs; keep pure Kotlin/Java or 16 KB-aligned arm64 `.so` |

At 340 dpi a full-width 1600 px page at 2048 px cap renders ~1.28× native, which is enough for
reading without holding multiple pages in memory. A 2048×2048 ARGB_8888 bitmap is 16 MB; even a
few transient copies stay far below 512 MB.
