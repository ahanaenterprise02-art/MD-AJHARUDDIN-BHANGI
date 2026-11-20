package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.image.ImageProcessor
import com.example.domain.model.EditableImagePage
import com.example.domain.model.ImageFilterType
import com.example.domain.model.NormalizedCropRect
import com.example.domain.model.NormalizedQuad
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandOrange
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandWhite
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

@Composable
fun DocumentScannerScreen(
    onBack: () -> Unit,
    onDoneWithPages: (List<EditableImagePage>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val scannedPages = remember { mutableStateListOf<EditableImagePage>() }
    var reviewMode by remember { mutableStateOf(false) }
    var selectedReviewIdx by remember { mutableIntStateOf(0) }
    var croppingPage by remember { mutableStateOf<EditableImagePage?>(null) }

    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var autoEdgeEnabled by remember { mutableStateOf(true) }
    var detectedQuad by remember { mutableStateOf(NormalizedQuad(0.10f, 0.12f, 0.90f, 0.12f, 0.90f, 0.88f, 0.10f, 0.88f)) }
    var isCapturing by remember { mutableStateOf(false) }

    if (!hasCameraPermission) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = BrandRed, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("Camera Access Needed", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "AhaPDF uses your camera locally for real-time document edge detection and multi-page scanning.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Grant Camera Permission")
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Back")
                }
            }
        }
        return
    }

    if (reviewMode && scannedPages.isNotEmpty()) {
        val currentIdx = selectedReviewIdx.coerceIn(0, scannedPages.lastIndex)
        val currentPage = scannedPages[currentIdx]
        var previewBmp by remember { mutableStateOf<Bitmap?>(null) }

        LaunchedEffect(
            currentPage.id, currentPage.rotationDegrees, currentPage.cropRect,
            currentPage.perspectiveQuad, currentPage.filter,
            currentPage.brightness, currentPage.contrast, currentPage.saturation
        ) {
            previewBmp = ImageProcessor.renderProcessedBitmap(currentPage, maxDimension = 1000, applyFilters = true)
        }

        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0E1118)) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { reviewMode = false }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to camera", tint = BrandWhite)
                    }
                    Text(
                        text = "Scan Review (${currentIdx + 1}/${scannedPages.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = BrandWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { onDoneWithPages(scannedPages.toList()) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("To PDF")
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    previewBmp?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Scanned page preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Enhance Filters Row
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val scanFilters = listOf(
                        ImageFilterType.DOCUMENT,
                        ImageFilterType.MAGIC_COLOR,
                        ImageFilterType.BLACK_AND_WHITE,
                        ImageFilterType.AUTO_ENHANCE,
                        ImageFilterType.ORIGINAL
                    )
                    items(scanFilters.size) { idx ->
                        val f = scanFilters[idx]
                        FilterChip(
                            selected = currentPage.filter == f,
                            onClick = {
                                scannedPages[currentIdx] = currentPage.copy(filter = f)
                            },
                            label = { Text(f.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandOrange,
                                selectedLabelColor = Color.Black,
                                labelColor = BrandWhite
                            )
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Page Switcher Strip
                if (scannedPages.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(scannedPages) { idx, _ ->
                            Surface(
                                color = if (idx == currentIdx) BrandNavy else Color(0xFF1F2636),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.clickable { selectedReviewIdx = idx }
                            ) {
                                Text(
                                    text = "Page ${idx + 1}",
                                    color = BrandWhite,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Edit Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ScannerActionBtn("Auto Crop", Icons.Filled.AutoFixHigh) {
                        scope.launch {
                            val raw = ImageProcessor.renderProcessedBitmap(
                                page = currentPage.copy(
                                    cropRect = NormalizedCropRect(),
                                    perspectiveQuad = null,
                                    filter = ImageFilterType.ORIGINAL,
                                    brightness = 0f,
                                    contrast = 1f,
                                    saturation = 1f
                                ),
                                maxDimension = 640,
                                applyFilters = false
                            )
                            if (raw != null) {
                                val (rect, _) = ImageProcessor.autoDetectCropAndQuad(raw)
                                raw.recycle()
                                scannedPages[currentIdx] = currentPage.copy(cropRect = rect, perspectiveQuad = null)
                                Toast.makeText(
                                    context,
                                    if (rect.isFull) "Full frame kept (clear borders not found)" else "Auto-cropped document!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    ScannerActionBtn("Crop/Warp", Icons.Filled.Crop) { croppingPage = currentPage }
                    ScannerActionBtn("Rotate", Icons.AutoMirrored.Filled.RotateRight) {
                        scannedPages[currentIdx] = currentPage.copy(rotationDegrees = (currentPage.rotationDegrees + 90) % 360)
                    }
                    ScannerActionBtn("Add Page", Icons.Filled.CameraAlt) { reviewMode = false }
                    ScannerActionBtn("Delete", Icons.Filled.Delete) {
                        scannedPages.removeAt(currentIdx)
                        if (scannedPages.isEmpty()) reviewMode = false
                        else selectedReviewIdx = currentIdx.coerceAtMost(scannedPages.lastIndex)
                    }
                }
            }
        }

        croppingPage?.let { target ->
            ImageCropDialog(
                page = target,
                onDismiss = { croppingPage = null },
                onApply = { rect, quad ->
                    val idx = scannedPages.indexOfFirst { it.id == target.id }
                    if (idx >= 0) {
                        scannedPages[idx] = target.copy(cropRect = rect, perspectiveQuad = quad)
                    }
                    croppingPage = null
                }
            )
        }
        return
    }

    // Live CameraX Viewfinder
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    LaunchedEffect(flashMode, boundCamera) {
        imageCapture?.flashMode = flashMode
        boundCamera?.cameraControl?.enableTorch(flashMode == ImageCapture.FLASH_MODE_ON)
    }

    LaunchedEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(flashMode)
                    .build()
                imageCapture = capture

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imgAnalysis ->
                        imgAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            if (autoEdgeEnabled) {
                                val buffer = imageProxy.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                val quad = ImageProcessor.estimateDocumentQuadFromLuma(
                                    bytes,
                                    imageProxy.width,
                                    imageProxy.height
                                )
                                detectedQuad = quad
                            }
                            imageProxy.close()
                        }
                    }

                cameraProvider.unbindAll()
                boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                    analysis
                )
            }.onFailure {
                Toast.makeText(context, "Camera unavailable on this device.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        if (autoEdgeEnabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val p0 = Offset(detectedQuad.topLeftX * size.width, detectedQuad.topLeftY * size.height)
                val p1 = Offset(detectedQuad.topRightX * size.width, detectedQuad.topRightY * size.height)
                val p2 = Offset(detectedQuad.bottomRightX * size.width, detectedQuad.bottomRightY * size.height)
                val p3 = Offset(detectedQuad.bottomLeftX * size.width, detectedQuad.bottomLeftY * size.height)
                val path = Path().apply {
                    moveTo(p0.x, p0.y)
                    lineTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    close()
                }
                drawPath(path, BrandOrange.copy(alpha = 0.14f))
                drawPath(path, BrandOrange, style = Stroke(width = 5f))
                listOf(p0, p1, p2, p3).forEach { pt ->
                    drawCircle(BrandRed, radius = 11f, center = pt)
                }
            }
        }

        // Top Camera Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close scanner", tint = BrandWhite)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { autoEdgeEnabled = !autoEdgeEnabled },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (autoEdgeEnabled) BrandNavy else Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = "Toggle Auto Edge Detection", tint = BrandWhite)
                }
                IconButton(
                    onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    val icon = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                        else -> Icons.Filled.FlashOff
                    }
                    Icon(icon, contentDescription = "Toggle Flash", tint = BrandWhite)
                }
            }
        }

        // Bottom Capture Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (autoEdgeEnabled) "Align document inside orange frame" else "Manual scan mode",
                style = MaterialTheme.typography.bodySmall,
                color = BrandWhite.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Scanned batch counter
                Box(
                    modifier = Modifier
                        .width(88.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1F2636))
                        .clickable(enabled = scannedPages.isNotEmpty()) {
                            selectedReviewIdx = scannedPages.lastIndex
                            reviewMode = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${scannedPages.size} Pages",
                        color = BrandWhite,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Shutter button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(BrandWhite)
                        .border(4.dp, BrandRed, CircleShape)
                        .clickable(enabled = !isCapturing) {
                            val cap = imageCapture ?: return@clickable
                            isCapturing = true
                            val dir = File(context.filesDir, "scans").apply { mkdirs() }
                            val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
                            val opts = ImageCapture.OutputFileOptions.Builder(file).build()
                            val capturedQuad = if (autoEdgeEnabled) detectedQuad else null
                            cap.takePicture(
                                opts,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        isCapturing = false
                                        scannedPages.add(
                                            EditableImagePage(
                                                originalPath = file.absolutePath,
                                                perspectiveQuad = capturedQuad,
                                                filter = ImageFilterType.DOCUMENT
                                            )
                                        )
                                        selectedReviewIdx = scannedPages.lastIndex
                                        reviewMode = true
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isCapturing = false
                                        Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        .testTag("scanner_shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(BrandRed)
                    )
                }

                Button(
                    onClick = { onDoneWithPages(scannedPages.toList()) },
                    enabled = scannedPages.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.width(88.dp).height(48.dp)
                ) {
                    Text("Done", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ScannerActionBtn(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = BrandWhite, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = BrandWhite, style = MaterialTheme.typography.labelSmall)
    }
}
