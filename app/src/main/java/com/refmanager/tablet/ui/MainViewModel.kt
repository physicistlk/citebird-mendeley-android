package com.refmanager.tablet.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.refmanager.tablet.TabletApp
import com.refmanager.tablet.data.Annotation
import com.refmanager.tablet.data.Collection
import com.refmanager.tablet.data.Document
import com.refmanager.tablet.data.LibraryFilter
import com.refmanager.tablet.data.MendeleyAuth
import com.refmanager.tablet.data.MendeleyClient
import com.refmanager.tablet.data.PageRect
import com.refmanager.tablet.data.PdfFilter
import com.refmanager.tablet.data.SidebarSection
import com.refmanager.tablet.data.SortOrder
import com.refmanager.tablet.data.UnauthorizedException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Progress of a Mendeley library pull, shown in the sync dialog. */
data class MendeleyState(
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val imported: Int = 0,
    val skipped: Int = 0,
    val withoutPdf: Int = 0,
    val failed: Int = 0,
    val status: String? = null,
    val error: String? = null,
    val finished: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as TabletApp).repository
    private val app: Application = application

    private val filter = MutableStateFlow(LibraryFilter())
    val filterState: StateFlow<LibraryFilter> = filter

    val selectedId = MutableStateFlow<Long?>(null)
    val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val isImporting = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val mendeley = MutableStateFlow(MendeleyState(signedIn = MendeleyAuth.isSignedIn(application)))
    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    private var pullJob: Job? = null

    val allDocuments: StateFlow<List<Document>> = repository.observeAllDocuments()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val documents: StateFlow<List<Document>> = filter
        .flatMapLatest { repository.observe(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val selected: StateFlow<Document?> = selectedId
        .flatMapLatest { id -> if (id == null) flowOf<Document?>(null) else repository.observeById(id) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    /** Annotations of the open document; empty when nothing is selected. */
    val annotations: StateFlow<List<Annotation>> = selectedId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeAnnotations(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun setQuery(value: String) {
        filter.value = filter.value.copy(query = value)
    }

    fun setSection(section: SidebarSection, collectionId: Long? = null) {
        filter.value = filter.value.copy(
            section = section,
            favoritesOnly = section == SidebarSection.FAVORITES,
            trashedOnly = section == SidebarSection.TRASH,
            collectionId = if (section == SidebarSection.COLLECTION) collectionId else null
        )
    }

    fun showAllPapers() {
        setSection(SidebarSection.ALL_REFERENCES)
    }

    fun setFavoritesOnly(enabled: Boolean) {
        setSection(if (enabled) SidebarSection.FAVORITES else SidebarSection.ALL_REFERENCES)
    }

    fun setTrashedOnly(enabled: Boolean) {
        setSection(if (enabled) SidebarSection.TRASH else SidebarSection.ALL_REFERENCES)
    }

    fun setCollection(id: Long?) {
        if (id == null) setSection(SidebarSection.ALL_REFERENCES)
        else setSection(SidebarSection.COLLECTION, id)
    }

    fun setPdfFilter(pdfFilter: PdfFilter) {
        filter.value = filter.value.copy(
            pdfFilter = if (filter.value.pdfFilter == pdfFilter) PdfFilter.ALL else pdfFilter
        )
    }

    fun setDocTypeFilter(docType: String?) {
        filter.value = filter.value.copy(
            docType = if (filter.value.docType == docType) null else docType
        )
    }

    fun setSort(sort: SortOrder) {
        val current = filter.value
        filter.value = if (current.sort == sort) {
            current.copy(sortAscending = !current.sortAscending)
        } else {
            val defaultAsc = sort == SortOrder.TITLE || sort == SortOrder.JOURNAL || sort == SortOrder.AUTHOR
            current.copy(sort = sort, sortAscending = defaultAsc)
        }
    }

    val collections: StateFlow<List<Collection>> = repository.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val documentLabels: StateFlow<Map<Long, List<String>>> = repository.labelMemberships()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val labelCollections: StateFlow<Map<Long, List<Collection>>> = repository.collectionMemberships("label")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val folderMemberships: StateFlow<Map<Long, List<Collection>>> = repository.collectionMemberships("folder")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val collectionCounts: StateFlow<Map<Long, Int>> = repository.collectionCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun createCollection(title: String, type: String, color: Int? = null) {
        viewModelScope.launch { repository.createCollection(title, type, color) }
    }

    fun deleteCollection(collection: Collection) {
        viewModelScope.launch(Dispatchers.IO) {
            if (filter.value.collectionId == collection.id) showAllPapers()
            repository.deleteCollection(collection)
        }
    }

    fun cleanupUnusedLabels() {
        viewModelScope.launch(Dispatchers.IO) {
            val counts = collectionCounts.value
            val unused = collections.value.filter { it.type == "label" && (counts[it.id] ?: 0) == 0 }
            unused.forEach {
                if (filter.value.collectionId == it.id) showAllPapers()
                repository.deleteCollection(it)
            }
            message.value = if (unused.isEmpty()) "No unused labels" else "Removed ${unused.size} unused label(s)"
        }
    }

    fun savePdfToUri(document: Document, destUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.exportPdf(app, document, destUri) }
                .onSuccess { message.value = "PDF saved to disk" }
                .onFailure { message.value = it.message ?: "Save failed" }
        }
    }

    fun hasValidMendeleyToken(): Boolean = MendeleyAuth.token(app) != null

    fun showMessage(text: String) {
        message.value = text
    }

    fun toggleDocumentCollection(documentId: Long, collectionId: Long, currentlyIn: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (currentlyIn) repository.removeFromCollection(documentId, collectionId)
            else repository.addToCollection(documentId, collectionId)
        }
    }

    fun toggleSelection(id: Long) {
        val cur = selectedIds.value
        selectedIds.value = if (id in cur) cur - id else cur + id
    }

    fun selectAll() {
        selectedIds.value = documents.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun batchStar(star: Boolean) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { repository.setFavorite(it, star) }
        }
    }

    fun batchTrash(trashed: Boolean) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { repository.setTrashed(it, trashed) }
            selectedIds.value = emptySet()
        }
    }

    fun moveToTrash(document: Document) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setTrashed(document.id, true)
            if (selectedId.value == document.id) selectedId.value = null
            message.value = "Moved to Trash"
        }
    }

    fun restoreFromTrash(document: Document) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setTrashed(document.id, false)
            message.value = "Restored from Trash"
        }
    }

    fun select(id: Long?) {
        selectedId.value = id
    }

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            isImporting.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.importPdf(getApplication(), uri) }
            }
            isImporting.value = false
            result.onSuccess { id ->
                selectedId.value = id
                message.value = "Imported"
            }.onFailure { error ->
                message.value = error.message ?: "Import failed"
            }
        }
    }

    /** Debug/testing helper: loads the bundled sample PDF and opens it. */
    fun importSample() {
        viewModelScope.launch {
            isImporting.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repository.importAsset(
                        context = getApplication(),
                        assetName = "sample.pdf",
                        title = "Sample Paper — Tablet Reading"
                    )
                }
            }
            isImporting.value = false
            result.onSuccess { id ->
                selectedId.value = id
                message.value = "Sample imported"
            }.onFailure { error ->
                message.value = error.message ?: "Sample import failed"
            }
        }
    }

    fun saveMetadata(updated: Document) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { repository.update(updated) } }
                .onSuccess { message.value = "Saved" }
                .onFailure { message.value = it.message ?: "Could not save" }
        }
    }

    fun toggleFavorite(document: Document) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.setFavorite(document.id, !document.favorite) }
        }
    }

    fun delete(document: Document) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.delete(document) }
                .onSuccess {
                    if (selectedId.value == document.id) selectedId.value = null
                }
        }
    }

    fun updateProgress(documentId: Long, page: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.setProgress(documentId, page) }
        }
    }

    fun addHighlight(page: Int, color: Int, rects: List<PageRect>) {
        val documentId = selectedId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.addHighlight(documentId, page, color, rects) }
                .onSuccess { message.value = "Highlight saved" }
                .onFailure { message.value = it.message ?: "Could not save highlight" }
        }
    }

    fun addNote(page: Int, color: Int, anchor: PageRect, note: String) {
        val documentId = selectedId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.addNote(documentId, page, color, anchor, note) }
                .onSuccess { message.value = "Note saved" }
                .onFailure { message.value = it.message ?: "Could not save note" }
        }
    }

    fun updateNote(annotation: Annotation, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.updateNote(annotation, note) }
                .onSuccess { message.value = "Note updated" }
                .onFailure { message.value = it.message ?: "Could not update note" }
        }
    }

    fun deleteAnnotation(annotation: Annotation) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.deleteAnnotation(annotation) }
                .onSuccess { message.value = "Annotation deleted" }
                .onFailure { message.value = it.message ?: "Could not delete annotation" }
        }
    }

    // ---- Mendeley pull ----------------------------------------------------

    /** Re-reads the stored token; call after the sign-in activity finishes. */
    fun refreshMendeleyState() {
        val signedIn = MendeleyAuth.token(app) != null
        mendeley.value = mendeley.value.copy(signedIn = signedIn)
    }

    fun signInSucceeded() {
        mendeley.value = MendeleyState(signedIn = true)
        pullMendeley()
    }

    fun signOutMendeley() {
        pullJob?.cancel()
        pullJob = null
        MendeleyAuth.signOut(app)
        mendeley.value = MendeleyState()
    }

    fun cancelMendeley() {
        if (pullJob?.isActive != true) return
        pullJob?.cancel()
        pullJob = null
        mendeley.value = mendeley.value.copy(busy = false, status = "Cancelled")
    }

    /**
     * Pulls the signed-in user's Mendeley library. Every reference becomes a row;
     * those with an attached PDF also get the file, the rest stay metadata-only.
     * References already in the local library (same DOI or title) are skipped.
     */
    fun pullMendeley() {
        if (pullJob?.isActive == true) return
        val token = MendeleyAuth.token(app)
        if (token == null) {
            mendeley.value = MendeleyState(signedIn = false, error = "Sign in to Mendeley first")
            return
        }
        mendeley.value = MendeleyState(signedIn = true, busy = true, status = "Fetching library…")
        pullJob = appScope.launch {
            val client = MendeleyClient(token)
            var imported = 0
            var skipped = 0
            var withoutPdf = 0
            var failed = 0
            try {
                val remoteDocuments = withContext(Dispatchers.IO) { client.documents() }
                val remoteFolders = withContext(Dispatchers.IO) { runCatching { client.folders() }.getOrDefault(emptyList()) }
                val localFolderByRemoteId = HashMap<String, Long>()
                val folderIdsByRemoteDoc = HashMap<String, MutableSet<Long>>()
                withContext(Dispatchers.IO) {
                    remoteFolders.forEach { folder ->
                        val localFolderId = repository.getOrCreateCollection(folder.name, "folder")
                        localFolderByRemoteId[folder.id] = localFolderId
                        val docIds = runCatching { client.folderDocumentIds(folder.id) }.getOrDefault(emptySet())
                        docIds.forEach { remoteDocId ->
                            folderIdsByRemoteDoc.getOrPut(remoteDocId) { LinkedHashSet() }.add(localFolderId)
                        }
                    }
                }
                val labelPalette = com.refmanager.tablet.ui.theme.CiteColors.LabelPalette
                val filesByDocument = withContext(Dispatchers.IO) {
                    client.allFiles()
                        .filter { file ->
                            file.mimeType.contains("pdf", ignoreCase = true) ||
                                file.fileName.endsWith(".pdf", ignoreCase = true)
                        }
                        .groupBy { it.documentId }
                        .mapValues { (_, files) -> files.first() }
                }
                val total = remoteDocuments.size
                mendeley.value = mendeley.value.copy(
                    total = total,
                    status = if (total == 0) "Library is empty" else "Syncing 0/$total…"
                )
                val channels = (Runtime.getRuntime().availableProcessors() - 2).coerceAtLeast(1)
                val lock = Any()
                val completedCount = AtomicInteger(0)
                val semaphore = kotlinx.coroutines.sync.Semaphore(channels)
                kotlinx.coroutines.coroutineScope {
                    remoteDocuments.map { remote ->
                        async {
                            if (!isActive) return@async
                            semaphore.acquire()
                            var localDocId: Long? = null
                            try {
                                val existing = repository.findMatching(remote.title, remote.doi)
                                if (existing != null) {
                                    localDocId = existing.id
                                    if (remote.starred && !existing.favorite) {
                                        repository.setFavorite(existing.id, true)
                                    }
                                    if (remote.authored && !existing.authored) {
                                        repository.update(existing.copy(authored = true))
                                    }
                                    synchronized(lock) { skipped++ }
                                } else {
                                    val pdf = filesByDocument[remote.id]
                                    val downloader: ((File) -> Unit)? = pdf?.let { file ->
                                        { destination: File -> client.download(file.id, destination) }
                                    }
                                    localDocId = runCatching { repository.importMendeley(remote, downloader) }.getOrNull()
                                    synchronized(lock) {
                                        if (localDocId != null) {
                                            imported++
                                            if (pdf == null) withoutPdf++
                                        } else {
                                            failed++
                                        }
                                    }
                                }
                                if (localDocId != null) {
                                    val folderIds = LinkedHashSet<Long>()
                                    folderIdsByRemoteDoc[remote.id]?.let { folderIds.addAll(it) }
                                    remote.folderUuids.forEach { uuid ->
                                        localFolderByRemoteId[uuid]?.let { folderIds.add(it) }
                                    }
                                    folderIds.forEach { folderId ->
                                        repository.addToCollection(localDocId, folderId)
                                    }
                                    remote.tags.forEach { tag ->
                                        val color = labelPalette[(tag.hashCode() and 0x7FFFFFFF) % labelPalette.size]
                                        val labelId = repository.getOrCreateCollection(tag, "label", color)
                                        repository.addToCollection(localDocId, labelId)
                                    }
                                }
                            } finally {
                                semaphore.release()
                            }
                            val done = completedCount.incrementAndGet()
                            synchronized(lock) {
                                mendeley.value = mendeley.value.copy(
                                    done = done,
                                    imported = imported,
                                    skipped = skipped,
                                    withoutPdf = withoutPdf,
                                    status = "Syncing $done / $total…"
                                )
                                notifyProgress(done, total, false)
                            }
                        }
                    }.awaitAll()
                }

                val text = summary(imported, withoutPdf, skipped, failed)
                notifyProgress(total, total, true)
                mendeley.value = mendeley.value.copy(
                    busy = false,
                    done = total,
                    failed = failed,
                    finished = true,
                    status = text
                )
                message.value = text
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: UnauthorizedException) {
                MendeleyAuth.signOut(app)
                mendeley.value = MendeleyState(
                    signedIn = false,
                    error = "Mendeley session expired. Sign in again."
                )
            } catch (error: Exception) {
                mendeley.value = mendeley.value.copy(
                    busy = false,
                    error = error.message ?: "Mendeley pull failed"
                )
            }
        }
    }

    private fun summary(
        imported: Int,
        withoutPdf: Int,
        skipped: Int,
        failed: Int
    ): String = buildString {
        append("Imported $imported")
        if (withoutPdf > 0) append(" (${imported - withoutPdf} with PDF, $withoutPdf metadata only)")
        if (skipped > 0) append(", $skipped already here")
        if (failed > 0) append(", $failed failed")
    }

    private fun notifyProgress(done: Int, total: Int, finished: Boolean) {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "mendeley_pull"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Mendeley pull", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif = NotificationCompat.Builder(app, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("CiteNest — Mendeley pull")
            .setContentText(if (finished) "Import finished" else "$done of $total")
            .setProgress(total, done, total == 0)
            .setOngoing(!finished)
            .build()
        nm.notify(1001, notif)
        if (finished) nm.cancel(1001)
    }

    fun clearMessage() {
        message.value = null
    }

    private companion object {
        /** Test pull: enough references to exercise both detail-pane states. */
        const val PULL_WITH_PDF = 8
        const val PULL_METADATA_ONLY = 4
    }
}
