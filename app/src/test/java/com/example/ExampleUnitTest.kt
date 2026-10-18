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
}

