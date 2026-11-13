package com.refmanager.tablet.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE documentId = :documentId ORDER BY page, createdAt")
    fun observeForDocument(documentId: Long): Flow<List<Annotation>>

    @Insert
    suspend fun insert(annotation: Annotation): Long

    @Update
    suspend fun update(annotation: Annotation)

    @Delete
    suspend fun delete(annotation: Annotation)

    @Query("DELETE FROM annotations WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: Long)
}
