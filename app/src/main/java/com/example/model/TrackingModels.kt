package com.example.model

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF

/**
 * Normalized 2D point (0.0 .. 1.0)
 */
data class NormalizedPoint(
    val x: Float,
    val y: Float
) {
    fun toAbsolute(width: Int, height: Int): PointF {
        return PointF(x * width, y * height)
    }
}

/**
 * Normalized Bounding Box (0.0 .. 1.0)
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0.01f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.01f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun toAbsolute(width: Int, height: Int): RectF {
        return RectF(
            left * width,
            top * height,
            right * width,
            bottom * height
        )
    }

    fun contains(pt: NormalizedPoint): Boolean {
        return pt.x in left..right && pt.y in top..bottom
    }

    companion object {
        fun fromCenter(center: NormalizedPoint, width: Float, height: Float): NormalizedRect {
            val halfW = width / 2f
            val halfH = height / 2f
            return NormalizedRect(
                left = (center.x - halfW).coerceIn(0f, 1f),
                top = (center.y - halfH).coerceIn(0f, 1f),
                right = (center.x + halfW).coerceIn(0f, 1f),
                bottom = (center.y + halfH).coerceIn(0f, 1f)
            )
        }
    }
}

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW;

    val label: String
        get() = when (this) {
            HIGH -> "High Confidence"
            MEDIUM -> "Medium Confidence"
            LOW -> "Low Confidence (Flagged)"
        }
}

enum class TrackingState {
    TRACKING_LOCKED,
    OCCLUDED_COASTING,
    OCCLUSION_STACK,
    REACQUIRING,
    LOST,
    UNRELIABLE
}

/**
 * Trajectory Trail visual effect modes
 */
enum class TrailEffectStyle(val id: String, val titleAr: String, val titleEn: String) {
    NEON_GLOW("neon", "شعاع النيون", "Neon Glow"),
    SPEED_HEATMAP("heatmap", "خريطة السرعة", "Speed Heatmap"),
    COMET_SPARKS("comet", "ذيل المذنب", "Comet Sparks"),
    GHOST_CARDS("ghost", "أشباح الكرت", "Ghost Cards"),
    WAYPOINTS("waypoints", "نقاط رقمية", "Waypoints"),
    CLASSIC_ARROW("arrow", "السهم فقط", "Classic Arrow")
}

/**
 * Duration / window of the visible motion trajectory
 */
enum class TrailLengthMode(val labelAr: String, val labelEn: String, val frameCount: Int) {
    FULL("المسار كاملاً", "Full Trail", -1),
    MEDIUM("آخر 15 إطار", "Last 15 Frames", 15),
    SHORT("آخر 6 إطارات", "Last 6 Frames", 6)
}

/**
 * Tracking data for an individual frame in the video sequence
 */
data class TrackedFrame(
    val frameIndex: Int,
    val timestampMs: Long,
    val rect: NormalizedRect,
    val center: NormalizedPoint,
    val confidence: Float, // 0.0 .. 1.0
    val trackingState: TrackingState,
    val isOccluded: Boolean,
    val inlierCount: Int,
    val velocityX: Float = 0f,
    val velocityY: Float = 0f,
    val speed: Float = 0f,
    val smoothedCenter: NormalizedPoint = center,
    val bitmap: Bitmap? = null,
    val isUnreliable: Boolean = (trackingState == TrackingState.UNRELIABLE || confidence < 0.85f)
)

/**
 * Strict screen positions for card tracking & shuffles:
 *  - Position 1 = Left (اليسار)
 *  - Position 2 = Middle / Center (الوسط)
 *  - Position 3 = Right (اليمين)
 */
enum class CardPosition(val id: Int, val nameEn: String, val nameAr: String) {
    LEFT(1, "Left", "اليسار"),
    MIDDLE(2, "Middle", "الوسط"),
    RIGHT(3, "Right", "اليمين");

    val shortLabel: String get() = "Pos $id"
    val displayLabel: String get() = "Position $id ($nameEn)"

    val defaultCenterX: Float
        get() = when (this) {
            LEFT -> 0.430f
            MIDDLE -> 0.575f
            RIGHT -> 0.720f
        }

    val defaultCenterY: Float get() = 0.65f

    /**
     * Relative coordinates anchored directly over the game cards inside active video frame:
     *  - Card 1 (Left): Center = 0.430f, X [0.36 .. 0.50], Y [0.45 .. 0.85]
     *  - Card 2 (Middle): Center = 0.575f, X [0.505 .. 0.645], Y [0.45 .. 0.85]
     *  - Card 3 (Right): Center = 0.720f, X [0.65 .. 0.79], Y [0.45 .. 0.85]
     */
    fun getPreconfiguredRect(): NormalizedRect {
        return when (this) {
            LEFT -> NormalizedRect(left = 0.36f, top = 0.45f, right = 0.50f, bottom = 0.85f)
            MIDDLE -> NormalizedRect(left = 0.505f, top = 0.45f, right = 0.645f, bottom = 0.85f)
            RIGHT -> NormalizedRect(left = 0.65f, top = 0.45f, right = 0.79f, bottom = 0.85f)
        }
    }

    companion object {
        fun fromId(id: Int): CardPosition = when (id) {
            1 -> LEFT
            3 -> RIGHT
            else -> MIDDLE
        }

        /**
         * Resolves position by normalized X coordinate:
         * Position 1 (Left): X < 0.5025f (Anchor center = 0.430f)
         * Position 2 (Middle): 0.5025f <= X <= 0.6475f (Anchor center = 0.575f)
         * Position 3 (Right): X > 0.6475f (Anchor center = 0.720f)
         */
        fun fromX(x: Float): CardPosition = when {
            x < 0.5025f -> LEFT
            x in 0.5025f..0.6475f -> MIDDLE
            else -> RIGHT
        }

        /**
         * Hit-tests whether a video-relative coordinate (relX, relY) falls directly inside
         * a card bounding box or its top guidance indicator box.
         */
        fun findPositionAt(relX: Float, relY: Float): CardPosition? {
            for (pos in entries) {
                val rect = pos.getPreconfiguredRect()
                // Check if inside card bounds or within top guidance box region (relY from 0.25 to 0.85)
                if (relX in rect.left..rect.right && relY in 0.25f..rect.bottom) {
                    return pos
                }
            }
            return null
        }

        /**
         * Transition with hysteresis thresholds:
         * Left/Middle boundary midpoint: 0.5025f (hysteresis [0.495f, 0.5025f])
         * Middle/Right boundary midpoint: 0.6475f (hysteresis [0.640f, 0.6475f])
         */
        fun transition(current: CardPosition, x: Float): CardPosition {
            return when (current) {
                LEFT -> when {
                    x > 0.6475f -> RIGHT
                    x >= 0.5025f -> MIDDLE
                    else -> LEFT
                }
                MIDDLE -> when {
                    x < 0.495f -> LEFT
                    x > 0.6475f -> RIGHT
                    else -> MIDDLE
                }
                RIGHT -> when {
                    x < 0.495f -> LEFT
                    x <= 0.640f -> MIDDLE
                    else -> RIGHT
                }
            }
        }
    }
}

/**
 * Structured log entry for detected card position transitions
 */
data class CardMoveLog(
    val moveNumber: Int,
    val fromPosition: CardPosition,
    val toPosition: CardPosition,
    val frameIndex: Int,
    val timestampMs: Long,
    val confidence: Float = 1.0f,
    val speed: Float = 0f
) {
    fun toFormattedString(): String {
        val seconds = timestampMs / 1000f
        val timeFormatted = String.format(java.util.Locale.US, "%.2fs", seconds)
        return "• Move $moveNumber: Card moved [${fromPosition.shortLabel} -> ${toPosition.shortLabel}] | Frame $frameIndex ($timeFormatted)"
    }

    fun toAnalyticalString(): String {
        val totalSeconds = timestampMs / 1000f
        val minutes = (totalSeconds / 60).toInt()
        val seconds = totalSeconds % 60
        val timeFormatted = String.format(java.util.Locale.US, "%02d:%04.1fs", minutes, seconds)
        val velocityFormatted = String.format(java.util.Locale.US, "%.2f", speed * 30f)
        return "• Move $moveNumber: ${fromPosition.shortLabel} -> ${toPosition.shortLabel} | Frame $frameIndex (Time: $timeFormatted) | Velocity: $velocityFormatted"
    }
}

/**
 * High-resolution visual template and feature descriptor fingerprint
 * captured at initial target lock (frame 0 / selection tap).
 */
data class TrackedTargetFingerprint(
    val initialTargetRect: NormalizedRect,
    val initialPosition: CardPosition,
    val templateWidth: Int = 32,
    val templateHeight: Int = 44,
    val zeroMeanPatch: FloatArray,
    val templateNorm: Float,
    val meanBrightness: Float,
    val isRedSuit: Boolean,
    val isBlackSuit: Boolean,
    val keyFeaturesCount: Int = 0,
    val redPixelCount: Int = 0,
    val darkPixelCount: Int = 0
)

/**
 * Detailed record of a Fingerprint / Visual Template Matching re-acquisition event
 */
data class FingerprintUsageEvent(
    val startFrame: Int,
    val endFrame: Int,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val targetPosition: CardPosition,
    val reason: String, // e.g. "Card Overlap / Center Stack", "Feature Loss", "Unstacking Separation"
    val restoredPosition: CardPosition,
    val confidenceScore: Float
) {
    val durationFrames: Int get() = maxOf(1, endFrame - startFrame + 1)
    val durationMs: Long get() = maxOf(0L, endTimestampMs - startTimestampMs)

    fun toDiagnosticString(): String {
        val seconds = startTimestampMs / 1000f
        val timeFormatted = String.format(java.util.Locale.US, "%02d:%04.1fs", (seconds / 60).toInt(), seconds % 60)
        val confPercent = (confidenceScore * 100).toInt()
        return "• Fingerprint Re-Acquisition: ${targetPosition.shortLabel} | Frame $startFrame-$endFrame (Time: $timeFormatted, ${durationFrames}f/${durationMs}ms) | Reason: $reason | Re-acquired at ${restoredPosition.shortLabel} (${restoredPosition.nameEn}) | Confidence: $confPercent%"
    }
}

/**
 * Detailed technical diagnostic cause for failure or uncertainty event
 */
data class FailureCause(
    val frameIndex: Int,
    val timestampMs: Long,
    val reason: String,
    val metricDetails: String
) {
    fun toDiagnosticString(): String {
        val seconds = timestampMs / 1000f
        val timeFormatted = String.format(java.util.Locale.US, "%02d:%04.1fs", (seconds / 60).toInt(), seconds % 60)
        return "• Frame $frameIndex (Time: $timeFormatted): $reason | Details: $metricDetails"
    }
}

/**
 * Complete result output of the tracking engine
 */
data class TrackingResult(
    val videoTitle: String,
    val totalFrames: Int,
    val trackedFrames: List<TrackedFrame>,
    val finalFrameIndex: Int,
    val finalRect: NormalizedRect,
    val finalCenter: NormalizedPoint,
    val finalSlot: String, // e.g. "Position 2 (Middle)", "Position 1 (Left)", "Position 3 (Right)"
    val averageConfidence: Float,
    val confidenceLevel: ConfidenceLevel,
    val occlusionEventsCount: Int,
    val totalProcessingTimeMs: Long,
    val processingFps: Float,
    val lostLockFlag: Boolean,
    val diagnosticNotes: List<String>,
    val totalDistanceTraveled: Float = 0f,
    val peakSpeed: Float = 0f,
    val averageSpeed: Float = 0f,
    val moveHistory: List<CardMoveLog> = emptyList(),
    val fingerprintEvents: List<FingerprintUsageEvent> = emptyList(),
    val targetFingerprint: TrackedTargetFingerprint? = null,
    val failureCauses: List<FailureCause> = emptyList(),
    val isUnreliable: Boolean = false
) {
    val isFailedOrUnreliable: Boolean
        get() = isUnreliable || averageConfidence < 0.85f || lostLockFlag || confidenceLevel == ConfidenceLevel.LOW
}

/**
 * Metadata for benchmark / sample shuffle video
 */
data class BenchmarkVideo(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val difficulty: String,
    val durationSec: Float,
    val frameCount: Int,
    val targetCardName: String
)
