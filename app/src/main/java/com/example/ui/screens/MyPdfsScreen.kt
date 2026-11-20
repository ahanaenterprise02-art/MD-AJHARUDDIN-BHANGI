package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.PdfRecordEntity
import com.example.domain.pdf.PdfFileHelper
import com.example.ui.components.PdfRecordGridItem
import com.example.ui.components.PdfRecordListItem
import com.example.ui.components.RenamePdfDialog
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.viewmodel.PdfSortOrder
import java.io.File

@Composable
fun MyPdfsScreen(
    strings: AppStrings,
    pdfs: List<PdfRecordEntity>,
    searchQuery: String,
    sortOrder: PdfSortOrder,
    isGridView: Boolean,
    onSearchChange: (String) -> Unit,
    onSortChange: (PdfSortOrder) -> Unit,
    onToggleGridView: () -> Unit,
    onOpenPreview: (PdfRecordEntity) -> Unit,
    onRenamePdf: (PdfRecordEntity, String) -> Unit,
    onDuplicatePdf: (PdfRecordEntity) -> Unit,
    onDeletePdf: (PdfRecordEntity) -> Unit,
    onSavePdfToDevice: (PdfRecordEntity) -> Unit,
    onCreatePdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sortMenuOpen by remember { mutableStateOf(false) }
    var renamingRecord by remember { mutableStateOf<PdfRecordEntity?>(null) }
    var deletingRecord by remember { mutableStateOf<PdfRecordEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = strings.navMyPdfs,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${pdfs.size} ${if (pdfs.size == 1) "Document" else "Documents"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(
                        onClick = { sortMenuOpen = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("mypdfs_sort_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort PDFs")
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        PdfSortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        order.label,
                                        fontWeight = if (order == sortOrder) FontWeight.Bold else FontWeight.Normal,
                                        color = if (order == sortOrder) BrandNavy else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onSortChange(order)
                                    sortMenuOpen = false
                                }
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onToggleGridView,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mypdfs_view_toggle")
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                        contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View"
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(strings.searchPdfsPlaceholder) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mypdfs_search_input")
        )
        Spacer(Modifier.height(12.dp))

        if (pdfs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PictureAsPdf,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isBlank()) strings.noPdfsYet else "No matching PDFs",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isBlank()) strings.noPdfsSubtitle else "Try a different filename.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (searchQuery.isBlank()) {
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onCreatePdf,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(strings.createFirstPdf)
                            }
                        }
                    }
                }
            }
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pdfs, key = { it.id }) { rec ->
                    PdfRecordGridItem(
                        record = rec,
                        strings = strings,
                        onOpen = { onOpenPreview(rec) },
                        onShare = { PdfFileHelper.sharePdf(context, File(rec.filePath)) },
                        onRename = { renamingRecord = rec },
                        onDuplicate = { onDuplicatePdf(rec) },
                        onPrint = { PdfFileHelper.printPdf(context, File(rec.filePath), rec.fileName) },
                        onSave = { onSavePdfToDevice(rec) },
                        onDelete = { deletingRecord = rec }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(pdfs, key = { it.id }) { rec ->
                    PdfRecordListItem(
                        record = rec,
                        strings = strings,
                        onOpen = { onOpenPreview(rec) },
                        onShare = { PdfFileHelper.sharePdf(context, File(rec.filePath)) },
                        onRename = { renamingRecord = rec },
                        onDuplicate = { onDuplicatePdf(rec) },
                        onPrint = { PdfFileHelper.printPdf(context, File(rec.filePath), rec.fileName) },
                        onSave = { onSavePdfToDevice(rec) },
                        onDelete = { deletingRecord = rec }
                    )
                }
            }
        }
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

    deletingRecord?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingRecord = null },
            title = { Text("Delete PDF?") },
            text = { Text("Remove \"${target.fileName}.pdf\" permanently from My PDFs?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePdf(target)
                        deletingRecord = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingRecord = null }) { Text("Cancel") }
            }
        )
    }
}
