package com.example.domain.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.data.db.PdfRecordEntity
import com.example.data.db.PdfRepository
import com.example.domain.image.ImageProcessor
import com.example.domain.model.EditableImagePage
import com.example.domain.model.ImageScaleMode
import com.example.domain.model.PdfGenerationConfig
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object PdfGenerator {

    suspend fun generatePdfFromImages(
        pages: List<EditableImagePage>,
        config: PdfGenerationConfig,
        repository: PdfRepository,
        onProgress: (current: Int, total: Int, status: String) -> Unit
    ): Result<PdfRecordEntity> = withContext(Dispatchers.IO) {
        runCatching {
            require(pages.isNotEmpty()) { "Select at least one image to generate a PDF." }

            val outputFile = repository.uniqueOutputFile(config.fileName)
            val tempUnencrypted = File(outputFile.parentFile, "tmp_${System.currentTimeMillis()}.pdf")
            val targetMaxDim = (config.imageQuality.maxDim * config.compressionLevel.scaleFactor)
                .roundToInt().coerceIn(720, 3000)
            val jpegQuality = (config.imageQuality.jpegQuality + config.compressionLevel.jpegQualityDelta)
                .coerceIn(35, 98)

            // Prepare processed bitmaps first
            val renderedBitmaps = mutableListOf<Bitmap>()
            var firstThumbBitmap: Bitmap? = null

            try {
                pages.forEachIndexed { index, page ->
                    onProgress(index + 1, pages.size, "Processing page ${index + 1} of ${pages.size}...")
                    val processed = ImageProcessor.renderProcessedBitmap(
                        page = page,
                        maxDimension = targetMaxDim,
                        applyFilters = true
                    ) ?: throw IllegalStateException("Could not decode image ${index + 1}")

                    val compressedBmp = if (jpegQuality < 96) {
                        val baos = ByteArrayOutputStream()
                        processed.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                        val bytes = baos.toByteArray()
                        processed.recycle()
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            ?: throw IllegalStateException("Compression failed on page ${index + 1}")
                    } else {
                        processed
                    }

                    if (index == 0) {
                        firstThumbBitmap = compressedBmp.copy(Bitmap.Config.ARGB_8888, false)
                    }
                    renderedBitmaps.add(compressedBmp)
                }

                onProgress(pages.size, pages.size, "Writing PDF file...")
                val wroteWithNative = runCatching {
                    writeUsingAndroidPdfDocument(renderedBitmaps, config, tempUnencrypted)
                    true
                }.getOrDefault(false)

                if (!wroteWithNative) {
                    writeUsingPdfBoxFallback(renderedBitmaps, config, jpegQuality, tempUnencrypted)
                }
            } finally {
                renderedBitmaps.forEach { if (!it.isRecycled) it.recycle() }
            }

            val isLocked = config.password.isNotBlank()
            if (isLocked) {
                onProgress(pages.size, pages.size, "Encrypting PDF with password...")
                PDDocument.load(tempUnencrypted).use { doc ->
                    val ap = AccessPermission()
                    val spp = StandardProtectionPolicy(config.password, config.password, ap).apply {
                        encryptionKeyLength = 128
                    }
                    doc.protect(spp)
                    doc.save(outputFile)
                }
                tempUnencrypted.delete()
            } else {
                if (!tempUnencrypted.renameTo(outputFile)) {
                    tempUnencrypted.copyTo(outputFile, overwrite = true)
                    tempUnencrypted.delete()
                }
            }

            val savedRecord = repository.registerGeneratedPdf(
                file = outputFile,
                displayName = outputFile.nameWithoutExtension,
                pageCount = pages.size,
                isLocked = isLocked,
                sourceTag = "Image to PDF",
                fallbackThumbnail = firstThumbBitmap
            )
            firstThumbBitmap?.recycle()
            savedRecord
        }
    }

    private fun writeUsingAndroidPdfDocument(
        bitmaps: List<Bitmap>,
        config: PdfGenerationConfig,
        outFile: File
    ) {
        val pdfDocument = PdfDocument()
        try {
            bitmaps.forEachIndexed { index, bitmap ->
                val (pageW, pageH) = config.resolvePageDimensionsPt(bitmap.width, bitmap.height)
                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                drawImageOnPdfCanvas(
                    canvas = pdfPage.canvas,
                    pageWidth = pageW,
                    pageHeight = pageH,
                    bitmap = bitmap,
                    config = config,
                    pageNumber = index + 1,
                    totalPages = bitmaps.size
                )
                pdfDocument.finishPage(pdfPage)
            }
            FileOutputStream(outFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
        } finally {
            pdfDocument.close()
        }
    }

    private fun writeUsingPdfBoxFallback(
        bitmaps: List<Bitmap>,
        config: PdfGenerationConfig,
        jpegQuality: Int,
        outFile: File
    ) {
        PDDocument().use { doc ->
            bitmaps.forEachIndexed { index, bitmap ->
                val (pageW, pageH) = config.resolvePageDimensionsPt(bitmap.width, bitmap.height)
                val page = PDPage(PDRectangle(pageW.toFloat(), pageH.toFloat()))
                doc.addPage(page)

                val pdImage = JPEGFactory.createFromImage(doc, bitmap, jpegQuality / 100f)
                val margin = config.marginPt.coerceIn(0, minOf(pageW, pageH) / 4).toFloat()
                val availW = (pageW - margin * 2f).coerceAtLeast(10f)
                val availH = (pageH - margin * 2f).coerceAtLeast(10f)
                val imgW = bitmap.width.toFloat().coerceAtLeast(1f)
                val imgH = bitmap.height.toFloat().coerceAtLeast(1f)
                val scale = if (config.scaleMode == ImageScaleMode.FILL_PAGE) {
                    maxOf(availW / imgW, availH / imgH)
                } else {
                    minOf(availW / imgW, availH / imgH)
                }
                val drawW = imgW * scale
                val drawH = imgH * scale
                val drawX = margin + (availW - drawW) / 2f
                val drawY = margin + (availH - drawH) / 2f

                PDPageContentStream(doc, page).use { cs ->
                    val bgR = (config.backgroundColorArgb shr 16) and 0xFF
                    val bgG = (config.backgroundColorArgb shr 8) and 0xFF
                    val bgB = config.backgroundColorArgb and 0xFF
                    cs.setNonStrokingColor(bgR, bgG, bgB)
                    cs.addRect(0f, 0f, pageW.toFloat(), pageH.toFloat())
                    cs.fill()

                    cs.drawImage(pdImage, drawX, drawY, drawW, drawH)

                    if (config.watermarkText.isNotBlank()) {
                        val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.25f }
                        cs.setGraphicsStateParameters(gs)
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA_BOLD, 34f)
                        cs.setNonStrokingColor(237, 16, 16)
                        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(35.0), pageW / 3f, pageH / 2f))
                        cs.showText(config.watermarkText.trim())
                        cs.endText()
                    }

                    if (config.addPageNumbers) {
                        val gsOpaque = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 1f }
                        cs.setGraphicsStateParameters(gsOpaque)
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA, 11f)
                        cs.setNonStrokingColor(60, 60, 60)
                        cs.newLineAtOffset(pageW / 2f - 12f, (margin * 0.45f).coerceAtLeast(10f))
                        cs.showText("${index + 1} / ${bitmaps.size}")
                        cs.endText()
                    }
                }
            }
            doc.save(outFile)
        }
    }

    private fun drawImageOnPdfCanvas(
        canvas: Canvas,
        pageWidth: Int,
        pageHeight: Int,
        bitmap: Bitmap,
        config: PdfGenerationConfig,
        pageNumber: Int,
        totalPages: Int
    ) {
        canvas.drawColor(config.backgroundColorArgb)

        val margin = config.marginPt.coerceIn(0, minOf(pageWidth, pageHeight) / 4).toFloat()
        val contentRect = RectF(
            margin,
            margin,
            pageWidth.toFloat() - margin,
            pageHeight.toFloat() - margin
        )
        val availW = contentRect.width().coerceAtLeast(10f)
        val availH = contentRect.height().coerceAtLeast(10f)
        val imgW = bitmap.width.toFloat().coerceAtLeast(1f)
        val imgH = bitmap.height.toFloat().coerceAtLeast(1f)

        val drawRect = when (config.scaleMode) {
            ImageScaleMode.FIT_TO_PAGE, ImageScaleMode.MAINTAIN_ASPECT_RATIO -> {
                val scale = minOf(availW / imgW, availH / imgH)
                val targetW = imgW * scale
                val targetH = imgH * scale
                val left = contentRect.left + (availW - targetW) / 2f
                val top = contentRect.top + (availH - targetH) / 2f
                RectF(left, top, left + targetW, top + targetH)
            }
            ImageScaleMode.FILL_PAGE -> {
                val scale = maxOf(availW / imgW, availH / imgH)
                val targetW = imgW * scale
                val targetH = imgH * scale
                val left = contentRect.left + (availW - targetW) / 2f
                val top = contentRect.top + (availH - targetH) / 2f
                canvas.save()
                canvas.clipRect(contentRect)
                RectF(left, top, left + targetW, top + targetH)
            }
        }

        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), drawRect, imagePaint)
        if (config.scaleMode == ImageScaleMode.FILL_PAGE) {
            canvas.restore()
        }

        if (config.watermarkText.isNotBlank()) {
            val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(65, 237, 16, 16)
                textSize = (minOf(pageWidth, pageHeight) * 0.065f).coerceIn(18f, 64f)
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.save()
            canvas.translate(pageWidth / 2f, pageHeight / 2f)
            canvas.rotate(-35f)
            canvas.drawText(config.watermarkText.trim(), 0f, 0f, wmPaint)
            canvas.restore()
        }

        if (config.addPageNumbers) {
            val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 11f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                "$pageNumber / $totalPages",
                pageWidth / 2f,
                pageHeight - (margin * 0.45f).coerceAtLeast(10f),
                numPaint
            )
        }
    }
}
