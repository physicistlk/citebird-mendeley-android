# Analysis: Paperpile for Android 1.3.8

Second reference app in the same space as Mendeley. Inspected with the Craft Anything MCP
(engine 0.6.1, deep mode) — signature verification, manifest decoding, DEX class reading,
archive inventory and extraction, plus static reading of the extracted React Native bundle and
the embedded Node sidecar. Nothing was executed and no Paperpile account was touched.

## 0. Provenance

| Fact | Value |
|---|---|
| File | `/mnt/nas_active/rea/Mendely/Paperpile_1.3.8_APKPure.apk` |
| SHA-256 | `88769955fa0aa89a9e107c52139938c83f490c6af6ecb8ca54dc67c49050e894` |
| Size | 160,037,069 bytes (153 MB) |
| Package / version | `com.paperpile` / 1.3.8 (code 4194460) |
| Signing | v1 + v2 + v3, RSA-2048, certificate `CN=Paperpile LLC, O=Paperpile LLC, L=Vienna`; digests verify. A Play **source stamp v2** entry is present, i.e. this is the Play-signed artefact re-hosted by APKPure — not a repack. Treat APKPure as untrusted anyway; install from Play for real use. |

## 1. At a glance

| Dimension | Paperpile 1.3.8 | Mendeley 1.20.1 (our first target) |
|---|---|---|
| SDK | minSdk 21, targetSdk 33 | minSdk 21, targetSdk 29 |
| Code | 3 DEX files, **21,235 classes** | 2 DEX files, 15,312 classes |
| UI | **React Native** (Hermes + JSC, react-native-paper, react-navigation) | Kotlin + Android Views |
| PDF | **PSPDFKit** (`com.pspdfkit.*`, `libpspdfkit.so` 20 MB) | PSPDFKit as well |
| Local DB | **Realm** (schemaVersion 46) | SQLite (Room-like) + PSPDFKit annotations |
| Sync | REST `https://api.paperpile.com` + **Socket.IO** realtime + S3 files | REST + SyncService (targetSdk 29 era) |
| File transfer | Native **Fetch2** downloads, `net.gotev.uploadservice` uploads, foreground service | DownloadManager-ish |
| Extra runtime | **nodejs-mobile, Node 12.19.0** (`libnode.so` 34 MB) | — |
| ABIs | 5 (arm64-v8a, armeabi-v7a, armeabi, x86, x86_64) | 4 |
| APK | 160 MB | 44 MB |
| Telemetry | Mixpanel, Bugsnag (incl. NDK), Bugfender, Firebase (FCM + Analytics), Google Sign-In | Pendo |

Observations above come from the manifest/DEX/native inventory (ev_69fb30…, ev_b2d34e…) and the
archive inventory (ev_b4fa1d…); the Mendeley column is from `docs/analysis.md` (ev_e11ac5…).

## 2. Architecture

```
┌──────────────────────────── React Native JS (index.android.bundle, 11 MB) ───────────────────────────┐
│  screens/navigation (react-native-paper) · Redux store · Realm (schema v46) · SyncClient(model, 2)   │
│  annotations = Notes rows (json) ──► PSPDFKit bridge        library model = pp-library-node          │
└───────────────┬───────────────────────────────┬───────────────────────────────┬─────────────────────┘
                │ NativeModules                 │ NativeModules                 │ rn-bridge channel
┌───────────────▼──────────────┐  ┌─────────────▼──────────────┐  ┌─────────────▼──────────────────┐
│ PSPDFKitModule / .CustomActivity │ PPBackgroundDownloaderModule │  │ nodejs-mobile: main.js         │
│ PSPDFKit UI + annotations     │  │  FetchService (Fetch2 +     │  │  PP.crawling.Crawler           │
│ SocketIO (live updates)       │  │  uploadservice)             │  │  PP.crawling.PdfResolver       │
└───────────────┬──────────────┘  └─────────────┬──────────────┘  │  pp-library-node (4.7 MB)      │
                │                               │                 │  CSL formatter + metadata      │
                ▼                               ▼                 └─────────────┬──────────────────┘
        api.paperpile.com  ◄──── HTTPS ────  S3 (paperpile-production)           │
        Socket.IO  /u?api_key=…  ◄── websocket ── library + annotation deltas    │
```

Derived from: RN bundle + `assets/nodejs-project` (ev_a1fee6…, ev_256d36…), native modules
(ev_d4bd00…, ev_f3eefa…, ev_490fa4…), PSPDFKit bridge (ev_3dbf5e…, ev_c9b1b6…).

## 3. PDF and annotations

- Rendering/editing is **PSPDFKit**, the same commercial engine Mendeley uses. `CustomActivity`
  extends `com.pspdfkit.ui.PdfActivity` and configures defaults for markup, note, free-text, ink,
  line and shape annotations. It implements `onDocumentSave/onDocumentSaved` and computes MD5 and
  file size — so the PDF file is saved after edits and its digest is used for sync.
- **Annotations are first-class app records.** The Realm `Notes` table holds
  `{_id, pub_id, attachment_id, json}`; `AnnotationsHelper` converts between PSPDFKit annotation
  objects and JSON (`mp_id`, `mp_subtype`, page, rect, colour…), and `PSPDFKitModule` pushes the
  result into the open document with `loadNewAnnotations` / `deleteAnnotations`.
- Live edits arrive on the socket (`singleAnnotationUpdate`, `annotationUpdates`,
  `generalUpdates`, `serverTimestamp`), are written to Realm, then replayed into PSPDFKit. This is
  how a highlight made on the web or another device appears while the PDF is open.
- Consequence: **the database, not the PDF, is the source of truth** for annotations; the PDF is
  only the rendering surface. (Inference from the code paths above.)

## 4. Local data model (Realm, schemaVersion 46)

| Table | Columns (indexed columns first) | Purpose |
|---|---|---|
| `Library` | `_id, created, starred, trashed, labels, folders, json, __index` | one row per reference; the whole publication is a JSON blob |
| `Attachments` | `_id, pub_id, article_pdf, s3_md5, __local_md5, json` | PDF attachments and their S3 digest |
| `Collections` | `_id, title, type, json` | folders/labels |
| `Notes` | `_id, pub_id, attachment_id, json` | annotations/highlights |
| `LocalAttachments` | `_id, deleted, local_md5, remote_md5, local_timestamp, remote_timestamp` | file sync state |
| `Changes` | `_id, owner, fields, push, pull, timestamp, mcollection, action, id` | **sync change log** |
| `ShareTasks` | `_id, url, step, type, pub_id, attachment_id, json, finished` | upload queue |
| `Users`, `LocalSettings`, `Recent`, `Job` | … | account, prefs, recents, job runner |

The design is deliberately simple: **JSON payload + a few indexed columns**, with a change-log
table and a task table beside it. It is a good template if we ever add sync (see §8).

## 5. Sync and file transfer

- REST base `https://api.paperpile.com` (`stage-api.paperpile.com` for staging), search service
  `search.paperpile.com`, web app `app.paperpile.com` — endpoints are chosen at runtime.
- Realtime: a Socket.IO client (`io.socket.client.Socket`) wrapped by `SocketIO`, connected from
  `PSPDFKitModule.setSocketConnection({baseUrl, socketUrl: "/u?api_key=" + apiKey, clientId})`.
  Events: socket connected/disconnected, server timestamp, restart socket, general updates,
  single annotation update, annotation updates batch.
- Auth: Paperpile account + **Google Sign-In** (`com.google.android.gms.auth.api.signin`), API key
  and client id kept in local settings; the key is used verbatim in the socket URL.
- Files: `PPBackgroundDownloaderModule` → `FetchService` (a foreground service) uses **Fetch2** for
  resumable downloads and `net.gotev.uploadservice` for uploads, with pause/resume/stop, timeouts,
  progress notifications and a URL allow/deny check (`restrictionRegexTester`). Digests
  (`s3_md5`, `local_md5`, `remote_md5`) decide what must be transferred.
- Objects live in S3 (`paperpile-production.s3.amazonaws.com`).

## 6. The Node sidecar

`libnode.so` is Node **12.19.0** (EOL since April 2022) wrapped by `nodejs-mobile-react-native`.
`assets/nodejs-project/main.js` is 1.7 KB and exposes exactly two RPCs to React Native:

| Message | Core code | Used for |
|---|---|---|
| `…crawler…` | `new PP.crawling.Crawler({mode:'PDF'})` | pull metadata/PDF links out of publisher pages |
| `…resolver…` | `new PP.crawling.PdfResolver({})` | resolve a DOI/landing page to a PDF URL |

The dependency is `pp-library-node@1.0.0`, a 4.7 MB prebuilt bundle of Paperpile's shared core —
the same code the browser extension and desktop use. It contains the publication model
(`Publication`, `Author`, `BibliographicData`, `Serialization`, `Diff`, `Merge`, `Snippets`),
the CSL citation formatter (`PP.format`), crawlers (`jsdom`, `cheerio`, `pdfjs-dist`) and the
`Socket`, `Permissions`, `Licensing`, `Versioning` modules. The mobile app therefore shares one
metadata/citation implementation across platforms — the main reason such a small team can keep
web, desktop and mobile consistent.

## 7. Security and privacy notes

Observed in the manifest:

- `WRITE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE` with `targetSdk 33` (legacy storage paths are
  mostly inert, but the permission request remains).
- `SYSTEM_ALERT_WINDOW`, `RECORD_AUDIO` (used by the in-app web crawler/voice search, not
  verifiable statically), `AD_ID`.
- `usesCleartextTraffic="true"` — HTTP is still permitted app-wide.
- Three telemetry stacks (Mixpanel, Bugsnag with NDK + ANR plugins, Bugfender) plus Firebase.
- Exported components: `MainActivity`, `ShareActivity` (SEND text/plain + application/pdf),
  `filesync.FetchUtils` receiver, `filesync.FetchService`, `DocumentSharingProvider`,
  `FirebaseInstanceIdReceiver`, `RevocationBoundService`.
- A Node 12 runtime in-process means a whole second language runtime and its CVEs are in the
  attack surface; `libjsc.so` + `libhermes*.so` + Node + Realm + PSPDFKit is why the APK is 160 MB
  and ships five ABIs.

## 8. What to take from it (and what not to)

Worth adopting in the tablet app:

1. **JSON payload + indexed columns.** Our Room `Document` entity could grow an `extraJson` column
   instead of a migration per metadata field — Paperpile's `Library.json` shows how far that scales.
2. **Annotations as rows, PDF as a render target.** When we add highlights, store
   `{docId, page, rects[], colour, note, createdAt}` in Room and draw them as an overlay on the
   `PdfRenderer` bitmap. Export to PDF only on demand. This keeps us independent of a PDF SDK.
3. **A `Changes`-style change log** (`owner, collection, action, id, timestamp`) is the cheapest
   path from "local-only" to "syncable" without redesigning the schema.
4. **Task table + foreground service for downloads.** `ShareTasks` + Fetch2 is the pattern for
   "download this PDF in the background, survive rotation, show progress".
5. **Tablet detection at ~540 dp** (`window.width > 540 ? tablet : phone`, portrait lock on phones
   only). Our `requiresSmallestWidthDp=600` + always-two-pane is a stricter, simpler rule that
   fits a single target device.
6. **One shared metadata/citation core across platforms.** If we ever add BibTeX/CSL export, put it
   in plain Kotlin with tests rather than forking it per platform.

Worth avoiding:

- A 160 MB, 5-ABI APK with two JS engines and an EOL Node runtime. Our R8 release is 2.1 MB for
  arm64 only.
- `SYSTEM_ALERT_WINDOW`, legacy storage and `usesCleartextTraffic` — none are needed for a reader.
- Three analytics SDKs in a personal library app.
- Depending on a licensed PDF SDK unless annotation fidelity is a hard requirement.

## 9. Still unknown

- The PSPDFKit licence key is injected at runtime (`setLicenseKey`); the commercial tier
  (annotation editing, collaboration) is not visible statically.
- Whether annotations are always written back into the PDF on save, or only in some flows.
- The exact REST endpoints (they are assembled at runtime from the base URL and not present as
  literals in the minified bundle); behaviour inferred from the sync client and change log.
- `RECORD_AUDIO` / `SYSTEM_ALERT_WINDOW` usage sites were not traced.

## 10. Evidence

| Id | Tool | What it established |
|---|---|---|
| ev_69fb30… | `android_package_show` | package/version/SDK/permissions/components/signing of the APK |
| ev_b4fa1d… | `core_archive_show` | 6,783 entries, native ABIs, asset layout, `index.android.bundle`, `nodejs-project` |
| ev_a1fee6… | `core_archive_extract` | extracted tree used for bundle/JS inspection |
| ev_256d36… | `js_app_map` | nodejs-project is a 2-file Node package depending on `pp-library-node` |
| ev_b2d34e… | `android_classes_find` | 107 `com.paperpile.*` classes of 21,235 |
| ev_0359da… | `android_class_show` | `MainApplication`: RN host + Expo unimodules |
| ev_3dbf5e… | `android_class_show` | `PSPDFKitModule`: present/load/delete annotations, socket, licence |
| ev_a381e1… | `android_class_show` | `CustomActivity`: PSPDFKit UI, save hooks, MD5, annotation defaults |
| ev_0bf6d9… | `android_class_show` | `AnnotationsHelper`: PSPDFKit ⇄ JSON conversion |
| ev_c9b1b6… | `android_class_show` | `SocketIO`: realtime channel |
| ev_d4bd00… | `android_class_show` | `PPBackgroundDownloaderModule`: download/upload/pause/stop API |
| ev_f3eefa… | `android_class_show` | `FetchService`: foreground service |
| ev_490fa4… | `android_class_show` | `FetchUtils`: Fetch2 + uploadservice, URL restrictions |
| ev_4d2073… | `android_class_show` | `ShareModule`: SEND intent handling |
