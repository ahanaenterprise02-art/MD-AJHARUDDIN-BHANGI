package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.PdfRecordEntity
import com.example.domain.model.PdfToolType
import com.example.domain.pdf.PdfFileHelper
import com.example.ui.components.SelectPdfSourceBottomSheet
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.BrandDarkRed
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandOrange
import com.example.ui.theme.BrandRed
import com.example.ui.viewmodel.PdfToolsUiState
import com.example.ui.viewmodel.PdfToolsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    strings: AppStrings,
    language: AppLanguage,
    allSavedPdfs: List<PdfRecordEntity>,
    uiState: PdfToolsUiState,
    viewModel: PdfToolsViewModel,
    onPdfCreated: (PdfRecordEntity) -> Unit,
    onNavigateToMyPdfs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSourcePicker by remember { mutableStateOf(false) }
    var appendMergeNext by remember { mutableStateOf(false) }

    val safPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFromSafUri(uri, appendForMerge = appendMergeNext)
        }
    }

    uiState.errorMessage?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.dismissError()
        }
    }

    uiState.compressionSummary?.let { summary ->
        LaunchedEffect(summary) {
            Toast.makeText(context, "Compressed: $summary", Toast.LENGTH_LONG).show()
        }
    }

    val activeTool = uiState.activeTool
    if (activeTool != null) {
        BackHandler { viewModel.openTool(null) }
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = if (language == AppLanguage.BENGALI) activeTool.titleBn else activeTool.titleEn,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.openTool(null) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tools")
                    }
                },
                actions = {
                    if (uiState.selectedPdfs.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                appendMergeNext = false
                                showSourcePicker = true
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = "Change PDF", tint = BrandNavy)
                        }
                    }
                }
            )

            if (uiState.isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = BrandRed)
                if (uiState.progressMessage.isNotBlank()) {
                    Text(
                        text = uiState.progressMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = BrandNavy,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            if (uiState.selectedPdfs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(BrandRed.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(toolIcon(activeTool), contentDescription = null, tint = BrandRed, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = if (language == AppLanguage.BENGALI) activeTool.titleBn else activeTool.titleEn,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (language == AppLanguage.BENGALI) activeTool.subtitleBn else activeTool.subtitleEn,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    appendMergeNext = false
                                    showSourcePicker = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("select_pdf_for_tool_btn")
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Select PDF File")
                            }
                        }
                    }
                }
            } else {
                val selected = uiState.selectedPdfs.first()
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${selected.name}.pdf",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${selected.pageCount} pages • ${PdfFileHelper.formatFileSize(selected.sizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                appendMergeNext = false
                                showSourcePicker = true
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Change")
                        }
                    }
                }

                ToolWorkspaceContent(
                    tool = activeTool,
                    uiState = uiState,
                    viewModel = viewModel,
                    onPickMoreForMerge = {
                        appendMergeNext = true
                        showSourcePicker = true
                    },
                    onPdfResult = onPdfCreated,
                    onSplitMultipleResult = {
                        Toast.makeText(context, "Created split PDFs in My PDFs!", Toast.LENGTH_LONG).show()
                        onNavigateToMyPdfs()
                    }
                )
            }
        }
    } else {
        // Directory of all 13 Tools
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = strings.pdfToolsTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "All operations run 100% locally on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val cols = if (maxWidth > 620.dp) 3 else 2
                    val rows = PdfToolType.entries.chunked(cols)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rows.forEach { rowTools ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowTools.forEach { tool ->
                                    PdfToolDirectoryCard(
                                        tool = tool,
                                        language = language,
                                        onClick = { viewModel.openTool(tool) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(cols - rowTools.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showSourcePicker) {
        SelectPdfSourceBottomSheet(
            records = allSavedPdfs,
            onPickFromDevice = { safPdfLauncher.launch(arrayOf("application/pdf")) },
            onSelectRecord = { rec -> viewModel.selectFromRecord(rec, appendForMerge = appendMergeNext) },
            onDismiss = { showSourcePicker = false }
        )
    }
}

@Composable
private fun PdfToolDirectoryCard(
    tool: PdfToolType,
    language: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = toolAccentColor(tool)
    Card(
        modifier = modifier
            .height(124.dp)
            .clickable(onClick = onClick)
            .testTag("tool_card_${tool.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(toolIcon(tool), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(
                    text = if (language == AppLanguage.BENGALI) tool.titleBn else tool.titleEn,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (language == AppLanguage.BENGALI) tool.subtitleBn else tool.subtitleEn,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun toolIcon(tool: PdfToolType): ImageVector = when (tool) {
    PdfToolType.PDF_TO_JPG -> Icons.Filled.Image
    PdfToolType.PDF_TO_PNG -> Icons.Filled.PhotoLibrary
    PdfToolType.MERGE_PDF -> Icons.Filled.MergeType
    PdfToolType.SPLIT_PDF -> Icons.AutoMirrored.Filled.CallSplit
    PdfToolType.COMPRESS_PDF -> Icons.Filled.Compress
    PdfToolType.ROTATE_PDF -> Icons.AutoMirrored.Filled.RotateRight
    PdfToolType.DELETE_PAGES -> Icons.Filled.DeleteSweep
    PdfToolType.ADD_TEXT -> Icons.Filled.TextFields
    PdfToolType.ADD_SIGNATURE -> Icons.Filled.Draw
    PdfToolType.ADD_WATERMARK -> Icons.Filled.WaterDrop
    PdfToolType.ADD_PAGE_NUMBERS -> Icons.Filled.FormatListNumbered
    PdfToolType.LOCK_PDF -> Icons.Filled.Lock
    PdfToolType.UNLOCK_PDF -> Icons.Filled.LockOpen
}

private fun toolAccentColor(tool: PdfToolType): Color = when (tool) {
    PdfToolType.PDF_TO_JPG, PdfToolType.COMPRESS_PDF, PdfToolType.DELETE_PAGES, PdfToolType.ADD_WATERMARK -> BrandRed
    PdfToolType.LOCK_PDF -> BrandDarkRed
    PdfToolType.PDF_TO_PNG, PdfToolType.ADD_SIGNATURE, PdfToolType.ADD_PAGE_NUMBERS -> BrandOrange
    else -> BrandNavy
}
