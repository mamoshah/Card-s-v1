package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConfidenceLevel
import com.example.model.TrackingResult
import com.example.model.TrailEffectStyle
import com.example.model.TrailLengthMode
import com.example.ui.components.TrajectoryTrailOverlay
import com.example.ui.theme.*
import com.example.viewmodel.ScrubberPlaybackState

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
    onResetToUpload: () -> Unit
) {
    var showDiagnosticDetails by remember { mutableStateOf(false) }

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
                    IconButton(onClick = onRepickTarget, modifier = Modifier.testTag("repick_target_top_btn")) {
                        Icon(Icons.Default.EditLocationAlt, contentDescription = "Re-pick Target", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Hero Final Position Card
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        1.5.dp,
                        when (result.confidenceLevel) {
                            ConfidenceLevel.HIGH -> EmeraldAccent.copy(alpha = 0.6f)
                            ConfidenceLevel.MEDIUM -> AmberWarning.copy(alpha = 0.6f)
                            ConfidenceLevel.LOW -> CoralAlert.copy(alpha = 0.6f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "FINAL POSITION • الموضع النهائي",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = TextSecondary
                            )
                            Text(
                                text = result.finalSlot.uppercase(),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                ),
                                color = NeonCyan
                            )
                            Text(
                                text = "Coord: X=${(result.finalCenter.x * 100).toInt()}%, Y=${(result.finalCenter.y * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = TextMuted
                            )
                        }

                        // Confidence Meter Badge
                        val badgeBg = when (result.confidenceLevel) {
                            ConfidenceLevel.HIGH -> EmeraldAccent
                            ConfidenceLevel.MEDIUM -> AmberWarning
                            ConfidenceLevel.LOW -> CoralAlert
                        }
                        Surface(
                            color = badgeBg.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${(result.averageConfidence * 100).toInt()}%",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = badgeBg,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = when (result.confidenceLevel) {
                                        ConfidenceLevel.HIGH -> "HIGH"
                                        ConfidenceLevel.MEDIUM -> "MEDIUM"
                                        ConfidenceLevel.LOW -> "LOW"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeBg
                                )
                            }
                        }
                    }
                }
            }

            // 2. Video Player Frame with Hardware-Accelerated Trajectory Overlay
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (currentBitmap != null) {
                            // Video Background
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
                                trailLength = playbackState.trailLength,
                                showBoundingBox = playbackState.showBoundingBox,
                                showArrow = playbackState.showArrow,
                                showTelemetryHud = playbackState.showTelemetryHud,
                                peakSpeed = result.peakSpeed,
                                imageWidth = currentBitmap.width,
                                imageHeight = currentBitmap.height,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Top-Right Live Telemetry HUD Tag
                            if (playbackState.showTelemetryHud) {
                                Surface(
                                    color = Color(0xCC111625),
                                    shape = RoundedCornerShape(8.dp),
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
            }

            // 3. Trajectory Trails & Visual Effects Controls (مؤثرات مسار الحركة)
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DarkSurfaceBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Trajectory Trails • مؤثرات مسار الحركة",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                            }
                        }

                        // Style selector chips
                        val scrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TrailEffectStyle.values().forEach { style ->
                                val isSelected = playbackState.trailEffect == style
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onTrailEffectChange(style) },
                                    label = {
                                        Text(
                                            text = "${style.titleAr} (${style.titleEn})",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonCyan.copy(alpha = 0.22f),
                                        selectedLabelColor = NeonCyan,
                                        containerColor = DarkSurfaceElevated,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }

                        // Trail duration & Overlays row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Duration Mode
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Window:", fontSize = 11.sp, color = TextMuted)
                                TrailLengthMode.values().forEach { mode ->
                                    val isSelected = playbackState.trailLength == mode
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onTrailLengthChange(mode) },
                                        label = { Text(mode.labelAr, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = NeonCyan,
                                            containerColor = DarkSurfaceElevated,
                                            labelColor = TextMuted
                                        )
                                    )
                                }
                            }

                            // Overlays toggles
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onToggleBoundingBox,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CropFree,
                                        contentDescription = "Target Box",
                                        tint = if (playbackState.showBoundingBox) NeonCyan else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onToggleArrow,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Navigation,
                                        contentDescription = "Arrow",
                                        tint = if (playbackState.showArrow) EmeraldAccent else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onToggleTelemetryHud,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Speed,
                                        contentDescription = "Speed HUD",
                                        tint = if (playbackState.showTelemetryHud) Color(0xFFFFD600) else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Scrubber Timeline & Slow-Mo Controls
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DarkSurfaceBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Scrubber Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onTogglePlay,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(NeonCyan.copy(alpha = 0.15f))
                                    .testTag("playback_toggle_btn")
                            ) {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                    tint = NeonCyan
                                )
                            }

                            // Step backward 1 frame
                            IconButton(
                                onClick = { onSeek(currentFrameIndex - 1) },
                                modifier = Modifier.size(32.dp),
                                enabled = currentFrameIndex > 0
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Frame", tint = TextSecondary, modifier = Modifier.size(20.dp))
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
                                modifier = Modifier.size(32.dp),
                                enabled = currentFrameIndex < result.totalFrames - 1
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next Frame", tint = TextSecondary, modifier = Modifier.size(20.dp))
                            }

                            Text(
                                text = "${currentFrameIndex + 1}/${result.totalFrames}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = TextPrimary
                            )
                        }

                        // Playback Speed Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Speed • السرعة:", fontSize = 11.sp, color = TextSecondary)
                                listOf(0.25f, 0.5f, 1.0f).forEach { speed ->
                                    val isSelected = playbackState.playbackSpeed == speed
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onSpeedChange(speed) },
                                        label = { Text("${speed}x", fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = NeonCyan,
                                            containerColor = DarkSurfaceElevated,
                                            labelColor = TextSecondary
                                        )
                                    )
                                }
                            }

                            Text(
                                text = "Frame ${currentFrameIndex + 1}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // 5. Engine Diagnostics & Kinematics Dashboard
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DarkSurfaceBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Analytics, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Kinematics & Diagnostics • القياسات الحركية",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                            }

                            TextButton(onClick = { showDiagnosticDetails = !showDiagnosticDetails }) {
                                Text(if (showDiagnosticDetails) "Hide Log" else "Show Log", fontSize = 12.sp, color = NeonCyan)
                            }
                        }

                        // Metric Pills Row 1: Kinematics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DiagnosticMetricPill(
                                label = "PEAK SPEED",
                                value = "${String.format("%.1f", result.peakSpeed * 30)} scr/s",
                                modifier = Modifier.weight(1f)
                            )
                            DiagnosticMetricPill(
                                label = "DISPLACEMENT",
                                value = "${String.format("%.2f", result.totalDistanceTraveled)}x",
                                modifier = Modifier.weight(1f)
                            )
                            DiagnosticMetricPill(
                                label = "OCCLUSIONS",
                                value = "${result.occlusionEventsCount}",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Metric Pills Row 2: Performance
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DiagnosticMetricPill(
                                label = "TOTAL FRAMES",
                                value = "${result.totalFrames}",
                                modifier = Modifier.weight(1f)
                            )
                            DiagnosticMetricPill(
                                label = "ENGINE FPS",
                                value = "${String.format("%.1f", result.processingFps)}",
                                modifier = Modifier.weight(1f)
                            )
                            DiagnosticMetricPill(
                                label = "CONFIDENCE",
                                value = "${(result.averageConfidence * 100).toInt()}%",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Collapsible Diagnostic Log
                        AnimatedVisibility(visible = showDiagnosticDetails) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkBackground)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                result.diagnosticNotes.forEach { note ->
                                    Text(
                                        text = "• $note",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        ),
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Action Buttons
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRepickTarget,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, NeonCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("repick_target_btn")
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-pick Target", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onRerun,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkSurfaceElevated,
                                contentColor = TextPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("rerun_analysis_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-run Engine", fontWeight = FontWeight.Bold)
                        }
                    }

                    FilledTonalButton(
                        onClick = onResetToUpload,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = DarkSurface,
                            contentColor = TextSecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_another_video_btn")
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Another Video")
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun DiagnosticMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = TextMuted
            )
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary
            )
        }
    }
}
