package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_records")
data class PdfRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val thumbnailPath: String? = null,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val isLocked: Boolean = false,
    val sourceTag: String = "Converted"
)
