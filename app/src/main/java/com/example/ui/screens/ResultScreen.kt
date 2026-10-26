package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConfidenceLevel
import com.example.model.TrackingResult
import com.example.model.TrailEffectStyle
import com.example.model.TrailLengthMode
import com.example.ui.components.KinematicsDiagnosticsSection
import com.example.ui.components.TrajectoryTrailOverlay
import com.example.ui.theme.*
import com.example.viewmodel.ScrubberPlaybackState

/**
 * Reorganized Landscape Results Interface.
 *
 * Requirements:
 * 1. Side-by-side landscape layout:
 *    - Left Panel: Video player frame with rich trajectory overlay, embedded scrubber,
 *      speed controls, and a prominent "Show Video Details" button.
 *    - Right Panel: Organized telemetry and diagnostics pane (Hero final position,
 *      sequential movement logs, occlusion recovery stats, kinematics, and actions).
 * 2. Simultaneous inspection of video playback and analytical diagnostics without clipping.
 * 3. Prominent, easily accessible "Show Video Details" action opening the full-screen view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    result: TrackingResult,
    playbackState: ScrubberPlaybackState,
    onSeek: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onTrailEffectChange: (TrailEffectStyle) -> Unit,
    onTrailLengthChange: (TrailLengthMode) -> Unit,
    onToggleBoundingBox: () -> Unit,
    onToggleArrow: () -> Unit,
    onToggleTelemetryHud: () -> Unit,
    onRepickTarget: () -> Unit,
    onRerun: () -> Unit,
    onResetToUpload: () -> Unit,
    onShowVideoDetails: () -> Unit = {}
) {
    val currentFrameIndex = playbackState.currentFrameIndex.coerceIn(0, result.trackedFrames.size - 1)
    val currentTrackedFrame = result.trackedFrames[currentFrameIndex]
    val currentBitmap = currentTrackedFrame.bitmap ?: result.trackedFrames.last().bitmap

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Tracking Result • نتائج التتبع",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = result.videoTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onResetToUpload, modifier = Modifier.testTag("result_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = onShowVideoDetails,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NeonCyan.copy(alpha = 0.2f),
                            contentColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("show_video_details_btn")
                    ) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Show Video Details", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Show Video Details", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(onClick = onRepickTarget, modifier = Modifier.testTag("repick_target_top_btn")) {
                        Icon(Icons.Default.EditLocationAlt, contentDescription = "Re-pick Target", tint = NeonCyan)
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
            // LEFT PANEL: Video Player & Scrubbing HUD
            // ==========================================
            Column(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Video Player Frame
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (currentBitmap != null) {
                            // Video frame rendering with ContentScale.Fit
                            Image(
                                bitmap = currentBitmap.asImageBitmap(),
                                contentDescription = "Video frame",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            // Rich Cinematic Trajectory Trails Overlay
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

                            // Overlaid Full-Screen Button
                            FilledTonalButton(
                                onClick = onShowVideoDetails,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xCC111625),
                                    contentColor = NeonCyan
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .testTag("show_video_details_button")
                            ) {
                                Icon(Icons.Default.Fullscreen, contentDescription = "Show Video Details", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Show Video Details", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Overlaid Live Telemetry Status
                            if (playbackState.showTelemetryHud) {
                                Surface(
                                    color = Color(0xCC111625),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
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
                                            text = if (currentTrackedFrame.isOccluded) "OCCLUSION" else "LOCKED",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (currentTrackedFrame.isOccluded) Color(0xFFFFAB00) else Color(0xFF00E676)
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
                        }
                    }
                }

                // 2. Scrubber Timeline & Playback Bar
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DarkSurfaceBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Slider & Frame Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = onTogglePlay,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(NeonCyan.copy(alpha = 0.15f))
                                    .testTag("playback_toggle_btn")
                            ) {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Step backward 1 frame
                            IconButton(
                                onClick = { onSeek(currentFrameIndex - 1) },
                                modifier = Modifier.size(28.dp),
                                enabled = currentFrameIndex > 0
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Frame", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }

                            Slider(
                                value = currentFrameIndex.toFloat(),
                                onValueChange = { onSeek(it.toInt()) },
                                valueRange = 0f..(result.totalFrames - 1).toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonCyan,
                                    activeTrackColor = NeonCyan,
                                    inactiveTrackColor = DarkSurfaceBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("frame_scrubber_slider")
                            )

                            // Step forward 1 frame
                            IconButton(
                                onClick = { onSeek(currentFrameIndex + 1) },
                                modifier = Modifier.size(28.dp),
                                enabled = currentFrameIndex < result.totalFrames - 1
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next Frame", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }

                            Text(
                                text = "${currentFrameIndex + 1}/${result.totalFrames}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = TextPrimary
                            )
                        }

                        // Speed & Overlay Quick Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Speed:", fontSize = 10.sp, color = TextSecondary)
                                listOf(0.25f, 0.5f, 1.0f, 2.0f).forEach { speed ->
                                    val isSelected = playbackState.playbackSpeed == speed
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onSpeedChange(speed) },
                                        label = { Text("${if (speed == 1.0f) "1" else if (speed == 2.0f) "2" else speed}x", fontSize = 9.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = NeonCyan,
                                            containerColor = DarkSurfaceElevated,
                                            labelColor = TextSecondary
                                        ),
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                            }

                            // Quick Overlays
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onToggleBoundingBox, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = "Crosshair", tint = if (playbackState.showBoundingBox) Color(0xFFE040FB) else TextMuted, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = onToggleArrow, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.Navigation, contentDescription = "Arrow", tint = if (playbackState.showArrow) EmeraldAccent else TextMuted, modifier = Modifier.size(14.dp))
                                }
                                IconButton(onClick = onToggleTelemetryHud, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.Speed, contentDescription = "HUD", tint = if (playbackState.showTelemetryHud) Color(0xFFFFD600) else TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                // 3. Prominent "Show Video Details" Full-Screen Launch Button
                FilledTonalButton(
                    onClick = onShowVideoDetails,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = NeonCyan.copy(alpha = 0.16f),
                        contentColor = NeonCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("show_video_details_action_btn")
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Show Video Details • عرض تفاصيل الفيديو", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ==========================================
            // RIGHT PANEL: Diagnostics & Telemetry
            // ==========================================
            LazyColumn(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Hero Final Position Verdict Card
                item {
                    val isFailed = result.isFailedOrUnreliable
                    val borderColor = if (isFailed) Color(0xFFFF1744) else when (result.confidenceLevel) {
                        ConfidenceLevel.HIGH -> EmeraldAccent.copy(alpha = 0.6f)
                        ConfidenceLevel.MEDIUM -> AmberWarning.copy(alpha = 0.6f)
                        ConfidenceLevel.LOW -> CoralAlert.copy(alpha = 0.6f)
                    }

                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, borderColor)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = if (isFailed) "TRACKING UNRELIABLE • غير موثوق" else "FINAL POSITION • الموضع النهائي",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            letterSpacing = 1.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = if (isFailed) Color(0xFFFF1744) else TextSecondary,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = if (isFailed) "FAILED TO LOCATE CARD" else result.finalSlot.uppercase(),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        ),
                                        color = if (isFailed) Color(0xFFFF1744) else NeonCyan
                                    )
                                    Text(
                                        text = "Coord: X=${(result.finalCenter.x * 100).toInt()}%, Y=${(result.finalCenter.y * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                // Confidence Meter Badge
                                val badgeBg = if (isFailed) Color(0xFFFF1744) else when (result.confidenceLevel) {
                                    ConfidenceLevel.HIGH -> EmeraldAccent
                                    ConfidenceLevel.MEDIUM -> AmberWarning
                                    ConfidenceLevel.LOW -> CoralAlert
                                }
                                Surface(
                                    color = badgeBg.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "${(result.averageConfidence * 100).toInt()}%",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            color = badgeBg,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = if (isFailed) "UNRELIABLE" else when (result.confidenceLevel) {
                                                ConfidenceLevel.HIGH -> "HIGH"
                                                ConfidenceLevel.MEDIUM -> "MEDIUM"
                                                ConfidenceLevel.LOW -> "LOW"
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeBg
                                        )
                                    }
                                }
                            }

                            if (isFailed && result.failureCauses.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = Color(0x22FF1744),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Failure Cause: ${result.failureCauses.first().reason}",
                                        fontSize = 10.sp,
                                        color = Color(0xFFFF8A80),
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Kinematics Diagnostics Dashboard
                item {
                    KinematicsDiagnosticsSection(
                        result = result,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 3. Action Buttons (Re-pick, Re-run, Test Another Video)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRepickTarget,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, NeonCyan),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("repick_target_btn")
                            ) {
                                Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Re-pick", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = onRerun,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DarkSurfaceElevated,
                                    contentColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("rerun_analysis_btn")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Re-run", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        FilledTonalButton(
                            onClick = onResetToUpload,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = DarkSurface,
                                contentColor = TextSecondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("test_another_video_btn")
                        ) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Another Video", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
