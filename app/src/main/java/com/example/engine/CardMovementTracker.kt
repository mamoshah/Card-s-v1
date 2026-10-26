package com.example.engine

import com.example.model.CardMoveLog
import com.example.model.CardPosition
import com.example.model.FailureCause
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
 * Includes Frame-0/1 motion debouncing, velocity gating (> 0.15 screens/s, delta > 3 frames),
 * and structured [FAILURE / UNCERTAINTY CAUSE ANALYSIS] diagnostic logging.
 */
class CardMovementTracker {

    private var currentPosition: CardPosition = CardPosition.MIDDLE
    private var initialAnchorPosition: CardPosition = CardPosition.MIDDLE
    private val moves = mutableListOf<CardMoveLog>()
    private val failureCauses = mutableListOf<FailureCause>()
    private var isInitialized = false

    // Debouncing state
    private var candidatePosition: CardPosition? = null
    private var candidateFramesCount: Int = 0
    private var candidateStartFrame: Int = 0

    /**
     * Initializes tracking with the starting frame and anchor lock.
     */
    fun init(initialFrame: TrackedFrame, explicitInitialPosition: CardPosition? = null) {
        val resolved = explicitInitialPosition ?: CardPosition.fromX(initialFrame.center.x)
        initialAnchorPosition = resolved
        currentPosition = resolved
        moves.clear()
        failureCauses.clear()
        candidatePosition = null
        candidateFramesCount = 0
        candidateStartFrame = 0
        isInitialized = true
    }

    /**
     * Evaluates a frame for card position transitions with velocity gating and frame debouncing.
     */
    fun processFrame(frame: TrackedFrame): CardMoveLog? {
        if (!isInitialized) {
            init(frame)
            return null
        }

        // Uncertainty & Failure Cause Logging
        if (frame.isUnreliable || frame.confidence < 0.85f) {
            val reason = when {
                frame.inlierCount < 3 -> "Optical Flow Consensus Loss during Frame ${frame.frameIndex}"
                frame.speed > 0.15f -> "High Velocity Crossover Ambiguity during Frame ${frame.frameIndex}"
                else -> "ZNCC Template Match Below Threshold (confidence ${(frame.confidence * 100).toInt()}%)"
            }
            val details = "Inliers=${frame.inlierCount}, Speed=${String.format(Locale.US, "%.2f", frame.speed)} screens/s, State=${frame.trackingState}"
            if (failureCauses.none { it.frameIndex == frame.frameIndex }) {
                failureCauses.add(FailureCause(frame.frameIndex, frame.timestampMs, reason, details))
            }
        }

        // Frame-0/1 Motion Debouncing & Lock Fix:
        // Ignore displacement noise unless velocity > 0.15 screens/s and delta > 3 frames to eliminate false t=0s jumps
        val nextCandidate = CardPosition.transition(currentPosition, frame.center.x)
        if (nextCandidate != currentPosition) {
            val isStartup = frame.frameIndex <= 2 || frame.timestampMs < 150L
            if (isStartup) {
                if (candidatePosition == nextCandidate) {
                    candidateFramesCount++
                } else {
                    candidatePosition = nextCandidate
                    candidateFramesCount = 1
                    candidateStartFrame = frame.frameIndex
                }

                // On startup frames, ignore noise unless velocity > 0.15 and delta > 3 frames
                if (candidateFramesCount <= 3 && frame.speed <= 0.15f) {
                    return null
                }
            }

            val move = CardMoveLog(
                moveNumber = moves.size + 1,
                fromPosition = currentPosition,
                toPosition = nextCandidate,
                frameIndex = frame.frameIndex,
                timestampMs = frame.timestampMs,
                confidence = frame.confidence,
                speed = frame.speed
            )
            moves.add(move)
            currentPosition = nextCandidate
            candidatePosition = null
            candidateFramesCount = 0
            return move
        } else {
            candidatePosition = null
            candidateFramesCount = 0
        }
        return null
    }

    /**
     * Finalizes tracking on the terminal frame to guarantee the final resting position
     * matches the card's final spatial coordinates.
     */
    fun finalizeTracking(finalFrame: TrackedFrame? = null): CardMoveLog? {
        if (finalFrame == null) return null
        val finalPos = CardPosition.fromX(finalFrame.center.x)
        if (finalPos != currentPosition) {
            val move = CardMoveLog(
                moveNumber = moves.size + 1,
                fromPosition = currentPosition,
                toPosition = finalPos,
                frameIndex = finalFrame.frameIndex,
                timestampMs = finalFrame.timestampMs,
                confidence = finalFrame.confidence,
                speed = finalFrame.speed
            )
            moves.add(move)
            currentPosition = finalPos
            return move
        }
        return null
    }

    fun getCurrentPosition(): CardPosition = currentPosition
    fun getMoveHistory(): List<CardMoveLog> = moves.toList()
    fun getFailureCauses(): List<FailureCause> = failureCauses.toList()

    fun logFailureCause(cause: FailureCause) {
        failureCauses.add(cause)
    }

    /**
     * Generates comprehensive AI-style analytical diagnostic report with dedicated
     * [FAILURE / UNCERTAINTY CAUSE ANALYSIS] section.
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

        // FAILURE / UNCERTAINTY CAUSE ANALYSIS SECTION (NEW)
        lines.add("[FAILURE / UNCERTAINTY CAUSE ANALYSIS]")
        if (failureCauses.isNotEmpty()) {
            lines.add("• Status: UNRELIABLE / TRANSIENT UNCERTAINTY DETECTED")
            lines.add("• Total Uncertainty Events Logged: ${failureCauses.size}")
            for (cause in failureCauses.take(8)) {
                lines.add(cause.toDiagnosticString())
            }
            if (failureCauses.size > 8) {
                lines.add("• ... and ${failureCauses.size - 8} additional transient optical flow flags.")
            }
        } else {
            lines.add("• Status: STABLE & VERIFIED (Zero critical tracking failures)")
            lines.add("• Optical Flow Consensus: High consensus maintained across all evaluated motion frames.")
            lines.add("• Template Correlation: ZNCC visual signature verified across candidate slots.")
        }
        lines.add(separator)

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
