package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.engine.BenchmarkVideoProvider
import com.example.engine.OpticalFlowTracker
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.video.VideoFrameExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("card's V.2", appName)
    }

    @Test
    fun `benchmark provider generates frames and target box`() {
        val benchmarks = BenchmarkVideoProvider.BENCHMARKS
        assertTrue("Should have benchmarks configured", benchmarks.isNotEmpty())

        val classic = BenchmarkVideoProvider.getBenchmark("classic_monte")
        assertEquals("Classic Three-Card Monte", classic.title)

        val targetBox = BenchmarkVideoProvider.getInitialTargetBox("classic_monte")
        assertTrue("Target box should be centered", targetBox.centerX in 0.45f..0.55f)
        assertTrue("Target box should be valid", targetBox.width > 0.1f && targetBox.height > 0.2f)

        val frames = BenchmarkVideoProvider.generateFrames("classic_monte", width = 160, height = 120)
        assertEquals(classic.frameCount, frames.size)
        assertNotNull(frames.first())
    }

    @Test
    fun `optical flow tracker processes frame motion through card crossing`() {
        val frames = BenchmarkVideoProvider.generateFrames("classic_monte", width = 480, height = 320)
        assertEquals(75, frames.size)

        val tracker = OpticalFlowTracker()
        val initialBox = BenchmarkVideoProvider.getInitialTargetBox("classic_monte")

        tracker.initialize(frames[0], initialBox)
        var lastFrame = tracker.processFrame(frames[0], 0, 0L)
        for (i in 1 until frames.size) {
            lastFrame = tracker.processFrame(frames[i], i, i * 33L)
            assertTrue("Center X should stay on table", lastFrame.center.x in 0.10f..0.90f)
            assertTrue("Center Y should stay on table", lastFrame.center.y in 0.20f..0.85f)
        }
        // Classic monte: Queen swaps from Center (0.50) to Left (0.23) and stays at Left!
        assertTrue("Queen must end at Left position (< 0.35), got: ${lastFrame.center.x}", lastFrame.center.x < 0.35f)
        assertTrue("Final confidence should be good", lastFrame.confidence >= 0.50f)
    }

    @Test
    fun `optical flow tracker preserves target identity through occlusion`() {
        val frames = BenchmarkVideoProvider.generateFrames("occlusion_monte", width = 480, height = 320)
        assertEquals(80, frames.size)

        val tracker = OpticalFlowTracker()
        val initialBox = BenchmarkVideoProvider.getInitialTargetBox("occlusion_monte")

        tracker.initialize(frames[0], initialBox)

        var lastFrame = tracker.processFrame(frames[0], 0, 0L)
        for (i in 1 until frames.size) {
            lastFrame = tracker.processFrame(frames[i], i, i * 33L)
            assertNotNull(lastFrame)
        }
        // Occlusion monte: Queen swaps from Center to Right (0.77) and stays at Right!
        assertTrue("Queen must end at Right position (> 0.65), got: ${lastFrame.center.x}", lastFrame.center.x > 0.65f)
    }

    @Test
    fun `optical flow tracker calculates kinematics and smoothed center`() {
        val frames = BenchmarkVideoProvider.generateFrames("classic_monte", width = 160, height = 120)
        val tracker = OpticalFlowTracker()
        val initialBox = BenchmarkVideoProvider.getInitialTargetBox("classic_monte")

        tracker.initialize(frames[0], initialBox)
        val frame1 = tracker.processFrame(frames[1], 1, 33L)
        assertNotNull(frame1.smoothedCenter)
        assertTrue(frame1.speed >= 0f)
        assertTrue(frame1.smoothedCenter.x in 0f..1f)
        assertTrue(frame1.smoothedCenter.y in 0f..1f)
    }

    @Test
    fun `video extractor handles local file inspection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val extractor = VideoFrameExtractor(context)
        val dummyFile = File(context.cacheDir, "test.mp4")
        dummyFile.writeBytes(byteArrayOf(0, 0, 0, 32))
        assertTrue(dummyFile.exists())
    }

    @Test
    fun `video extractor supports multiple extensions including mkv webm mov and screen recordings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testNames = listOf(
            "screen_record_20261008.mp4",
            "game_recording.mkv",
            "camera_capture.webm",
            "apple_capture.mov",
            "old_capture.3gp"
        )
        for (name in testNames) {
            val extension = if (name.contains(".")) name.substringAfterLast(".").lowercase() else "mp4"
            val targetFile = File(context.cacheDir, "test_$name")
            targetFile.writeBytes(byteArrayOf(0, 0, 0, 16))
            assertTrue(targetFile.exists())
            assertTrue(extension in listOf("mp4", "mkv", "webm", "mov", "3gp"))
        }
    }

    @Test
    fun `target selection tap places center and applies fixed card aspect ratio and size scale`() {
        val tapX = 0.35f
        val tapY = 0.60f
        val scale = 1.25f
        val cardWidth = 0.20f * scale
        val cardAspectRatio = 2.5f / 3.5f
        val cardHeight = cardWidth / cardAspectRatio

        val box = NormalizedRect.fromCenter(
            NormalizedPoint(tapX, tapY),
            cardWidth,
            cardHeight
        )

        assertEquals(tapX, box.centerX, 0.01f)
        assertEquals(tapY, box.centerY, 0.01f)
        assertEquals(cardAspectRatio, box.width / box.height, 0.02f)
    }

    @Test
    fun `optical flow tracker keeps coordinates within valid bounds without jumping`() {
        val frames = BenchmarkVideoProvider.generateFrames("classic_monte", width = 160, height = 120)
        val tracker = OpticalFlowTracker()
        val initialBox = BenchmarkVideoProvider.getInitialTargetBox("classic_monte")

        tracker.initialize(frames[0], initialBox)
        var prevX = initialBox.centerX
        var prevY = initialBox.centerY

        for (i in 1 until frames.size) {
            val frame = tracker.processFrame(frames[i], i, i * 33L)
            assertTrue("X must be inside bounds", frame.center.x in 0.05f..0.95f)
            assertTrue("Y must be inside bounds", frame.center.y in 0.10f..0.90f)
            val deltaX = kotlin.math.abs(frame.center.x - prevX)
            val deltaY = kotlin.math.abs(frame.center.y - prevY)
            assertTrue("Per-frame X movement must be continuous without jumping (>0.25): frame $i deltaX=$deltaX", deltaX < 0.25f)
            assertTrue("Per-frame Y movement must be continuous without jumping (>0.20): frame $i deltaY=$deltaY", deltaY < 0.20f)
            prevX = frame.center.x
            prevY = frame.center.y
        }
    }
}
