package com.refmanager.tablet.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY type, title")
    fun all(): Flow<List<Collection>>

    @Query("SELECT * FROM collections WHERE type = :type ORDER BY title")
    fun byType(type: String): Flow<List<Collection>>

    @Query("SELECT * FROM collections WHERE title = :title AND type = :type LIMIT 1")
    suspend fun findByTitleAndType(title: String, type: String): Collection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collection: Collection): Long

    @Delete
    suspend fun delete(collection: Collection)

    @Query("INSERT OR IGNORE INTO document_collections (documentId, collectionId) VALUES (:documentId, :collectionId)")
    suspend fun addToCollection(documentId: Long, collectionId: Long)

    @Query("DELETE FROM document_collections WHERE documentId = :documentId AND collectionId = :collectionId")
    suspend fun removeFromCollection(documentId: Long, collectionId: Long)

    @Query("DELETE FROM document_collections WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: Long)

    @Query("SELECT c.* FROM collections c INNER JOIN document_collections dc ON c.id = dc.collectionId WHERE dc.documentId = :documentId")
    fun collectionsFor(documentId: Long): Flow<List<Collection>>

    @Query("SELECT d.* FROM documents d INNER JOIN document_collections dc ON d.id = dc.documentId WHERE dc.collectionId = :collectionId ORDER BY d.addedAt DESC")
    fun documentsIn(collectionId: Long): Flow<List<Document>>
}
