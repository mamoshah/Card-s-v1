package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TrackingResult
import com.example.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Kinematics & Diagnostics Section:
 * Displays kinematic metrics, performance parameters, and the comprehensive
 * AI-style analytical diagnostic report with sequential movement analysis,
 * visual template fingerprint tracking events, and a single-tap Copy Log feature.
 */
@Composable
fun KinematicsDiagnosticsSection(
    result: TrackingResult,
    modifier: Modifier = Modifier
) {
    var showDiagnosticDetails by remember { mutableStateOf(false) }
    var hasCopiedLog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(hasCopiedLog) {
        if (hasCopiedLog) {
            delay(2000)
            hasCopiedLog = false
        }
    }

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DarkSurfaceBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Title, Copy Log button, and Show/Hide toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Kinematics & Diagnostics • القياسات الحركية",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Copy Log Button
                    FilledTonalButton(
                        onClick = {
                            val fullReport = result.diagnosticNotes.joinToString("\n")
                            clipboardManager.setText(AnnotatedString(fullReport))
                            Toast.makeText(
                                context,
                                "Log Copied to Clipboard • تم نسخ التقرير",
                                Toast.LENGTH_SHORT
                            ).show()
                            hasCopiedLog = true
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (hasCopiedLog) EmeraldAccent.copy(alpha = 0.2f) else DarkSurfaceElevated,
                            contentColor = if (hasCopiedLog) EmeraldAccent else NeonCyan
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("copy_log_btn")
                    ) {
                        Icon(
                            imageVector = if (hasCopiedLog) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy Log",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (hasCopiedLog) "Copied" else "Copy Log",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    TextButton(
                        onClick = { showDiagnosticDetails = !showDiagnosticDetails },
                        modifier = Modifier.testTag("toggle_log_btn")
                    ) {
                        Text(
                            text = if (showDiagnosticDetails) "Hide Log" else "Show Log",
                            fontSize = 12.sp,
                            color = NeonCyan
                        )
                    }
                }
            }

            // Metric Pills Row 1: Kinematics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiagnosticMetricPill(
                    label = "PEAK SPEED",
                    value = "${String.format(java.util.Locale.US, "%.1f", result.peakSpeed * 30)} scr/s",
                    modifier = Modifier.weight(1f)
                )
                DiagnosticMetricPill(
                    label = "DISPLACEMENT",
                    value = "${String.format(java.util.Locale.US, "%.2f", result.totalDistanceTraveled)}x",
                    modifier = Modifier.weight(1f)
                )
                val totalOcclusions = maxOf(result.occlusionEventsCount, result.fingerprintEvents.size)
                DiagnosticMetricPill(
                    label = "OCCLUSIONS",
                    value = "$totalOcclusions",
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
                    value = String.format(java.util.Locale.US, "%.1f", result.processingFps),
                    modifier = Modifier.weight(1f)
                )
                DiagnosticMetricPill(
                    label = "CONFIDENCE",
                    value = "${(result.averageConfidence * 100).toInt()}%",
                    modifier = Modifier.weight(1f)
                )
            }

            // Collapsible Diagnostic Log Console
            AnimatedVisibility(visible = showDiagnosticDetails) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkBackground)
                        .padding(12.dp)
                        .testTag("diagnostic_log_console"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    result.diagnosticNotes.forEach { line ->
                        val color = when {
                            line.startsWith("---") -> TextMuted.copy(alpha = 0.5f)
                            line.startsWith("[") -> NeonCyan
                            line.startsWith("• Move") -> TextPrimary
                            line.startsWith("• Fingerprint Re-Acquisition:") -> EmeraldAccent
                            line.startsWith("• Final Card Position:") ||
                                line.startsWith("• Tracking Confidence:") ||
                                line.startsWith("• Shuffle Completed:") -> EmeraldAccent
                            line.startsWith("• Target Card:") || line.startsWith("• Initial Coordinates:") ||
                                line.startsWith("• Target Fingerprint:") -> TextPrimary
                            else -> TextSecondary
                        }
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = color
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticMetricPill(
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
