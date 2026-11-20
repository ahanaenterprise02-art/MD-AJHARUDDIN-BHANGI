package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AhaPdfApplication
import com.example.data.db.PdfRecordEntity
import com.example.domain.image.ImageProcessor
import com.example.domain.model.CompressionLevel
import com.example.domain.model.EditableImagePage
import com.example.domain.model.ImageFilterType
import com.example.domain.model.ImageQualityLevel
import com.example.domain.model.ImageScaleMode
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.NormalizedCropRect
import com.example.domain.model.NormalizedQuad
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizePreset
import com.example.domain.model.PdfGenerationConfig
import com.example.domain.pdf.PdfGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImageToPdfUiState(
    val pages: List<EditableImagePage> = emptyList(),
    val selectedPageId: String? = null,
    val config: PdfGenerationConfig = PdfGenerationConfig(),
    val isImporting: Boolean = false,
    val isGenerating: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val progressMessage: String = "",
    val errorMessage: String? = null
) {
    val selectedPage: EditableImagePage?
        get() = pages.firstOrNull { it.id == selectedPageId } ?: pages.firstOrNull()
}

class ImageToPdfViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AhaPdfApplication
    private val repository = app.pdfRepository
    private val settingsRepo = app.settingsRepository

    private val _uiState = MutableStateFlow(
        ImageToPdfUiState(
            config = PdfGenerationConfig(fileName = defaultFileName())
        )
    )
    val uiState: StateFlow<ImageToPdfUiState> = _uiState

    init {
        viewModelScope.launch {
            val s = settingsRepo.settingsFlow.first()
            _uiState.update { state ->
                state.copy(
                    config = state.config.copy(
                        pageSizePreset = s.defaultPageSize,
                        imageQuality = s.defaultImageQuality
                    )
                )
            }
        }
    }

    private fun defaultFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "AhaPDF_$stamp"
    }

    fun addImagesFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, errorMessage = null) }
            val newPages = mutableListOf<EditableImagePage>()
            for (uri in uris) {
                val localPath = ImageProcessor.copyUriToOriginals(app, uri, repository.originalsDir())
                if (localPath != null) {
                    newPages.add(EditableImagePage(originalPath = localPath))
                }
            }
            _uiState.update { state ->
                val combined = state.pages + newPages
                state.copy(
                    pages = combined,
                    selectedPageId = state.selectedPageId ?: combined.firstOrNull()?.id,
                    isImporting = false
                )
            }
        }
    }

    fun addScannedPages(scannedPages: List<EditableImagePage>, replaceExisting: Boolean = false) {
        if (scannedPages.isEmpty()) return
        _uiState.update { state ->
            val combined = if (replaceExisting) scannedPages else state.pages + scannedPages
            state.copy(
                pages = combined,
                selectedPageId = combined.firstOrNull()?.id,
                config = state.config.copy(
                    fileName = if (state.pages.isEmpty() || replaceExisting) {
                        "AhaScan_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}"
                    } else state.config.fileName
                )
            )
        }
    }

    fun addCapturedFile(filePath: String) {
        val page = EditableImagePage(originalPath = filePath)
        _uiState.update { state ->
            val combined = state.pages + page
            state.copy(
                pages = combined,
                selectedPageId = state.selectedPageId ?: page.id
            )
        }
    }

    fun selectPage(pageId: String) {
        _uiState.update { it.copy(selectedPageId = pageId) }
    }

    fun removePage(pageId: String) {
        _uiState.update { state ->
            val filtered = state.pages.filterNot { it.id == pageId }
            val nextSelected = if (state.selectedPageId == pageId) {
                filtered.firstOrNull()?.id
            } else state.selectedPageId
            state.copy(pages = filtered, selectedPageId = nextSelected)
        }
    }

    fun clearAllPages() {
        _uiState.update {
            it.copy(
                pages = emptyList(),
                selectedPageId = null,
                config = it.config.copy(fileName = defaultFileName())
            )
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            if (fromIndex !in state.pages.indices || toIndex !in state.pages.indices || fromIndex == toIndex) {
                return@update state
            }
            val mutable = state.pages.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            state.copy(pages = mutable)
        }
    }

    fun rotatePage(pageId: String, deltaDegrees: Int = 90) {
        _uiState.update { state ->
            state.copy(
                pages = state.pages.map { p ->
                    if (p.id == pageId) p.copy(rotationDegrees = (p.rotationDegrees + deltaDegrees + 360) % 360)
                    else p
                }
            )
        }
    }

    fun updateCrop(pageId: String, cropRect: NormalizedCropRect, perspectiveQuad: NormalizedQuad? = null) {
        _uiState.update { state ->
            state.copy(
                pages = state.pages.map { p ->
                    if (p.id == pageId) p.copy(
                        cropRect = cropRect,
                        perspectiveQuad = perspectiveQuad
                    ) else p
                }
            )
        }
    }

    fun autoCropPage(pageId: String, onDone: (Boolean) -> Unit = {}) {
        val target = _uiState.value.pages.firstOrNull { it.id == pageId } ?: return
        viewModelScope.launch {
            val raw = ImageProcessor.renderProcessedBitmap(
                page = target.copy(
                    cropRect = NormalizedCropRect(),
                    perspectiveQuad = null,
                    filter = ImageFilterType.ORIGINAL,
                    brightness = 0f,
                    contrast = 1f,
                    saturation = 1f
                ),
                maxDimension = 640,
                applyFilters = false
            )
            if (raw != null) {
                val (rect, _) = ImageProcessor.autoDetectCropAndQuad(raw)
                raw.recycle()
                updateCrop(pageId, rect, null)
                onDone(!rect.isFull)
            } else {
                onDone(false)
            }
        }
    }

    fun autoCropAllPages(onDone: (Int) -> Unit = {}) {
        val currentPages = _uiState.value.pages
        if (currentPages.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            var croppedCount = 0
            val updatedPages = currentPages.map { p ->
                val raw = ImageProcessor.renderProcessedBitmap(
                    page = p.copy(
                        cropRect = NormalizedCropRect(),
                        perspectiveQuad = null,
                        filter = ImageFilterType.ORIGINAL,
                        brightness = 0f,
                        contrast = 1f,
                        saturation = 1f
                    ),
                    maxDimension = 640,
                    applyFilters = false
                )
                if (raw != null) {
                    val (rect, _) = ImageProcessor.autoDetectCropAndQuad(raw)
                    raw.recycle()
                    if (!rect.isFull) croppedCount++
                    p.copy(cropRect = rect, perspectiveQuad = null)
                } else p
            }
            _uiState.update { it.copy(pages = updatedPages, isImporting = false) }
            onDone(croppedCount)
        }
    }

    fun applyFilter(
        pageId: String,
        filter: ImageFilterType,
        brightness: Float,
        contrast: Float,
        saturation: Float,
        applyToAll: Boolean
    ) {
        _uiState.update { state ->
            state.copy(
                pages = state.pages.map { p ->
                    if (applyToAll || p.id == pageId) {
                        p.copy(
                            filter = filter,
                            brightness = brightness,
                            contrast = contrast,
                            saturation = saturation
                        )
                    } else p
                }
            )
        }
    }

    fun updateConfig(transform: (PdfGenerationConfig) -> PdfGenerationConfig) {
        _uiState.update { it.copy(config = transform(it.config)) }
    }

    fun setPageSizePreset(preset: PageSizePreset) {
        updateConfig { it.copy(pageSizePreset = preset) }
    }

    fun setMeasurementUnit(unit: MeasurementUnit) {
        updateConfig { it.copy(unit = unit) }
    }

    fun setOrientation(orientation: PageOrientation) {
        updateConfig { it.copy(orientation = orientation) }
    }

    fun setScaleMode(mode: ImageScaleMode) {
        updateConfig { it.copy(scaleMode = mode) }
    }

    fun setQuality(quality: ImageQualityLevel) {
        updateConfig { it.copy(imageQuality = quality) }
    }

    fun setCompression(level: CompressionLevel) {
        updateConfig { it.copy(compressionLevel = level) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun generatePdf(onSuccess: (PdfRecordEntity) -> Unit) {
        val snapshot = _uiState.value
        if (snapshot.pages.isEmpty() || snapshot.isGenerating) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    progressCurrent = 0,
                    progressTotal = snapshot.pages.size,
                    progressMessage = "Preparing PDF...",
                    errorMessage = null
                )
            }
            val result = PdfGenerator.generatePdfFromImages(
                pages = snapshot.pages,
                config = snapshot.config,
                repository = repository
            ) { current, total, status ->
                _uiState.update {
                    it.copy(progressCurrent = current, progressTotal = total, progressMessage = status)
                }
            }
            result.onSuccess { record ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        config = it.config.copy(fileName = defaultFileName(), password = "")
                    )
                }
                onSuccess(record)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = err.message ?: "Failed to generate PDF."
                    )
                }
            }
        }
    }
}
