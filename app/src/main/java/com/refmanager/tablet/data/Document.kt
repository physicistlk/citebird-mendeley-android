package com.refmanager.tablet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val authors: String = "",
    val year: Int? = null,
    val journal: String = "",
    val doi: String = "",
    val fileUri: String,
    val pageCount: Int? = null,
    val lastPage: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val trashed: Boolean = false,
    val pdfRestricted: Boolean = false,
    val pdfSize: String = "",
    val docType: String = "Journal Article",
    val abstractText: String = "",
    val authored: Boolean = false
)

enum class SortOrder { DATE_ADDED, PUBLICATION_DATE, TITLE, JOURNAL, AUTHOR }

enum class PdfFilter { ALL, HAS_PDF, NO_PDF, UNSORTED, RESTRICTED, INCOMPLETE, HAS_NOTES }

enum class SidebarSection {
    ALL_REFERENCES,
    RECENTLY_ADDED,
    RECENTLY_READ,
    FAVORITES,
    MY_PUBLICATIONS,
    UNSORTED,
    DUPLICATES,
    TRASH,
    COLLECTION
}

data class LibraryFilter(
    val query: String = "",
    val section: SidebarSection = SidebarSection.ALL_REFERENCES,
    val favoritesOnly: Boolean = false,
    val trashedOnly: Boolean = false,
    val collectionId: Long? = null,
    val pdfFilter: PdfFilter = PdfFilter.ALL,
    val docType: String? = null,
    val sort: SortOrder = SortOrder.DATE_ADDED,
    val sortAscending: Boolean = false
)
