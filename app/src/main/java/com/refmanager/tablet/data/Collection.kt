package com.refmanager.tablet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class Collection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String, // "folder" or "label"
    val color: Int? = null, // ARGB for labels
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "document_collections", primaryKeys = ["documentId", "collectionId"])
data class DocumentCollection(
    val documentId: Long,
    val collectionId: Long
)