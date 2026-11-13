package com.refmanager.tablet.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents")
    fun observeAll(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun findById(id: Long): Document?

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeById(id: Long): Flow<Document?>

    @Insert
    suspend fun insert(document: Document): Long

    @Update
    suspend fun update(document: Document)

    @Delete
    suspend fun delete(document: Document)

    @Query("UPDATE documents SET favorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    @Query("UPDATE documents SET lastPage = :page, lastOpenedAt = :now WHERE id = :id")
    suspend fun setProgress(id: Long, page: Int, now: Long)

    @Query("UPDATE documents SET trashed = :trashed, updatedAt = :now WHERE id = :id")
    suspend fun setTrashed(id: Long, trashed: Boolean, now: Long)
    @Query("SELECT COUNT(*) FROM documents WHERE title = :title OR (doi != '' AND doi = :doi)")
    suspend fun countMatching(title: String, doi: String): Int

    @Query("SELECT * FROM documents WHERE title = :title OR (doi != '' AND doi = :doi) LIMIT 1")
    suspend fun findMatching(title: String, doi: String): Document?
}
