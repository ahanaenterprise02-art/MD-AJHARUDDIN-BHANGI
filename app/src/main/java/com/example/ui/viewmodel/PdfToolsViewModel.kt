package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AhaPdfApplication
import com.example.data.db.PdfRecordEntity
import com.example.domain.model.PdfToolType
import com.example.domain.model.SelectedPdfSource
import com.example.domain.pdf.PdfFileHelper
import com.example.domain.pdf.PdfToolbox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class PdfToolsUiState(
    val activeTool: PdfToolType? = null,
    val selectedPdfs: List<SelectedPdfSource> = emptyList(),
    val pageThumbnails: List<Bitmap> = emptyList(),
    val isLoadingThumbnails: Boolean = false,
    val isProcessing: Boolean = false,
    val progressMessage: String = "",
    val errorMessage: String? = null,
    val exportedImages: List<File> = emptyList(),
    val compressionSummary: String? = null,
    val splitCreatedRecords: List<PdfRecordEntity> = emptyList()
)

class PdfToolsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AhaPdfApplication
    private val repository = app.pdfRepository

    private val _uiState = MutableStateFlow(PdfToolsUiState())
    val uiState: StateFlow<PdfToolsUiState> = _uiState

    fun openTool(tool: PdfToolType?) {
        clearThumbnails()
        _uiState.value = PdfToolsUiState(activeTool = tool)
    }

    fun selectFromRecord(record: PdfRecordEntity, appendForMerge: Boolean = false) {
        viewModelScope.launch {
            val file = File(record.filePath)
            if (!file.exists()) {
                _uiState.update { it.copy(errorMessage = "File not found on device.") }
                return@launch
            }
            val source = SelectedPdfSource(
                name = record.fileName,
                filePath = file.absolutePath,
                pageCount = record.pageCount,
                sizeBytes = file.length()
            )
            addOrSetSource(source, appendForMerge)
        }
    }

    fun importFromSafUri(uri: Uri, appendForMerge: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Reading PDF...", errorMessage = null) }
            val copied = PdfFileHelper.copySafPdfToTemp(app, uri)
            if (copied == null) {
                _uiState.update { it.copy(isProcessing = false, errorMessage = "Unable to read selected PDF.") }
                return@launch
            }
            val (name, file) = copied
            val pageCount = PdfToolbox.getPageCount(file)
            val source = SelectedPdfSource(
                name = name,
                filePath = file.absolutePath,
                pageCount = pageCount,
                sizeBytes = file.length(),
                uri = uri
            )
            _uiState.update { it.copy(isProcessing = false, progressMessage = "") }
            addOrSetSource(source, appendForMerge)
        }
    }

    private suspend fun addOrSetSource(source: SelectedPdfSource, appendForMerge: Boolean) {
        val tool = _uiState.value.activeTool
        val updatedList = if (appendForMerge && tool == PdfToolType.MERGE_PDF) {
            _uiState.value.selectedPdfs + source
        } else {
            listOf(source)
        }
        _uiState.update {
            it.copy(
                selectedPdfs = updatedList,
                exportedImages = emptyList(),
                compressionSummary = null,
                splitCreatedRecords = emptyList(),
                errorMessage = null
            )
        }
        if (tool in setOf(
                PdfToolType.ROTATE_PDF,
                PdfToolType.DELETE_PAGES,
                PdfToolType.ADD_TEXT,
                PdfToolType.ADD_SIGNATURE
            )
        ) {
            loadThumbnails(File(source.filePath))
        }
    }

    fun moveMergeItem(from: Int, to: Int) {
        _uiState.update { state ->
            val list = state.selectedPdfs.toMutableList()
            if (from !in list.indices || to !in list.indices) return@update state
            val item = list.removeAt(from)
            list.add(to, item)
            state.copy(selectedPdfs = list)
        }
    }

    fun removeSelectedPdfAt(index: Int) {
        _uiState.update { state ->
            val list = state.selectedPdfs.toMutableList()
            if (index in list.indices) list.removeAt(index)
            state.copy(selectedPdfs = list)
        }
    }

    private suspend fun loadThumbnails(file: File) {
        clearThumbnails()
        _uiState.update { it.copy(isLoadingThumbnails = true) }
        val thumbs = PdfToolbox.renderAllPageThumbnails(file, thumbWidth = 340, maxPages = 50)
        _uiState.update { it.copy(pageThumbnails = thumbs, isLoadingThumbnails = false) }
    }

    private fun clearThumbnails() {
        _uiState.value.pageThumbnails.forEach { if (!it.isRecycled) it.recycle() }
    }

    fun runPdfToImages(asPng: Boolean) {
        val source = _uiState.value.selectedPdfs.firstOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Converting pages...", errorMessage = null) }
            val outDir = File(app.cacheDir, "pdf_images_${System.currentTimeMillis()}")
            val format = if (asPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val ext = if (asPng) "png" else "jpg"
            val result = PdfToolbox.pdfToImages(File(source.filePath), format, ext, outDir) { cur, tot ->
                _uiState.update { it.copy(progressMessage = "Rendering page $cur of $tot...") }
            }
            result.onSuccess { files ->
                _uiState.update { it.copy(isProcessing = false, exportedImages = files, progressMessage = "") }
            }.onFailure { err ->
                _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Conversion failed.") }
            }
        }
    }

    fun runMerge(outputName: String, onSuccess: (PdfRecordEntity) -> Unit) {
        val files = _uiState.value.selectedPdfs.map { File(it.filePath) }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Merging ${files.size} PDFs...", errorMessage = null) }
            PdfToolbox.mergePdfs(files, outputName, repository)
                .onSuccess { record ->
                    _uiState.update { it.copy(isProcessing = false, progressMessage = "") }
                    onSuccess(record)
                }.onFailure { err ->
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Merge failed.") }
                }
        }
    }

    fun runSplit(ranges: String, splitEachPage: Boolean, outputName: String, onSingleOrList: (List<PdfRecordEntity>) -> Unit) {
        val src = _uiState.value.selectedPdfs.firstOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Splitting PDF...", errorMessage = null) }
            PdfToolbox.splitPdf(File(src.filePath), ranges, splitEachPage, outputName, repository)
                .onSuccess { list ->
                    _uiState.update { it.copy(isProcessing = false, splitCreatedRecords = list, progressMessage = "") }
                    onSingleOrList(list)
                }.onFailure { err ->
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Split failed.") }
                }
        }
    }

    fun runCompress(qualityPercent: Int, maxDim: Int, outputName: String, onSuccess: (PdfRecordEntity) -> Unit) {
        val src = _uiState.value.selectedPdfs.firstOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Compressing PDF locally...", errorMessage = null) }
            PdfToolbox.compressPdf(File(src.filePath), qualityPercent, maxDim, outputName, repository)
                .onSuccess { res ->
                    val savedPct = if (res.beforeBytes > 0) {
                        (((res.beforeBytes - res.afterBytes).toDouble() / res.beforeBytes) * 100.0)
                            .toInt().coerceAtLeast(0)
                    } else 0
                    val summary = "${PdfFileHelper.formatFileSize(res.beforeBytes)} -> ${PdfFileHelper.formatFileSize(res.afterBytes)} ($savedPct% smaller)"
                    _uiState.update { it.copy(isProcessing = false, compressionSummary = summary, progressMessage = "") }
                    onSuccess(res.record)
                }.onFailure { err ->
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Compression failed.") }
                }
        }
    }

    fun runRotate(rotations: Map<Int, Int>, outputName: String, onSuccess: (PdfRecordEntity) -> Unit) {
        val src = _uiState.value.selectedPdfs.firstOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Rotating pages...", errorMessage = null) }
            PdfToolbox.rotatePdf(File(src.filePath), rotations, outputName, repository)
                .onSuccess { record ->
                    _uiState.update { it.copy(isProcessing = false, progressMessage = "") }
                    onSuccess(record)
                }.onFailure { err ->
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Rotate failed.") }
                }
        }
    }

    fun runDeletePages(pagesToDelete: Set<Int>, outputName: String, onSuccess: (PdfRecordEntity) -> Unit) {
        val src = _uiState.value.selectedPdfs.firstOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Removing pages...", errorMessage = null) }
            PdfToolbox.deletePdfPages(File(src.filePath), pagesToDelete, outputName, repository)
                .onSuccess { record ->
                    _uiState.update { it.copy(isProcessing = false, progressMessage = "") }
                    onSuccess(record)
                }.onFailure { err ->
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Delete pages failed.") }
                }
        }
    }

    fun runAddText(
        pageIndexOrNullForAll: Int?,
        text: String,
        xRatio: Float,
        yRatio: Float,
        fontSizePt: Float,
        colorArgb: Int,
        outputName: String,
        onSuccess: (PdfRecordEntity) -> Unit
    ) {
        val src = _uiState.value.selectedPdfs.firstOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Adding text to PDF...", errorMessage = null) }
            PdfToolbox.addTextToPdf(
                File(src.filePath), pageIndexOrNullForAll, text, xRatio, yRatio, fontSizePt, colorArgb, outputName, repository
            ).onSuccess { record ->
                _uiState.update { it.copy(isProcessing = false, progressMessage = "") }
                onSuccess(record)
            }.onFailure { err ->
                _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Add text failed.") }
            }
        }
    }

    fun runAddSignature(
        pageIndex: Int,
        signatureBitmap: Bitmap,
        xRatio: Float,
        yRatio: Float,
        widthRatio: Float,
        outputName: String,
        onSuccess: (PdfRecordEntity) -> Unit
    ) {
        val src = _uiState.value.selectedPdfs.firstOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Stamping signature...", errorMessage = null) }
            PdfToolbox.addSignatureToPdf(
                File(src.filePath), pageIndex, signatureBitmap, xRatio, yRatio, widthRatio, outputName, repository
            ).onSuccess { record ->
                _uiState.update { it.copy(isProcessing = false, progressMessage = "") }
                onSuccess(record)
            }.onFailure { err ->
                _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Add signature failed.") }
            }
        }
    }

    fun runAddWatermark(
        watermarkText: String,
        opacity: Float,
        angleDegrees: Float,
        fontSizePt: Float,
        colorArgb: Int,
        outputName: String,
        onSuccess: (PdfRecordEntity) -> Unit
    ) {
        val src = _uiState.value.selectedPdfs.firstOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Adding watermark...", errorMessage = null) }
            PdfToolbox.addWatermarkToPdf(
                File(src.filePath), watermarkText, opacity, angleDegrees, fontSizePt, colorArgb, outputName, repository
            ).onSuccess { record ->
                _uiState.update { it.copy(isProcessing = false, progressMessage = "") }
                onSuccess(record)
            }.onFailure { err ->
                _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Watermark failed.") }
            }
        }
    }

    fun runAddPageNumbers(
        formatStyle: Int,
        bottomCenter: Boolean,
        startNumber: Int,
        outputName: String,
        onSuccess: (PdfRecordEntity) -> Unit
    ) {
        val src = _uiState.value.selectedPdfs.firstOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Numbering pages...", errorMessage = null) }
            PdfToolbox.addPageNumbersToPdf(
                File(src.filePath), formatStyle, bottomCenter, startNumber, outputName, repository
            ).onSuccess { record ->
                _uiState.update { it.copy(isProcessing = false, progressMessage = "") }
                onSuccess(record)
            }.onFailure { err ->
                _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Page numbering failed.") }
            }
        }
    }

    fun runLockPdf(password: String, outputName: String, onSuccess: (PdfRecordEntity) -> Unit) {
        val src = _uiState.value.selectedPdfs.firstOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Encrypting PDF...", errorMessage = null) }
            PdfToolbox.lockPdf(File(src.filePath), password, outputName, repository)
                .onSuccess { record ->
                    _uiState.update { it.copy(isProcessing = false, progressMessage = "") }
                    onSuccess(record)
                }.onFailure { err ->
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Lock failed.") }
                }
        }
    }

    fun runUnlockPdf(password: String, outputName: String, onSuccess: (PdfRecordEntity) -> Unit) {
        val src = _uiState.value.selectedPdfs.firstOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Unlocking PDF...", errorMessage = null) }
            PdfToolbox.unlockPdf(File(src.filePath), password, outputName, repository)
                .onSuccess { record ->
                    _uiState.update { it.copy(isProcessing = false, progressMessage = "") }
                    onSuccess(record)
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = if (err.message?.contains("password", ignoreCase = true) == true) {
                                "Incorrect password. Please try again."
                            } else err.message ?: "Unlock failed."
                        )
                    }
                }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        clearThumbnails()
    }
}
