package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
 * Fixed 3-Card Grid Single-Tap Target Selection Screen.
 *
 * 1. Fixed Card Grid & Bounding Boxes:
 *    - Position 1 = Left, Position 2 = Middle, Position 3 = Right.
 *    - Fixed Dark Green outlines around each of the 3 card regions.
 *    - Interactive Dark/Black touch target area inside each card box for single-tap selection.
 *
 * 2. Top Guidance & Selection Header (White Boxes):
 *    - Positioned above each card bounding box, outlined in white.
 *    - Displays guidance numbers 1, 2, 3 corresponding to positions.
 *    - Displays green checkmark ✅ inside the white box when selected.
 *
 * 3. Single-Tap Selection:
 *    - Tapping inside a card or its top indicator immediately locks that card as the target.
 *    - Only one card can be active at a time.
 *
 * 4. Clean UI:
 *    - Target size slider & preset chips removed completely.
 *    - Old manual crosshairs removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetSelectionScreen(
    videoTitle: String,
    selectionState: TargetSelectionState,
    onCardSelected: (CardPosition) -> Unit,
    onConfirmTarget: () -> Unit,
    onBack: () -> Unit,
    onFrameTapped: ((Float, Float) -> Unit)? = null
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Interactive Video Frame Card with Fixed 3-Card Grid
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
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

                            // 1. Touch detector with Aspect-Fit Relative Coordinate Transformation
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(fitRect) {
                                        detectTapGestures { tapOffset ->
                                            val relativeX = ((tapOffset.x - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
                                            val relativeY = ((tapOffset.y - fitRect.top) / fitRect.height).coerceIn(0f, 1f)

                                            val hitPos = CardPosition.findPositionAt(relativeX, relativeY)
                                                ?: when {
                                                    relativeX < 0.50f -> CardPosition.LEFT
                                                    relativeX in 0.50f..0.64f -> CardPosition.MIDDLE
                                                    else -> CardPosition.RIGHT
                                                }

                                            onCardSelected(hitPos)
                                            onFrameTapped?.invoke(relativeX, relativeY)
                                        }
                                    }
                            )

                            // 2. Active Video Viewport Overlay anchored strictly at fitRect
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

                                    // Direct transformation:
                                    // screenX = fitRect.left + relativeX * fitRect.width
                                    // Inside this overlay Box (aligned TopStart at fitRect), local offset is:
                                    val cardLeftDp = with(density) { (cardRect.left * fitRect.width).toDp() }
                                    val cardTopDp = with(density) { (cardRect.top * fitRect.height).toDp() }
                                    val cardWidthDp = with(density) { (cardRect.width * fitRect.width).toDp() }
                                    val cardHeightDp = with(density) { (cardRect.height * fitRect.height).toDp() }

                                    // Top Guidance & Selection Header (White Box)
                                    val whiteBoxWidth = 46.dp.coerceAtMost(cardWidthDp * 0.85f)
                                    val whiteBoxHeight = 28.dp
                                    val whiteBoxLeft = cardLeftDp + (cardWidthDp - whiteBoxWidth) / 2f
                                    val whiteBoxTop = (cardTopDp - whiteBoxHeight - 6.dp).coerceAtLeast(2.dp)

                                    // 1. Top Indicator Box (Outlined in White)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(x = whiteBoxLeft, y = whiteBoxTop)
                                            .size(width = whiteBoxWidth, height = whiteBoxHeight)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Black.copy(alpha = 0.90f))
                                            .border(
                                                width = 2.dp,
                                                color = Color.White,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable {
                                                onCardSelected(pos)
                                                onFrameTapped?.invoke(pos.defaultCenterX, pos.defaultCenterY)
                                            }
                                            .testTag("top_white_box_${pos.id}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = "✅",
                                                    fontSize = 12.sp
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = "${pos.id}",
                                                    color = EmeraldAccent,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "${pos.id}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    // 2. Card Bounding Box & Interactive Touch Area
                                    // Dark Green outline + Black/Dark interactive tap zone
                                    val darkGreenOutline = Color(0xFF1B5E20)
                                    val darkGreenBorderColor = if (isSelected) EmeraldAccent else darkGreenOutline

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(x = cardLeftDp, y = cardTopDp)
                                            .size(width = cardWidthDp, height = cardHeightDp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) Color(0xAA051A0E) else Color(0x99000000)
                                            )
                                            .border(
                                                width = if (isSelected) 3.dp else 2.5.dp,
                                                color = darkGreenBorderColor,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                onCardSelected(pos)
                                                onFrameTapped?.invoke(pos.defaultCenterX, pos.defaultCenterY)
                                            }
                                            .testTag("card_touch_zone_${pos.id}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Surface(
                                                color = EmeraldAccent.copy(alpha = 0.20f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldAccent)
                                            ) {
                                                Text(
                                                    text = "TARGET",
                                                    color = EmeraldAccent,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = NeonCyan
                            )
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
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = if (hasSelectedTarget) EmeraldAccent.copy(alpha = 0.2f) else DarkSurfaceElevated,
                            shape = CircleShape,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (hasSelectedTarget) "✅" else "🎯",
                                    fontSize = 18.sp
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasSelectedTarget && selectedPosition != null) {
                                    "Target Selected: ${selectedPosition.nameEn} • تم تحديد الكرت"
                                } else {
                                    "Select Target Card • اضغط على الكرت المطلوب"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = if (hasSelectedTarget && selectedPosition != null) {
                                    "Position ${selectedPosition.id} (${selectedPosition.nameEn} • ${selectedPosition.nameAr}) locked. Tap any card to switch."
                                } else {
                                    "Tap directly inside any of the 3 card boxes (1 Left, 2 Middle, 3 Right) to select the target."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Bottom CTA Button (Without crosshair)
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
                        containerColor = if (hasSelectedTarget) EmeraldAccent else DarkSurfaceBorder,
                        contentColor = if (hasSelectedTarget) Color(0xFF032210) else TextSecondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start Tracking",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasSelectedTarget) {
                            "Lock Target & Start Tracking • بدء التتبع"
                        } else {
                            "Select Target Card to Continue"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
