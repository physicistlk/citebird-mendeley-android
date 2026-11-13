# Specification

## Target (single device class)

| Property | Value |
|---|---|
| OS | Android 16 (API 36) |
| minSdk / targetSdk | **36 / 36** (tablet-only, no legacy paths) |
| Screen | 1600 × 2560 px, 340 dpi → ~753 × 1204 dp |
| ABI | arm64-v8a |
| RAM | ~5.4 GB total; app peak **< 512 MB** |
| Storage | 256 GB internal; PDFs in app-private `files/library` |

`android:requiresSmallestWidthDp="600"` keeps the app off phones.

## Functional scope (MVP)

- Import PDFs via SAF (no storage permissions).
- Library list with search, favorites, and sort (recent / title / author).
- Metadata editing (title, authors, year, journal, DOI).
- Reader with page navigation, pinch zoom, double-tap zoom, resume at last page.
- Delete document + file.
- Dark/light theme.

## Non-functional

- Cold start < 2 s.
- Peak memory < 512 MB: one PDF page bitmap at a time, capped at 2048 px.
- No native libraries (platform `PdfRenderer`) → no 16 KB page-size risk.
- 60 fps list scrolling; sorting/filtering off the main thread via Flow.
- Always two-pane: list pane 34 % of width (320–460 dp), reader fills the rest.
- No orientation lock; layout is size-driven.
