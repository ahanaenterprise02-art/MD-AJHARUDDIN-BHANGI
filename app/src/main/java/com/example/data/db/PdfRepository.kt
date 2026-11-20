package com.example.data.db

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfRepository(
    private val context: Context,
    private val dao: PdfRecordDao
) {
    val allPdfs: Flow<List<PdfRecordEntity>> = dao.observeAll()

    fun recentPdfs(limit: Int = 6): Flow<List<PdfRecordEntity>> = dao.observeRecent(limit)

    fun pdfOutputDir(): File = File(context.filesDir, "pdfs").apply { mkdirs() }

    fun thumbnailDir(): File = File(context.filesDir, "thumbs").apply { mkdirs() }

    fun originalsDir(): File = File(context.filesDir, "originals").apply { mkdirs() }

    suspend fun getById(id: Long): PdfRecordEntity? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    suspend fun registerGeneratedPdf(
        file: File,
        displayName: String,
        pageCount: Int,
        isLocked: Boolean,
        sourceTag: String,
        fallbackThumbnail: Bitmap? = null
    ): PdfRecordEntity = withContext(Dispatchers.IO) {
        val cleanName = sanitizePdfName(displayName)
        val thumbFile = File(thumbnailDir(), "thumb_${System.currentTimeMillis()}_${cleanName.hashCode()}.png")
        val generatedThumb = generatePdfThumbnail(file, thumbFile, fallbackThumbnail)
        val record = PdfRecordEntity(
            fileName = cleanName,
            filePath = file.absolutePath,
            thumbnailPath = generatedThumb?.absolutePath,
            pageCount = pageCount.coerceAtLeast(1),
            fileSizeBytes = file.length(),
            createdAt = System.currentTimeMillis(),
            isLocked = isLocked,
            sourceTag = sourceTag
        )
        val insertedId = dao.insert(record)
        record.copy(id = insertedId)
    }

    suspend fun renamePdf(record: PdfRecordEntity, newDisplayName: String): PdfRecordEntity? = withContext(Dispatchers.IO) {
        val cleanName = sanitizePdfName(newDisplayName)
        val oldFile = File(record.filePath)
        if (!oldFile.exists()) return@withContext null
        val targetFile = uniqueFile(pdfOutputDir(), "$cleanName.pdf")
        val moved = if (oldFile.absolutePath == targetFile.absolutePath) {
            true
        } else {
            oldFile.renameTo(targetFile) || run {
                oldFile.copyTo(targetFile, overwrite = true)
                oldFile.delete()
            }
        }
        if (!moved) return@withContext null
        val updated = record.copy(
            fileName = cleanName,
            filePath = targetFile.absolutePath,
            fileSizeBytes = targetFile.length()
        )
        dao.update(updated)
        updated
    }

    suspend fun duplicatePdf(record: PdfRecordEntity): PdfRecordEntity? = withContext(Dispatchers.IO) {
        val src = File(record.filePath)
        if (!src.exists()) return@withContext null
        val base = "${record.fileName}_copy"
        val dest = uniqueFile(pdfOutputDir(), "$base.pdf")
        src.copyTo(dest, overwrite = false)
        val newThumb = record.thumbnailPath?.let { thumbPath ->
            val oldThumb = File(thumbPath)
            if (oldThumb.exists()) {
                val copyThumb = File(thumbnailDir(), "thumb_${System.currentTimeMillis()}.png")
                oldThumb.copyTo(copyThumb, overwrite = true)
                copyThumb.absolutePath
            } else null
        }
        val copyRecord = record.copy(
            id = 0,
            fileName = dest.nameWithoutExtension,
            filePath = dest.absolutePath,
            thumbnailPath = newThumb,
            createdAt = System.currentTimeMillis()
        )
        val newId = dao.insert(copyRecord)
        copyRecord.copy(id = newId)
    }

    suspend fun deletePdf(record: PdfRecordEntity) = withContext(Dispatchers.IO) {
        runCatching { File(record.filePath).delete() }
        record.thumbnailPath?.let { path -> runCatching { File(path).delete() } }
        dao.deleteById(record.id)
    }

    fun uniqueOutputFile(baseName: String): File {
        val clean = sanitizePdfName(baseName)
        return uniqueFile(pdfOutputDir(), "$clean.pdf")
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val stem = fileName.removeSuffix(".pdf")
        var idx = 1
        while (candidate.exists()) {
            candidate = File(dir, "${stem}_$idx.pdf")
            idx++
        }
        return candidate
    }

    private fun sanitizePdfName(raw: String): String {
        val trimmed = raw.trim().removeSuffix(".pdf").removeSuffix(".PDF")
        val safe = trimmed.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").trim()
        return if (safe.isBlank()) "AhaPDF_${System.currentTimeMillis()}" else safe
    }

    private fun generatePdfThumbnail(pdfFile: File, outThumb: File, fallback: Bitmap?): File? {
        runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            val targetW = 360
                            val targetH = ((page.height.toFloat() / page.width.coerceAtLeast(1)) * targetW)
                                .toInt().coerceIn(240, 540)
                            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            FileOutputStream(outThumb).use { fos ->
                                bmp.compress(Bitmap.CompressFormat.PNG, 90, fos)
                            }
                            bmp.recycle()
                            return outThumb
                        }
                    }
                }
            }
        }
        if (fallback != null) {
            runCatching {
                val scale = 320f / maxOf(fallback.width, fallback.height).coerceAtLeast(1)
                val w = (fallback.width * scale).toInt().coerceAtLeast(100)
                val h = (fallback.height * scale).toInt().coerceAtLeast(100)
                val scaled = Bitmap.createScaledBitmap(fallback, w, h, true)
                FileOutputStream(outThumb).use { fos ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 88, fos)
                }
                if (scaled !== fallback) scaled.recycle()
                return outThumb
            }
        }
        return null
    }
}
