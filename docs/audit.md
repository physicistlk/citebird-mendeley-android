# CiteBird App Audit

Audited against `docs/spec.md`, `docs/analysis.md`, `docs/paperpile-analysis.md`, and the target Paperpile UI screenshots.

## Audit Findings & Fix Order

1. **Non-blocking Mendeley Background Sync (High)**
   - *Issue:* Modal `MendeleyDialog` blocked interaction during sync; `done` counter used coroutine index instead of atomic completion count; expired 45-min token caused `"Sign in to Mendeley first"` instead of prompting re-auth.
   - *Fix:* Replaced modal sync dialog with a non-blocking top-bar sync pill, popover, and reader strip; switched to `AtomicInteger` for progress; auto-launch `MendeleyAuthActivity` if `isSignedIn` is true but token expired.

2. **Database & Orphaned Join Rows (Medium)**
   - *Issue:* Deleting a document in `DocumentRepository.delete()` removed annotations and the PDF file, but left orphaned rows in `document_collections`.
   - *Fix:* Add `CollectionDao.deleteForDocument(documentId)` and invoke it inside `DocumentRepository.delete()`.

3. **Compiler & Deprecation Warnings (Low)**
   - *Issue:* `flatMapLatest` opt-in warnings in `DocumentRepository.kt`; deprecated `Icons.Filled.Label` in `LibraryScreen.kt`.
   - *Fix:* Add `@OptIn(ExperimentalCoroutinesApi::class)` on `DocumentRepository` and switch to `Icons.AutoMirrored.Filled.Label`.

4. **UI Parity & Button Wiring (High)**
   - *Issue:* Missing UI controls from the target screenshots:
     - Left sidebar: `SHARED FOLDERS` (placeholder), `LABELS` search & `clean up` unused labels.
     - Paper cards: `...` More context menu (Copy BibTeX, Copy DOI, etc.), Notes/Abstract editor button, expanded drawer actions (`Rename`, `Remove`, `Save to disk` via SAF `CreateDocument`, `Is main PDF`, `Attach supplementary PDF` placeholder, `Browse journal website` via DOI URL).
     - Right panel: `Incomplete` and `Notes` filters, `Type` `More...` toggle, and panel collapse button.
   - *Fix:* Implement all controls with real actions where applicable, and mark unbacked controls with a tiny `place holder` caption.

5. **Manifest Backup Hygiene (Low)**
   - *Issue:* `android:allowBackup="true"` with `EncryptedSharedPreferences` can restore unreadable key material.
   - *Fix:* Set `android:allowBackup="false"` in `AndroidManifest.xml`.
