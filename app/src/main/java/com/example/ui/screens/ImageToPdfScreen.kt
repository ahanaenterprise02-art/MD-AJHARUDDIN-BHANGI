package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.db.PdfRecordEntity
import com.example.domain.image.ImageProcessor
import com.example.domain.model.CompressionLevel
import com.example.domain.model.EditableImagePage
import com.example.domain.model.ImageFilterType
import com.example.domain.model.ImageQualityLevel
import com.example.domain.model.ImageScaleMode
import com.example.domain.model.MeasurementUnit
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizePreset
import com.example.domain.model.PdfGenerationConfig
import com.example.domain.pdf.PdfFileHelper
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandOrange
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandWhite
import com.example.ui.viewmodel.ImageToPdfUiState
import com.example.ui.viewmodel.ImageToPdfViewModel
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToPdfScreen(
    strings: AppStrings,
    uiState: ImageToPdfUiState,
    viewModel: ImageToPdfViewModel,
    onBack: () -> Unit,
    onOpenScanner: () -> Unit,
    onPdfGenerated: (PdfRecordEntity) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var croppingPage by remember { mutableStateOf<EditableImagePage?>(null) }
    var filteringPage by remember { mutableStateOf<EditableImagePage?>(null) }
    var previewingPageId by remember { mutableStateOf<String?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addImagesFromUris(uris)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        if (success && file != null && file.exists()) {
            viewModel.addCapturedFile(file.absolutePath)
        }
        pendingCameraFile = null
    }

    fun launchNativeCamera() {
        val dir = File(context.filesDir, "originals").apply { mkdirs() }
        val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
        pendingCameraFile = file
        val uri = PdfFileHelper.getFileProviderUri(context, file)
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchNativeCamera()
        } else {
            Toast.makeText(context, "Camera permission is required to capture photos.", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleCameraClick() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launchNativeCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    uiState.errorMessage?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(strings.imageToPdf, fontWeight = FontWeight.Bold)
                        Text(
                            text = strings.selectedImagesCount(uiState.pages.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandRed
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = strings.addMoreImages, tint = BrandNavy)
                    }
                    IconButton(
                        onClick = { handleCameraClick() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = strings.takePhoto, tint = BrandRed)
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.pages.isNotEmpty()) {
                Surface(
                    shadowElevation = 10.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        if (uiState.isGenerating) {
                            Text(
                                text = uiState.progressMessage,
                                style = MaterialTheme.typography.labelMedium,
                                color = BrandNavy
                            )
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = {
                                    if (uiState.progressTotal > 0) {
                                        uiState.progressCurrent.toFloat() / uiState.progressTotal
                                    } else 0f
                                },
                                color = BrandRed,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(
                            onClick = { viewModel.generatePdf(onPdfGenerated) },
                            enabled = !uiState.isGenerating && !uiState.isImporting,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("generate_pdf_button")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (uiState.isGenerating) "Creating PDF..." else "${strings.generatePdf} (${uiState.pages.size})",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Images (${uiState.pages.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Filters") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Page & PDF") }
                )
            }

            if (uiState.isImporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = BrandRed)
            }

            if (uiState.pages.isEmpty()) {
                EmptyImagesPickerPane(
                    strings = strings,
                    onPickGallery = {
                        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onTakePhoto = { handleCameraClick() },
                    onOpenScanner = onOpenScanner
                )
            } else {
                when (selectedTab) {
                    0 -> ImagesReorderGridTab(
                        pages = uiState.pages,
                        onSelect = { viewModel.selectPage(it) },
                        onRotate = { viewModel.rotatePage(it) },
                        onCrop = { page -> croppingPage = page },
                        onAutoCrop = { page ->
                            viewModel.autoCropPage(page.id) { cropped ->
                                Toast.makeText(
                                    context,
                                    if (cropped) "Auto-cropped page borders!" else "Full image kept (clear borders not found)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onAutoCropAll = {
                            viewModel.autoCropAllPages { count ->
                                Toast.makeText(
                                    context,
                                    if (count > 0) "Auto-cropped $count image(s)!" else "Images already fill frame cleanly",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onFilter = { page -> filteringPage = page },
                        onPreview = { page -> previewingPageId = page.id },
                        onRemove = { viewModel.removePage(it) },
                        onMove = { from, to -> viewModel.movePage(from, to) },
                        onAddMore = {
                            galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                    1 -> InlineFiltersTab(
                        pages = uiState.pages,
                        selectedPage = uiState.selectedPage ?: uiState.pages.first(),
                        onSelectPage = { viewModel.selectPage(it) },
                        onApplyFilter = { id, filter, b, c, s, all ->
                            viewModel.applyFilter(id, filter, b, c, s, all)
                        }
                    )
                    2 -> PageSettingsAndPreviewTab(
                        config = uiState.config,
                        samplePage = uiState.selectedPage ?: uiState.pages.first(),
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    croppingPage?.let { page ->
        ImageCropDialog(
            page = page,
            onDismiss = { croppingPage = null },
            onApply = { rect, quad ->
                viewModel.updateCrop(page.id, rect, quad)
                croppingPage = null
            }
        )
    }

    filteringPage?.let { page ->
        ImageFilterStudioDialog(
            page = page,
            onDismiss = { filteringPage = null },
            onApply = { filter, b, c, s, applyAll ->
                viewModel.applyFilter(page.id, filter, b, c, s, applyAll)
                filteringPage = null
            }
        )
    }

    previewingPageId?.let { id ->
        ImageFullPreviewDialog(
            pages = uiState.pages,
            initialPageId = id,
            onDismiss = { previewingPageId = null }
        )
    }
}

@Composable
private fun EmptyImagesPickerPane(
    strings: AppStrings,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onOpenScanner: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(BrandRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = BrandRed, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Select Images to Convert",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Choose multiple photos from Gallery or capture crisp document pages with Camera.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onPickGallery,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("pick_gallery_button")
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select from ${strings.selectImages}")
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onTakePhoto,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.takePhoto)
                    }
                    OutlinedButton(
                        onClick = onOpenScanner,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(strings.scanDocument, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagesReorderGridTab(
    pages: List<EditableImagePage>,
    onSelect: (String) -> Unit,
    onRotate: (String) -> Unit,
    onCrop: (EditableImagePage) -> Unit,
    onAutoCrop: (EditableImagePage) -> Unit,
    onAutoCropAll: () -> Unit,
    onFilter: (EditableImagePage) -> Unit,
    onPreview: (EditableImagePage) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAddMore: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Hold & drag to reorder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            OutlinedButton(
                onClick = onAutoCropAll,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("auto_crop_all_button")
            ) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = BrandOrange, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Auto Crop", maxLines = 1)
            }
            OutlinedButton(
                onClick = onAddMore,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("+ Add", maxLines = 1)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 158.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(pages, key = { _, p -> p.id }) { index, page ->
                EditableImageGridCard(
                    index = index,
                    total = pages.size,
                    page = page,
                    onSelect = { onSelect(page.id) },
                    onRotate = { onRotate(page.id) },
                    onCrop = { onCrop(page) },
                    onAutoCrop = { onAutoCrop(page) },
                    onFilter = { onFilter(page) },
                    onPreview = { onPreview(page) },
                    onRemove = { onRemove(page.id) },
                    onMoveLeft = { if (index > 0) onMove(index, index - 1) },
                    onMoveRight = { if (index < pages.lastIndex) onMove(index, index + 1) },
                    onDragSwap = { step ->
                        val target = (index + step).coerceIn(0, pages.lastIndex)
                        if (target != index) onMove(index, target)
                    }
                )
            }
        }
    }
}

@Composable
private fun EditableImageGridCard(
    index: Int,
    total: Int,
    page: EditableImagePage,
    onSelect: () -> Unit,
    onRotate: () -> Unit,
    onCrop: () -> Unit,
    onAutoCrop: () -> Unit,
    onFilter: () -> Unit,
    onPreview: () -> Unit,
    onRemove: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDragSwap: (Int) -> Unit
) {
    var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dragAccumX by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(
        page.id, page.rotationDegrees, page.cropRect, page.perspectiveQuad,
        page.filter, page.brightness, page.contrast, page.saturation
    ) {
        thumbBitmap = ImageProcessor.renderProcessedBitmap(page, maxDimension = 420, applyFilters = true)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .offset { IntOffset(dragAccumX.roundToInt(), 0) }
            .pointerInput(index, total) {
                detectDragGesturesAfterLongPress(
                    onDragEnd = { dragAccumX = 0f },
                    onDragCancel = { dragAccumX = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumX += dragAmount.x
                        if (dragAccumX > 110f) {
                            onDragSwap(1)
                            dragAccumX = 0f
                        } else if (dragAccumX < -110f) {
                            onDragSwap(-1)
                            dragAccumX = 0f
                        }
                    }
                )
            }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF151923))
                    .clickable {
                        onSelect()
                        onPreview()
                    }
            ) {
                val bmp = thumbBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${index + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        color = BrandRed,
                        modifier = Modifier.align(Alignment.Center).size(28.dp)
                    )
                }

                // Page Number Badge
                Surface(
                    color = BrandNavy,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Icon(Icons.Filled.DragIndicator, contentDescription = null, tint = BrandWhite, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("${index + 1}", color = BrandWhite, style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Remove Button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(BrandRed)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = BrandWhite, modifier = Modifier.size(16.dp))
                }

                // Quick Auto-Crop badge
                Surface(
                    color = BrandOrange,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clickable(onClick = onAutoCrop)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = "Auto Crop", tint = Color.Black, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Auto", color = Color.Black, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onMoveLeft, enabled = index > 0, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move earlier", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onRotate, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onCrop, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Crop, contentDescription = "Crop", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onFilter, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Filter, contentDescription = "Filter", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveRight, enabled = index < total - 1, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move later", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun InlineFiltersTab(
    pages: List<EditableImagePage>,
    selectedPage: EditableImagePage,
    onSelectPage: (String) -> Unit,
    onApplyFilter: (String, ImageFilterType, Float, Float, Float, Boolean) -> Unit
) {
    var applyToAll by remember { mutableStateOf(false) }
    var previewBmp by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(
        selectedPage.id, selectedPage.filter, selectedPage.brightness,
        selectedPage.contrast, selectedPage.saturation, selectedPage.rotationDegrees,
        selectedPage.cropRect, selectedPage.perspectiveQuad
    ) {
        previewBmp = ImageProcessor.renderProcessedBitmap(selectedPage, maxDimension = 760, applyFilters = true)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (pages.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pages.size) { idx ->
                        val p = pages[idx]
                        FilterChip(
                            selected = p.id == selectedPage.id,
                            onClick = { onSelectPage(p.id) },
                            label = { Text("Image ${idx + 1}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandNavy,
                                selectedLabelColor = BrandWhite
                            )
                        )
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF141822)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = previewBmp
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Live Filter Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                } else {
                    CircularProgressIndicator(color = BrandRed)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Apply changes to all images", style = MaterialTheme.typography.titleSmall)
                Switch(checked = applyToAll, onCheckedChange = { applyToAll = it })
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ImageFilterType.entries) { filter ->
                    FilterChip(
                        selected = selectedPage.filter == filter,
                        onClick = {
                            onApplyFilter(
                                selectedPage.id, filter,
                                selectedPage.brightness, selectedPage.contrast, selectedPage.saturation,
                                applyToAll
                            )
                        },
                        label = { Text(filter.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandRed,
                            selectedLabelColor = BrandWhite
                        )
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Fine Adjustments", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text("Brightness (${selectedPage.brightness.toInt()})", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = selectedPage.brightness,
                        onValueChange = {
                            onApplyFilter(selectedPage.id, selectedPage.filter, it, selectedPage.contrast, selectedPage.saturation, applyToAll)
                        },
                        valueRange = -80f..80f,
                        colors = SliderDefaults.colors(thumbColor = BrandRed, activeTrackColor = BrandNavy)
                    )
                    Text("Contrast (${String.format("%.2f", selectedPage.contrast)}x)", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = selectedPage.contrast,
                        onValueChange = {
                            onApplyFilter(selectedPage.id, selectedPage.filter, selectedPage.brightness, it, selectedPage.saturation, applyToAll)
                        },
                        valueRange = 0.5f..1.8f,
                        colors = SliderDefaults.colors(thumbColor = BrandRed, activeTrackColor = BrandNavy)
                    )
                    Text("Saturation (${String.format("%.2f", selectedPage.saturation)}x)", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = selectedPage.saturation,
                        onValueChange = {
                            onApplyFilter(selectedPage.id, selectedPage.filter, selectedPage.brightness, selectedPage.contrast, it, applyToAll)
                        },
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(thumbColor = BrandRed, activeTrackColor = BrandNavy)
                    )
                }
            }
        }
    }
}

@Composable
private fun PageSettingsAndPreviewTab(
    config: PdfGenerationConfig,
    samplePage: EditableImagePage,
    viewModel: ImageToPdfViewModel
) {
    var sampleBmp by remember { mutableStateOf<Bitmap?>(null) }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(
        samplePage.id, samplePage.rotationDegrees, samplePage.filter,
        samplePage.cropRect, samplePage.perspectiveQuad
    ) {
        sampleBmp = ImageProcessor.renderProcessedBitmap(samplePage, maxDimension = 360, applyFilters = true)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Live Page Preview
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Live Page Preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    val (pageW, pageH) = config.resolvePageDimensionsPt(
                        sampleBmp?.width ?: 595,
                        sampleBmp?.height ?: 842
                    )
                    val aspect = (pageW.toFloat() / pageH.coerceAtLeast(1)).coerceIn(0.45f, 2.0f)
                    val bgColor = Color(config.backgroundColorArgb)

                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val targetHeight = (maxWidth / aspect).coerceAtMost(190.dp)
                        val targetWidth = targetHeight * aspect
                        Box(
                            modifier = Modifier
                                .size(targetWidth, targetHeight)
                                .clip(RoundedCornerShape(6.dp))
                                .background(bgColor)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val marginFraction = (config.marginPt.toFloat() / minOf(pageW, pageH).coerceAtLeast(100)).coerceIn(0f, 0.25f)
                            val padDp = targetHeight * marginFraction
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padDp),
                                contentAlignment = Alignment.Center
                            ) {
                                sampleBmp?.let { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Page layout preview",
                                        contentScale = if (config.scaleMode == ImageScaleMode.FILL_PAGE) ContentScale.Crop else ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                if (config.marginPt > 0) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawRect(
                                            color = BrandNavy.copy(alpha = 0.28f),
                                            style = Stroke(width = 2f)
                                        )
                                    }
                                }
                            }
                            if (config.watermarkText.isNotBlank()) {
                                Text(
                                    text = config.watermarkText,
                                    color = BrandRed.copy(alpha = 0.45f),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (config.addPageNumbers) {
                                Text(
                                    text = "1 / N",
                                    color = Color.DarkGray,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${config.pageSizePreset.label} • ${config.orientation.label} • ${pageW}x${pageH} pt",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // File Name
        item {
            OutlinedTextField(
                value = config.fileName,
                onValueChange = { txt -> viewModel.updateConfig { it.copy(fileName = txt) } },
                label = { Text("PDF File Name") },
                suffix = { Text(".pdf") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("pdf_filename_input")
            )
        }

        // Page Size Presets
        item {
            Column {
                Text("Page Size", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PageSizePreset.entries) { preset ->
                        FilterChip(
                            selected = config.pageSizePreset == preset,
                            onClick = { viewModel.setPageSizePreset(preset) },
                            label = { Text(preset.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandNavy,
                                selectedLabelColor = BrandWhite
                            )
                        )
                    }
                }
            }
        }

        // Custom Width/Height & Measurement Units
        if (config.pageSizePreset == PageSizePreset.CUSTOM) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Custom Dimensions", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MeasurementUnit.entries.forEach { u ->
                                FilterChip(
                                    selected = config.unit == u,
                                    onClick = { viewModel.setMeasurementUnit(u) },
                                    label = { Text("${u.label} (${u.shortLabel})") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandRed,
                                        selectedLabelColor = BrandWhite
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = config.customWidth.toString(),
                                onValueChange = { v ->
                                    v.toFloatOrNull()?.let { w -> viewModel.updateConfig { it.copy(customWidth = w) } }
                                },
                                label = { Text("Width (${config.unit.shortLabel})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = config.customHeight.toString(),
                                onValueChange = { v ->
                                    v.toFloatOrNull()?.let { h -> viewModel.updateConfig { it.copy(customHeight = h) } }
                                },
                                label = { Text("Height (${config.unit.shortLabel})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Orientation & Scaling Mode
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Orientation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageOrientation.entries.forEach { ori ->
                        FilterChip(
                            selected = config.orientation == ori,
                            onClick = { viewModel.setOrientation(ori) },
                            label = { Text(ori.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandNavy,
                                selectedLabelColor = BrandWhite
                            )
                        )
                    }
                }
                Text("Image Scaling", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ImageScaleMode.entries) { mode ->
                        FilterChip(
                            selected = config.scaleMode == mode,
                            onClick = { viewModel.setScaleMode(mode) },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandNavy,
                                selectedLabelColor = BrandWhite
                            )
                        )
                    }
                }
            }
        }

        // Margins & Background Color
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Page Margin: ${config.marginPt} pt", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = config.marginPt.toFloat(),
                        onValueChange = { v -> viewModel.updateConfig { it.copy(marginPt = v.roundToInt()) } },
                        valueRange = 0f..56f,
                        colors = SliderDefaults.colors(thumbColor = BrandRed, activeTrackColor = BrandNavy)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Page Background", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    val swatches = listOf(
                        "White" to 0xFFFFFFFF.toInt(),
                        "Cream" to 0xFFFFF8E7.toInt(),
                        "Light Gray" to 0xFFF2F4F7.toInt(),
                        "Ice Blue" to 0xFFEFF4FF.toInt(),
                        "Dark" to 0xFF181C24.toInt()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        swatches.forEach { (name, argb) ->
                            val selected = config.backgroundColorArgb == argb
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) BrandRed else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        viewModel.updateConfig { it.copy(backgroundColorArgb = argb) }
                                    }
                            )
                        }
                    }
                }
            }
        }

        // Image Quality & Compression
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Image Quality", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ImageQualityLevel.entries) { q ->
                        FilterChip(
                            selected = config.imageQuality == q,
                            onClick = { viewModel.setQuality(q) },
                            label = { Text("${q.label} (${q.jpegQuality}%)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandNavy,
                                selectedLabelColor = BrandWhite
                            )
                        )
                    }
                }
                Text("Compression Level", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompressionLevel.entries.forEach { c ->
                        FilterChip(
                            selected = config.compressionLevel == c,
                            onClick = { viewModel.setCompression(c) },
                            label = { Text(c.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandRed,
                                selectedLabelColor = BrandWhite
                            )
                        )
                    }
                }
            }
        }

        // Page Numbers, Watermark, Password Protection
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Add Page Numbers", style = MaterialTheme.typography.titleSmall)
                        Switch(
                            checked = config.addPageNumbers,
                            onCheckedChange = { checked ->
                                viewModel.updateConfig { it.copy(addPageNumbers = checked) }
                            }
                        )
                    }
                    OutlinedTextField(
                        value = config.watermarkText,
                        onValueChange = { wm -> viewModel.updateConfig { it.copy(watermarkText = wm) } },
                        label = { Text("Watermark Text (Optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = config.password,
                        onValueChange = { pw -> viewModel.updateConfig { it.copy(password = pw) } },
                        label = { Text("Password Protection (Optional)") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
