package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AhaPdfApplication
import com.example.data.db.PdfRecordEntity
import com.example.data.prefs.AppSettings
import com.example.data.prefs.ThemeMode
import com.example.domain.model.ImageQualityLevel
import com.example.domain.model.PageSizePreset
import com.example.ui.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PdfSortOrder(val label: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    NAME_ASC("Name (A-Z)"),
    SIZE_DESC("Largest Size")
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AhaPdfApplication
    private val pdfRepository = app.pdfRepository
    private val settingsRepository = app.settingsRepository

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortOrder = MutableStateFlow(PdfSortOrder.NEWEST)
    val sortOrder: StateFlow<PdfSortOrder> = _sortOrder

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView

    val recentPdfs: StateFlow<List<PdfRecordEntity>> = pdfRepository.recentPdfs(6)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredPdfs: StateFlow<List<PdfRecordEntity>> = combine(
        pdfRepository.allPdfs,
        _searchQuery,
        _sortOrder
    ) { all, query, sort ->
        val matching = if (query.isBlank()) {
            all
        } else {
            val q = query.trim().lowercase()
            all.filter { it.fileName.lowercase().contains(q) || it.sourceTag.lowercase().contains(q) }
        }
        when (sort) {
            PdfSortOrder.NEWEST -> matching.sortedByDescending { it.createdAt }
            PdfSortOrder.OLDEST -> matching.sortedBy { it.createdAt }
            PdfSortOrder.NAME_ASC -> matching.sortedBy { it.fileName.lowercase() }
            PdfSortOrder.SIZE_DESC -> matching.sortedByDescending { it.fileSizeBytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortOrder(order: PdfSortOrder) {
        _sortOrder.value = order
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    suspend fun getPdfById(id: Long): PdfRecordEntity? = pdfRepository.getById(id)

    fun renamePdf(record: PdfRecordEntity, newName: String, onDone: (PdfRecordEntity?) -> Unit = {}) {
        viewModelScope.launch {
            val updated = pdfRepository.renamePdf(record, newName)
            onDone(updated)
        }
    }

    fun duplicatePdf(record: PdfRecordEntity, onDone: (PdfRecordEntity?) -> Unit = {}) {
        viewModelScope.launch {
            val copy = pdfRepository.duplicatePdf(record)
            onDone(copy)
        }
    }

    fun deletePdf(record: PdfRecordEntity) {
        viewModelScope.launch {
            pdfRepository.deletePdf(record)
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDefaultPageSize(size: PageSizePreset) {
        viewModelScope.launch { settingsRepository.setDefaultPageSize(size) }
    }

    fun setDefaultImageQuality(quality: ImageQualityLevel) {
        viewModelScope.launch { settingsRepository.setDefaultImageQuality(quality) }
    }

    fun setDefaultSaveLocation(uri: Uri, label: String) {
        viewModelScope.launch { settingsRepository.setDefaultSaveLocation(uri.toString(), label) }
    }
}
