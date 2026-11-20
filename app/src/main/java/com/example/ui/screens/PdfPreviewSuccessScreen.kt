package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.PdfRecordEntity
import com.example.domain.pdf.PdfFileHelper
import com.example.domain.pdf.PdfToolbox
import com.example.ui.components.AdMobBannerBar
import com.example.ui.components.OfficialAhaLogo
import com.example.ui.components.RenamePdfDialog
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandWhite
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenContainer
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewSuccessScreen(
    recordId: Long,
    showSuccessBanner: Boolean,
    strings: AppStrings,
    loadRecord: suspend (Long) -> PdfRecordEntity?,
    onRenameRecord: (PdfRecordEntity, String, (PdfRecordEntity?) -> Unit) -> Unit,
    onBack: () -> Unit,
    onUnlockPdfRequested: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var record by remember { mutableStateOf<PdfRecordEntity?>(null) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(true) }
    var showRenameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(recordId) {
        record = loadRecord(recordId)
    }

    LaunchedEffect(record?.filePath, currentPageIndex) {
        val rec = record ?: return@LaunchedEffect
        isRendering = true
        val file = File(rec.filePath)
        pageBitmap = if (!rec.isLocked && file.exists()) {
            PdfToolbox.renderPdfPageBitmap(file, currentPageIndex, targetWidth = 1080)
        } else null
        isRendering = false
    }

    val saveSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val rec = record ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                val ok = PdfFileHelper.savePdfToSafUri(context, File(rec.filePath), uri)
                Toast.makeText(
                    context,
                    if (ok) "PDF saved to device successfully!" else "Failed to save PDF.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = record?.fileName?.let { "$it.pdf" } ?: "PDF Preview",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            AdMobBannerBar(modifier = Modifier.navigationBarsPadding())
        }
    ) { padding ->
        val rec = record
        if (rec == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandRed)
            }
            return@Scaffold
        }

        val pdfFile = File(rec.filePath)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (showSuccessBanner) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SuccessGreenContainer),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OfficialAhaLogo(size = 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = strings.pdfReadyTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = SuccessGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Saved permanently to My PDFs • AHANA ENTERPRISE",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF14532D)
                                )
                            }
                        }
                    }
                }
            }

            // PDF Metadata Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${rec.fileName}.pdf", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${strings.pagesLabel(rec.pageCount)} • ${PdfFileHelper.formatFileSize(rec.fileSizeBytes)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = BrandRed
                            )
                            Text(
                                text = PdfFileHelper.formatDate(rec.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (rec.isLocked) {
                            Surface(color = BrandNavy, shape = RoundedCornerShape(10.dp)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = BrandWhite, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Locked", color = BrandWhite, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            // Live PDF Page Renderer
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1A1F2C)),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                rec.isLocked -> {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                        Icon(Icons.Filled.Lock, contentDescription = null, tint = BrandWhite, modifier = Modifier.size(48.dp))
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            text = "Password-Protected PDF",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = BrandWhite
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Open with a PDF viewer or use Unlock PDF tool to view inside AhaPDF.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BrandWhite.copy(alpha = 0.8f)
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Button(
                                            onClick = onUnlockPdfRequested,
                                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                                        ) {
                                            Text("Unlock PDF")
                                        }
                                    }
                                }
                                isRendering -> {
                                    CircularProgressIndicator(color = BrandRed)
                                }
                                pageBitmap != null -> {
                                    Image(
                                        bitmap = pageBitmap!!.asImageBitmap(),
                                        contentDescription = "PDF Page ${currentPageIndex + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(8.dp)
                                    )
                                }
                                else -> {
                                    Text("Unable to render PDF preview", color = BrandWhite)
                                }
                            }
                        }

                        if (!rec.isLocked && rec.pageCount > 1) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                IconButton(
                                    onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                                    enabled = currentPageIndex > 0,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                                }
                                Text(
                                    text = "Page ${currentPageIndex + 1} of ${rec.pageCount}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = { if (currentPageIndex < rec.pageCount - 1) currentPageIndex++ },
                                    enabled = currentPageIndex < rec.pageCount - 1,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                                }
                            }
                        }
                    }
                }
            }

            // Action Buttons: Open | Share | Download/Save | Print | Rename
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val opened = PdfFileHelper.openPdfWithSystemViewer(context, pdfFile)
                                if (!opened) {
                                    Toast.makeText(context, "No external PDF viewer installed.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(50.dp).testTag("preview_open_button")
                        ) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.open)
                        }
                        Button(
                            onClick = { PdfFileHelper.sharePdf(context, pdfFile) },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(50.dp).testTag("preview_share_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.share)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { saveSafLauncher.launch("${rec.fileName}.pdf") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("preview_save_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.downloadSave, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { PdfFileHelper.printPdf(context, pdfFile, rec.fileName) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("preview_print_button")
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.print, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showRenameDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("preview_rename_button")
                        ) {
                            Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.rename, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog && record != null) {
        RenamePdfDialog(
            currentName = record!!.fileName,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                onRenameRecord(record!!, newName) { updated ->
                    if (updated != null) record = updated
                }
                showRenameDialog = false
            }
        )
    }
}
