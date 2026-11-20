package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AhaPdfDatabase
import com.example.data.db.PdfRepository
import com.example.domain.image.ImageProcessor
import com.example.domain.model.EditableImagePage
import com.example.domain.model.ImageFilterType
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizePreset
import com.example.domain.model.PdfGenerationConfig
import com.example.domain.pdf.PdfGenerator
import com.example.domain.pdf.PdfToolbox
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name is AhaPDF`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("AhaPDF", context.getString(R.string.app_name))
    }

    @Test
    fun `page size conversions work for presets and custom units`() {
        val a4Portrait = PdfGenerationConfig(
            pageSizePreset = PageSizePreset.A4,
            orientation = PageOrientation.PORTRAIT
        ).resolvePageDimensionsPt(800, 600)
        assertTrue(a4Portrait.first in 590..600)
        assertTrue(a4Portrait.second in 838..846)

        val a4Landscape = PdfGenerationConfig(
            pageSizePreset = PageSizePreset.A4,
            orientation = PageOrientation.LANDSCAPE
        ).resolvePageDimensionsPt(800, 600)
        assertTrue(a4Landscape.first > a4Landscape.second)

        val customInches = PdfGenerationConfig(
            pageSizePreset = PageSizePreset.CUSTOM,
            customWidth = 8.5f,
            customHeight = 11f,
            unit = MeasurementUnit.INCH,
            orientation = PageOrientation.PORTRAIT
        ).resolvePageDimensionsPt(100, 100)
        assertEquals(612, customInches.first)
        assertEquals(792, customInches.second)
    }

    @Test
    fun `parsePageRanges extracts valid zero-based indices`() {
        val indices = PdfToolbox.parsePageRanges("1-3, 5, 99", totalPages = 6)
        assertEquals(listOf(0, 1, 2, 4), indices)
    }

    @Test
    fun `image filters process bitmap safely without crashing`() {
        val bmp = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.LTGRAY)
        for (filter in ImageFilterType.entries) {
            val out = ImageProcessor.applyFilterAndAdjustments(bmp, filter, 10f, 1.1f, 1.1f)
            assertEquals(80, out.width)
            assertEquals(80, out.height)
            out.recycle()
        }
        bmp.recycle()
    }

    @Test
    fun `pdf generation room persistence lock unlock and merge work end to end`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)
        val db = AhaPdfDatabase.getInstance(context)
        val repo = PdfRepository(context, db.pdfRecordDao())

        val imgFile = File(context.cacheDir, "test_page.jpg")
        val bmp = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.WHITE)
        FileOutputStream(imgFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()

        val page = EditableImagePage(originalPath = imgFile.absolutePath, filter = ImageFilterType.DOCUMENT)
        val config = PdfGenerationConfig(
            fileName = "UnitTest_Doc",
            addPageNumbers = true,
            watermarkText = "AHANA"
        )
        val result = PdfGenerator.generatePdfFromImages(listOf(page), config, repo) { _, _, _ -> }
        assertTrue("PDF generation failed: ${result.exceptionOrNull()?.stackTraceToString()}", result.isSuccess)
        val record = result.getOrThrow()
        assertEquals(1, record.pageCount)
        assertFalse(record.isLocked)
        assertTrue(File(record.filePath).exists())

        // Verify Room DB lookup
        val fetched = repo.getById(record.id)
        assertEquals(record.fileName, fetched?.fileName)

        // Verify Merge
        val mergedRes = PdfToolbox.mergePdfs(listOf(File(record.filePath), File(record.filePath)), "Merged_Test", repo)
        assertTrue("Merge failed: ${mergedRes.exceptionOrNull()?.message}", mergedRes.isSuccess)
        val merged = mergedRes.getOrThrow()
        assertEquals(2, merged.pageCount)

        // Verify Lock + Unlock
        val lockedRes = PdfToolbox.lockPdf(File(merged.filePath), "secret123", "Locked_Test", repo)
        assertTrue("Lock failed: ${lockedRes.exceptionOrNull()?.message}", lockedRes.isSuccess)
        val locked = lockedRes.getOrThrow()
        assertTrue(locked.isLocked)
        assertTrue(PdfToolbox.isPdfEncrypted(File(locked.filePath)))

        val unlockedRes = PdfToolbox.unlockPdf(File(locked.filePath), "secret123", "Unlocked_Test", repo)
        assertTrue("Unlock failed: ${unlockedRes.exceptionOrNull()?.message}", unlockedRes.isSuccess)
        val unlocked = unlockedRes.getOrThrow()
        assertFalse(unlocked.isLocked)
        assertEquals(2, unlocked.pageCount)

        // Cleanup
        repo.deletePdf(record)
        repo.deletePdf(merged)
        repo.deletePdf(locked)
        repo.deletePdf(unlocked)
    }

    @Test
    fun `autoDetectCropAndQuad detects centered document bounds and preserves uniform frames`() {
        // Dark table background (400x400) with a bright white document (100..300, 60..340)
        val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(25, 28, 35))
        val paint = android.graphics.Paint().apply { color = Color.WHITE }
        canvas.drawRect(100f, 60f, 300f, 340f, paint)

        val (rect, quad) = ImageProcessor.autoDetectCropAndQuad(bmp)
        assertFalse("Expected auto-crop to tighten bounds", rect.isFull)
        assertTrue("Left should be near 0.25 (was ${rect.left})", rect.left in 0.18f..0.28f)
        assertTrue("Top should be near 0.15 (was ${rect.top})", rect.top in 0.08f..0.18f)
        assertTrue("Right should be near 0.75 (was ${rect.right})", rect.right in 0.72f..0.82f)
        assertTrue("Bottom should be near 0.85 (was ${rect.bottom})", rect.bottom in 0.82f..0.92f)
        assertEquals(rect.left, quad.topLeftX, 0.01f)
        bmp.recycle()

        // Uniform white page should keep full frame
        val uniform = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        Canvas(uniform).drawColor(Color.WHITE)
        val (uniformRect, _) = ImageProcessor.autoDetectCropAndQuad(uniform)
        assertTrue("Uniform image should remain full frame", uniformRect.isFull)
        uniform.recycle()
    }

    @Test
    fun `admob manager uses official Google test IDs and initializes safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(com.example.ads.AdMobManager.TEST_APP_ID.startsWith("ca-app-pub-3940256099942544~"))
        assertTrue(com.example.ads.AdMobManager.TEST_BANNER_AD_UNIT_ID.startsWith("ca-app-pub-3940256099942544/"))
        assertTrue(com.example.ads.AdMobManager.TEST_INTERSTITIAL_AD_UNIT_ID.startsWith("ca-app-pub-3940256099942544/"))
        com.example.ads.AdMobManager.initialize(context)
    }
}
