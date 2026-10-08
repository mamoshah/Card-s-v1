package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NormalizedRect
import com.example.ui.theme.*
import com.example.ui.util.VideoDisplayGeometry
import com.example.viewmodel.TargetSelectionState
import com.example.viewmodel.TargetSizeOption
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetSelectionScreen(
    videoTitle: String,
    selectionState: TargetSelectionState,
    onFrameTapped: (Float, Float) -> Unit,
    onTargetSizeChanged: (Float) -> Unit,
    onConfirmTarget: () -> Unit,
    onBack: () -> Unit,
    onBoxDrawn: (NormalizedRect) -> Unit = {}
) {
    val firstFrame = selectionState.firstFrame
    var displaySize by remember { mutableStateOf(IntSize.Zero) }
    val hasSelectedTarget = selectionState.selectedBox != null

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Target Selection • تحديد الهدف",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = videoTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    if (hasSelectedTarget) {
                        Surface(
                            color = EmeraldAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.5f)),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldAccent)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "TARGET LOCKED",
                                    color = EmeraldAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Interactive Video Frame Canvas (Tap to Center Card)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { displaySize = it }
                            .pointerInput(Unit) {
                                detectTapGestures { tapOffset ->
                                    if (displaySize.width > 0 && displaySize.height > 0 && firstFrame != null) {
                                        val fitRect = VideoDisplayGeometry.calculateFitRect(
                                            Size(displaySize.width.toFloat(), displaySize.height.toFloat()),
                                            firstFrame.width,
                                            firstFrame.height
                                        )
                                        val normPt = VideoDisplayGeometry.toNormalizedPoint(tapOffset, fitRect)
                                        onFrameTapped(normPt.x, normPt.y)
                                    }
                                }
                            }
                            .testTag("interactive_frame_canvas"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (firstFrame != null) {
                            Image(
                                bitmap = firstFrame.asImageBitmap(),
                                contentDescription = "First Video Frame",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            // Canvas Overlay for Locked Card Target Reticle
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                if (w <= 0 || h <= 0) return@Canvas

                                val targetBox = selectionState.selectedBox
                                if (targetBox != null) {
                                    val fitRect = VideoDisplayGeometry.calculateFitRect(
                                        size,
                                        firstFrame.width,
                                        firstFrame.height
                                    )
                                    val left = fitRect.left + targetBox.left * fitRect.width
                                    val top = fitRect.top + targetBox.top * fitRect.height
                                    val width = targetBox.width * fitRect.width
                                    val height = targetBox.height * fitRect.height
                                    val centerX = left + width / 2f
                                    val centerY = top + height / 2f

                                    // Outer Box Stroke (Emerald when locked)
                                    drawRoundRect(
                                        color = EmeraldAccent,
                                        topLeft = Offset(left, top),
                                        size = Size(width, height),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                                        style = Stroke(width = 3.dp.toPx())
                                    )

                                    // Subtle inner glowing fill
                                    drawRoundRect(
                                        color = EmeraldAccent.copy(alpha = 0.08f),
                                        topLeft = Offset(left, top),
                                        size = Size(width, height),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                                    )

                                    // 4 Corner Brackets
                                    val cornerLen = 18.dp.toPx()
                                    val bracketStroke = 4.dp.toPx()
                                    val cornerColor = Color.White

                                    // Top-Left
                                    drawLine(cornerColor, Offset(left, top), Offset(left + cornerLen, top), bracketStroke)
                                    drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerLen), bracketStroke)
                                    // Top-Right
                                    drawLine(cornerColor, Offset(left + width, top), Offset(left + width - cornerLen, top), bracketStroke)
                                    drawLine(cornerColor, Offset(left + width, top), Offset(left + width, top + cornerLen), bracketStroke)
                                    // Bottom-Left
                                    drawLine(cornerColor, Offset(left, top + height), Offset(left + cornerLen, top + height), bracketStroke)
                                    drawLine(cornerColor, Offset(left, top + height), Offset(left, top + height - cornerLen), bracketStroke)
                                    // Bottom-Right
                                    drawLine(cornerColor, Offset(left + width, top + height), Offset(left + width - cornerLen, top + height), bracketStroke)
                                    drawLine(cornerColor, Offset(left + width, top + height), Offset(left + width, top + height - cornerLen), bracketStroke)

                                    // Center Precision Crosshair
                                    val chSize = 14.dp.toPx()
                                    drawLine(EmeraldAccent, Offset(centerX - chSize, centerY), Offset(centerX + chSize, centerY), 2.dp.toPx())
                                    drawLine(EmeraldAccent, Offset(centerX, centerY - chSize), Offset(centerX, centerY + chSize), 2.dp.toPx())
                                    drawCircle(EmeraldAccent, radius = 4.dp.toPx(), center = Offset(centerX, centerY))
                                }
                            }
                        } else {
                            CircularProgressIndicator(color = NeonCyan)
                        }
                    }
                }

                // Interactive Instructions Banner
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdsClick,
                            contentDescription = null,
                            tint = if (hasSelectedTarget) EmeraldAccent else NeonCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasSelectedTarget) "Target Card Positioned • تم تحديد الكرت" else "Tap on Target Card • اضغط على الكرت المطلوب",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Tap directly on the card to set its center, then choose the Target Size below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Predefined Target Size Options & Dimension Scale
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header row with Target Size title and current percentage
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Target Size • حجم الهدف",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                            }
                            Surface(
                                color = NeonCyan.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${(selectionState.targetSizeScale * 100).toInt()}%",
                                    color = NeonCyan,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Predefined Size Chips (Small, Standard, Large, XL)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TargetSizeOption.values().forEach { option ->
                                val isSelected = abs(selectionState.targetSizeScale - option.scale) < 0.08f
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onTargetSizeChanged(option.scale) },
                                    label = {
                                        Text(
                                            text = option.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonCyan.copy(alpha = 0.22f),
                                        selectedLabelColor = NeonCyan,
                                        containerColor = DarkSurfaceElevated,
                                        labelColor = TextSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = if (isSelected) NeonCyan else DarkSurfaceBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("size_chip_${option.name.lowercase()}")
                                )
                            }
                        }

                        // Fine-tuning Target Size Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Fine Scale:",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Slider(
                                value = selectionState.targetSizeScale,
                                onValueChange = onTargetSizeChanged,
                                valueRange = 0.6f..1.8f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonCyan,
                                    activeTrackColor = NeonCyan,
                                    inactiveTrackColor = DarkSurfaceBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("box_scale_slider")
                            )
                        }
                    }
                }
            }

            // Bottom CTA
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConfirmTarget,
                    enabled = hasSelectedTarget,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("confirm_target_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasSelectedTarget) EmeraldAccent else NeonCyan,
                        contentColor = Color(0xFF032210)
                    )
                ) {
                    Icon(
                        imageVector = if (hasSelectedTarget) Icons.Default.Lock else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasSelectedTarget) "Lock Target & Start Tracking • بدء التتبع" else "Select Target to Continue",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
