package com.refmanager.tablet.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentRepository(
    private val dao: DocumentDao,
    private val annotationDao: AnnotationDao,
    private val collectionDao: CollectionDao,
    private val filesDir: File
) {
    fun observeAllDocuments(): Flow<List<Document>> = dao.observeAll()

    fun observe(filter: LibraryFilter): Flow<List<Document>> {
        val needFolders = filter.pdfFilter == PdfFilter.UNSORTED || filter.section == SidebarSection.UNSORTED
        val folderMapFlow = if (needFolders) {
            collectionMemberships("folder")
        } else {
            flowOf(emptyMap())
        }
        val base = combine(dao.observeAll(), folderMapFlow) { documents, folderMap ->
            val duplicateIds = if (filter.section == SidebarSection.DUPLICATES) {
                val active = documents.filter { !it.trashed }
                val titleCounts = active.groupingBy { it.title.trim().lowercase() }.eachCount()
                val doiCounts = active.filter { it.doi.isNotBlank() }.groupingBy { it.doi.trim().lowercase() }.eachCount()
                active.filter {
                    (titleCounts[it.title.trim().lowercase()] ?: 0) > 1 ||
                        (it.doi.isNotBlank() && (doiCounts[it.doi.trim().lowercase()] ?: 0) > 1)
                }.map { it.id }.toSet()
            } else emptySet()

            val query = filter.query.trim()
            val filtered = documents
                .asSequence()
                .filter { document ->
                    query.isEmpty() ||
                        document.title.contains(query, ignoreCase = true) ||
                        document.authors.contains(query, ignoreCase = true) ||
                        document.journal.contains(query, ignoreCase = true) ||
                        document.doi.contains(query, ignoreCase = true)
                }
                .filter {
                    when (filter.section) {
                        SidebarSection.TRASH -> it.trashed
                        SidebarSection.ALL_REFERENCES -> !it.trashed
                        SidebarSection.RECENTLY_ADDED -> !it.trashed
                        SidebarSection.RECENTLY_READ -> !it.trashed && it.lastOpenedAt != null
                        SidebarSection.FAVORITES -> !it.trashed && it.favorite
                        SidebarSection.MY_PUBLICATIONS -> !it.trashed && it.authored
                        SidebarSection.UNSORTED -> !it.trashed && folderMap[it.id].isNullOrEmpty()
                        SidebarSection.DUPLICATES -> !it.trashed && it.id in duplicateIds
                        SidebarSection.COLLECTION -> !it.trashed
                    }
                }
                .filter {
                    when (filter.pdfFilter) {
                        PdfFilter.ALL -> true
                        PdfFilter.HAS_PDF -> it.fileUri.isNotBlank()
                        PdfFilter.NO_PDF -> it.fileUri.isBlank() && !it.pdfRestricted
                        PdfFilter.RESTRICTED -> it.pdfRestricted
                        PdfFilter.UNSORTED -> folderMap[it.id].isNullOrEmpty()
                        PdfFilter.INCOMPLETE -> it.authors.isBlank() || it.year == null || it.journal.isBlank()
                        PdfFilter.HAS_NOTES -> it.abstractText.isNotBlank()
                    }
                }
                .filter { filter.docType == null || it.docType == filter.docType }

            when (filter.section) {
                SidebarSection.RECENTLY_ADDED -> filtered.sortedByDescending { it.addedAt }.toList()
                SidebarSection.RECENTLY_READ -> filtered.sortedByDescending { it.lastOpenedAt ?: 0L }.toList()
                else -> {
                    val comparator: Comparator<Document> = when (filter.sort) {
                        SortOrder.DATE_ADDED -> compareBy { it.addedAt }
                        SortOrder.PUBLICATION_DATE -> compareBy { it.year ?: 0 }
                        SortOrder.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                        SortOrder.JOURNAL -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.journal.ifBlank { "\uFFFF" } }
                        SortOrder.AUTHOR -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.authors.ifBlank { "\uFFFF" } }
                    }
                    val ordered = if (filter.sortAscending) comparator else comparator.reversed()
                    filtered.sortedWith(ordered).toList()
                }
            }
        }
        val collectionId = filter.collectionId
        if (collectionId == null) return base
        return combine(base, collectionDao.documentsIn(collectionId)) { docs, inCollection ->
            val ids = inCollection.map { it.id }.toSet()
            docs.filter { it.id in ids }
        }
    }

    fun observeById(id: Long): Flow<Document?> = dao.observeById(id)

    suspend fun findById(id: Long): Document? = dao.findById(id)

    suspend fun update(document: Document) =
        dao.update(document.copy(updatedAt = System.currentTimeMillis()))

    suspend fun setFavorite(id: Long, favorite: Boolean) =
        dao.setFavorite(id, favorite, System.currentTimeMillis())

    suspend fun setProgress(id: Long, page: Int) =
        dao.setProgress(id, page, System.currentTimeMillis())

    suspend fun setTrashed(id: Long, trashed: Boolean) =
        dao.setTrashed(id, trashed, System.currentTimeMillis())

    suspend fun delete(document: Document) {
        annotationDao.deleteForDocument(document.id)
        collectionDao.deleteForDocument(document.id)
        runCatching { File(Uri.parse(document.fileUri).path.orEmpty()).delete() }
        dao.delete(document)
    }

    fun exportPdf(context: Context, document: Document, destUri: Uri) {
        val srcPath = Uri.parse(document.fileUri).path ?: error("No PDF file")
        val srcFile = File(srcPath)
        if (!srcFile.exists()) error("PDF file missing")
        context.contentResolver.openOutputStream(destUri)?.use { output ->
            srcFile.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Cannot write to destination")
    }

    fun observeAnnotations(documentId: Long): Flow<List<Annotation>> =
        annotationDao.observeForDocument(documentId)

    fun observeCollections(type: String? = null): Flow<List<Collection>> =
        if (type == null) collectionDao.all() else collectionDao.byType(type)

    fun documentsInCollection(collectionId: Long): Flow<List<Document>> =
        collectionDao.documentsIn(collectionId)

    fun collectionMemberships(type: String): Flow<Map<Long, List<Collection>>> =
        observeCollections(type).flatMapLatest { collections ->
            if (collections.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(collections.map { c -> documentsInCollection(c.id).map { docs -> c to docs } }) { pairs ->
                    val map = mutableMapOf<Long, MutableList<Collection>>()
                    pairs.forEach { (col, docs) ->
                        docs.forEach { d -> map.getOrPut(d.id) { mutableListOf() }.add(col) }
                    }
                    map.mapValues { it.value.toList() }
                }
            }
        }

    fun collectionCounts(): Flow<Map<Long, Int>> =
        observeCollections().flatMapLatest { collections ->
            if (collections.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(collections.map { c ->
                    documentsInCollection(c.id).map { docs -> c.id to docs.count { !it.trashed } }
                }) { pairs ->
                    pairs.toMap()
                }
            }
        }

    fun labelMemberships(): Flow<Map<Long, List<String>>> =
        observeCollections("label").flatMapLatest { labels ->
            if (labels.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(labels.map { c -> documentsInCollection(c.id).map { docs -> c.title to docs } }) { pairs ->
                    val map = mutableMapOf<Long, MutableList<String>>()
                    pairs.forEach { (title, docs) -> docs.forEach { d -> map.getOrPut(d.id) { mutableListOf() }.add(title) } }
                    map.mapValues { it.value.toList() }
                }
            }
        }

    suspend fun createCollection(title: String, type: String, color: Int? = null): Long =
        collectionDao.insert(Collection(title = title, type = type, color = color))

    suspend fun deleteCollection(collection: Collection) = collectionDao.delete(collection)

    suspend fun addToCollection(documentId: Long, collectionId: Long) =
        collectionDao.addToCollection(documentId, collectionId)

    suspend fun removeFromCollection(documentId: Long, collectionId: Long) =
        collectionDao.removeFromCollection(documentId, collectionId)

    suspend fun existsMatching(title: String, doi: String): Boolean =
        dao.countMatching(title, doi) > 0

    suspend fun findMatching(title: String, doi: String): Document? =
        dao.findMatching(title, doi)

    suspend fun getOrCreateCollection(title: String, type: String, color: Int? = null): Long {
        val existing = collectionDao.findByTitleAndType(title, type)
        if (existing != null) return existing.id
        return collectionDao.insert(Collection(title = title, type = type, color = color))
    }

    suspend fun importMendeley(document: RemoteDocument, downloader: ((File) -> Unit)?): Long? {
        val destName = "mendeley-${document.id.take(8)}-${document.title.take(40).replace(Regex("[^A-Za-z0-9._-]"), "_")}.pdf"
        val dest = File(filesDir, "library/$destName").apply { parentFile?.mkdirs() }
        var pageCount: Int? = null
        var pdfSize = ""
        if (downloader != null) {
            try {
                downloader(dest)
                pdfSize = formatFileSize(dest.length())
                pageCount = try {
                    PdfRenderer(ParcelFileDescriptor.open(dest, ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount }
                } catch (e: Exception) { null }
            } catch (e: Exception) {
                dest.delete()
                return null
            }
        }
        return dao.insert(
            Document(
                title = document.title,
                authors = document.authors,
                year = document.year,
                journal = document.journal,
                doi = document.doi,
                fileUri = if (downloader != null) Uri.fromFile(dest).toString() else "",
                pageCount = pageCount,
                favorite = document.starred,
                pdfSize = pdfSize,
                docType = document.docType,
                abstractText = document.abstractText,
                authored = document.authored
            )
        )
    }

    suspend fun importPdf(context: Context, uri: Uri): Long {
        val name = "imported-${System.currentTimeMillis()}.pdf"
        val dest = File(filesDir, "library/$name").apply { parentFile?.mkdirs() }
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot open PDF")
        val pageCount = try {
            PdfRenderer(ParcelFileDescriptor.open(dest, ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount }
        } catch (e: Exception) { null }
        val cleanTitle = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.removeSuffix(".pdf")
            ?.ifBlank { null } ?: name
        return dao.insert(
            Document(
                title = cleanTitle,
                fileUri = Uri.fromFile(dest).toString(),
                pageCount = pageCount,
                pdfSize = formatFileSize(dest.length())
            )
        )
    }

    suspend fun importAsset(context: Context, assetName: String, title: String): Long {
        val dest = File(filesDir, "library/$assetName").apply { parentFile?.mkdirs() }
        if (!dest.exists()) {
            context.assets.open(assetName).use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
        }
        val pageCount = try {
            PdfRenderer(ParcelFileDescriptor.open(dest, ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount }
        } catch (e: Exception) { null }
        return dao.insert(
            Document(
                title = title,
                fileUri = Uri.fromFile(dest).toString(),
                pageCount = pageCount,
                pdfSize = formatFileSize(dest.length())
            )
        )
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) String.format(java.util.Locale.US, "%.2f MB", mb)
        else String.format(java.util.Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    }

    suspend fun addHighlight(documentId: Long, page: Int, color: Int, rects: List<PageRect>) {
        annotationDao.insert(
            Annotation(
                documentId = documentId,
                page = page,
                kind = Annotation.KIND_HIGHLIGHT,
                color = color,
                rectsData = encodeRects(rects)
            )
        )
    }

    suspend fun addNote(documentId: Long, page: Int, color: Int, anchor: PageRect, note: String) {
        annotationDao.insert(
            Annotation(
                documentId = documentId,
                page = page,
                kind = Annotation.KIND_NOTE,
                color = color,
                rectsData = encodeRects(listOf(anchor)),
                note = note
            )
        )
    }

    suspend fun updateNote(annotation: Annotation, note: String) {
        annotationDao.update(annotation.copy(note = note, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteAnnotation(annotation: Annotation) {
        annotationDao.delete(annotation)
    }
}
