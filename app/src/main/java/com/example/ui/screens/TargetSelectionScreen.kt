package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CardPosition
import com.example.ui.theme.*
import com.example.ui.util.VideoDisplayGeometry
import com.example.viewmodel.TargetSelectionState

/**
 * Fixed 3-Card Grid Single-Tap Target Selection Screen - Landscape Layout.
 *
 * 1. Side-by-side landscape organization:
 *    - Left Panel: Video Frame with fixed 3-Card Grid and top white indicators.
 *    - Right Panel: Target status, direct position selectors, and "Lock Target & Start Tracking" CTA.
 * 2. Position 1 (Left: X < 0.42), Position 2 (Middle: 0.42 <= X <= 0.58), Position 3 (Right: X > 0.58).
 * 3. Aspect-Fit Relative Coordinate Transformation ensuring precision hit detection.
 * 4. "Show Video Details" access.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetSelectionScreen(
    videoTitle: String,
    selectionState: TargetSelectionState,
    onCardSelected: (CardPosition) -> Unit,
    onConfirmTarget: () -> Unit,
    onBack: () -> Unit,
    onFrameTapped: ((Float, Float) -> Unit)? = null,
    onShowVideoDetails: (() -> Unit)? = null
) {
    val firstFrame = selectionState.firstFrame
    val selectedPosition = selectionState.selectedPosition ?: selectionState.selectedBox?.let { box ->
        CardPosition.fromX(box.centerX)
    }
    val hasSelectedTarget = selectionState.selectedBox != null || selectedPosition != null

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
                    if (onShowVideoDetails != null) {
                        FilledTonalButton(
                            onClick = onShowVideoDetails,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = NeonCyan.copy(alpha = 0.2f),
                                contentColor = NeonCyan
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("target_show_video_details_btn")
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Show Video Details", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Show Video Details", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    if (hasSelectedTarget && selectedPosition != null) {
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
                                    text = "POS ${selectedPosition.id} LOCKED",
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==========================================
            // LEFT PANEL: Interactive Video Frame & 3-Card Grid
            // ==========================================
            Card(
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("interactive_frame_canvas")
                ) {
                    if (firstFrame != null) {
                        Image(
                            bitmap = firstFrame.asImageBitmap(),
                            contentDescription = "First Video Frame",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        val containerW = constraints.maxWidth.toFloat()
                        val containerH = constraints.maxHeight.toFloat()
                        val fitRect = VideoDisplayGeometry.calculateFitRect(
                            Size(containerW, containerH),
                            firstFrame.width,
                            firstFrame.height
                        )

                        val density = LocalDensity.current
                        val cardPositions = listOf(CardPosition.LEFT, CardPosition.MIDDLE, CardPosition.RIGHT)

                        // Touch detector with Aspect-Fit Relative Coordinate Transformation
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(fitRect) {
                                    detectTapGestures { tapOffset ->
                                        val relativeX = ((tapOffset.x - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                                        val relativeY = ((tapOffset.y - fitRect.top) / fitRect.height).coerceIn(0f, 1f)

                                        val hitPos = CardPosition.findPositionAt(relativeX, relativeY)
                                            ?: when {
                                                relativeX < 0.42f -> CardPosition.LEFT
                                                relativeX in 0.42f..0.58f -> CardPosition.MIDDLE
                                                else -> CardPosition.RIGHT
                                            }

                                        onCardSelected(hitPos)
                                        onFrameTapped?.invoke(relativeX, relativeY)
                                    }
                                }
                        )

                        // Active Video Viewport Overlay anchored strictly at fitRect
                        val fitLeftDp = with(density) { fitRect.left.toDp() }
                        val fitTopDp = with(density) { fitRect.top.toDp() }
                        val fitWidthDp = with(density) { fitRect.width.toDp() }
                        val fitHeightDp = with(density) { fitRect.height.toDp() }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = fitLeftDp, y = fitTopDp)
                                .size(width = fitWidthDp, height = fitHeightDp)
                        ) {
                            cardPositions.forEach { pos ->
                                val cardRect = pos.getPreconfiguredRect()
                                val isSelected = (selectedPosition == pos)

                                val cardLeftDp = with(density) { (cardRect.left * fitRect.width).toDp() }
                                val cardTopDp = with(density) { (cardRect.top * fitRect.height).toDp() }
                                val cardWidthDp = with(density) { (cardRect.width * fitRect.width).toDp() }
                                val cardHeightDp = with(density) { (cardRect.height * fitRect.height).toDp() }

                                // 1. Fixed Card Region
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(x = cardLeftDp, y = cardTopDp)
                                        .size(width = cardWidthDp, height = cardHeightDp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) Color(0x6600E676)
                                            else Color(0x44000000)
                                        )
                                        .border(
                                            width = if (isSelected) 3.dp else 1.5.dp,
                                            color = if (isSelected) EmeraldAccent else Color(0xFF1B5E20),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            onCardSelected(pos)
                                            onFrameTapped?.invoke(pos.defaultCenterX, pos.defaultCenterY)
                                        }
                                        .testTag("card_region_${pos.id}")
                                )

                                // 2. Top Guidance & Selection Header (White Box)
                                val indicatorWidth = 44.dp
                                val indicatorHeight = 28.dp
                                val indicatorLeft = cardLeftDp + (cardWidthDp - indicatorWidth) / 2f
                                val indicatorTop = (cardTopDp - indicatorHeight - 6.dp).coerceAtLeast(2.dp)

                                Surface(
                                    color = if (isSelected) EmeraldAccent.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.90f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.5.dp,
                                        color = if (isSelected) EmeraldAccent else Color.White
                                    ),
                                    shadowElevation = if (isSelected) 6.dp else 2.dp,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(x = indicatorLeft, y = indicatorTop)
                                        .size(width = indicatorWidth, height = indicatorHeight)
                                        .clickable {
                                            onCardSelected(pos)
                                            onFrameTapped?.invoke(pos.defaultCenterX, pos.defaultCenterY)
                                        }
                                        .testTag("card_indicator_${pos.id}")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isSelected) {
                                            Text(
                                                text = "✅ ${pos.id}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF032210)
                                            )
                                        } else {
                                            Text(
                                                text = "${pos.id}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = NeonCyan)
                        }
                    }
                }
            }

            // ==========================================
            // RIGHT PANEL: Controls, Status & Action CTA
            // ==========================================
            Column(
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Selection Status Banner
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
                            Surface(
                                color = if (hasSelectedTarget) EmeraldAccent.copy(alpha = 0.2f) else DarkSurfaceElevated,
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (hasSelectedTarget) "✅" else "🎯",
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasSelectedTarget && selectedPosition != null) {
                                        "Target: ${selectedPosition.nameEn} (${selectedPosition.id})"
                                    } else {
                                        "Select Target Card"
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (hasSelectedTarget && selectedPosition != null) {
                                        "Position ${selectedPosition.id} (${selectedPosition.nameAr}) locked."
                                    } else {
                                        "Tap any card in the frame or select below."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Direct Position Selection Buttons (1 Left, 2 Middle, 3 Right)
                    Text(
                        text = "Or Select Position Directly:",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(CardPosition.LEFT, CardPosition.MIDDLE, CardPosition.RIGHT).forEach { pos ->
                            val isSelected = selectedPosition == pos
                            Button(
                                onClick = {
                                    onCardSelected(pos)
                                    onFrameTapped?.invoke(pos.defaultCenterX, pos.defaultCenterY)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) EmeraldAccent else DarkSurfaceElevated,
                                    contentColor = if (isSelected) Color(0xFF032210) else TextPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${pos.id}: ${pos.nameEn}",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // Guidance Note
                    Surface(
                        color = Color(0x33101826),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💡 Ground truth slot boundaries: Pos 1 (Left) X < 0.42, Pos 2 (Middle) 0.42-0.58, Pos 3 (Right) X > 0.58.",
                            fontSize = 10.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // CTA Button: Lock Target & Start Tracking
                Button(
                    onClick = onConfirmTarget,
                    enabled = hasSelectedTarget,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(top = 8.dp)
                        .testTag("confirm_target_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasSelectedTarget) EmeraldAccent else DarkSurfaceBorder,
                        contentColor = if (hasSelectedTarget) Color(0xFF032210) else TextSecondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start Tracking",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasSelectedTarget) "Lock Target & Track • بدء التتبع" else "Select Target to Continue",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
