package com.example.engine

import com.example.model.CardMoveLog
import com.example.model.CardPosition
import com.example.model.FingerprintUsageEvent
import com.example.model.TrackedFrame
import java.util.Locale
import kotlin.math.max

/**
 * Sequential card movement tracking engine & structured diagnostic logger.
 *
 * Implements strict 3-slot position mapping:
 *  - Position 1 = Left (اليسار)
 *  - Position 2 = Middle / Center (الوسط)
 *  - Position 3 = Right (اليمين)
 *
 * Detects frame-by-frame position transitions with hysteresis to suppress
 * boundary micro-jitter, and builds structured diagnostic log entries.
 */
class CardMovementTracker {

    private var currentPosition: CardPosition = CardPosition.MIDDLE
    private val moves = mutableListOf<CardMoveLog>()
    private var isInitialized = false

    /**
     * Initializes tracking with the starting frame
     */
    fun init(initialFrame: TrackedFrame) {
        currentPosition = CardPosition.fromX(initialFrame.center.x)
        moves.clear()
        isInitialized = true
    }

    /**
     * Evaluates a frame for card position transitions
     */
    fun processFrame(frame: TrackedFrame): CardMoveLog? {
        if (!isInitialized) {
            init(frame)
            return null
        }

        val nextPosition = CardPosition.transition(currentPosition, frame.center.x)
        if (nextPosition != currentPosition) {
            val move = CardMoveLog(
                moveNumber = moves.size + 1,
                fromPosition = currentPosition,
                toPosition = nextPosition,
                frameIndex = frame.frameIndex,
                timestampMs = frame.timestampMs,
                confidence = frame.confidence,
                speed = frame.speed
            )
            moves.add(move)
            currentPosition = nextPosition
            return move
        }
        return null
    }

    /**
     * Current detected card position
     */
    fun getCurrentPosition(): CardPosition = currentPosition

    /**
     * All detected movement transition logs
     */
    fun getMoveHistory(): List<CardMoveLog> = moves.toList()

    /**
     * Generates comprehensive AI-style analytical diagnostic report:
     * --------------------------------------------------
     * [TRACKING DIAGNOSTIC REPORT]
     * • Target Card: Selected Position (Left/Middle/Right)
     * • Initial Coordinates: x=X%, y=Y% (Target Locked)
     * --------------------------------------------------
     * [SEQUENTIAL MOVEMENT ANALYSIS]
     * • Move 1: Pos X -> Pos Y | Frame # (Time: 00:00.0s) | Velocity: V
     * • Move 2: Pos Y -> Pos Z | Frame # (Time: 00:00.0s) | Velocity: V
     * --------------------------------------------------
     * [VISUAL FINGERPRINT & RE-ACQUISITION LOG] (if used)
     * • Target Fingerprint: Locked at Position 2 (Middle) (Frame 0)
     * • Fingerprint Re-Acquisition: Target Pos 2 | Frame 40-48 ...
     * --------------------------------------------------
     * [FINAL EVALUATION & VERDICT]
     * • Shuffle Completed: Total Moves = N
     * • Final Card Position: Position X (Left / Middle / Right)
     * • Tracking Confidence: 93%
     * --------------------------------------------------
     * [KINEMATIC METRICS SUMMARY]
     * • Processed Frames: N frames in T ms (Average FPS: X.Y)
     * • Peak Speed: X.XX screens/s | Total Displacement: Y%
     * • Occlusion Events Handled: N
     * --------------------------------------------------
     */
    fun generateAiAnalyticalReport(
        targetCardLabel: String = "Position 2 (Middle)",
        initialCoordinates: String = "x=57%, y=65% (Target Locked)",
        totalFrames: Int,
        totalDurationMs: Long,
        processingFps: Float,
        confidencePercent: Int = 93,
        peakSpeedScreensPerSec: Float = 0f,
        totalDisplacementPercent: Int = 0,
        occlusionEventsCount: Int = 0,
        fingerprintEvents: List<FingerprintUsageEvent> = emptyList(),
        extraNotes: List<String> = emptyList()
    ): List<String> {
        val lines = mutableListOf<String>()
        val separator = "--------------------------------------------------"

        lines.add(separator)
        lines.add("[TRACKING DIAGNOSTIC REPORT]")
        lines.add("• Target Card: $targetCardLabel")
        lines.add("• Initial Coordinates: $initialCoordinates")
        lines.add(separator)

        lines.add("[SEQUENTIAL MOVEMENT ANALYSIS]")
        if (moves.isEmpty()) {
            lines.add("• Card remained steady at $targetCardLabel (No position swaps detected)")
        } else {
            for (move in moves) {
                lines.add(move.toAnalyticalString())
            }
        }
        lines.add(separator)

        // Visual Fingerprint / Template Matching Usage Section
        if (fingerprintEvents.isNotEmpty()) {
            lines.add("[VISUAL FINGERPRINT & RE-ACQUISITION LOG]")
            lines.add("• Target Fingerprint: Locked at $targetCardLabel (Frame 0)")
            for (event in fingerprintEvents) {
                lines.add(event.toDiagnosticString())
            }
            lines.add(separator)
        }

        lines.add("[FINAL EVALUATION & VERDICT]")
        lines.add("• Shuffle Completed: Total Moves = ${moves.size}")
        lines.add("• Final Card Position: ${currentPosition.displayLabel}")
        lines.add("• Tracking Confidence: $confidencePercent%")
        lines.add(separator)

        val totalOcclusionsHandled = maxOf(occlusionEventsCount, fingerprintEvents.size)
        lines.add("[KINEMATIC METRICS SUMMARY]")
        lines.add(
            "• Processed Frames: $totalFrames frames in ${totalDurationMs}ms (Average FPS: ${String.format(Locale.US, "%.1f", processingFps)})"
        )
        lines.add(
            "• Peak Speed: ${String.format(Locale.US, "%.2f", peakSpeedScreensPerSec)} screens/s | Total Displacement: $totalDisplacementPercent%"
        )
        if (fingerprintEvents.isNotEmpty()) {
            lines.add("• Occlusion Events Handled: $totalOcclusionsHandled (Fingerprint Re-Acquisitions: ${fingerprintEvents.size})")
        } else {
            lines.add("• Occlusion Events Handled: $totalOcclusionsHandled")
        }

        for (extra in extraNotes) {
            lines.add("• $extra")
        }
        lines.add(separator)

        return lines
    }

    /**
     * Backward-compatible delegation to generateAiAnalyticalReport
     */
    fun generateConsoleLogs(
        totalFrames: Int,
        totalDurationMs: Long,
        processingFps: Float,
        targetCardLabel: String = "Position 2 (Middle)",
        initialCoordinates: String = "x=57%, y=65% (Target Locked)",
        confidencePercent: Int = 93,
        peakSpeedScreensPerSec: Float = 0f,
        totalDisplacementPercent: Int = 0,
        occlusionEventsCount: Int = 0,
        fingerprintEvents: List<FingerprintUsageEvent> = emptyList(),
        extraNotes: List<String> = emptyList()
    ): List<String> {
        return generateAiAnalyticalReport(
            targetCardLabel = targetCardLabel,
            initialCoordinates = initialCoordinates,
            totalFrames = totalFrames,
            totalDurationMs = totalDurationMs,
            processingFps = processingFps,
            confidencePercent = confidencePercent,
            peakSpeedScreensPerSec = peakSpeedScreensPerSec,
            totalDisplacementPercent = totalDisplacementPercent,
            occlusionEventsCount = occlusionEventsCount,
            fingerprintEvents = fingerprintEvents,
            extraNotes = extraNotes
        )
    }
}
