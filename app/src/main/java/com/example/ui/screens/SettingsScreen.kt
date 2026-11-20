package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.ads.AdMobManager
import com.example.data.prefs.AppSettings
import com.example.data.prefs.ThemeMode
import com.example.domain.model.ImageQualityLevel
import com.example.domain.model.PageSizePreset
import com.example.ui.components.OfficialAhaLogo
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed

@Composable
fun SettingsScreen(
    strings: AppStrings,
    settings: AppSettings,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDefaultPageSizeChange: (PageSizePreset) -> Unit,
    onDefaultQualityChange: (ImageQualityLevel) -> Unit,
    onDefaultSaveLocationChange: (Uri, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPageSizeDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val label = uri.lastPathSegment ?: "Selected Folder"
            onDefaultSaveLocationChange(uri, label)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = strings.settingsTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Language Section
        item {
            SettingsCard(title = strings.languageLabel, icon = Icons.Filled.Language) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = settings.language == lang,
                            onClick = { onLanguageChange(lang) },
                            label = { Text(lang.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandNavy,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("lang_chip_${lang.code}")
                        )
                    }
                }
            }
        }

        // Theme Section
        item {
            SettingsCard(title = strings.themeLabel, icon = Icons.Filled.DarkMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandNavy,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("theme_chip_${mode.name}")
                        )
                    }
                }
            }
        }

        // PDF Defaults
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsRowItem(
                        icon = Icons.Filled.AspectRatio,
                        title = strings.defaultPageSizeLabel,
                        subtitle = settings.defaultPageSize.label,
                        onClick = { showPageSizeDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsRowItem(
                        icon = Icons.Filled.HighQuality,
                        title = strings.defaultQualityLabel,
                        subtitle = "${settings.defaultImageQuality.label} (${settings.defaultImageQuality.jpegQuality}%)",
                        onClick = { showQualityDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsRowItem(
                        icon = Icons.Filled.Folder,
                        title = strings.defaultSaveLocationLabel,
                        subtitle = settings.defaultSaveLocationLabel,
                        onClick = { folderPicker.launch(null) }
                    )
                }
            }
        }

        // Legal & About AhaPDF
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsRowItem(
                        icon = Icons.Filled.Policy,
                        title = strings.privacyPolicyLabel,
                        subtitle = "100% local on-device processing",
                        onClick = { showPrivacyDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsRowItem(
                        icon = Icons.Filled.Info,
                        title = strings.aboutLabel,
                        subtitle = "AhaPDF • Developed by AHANA ENTERPRISE",
                        onClick = { showAboutDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsRowItem(
                        icon = Icons.Filled.AdsClick,
                        title = "AdMob Test Ads",
                        subtitle = "Test mode active • Tap to preview Test Interstitial Ad",
                        onClick = {
                            val act = context.findHostActivity()
                            if (act != null) {
                                val shown = AdMobManager.showInterstitialIfReady(act, forceIgnoreCooldown = true)
                                if (!shown) {
                                    Toast.makeText(
                                        context,
                                        "Loading AdMob Test Interstitial... Please try again in a moment.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SettingsRowItem(
                        icon = Icons.Filled.VerifiedUser,
                        title = strings.appVersionLabel,
                        subtitle = "${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        onClick = { showAboutDialog = true }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    if (showPageSizeDialog) {
        val options = listOf(
            PageSizePreset.A4,
            PageSizePreset.A3,
            PageSizePreset.A5,
            PageSizePreset.LETTER,
            PageSizePreset.LEGAL,
            PageSizePreset.EXECUTIVE,
            PageSizePreset.FIT_TO_IMAGE
        )
        AlertDialog(
            onDismissRequest = { showPageSizeDialog = false },
            title = { Text(strings.defaultPageSizeLabel) },
            text = {
                Column {
                    options.forEach { preset ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDefaultPageSizeChange(preset)
                                    showPageSizeDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = settings.defaultPageSize == preset,
                                onClick = {
                                    onDefaultPageSizeChange(preset)
                                    showPageSizeDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(preset.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPageSizeDialog = false }) { Text("Close") }
            }
        )
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text(strings.defaultQualityLabel) },
            text = {
                Column {
                    ImageQualityLevel.entries.forEach { q ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDefaultQualityChange(q)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = settings.defaultImageQuality == q,
                                onClick = {
                                    onDefaultQualityChange(q)
                                    showQualityDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${q.label} (${q.jpegQuality}%)")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) { Text("Close") }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy — AhaPDF", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "AhaPDF by AHANA ENTERPRISE processes all images, document scans, signatures, watermarks, " +
                        "compressions, and PDF encryptions strictly locally on your Android device.\n\n" +
                        "• Zero cloud uploads of your PDFs or photos.\n" +
                        "• Media selection uses the zero-permission Android Photo Picker.\n" +
                        "• Camera permission is used exclusively when you open the Document Scanner or Camera Capture.\n" +
                        "• Your PDF library history is stored locally in your device's private Room database.\n" +
                        "• In-app ads are powered by Google AdMob (currently configured with official Google Test Ad Units)."
                )
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandNavy)
                ) { Text("Understood") }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = null,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        OfficialAhaLogo(size = 96.dp, modifier = Modifier.padding(6.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "AhaPDF",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandNavy
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandRed
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = BrandNavy.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Developed & Branded by AHANA ENTERPRISE",
                            style = MaterialTheme.typography.labelLarge,
                            color = BrandNavy,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Complete native Android PDF converter, CameraX document scanner, and local PDF toolkit.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandNavy)
                ) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = BrandNavy)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BrandNavy, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
