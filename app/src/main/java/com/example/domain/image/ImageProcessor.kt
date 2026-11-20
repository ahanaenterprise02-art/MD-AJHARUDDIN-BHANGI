package com.example.domain.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import com.example.domain.model.EditableImagePage
import com.example.domain.model.ImageFilterType
import com.example.domain.model.NormalizedCropRect
import com.example.domain.model.NormalizedQuad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

object ImageProcessor {

    suspend fun copyUriToOriginals(context: Context, uri: Uri, originalsDir: File): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                originalsDir.mkdirs()
                val outFile = File(originalsDir, "orig_${System.currentTimeMillis()}_${uri.hashCode()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext null
                outFile.absolutePath
            }.getOrNull()
        }

    suspend fun renderProcessedBitmap(
        page: EditableImagePage,
        maxDimension: Int = 1400,
        applyFilters: Boolean = true
    ): Bitmap? = withContext(Dispatchers.Default) {
        val raw = decodeSampledBitmapWithExif(page.originalPath, maxDimension) ?: return@withContext null
        var current = raw

        // 1. User rotation
        if (page.rotationDegrees % 360 != 0) {
            val rotMatrix = Matrix().apply { postRotate((page.rotationDegrees % 360).toFloat()) }
            val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, rotMatrix, true)
            if (rotated !== current) current.recycle()
            current = rotated
        }

        // 2. Perspective quad correction (if scanned with quad)
        val quad = page.perspectiveQuad
        if (quad != null && !quad.isDefaultFull) {
            val warped = warpPerspective(current, quad)
            if (warped !== current) current.recycle()
            current = warped
        }

        // 3. Rectangular crop
        if (!page.cropRect.isFull) {
            val cropL = (page.cropRect.left.coerceIn(0f, 0.95f) * current.width).roundToInt()
            val cropT = (page.cropRect.top.coerceIn(0f, 0.95f) * current.height).roundToInt()
            val cropR = (page.cropRect.right.coerceIn(page.cropRect.left + 0.05f, 1f) * current.width).roundToInt()
            val cropB = (page.cropRect.bottom.coerceIn(page.cropRect.top + 0.05f, 1f) * current.height).roundToInt()
            val cropW = (cropR - cropL).coerceAtLeast(16)
            val cropH = (cropB - cropT).coerceAtLeast(16)
            val cropped = Bitmap.createBitmap(current, cropL, cropT, cropW, cropH)
            if (cropped !== current) current.recycle()
            current = cropped
        }

        // 4. Filter + brightness/contrast/saturation
        if (applyFilters && (page.filter != ImageFilterType.ORIGINAL ||
                abs(page.brightness) > 0.5f ||
                abs(page.contrast - 1f) > 0.02f ||
                abs(page.saturation - 1f) > 0.02f)
        ) {
            val filtered = applyFilterAndAdjustments(
                source = current,
                filter = page.filter,
                brightness = page.brightness,
                contrast = page.contrast,
                saturation = page.saturation
            )
            if (filtered !== current) current.recycle()
            current = filtered
        }

        current
    }

    fun decodeSampledBitmapWithExif(filePath: String, maxDimension: Int): Bitmap? {
        val file = File(filePath)
        if (!file.exists()) return null

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, boundsOpts)
        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        var sampleSize = 1
        while (rawW / sampleSize > maxDimension * 2 || rawH / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(filePath, decodeOpts) ?: return null

        val exifRotation = runCatching {
            when (ExifInterface(filePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        var oriented = decoded
        if (exifRotation != 0f) {
            val matrix = Matrix().apply { postRotate(exifRotation) }
            oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (oriented !== decoded) decoded.recycle()
        }

        val maxNow = maxOf(oriented.width, oriented.height)
        if (maxNow > maxDimension) {
            val scale = maxDimension.toFloat() / maxNow
            val targetW = (oriented.width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (oriented.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(oriented, targetW, targetH, true)
            if (scaled !== oriented) oriented.recycle()
            return scaled
        }
        return oriented
    }

    fun warpPerspective(source: Bitmap, quad: NormalizedQuad): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()

        val tlX = quad.topLeftX * w
        val tlY = quad.topLeftY * h
        val trX = quad.topRightX * w
        val trY = quad.topRightY * h
        val brX = quad.bottomRightX * w
        val brY = quad.bottomRightY * h
        val blX = quad.bottomLeftX * w
        val blY = quad.bottomLeftY * h

        val topWidth = hypot(trX - tlX, trY - tlY)
        val bottomWidth = hypot(brX - blX, brY - blY)
        val leftHeight = hypot(blX - tlX, blY - tlY)
        val rightHeight = hypot(brX - trX, brY - trY)

        val outW = maxOf(topWidth, bottomWidth).roundToInt().coerceIn(64, source.width * 2)
        val outH = maxOf(leftHeight, rightHeight).roundToInt().coerceIn(64, source.height * 2)

        val srcPoints = floatArrayOf(tlX, tlY, trX, trY, brX, brY, blX, blY)
        val dstPoints = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())

        val matrix = Matrix()
        val ok = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)
        if (!ok) return source.copy(Bitmap.Config.ARGB_8888, false)

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return output
    }

    fun applyFilterAndAdjustments(
        source: Bitmap,
        filter: ImageFilterType,
        brightness: Float,
        contrast: Float,
        saturation: Float
    ): Bitmap {
        var working = when (filter) {
            ImageFilterType.AUTO_ENHANCE -> autoEnhanceBitmap(source)
            ImageFilterType.SHARPEN -> sharpenBitmap(source)
            ImageFilterType.BLACK_AND_WHITE -> blackAndWhiteBitmap(source)
            ImageFilterType.DOCUMENT -> documentEnhanceBitmap(source)
            else -> source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val matrix = ColorMatrix()
        when (filter) {
            ImageFilterType.GRAYSCALE -> matrix.setSaturation(0f)
            ImageFilterType.COLOR -> {
                matrix.setSaturation(1.38f)
                matrix.postConcat(contrastBrightnessMatrix(1.12f, 4f))
            }
            ImageFilterType.BRIGHT -> {
                matrix.postConcat(contrastBrightnessMatrix(1.06f, 28f))
            }
            ImageFilterType.HIGH_CONTRAST -> {
                matrix.postConcat(contrastBrightnessMatrix(1.48f, -12f))
            }
            ImageFilterType.MAGIC_COLOR -> {
                matrix.setSaturation(1.45f)
                matrix.postConcat(contrastBrightnessMatrix(1.30f, 12f))
            }
            else -> Unit
        }

        // Apply user slider adjustments on top
        if (abs(saturation - 1f) > 0.02f) {
            val satMatrix = ColorMatrix().apply { setSaturation(saturation) }
            matrix.postConcat(satMatrix)
        }
        if (abs(contrast - 1f) > 0.02f || abs(brightness) > 0.5f) {
            matrix.postConcat(contrastBrightnessMatrix(contrast, brightness))
        }

        val out = Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(working, 0f, 0f, paint)
        if (working !== source) working.recycle()
        return out
    }

    private fun contrastBrightnessMatrix(contrast: Float, brightness: Float): ColorMatrix {
        val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
        return ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun documentEnhanceBitmap(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val luma = (0.299f * r + 0.587f * g + 0.114f * b)
            // Brighten light paper areas while deepening ink strokes
            val factor = when {
                luma > 185f -> 1.25f
                luma < 95f -> 0.72f
                else -> 1.05f
            }
            val nr = ((r - 128) * 1.32f + 142f).times(if (luma > 185f) 1.08f else 1f).coerceIn(0f, 255f).toInt()
            val ng = ((g - 128) * 1.32f + 142f).times(if (luma > 185f) 1.08f else 1f).coerceIn(0f, 255f).toInt()
            val nb = ((b - 128) * 1.32f + 142f).times(if (luma > 185f) 1.08f else 1f).coerceIn(0f, 255f).toInt()
            pixels[i] = (0xFF shl 24) or
                ((nr * factor).coerceIn(0f, 255f).toInt() shl 16) or
                ((ng * factor).coerceIn(0f, 255f).toInt() shl 8) or
                (nb * factor).coerceIn(0f, 255f).toInt()
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun blackAndWhiteBitmap(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        var sum = 0L
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sum += (0.299f * r + 0.587f * g + 0.114f * b).toInt()
        }
        val mean = (sum / pixels.size.coerceAtLeast(1)).toFloat()
        val threshold = (mean * 0.92f).coerceIn(85f, 175f)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            val v = when {
                luma < threshold - 22f -> 0
                luma > threshold + 22f -> 255
                else -> (((luma - (threshold - 22f)) / 44f) * 255f).toInt().coerceIn(0, 255)
            }
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun autoEnhanceBitmap(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val hist = IntArray(256)
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val luma = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
            hist[luma]++
        }
        val total = pixels.size
        val lowTarget = (total * 0.02f).toInt()
        val highTarget = (total * 0.98f).toInt()
        var acc = 0
        var minL = 0
        var maxL = 255
        for (i in 0..255) {
            acc += hist[i]
            if (acc >= lowTarget) {
                minL = i
                break
            }
        }
        acc = 0
        for (i in 0..255) {
            acc += hist[i]
            if (acc >= highTarget) {
                maxL = i
                break
            }
        }
        val range = (maxL - minL).coerceAtLeast(32).toFloat()
        val scale = 255f / range
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (((p shr 16) and 0xFF) - minL) * scale
            val g = (((p shr 8) and 0xFF) - minL) * scale
            val b = ((p and 0xFF) - minL) * scale
            pixels[i] = (0xFF shl 24) or
                (r.coerceIn(0f, 255f).toInt() shl 16) or
                (g.coerceIn(0f, 255f).toInt() shl 8) or
                b.coerceIn(0f, 255f).toInt()
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun sharpenBitmap(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val src = IntArray(w * h)
        val dst = IntArray(w * h)
        source.getPixels(src, 0, w, 0, 0, w, h)
        System.arraycopy(src, 0, dst, 0, src.size)
        // 3x3 Sharpen kernel: [0, -1, 0; -1, 5, -1; 0, -1, 0]
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                val c = src[idx]
                val u = src[idx - w]
                val d = src[idx + w]
                val l = src[idx - 1]
                val r = src[idx + 1]

                val red = 5 * ((c shr 16) and 0xFF) - ((u shr 16) and 0xFF) - ((d shr 16) and 0xFF) - ((l shr 16) and 0xFF) - ((r shr 16) and 0xFF)
                val grn = 5 * ((c shr 8) and 0xFF) - ((u shr 8) and 0xFF) - ((d shr 8) and 0xFF) - ((l shr 8) and 0xFF) - ((r shr 8) and 0xFF)
                val blu = 5 * (c and 0xFF) - (u and 0xFF) - (d and 0xFF) - (l and 0xFF) - (r and 0xFF)

                dst[idx] = (0xFF shl 24) or
                    (red.coerceIn(0, 255) shl 16) or
                    (grn.coerceIn(0, 255) shl 8) or
                    blu.coerceIn(0, 255)
            }
        }
        return Bitmap.createBitmap(dst, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Lightweight gradient-based document edge estimator for CameraX ImageAnalysis & initial scan crop.
     */
    fun estimateDocumentQuadFromLuma(lumaBytes: ByteArray, width: Int, height: Int): NormalizedQuad {
        if (width < 16 || height < 16 || lumaBytes.size < width * height) {
            return NormalizedQuad()
        }
        val step = 4
        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0
        var edgeHits = 0

        for (y in step until height - step step step) {
            val row = y * width
            for (x in step until width - step step step) {
                val c = lumaBytes[row + x].toInt() and 0xFF
                val right = lumaBytes[row + x + step].toInt() and 0xFF
                val down = lumaBytes[(y + step) * width + x].toInt() and 0xFF
                val grad = abs(c - right) + abs(c - down)
                if (grad > 48) {
                    edgeHits++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (edgeHits < 12 || (maxX - minX) < width * 0.2f || (maxY - minY) < height * 0.2f) {
            return NormalizedQuad(0.10f, 0.12f, 0.90f, 0.12f, 0.90f, 0.88f, 0.10f, 0.88f)
        }

        val l = (minX.toFloat() / width).coerceIn(0.05f, 0.45f)
        val r = (maxX.toFloat() / width).coerceIn(0.55f, 0.95f)
        val t = (minY.toFloat() / height).coerceIn(0.05f, 0.45f)
        val b = (maxY.toFloat() / height).coerceIn(0.55f, 0.95f)
        return NormalizedQuad(l, t, r, t, r, b, l, b)
    }

    /**
     * Automatically detects the bounding box and 4-corner quad of a document or foreground subject
     * by comparing RGB distance from perimeter background and local luminance gradients.
     */
    fun autoDetectCropAndQuad(source: Bitmap): Pair<NormalizedCropRect, NormalizedQuad> {
        val maxSide = 220
        val scale = minOf(maxSide.toFloat() / source.width.coerceAtLeast(1), maxSide.toFloat() / source.height.coerceAtLeast(1), 1f)
        val w = (source.width * scale).roundToInt().coerceAtLeast(24)
        val h = (source.height * scale).roundToInt().coerceAtLeast(24)
        val scaled = if (w != source.width || h != source.height) {
            Bitmap.createScaledBitmap(source, w, h, true)
        } else {
            source
        }

        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== source) scaled.recycle()

        // Sample outer border pixels to estimate background color
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var borderCount = 0L
        val borderDepth = 2
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (x < borderDepth || x >= w - borderDepth || y < borderDepth || y >= h - borderDepth) {
                    val p = pixels[y * w + x]
                    sumR += (p shr 16) and 0xFF
                    sumG += (p shr 8) and 0xFF
                    sumB += p and 0xFF
                    borderCount++
                }
            }
        }
        val bgR = (sumR / borderCount.coerceAtLeast(1)).toInt()
        val bgG = (sumG / borderCount.coerceAtLeast(1)).toInt()
        val bgB = (sumB / borderCount.coerceAtLeast(1)).toInt()

        val rowHits = IntArray(h)
        val colHits = IntArray(w)
        val fgMask = BooleanArray(w * h)

        for (y in 0 until h - 1) {
            val rowOffset = y * w
            for (x in 0 until w - 1) {
                val p = pixels[rowOffset + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                val colorDist = abs(r - bgR) + abs(g - bgG) + abs(b - bgB)

                val pRight = pixels[rowOffset + x + 1]
                val pDown = pixels[(y + 1) * w + x]
                val lumaC = (r * 3 + g * 6 + b) / 10
                val lumaR = (((pRight shr 16) and 0xFF) * 3 + ((pRight shr 8) and 0xFF) * 6 + (pRight and 0xFF)) / 10
                val lumaD = (((pDown shr 16) and 0xFF) * 3 + ((pDown shr 8) and 0xFF) * 6 + (pDown and 0xFF)) / 10
                val grad = abs(lumaC - lumaR) + abs(lumaC - lumaD)

                if (colorDist > 42 || grad > 34) {
                    fgMask[rowOffset + x] = true
                    rowHits[y]++
                    colHits[x]++
                }
            }
        }

        val minRowHits = (w * 0.05f).toInt().coerceAtLeast(3)
        val minColHits = (h * 0.05f).toInt().coerceAtLeast(3)

        var top = 0
        while (top < h && rowHits[top] < minRowHits) top++

        var bottom = h - 1
        while (bottom > top && rowHits[bottom] < minRowHits) bottom--

        var left = 0
        while (left < w && colHits[left] < minColHits) left++

        var right = w - 1
        while (right > left && colHits[right] < minColHits) right--

        val widthFrac = (right - left).toFloat() / w
        val heightFrac = (bottom - top).toFloat() / h

        if (widthFrac < 0.16f || heightFrac < 0.16f) {
            return NormalizedCropRect() to NormalizedQuad(0.03f, 0.03f, 0.97f, 0.03f, 0.97f, 0.97f, 0.03f, 0.97f)
        }

        // Pad outward slightly (1.5%) to avoid clipping document borders
        val padX = 0.015f
        val padY = 0.015f
        val normL = ((left.toFloat() / w) - padX).coerceIn(0f, 0.80f)
        val normT = ((top.toFloat() / h) - padY).coerceIn(0f, 0.80f)
        val normR = ((right.toFloat() / w) + padX).coerceIn((normL + 0.15f).coerceAtMost(1f), 1f)
        val normB = ((bottom.toFloat() / h) + padY).coerceIn((normT + 0.15f).coerceAtMost(1f), 1f)

        val rect = NormalizedCropRect(normL, normT, normR, normB)
        val quad = NormalizedQuad(
            topLeftX = normL, topLeftY = normT,
            topRightX = normR, topRightY = normT,
            bottomRightX = normR, bottomRightY = normB,
            bottomLeftX = normL, bottomLeftY = normB
        )
        return rect to quad
    }
}
