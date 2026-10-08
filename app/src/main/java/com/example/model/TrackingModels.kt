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
    REACQUIRING,
    LOST
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
    val bitmap: Bitmap? = null
)

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
    val finalSlot: String, // e.g. "Left Position", "Middle Position", "Right Position"
    val averageConfidence: Float,
    val confidenceLevel: ConfidenceLevel,
    val occlusionEventsCount: Int,
    val totalProcessingTimeMs: Long,
    val processingFps: Float,
    val lostLockFlag: Boolean,
    val diagnosticNotes: List<String>,
    val totalDistanceTraveled: Float = 0f,
    val peakSpeed: Float = 0f,
    val averageSpeed: Float = 0f
)

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
