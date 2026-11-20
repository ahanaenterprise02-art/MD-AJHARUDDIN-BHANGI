package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfRecordDao {
    @Query("SELECT * FROM pdf_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PdfRecordEntity>>

    @Query("SELECT * FROM pdf_records ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PdfRecordEntity>>

    @Query("SELECT * FROM pdf_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PdfRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PdfRecordEntity): Long

    @Update
    suspend fun update(record: PdfRecordEntity)

    @Query("DELETE FROM pdf_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
