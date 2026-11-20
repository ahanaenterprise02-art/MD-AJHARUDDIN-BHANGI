package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ImageQualityLevel
import com.example.domain.model.PageSizePreset
import com.example.ui.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System Default")
}

data class AppSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val defaultPageSize: PageSizePreset = PageSizePreset.A4,
    val defaultImageQuality: ImageQualityLevel = ImageQualityLevel.HIGH,
    val defaultSaveLocationUri: String = "",
    val defaultSaveLocationLabel: String = "App Private Library (Local)"
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ahapdf_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_PAGE_SIZE = stringPreferencesKey("default_page_size")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val SAVE_URI = stringPreferencesKey("save_uri")
        val SAVE_LABEL = stringPreferencesKey("save_label")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            language = prefs[Keys.LANGUAGE]?.let { code ->
                AppLanguage.entries.firstOrNull { it.code == code }
            } ?: AppLanguage.ENGLISH,
            themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                runCatching { ThemeMode.valueOf(name) }.getOrNull()
            } ?: ThemeMode.LIGHT,
            defaultPageSize = prefs[Keys.DEFAULT_PAGE_SIZE]?.let { name ->
                runCatching { PageSizePreset.valueOf(name) }.getOrNull()
            } ?: PageSizePreset.A4,
            defaultImageQuality = prefs[Keys.DEFAULT_QUALITY]?.let { name ->
                runCatching { ImageQualityLevel.valueOf(name) }.getOrNull()
            } ?: ImageQualityLevel.HIGH,
            defaultSaveLocationUri = prefs[Keys.SAVE_URI] ?: "",
            defaultSaveLocationLabel = prefs[Keys.SAVE_LABEL] ?: "App Private Library (Local)"
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultPageSize(size: PageSizePreset) {
        context.dataStore.edit { it[Keys.DEFAULT_PAGE_SIZE] = size.name }
    }

    suspend fun setDefaultImageQuality(quality: ImageQualityLevel) {
        context.dataStore.edit { it[Keys.DEFAULT_QUALITY] = quality.name }
    }

    suspend fun setDefaultSaveLocation(uri: String, label: String) {
        context.dataStore.edit {
            it[Keys.SAVE_URI] = uri
            it[Keys.SAVE_LABEL] = label
        }
    }
}
