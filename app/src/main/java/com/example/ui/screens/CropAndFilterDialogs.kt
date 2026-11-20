package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.image.ImageProcessor
import com.example.domain.model.EditableImagePage
import com.example.domain.model.ImageFilterType
import com.example.domain.model.NormalizedCropRect
import com.example.domain.model.NormalizedQuad
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandOrange
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandWhite
import kotlin.math.hypot

@Composable
fun ImageCropDialog(
    page: EditableImagePage,
    onDismiss: () -> Unit,
    onApply: (NormalizedCropRect, NormalizedQuad?) -> Unit
) {
    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var usePerspective by remember { mutableStateOf(page.perspectiveQuad != null) }

    var cropLeft by remember { mutableFloatStateOf(page.cropRect.left) }
    var cropTop by remember { mutableFloatStateOf(page.cropRect.top) }
    var cropRight by remember { mutableFloatStateOf(page.cropRect.right) }
    var cropBottom by remember { mutableFloatStateOf(page.cropRect.bottom) }

    var quad by remember {
        mutableStateOf(page.perspectiveQuad ?: NormalizedQuad(0.06f, 0.06f, 0.94f, 0.06f, 0.94f, 0.94f, 0.06f, 0.94f))
    }

    LaunchedEffect(page.id, page.rotationDegrees) {
        val raw = ImageProcessor.renderProcessedBitmap(
            page = page.copy(cropRect = NormalizedCropRect(), perspectiveQuad = null, filter = ImageFilterType.ORIGINAL, brightness = 0f, contrast = 1f, saturation = 1f),
            maxDimension = 1000,
            applyFilters = false
        )
        baseBitmap = raw
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0E1118)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = BrandWhite)
                    }
                    Text(
                        text = if (usePerspective) "Perspective & Crop" else "Crop Image",
                        style = MaterialTheme.typography.titleLarge,
                        color = BrandWhite
                    )
                    IconButton(
                        onClick = {
                            if (usePerspective) {
                                onApply(NormalizedCropRect(), quad)
                            } else {
                                onApply(
                                    NormalizedCropRect(cropLeft, cropTop, cropRight, cropBottom),
                                    null
                                )
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Apply Crop", tint = BrandOrange)
                    }
                }

                // Mode Switch + Auto Crop
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = !usePerspective,
                            onClick = { usePerspective = false },
                            label = { Text("Box Crop") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandNavy,
                                selectedLabelColor = BrandWhite,
                                labelColor = BrandWhite
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = usePerspective,
                            onClick = { usePerspective = true },
                            label = { Text("4-Corner Perspective") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandRed,
                                selectedLabelColor = BrandWhite,
                                labelColor = BrandWhite
                            )
                        )
                    }
                    item {
                        Button(
                            onClick = {
                                val bmp = baseBitmap ?: return@Button
                                val (detectedRect, detectedQuad) = ImageProcessor.autoDetectCropAndQuad(bmp)
                                cropLeft = detectedRect.left
                                cropTop = detectedRect.top
                                cropRight = detectedRect.right
                                cropBottom = detectedRect.bottom
                                quad = detectedQuad
                                Toast.makeText(
                                    context,
                                    if (detectedRect.isFull) "No borders detected — full image kept"
                                    else "Auto-detected document borders!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandOrange, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Auto Crop", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    item {
                        IconButton(
                            onClick = {
                                cropLeft = 0f; cropTop = 0f; cropRight = 1f; cropBottom = 1f
                                quad = NormalizedQuad(0.05f, 0.05f, 0.95f, 0.05f, 0.95f, 0.95f, 0.05f, 0.95f)
                            }
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reset Crop", tint = BrandWhite)
                        }
                    }
                }

                // Interactive Crop Canvas
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = baseBitmap
                    if (bmp == null) {
                        CircularProgressIndicator(color = BrandRed)
                    } else {
                        val containerW = constraints.maxWidth.toFloat()
                        val containerH = constraints.maxHeight.toFloat()
                        val scale = minOf(containerW / bmp.width, containerH / bmp.height)
                        val drawW = bmp.width * scale
                        val drawH = bmp.height * scale
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val boxWidthDp = with(density) { drawW.toDp() }
                        val boxHeightDp = with(density) { drawH.toDp() }

                        var activeHandle by remember { mutableIntStateOf(-1) }

                        Box(
                            modifier = Modifier
                                .size(boxWidthDp, boxHeightDp)
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Image to crop",
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize()
                            )
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(usePerspective, drawW, drawH) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val nx = offset.x / drawW
                                                val ny = offset.y / drawH
                                                activeHandle = if (usePerspective) {
                                                    val pts = listOf(
                                                        quad.topLeftX to quad.topLeftY,
                                                        quad.topRightX to quad.topRightY,
                                                        quad.bottomRightX to quad.bottomRightY,
                                                        quad.bottomLeftX to quad.bottomLeftY
                                                    )
                                                    pts.indices.minByOrNull { idx ->
                                                        hypot(pts[idx].first - nx, pts[idx].second - ny)
                                                    } ?: -1
                                                } else {
                                                    val pts = listOf(
                                                        cropLeft to cropTop,
                                                        cropRight to cropTop,
                                                        cropRight to cropBottom,
                                                        cropLeft to cropBottom
                                                    )
                                                    pts.indices.minByOrNull { idx ->
                                                        hypot(pts[idx].first - nx, pts[idx].second - ny)
                                                    } ?: -1
                                                }
                                            },
                                            onDragEnd = { activeHandle = -1 },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val dx = dragAmount.x / drawW
                                                val dy = dragAmount.y / drawH
                                                if (usePerspective) {
                                                    quad = when (activeHandle) {
                                                        0 -> quad.copy(
                                                            topLeftX = (quad.topLeftX + dx).coerceIn(0f, 0.7f),
                                                            topLeftY = (quad.topLeftY + dy).coerceIn(0f, 0.7f)
                                                        )
                                                        1 -> quad.copy(
                                                            topRightX = (quad.topRightX + dx).coerceIn(0.3f, 1f),
                                                            topRightY = (quad.topRightY + dy).coerceIn(0f, 0.7f)
                                                        )
                                                        2 -> quad.copy(
                                                            bottomRightX = (quad.bottomRightX + dx).coerceIn(0.3f, 1f),
                                                            bottomRightY = (quad.bottomRightY + dy).coerceIn(0.3f, 1f)
                                                        )
                                                        3 -> quad.copy(
                                                            bottomLeftX = (quad.bottomLeftX + dx).coerceIn(0f, 0.7f),
                                                            bottomLeftY = (quad.bottomLeftY + dy).coerceIn(0.3f, 1f)
                                                        )
                                                        else -> quad
                                                    }
                                                } else {
                                                    when (activeHandle) {
                                                        0 -> {
                                                            cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.12f)
                                                            cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.12f)
                                                        }
                                                        1 -> {
                                                            cropRight = (cropRight + dx).coerceIn(cropLeft + 0.12f, 1f)
                                                            cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.12f)
                                                        }
                                                        2 -> {
                                                            cropRight = (cropRight + dx).coerceIn(cropLeft + 0.12f, 1f)
                                                            cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.12f, 1f)
                                                        }
                                                        3 -> {
                                                            cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.12f)
                                                            cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.12f, 1f)
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                            ) {
                                if (usePerspective) {
                                    val p0 = Offset(quad.topLeftX * size.width, quad.topLeftY * size.height)
                                    val p1 = Offset(quad.topRightX * size.width, quad.topRightY * size.height)
                                    val p2 = Offset(quad.bottomRightX * size.width, quad.bottomRightY * size.height)
                                    val p3 = Offset(quad.bottomLeftX * size.width, quad.bottomLeftY * size.height)
                                    val path = Path().apply {
                                        moveTo(p0.x, p0.y)
                                        lineTo(p1.x, p1.y)
                                        lineTo(p2.x, p2.y)
                                        lineTo(p3.x, p3.y)
                                        close()
                                    }
                                    drawPath(path, Color(0x33102F83))
                                    drawPath(path, BrandOrange, style = Stroke(width = 4f))
                                    listOf(p0, p1, p2, p3).forEach { pt ->
                                        drawCircle(BrandWhite, radius = 18f, center = pt)
                                        drawCircle(BrandRed, radius = 12f, center = pt)
                                    }
                                } else {
                                    val l = cropLeft * size.width
                                    val t = cropTop * size.height
                                    val r = cropRight * size.width
                                    val b = cropBottom * size.height
                                    drawRect(Color.Black.copy(alpha = 0.45f), size = Size(size.width, t))
                                    drawRect(Color.Black.copy(alpha = 0.45f), topLeft = Offset(0f, b), size = Size(size.width, size.height - b))
                                    drawRect(Color.Black.copy(alpha = 0.45f), topLeft = Offset(0f, t), size = Size(l, b - t))
                                    drawRect(Color.Black.copy(alpha = 0.45f), topLeft = Offset(r, t), size = Size(size.width - r, b - t))
                                    drawRect(
                                        color = BrandOrange,
                                        topLeft = Offset(l, t),
                                        size = Size(r - l, b - t),
                                        style = Stroke(width = 4f)
                                    )
                                    listOf(
                                        Offset(l, t), Offset(r, t),
                                        Offset(r, b), Offset(l, b)
                                    ).forEach { pt ->
                                        drawCircle(BrandWhite, radius = 18f, center = pt)
                                        drawCircle(BrandNavy, radius = 12f, center = pt)
                                    }
                                }
                            }
                        }
                    }
                }

                // Aspect ratio quick presets (Box Crop)
                if (!usePerspective) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            "Free" to null,
                            "1:1" to 1f,
                            "4:3" to (4f / 3f),
                            "A4" to (210f / 297f),
                            "16:9" to (16f / 9f)
                        )
                        items(presets) { (label, ratio) ->
                            OutlinedButton(
                                onClick = {
                                    if (ratio == null) {
                                        cropLeft = 0.05f; cropTop = 0.05f; cropRight = 0.95f; cropBottom = 0.95f
                                    } else {
                                        val bmp = baseBitmap ?: return@OutlinedButton
                                        val imgAspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
                                        val normRatio = ratio / imgAspect
                                        if (normRatio > 1f) {
                                            val h = (0.85f / normRatio).coerceIn(0.2f, 0.9f)
                                            cropLeft = 0.075f; cropRight = 0.925f
                                            cropTop = (1f - h) / 2f; cropBottom = 1f - cropTop
                                        } else {
                                            val w = (0.85f * normRatio).coerceIn(0.2f, 0.9f)
                                            cropTop = 0.075f; cropBottom = 0.925f
                                            cropLeft = (1f - w) / 2f; cropRight = 1f - cropLeft
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(label, color = BrandWhite)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageFilterStudioDialog(
    page: EditableImagePage,
    onDismiss: () -> Unit,
    onApply: (ImageFilterType, Float, Float, Float, Boolean) -> Unit
) {
    var filter by remember(page.id) { mutableStateOf(page.filter) }
    var brightness by remember(page.id) { mutableFloatStateOf(page.brightness) }
    var contrast by remember(page.id) { mutableFloatStateOf(page.contrast) }
    var saturation by remember(page.id) { mutableFloatStateOf(page.saturation) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(page.id, filter, brightness, contrast, saturation) {
        val updated = page.copy(
            filter = filter,
            brightness = brightness,
            contrast = contrast,
            saturation = saturation
        )
        previewBitmap = ImageProcessor.renderProcessedBitmap(updated, maxDimension = 900, applyFilters = true)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                    Text("Filters & Enhance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = {
                            filter = ImageFilterType.ORIGINAL
                            brightness = 0f; contrast = 1f; saturation = 1f
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reset Filters")
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF151923)),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = previewBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Filtered Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    } else {
                        CircularProgressIndicator(color = BrandRed)
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ImageFilterType.entries) { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(f.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandNavy,
                                selectedLabelColor = BrandWhite
                            )
                        )
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SliderRow("Brightness", brightness, -80f..80f) { brightness = it }
                    SliderRow("Contrast", contrast, 0.5f..1.8f) { contrast = it }
                    SliderRow("Saturation", saturation, 0f..2f) { saturation = it }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onApply(filter, brightness, contrast, saturation, true) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Apply to All")
                    }
                    Button(
                        onClick = { onApply(filter, brightness, contrast, saturation, false) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(84.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = BrandRed, activeTrackColor = BrandNavy),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ImageFullPreviewDialog(
    pages: List<EditableImagePage>,
    initialPageId: String,
    onDismiss: () -> Unit
) {
    var index by remember {
        mutableIntStateOf(pages.indexOfFirst { it.id == initialPageId }.coerceAtLeast(0))
    }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    val currentPage = pages.getOrNull(index)

    LaunchedEffect(
        currentPage?.id,
        currentPage?.rotationDegrees,
        currentPage?.filter,
        currentPage?.cropRect,
        currentPage?.perspectiveQuad,
        currentPage?.brightness,
        currentPage?.contrast,
        currentPage?.saturation
    ) {
        bmp = currentPage?.let { ImageProcessor.renderProcessedBitmap(it, maxDimension = 1200, applyFilters = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = BrandWhite)
                    }
                    Text(
                        text = "Page ${index + 1} of ${pages.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = BrandWhite
                    )
                    Spacer(Modifier.size(48.dp))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val img = bmp
                    if (img != null) {
                        Image(
                            bitmap = img.asImageBitmap(),
                            contentDescription = "Page preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(color = BrandRed)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (index > 0) index-- },
                        enabled = index > 0,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(BrandNavy.copy(alpha = if (index > 0) 0.9f else 0.3f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous", tint = BrandWhite)
                    }
                    IconButton(
                        onClick = { if (index < pages.lastIndex) index++ },
                        enabled = index < pages.lastIndex,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(BrandNavy.copy(alpha = if (index < pages.lastIndex) 0.9f else 0.3f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", tint = BrandWhite)
                    }
                }
            }
        }
    }
}
