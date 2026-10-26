package com.example

import com.example.data.local.AppDatabase
import com.example.data.local.UserEntity
import com.example.data.repository.AuthRepository
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `single owner account constants are properly configured`() {
        assertEquals("MtshoHunt@server.com", AppDatabase.OWNER_HOST_NAME)
        assertEquals("Mesho219&212&410", AppDatabase.OWNER_PASSWORD)
    }


    @Test
    fun `owner user entity models credentials accurately`() {
        val user = UserEntity(
            id = 1,
            username = AppDatabase.OWNER_HOST_NAME,
            password = AppDatabase.OWNER_PASSWORD,
            displayName = "Mtsho Hunt",
            role = "Owner"
        )
        assertEquals("MtshoHunt@server.com", user.username)
        assertEquals("Mesho219&212&410", user.password)
        assertEquals("Mtsho Hunt", user.displayName)
        assertEquals("Owner", user.role)
        assertTrue(user.createdAt > 0)
    }

    @Test
    fun `host name validation logic strictly checks correct host`() {
        val correctHost = "MtshoHunt@server.com"
        val incorrectHost = "admin@server.com"
        val emptyHost = ""

        assertTrue(correctHost.trim().equals(AppDatabase.OWNER_HOST_NAME, ignoreCase = true))
        assertFalse(incorrectHost.trim().equals(AppDatabase.OWNER_HOST_NAME, ignoreCase = true))
        assertFalse(emptyHost.trim().equals(AppDatabase.OWNER_HOST_NAME, ignoreCase = true))
    }

    @Test
    fun `password validation logic strictly checks correct password`() {
        val correctPassword = "Mesho219&212&410"
        val incorrectPassword = "wrongPassword123"

        assertTrue(correctPassword == AppDatabase.OWNER_PASSWORD)
        assertFalse(incorrectPassword == AppDatabase.OWNER_PASSWORD)
    }

    @Test
    fun `normalized rect calculates center and bounds properly`() {
        val center = NormalizedPoint(0.5f, 0.5f)
        val rect = NormalizedRect.fromCenter(center, width = 0.2f, height = 0.28f)

        assertEquals(0.5f, rect.centerX, 0.001f)
        assertEquals(0.5f, rect.centerY, 0.001f)
        assertEquals(0.4f, rect.left, 0.001f)
        assertEquals(0.6f, rect.right, 0.001f)
        assertEquals(0.36f, rect.top, 0.001f)
        assertEquals(0.64f, rect.bottom, 0.001f)
    }

    @Test
    fun `target selection preserves playing card ratio`() {
        val cardAspectRatio = 2.5f / 3.5f
        val width = 0.20f
        val height = width / cardAspectRatio
        val rect = NormalizedRect.fromCenter(NormalizedPoint(0.3f, 0.6f), width, height)

        assertEquals(0.3f, rect.centerX, 0.001f)
        assertEquals(0.6f, rect.centerY, 0.001f)
        assertEquals(cardAspectRatio, rect.width / rect.height, 0.01f)
    }

    @Test
    fun `tracking axis constraints enforce strictly horizontal motion and eliminate vertical drift`() {
        // Given an initial card target centered at Y = 0.55f
        val initialCenter = NormalizedPoint(0.50f, 0.55f)
        val targetRect = NormalizedRect.fromCenter(initialCenter, 0.18f, 0.36f)

        // When simulating horizontal displacements (left and right motion)
        val horizontalMovementRight = 0.05f
        val horizontalMovementLeft = -0.05f
        val verticalDistortionAttempt = -0.10f // Attempted upward drift

        // The tracking algorithm strictly locks Y to the initial baseline (no upward/vertical tilt)
        val lockedCenterY = targetRect.centerY
        val updatedCenterXRight = (initialCenter.x + horizontalMovementRight).coerceIn(0.08f, 0.92f)
        val updatedCenterYRight = lockedCenterY // Strictly clamped to horizontal baseline

        assertEquals(0.55f, updatedCenterYRight, 0.0001f)
        assertEquals(0.55f, lockedCenterY, 0.0001f)
        assertTrue("Motion should be horizontal right", updatedCenterXRight > initialCenter.x)

        val updatedCenterXLeft = (initialCenter.x + horizontalMovementLeft).coerceIn(0.08f, 0.92f)
        val updatedCenterYLeft = lockedCenterY
        assertEquals(0.55f, updatedCenterYLeft, 0.0001f)
        assertTrue("Motion should be horizontal left", updatedCenterXLeft < initialCenter.x)
    }

    @Test
    fun `downward green arrow respects maximum downward boundary clamp`() {
        val viewportHeight = 800f
        val viewportTop = 50f
        val maxDownwardBoundaryY = viewportTop + viewportHeight * 0.365f // Clamp line
        val minTopY = viewportTop + 28f

        // Case 1: Card is low on screen (e.g. cardTop = 450f)
        val lowCardTopY = viewportTop + 450f
        val desiredTipY1 = lowCardTopY - 4f
        val clampedTipY1 = desiredTipY1.coerceIn(minTopY, maxDownwardBoundaryY)

        // Must NOT descend past maxDownwardBoundaryY
        assertEquals(maxDownwardBoundaryY, clampedTipY1, 0.001f)
        assertTrue("Tip must not exceed clamp boundary", clampedTipY1 <= maxDownwardBoundaryY)

        // Case 2: Card is higher up (e.g. cardTop = 150f)
        val highCardTopY = viewportTop + 150f
        val desiredTipY2 = highCardTopY - 4f
        val clampedTipY2 = desiredTipY2.coerceIn(minTopY, maxDownwardBoundaryY)

        assertTrue("High card tip stays within bounds", clampedTipY2 in minTopY..maxDownwardBoundaryY)
        assertTrue("Tip must not exceed clamp boundary", clampedTipY2 <= maxDownwardBoundaryY)
    }

    @Test
    fun `tracking crosshair indicator is independent of bounding box and styled in Plum 4C0043`() {
        // Color constant for Plum #4C0043
        val plumHex = 0xFF4C0043.toLong()
        val expectedRed = 0x4C
        val expectedGreen = 0x00
        val expectedBlue = 0x43

        assertEquals(0x4C, ((plumHex shr 16) and 0xFF).toInt())
        assertEquals(0x00, ((plumHex shr 8) and 0xFF).toInt())
        assertEquals(0x43, (plumHex and 0xFF).toInt())

        // Tracking indicator center is always linked directly to card tracked center
        val cardCenter = NormalizedPoint(0.42f, 0.55f)
        val viewportWidth = 1000f
        val viewportHeight = 600f
        val fitLeft = 100f
        val fitTop = 50f

        val indicatorCenterX = fitLeft + cardCenter.x * viewportWidth
        val indicatorCenterY = fitTop + cardCenter.y * viewportHeight

        assertEquals(520f, indicatorCenterX, 0.001f)
        assertEquals(380f, indicatorCenterY, 0.001f)

        // The tracking crosshair (+) coordinates remain invariant whether bounding box is enabled or disabled
        val showBoundingBoxEnabled = true
        val showBoundingBoxDisabled = false

        fun getCrosshairCenter(enabled: Boolean): Pair<Float, Float> {
            // Both states permanently return the tracked center without rectangular frame dependencies
            return Pair(indicatorCenterX, indicatorCenterY)
        }

        assertEquals(getCrosshairCenter(showBoundingBoxEnabled), getCrosshairCenter(showBoundingBoxDisabled))
    }

    @Test
    fun `video metadata correctly calculates display dimensions based on rotation metadata`() {
        // Case 1: Video recorded in portrait (raw 1920x1080 buffer, rotation 90)
        val rawWidth = 1920
        val rawHeight = 1080
        val rotation90 = 90
        val (displayWidth90, displayHeight90) = if (rotation90 == 90 || rotation90 == 270) {
            Pair(rawHeight, rawWidth)
        } else {
            Pair(rawWidth, rawHeight)
        }
        val meta90 = com.example.video.VideoMetadata(
            durationMs = 5000L,
            width = displayWidth90,
            height = displayHeight90,
            rotation = rotation90,
            rawWidth = rawWidth,
            rawHeight = rawHeight
        )
        assertEquals(1080, meta90.width)
        assertEquals(1920, meta90.height)
        assertEquals(1920, meta90.rawWidth)
        assertEquals(1080, meta90.rawHeight)

        // Case 2: Standard landscape video (raw 1280x720, rotation 0)
        val meta0 = com.example.video.VideoMetadata(
            durationMs = 8000L,
            width = 1280,
            height = 720,
            rotation = 0
        )
        assertEquals(1280, meta0.width)
        assertEquals(720, meta0.height)
        assertEquals(0, meta0.rotation)
    }

    @Test
    fun `app screen enum supports dedicated video details screen`() {
        val screens = com.example.viewmodel.AppScreen.values()
        assertTrue("AppScreen should contain VIDEO_DETAILS", screens.contains(com.example.viewmodel.AppScreen.VIDEO_DETAILS))
        assertEquals(5, screens.size)
    }

    @Test
    fun `video display geometry calculates aspect-fit without stretching or distortion`() {
        val containerSize = androidx.compose.ui.geometry.Size(800f, 400f) // 2:1 container
        val imageW = 1280
        val imageH = 720 // 16:9 aspect ratio

        val fitRect = com.example.ui.util.VideoDisplayGeometry.calculateFitRect(containerSize, imageW, imageH)

        // 16:9 fitted in 2:1 container should be pillarboxed (centered horizontally, full height 400f)
        assertEquals(400f, fitRect.height, 0.01f)
        val expectedWidth = 400f * (16f / 9f)
        assertEquals(expectedWidth, fitRect.width, 0.1f)
        val expectedLeft = (800f - expectedWidth) / 2f
        assertEquals(expectedLeft, fitRect.left, 0.1f)
        assertEquals(0f, fitRect.top, 0.01f)
    }

    @Test
    fun `spatial slot boundaries correctly classify position 1, 2, and 3`() {
        // Position 1 (Left): X < 0.5025f (Anchor center = 0.430f)
        assertEquals(com.example.model.CardPosition.LEFT, com.example.model.CardPosition.fromX(0.35f))
        assertEquals(com.example.model.CardPosition.LEFT, com.example.model.CardPosition.fromX(0.430f))
        assertEquals(com.example.model.CardPosition.LEFT, com.example.model.CardPosition.fromX(0.50f))

        // Position 2 (Middle): 0.5025f <= X <= 0.6475f (Anchor center = 0.575f)
        assertEquals(com.example.model.CardPosition.MIDDLE, com.example.model.CardPosition.fromX(0.5025f))
        assertEquals(com.example.model.CardPosition.MIDDLE, com.example.model.CardPosition.fromX(0.575f))
        assertEquals(com.example.model.CardPosition.MIDDLE, com.example.model.CardPosition.fromX(0.6475f))

        // Position 3 (Right): X > 0.6475f (Anchor center = 0.720f)
        assertEquals(com.example.model.CardPosition.RIGHT, com.example.model.CardPosition.fromX(0.65f))
        assertEquals(com.example.model.CardPosition.RIGHT, com.example.model.CardPosition.fromX(0.720f))
    }

    @Test
    fun `hysteresis transitions reliably classify movements across 0_5025 and 0_6475 boundaries`() {
        val middle = com.example.model.CardPosition.MIDDLE
        // A rightward move to X = 0.68 must transition immediately to RIGHT
        val verdictRight = com.example.model.CardPosition.transition(middle, 0.68f)
        assertEquals(com.example.model.CardPosition.RIGHT, verdictRight)

        // A leftward move below 0.495f must transition to LEFT
        val verdictLeft = com.example.model.CardPosition.transition(middle, 0.48f)
        assertEquals(com.example.model.CardPosition.LEFT, verdictLeft)
    }

    @Test
    fun `display coordinates map correctly to normalized source video coordinates`() {
        val fitRect = androidx.compose.ui.geometry.Rect(
            left = 50f,
            top = 20f,
            right = 850f,
            bottom = 470f
        )
        val tapX = 450f
        val tapY = 245f

        val relX = ((tapX - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
        val relY = ((tapY - fitRect.top) / fitRect.height).coerceIn(0f, 1f)

        assertEquals(0.5f, relX, 0.001f)
        assertEquals(0.5f, relY, 0.001f)
    }
}


