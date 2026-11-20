package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.components.OfficialAhaLogo
import com.example.ui.components.PdfRecordListItem
import com.example.ui.components.RenamePdfDialog
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.BrandDarkRed
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandOrange
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandWhite
import java.io.File

private data class HomeQuickAction(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val accent: Color,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    strings: AppStrings,
    recentPdfs: List<PdfRecordEntity>,
    onOpenImageToPdf: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenTool: (PdfToolType) -> Unit,
    onOpenAllTools: () -> Unit,
    onOpenMyPdfs: () -> Unit,
    onOpenPreview: (PdfRecordEntity) -> Unit,
    onRenamePdf: (PdfRecordEntity, String) -> Unit,
    onDuplicatePdf: (PdfRecordEntity) -> Unit,
    onDeletePdf: (PdfRecordEntity) -> Unit,
    onSavePdfToDevice: (PdfRecordEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var renamingRecord by remember { mutableStateOf<PdfRecordEntity?>(null) }

    val quickActions = remember(strings) {
        listOf(
            HomeQuickAction("image_to_pdf", strings.imageToPdf, Icons.Filled.Image, BrandRed, onOpenImageToPdf),
            HomeQuickAction("scan_doc", strings.scanDocument, Icons.Filled.DocumentScanner, BrandNavy, onOpenScanner),
            HomeQuickAction("pdf_to_img", strings.pdfToImage, Icons.Filled.PhotoLibrary, BrandOrange) {
                onOpenTool(PdfToolType.PDF_TO_JPG)
            },
            HomeQuickAction("merge_pdf", strings.mergePdf, Icons.Filled.MergeType, BrandNavy) {
                onOpenTool(PdfToolType.MERGE_PDF)
            },
            HomeQuickAction("compress_pdf", strings.compressPdf, Icons.Filled.Compress, BrandRed) {
                onOpenTool(PdfToolType.COMPRESS_PDF)
            },
            HomeQuickAction("edit_pdf", strings.editPdf, Icons.Filled.EditNote, BrandOrange) {
                onOpenAllTools()
            },
            HomeQuickAction("split_pdf", strings.splitPdf, Icons.AutoMirrored.Filled.CallSplit, BrandNavy) {
                onOpenTool(PdfToolType.SPLIT_PDF)
            },
            HomeQuickAction("lock_pdf", strings.lockPdf, Icons.Filled.Lock, BrandDarkRed) {
                onOpenTool(PdfToolType.LOCK_PDF)
            },
            HomeQuickAction("unlock_pdf", strings.unlockPdf, Icons.Filled.LockOpen, BrandNavy) {
                onOpenTool(PdfToolType.UNLOCK_PDF)
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Branded Header + Welcome Hero
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(BrandRed, BrandDarkRed)
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = BrandWhite,
                                shadowElevation = 4.dp
                            ) {
                                OfficialAhaLogo(
                                    size = 58.dp,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = strings.appName,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = BrandWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = BrandOrange,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "PRO",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BrandWhite,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = strings.welcomeTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = BrandWhite.copy(alpha = 0.95f)
                                )
                                Text(
                                    text = strings.welcomeSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BrandWhite.copy(alpha = 0.85f),
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    // Primary CTA row with Navy Blue buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onOpenImageToPdf,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("home_cta_image_to_pdf")
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.imageToPdf, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = onOpenScanner,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("home_cta_scan_doc")
                        ) {
                            Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.scanDocument, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // 9 Quick Actions Grid
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.quickActions,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onOpenAllTools) {
                        Text(strings.viewAll, color = BrandNavy, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columns = if (maxWidth > 620.dp) 4 else 3
                    val rows = quickActions.chunked(columns)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { action ->
                                    QuickActionTile(
                                        action = action,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(columns - rowItems.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent PDFs Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.recentPdfs,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (recentPdfs.isNotEmpty()) {
                    TextButton(onClick = onOpenMyPdfs) {
                        Text(strings.viewAll, color = BrandNavy, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (recentPdfs.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                tint = BrandRed,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(strings.noPdfsYet, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            strings.noPdfsSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = onOpenImageToPdf,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(strings.createFirstPdf)
                        }
                    }
                }
            }
        } else {
            items(recentPdfs, key = { it.id }) { record ->
                PdfRecordListItem(
                    record = record,
                    strings = strings,
                    onOpen = { onOpenPreview(record) },
                    onShare = { PdfFileHelper.sharePdf(context, File(record.filePath)) },
                    onRename = { renamingRecord = record },
                    onDuplicate = { onDuplicatePdf(record) },
                    onPrint = { PdfFileHelper.printPdf(context, File(record.filePath), record.fileName) },
                    onSave = { onSavePdfToDevice(record) },
                    onDelete = { onDeletePdf(record) }
                )
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    renamingRecord?.let { target ->
        RenamePdfDialog(
            currentName = target.fileName,
            onDismiss = { renamingRecord = null },
            onConfirm = { newName ->
                onRenamePdf(target, newName)
                renamingRecord = null
            }
        )
    }
}

@Composable
private fun QuickActionTile(
    action: HomeQuickAction,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(102.dp)
            .clickable(onClick = action.onClick)
            .testTag("quick_action_${action.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(action.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.title,
                    tint = action.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = action.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
