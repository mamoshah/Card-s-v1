package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CardPosition
import com.example.model.ConfidenceLevel
import com.example.model.TrackingResult
import com.example.model.TrailEffectStyle
import com.example.model.TrailLengthMode
import com.example.ui.components.TrajectoryTrailOverlay
import com.example.ui.theme.*
import com.example.ui.util.VideoDisplayGeometry
import com.example.video.VideoMetadata
import com.example.viewmodel.ScrubberPlaybackState
import com.example.viewmodel.TargetSelectionState
import java.util.Locale

/**
 * Dedicated Full-Screen Landscape Video Details Screen.
 *
 * Implements:
 * 1. Dedicated full-screen landscape layout maximizing the usable viewing area.
 * 2. Clear Back and Close controls to return to the previous screen while preserving application state.
 * 3. Video preview inside a dedicated high-efficiency frame displaying the video in landscape orientation
 *    with correct aspect ratio (ContentScale.Fit) and zero stretching, cropping, or distortion.
 * 4. Generous viewing frame to inspect the three cards (Left, Middle, Right) and their movement clearly.
 * 5. Video playback controls placed directly inside/overlaid on the video frame:
 *    - Play and Pause
 *    - Seek bar with current playback time and total duration (mm:ss.S) + frame index
 *    - Frame-by-frame navigation (Previous Frame / Next Frame)
 *    - Playback speed selector (0.25x, 0.5x, 1x, 2x)
 *    - Control to show or hide playback controls for completely unobstructed viewing
 *    - Clear Back and Close controls to exit the details page
 * 6. Interactive 3-Card Slot Position Guides (Pos 1 Left, Pos 2 Middle, Pos 3 Right)
 * 7. Full respect for system bars and WindowInsets.safeDrawing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDetailsScreen(
    videoTitle: String,
    metadata: VideoMetadata?,
    result: TrackingResult?,
    selectionState: TargetSelectionState?,
    playbackState: ScrubberPlaybackState,
    onSeek: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onTrailEffectChange: (TrailEffectStyle) -> Unit,
    onToggleBoundingBox: () -> Unit,
    onToggleArrow: () -> Unit,
    onToggleTelemetryHud: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    var showSlotGuides by remember { mutableStateOf(true) }
    var showTechMetadataSheet by remember { mutableStateOf(false) }
    var showPlaybackControls by remember { mutableStateOf(true) }

    // Resolve current frame bitmap and metadata
    val totalFrames = result?.totalFrames ?: 1
    val currentFrameIndex = if (result != null) {
        playbackState.currentFrameIndex.coerceIn(0, totalFrames - 1)
    } else {
        0
    }

    val currentTrackedFrame = result?.trackedFrames?.getOrNull(currentFrameIndex)
    val currentBitmap: Bitmap? = currentTrackedFrame?.bitmap
        ?: result?.trackedFrames?.lastOrNull()?.bitmap
        ?: selectionState?.firstFrame

    val videoWidth = metadata?.width ?: currentBitmap?.width ?: 1280
    val videoHeight = metadata?.height ?: currentBitmap?.height ?: 720
    val totalDurationMs = metadata?.durationMs ?: (totalFrames * 33L)
    val currentTimestampMs = currentTrackedFrame?.timestampMs ?: ((currentFrameIndex * totalDurationMs) / totalFrames.coerceAtLeast(1))

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000f
        val minutes = (totalSec / 60).toInt()
        val seconds = totalSec % 60
        return String.format(Locale.US, "%02d:%04.1f", minutes, seconds)
    }

    Scaffold(
        containerColor = Color(0xFF07090E),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { insetsPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(insetsPadding)
                .background(Color(0xFF07090E))
                .testTag("video_details_screen")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Top Header Bar (Title, Metadata badges, Controls toggle, Close & Back buttons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back button + Close button + Video Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onBack,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = DarkSurfaceElevated,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("video_details_back_btn")
                                .testTag("video_details_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(16.dp),
                                tint = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Back",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceElevated)
                                .testTag("video_details_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Video Details",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = videoTitle,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = "Full-Screen Inspection • فحص حركة الكروت",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }

                    // Metadata Chips & Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Resolution Badge
                        Surface(
                            color = DarkSurface,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, DarkSurfaceBorder)
                        ) {
                            Text(
                                text = "${videoWidth}x${videoHeight}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = NeonCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        // Duration / Frame Count Badge
                        Surface(
                            color = DarkSurface,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, DarkSurfaceBorder)
                        ) {
                            Text(
                                text = "${formatDuration(totalDurationMs)} • $totalFrames frames",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        // Slot target indicator
                        if (result != null) {
                            Surface(
                                color = EmeraldAccent.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "FINAL: ${result.finalSlot}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = EmeraldAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Show/Hide Playback Controls inside video frame
                        IconButton(
                            onClick = { showPlaybackControls = !showPlaybackControls },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (showPlaybackControls) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceElevated)
                                .testTag("details_toggle_controls_visibility_btn")
                        ) {
                            Icon(
                                imageVector = if (showPlaybackControls) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (showPlaybackControls) "Hide Controls" else "Show Controls",
                                tint = if (showPlaybackControls) NeonCyan else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Toggle 3-Card Slot Guides
                        IconButton(
                            onClick = { showSlotGuides = !showSlotGuides },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (showSlotGuides) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceElevated)
                                .testTag("toggle_slot_guides_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = "Toggle Slot Guides",
                                tint = if (showSlotGuides) NeonCyan else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Technical Specs Sheet Button
                        IconButton(
                            onClick = { showTechMetadataSheet = !showTechMetadataSheet },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (showTechMetadataSheet) EmeraldAccent.copy(alpha = 0.2f) else DarkSurfaceElevated)
                                .testTag("toggle_tech_specs_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Technical Specs",
                                tint = if (showTechMetadataSheet) EmeraldAccent else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 2. Dedicated Video Frame with Overlaid Controls (Large, Aspect-Fit, High Visibility)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, DarkSurfaceBorder, RoundedCornerShape(14.dp))
                        .testTag("video_details_frame_card"),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showPlaybackControls = !showPlaybackControls
                            }
                            .testTag("video_details_viewport_box")
                    ) {
                        if (currentBitmap != null) {
                            // Video Background Image rendered with ContentScale.Fit (no cropping, no stretching)
                            Image(
                                bitmap = currentBitmap.asImageBitmap(),
                                contentDescription = "Full Screen Video Frame",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            val containerW = constraints.maxWidth.toFloat()
                            val containerH = constraints.maxHeight.toFloat()
                            val fitRect = VideoDisplayGeometry.calculateFitRect(
                                Size(containerW, containerH),
                                currentBitmap.width,
                                currentBitmap.height
                            )

                            val density = LocalDensity.current

                            // Hardware-accelerated rich kinematic trajectory overlay
                            if (result != null) {
                                TrajectoryTrailOverlay(
                                    trackedFrames = result.trackedFrames,
                                    currentFrameIndex = currentFrameIndex,
                                    trailEffect = playbackState.trailEffect,
                                    trailLength = TrailLengthMode.FULL,
                                    showBoundingBox = playbackState.showBoundingBox,
                                    showArrow = playbackState.showArrow,
                                    showTelemetryHud = playbackState.showTelemetryHud,
                                    peakSpeed = result.peakSpeed,
                                    imageWidth = currentBitmap.width,
                                    imageHeight = currentBitmap.height,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // 3-Card Slot Position Guides Overlay (Pos 1 Left <0.42, Pos 2 Middle 0.42-0.58, Pos 3 Right >0.58)
                            if (showSlotGuides) {
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
                                    val cardPositions = listOf(CardPosition.LEFT, CardPosition.MIDDLE, CardPosition.RIGHT)
                                    val activeSlot = currentTrackedFrame?.let { CardPosition.fromX(it.center.x) }
                                        ?: CardPosition.MIDDLE

                                    cardPositions.forEach { pos ->
                                        val rect = pos.getPreconfiguredRect()
                                        val isCurrentSlot = (pos == activeSlot)

                                        val cardLeftDp = with(density) { (rect.left * fitRect.width).toDp() }
                                        val cardTopDp = with(density) { (rect.top * fitRect.height).toDp() }
                                        val cardWidthDp = with(density) { (rect.width * fitRect.width).toDp() }
                                        val cardHeightDp = with(density) { (rect.height * fitRect.height).toDp() }

                                        // Slot Boundary Box
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .offset(x = cardLeftDp, y = cardTopDp)
                                                .size(width = cardWidthDp, height = cardHeightDp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                    width = if (isCurrentSlot) 2.dp else 1.dp,
                                                    color = if (isCurrentSlot) EmeraldAccent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.25f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                        )

                                        // Top Slot Badge (1 Left, 2 Middle, 3 Right)
                                        val badgeWidth = 36.dp
                                        val badgeHeight = 22.dp
                                        val badgeLeft = cardLeftDp + (cardWidthDp - badgeWidth) / 2f
                                        val badgeTop = (cardTopDp - badgeHeight - 4.dp).coerceAtLeast(2.dp)

                                        Surface(
                                            color = if (isCurrentSlot) EmeraldAccent.copy(alpha = 0.90f) else Color.Black.copy(alpha = 0.75f),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, if (isCurrentSlot) EmeraldAccent else Color.White.copy(alpha = 0.5f)),
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .offset(x = badgeLeft, y = badgeTop)
                                                .size(width = badgeWidth, height = badgeHeight)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${pos.id}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (isCurrentSlot) Color(0xFF032210) else Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Telemetry HUD Chip (Top-Right of video viewport)
                            if (result != null && playbackState.showTelemetryHud && currentTrackedFrame != null) {
                                Surface(
                                    color = Color(0xCC0E131F),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, DarkSurfaceBorder),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (currentTrackedFrame.isOccluded) Color(0xFFFFAB00)
                                                    else Color(0xFF00E676)
                                                )
                                        )
                                        Text(
                                            text = if (currentTrackedFrame.isOccluded) "OCCLUSION" else "FLOW LOCKED",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (currentTrackedFrame.isOccluded) Color(0xFFFFAB00) else Color(0xFF00E676)
                                        )
                                        Text(
                                            text = "X=${(currentTrackedFrame.center.x * 100).toInt()}%",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = "SPD: ${(currentTrackedFrame.speed * 100).toInt()}%",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }

                            // 3. Playback Controls Directly Overlaid Inside the Video Frame
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showPlaybackControls && result != null,
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Surface(
                                    color = Color(0xDC0C111E),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, DarkSurfaceBorder),
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        // Row 1: Time, Frame Index, Scrubber Slider, and Remaining Time
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = formatDuration(currentTimestampMs),
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = NeonCyan
                                            )

                                            // Timeline Scrubber Slider
                                            Slider(
                                                value = currentFrameIndex.toFloat(),
                                                onValueChange = { onSeek(it.toInt()) },
                                                valueRange = 0f..(totalFrames - 1).toFloat(),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = NeonCyan,
                                                    activeTrackColor = NeonCyan,
                                                    inactiveTrackColor = DarkSurfaceBorder
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("details_frame_scrubber_slider")
                                            )

                                            Text(
                                                text = "${formatDuration(totalDurationMs)} (F${currentFrameIndex + 1}/$totalFrames)",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = TextSecondary
                                            )
                                        }

                                        // Row 2: Play/Pause, Step Frames, Speed Selector, Overlay Toggles, and Hide Button
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            // Left group: Playback & Frame Stepping
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                // Play / Pause Button
                                                IconButton(
                                                    onClick = onTogglePlay,
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(NeonCyan.copy(alpha = 0.20f))
                                                        .testTag("details_playback_toggle_btn")
                                                ) {
                                                    Icon(
                                                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                                        tint = NeonCyan,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                // Step Backward 1 Frame
                                                IconButton(
                                                    onClick = { onSeek(currentFrameIndex - 1) },
                                                    modifier = Modifier.size(30.dp),
                                                    enabled = currentFrameIndex > 0
                                                ) {
                                                    Icon(
                                                        Icons.Default.SkipPrevious,
                                                        contentDescription = "Previous Frame",
                                                        tint = if (currentFrameIndex > 0) TextPrimary else TextMuted,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                // Step Forward 1 Frame
                                                IconButton(
                                                    onClick = { onSeek(currentFrameIndex + 1) },
                                                    modifier = Modifier.size(30.dp),
                                                    enabled = currentFrameIndex < totalFrames - 1
                                                ) {
                                                    Icon(
                                                        Icons.Default.SkipNext,
                                                        contentDescription = "Next Frame",
                                                        tint = if (currentFrameIndex < totalFrames - 1) TextPrimary else TextMuted,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            // Center group: Playback speed options (0.25x, 0.5x, 1x, 2x)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                listOf(0.25f, 0.5f, 1.0f, 2.0f).forEach { speed ->
                                                    val isSelected = playbackState.playbackSpeed == speed
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = { onSpeedChange(speed) },
                                                        label = { Text("${if (speed == 1.0f) "1" else if (speed == 2.0f) "2" else speed}x", fontSize = 10.sp) },
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = NeonCyan.copy(alpha = 0.25f),
                                                            selectedLabelColor = NeonCyan,
                                                            containerColor = DarkSurfaceElevated,
                                                            labelColor = TextSecondary
                                                        ),
                                                        modifier = Modifier.height(26.dp)
                                                    )
                                                }
                                            }

                                            // Right group: Overlay Toggles & Hide Controls
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                // Target Crosshair / Box Toggle
                                                IconButton(
                                                    onClick = onToggleBoundingBox,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Add,
                                                        contentDescription = "Target Indicator",
                                                        tint = if (playbackState.showBoundingBox) Color(0xFFE040FB) else TextMuted,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                // Direction Arrow Toggle
                                                IconButton(
                                                    onClick = onToggleArrow,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Navigation,
                                                        contentDescription = "Arrow",
                                                        tint = if (playbackState.showArrow) EmeraldAccent else TextMuted,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                // Telemetry HUD Toggle
                                                IconButton(
                                                    onClick = onToggleTelemetryHud,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Speed,
                                                        contentDescription = "Telemetry HUD",
                                                        tint = if (playbackState.showTelemetryHud) Color(0xFFFFD600) else TextMuted,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                VerticalDivider(modifier = Modifier.height(18.dp), color = DarkSurfaceBorder)

                                                // Hide Controls overlay button
                                                IconButton(
                                                    onClick = { showPlaybackControls = false },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.KeyboardArrowDown,
                                                        contentDescription = "Hide Controls",
                                                        tint = TextSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Floating "Show Controls" pill when controls are hidden
                            if (!showPlaybackControls && result != null) {
                                Surface(
                                    color = Color(0xAA000000),
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, DarkSurfaceBorder),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 12.dp)
                                        .clickable { showPlaybackControls = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.PlayCircle,
                                            contentDescription = "Show Controls",
                                            tint = NeonCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Show Controls • إظهار التحكم",
                                            fontSize = 11.sp,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                }
            }

            // 4. Slide-in Technical Specs Overlay Card
            AnimatedVisibility(
                visible = showTechMetadataSheet,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 54.dp, end = 16.dp)
            ) {
                Surface(
                    color = Color(0xFA141A29),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Video Metadata & Specs",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = NeonCyan
                            )
                            IconButton(
                                onClick = { showTechMetadataSheet = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }

                        HorizontalDivider(color = DarkSurfaceBorder)

                        Text("• Container: ${metadata?.mimeType ?: "video/mp4"}", fontSize = 11.sp, color = TextPrimary)
                        Text("• Dimensions: ${videoWidth} x ${videoHeight} px", fontSize = 11.sp, color = TextPrimary)
                        Text("• Rotation Metadata: ${metadata?.rotation ?: 0}° (Corrected)", fontSize = 11.sp, color = TextPrimary)
                        Text("• Duration: ${formatDuration(totalDurationMs)} ($totalFrames frames)", fontSize = 11.sp, color = TextPrimary)
                        Text("• Sampling FPS: 20-30 fps", fontSize = 11.sp, color = TextPrimary)

                        if (result != null) {
                            HorizontalDivider(color = DarkSurfaceBorder)
                            Text("• Tracking Algorithm: Pyramidal Lucas-Kanade + ZNCC", fontSize = 11.sp, color = EmeraldAccent)
                            Text("• Average Confidence: ${(result.averageConfidence * 100).toInt()}% (${result.confidenceLevel})", fontSize = 11.sp, color = TextPrimary)
                            Text("• Total Occlusions: ${result.occlusionEventsCount}", fontSize = 11.sp, color = TextPrimary)
                            Text("• Peak Speed: %.2f screens/sec".format(result.peakSpeed * 30f), fontSize = 11.sp, color = TextPrimary)
                            Text("• Final Resting Slot: ${result.finalSlot}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldAccent)
                        }
                    }
                }
            }
        }
    }
}
