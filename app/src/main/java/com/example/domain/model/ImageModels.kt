package com.example.domain.model

import android.net.Uri
import java.util.UUID

enum class ImageFilterType(val label: String) {
    ORIGINAL("Original"),
    DOCUMENT("Document"),
    BLACK_AND_WHITE("B & W"),
    GRAYSCALE("Grayscale"),
    COLOR("Color"),
    BRIGHT("Bright"),
    HIGH_CONTRAST("Contrast"),
    MAGIC_COLOR("Magic Color"),
    SHARPEN("Sharpen"),
    AUTO_ENHANCE("Auto Enhance")
}

data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val isFull: Boolean
        get() = left <= 0.005f && top <= 0.005f && right >= 0.995f && bottom >= 0.995f
}

data class NormalizedQuad(
    val topLeftX: Float = 0.08f,
    val topLeftY: Float = 0.08f,
    val topRightX: Float = 0.92f,
    val topRightY: Float = 0.08f,
    val bottomRightX: Float = 0.92f,
    val bottomRightY: Float = 0.92f,
    val bottomLeftX: Float = 0.08f,
    val bottomLeftY: Float = 0.92f
) {
    val isDefaultFull: Boolean
        get() = topLeftX <= 0.01f && topLeftY <= 0.01f &&
            topRightX >= 0.99f && topRightY <= 0.01f &&
            bottomRightX >= 0.99f && bottomRightY >= 0.99f &&
            bottomLeftX <= 0.01f && bottomLeftY >= 0.99f
}

data class EditableImagePage(
    val id: String = UUID.randomUUID().toString(),
    val originalPath: String,
    val rotationDegrees: Int = 0,
    val cropRect: NormalizedCropRect = NormalizedCropRect(),
    val perspectiveQuad: NormalizedQuad? = null,
    val filter: ImageFilterType = ImageFilterType.ORIGINAL,
    val brightness: Float = 0f, // -100f..100f
    val contrast: Float = 1f,   // 0.5f..2.0f
    val saturation: Float = 1f  // 0f..2.0f
)

enum class PageSizePreset(val label: String, val widthMm: Float, val heightMm: Float) {
    A4("A4", 210f, 297f),
    A3("A3", 297f, 420f),
    A5("A5", 148f, 210f),
    LETTER("Letter", 215.9f, 279.4f),
    LEGAL("Legal", 215.9f, 355.6f),
    EXECUTIVE("Executive", 184.15f, 266.7f),
    FIT_TO_IMAGE("Fit to Image", 0f, 0f),
    CUSTOM("Custom Size", 210f, 297f)
}

enum class MeasurementUnit(val label: String, val shortLabel: String) {
    MILLIMETER("Millimeter", "mm"),
    CENTIMETER("Centimeter", "cm"),
    INCH("Inches", "in")
}

enum class PageOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class ImageScaleMode(val label: String) {
    FIT_TO_PAGE("Fit to Page"),
    FILL_PAGE("Fill Page"),
    MAINTAIN_ASPECT_RATIO("Maintain Aspect Ratio")
}

enum class ImageQualityLevel(val label: String, val jpegQuality: Int, val maxDim: Int) {
    LOW("Low", 55, 1200),
    MEDIUM("Medium", 75, 1650),
    HIGH("High", 90, 2200),
    MAXIMUM("Maximum", 98, 3000)
}

enum class CompressionLevel(val label: String, val scaleFactor: Float, val jpegQualityDelta: Int) {
    NONE("None", 1.0f, 0),
    BALANCED("Balanced", 0.85f, -8),
    HIGH("High", 0.65f, -20)
}

data class PdfGenerationConfig(
    val fileName: String = "AhaPDF_Document",
    val pageSizePreset: PageSizePreset = PageSizePreset.A4,
    val customWidth: Float = 210f,
    val customHeight: Float = 297f,
    val unit: MeasurementUnit = MeasurementUnit.MILLIMETER,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val marginPt: Int = 18,
    val scaleMode: ImageScaleMode = ImageScaleMode.FIT_TO_PAGE,
    val backgroundColorArgb: Int = 0xFFFFFFFF.toInt(),
    val imageQuality: ImageQualityLevel = ImageQualityLevel.HIGH,
    val compressionLevel: CompressionLevel = CompressionLevel.BALANCED,
    val addPageNumbers: Boolean = false,
    val watermarkText: String = "",
    val password: String = ""
) {
    /**
     * Converts configured width & height to PDF points (1 inch = 72 points, 1 inch = 25.4 mm).
     */
    fun resolvePageDimensionsPt(imageWidth: Int, imageHeight: Int): Pair<Int, Int> {
        if (pageSizePreset == PageSizePreset.FIT_TO_IMAGE) {
            val maxPt = 1200f
            val w = imageWidth.coerceAtLeast(100).toFloat()
            val h = imageHeight.coerceAtLeast(100).toFloat()
            val scale = if (maxOf(w, h) > maxPt) maxPt / maxOf(w, h) else 1f
            val finalW = (w * scale).toInt().coerceAtLeast(200)
            val finalH = (h * scale).toInt().coerceAtLeast(200)
            return finalW to finalH
        }
        val (rawW, rawH) = if (pageSizePreset == PageSizePreset.CUSTOM) {
            val factor = when (unit) {
                MeasurementUnit.MILLIMETER -> 72f / 25.4f
                MeasurementUnit.CENTIMETER -> 72f / 2.54f
                MeasurementUnit.INCH -> 72f
            }
            (customWidth.coerceAtLeast(0.5f) * factor) to (customHeight.coerceAtLeast(0.5f) * factor)
        } else {
            val mmToPt = 72f / 25.4f
            (pageSizePreset.widthMm * mmToPt) to (pageSizePreset.heightMm * mmToPt)
        }
        val minVal = minOf(rawW, rawH).toInt().coerceAtLeast(100)
        val maxVal = maxOf(rawW, rawH).toInt().coerceAtLeast(100)
        return if (orientation == PageOrientation.LANDSCAPE) {
            maxVal to minVal
        } else {
            minVal to maxVal
        }
    }
}

data class SelectedPdfSource(
    val name: String,
    val filePath: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val uri: Uri? = null
)
