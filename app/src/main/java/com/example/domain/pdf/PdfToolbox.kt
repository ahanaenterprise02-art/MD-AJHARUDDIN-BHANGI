package com.example.domain.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.data.db.PdfRecordEntity
import com.example.data.db.PdfRepository
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

data class CompressionResult(
    val record: PdfRecordEntity,
    val beforeBytes: Long,
    val afterBytes: Long
)

object PdfToolbox {

    suspend fun getPageCount(file: File, password: String? = null): Int = withContext(Dispatchers.IO) {
        if (password.isNullOrBlank()) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { it.pageCount }
                }
            }.getOrElse {
                runCatching {
                    PDDocument.load(file).use { it.numberOfPages }
                }.getOrDefault(0)
            }
        } else {
            runCatching {
                PDDocument.load(file, password).use { it.numberOfPages }
            }.getOrDefault(0)
        }
    }

    suspend fun isPdfEncrypted(file: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(file).use { doc ->
                val encrypted = doc.isEncrypted
                encrypted
            }
        }.getOrElse { ex ->
            ex.message?.contains("password", ignoreCase = true) == true ||
                ex.javaClass.simpleName.contains("InvalidPassword", ignoreCase = true)
        }
    }

    suspend fun renderPdfPageBitmap(
        file: File,
        pageIndex: Int,
        targetWidth: Int = 900
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        val h = ((page.height.toFloat() / page.width.coerceAtLeast(1)) * targetWidth)
                            .roundToInt().coerceIn(100, 3200)
                        val bmp = Bitmap.createBitmap(targetWidth, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }.getOrNull()
    }

    suspend fun renderAllPageThumbnails(
        file: File,
        thumbWidth: Int = 260,
        maxPages: Int = 40
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Bitmap>()
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = minOf(renderer.pageCount, maxPages)
                    for (i in 0 until count) {
                        renderer.openPage(i).use { page ->
                            val h = ((page.height.toFloat() / page.width.coerceAtLeast(1)) * thumbWidth)
                                .roundToInt().coerceIn(80, 720)
                            val bmp = Bitmap.createBitmap(thumbWidth, h, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            result.add(bmp)
                        }
                    }
                }
            }
        }
        result
    }

    suspend fun pdfToImages(
        pdfFile: File,
        format: Bitmap.CompressFormat,
        extension: String,
        outDir: File,
        onProgress: (Int, Int) -> Unit
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            outDir.mkdirs()
            val outputs = mutableListOf<File>()
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = renderer.pageCount
                    require(total > 0) { "PDF has no renderable pages." }
                    val stem = pdfFile.nameWithoutExtension
                    for (i in 0 until total) {
                        onProgress(i + 1, total)
                        renderer.openPage(i).use { page ->
                            val targetW = 1440
                            val targetH = ((page.height.toFloat() / page.width.coerceAtLeast(1)) * targetW)
                                .roundToInt().coerceIn(200, 3200)
                            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val outFile = File(outDir, "${stem}_page_${i + 1}.$extension")
                            FileOutputStream(outFile).use { fos ->
                                bmp.compress(format, 92, fos)
                            }
                            bmp.recycle()
                            outputs.add(outFile)
                        }
                    }
                }
            }
            outputs
        }
    }

    suspend fun mergePdfs(
        inputFiles: List<File>,
        outputName: String,
        repository: PdfRepository
    ): Result<PdfRecordEntity> = withContext(Dispatchers.IO) {
        runCatching {
            require(inputFiles.size >= 2) { "Select at least 2 PDF files to merge." }
            val outFile = repository.uniqueOutputFile(outputName)
            val merger = PDFMergerUtility()
            merger.destinationFileName = outFile.absolutePath
            inputFiles.forEach { merger.addSource(it) }
            merger.mergeDocuments(null)
            val pageCount = getPageCount(outFile)
            repository.registerGeneratedPdf(
                file = outFile,
                displayName = outFile.nameWithoutExtension,
                pageCount = pageCount,
                isLocked = false,
                sourceTag = "Merged PDF"
            )
        }
    }

    suspend fun splitPdf(
        inputFile: File,
        pageRangesInput: String,
        splitEachPage: Boolean,
        outputBaseName: String,
        repository: PdfRepository
    ): Result<List<PdfRecordEntity>> = withContext(Dispatchers.IO) {
        runCatching {
            val results = mutableListOf<PdfRecordEntity>()
            PDDocument.load(inputFile).use { sourceDoc ->
                val total = sourceDoc.numberOfPages
                require(total > 0) { "Empty PDF document." }

                if (splitEachPage) {
                    for (i in 0 until total) {
                        PDDocument().use { singleDoc ->
                            singleDoc.importPage(sourceDoc.getPage(i))
                            val outFile = repository.uniqueOutputFile("${outputBaseName}_p${i + 1}")
                            singleDoc.save(outFile)
                            results.add(
                                repository.registerGeneratedPdf(
                                    file = outFile,
                                    displayName = outFile.nameWithoutExtension,
                                    pageCount = 1,
                                    isLocked = false,
                                    sourceTag = "Split PDF"
                                )
                            )
                        }
                    }
                } else {
                    val selectedIndices = parsePageRanges(pageRangesInput, total)
                    require(selectedIndices.isNotEmpty()) { "Enter valid page numbers (1-$total), e.g. 1-3, 5" }
                    PDDocument().use { splitDoc ->
                        selectedIndices.forEach { idx ->
                            splitDoc.importPage(sourceDoc.getPage(idx))
                        }
                        val outFile = repository.uniqueOutputFile(outputBaseName)
                        splitDoc.save(outFile)
                        results.add(
                            repository.registerGeneratedPdf(
                                file = outFile,
                                displayName = outFile.nameWithoutExtension,
                                pageCount = selectedIndices.size,
                                isLocked = false,
                                sourceTag = "Split PDF"
                            )
                        )
                    }
                }
            }
            results
        }
    }

    suspend fun compressPdf(
        inputFile: File,
        qualityPercent: Int,
        maxImageDim: Int,
        outputName: String,
        repository: PdfRepository
    ): Result<CompressionResult> = withContext(Dispatchers.IO) {
        runCatching {
            val beforeBytes = inputFile.length()
            val outFile = repository.uniqueOutputFile(outputName)
            var compressedImages = 0
            PDDocument.load(inputFile).use { doc ->
                for (page in doc.pages) {
                    val res = page.resources ?: continue
                    for (name in res.xObjectNames) {
                        val xobj = runCatching { res.getXObject(name) }.getOrNull()
                        if (xobj is PDImageXObject) {
                            val bmp = runCatching { xobj.image }.getOrNull() ?: continue
                            val maxSide = maxOf(bmp.width, bmp.height)
                            val scaled = if (maxSide > maxImageDim) {
                                val s = maxImageDim.toFloat() / maxSide
                                Bitmap.createScaledBitmap(
                                    bmp,
                                    (bmp.width * s).roundToInt().coerceAtLeast(1),
                                    (bmp.height * s).roundToInt().coerceAtLeast(1),
                                    true
                                )
                            } else bmp
                            val jpeg = JPEGFactory.createFromImage(doc, scaled, qualityPercent / 100f)
                            res.put(name, jpeg)
                            if (scaled !== bmp) scaled.recycle()
                            bmp.recycle()
                            compressedImages++
                        }
                    }
                }
                doc.save(outFile)
            }

            // If PDF had vector-only/non-XObject content and did not shrink, raster-optimize cleanly if requested
            if (compressedImages == 0 || outFile.length() >= beforeBytes) {
                val rasterFile = File(outFile.parentFile, "raster_tmp_${System.currentTimeMillis()}.pdf")
                val renderedCount = rasterCompressFallback(inputFile, rasterFile, qualityPercent, maxImageDim)
                if (renderedCount > 0 && rasterFile.length() < outFile.length()) {
                    rasterFile.copyTo(outFile, overwrite = true)
                }
                rasterFile.delete()
            }

            val pageCount = getPageCount(outFile)
            val record = repository.registerGeneratedPdf(
                file = outFile,
                displayName = outFile.nameWithoutExtension,
                pageCount = pageCount,
                isLocked = false,
                sourceTag = "Compressed PDF"
            )
            CompressionResult(record = record, beforeBytes = beforeBytes, afterBytes = outFile.length())
        }
    }

    private fun rasterCompressFallback(
        inputFile: File,
        outFile: File,
        qualityPercent: Int,
        maxDim: Int
    ): Int {
        var count = 0
        runCatching {
            ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pdfDoc = android.graphics.pdf.PdfDocument()
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val targetW = minOf(maxDim, 1200)
                            val targetH = ((page.height.toFloat() / page.width.coerceAtLeast(1)) * targetW)
                                .roundToInt().coerceAtLeast(100)
                            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val baos = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, qualityPercent.coerceIn(30, 90), baos)
                            bmp.recycle()
                            val bytes = baos.toByteArray()
                            val redecoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use

                            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(page.width, page.height, i + 1).create()
                            val outPage = pdfDoc.startPage(pageInfo)
                            outPage.canvas.drawBitmap(
                                redecoded,
                                null,
                                android.graphics.Rect(0, 0, page.width, page.height),
                                null
                            )
                            pdfDoc.finishPage(outPage)
                            redecoded.recycle()
                            count++
                        }
                    }
                    FileOutputStream(outFile).use { fos -> pdfDoc.writeTo(fos) }
                    pdfDoc.close()
                }
            }
        }
        return count
    }

    suspend fun rotatePdf(
        inputFile: File,
        rotationsByPage: Map<Int, Int>,
        outputName: String,
        repository: PdfRepository
    ): Result<PdfRecordEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = repository.uniqueOutputFile(outputName)
            PDDocument.load(inputFile).use { doc ->
                for (i in 0 until doc.numberOfPages) {
                    val delta = rotationsByPage[i] ?: 0
                    if (delta != 0) {
                        val page = doc.getPage(i)
                        page.rotation = (page.rotation + delta + 360) % 360
                    }
                }
                doc.save(outFile)
            }
            repository.registerGeneratedPdf(
                file = outFile,
                displayName = outFile.nameWithoutExtension,
                pageCount = getPageCount(outFile),
                isLocked = false,
                sourceTag = "Rotated PDF"
            )
        }
    }

    suspend fun deletePdfPages(
        inputFile: File,
        pagesToDeleteZeroBased: Set<Int>,
        outputName: String,
        repository: PdfRepository
    ): Result<PdfRecordEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = repository.uniqueOutputFile(outputName)
            PDDocument.load(inputFile).use { doc ->
                val total = doc.numberOfPages
                require(pagesToDeleteZeroBased.isNotEmpty()) { "Select at least 1 page to delete." }
                require(pagesToDeleteZeroBased.size < total) { "Cannot delete all pages from a PDF." }
                pagesToDeleteZeroBased.sortedDescending().forEach { idx ->
                    if (idx in 0 until doc.numberOfPages) {
                        doc.removePage(idx)
                    }
                }
                doc.save(outFile)
            }
            repository.registerGeneratedPdf(
                file = outFile,
                displayName = outFile.nameWithoutExtension,
                pageCount = getPageCount(outFile),
                isLocked = false,
                sourceTag = "Edited PDF"
            )
        }
    }

    suspend fun addTextToPdf(
        inputFile: File,
        pageIndexOrNullForAll: Int?,
        text: String,
        xRatio: Float,
        yRatioFromTop: Float,
        fontSizePt: Float,
        colorArgb: Int,
        outputName: String,
        repository: PdfRepository
    ): Result<PdfRecordEntity> = withContext(Dispatchers.IO) {
        runCatching {
            require(text.isNotBlank()) { "Enter text to add." }
            val outFile = repository.uniqueOutputFile(outputName)
            PDDocument.load(inputFile).use { doc ->
                val targetIndices = if (pageIndexOrNullForAll == null) {
                    (0 until doc.numberOfPages).toList()
                } else {
                    listOf(pageIndexOrNullForAll.coerceIn(0, doc.numberOfPages - 1))
                }
                val r = (colorArgb shr 16) and 0xFF
                val g = (colorArgb shr 8) and 0xFF
                val b = colorArgb and 0xFF
                val safeText = text.replace("\n", " ").filter { it.code in 32..126 }
                val finalString = safeText.ifBlank { text }

                for (idx in targetIndices) {
                    val page: PDPage = doc.getPage(idx)
                    val box = page.mediaBox
                    val x = box.lowerLeftX + box.width * xRatio.coerceIn(0.04f, 0.90f)
                    val y = box.lowerLeftY + box.height * (1f - yRatioFromTop.coerceIn(0.05f, 0.95f))

                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA_BOLD, fontSizePt.coerceIn(8f, 72f))
                        cs.setNonStrokingColor(r, g, b)
                        cs.newLineAtOffset(x, y)
                        cs.showText(finalString)
                        cs.endText()
                    }
                }
                doc.save(outFile)
            }
            repository.registerGeneratedPdf(
                file = outFile,
                displayName = outFile.nameWithoutExtension,
                pageCount = getPageCount(outFile),
                isLocked = false,
                sourceTag = "Text Added"
            )
        }
    }

    suspend fun addSignatureToPdf(
        inputFile: File,
        pageIndex: Int,
        signatureBitmap: Bitmap,
        xRatio: Float,
        yRatioFromTop: Float,
        widthRatio: Float,
        outputName: String,
        repository: PdfRepository
    ): Result<PdfRecordEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = repository.uniqueOutputFile(outputName)
            PDDocument.load(inputFile).use { doc ->
                val idx = pageIndex.coerceIn(0, doc.numberOfPages - 1)
                val page = doc.getPage(idx)
                val box = page.mediaBox
                val pdImage = LosslessFactory.createFromImage(doc, signatureBitmap)
                val sigW = box.width * widthRatio.coerceIn(0.12f, 0.7f)
                val aspect = signatureBitmap.height.toFloat() / signatureBitmap.width.coerceAtLeast(1)
                val sigH = sigW * aspect
                val x = box.lowerLeftX + (box.width * xRatio.coerceIn(0.02f, 0.90f)) - sigW / 2f
                val y = box.lowerLeftY + (box.height * (1f - yRatioFromTop.coerceIn(0.05f, 0.95f))) - sigH / 2f

                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.drawImage(
                        pdImage,
                        x.coerceIn(box.lowerLeftX, box.upperRightX - sigW),
                        y.coerceIn(box.lowerLeftY, box.upperRightY - sigH),
                        sigW,
                        sigH
                    )
                }
                doc.save(outFile)
            }
            repository.registerGeneratedPdf(
                file = outFile,
                displayName = outFile.nameWithoutExtension,
                pageCount = getPageCount(outFile),
                isLocked = false,
                sourceTag = "Signed PDF"
            )
        }
    }

    suspend fun addWatermarkToPdf(
        inputFile: File,
        watermarkText: String,
        opacity: Float,
        angleDegrees: Float,
        fontSizePt: Float,
        colorArgb: Int,
        outputName: String,
        repository: PdfRepository
    ): Result<PdfRecordEntity> = withContext(Dispatchers.IO) {
        runCatching {
            require(watermarkText.isNotBlank()) { "Enter watermark text." }
            val outFile = repository.uniqueOutputFile(outputName)
            val safeText = watermarkText.trim().filter { it.code in 32..126 }.ifBlank { "CONFIDENTIAL" }
            val r = (colorArgb shr 16) and 0xFF
            val g = (colorArgb shr 8) and 0xFF
            val b = colorArgb and 0xFF

            PDDocument.load(inputFile).use { doc ->
                val extGState = PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = opacity.coerceIn(0.08f, 0.85f)
                }
                for (page in doc.pages) {
                    val box = page.mediaBox
                    val cx = box.lowerLeftX + box.width / 2f
                    val cy = box.lowerLeftY + box.height / 2f
                    val font = PDType1Font.HELVETICA_BOLD
                    val textW = font.getStringWidth(safeText) / 1000f * fontSizePt

                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.setGraphicsStateParameters(extGState)
                        cs.beginText()
                        cs.setFont(font, fontSizePt)
                        cs.setNonStrokingColor(r, g, b)
                        val rad = Math.toRadians(angleDegrees.toDouble())
                        val tx = cx - (textW / 2f) * kotlin.math.cos(rad).toFloat()
                        val ty = cy - (textW / 2f) * kotlin.math.sin(rad).toFloat()
                        cs.setTextMatrix(Matrix.getRotateInstance(rad, tx, ty))
                        cs.showText(safeText)
                        cs.endText()
                    }
                }
                doc.save(outFile)
            }
            repository.registerGeneratedPdf(
                file = outFile,
                displayName = outFile.nameWithoutExtension,
                pageCount = getPageCount(outFile),
                isLocked = false,
                sourceTag = "Watermarked"
            )
        }
    }

    suspend fun addPageNumbersToPdf(
        inputFile: File,
        formatStyle: Int, // 0: "1", 1: "Page 1 of N", 2: "1 / N"
        bottomCenter: Boolean,
        startNumber: Int,
        outputName: String,
        repository: PdfRepository
    ): Result<PdfRecordEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = repository.uniqueOutputFile(outputName)
            PDDocument.load(inputFile).use { doc ->
                val total = doc.numberOfPages
                val font = PDType1Font.HELVETICA
                val fontSize = 11f
                for (i in 0 until total) {
                    val num = startNumber + i
                    val label = when (formatStyle) {
                        1 -> "Page $num of ${startNumber + total - 1}"
                        2 -> "$num / ${startNumber + total - 1}"
                        else -> "$num"
                    }
                    val page = doc.getPage(i)
                    val box = page.mediaBox
                    val textW = font.getStringWidth(label) / 1000f * fontSize
                    val x = if (bottomCenter) {
                        box.lowerLeftX + (box.width - textW) / 2f
                    } else {
                        box.upperRightX - textW - 28f
                    }
                    val y = box.lowerLeftY + 20f

                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.beginText()
                        cs.setFont(font, fontSize)
                        cs.setNonStrokingColor(60, 60, 60)
                        cs.newLineAtOffset(x, y)
                        cs.showText(label)
                        cs.endText()
                    }
                }
                doc.save(outFile)
            }
            repository.registerGeneratedPdf(
                file = outFile,
                displayName = outFile.nameWithoutExtension,
                pageCount = getPageCount(outFile),
                isLocked = false,
                sourceTag = "Numbered PDF"
            )
        }
    }

    suspend fun lockPdf(
        inputFile: File,
        password: String,
        outputName: String,
        repository: PdfRepository
    ): Result<PdfRecordEntity> = withContext(Dispatchers.IO) {
        runCatching {
            require(password.isNotBlank()) { "Please enter a password." }
            val pageCount = getPageCount(inputFile)
            val thumbFallback = renderPdfPageBitmap(inputFile, 0, 320)
            val outFile = repository.uniqueOutputFile(outputName)
            PDDocument.load(inputFile).use { doc ->
                val ap = AccessPermission()
                val spp = StandardProtectionPolicy(password, password, ap).apply {
                    encryptionKeyLength = 128
                }
                doc.protect(spp)
                doc.save(outFile)
            }
            val record = repository.registerGeneratedPdf(
                file = outFile,
                displayName = outFile.nameWithoutExtension,
                pageCount = pageCount.coerceAtLeast(1),
                isLocked = true,
                sourceTag = "Locked PDF",
                fallbackThumbnail = thumbFallback
            )
            thumbFallback?.recycle()
            record
        }
    }

    suspend fun unlockPdf(
        inputFile: File,
        password: String,
        outputName: String,
        repository: PdfRepository
    ): Result<PdfRecordEntity> = withContext(Dispatchers.IO) {
        runCatching {
            require(password.isNotBlank()) { "Please enter the PDF password." }
            val outFile = repository.uniqueOutputFile(outputName)
            PDDocument.load(inputFile, password).use { doc ->
                doc.isAllSecurityToBeRemoved = true
                doc.save(outFile)
            }
            repository.registerGeneratedPdf(
                file = outFile,
                displayName = outFile.nameWithoutExtension,
                pageCount = getPageCount(outFile),
                isLocked = false,
                sourceTag = "Unlocked PDF"
            )
        }
    }

    fun parsePageRanges(input: String, totalPages: Int): List<Int> {
        val result = linkedSetOf<Int>()
        val parts = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        for (part in parts) {
            if (part.contains("-")) {
                val bounds = part.split("-").mapNotNull { it.trim().toIntOrNull() }
                if (bounds.size == 2) {
                    val start = bounds[0].coerceIn(1, totalPages)
                    val end = bounds[1].coerceIn(1, totalPages)
                    val range = if (start <= end) start..end else end..start
                    range.forEach { result.add(it - 1) }
                }
            } else {
                part.toIntOrNull()?.let { p ->
                    if (p in 1..totalPages) result.add(p - 1)
                }
            }
        }
        return result.toList()
    }
}
