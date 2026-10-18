package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.model.TrackedFrame
import com.example.model.TrailEffectStyle
import com.example.model.TrailLengthMode
import com.example.ui.util.VideoDisplayGeometry
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance, hardware-accelerated trajectory visualizer.
 * Correctly maps coordinates within the letterboxed/pillarboxed video viewport.
 */
@Composable
fun TrajectoryTrailOverlay(
    trackedFrames: List<TrackedFrame>,
    currentFrameIndex: Int,
    trailEffect: TrailEffectStyle,
    trailLength: TrailLengthMode,
    showBoundingBox: Boolean,
    showArrow: Boolean,
    showTelemetryHud: Boolean,
    peakSpeed: Float,
    imageWidth: Int = 480,
    imageHeight: Int = 320,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    if (trackedFrames.isEmpty()) return

    val clampedIndex = currentFrameIndex.coerceIn(0, trackedFrames.size - 1)
    val currentFrame = trackedFrames[clampedIndex]

    // Determine the active window of frames for the trail
    val startIdx = when (trailLength) {
        TrailLengthMode.FULL -> 0
        TrailLengthMode.MEDIUM -> max(0, clampedIndex - trailLength.frameCount)
        TrailLengthMode.SHORT -> max(0, clampedIndex - trailLength.frameCount)
    }

    val visibleFrames = trackedFrames.subList(startIdx, clampedIndex + 1)

    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        // Precise letterbox projection mapping
        val fitRect = VideoDisplayGeometry.calculateFitRect(size, imageWidth, imageHeight)

        // 1. Draw Selected Trajectory Trail Effect
        when (trailEffect) {
            TrailEffectStyle.NEON_GLOW -> {
                drawNeonGlowTrail(visibleFrames, fitRect)
            }
            TrailEffectStyle.SPEED_HEATMAP -> {
                drawSpeedHeatmapTrail(visibleFrames, peakSpeed, fitRect)
            }
            TrailEffectStyle.COMET_SPARKS -> {
                drawCometSparksTrail(visibleFrames, fitRect)
            }
            TrailEffectStyle.GHOST_CARDS -> {
                drawGhostCardsTrail(visibleFrames, fitRect)
            }
            TrailEffectStyle.WAYPOINTS -> {
                drawWaypointsTrail(visibleFrames, fitRect)
            }
            TrailEffectStyle.CLASSIC_ARROW -> {
                // No trail lines, only target indicator
            }
        }

        // 2. Persistent Tracking Indicator (+) in Plum (#4C0043)
        // Independent, locked to card tracker center, permanently active in all cases (whether selection visibility is enabled or disabled).
        // Blue selection rectangle, brackets, and side borders are eliminated completely.
        drawTrackingCrosshairIndicator(currentFrame, fitRect)

        // 3. Draw Instantaneous Velocity Vector
        if (showTelemetryHud && currentFrame.speed > 0.004f) {
            drawVelocityVector(currentFrame, fitRect)
        }

        // 4. Draw Classic Downward Pointer Arrow
        if (showArrow) {
            drawDownwardGreenArrow(currentFrame, fitRect)
        }
    }
}

/**
 * Style 1: Neon Cyberpunk Trail with multi-pass glowing bloom and pulsing head
 */
private fun DrawScope.drawNeonGlowTrail(
    frames: List<TrackedFrame>,
    fitRect: Rect
) {
    if (frames.size < 2) return
    val total = frames.size

    for (i in 0 until total - 1) {
        val f0 = frames[i]
        val f1 = frames[i + 1]

        val p0 = Offset(fitRect.left + f0.center.x * fitRect.width, fitRect.top + f0.center.y * fitRect.height)
        val p1 = Offset(fitRect.left + f1.center.x * fitRect.width, fitRect.top + f1.center.y * fitRect.height)

        val progress = (i + 1).toFloat() / total
        val alpha = (0.20f + 0.80f * progress).coerceIn(0.1f, 1.0f)

        val segmentColor = if (progress < 0.5f) {
            Color(0xFFB026FF).copy(alpha = alpha * 0.9f)
        } else {
            Color(0xFF00E5FF).copy(alpha = alpha)
        }

        // Outer bloom halo
        drawLine(
            color = segmentColor.copy(alpha = alpha * 0.35f),
            start = p0,
            end = p1,
            strokeWidth = (9.dp.toPx() * progress).coerceAtLeast(3.dp.toPx()),
            cap = StrokeCap.Round
        )

        // Mid glow line
        drawLine(
            color = segmentColor,
            start = p0,
            end = p1,
            strokeWidth = (4.dp.toPx() * progress).coerceAtLeast(2.dp.toPx()),
            cap = StrokeCap.Round
        )

        // White-hot sharp center core
        drawLine(
            color = Color.White.copy(alpha = alpha * 0.95f),
            start = p0,
            end = p1,
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    // Glowing head rings at current frame position
    val head = frames.last()
    val headCenter = Offset(fitRect.left + head.center.x * fitRect.width, fitRect.top + head.center.y * fitRect.height)

    drawCircle(
        color = Color(0xFF00E5FF).copy(alpha = 0.35f),
        radius = 14.dp.toPx(),
        center = headCenter
    )
    drawCircle(
        color = Color(0xFF00E5FF),
        radius = 8.dp.toPx(),
        center = headCenter,
        style = Stroke(width = 2.5.dp.toPx())
    )
    drawCircle(
        color = Color.White,
        radius = 3.5.dp.toPx(),
        center = headCenter
    )
}

/**
 * Style 2: Speed Heatmap Trail (Green = slow, Yellow = medium, Fiery Crimson = high speed shuffle)
 */
private fun DrawScope.drawSpeedHeatmapTrail(
    frames: List<TrackedFrame>,
    peakSpeed: Float,
    fitRect: Rect
) {
    if (frames.size < 2) return
    val total = frames.size
    val effectivePeak = peakSpeed.coerceAtLeast(0.015f)

    for (i in 0 until total - 1) {
        val f0 = frames[i]
        val f1 = frames[i + 1]

        val p0 = Offset(fitRect.left + f0.center.x * fitRect.width, fitRect.top + f0.center.y * fitRect.height)
        val p1 = Offset(fitRect.left + f1.center.x * fitRect.width, fitRect.top + f1.center.y * fitRect.height)

        val progress = (i + 1).toFloat() / total
        val alpha = (0.25f + 0.75f * progress).coerceIn(0.1f, 1.0f)

        val speedRatio = (f1.speed / effectivePeak).coerceIn(0f, 1f)
        val heatColor = when {
            speedRatio > 0.60f -> Color(0xFFFF1744) // Rapid burst / shuffle
            speedRatio > 0.28f -> Color(0xFFFFD600) // Medium transition
            else -> Color(0xFF00E676)               // Smooth / slow movement
        }

        // Dark border for contrast
        drawLine(
            color = Color.Black.copy(alpha = alpha * 0.8f),
            start = p0,
            end = p1,
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )

        // High-vis heat line
        drawLine(
            color = heatColor.copy(alpha = alpha),
            start = p0,
            end = p1,
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        if (i % 2 == 0) {
            drawCircle(
                color = heatColor.copy(alpha = alpha),
                radius = 3.dp.toPx(),
                center = p1
            )
        }
    }
}

/**
 * Style 3: Comet Tail with trailing particle sparks & velocity taper
 */
private fun DrawScope.drawCometSparksTrail(
    frames: List<TrackedFrame>,
    fitRect: Rect
) {
    if (frames.size < 2) return
    val total = frames.size

    for (i in 0 until total - 1) {
        val f0 = frames[i]
        val f1 = frames[i + 1]

        val p0 = Offset(fitRect.left + f0.center.x * fitRect.width, fitRect.top + f0.center.y * fitRect.height)
        val p1 = Offset(fitRect.left + f1.center.x * fitRect.width, fitRect.top + f1.center.y * fitRect.height)

        val progress = (i + 1).toFloat() / total
        val widthPx = 1.5.dp.toPx() + 6.5.dp.toPx() * (progress * progress)

        drawLine(
            color = Color(0xFFFFB74D).copy(alpha = progress * 0.85f),
            start = p0,
            end = p1,
            strokeWidth = widthPx,
            cap = StrokeCap.Round
        )

        drawLine(
            color = Color(0xFFFFF176).copy(alpha = progress),
            start = p0,
            end = p1,
            strokeWidth = (widthPx * 0.45f).coerceAtLeast(1.5.dp.toPx()),
            cap = StrokeCap.Round
        )

        // Lateral spark particles
        if (i > 0 && i % 2 == 0) {
            val dx = p1.x - p0.x
            val dy = p1.y - p0.y
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val nx = -dy / len
            val ny = dx / len

            val sparkSide = if (i % 4 == 0) 1f else -1f
            val sparkOffsetDist = (4.dp.toPx() + (i % 3) * 3.dp.toPx()) * sparkSide
            val sparkPos = Offset(p1.x + nx * sparkOffsetDist, p1.y + ny * sparkOffsetDist)

            drawCircle(
                color = Color(0xFFFFD54F).copy(alpha = progress * 0.75f),
                radius = 2.dp.toPx(),
                center = sparkPos
            )
        }
    }

    val head = frames.last()
    val headPt = Offset(fitRect.left + head.center.x * fitRect.width, fitRect.top + head.center.y * fitRect.height)

    drawCircle(
        color = Color(0xFFFF9800).copy(alpha = 0.4f),
        radius = 16.dp.toPx(),
        center = headPt
    )
    drawCircle(
        color = Color(0xFFFFEB3B),
        radius = 7.dp.toPx(),
        center = headPt
    )
    drawCircle(
        color = Color.White,
        radius = 3.5.dp.toPx(),
        center = headPt
    )
}

/**
 * Style 4: Ghost Cards / Keyframe Echo
 */
private fun DrawScope.drawGhostCardsTrail(
    frames: List<TrackedFrame>,
    fitRect: Rect
) {
    if (frames.isEmpty()) return
    val total = frames.size

    // Connecting line
    if (total >= 2) {
        for (i in 0 until total - 1) {
            val p0 = Offset(fitRect.left + frames[i].center.x * fitRect.width, fitRect.top + frames[i].center.y * fitRect.height)
            val p1 = Offset(fitRect.left + frames[i + 1].center.x * fitRect.width, fitRect.top + frames[i + 1].center.y * fitRect.height)
            val progress = (i + 1).toFloat() / total

            drawLine(
                color = Color(0xFF00E5FF).copy(alpha = 0.4f * progress),
                start = p0,
                end = p1,
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )
        }
    }

    val stride = if (total > 20) 4 else if (total > 10) 3 else 2

    for (i in 0 until total - 1 step stride) {
        val f = frames[i]
        val progress = (i + 1).toFloat() / total
        val alpha = (0.12f + 0.35f * progress).coerceIn(0.08f, 0.50f)

        val boxL = fitRect.left + f.rect.left * fitRect.width
        val boxT = fitRect.top + f.rect.top * fitRect.height
        val boxW = f.rect.width * fitRect.width
        val boxH = f.rect.height * fitRect.height

        drawRoundRect(
            color = Color(0xFF00E5FF).copy(alpha = alpha * 0.12f),
            topLeft = Offset(boxL, boxT),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )

        drawRoundRect(
            color = Color(0xFF00E5FF).copy(alpha = alpha),
            topLeft = Offset(boxL, boxT),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            style = Stroke(
                width = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
        )
    }
}

/**
 * Style 5: Digital Telemetry Waypoints
 */
private fun DrawScope.drawWaypointsTrail(
    frames: List<TrackedFrame>,
    fitRect: Rect
) {
    if (frames.isEmpty()) return
    val total = frames.size

    if (total >= 2) {
        for (i in 0 until total - 1) {
            val p0 = Offset(fitRect.left + frames[i].center.x * fitRect.width, fitRect.top + frames[i].center.y * fitRect.height)
            val p1 = Offset(fitRect.left + frames[i + 1].center.x * fitRect.width, fitRect.top + frames[i + 1].center.y * fitRect.height)

            drawLine(
                color = Color(0xFF00E5FF).copy(alpha = 0.5f),
                start = p0,
                end = p1,
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }

    for (i in 0 until total) {
        val f = frames[i]
        val center = Offset(fitRect.left + f.center.x * fitRect.width, fitRect.top + f.center.y * fitRect.height)

        val nodeColor = when {
            f.isOccluded -> Color(0xFFFF5252)
            f.confidence >= 0.65f -> Color(0xFF00E676)
            f.confidence >= 0.40f -> Color(0xFFFFB300)
            else -> Color(0xFFFF5252)
        }

        drawCircle(
            color = Color.Black.copy(alpha = 0.7f),
            radius = 4.5.dp.toPx(),
            center = center
        )

        drawCircle(
            color = nodeColor,
            radius = 3.dp.toPx(),
            center = center
        )
    }
}

/**
 * Dedicated Card Tracking Crosshair Indicator (+)
 * - Rendered independently without any rectangular frame or side brackets.
 * - Permanently fixed and tracking the target card center (cx, cy).
 * - Styled in deep Plum (#4C0043) with high-contrast subtle outline for optimal visibility.
 * - Active permanently across all situations (whether selection visibility is toggled on or off).
 */
private fun DrawScope.drawTrackingCrosshairIndicator(
    frame: TrackedFrame,
    fitRect: Rect
) {
    val cx = fitRect.left + frame.center.x * fitRect.width
    val cy = fitRect.top + frame.center.y * fitRect.height

    val plumColor = Color(0xFF4C0043)
    val crossLen = 8.dp.toPx()
    val strokeWidth = 2.5.dp.toPx()

    // 1. High-contrast clean halo/outline so the Plum crosshair is clearly distinguishable
    // on both light cards and dark felt surfaces
    val outlineWidth = strokeWidth + 1.6.dp.toPx()
    val outlineColor = Color.White.copy(alpha = 0.70f)

    // Contrast outline
    drawLine(
        color = outlineColor,
        start = Offset(cx - crossLen - 0.5.dp.toPx(), cy),
        end = Offset(cx + crossLen + 0.5.dp.toPx(), cy),
        strokeWidth = outlineWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = outlineColor,
        start = Offset(cx, cy - crossLen - 0.5.dp.toPx()),
        end = Offset(cx, cy + crossLen + 0.5.dp.toPx()),
        strokeWidth = outlineWidth,
        cap = StrokeCap.Round
    )

    // 2. Primary Plum (#4C0043) Crosshair (+)
    drawLine(
        color = plumColor,
        start = Offset(cx - crossLen, cy),
        end = Offset(cx + crossLen, cy),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    drawLine(
        color = plumColor,
        start = Offset(cx, cy - crossLen),
        end = Offset(cx, cy + crossLen),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    // Small crisp precision center point in Plum
    drawCircle(
        color = plumColor,
        radius = 2.dp.toPx(),
        center = Offset(cx, cy)
    )
}

/**
 * Instantaneous Horizontal Tracking Vector Arrow (السهم الأصفر لمؤشر الرصد)
 * Constrained strictly to horizontal axis (Right / Left only).
 * Zero vertical component or upward deviation.
 */
private fun DrawScope.drawVelocityVector(
    frame: TrackedFrame,
    fitRect: Rect
) {
    val cx = fitRect.left + frame.center.x * fitRect.width
    val cy = fitRect.top + frame.center.y * fitRect.height

    val vxNorm = frame.velocityX
    val absVx = kotlin.math.abs(vxNorm)
    if (absVx < 0.002f && frame.speed < 0.002f) return

    val isMovingRight = vxNorm >= 0f
    val dir = if (isMovingRight) 1f else -1f
    val effectiveSpeed = max(absVx, frame.speed)
    val vecLength = (effectiveSpeed * fitRect.width * 3.5f).coerceIn(18.dp.toPx(), 65.dp.toPx())

    val targetX = cx + dir * vecLength
    val targetY = cy // Strictly horizontal Y - perfectly aligned with horizontal track center!

    // Shadow line for high contrast
    drawLine(
        color = Color(0xDD000000),
        start = Offset(cx, cy),
        end = Offset(targetX, targetY),
        strokeWidth = 5.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Main vibrant yellow arrow body (مؤشر الرصد - السهم الأصفر)
    drawLine(
        color = Color(0xFFFFD600),
        start = Offset(cx, cy),
        end = Offset(targetX, targetY),
        strokeWidth = 3.2.dp.toPx(),
        cap = StrokeCap.Round
    )

    val tipLenX = 12.dp.toPx()
    val tipLenY = 8.dp.toPx()

    val arrowHeadPath = Path().apply {
        moveTo(targetX, targetY)
        lineTo(targetX - dir * tipLenX, targetY - tipLenY)
        lineTo(targetX - dir * (tipLenX * 0.65f), targetY)
        lineTo(targetX - dir * tipLenX, targetY + tipLenY)
        close()
    }

    drawPath(
        path = arrowHeadPath,
        color = Color(0xDD000000),
        style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round)
    )

    drawPath(
        path = arrowHeadPath,
        color = Color(0xFFFFD600)
    )
}

/**
 * Large downward green arrow pointing to the detected card with downward Y-Axis Clamp Boundary.
 * Ensures arrow tip NEVER descends below the horizontal green boundary clamp line.
 */
private fun DrawScope.drawDownwardGreenArrow(
    frame: TrackedFrame,
    fitRect: Rect
) {
    val centerX = fitRect.left + frame.center.x * fitRect.width
    val cardTopY = fitRect.top + frame.rect.top * fitRect.height

    // Y-Axis Clamp Boundary (الخط الأخضر الأفقي المحدد)
    // The downward boundary prevents the green arrow tip from descending below the boundary line
    val maxDownwardBoundaryY = fitRect.top + fitRect.height * 0.365f
    val minTopY = fitRect.top + 28.dp.toPx()

    // Clamp arrow tip so it strictly respects the downward boundary limit
    val desiredTipY = cardTopY - 4.dp.toPx()
    val arrowTipY = desiredTipY.coerceIn(minTopY, maxDownwardBoundaryY)

    val arrowHeadHeight = 22.dp.toPx()
    val arrowHeadWidth = 38.dp.toPx()
    val shaftWidth = 14.dp.toPx()
    val arrowHeadBaseY = arrowTipY - arrowHeadHeight
    val shaftTopY = (arrowHeadBaseY - 32.dp.toPx()).coerceAtLeast(fitRect.top + 6.dp.toPx())

    // Draw subtle horizontal boundary limit line across the tracking corridor
    drawLine(
        color = Color(0x7700E676),
        start = Offset(fitRect.left + 8.dp.toPx(), maxDownwardBoundaryY),
        end = Offset(fitRect.right - 8.dp.toPx(), maxDownwardBoundaryY),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
    )

    val arrowPath = Path().apply {
        moveTo(centerX, arrowTipY)
        lineTo(centerX - arrowHeadWidth / 2f, arrowHeadBaseY)
        lineTo(centerX - shaftWidth / 2f, arrowHeadBaseY)
        lineTo(centerX - shaftWidth / 2f, shaftTopY)
        lineTo(centerX + shaftWidth / 2f, shaftTopY)
        lineTo(centerX + shaftWidth / 2f, arrowHeadBaseY)
        lineTo(centerX + arrowHeadWidth / 2f, arrowHeadBaseY)
        close()
    }

    // High-contrast outline drop shadow
    drawPath(
        path = arrowPath,
        color = Color(0xEE000000),
        style = Stroke(width = 4.dp.toPx(), join = StrokeJoin.Round)
    )

    // Vibrant green fill
    drawPath(
        path = arrowPath,
        color = Color(0xFF00E676)
    )
}
