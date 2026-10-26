package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.engine.CardMovementTracker
import com.example.engine.NativeOpticalFlowTracker
import com.example.model.CardPosition
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.model.TrackedFrame
import com.example.model.TrackingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NativeOpticalFlowTrackerTest {

    @Test
    fun testNativeOpticalFlowTrackerLifecycleAndFallback() {
        val framesDir = File("/tmp/full_frames")
        if (!framesDir.exists() || framesDir.listFiles()?.isEmpty() == true) {
            println("Frames directory not found, skipping video test")
            return
        }

        val frameFiles = framesDir.listFiles { file -> file.name.endsWith(".png") }
            ?.sortedBy { it.name } ?: emptyList()

        assertTrue("Frame files must be present", frameFiles.isNotEmpty())

        val targetBox = NormalizedRect.fromCenter(
            center = NormalizedPoint(0.430f, 0.530f),
            width = 0.14f,
            height = 0.36f
        )

        val firstBmp = BitmapFactory.decodeFile(frameFiles[0].absolutePath)
        assertNotNull("First frame bitmap must decode", firstBmp)

        val nativeTracker = NativeOpticalFlowTracker()
        nativeTracker.initialize(firstBmp, targetBox)

        val initialTracked = TrackedFrame(
            frameIndex = 0,
            timestampMs = 0L,
            rect = targetBox,
            center = NormalizedPoint(targetBox.centerX, targetBox.centerY),
            confidence = 1.0f,
            trackingState = TrackingState.TRACKING_LOCKED,
            isOccluded = false,
            inlierCount = 36,
            bitmap = firstBmp
        )

        val movementTracker = CardMovementTracker()
        movementTracker.init(initialTracked, CardPosition.LEFT)

        val sampledFrameFiles = frameFiles.filterIndexed { index, _ -> index % 2 == 0 }

        for (i in 1 until sampledFrameFiles.size) {
            val file = sampledFrameFiles[i]
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            val timestampMs = i * 50L
            val tracked = nativeTracker.processFrame(bmp, i, timestampMs)
            movementTracker.processFrame(tracked)
        }

        val lastBmp = BitmapFactory.decodeFile(sampledFrameFiles.last().absolutePath)
        val finalTracked = nativeTracker.processFrame(lastBmp, sampledFrameFiles.size, sampledFrameFiles.size * 50L)
        movementTracker.finalizeTracking(finalTracked)

        val moves = movementTracker.getMoveHistory()
        val finalPos = movementTracker.getCurrentPosition()

        assertEquals("Must log exactly 4 sequential moves", 4, moves.size)
        assertEquals("Move 1: Pos 1 -> Pos 2", CardPosition.LEFT, moves[0].fromPosition)
        assertEquals("Move 1: Pos 1 -> Pos 2", CardPosition.MIDDLE, moves[0].toPosition)
        assertEquals("Move 2: Pos 2 -> Pos 3", CardPosition.MIDDLE, moves[1].fromPosition)
        assertEquals("Move 2: Pos 2 -> Pos 3", CardPosition.RIGHT, moves[1].toPosition)
        assertEquals("Move 3: Pos 3 -> Pos 2", CardPosition.RIGHT, moves[2].fromPosition)
        assertEquals("Move 3: Pos 3 -> Pos 2", CardPosition.MIDDLE, moves[2].toPosition)
        assertEquals("Move 4: Pos 2 -> Pos 3", CardPosition.MIDDLE, moves[3].fromPosition)
        assertEquals("Move 4: Pos 2 -> Pos 3", CardPosition.RIGHT, moves[3].toPosition)
        assertEquals("Final position must be Right (Pos 3)", CardPosition.RIGHT, finalPos)

        nativeTracker.release()
    }
}
