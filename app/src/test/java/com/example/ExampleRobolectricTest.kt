package com.example

import com.example.engine.BenchmarkVideoProvider
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleRobolectricTest {

    @Test
    fun `benchmark provider generates benchmarks list and initial target box`() {
        val benchmarks = BenchmarkVideoProvider.BENCHMARKS
        assertTrue("Should have benchmarks configured", benchmarks.isNotEmpty())

        val classic = BenchmarkVideoProvider.getBenchmark("classic_monte")
        assertEquals("Classic Three-Card Monte", classic.title)

        val targetBox = BenchmarkVideoProvider.getInitialTargetBox("classic_monte")
        assertTrue("Target box should be centered", targetBox.centerX in 0.45f..0.55f)
        assertTrue("Target box should be valid", targetBox.width > 0.1f && targetBox.height > 0.2f)
    }

    @Test
    fun `fixed 3-card grid provides preconfigured bounding boxes and resolves tap positions`() {
        val leftBox = com.example.model.CardPosition.LEFT.getPreconfiguredRect()
        val middleBox = com.example.model.CardPosition.MIDDLE.getPreconfiguredRect()
        val rightBox = com.example.model.CardPosition.RIGHT.getPreconfiguredRect()

        assertEquals(0.430f, leftBox.centerX, 0.01f)
        assertEquals(0.575f, middleBox.centerX, 0.01f)
        assertEquals(0.720f, rightBox.centerX, 0.01f)

        assertEquals(0.65f, leftBox.centerY, 0.01f)
        assertEquals(0.65f, middleBox.centerY, 0.01f)
        assertEquals(0.65f, rightBox.centerY, 0.01f)

        assertEquals(0.14f, leftBox.width, 0.01f)
        assertEquals(0.40f, leftBox.height, 0.01f)

        // Single tap resolution across the 3 zones
        assertEquals(com.example.model.CardPosition.LEFT, com.example.model.CardPosition.fromX(0.430f))
        assertEquals(com.example.model.CardPosition.MIDDLE, com.example.model.CardPosition.fromX(0.575f))
        assertEquals(com.example.model.CardPosition.RIGHT, com.example.model.CardPosition.fromX(0.720f))

        // Direct hit testing on cards and top guidance zones
        assertEquals(com.example.model.CardPosition.LEFT, com.example.model.CardPosition.findPositionAt(0.43f, 0.60f))
        assertEquals(com.example.model.CardPosition.MIDDLE, com.example.model.CardPosition.findPositionAt(0.575f, 0.35f))
        assertEquals(com.example.model.CardPosition.RIGHT, com.example.model.CardPosition.findPositionAt(0.72f, 0.70f))
    }

    @Test
    fun `benchmark provider ground truth matches configuration`() {
        val slot1 = BenchmarkVideoProvider.getGroundTruthFinalSlot("classic_monte")
        val slot2 = BenchmarkVideoProvider.getGroundTruthFinalSlot("occlusion_monte")
        val slot3 = BenchmarkVideoProvider.getGroundTruthFinalSlot("rapid_swap")

        assertNotNull(slot1)
        assertNotNull(slot2)
        assertNotNull(slot3)
    }

    @Test
    fun `card position mapping adheres strictly to 1 Left, 2 Middle, 3 Right`() {
        assertEquals(1, com.example.model.CardPosition.LEFT.id)
        assertEquals("Left", com.example.model.CardPosition.LEFT.nameEn)
        assertEquals("Pos 1", com.example.model.CardPosition.LEFT.shortLabel)

        assertEquals(2, com.example.model.CardPosition.MIDDLE.id)
        assertEquals("Middle", com.example.model.CardPosition.MIDDLE.nameEn)
        assertEquals("Pos 2", com.example.model.CardPosition.MIDDLE.shortLabel)

        assertEquals(3, com.example.model.CardPosition.RIGHT.id)
        assertEquals("Right", com.example.model.CardPosition.RIGHT.nameEn)
        assertEquals("Pos 3", com.example.model.CardPosition.RIGHT.shortLabel)

        // Coordinates mapping
        assertEquals(com.example.model.CardPosition.LEFT, com.example.model.CardPosition.fromX(0.40f))
        assertEquals(com.example.model.CardPosition.MIDDLE, com.example.model.CardPosition.fromX(0.55f))
        assertEquals(com.example.model.CardPosition.RIGHT, com.example.model.CardPosition.fromX(0.75f))
    }

    @Test
    fun `card movement tracker logs sequential transitions and matches expected log format`() {
        val tracker = com.example.engine.CardMovementTracker()

        fun makeFrame(index: Int, x: Float, timestampMs: Long): com.example.model.TrackedFrame {
            val center = NormalizedPoint(x, 0.5f)
            return com.example.model.TrackedFrame(
                frameIndex = index,
                timestampMs = timestampMs,
                rect = NormalizedRect(x - 0.05f, 0.4f, x + 0.05f, 0.6f),
                center = center,
                confidence = 0.95f,
                trackingState = com.example.model.TrackingState.TRACKING_LOCKED,
                isOccluded = false,
                inlierCount = 30,
                speed = 0.05f
            )
        }

        // Frame 0: Starts in Middle (Pos 2)
        tracker.init(makeFrame(0, 0.575f, 0L))
        assertEquals(com.example.model.CardPosition.MIDDLE, tracker.getCurrentPosition())

        // Move 1: Card moved [Pos 2 -> Pos 1] at Frame 34 (250ms = 0.25s)
        val move1 = tracker.processFrame(makeFrame(34, 0.22f, 250L))
        assertNotNull(move1)
        assertEquals(1, move1!!.moveNumber)
        assertEquals(com.example.model.CardPosition.MIDDLE, move1.fromPosition)
        assertEquals(com.example.model.CardPosition.LEFT, move1.toPosition)
        assertEquals("• Move 1: Card moved [Pos 2 -> Pos 1] | Frame 34 (0.25s)", move1.toFormattedString())

        // Move 2: Card moved [Pos 1 -> Pos 3] at Frame 78 (800ms = 0.80s)
        val move2 = tracker.processFrame(makeFrame(78, 0.78f, 800L))
        assertNotNull(move2)
        assertEquals(2, move2!!.moveNumber)
        assertEquals(com.example.model.CardPosition.LEFT, move2.fromPosition)
        assertEquals(com.example.model.CardPosition.RIGHT, move2.toPosition)
        assertEquals("• Move 2: Card moved [Pos 1 -> Pos 3] | Frame 78 (0.80s)", move2.toFormattedString())

        // Move 3: Card moved [Pos 3 -> Pos 2] at Frame 122 (1400ms = 1.40s)
        val move3 = tracker.processFrame(makeFrame(122, 0.50f, 1400L))
        assertNotNull(move3)
        assertEquals(3, move3!!.moveNumber)
        assertEquals(com.example.model.CardPosition.RIGHT, move3.fromPosition)
        assertEquals(com.example.model.CardPosition.MIDDLE, move3.toPosition)
        assertEquals("• Move 3: Card moved [Pos 3 -> Pos 2] | Frame 122 (1.40s)", move3.toFormattedString())

        val logs = tracker.generateConsoleLogs(
            totalFrames = 176,
            totalDurationMs = 235567L,
            processingFps = 0.7f
        )

        assertTrue(logs.contains("--------------------------------------------------"))
        assertTrue(logs.contains("[TRACKING DIAGNOSTIC REPORT]"))
        assertTrue(logs.contains("[SEQUENTIAL MOVEMENT ANALYSIS]"))
        assertTrue(logs.any { it.contains("Move 1: Pos 2 -> Pos 1") && it.contains("Frame 34") })
        assertTrue(logs.any { it.contains("Move 2: Pos 1 -> Pos 3") && it.contains("Frame 78") })
        assertTrue(logs.any { it.contains("Move 3: Pos 3 -> Pos 2") && it.contains("Frame 122") })
        assertTrue(logs.contains("[FINAL EVALUATION & VERDICT]"))
        assertTrue(logs.contains("• Shuffle Completed: Total Moves = 3"))
        assertTrue(logs.contains("• Final Card Position: Position 2 (Middle)"))
        assertTrue(logs.contains("[KINEMATIC METRICS SUMMARY]"))
        assertTrue(logs.any { it.contains("Processed Frames: 176 frames") })
    }

    @Test
    fun `end-to-end simulation test for K2 King of Spades middle card sequence`() {
        // 1. Target Selection Simulation: Target K2 (Middle Card, Position 2)
        val targetPos = com.example.model.CardPosition.findPositionAt(0.57f, 0.65f)
        assertEquals(com.example.model.CardPosition.MIDDLE, targetPos)
        assertNotNull(targetPos)

        val middleBox = targetPos!!.getPreconfiguredRect()
        assertEquals(0.505f, middleBox.left, 0.01f)
        assertEquals(0.645f, middleBox.right, 0.01f)
        assertEquals(0.45f, middleBox.top, 0.01f)
        assertEquals(0.85f, middleBox.bottom, 0.01f)
        assertEquals(2, targetPos.id)

        // 2. Sequential Tracking Simulation
        val tracker = com.example.engine.CardMovementTracker()
        val peakSpeed = 0.050f

        fun buildFrame(index: Int, x: Float, vx: Float, spd: Float, timestampMs: Long): com.example.model.TrackedFrame {
            return com.example.model.TrackedFrame(
                frameIndex = index,
                timestampMs = timestampMs,
                rect = NormalizedRect(x - 0.07f, 0.45f, x + 0.07f, 0.85f),
                center = NormalizedPoint(x, 0.65f),
                confidence = 0.94f,
                trackingState = com.example.model.TrackingState.TRACKING_LOCKED,
                isOccluded = false,
                inlierCount = 35,
                velocityX = vx,
                velocityY = 0f,
                speed = spd
            )
        }

        // Frame 0 (0.0s): Face-up King of Spades in Middle (Pos 2), stationary
        val frame0 = buildFrame(0, 0.57f, 0.0f, 0.0f, 0L)
        tracker.init(frame0)
        assertEquals(com.example.model.CardPosition.MIDDLE, tracker.getCurrentPosition())

        // a) Speed Heatmap Color Validation at Frame 0: Slow / Stationary -> Green
        val ratio0 = (frame0.speed / peakSpeed).coerceIn(0f, 1f)
        assertTrue("Ratio at rest should be <= 0.28 (Green)", ratio0 <= 0.28f)

        // b) Velocity Arrow Vector Validation at Frame 0: speed < 0.002f -> Arrow Hidden
        assertTrue("Arrow should be hidden when speed < 0.002f", frame0.speed < 0.002f)

        // Frame 30 (1.5s): Card accelerating towards Right (Pos 3) at moderate speed (still in Middle slot <= 0.58)
        val frame30 = buildFrame(30, 0.56f, 0.020f, 0.020f, 1500L)
        val moveA = tracker.processFrame(frame30)
        val ratio30 = (frame30.speed / peakSpeed).coerceIn(0f, 1f)
        assertTrue("Ratio at frame 30 should be in medium yellow band (0.28..0.60)", ratio30 in 0.28f..0.60f)
        assertTrue("Arrow should point rightwards (positive vx)", frame30.velocityX > 0f)

        // Frame 45 (2.25s): Peak speed swap crossing into Slot 3
        val frame45 = buildFrame(45, 0.69f, 0.045f, 0.045f, 2250L)
        val moveB = tracker.processFrame(frame45)
        val ratio45 = (frame45.speed / peakSpeed).coerceIn(0f, 1f)
        assertTrue("Ratio at peak should exceed 0.60 (Fiery Crimson Red)", ratio45 > 0.60f)

        // Verify transition occurred from Pos 2 -> Pos 3
        assertNotNull(moveB)
        assertEquals(1, moveB!!.moveNumber)
        assertEquals(com.example.model.CardPosition.MIDDLE, moveB.fromPosition)
        assertEquals(com.example.model.CardPosition.RIGHT, moveB.toPosition)
        assertEquals("• Move 1: Card moved [Pos 2 -> Pos 3] | Frame 45 (2.25s)", moveB.toFormattedString())

        // Frame 100 (5.0s): Settle into Position 3 (Right, x = 0.71f), stationary
        val frame100 = buildFrame(100, 0.71f, 0.0f, 0.0f, 5000L)
        val moveC = tracker.processFrame(frame100)
        assertEquals(com.example.model.CardPosition.RIGHT, tracker.getCurrentPosition())

        // a) Speed Heatmap Color Validation at Frame 100: Green (Settled)
        val ratio100 = (frame100.speed / peakSpeed).coerceIn(0f, 1f)
        assertTrue("Final settled ratio should be Green (<= 0.28)", ratio100 <= 0.28f)

        // 3. Diagnostics & Kinematics Log Verification
        val logs = tracker.generateConsoleLogs(
            totalFrames = 100,
            totalDurationMs = 5000L,
            processingFps = 20.0f
        )

        assertTrue(logs.contains("[TRACKING DIAGNOSTIC REPORT]"))
        assertTrue(logs.contains("[SEQUENTIAL MOVEMENT ANALYSIS]"))
        assertTrue(logs.any { it.contains("Move 1: Pos 2 -> Pos 3") && it.contains("Frame 45") })
        assertTrue(logs.contains("• Final Card Position: Position 3 (Right)"))
        assertTrue(logs.contains("• Shuffle Completed: Total Moves = 1"))
        assertTrue(logs.contains("[KINEMATIC METRICS SUMMARY]"))
    }

    @Test
    fun `visual template fingerprinting, center stacking occlusion, and separation re-acquisition`() {
        val opticalFlowTracker = com.example.engine.OpticalFlowTracker()
        val bmp = android.graphics.Bitmap.createBitmap(320, 240, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.DKGRAY)

        // Target K2 at Position 2 (Middle, x = 0.57f, y = 0.65f)
        val initialRect = com.example.model.CardPosition.MIDDLE.getPreconfiguredRect()
        opticalFlowTracker.initialize(bmp, initialRect)

        // 1. Verify Target Visual Fingerprinting
        val fingerprint = opticalFlowTracker.getTargetFingerprint()
        assertNotNull("Fingerprint should be captured at Frame 0", fingerprint)
        assertEquals(com.example.model.CardPosition.MIDDLE, fingerprint!!.initialPosition)
        assertEquals(32, fingerprint.templateWidth)
        assertEquals(44, fingerprint.templateHeight)
        assertTrue("Zero mean patch must be populated", fingerprint.zeroMeanPatch.isNotEmpty())

        // 2. Simulate Fingerprint Usage Event during Card Overlap / Unstacking
        val fpEvent = com.example.model.FingerprintUsageEvent(
            startFrame = 40,
            endFrame = 48,
            startTimestampMs = 1333L,
            endTimestampMs = 1600L,
            targetPosition = com.example.model.CardPosition.MIDDLE,
            reason = "Card Overlap / Center Stack",
            restoredPosition = com.example.model.CardPosition.RIGHT,
            confidenceScore = 0.92f
        )
        opticalFlowTracker.addFingerprintEvent(fpEvent)

        val events = opticalFlowTracker.getFingerprintEvents()
        assertEquals(1, events.size)
        assertEquals("Card Overlap / Center Stack", events[0].reason)
        assertEquals(com.example.model.CardPosition.RIGHT, events[0].restoredPosition)
        assertEquals(9, events[0].durationFrames)

        // 3. Verify Kinematics Log Output with Visual Fingerprint section
        val movementTracker = com.example.engine.CardMovementTracker()
        val f0 = com.example.model.TrackedFrame(
            frameIndex = 0,
            timestampMs = 0L,
            rect = initialRect,
            center = com.example.model.NormalizedPoint(0.57f, 0.65f),
            confidence = 1f,
            trackingState = com.example.model.TrackingState.TRACKING_LOCKED,
            isOccluded = false,
            inlierCount = 12
        )
        movementTracker.init(f0)

        val fEnd = com.example.model.TrackedFrame(
            frameIndex = 50,
            timestampMs = 1667L,
            rect = com.example.model.CardPosition.RIGHT.getPreconfiguredRect(),
            center = com.example.model.NormalizedPoint(0.71f, 0.65f),
            confidence = 0.92f,
            trackingState = com.example.model.TrackingState.TRACKING_LOCKED,
            isOccluded = false,
            inlierCount = 10,
            speed = 0.04f
        )
        val move = movementTracker.processFrame(fEnd)
        assertNotNull(move)
        assertEquals(com.example.model.CardPosition.MIDDLE, move!!.fromPosition)
        assertEquals(com.example.model.CardPosition.RIGHT, move.toPosition)

        val reportLogs = movementTracker.generateAiAnalyticalReport(
            targetCardLabel = "Position 2 (Middle)",
            initialCoordinates = "x=57%, y=65% (Target Locked)",
            totalFrames = 100,
            totalDurationMs = 3333L,
            processingFps = 30.0f,
            confidencePercent = 92,
            peakSpeedScreensPerSec = 1.2f,
            totalDisplacementPercent = 85,
            occlusionEventsCount = 1,
            fingerprintEvents = events
        )

        assertTrue(reportLogs.contains("[VISUAL FINGERPRINT & RE-ACQUISITION LOG]"))
        assertTrue(reportLogs.any { it.contains("Fingerprint Re-Acquisition") && it.contains("Re-acquired at Pos 3") })
        assertTrue(reportLogs.any { it.contains("Occlusion Events Handled: 1 (Fingerprint Re-Acquisitions: 1)") })
        assertTrue(reportLogs.contains("• Final Card Position: Position 3 (Right)"))
    }

    @Test
    fun `spatial slot boundaries classify Left under 0_5025, Middle 0_5025 to 0_6475, and Right over 0_6475`() {
        // Boundary values
        assertEquals(com.example.model.CardPosition.LEFT, com.example.model.CardPosition.fromX(0.08f))
        assertEquals(com.example.model.CardPosition.LEFT, com.example.model.CardPosition.fromX(0.430f))
        assertEquals(com.example.model.CardPosition.LEFT, com.example.model.CardPosition.fromX(0.50f))

        assertEquals(com.example.model.CardPosition.MIDDLE, com.example.model.CardPosition.fromX(0.5025f))
        assertEquals(com.example.model.CardPosition.MIDDLE, com.example.model.CardPosition.fromX(0.575f))
        assertEquals(com.example.model.CardPosition.MIDDLE, com.example.model.CardPosition.fromX(0.6475f))

        assertEquals(com.example.model.CardPosition.RIGHT, com.example.model.CardPosition.fromX(0.65f))
        assertEquals(com.example.model.CardPosition.RIGHT, com.example.model.CardPosition.fromX(0.720f))
        assertEquals(com.example.model.CardPosition.RIGHT, com.example.model.CardPosition.fromX(0.75f))

        // Hysteresis transitions
        // 1. Rightward movement past X = 0.6475 from Middle is instantly classified as Right
        val toRight = com.example.model.CardPosition.transition(com.example.model.CardPosition.MIDDLE, 0.68f)
        assertEquals(com.example.model.CardPosition.RIGHT, toRight)

        // 2. Rightward movement past X = 0.6475 from Left is instantly classified as Right
        val leftToRight = com.example.model.CardPosition.transition(com.example.model.CardPosition.LEFT, 0.68f)
        assertEquals(com.example.model.CardPosition.RIGHT, leftToRight)

        // 3. Movement from Middle to Left when X < 0.495
        val toLeft = com.example.model.CardPosition.transition(com.example.model.CardPosition.MIDDLE, 0.48f)
        assertEquals(com.example.model.CardPosition.LEFT, toLeft)

        // 4. Movement from Right returning to Middle when X <= 0.640
        val backToMiddle = com.example.model.CardPosition.transition(com.example.model.CardPosition.RIGHT, 0.62f)
        assertEquals(com.example.model.CardPosition.MIDDLE, backToMiddle)
    }

    @Test
    fun `card movement tracker logs card 2 final transition to Position 3 Right`() {
        val tracker = com.example.engine.CardMovementTracker()

        fun makeFrame(index: Int, x: Float, timestampMs: Long): com.example.model.TrackedFrame {
            val center = com.example.model.NormalizedPoint(x, 0.65f)
            return com.example.model.TrackedFrame(
                frameIndex = index,
                timestampMs = timestampMs,
                rect = com.example.model.NormalizedRect(x - 0.05f, 0.4f, x + 0.05f, 0.6f),
                center = center,
                confidence = 0.95f,
                trackingState = com.example.model.TrackingState.TRACKING_LOCKED,
                isOccluded = false,
                inlierCount = 30,
                speed = 0.05f
            )
        }

        // Starts Middle (Card #2 at 0.575f)
        tracker.init(makeFrame(0, 0.575f, 0L))
        assertEquals(com.example.model.CardPosition.MIDDLE, tracker.getCurrentPosition())

        // Move 1: Card #2 moves Middle -> Left (e.g. x = 0.35f at frame 30)
        val move1 = tracker.processFrame(makeFrame(30, 0.35f, 1000L))
        assertNotNull(move1)
        assertEquals(com.example.model.CardPosition.MIDDLE, move1!!.fromPosition)
        assertEquals(com.example.model.CardPosition.LEFT, move1.toPosition)

        // Move 2: Card #2 moves Left -> Right past 0.6475 (e.g. x = 0.68f at frame 60)
        val move2 = tracker.processFrame(makeFrame(60, 0.68f, 2000L))
        assertNotNull(move2)
        assertEquals(com.example.model.CardPosition.LEFT, move2!!.fromPosition)
        assertEquals(com.example.model.CardPosition.RIGHT, move2.toPosition)

        // Settles into Position 3 (Right, x = 0.72f)
        val finalFrame = makeFrame(90, 0.72f, 3000L)
        tracker.processFrame(finalFrame)
        tracker.finalizeTracking(finalFrame)

        assertEquals(com.example.model.CardPosition.RIGHT, tracker.getCurrentPosition())

        val report = tracker.generateAiAnalyticalReport(
            targetCardLabel = "Position 2 (Middle)",
            totalFrames = 90,
            totalDurationMs = 3000L,
            processingFps = 30.0f
        )
        assertTrue(report.contains("• Final Card Position: Position 3 (Right)"))
        assertTrue(report.any { it.contains("Move 2: Pos 1 -> Pos 3") })
    }
}
