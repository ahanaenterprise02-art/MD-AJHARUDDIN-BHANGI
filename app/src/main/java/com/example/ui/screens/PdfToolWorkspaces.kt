package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.db.PdfRecordEntity
import com.example.domain.model.PdfToolType
import com.example.domain.pdf.PdfFileHelper
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandWhite
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.PdfToolsUiState
import com.example.ui.viewmodel.PdfToolsViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ToolWorkspaceContent(
    tool: PdfToolType,
    uiState: PdfToolsUiState,
    viewModel: PdfToolsViewModel,
    onPickMoreForMerge: () -> Unit,
    onPdfResult: (PdfRecordEntity) -> Unit,
    onSplitMultipleResult: () -> Unit
) {
    val primary = uiState.selectedPdfs.firstOrNull() ?: return
    val baseOutputName = remember(primary.name, tool.id) { "${primary.name}_${tool.id}" }

    when (tool) {
        PdfToolType.PDF_TO_JPG, PdfToolType.PDF_TO_PNG -> {
            PdfToImagesWorkspace(
                asPng = tool == PdfToolType.PDF_TO_PNG,
                uiState = uiState,
                onConvert = { viewModel.runPdfToImages(asPng = tool == PdfToolType.PDF_TO_PNG) }
            )
        }
        PdfToolType.MERGE_PDF -> {
            MergePdfWorkspace(
                uiState = uiState,
                viewModel = viewModel,
                onAddPdf = onPickMoreForMerge,
                onMerge = { name -> viewModel.runMerge(name, onPdfResult) }
            )
        }
        PdfToolType.SPLIT_PDF -> {
            SplitPdfWorkspace(
                totalPages = primary.pageCount,
                defaultName = baseOutputName,
                isBusy = uiState.isProcessing,
                onSplit = { ranges, eachPage, outName ->
                    viewModel.runSplit(ranges, eachPage, outName) { created ->
                        if (created.size == 1) onPdfResult(created.first())
                        else onSplitMultipleResult()
                    }
                }
            )
        }
        PdfToolType.COMPRESS_PDF -> {
            CompressPdfWorkspace(
                currentBytes = primary.sizeBytes,
                defaultName = "${primary.name}_compressed",
                isBusy = uiState.isProcessing,
                onCompress = { q, maxDim, outName ->
                    viewModel.runCompress(q, maxDim, outName, onPdfResult)
                }
            )
        }
        PdfToolType.ROTATE_PDF -> {
            RotatePdfWorkspace(
                uiState = uiState,
                defaultName = "${primary.name}_rotated",
                onRotate = { map, outName -> viewModel.runRotate(map, outName, onPdfResult) }
            )
        }
        PdfToolType.DELETE_PAGES -> {
            DeletePagesWorkspace(
                uiState = uiState,
                defaultName = "${primary.name}_edited",
                onDeletePages = { set, outName -> viewModel.runDeletePages(set, outName, onPdfResult) }
            )
        }
        PdfToolType.ADD_TEXT -> {
            AddTextWorkspace(
                uiState = uiState,
                defaultName = "${primary.name}_text",
                onApply = { pageIdx, text, xR, yR, sizePt, colorArgb, outName ->
                    viewModel.runAddText(pageIdx, text, xR, yR, sizePt, colorArgb, outName, onPdfResult)
                }
            )
        }
        PdfToolType.ADD_SIGNATURE -> {
            AddSignatureWorkspace(
                uiState = uiState,
                defaultName = "${primary.name}_signed",
                onApply = { pageIdx, sigBmp, xR, yR, wR, outName ->
                    viewModel.runAddSignature(pageIdx, sigBmp, xR, yR, wR, outName, onPdfResult)
                }
            )
        }
        PdfToolType.ADD_WATERMARK -> {
            AddWatermarkWorkspace(
                defaultName = "${primary.name}_watermarked",
                isBusy = uiState.isProcessing,
                onApply = { text, opacity, angle, size, colorArgb, outName ->
                    viewModel.runAddWatermark(text, opacity, angle, size, colorArgb, outName, onPdfResult)
                }
            )
        }
        PdfToolType.ADD_PAGE_NUMBERS -> {
            AddPageNumbersWorkspace(
                defaultName = "${primary.name}_numbered",
                isBusy = uiState.isProcessing,
                onApply = { style, center, startNum, outName ->
                    viewModel.runAddPageNumbers(style, center, startNum, outName, onPdfResult)
                }
            )
        }
        PdfToolType.LOCK_PDF -> {
            LockUnlockWorkspace(
                isLock = true,
                defaultName = "${primary.name}_locked",
                isBusy = uiState.isProcessing,
                onSubmit = { pw, outName -> viewModel.runLockPdf(pw, outName, onPdfResult) }
            )
        }
        PdfToolType.UNLOCK_PDF -> {
            LockUnlockWorkspace(
                isLock = false,
                defaultName = "${primary.name}_unlocked",
                isBusy = uiState.isProcessing,
                onSubmit = { pw, outName -> viewModel.runUnlockPdf(pw, outName, onPdfResult) }
            )
        }
    }
}

@Composable
private fun PdfToImagesWorkspace(
    asPng: Boolean,
    uiState: PdfToolsUiState,
    onConvert: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mime = if (asPng) "image/png" else "image/jpeg"
    val extLabel = if (asPng) "PNG" else "JPG"

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Button(
                onClick = onConvert,
                enabled = !uiState.isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("tool_run_button")
            ) {
                Text("Convert All Pages to $extLabel")
            }
        }

        if (uiState.exportedImages.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                var saved = 0
                                uiState.exportedImages.forEach { f ->
                                    if (PdfFileHelper.saveImageToMediaStore(context, f, mime)) saved++
                                }
                                Toast.makeText(context, "Saved $saved $extLabel images to Pictures/AhaPDF", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save All")
                    }
                    Button(
                        onClick = { PdfFileHelper.shareImages(context, uiState.exportedImages) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share All")
                    }
                }
            }
            itemsIndexed(uiState.exportedImages) { idx, file ->
                val bmp = remember(file.absolutePath) { BitmapFactory.decodeFile(file.absolutePath) }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Page ${idx + 1} (${file.name})", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        bmp?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Exported page ${idx + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().height(200.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MergePdfWorkspace(
    uiState: PdfToolsUiState,
    viewModel: PdfToolsViewModel,
    onAddPdf: () -> Unit,
    onMerge: (String) -> Unit
) {
    var outputName by remember { mutableStateOf("AhaPDF_Merged_${System.currentTimeMillis() / 1000}") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = outputName,
                onValueChange = { outputName = it },
                label = { Text("Merged File Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedButton(
                onClick = onAddPdf,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Add Another PDF (${uiState.selectedPdfs.size} selected)")
            }
        }
        itemsIndexed(uiState.selectedPdfs) { idx, src ->
            Card(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${idx + 1}.", style = MaterialTheme.typography.titleMedium, color = BrandNavy, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${src.name}.pdf", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text("${src.pageCount} pages • ${PdfFileHelper.formatFileSize(src.sizeBytes)}", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { viewModel.moveMergeItem(idx, idx - 1) }, enabled = idx > 0) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                    }
                    IconButton(onClick = { viewModel.moveMergeItem(idx, idx + 1) }, enabled = idx < uiState.selectedPdfs.lastIndex) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                    }
                    IconButton(onClick = { viewModel.removeSelectedPdfAt(idx) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove", tint = BrandRed)
                    }
                }
            }
        }
        item {
            Button(
                onClick = { onMerge(outputName) },
                enabled = uiState.selectedPdfs.size >= 2 && !uiState.isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("tool_run_button")
            ) {
                Text("Merge ${uiState.selectedPdfs.size} PDFs")
            }
        }
    }
}

@Composable
private fun SplitPdfWorkspace(
    totalPages: Int,
    defaultName: String,
    isBusy: Boolean,
    onSplit: (String, Boolean, String) -> Unit
) {
    var splitEach by remember { mutableStateOf(false) }
    var ranges by remember { mutableStateOf(if (totalPages > 1) "1-${totalPages / 2}" else "1") }
    var outputName by remember(defaultName) { mutableStateOf(defaultName) }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Total Pages: $totalPages", style = MaterialTheme.typography.titleSmall, color = BrandNavy)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !splitEach,
                onClick = { splitEach = false },
                label = { Text("Extract Range") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandNavy, selectedLabelColor = BrandWhite)
            )
            FilterChip(
                selected = splitEach,
                onClick = { splitEach = true },
                label = { Text("Every Page Separate") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandRed, selectedLabelColor = BrandWhite)
            )
        }
        if (!splitEach) {
            OutlinedTextField(
                value = ranges,
                onValueChange = { ranges = it },
                label = { Text("Pages / Ranges (e.g. 1-3, 5)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = outputName,
            onValueChange = { outputName = it },
            label = { Text("Output Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onSplit(ranges, splitEach, outputName) },
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("tool_run_button")
        ) {
            Text(if (splitEach) "Split All $totalPages Pages" else "Extract Selected Pages")
        }
    }
}

@Composable
private fun CompressPdfWorkspace(
    currentBytes: Long,
    defaultName: String,
    isBusy: Boolean,
    onCompress: (Int, Int, String) -> Unit
) {
    var level by remember { mutableIntStateOf(1) } // 0=Low, 1=Balanced, 2=Extreme
    var outputName by remember(defaultName) { mutableStateOf(defaultName) }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Current Size: ${PdfFileHelper.formatFileSize(currentBytes)}", style = MaterialTheme.typography.titleSmall, color = BrandNavy)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Gentle" to 0, "Balanced" to 1, "Maximum" to 2).forEach { (label, idx) ->
                FilterChip(
                    selected = level == idx,
                    onClick = { level = idx },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandNavy, selectedLabelColor = BrandWhite)
                )
            }
        }
        OutlinedTextField(
            value = outputName,
            onValueChange = { outputName = it },
            label = { Text("Output File Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                val (q, dim) = when (level) {
                    0 -> 82 to 1800
                    2 -> 42 to 1050
                    else -> 64 to 1400
                }
                onCompress(q, dim, outputName)
            },
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("tool_run_button")
        ) {
            Text("Compress PDF")
        }
    }
}

@Composable
private fun RotatePdfWorkspace(
    uiState: PdfToolsUiState,
    defaultName: String,
    onRotate: (Map<Int, Int>, String) -> Unit
) {
    val rotations = remember { mutableStateMapOf<Int, Int>() }
    var outputName by remember(defaultName) { mutableStateOf(defaultName) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tap page to rotate 90°", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = {
                    uiState.pageThumbnails.indices.forEach { idx ->
                        rotations[idx] = ((rotations[idx] ?: 0) + 90) % 360
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Rotate All")
            }
        }
        Spacer(Modifier.height(10.dp))
        if (uiState.isLoadingThumbnails) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandRed)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(uiState.pageThumbnails) { idx, bmp ->
                    val angle = rotations[idx] ?: 0
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, if (angle != 0) BrandRed else MaterialTheme.colorScheme.outline),
                        modifier = Modifier.clickable {
                            rotations[idx] = (angle + 90) % 360
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Page ${idx + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .height(120.dp)
                                    .fillMaxWidth()
                                    .rotate(angle.toFloat())
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("P${idx + 1} (${angle}°)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = outputName,
            onValueChange = { outputName = it },
            label = { Text("Output File Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onRotate(rotations.toMap(), outputName) },
            enabled = !uiState.isProcessing,
            colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("tool_run_button")
        ) {
            Text("Save Rotated PDF")
        }
    }
}

@Composable
private fun DeletePagesWorkspace(
    uiState: PdfToolsUiState,
    defaultName: String,
    onDeletePages: (Set<Int>, String) -> Unit
) {
    val selectedForDeletion = remember { mutableStateListOf<Int>() }
    var outputName by remember(defaultName) { mutableStateOf(defaultName) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Tap pages to delete (${selectedForDeletion.size} selected)",
            style = MaterialTheme.typography.titleSmall,
            color = BrandRed
        )
        Spacer(Modifier.height(10.dp))
        if (uiState.isLoadingThumbnails) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandRed)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(uiState.pageThumbnails) { idx, bmp ->
                    val marked = idx in selectedForDeletion
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(2.dp, if (marked) BrandRed else MaterialTheme.colorScheme.outline),
                        modifier = Modifier.clickable {
                            if (marked) selectedForDeletion.remove(idx)
                            else selectedForDeletion.add(idx)
                        }
                    ) {
                        Box(modifier = Modifier.padding(6.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Page ${idx + 1}",
                                    contentScale = ContentScale.Fit,
                                    alpha = if (marked) 0.4f else 1f,
                                    modifier = Modifier.height(120.dp).fillMaxWidth()
                                )
                                Text("Page ${idx + 1}", style = MaterialTheme.typography.labelSmall)
                            }
                            if (marked) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(BrandRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Marked", tint = BrandWhite, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = outputName,
            onValueChange = { outputName = it },
            label = { Text("Output File Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onDeletePages(selectedForDeletion.toSet(), outputName) },
            enabled = selectedForDeletion.isNotEmpty() && !uiState.isProcessing,
            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("tool_run_button")
        ) {
            Text("Delete ${selectedForDeletion.size} Selected Pages")
        }
    }
}

@Composable
private fun AddTextWorkspace(
    uiState: PdfToolsUiState,
    defaultName: String,
    onApply: (Int?, String, Float, Float, Float, Int, String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var applyAll by remember { mutableStateOf(false) }
    var pageIdx by remember { mutableIntStateOf(0) }
    var xRatio by remember { mutableFloatStateOf(0.15f) }
    var yRatio by remember { mutableFloatStateOf(0.18f) }
    var fontSize by remember { mutableFloatStateOf(18f) }
    var colorArgb by remember { mutableIntStateOf(0xFF102F83.toInt()) }
    var outputName by remember(defaultName) { mutableStateOf(defaultName) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text to Add") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Apply to All Pages", style = MaterialTheme.typography.titleSmall)
                Switch(checked = applyAll, onCheckedChange = { applyAll = it })
            }
        }
        if (!applyAll && uiState.pageThumbnails.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.pageThumbnails.size) { idx ->
                        FilterChip(
                            selected = pageIdx == idx,
                            onClick = { pageIdx = idx },
                            label = { Text("Page ${idx + 1}") }
                        )
                    }
                }
            }
        }
        item {
            val previewBmp = uiState.pageThumbnails.getOrNull(pageIdx)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E2430)),
                contentAlignment = Alignment.Center
            ) {
                previewBmp?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Text position preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(
                    text = text.ifBlank { "Sample Text" },
                    color = Color(colorArgb),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = (xRatio * 220).dp,
                            top = (yRatio * 170).dp
                        )
                )
            }
        }
        item {
            Column {
                Text("Horizontal Position (${(xRatio * 100).roundToInt()}%)", style = MaterialTheme.typography.labelSmall)
                Slider(value = xRatio, onValueChange = { xRatio = it }, valueRange = 0.05f..0.85f)
                Text("Vertical Position (${(yRatio * 100).roundToInt()}%)", style = MaterialTheme.typography.labelSmall)
                Slider(value = yRatio, onValueChange = { yRatio = it }, valueRange = 0.05f..0.90f)
                Text("Font Size: ${fontSize.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 10f..48f)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0xFF102F83.toInt(), 0xFFED1010.toInt(), 0xFF111111.toInt(), 0xFF555555.toInt()).forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(if (colorArgb == c) 3.dp else 1.dp, Color.White, CircleShape)
                            .clickable { colorArgb = c }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = outputName,
                onValueChange = { outputName = it },
                label = { Text("Output File Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = { onApply(if (applyAll) null else pageIdx, text, xRatio, yRatio, fontSize, colorArgb, outputName) },
                enabled = text.isNotBlank() && !uiState.isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("tool_run_button")
            ) {
                Text("Add Text to PDF")
            }
        }
    }
}

@Composable
private fun AddSignatureWorkspace(
    uiState: PdfToolsUiState,
    defaultName: String,
    onApply: (Int, Bitmap, Float, Float, Float, String) -> Unit
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var inkColor by remember { mutableStateOf(Color(0xFF102F83)) }
    var pageIdx by remember { mutableIntStateOf(0) }
    var xRatio by remember { mutableFloatStateOf(0.68f) }
    var yRatio by remember { mutableFloatStateOf(0.82f) }
    var widthRatio by remember { mutableFloatStateOf(0.28f) }
    var canvasWidthPx by remember { mutableFloatStateOf(600f) }
    var canvasHeightPx by remember { mutableFloatStateOf(240f) }
    var outputName by remember(defaultName) { mutableStateOf(defaultName) }

    fun renderSignatureBitmap(): Bitmap {
        val w = canvasWidthPx.roundToInt().coerceIn(300, 1000)
        val h = canvasHeightPx.roundToInt().coerceIn(150, 500)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(
                255,
                (inkColor.red * 255).toInt(),
                (inkColor.green * 255).toInt(),
                (inkColor.blue * 255).toInt()
            )
            strokeWidth = 8f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { pts ->
            if (pts.size >= 2) {
                val p = Path()
                p.moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { pt -> p.lineTo(pt.x, pt.y) }
                canvas.drawPath(p, paint)
            }
        }
        return bmp
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Draw Your Signature Below", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = { strokes.clear() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Clear Signature")
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BrandWhite)
                    .border(2.dp, BrandNavy, RoundedCornerShape(16.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { start -> currentStroke = listOf(start) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.isNotEmpty()) strokes.add(currentStroke)
                                    currentStroke = emptyList()
                                }
                            )
                        }
                ) {
                    canvasWidthPx = size.width
                    canvasHeightPx = size.height
                    val all = strokes + listOf(currentStroke)
                    all.forEach { pts ->
                        if (pts.size >= 2) {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(pts.first().x, pts.first().y)
                                pts.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(
                                path = path,
                                color = inkColor,
                                style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }
                if (strokes.isEmpty() && currentStroke.isEmpty()) {
                    Text(
                        text = "Sign here with your finger",
                        color = Color.LightGray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Color(0xFF102F83), Color.Black, BrandRed).forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (inkColor == c) 3.dp else 1.dp, Color.Gray, CircleShape)
                            .clickable { inkColor = c }
                    )
                }
            }
        }

        if (uiState.pageThumbnails.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.pageThumbnails.size) { idx ->
                        FilterChip(
                            selected = pageIdx == idx,
                            onClick = { pageIdx = idx },
                            label = { Text("Page ${idx + 1}") }
                        )
                    }
                }
            }
        }

        item {
            Text("Horizontal Position (${(xRatio * 100).roundToInt()}%)", style = MaterialTheme.typography.labelSmall)
            Slider(value = xRatio, onValueChange = { xRatio = it }, valueRange = 0.15f..0.85f)
            Text("Vertical Position (${(yRatio * 100).roundToInt()}%)", style = MaterialTheme.typography.labelSmall)
            Slider(value = yRatio, onValueChange = { yRatio = it }, valueRange = 0.10f..0.92f)
            Text("Signature Width (${(widthRatio * 100).roundToInt()}%)", style = MaterialTheme.typography.labelSmall)
            Slider(value = widthRatio, onValueChange = { widthRatio = it }, valueRange = 0.15f..0.60f)
        }

        item {
            OutlinedTextField(
                value = outputName,
                onValueChange = { outputName = it },
                label = { Text("Output File Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Button(
                onClick = {
                    val bmp = renderSignatureBitmap()
                    onApply(pageIdx, bmp, xRatio, yRatio, widthRatio, outputName)
                },
                enabled = strokes.isNotEmpty() && !uiState.isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("tool_run_button")
            ) {
                Text("Stamp Signature onto PDF")
            }
        }
    }
}

@Composable
private fun AddWatermarkWorkspace(
    defaultName: String,
    isBusy: Boolean,
    onApply: (String, Float, Float, Float, Int, String) -> Unit
) {
    var text by remember { mutableStateOf("AHANA ENTERPRISE") }
    var opacity by remember { mutableFloatStateOf(0.28f) }
    var angle by remember { mutableFloatStateOf(35f) }
    var fontSize by remember { mutableFloatStateOf(42f) }
    var colorArgb by remember { mutableIntStateOf(0xFFED1010.toInt()) }
    var outputName by remember(defaultName) { mutableStateOf(defaultName) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Watermark Text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text("Opacity: ${(opacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
            Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.1f..0.75f)
            Text("Font Size: ${fontSize.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
            Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 18f..72f)
            Text("Angle: ${angle.roundToInt()}°", style = MaterialTheme.typography.labelSmall)
            Slider(value = angle, onValueChange = { angle = it }, valueRange = -60f..60f)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0xFFED1010.toInt(), 0xFF102F83.toInt(), 0xFF666666.toInt()).forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(if (colorArgb == c) 3.dp else 1.dp, Color.White, CircleShape)
                            .clickable { colorArgb = c }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = outputName,
                onValueChange = { outputName = it },
                label = { Text("Output File Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = { onApply(text, opacity, angle, fontSize, colorArgb, outputName) },
                enabled = text.isNotBlank() && !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("tool_run_button")
            ) {
                Text("Apply Watermark")
            }
        }
    }
}

@Composable
private fun AddPageNumbersWorkspace(
    defaultName: String,
    isBusy: Boolean,
    onApply: (Int, Boolean, Int, String) -> Unit
) {
    var style by remember { mutableIntStateOf(1) }
    var bottomCenter by remember { mutableStateOf(true) }
    var startNumText by remember { mutableStateOf("1") }
    var outputName by remember(defaultName) { mutableStateOf(defaultName) }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Number Format", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("1" to 0, "Page 1 of N" to 1, "1 / N" to 2).forEach { (label, idx) ->
                FilterChip(
                    selected = style == idx,
                    onClick = { style = idx },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandNavy, selectedLabelColor = BrandWhite)
                )
            }
        }
        Text("Position", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = bottomCenter, onClick = { bottomCenter = true }, label = { Text("Bottom Center") })
            FilterChip(selected = !bottomCenter, onClick = { bottomCenter = false }, label = { Text("Bottom Right") })
        }
        OutlinedTextField(
            value = startNumText,
            onValueChange = { startNumText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Start Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = outputName,
            onValueChange = { outputName = it },
            label = { Text("Output File Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onApply(style, bottomCenter, startNumText.toIntOrNull() ?: 1, outputName) },
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("tool_run_button")
        ) {
            Text("Add Page Numbers")
        }
    }
}

@Composable
private fun LockUnlockWorkspace(
    isLock: Boolean,
    defaultName: String,
    isBusy: Boolean,
    onSubmit: (String, String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var outputName by remember(defaultName) { mutableStateOf(defaultName) }

    val valid = if (isLock) password.isNotBlank() && password == confirmPassword else password.isNotBlank()

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(if (isLock) "Set PDF Password" else "Enter PDF Password") },
            leadingIcon = { Icon(Icons.Filled.Lock, null) },
            trailingIcon = {
                IconButton(onClick = { showPw = !showPw }) {
                    Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Toggle password")
                }
            },
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (isLock) {
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password") },
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                isError = confirmPassword.isNotEmpty() && confirmPassword != password,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = outputName,
            onValueChange = { outputName = it },
            label = { Text("Output File Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onSubmit(password, outputName) },
            enabled = valid && !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = if (isLock) BrandRed else BrandNavy),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("tool_run_button")
        ) {
            Text(if (isLock) "Lock PDF with Password" else "Unlock PDF")
        }
    }
}
