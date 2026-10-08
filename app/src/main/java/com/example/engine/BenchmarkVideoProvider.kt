package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.example.model.BenchmarkVideo
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates realistic diagnostic benchmark video sequences for testing
 * fast-motion optical-flow tracking, Three-Card Monte shuffles, and hand occlusions.
 */
object BenchmarkVideoProvider {

    val BENCHMARKS = listOf(
        BenchmarkVideo(
            id = "classic_monte",
            title = "Classic Three-Card Monte",
            subtitle = "Standard 2-swap shuffle at medium speed",
            description = "Track the Queen of Hearts (Center card) through 2 smooth swaps on a green felt table.",
            difficulty = "Normal",
            durationSec = 3.0f,
            frameCount = 75,
            targetCardName = "Queen of Hearts (Center)"
        ),
        BenchmarkVideo(
            id = "occlusion_monte",
            title = "High-Speed Occlusion Shuffle",
            subtitle = "Dealer hands covering cards during rapid swaps",
            description = "Stress test for optical flow: rapid card swaps while dealer hands repeatedly occlude the card.",
            difficulty = "Hard",
            durationSec = 3.2f,
            frameCount = 80,
            targetCardName = "Queen of Hearts (Center)"
        ),
        BenchmarkVideo(
            id = "rapid_cross_monte",
            title = "Rapid Cross-Swap Stress Test",
            subtitle = "Triple cross-swap with tight card overlaps",
            description = "Diagnostic stress test with fast overlapping motion, identical distractors, and rapid direction changes.",
            difficulty = "Expert",
            durationSec = 3.5f,
            frameCount = 85,
            targetCardName = "Queen of Hearts (Center)"
        )
    )

    fun getBenchmark(id: String): BenchmarkVideo {
        return BENCHMARKS.firstOrNull { it.id == id } ?: BENCHMARKS[0]
    }

    /**
     * Generates all frames for the specified benchmark video.
     */
    fun generateFrames(benchmarkId: String, width: Int = 480, height: Int = 320): List<Bitmap> {
        val benchmark = getBenchmark(benchmarkId)
        val frames = mutableListOf<Bitmap>()

        for (i in 0 until benchmark.frameCount) {
            val progress = i.toFloat() / (benchmark.frameCount - 1).coerceAtLeast(1)
            val bmp = renderFrame(benchmarkId, i, progress, width, height)
            frames.add(bmp)
        }

        return frames
    }

    /**
     * Returns the recommended starting target box for the benchmark (the Queen of Hearts).
     */
    fun getInitialTargetBox(benchmarkId: String): NormalizedRect {
        // Initial Center Card position (slot 1)
        // Table centers: Left ~ 0.23, Center ~ 0.50, Right ~ 0.77
        val cardWidth = 0.18f
        val cardHeight = 0.36f
        return NormalizedRect.fromCenter(
            NormalizedPoint(0.50f, 0.55f),
            cardWidth,
            cardHeight
        )
    }

    /**
     * Calculates the true ground truth position of the target Queen for verification.
     */
    fun getGroundTruthFinalSlot(benchmarkId: String): String {
        return when (benchmarkId) {
            "classic_monte" -> "Left Position" // Starts Center -> swaps with Left -> swaps with Right -> ends Left
            "occlusion_monte" -> "Right Position" // Starts Center -> swaps with Right -> ends Right
            "rapid_cross_monte" -> "Middle Position" // Complex 3 swaps, ends back in Middle
            else -> "Left Position"
        }
    }

    private data class CardState(
        val name: String,
        val rank: String,
        val suit: String,
        val isRed: Boolean,
        var x: Float,
        var y: Float,
        var zIndex: Int = 0
    )

    private fun renderFrame(
        benchmarkId: String,
        frameIndex: Int,
        progress: Float,
        w: Int,
        h: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw Casino Felt Background
        val feltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 58, 38) // Deep casino green
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), feltPaint)

        // Subtle table texture lines & watermark
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(35, 255, 255, 255)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(20f, 20f, w - 20f, h - 20f, 16f, 16f, linePaint)

        // 2. Compute Card Positions based on shuffle choreography
        val cardW = w * 0.18f
        val cardH = h * 0.36f
        val tableY = h * 0.55f

        val slotLeftX = w * 0.23f
        val slotCenterX = w * 0.50f
        val slotRightX = w * 0.77f

        // Initial setup:
        // Card 0: King of Spades (starts Left)
        // Card 1: Queen of Hearts (TARGET - starts Center)
        // Card 2: King of Clubs (starts Right)
        val card0 = CardState("King of Spades", "K", "♠", false, slotLeftX, tableY, 0)
        val queen = CardState("Queen of Hearts", "Q", "♥", true, slotCenterX, tableY, 1)
        val card2 = CardState("King of Clubs", "K", "♣", false, slotRightX, tableY, 0)

        // Animate shuffles based on benchmark
        when (benchmarkId) {
            "classic_monte" -> {
                // Phase 1 (progress 0.15 .. 0.50): Swap Queen (Center) and King (Left)
                // Phase 2 (progress 0.55 .. 0.90): Swap Center (now King) and Right (King)
                if (progress in 0.15f..0.50f) {
                    val p = easeInOutCubic((progress - 0.15f) / 0.35f)
                    queen.x = lerp(slotCenterX, slotLeftX, p)
                    queen.y = tableY + sin(p * Math.PI.toFloat()) * (h * 0.08f)
                    queen.zIndex = 2

                    card0.x = lerp(slotLeftX, slotCenterX, p)
                    card0.y = tableY - sin(p * Math.PI.toFloat()) * (h * 0.08f)
                    card0.zIndex = 1
                } else if (progress > 0.50f) {
                    queen.x = slotLeftX
                    card0.x = slotCenterX

                    if (progress in 0.55f..0.90f) {
                        val p = easeInOutCubic((progress - 0.55f) / 0.35f)
                        card0.x = lerp(slotCenterX, slotRightX, p)
                        card0.y = tableY + sin(p * Math.PI.toFloat()) * (h * 0.07f)

                        card2.x = lerp(slotRightX, slotCenterX, p)
                        card2.y = tableY - sin(p * Math.PI.toFloat()) * (h * 0.07f)
                    } else if (progress > 0.90f) {
                        card0.x = slotRightX
                        card2.x = slotCenterX
                    }
                }
            }

            "occlusion_monte" -> {
                // High-speed swap between Queen (Center) and Right (card2)
                // Dealer hand passes over Queen between 0.20 .. 0.70
                if (progress in 0.15f..0.45f) {
                    val p = easeInOutCubic((progress - 0.15f) / 0.30f)
                    queen.x = lerp(slotCenterX, slotRightX, p)
                    queen.y = tableY + sin(p * Math.PI.toFloat()) * (h * 0.09f)

                    card2.x = lerp(slotRightX, slotCenterX, p)
                    card2.y = tableY - sin(p * Math.PI.toFloat()) * (h * 0.09f)
                } else if (progress > 0.45f) {
                    queen.x = slotRightX
                    card2.x = slotCenterX

                    // Second swap: Left (card0) and Center (card2)
                    if (progress in 0.52f..0.85f) {
                        val p = easeInOutCubic((progress - 0.52f) / 0.33f)
                        card0.x = lerp(slotLeftX, slotCenterX, p)
                        card0.y = tableY + sin(p * Math.PI.toFloat()) * (h * 0.06f)

                        card2.x = lerp(slotCenterX, slotLeftX, p)
                        card2.y = tableY - sin(p * Math.PI.toFloat()) * (h * 0.06f)
                    } else if (progress > 0.85f) {
                        card0.x = slotCenterX
                        card2.x = slotLeftX
                    }
                }
            }

            "rapid_cross_monte" -> {
                // Rapid 3-way swap:
                // 1) Queen swaps with Right (0.10 .. 0.38)
                // 2) Queen swaps with Left across table (0.42 .. 0.70)
                // 3) Queen swaps back into Middle (0.72 .. 0.95)
                if (progress in 0.10f..0.38f) {
                    val p = easeInOutCubic((progress - 0.10f) / 0.28f)
                    queen.x = lerp(slotCenterX, slotRightX, p)
                    queen.y = tableY + sin(p * Math.PI.toFloat()) * (h * 0.07f)
                    card2.x = lerp(slotRightX, slotCenterX, p)
                    card2.y = tableY - sin(p * Math.PI.toFloat()) * (h * 0.07f)
                } else if (progress in 0.38f..0.42f) {
                    queen.x = slotRightX
                    card2.x = slotCenterX
                } else if (progress in 0.42f..0.70f) {
                    val p = easeInOutCubic((progress - 0.42f) / 0.28f)
                    queen.x = lerp(slotRightX, slotLeftX, p)
                    queen.y = tableY + sin(p * Math.PI.toFloat()) * (h * 0.12f)
                    queen.zIndex = 3

                    card0.x = lerp(slotLeftX, slotRightX, p)
                    card0.y = tableY - sin(p * Math.PI.toFloat()) * (h * 0.12f)
                } else if (progress in 0.70f..0.72f) {
                    queen.x = slotLeftX
                    card0.x = slotRightX
                } else if (progress in 0.72f..0.95f) {
                    val p = easeInOutCubic((progress - 0.72f) / 0.23f)
                    queen.x = lerp(slotLeftX, slotCenterX, p)
                    queen.y = tableY + sin(p * Math.PI.toFloat()) * (h * 0.08f)

                    card2.x = lerp(slotCenterX, slotLeftX, p)
                    card2.y = tableY - sin(p * Math.PI.toFloat()) * (h * 0.08f)
                } else if (progress > 0.95f) {
                    queen.x = slotCenterX
                    card2.x = slotLeftX
                    card0.x = slotRightX
                }
            }
        }

        // 3. Draw Cards in zIndex order
        val allCards = listOf(card0, queen, card2).sortedBy { it.zIndex }
        for (card in allCards) {
            drawPlayingCard(canvas, card, cardW, cardH)
        }

        // Generate direct pixel raster for cross-platform/Robolectric software buffer accuracy
        val pixels = IntArray(w * h)
        val tableColor = Color.rgb(18, 58, 38)
        pixels.fill(tableColor)

        // Rasterize table border line
        for (x in 20 until w - 20) {
            if (20 < h) pixels[20 * w + x] = Color.rgb(35, 80, 55)
            if (h - 21 >= 0) pixels[(h - 21) * w + x] = Color.rgb(35, 80, 55)
        }

        // Rasterize cards into pixel buffer
        for (card in allCards) {
            val cLeft = (card.x - cardW / 2f).toInt().coerceIn(0, w - 1)
            val cRight = (card.x + cardW / 2f).toInt().coerceIn(0, w - 1)
            val cTop = (card.y - cardH / 2f).toInt().coerceIn(0, h - 1)
            val cBottom = (card.y + cardH / 2f).toInt().coerceIn(0, h - 1)

            val borderColor = if (card.isRed) Color.rgb(220, 20, 60) else Color.rgb(40, 40, 40)
            val suitColor = if (card.isRed) Color.rgb(210, 20, 40) else Color.rgb(20, 20, 20)

            for (py in cTop..cBottom) {
                val rowOffset = py * w
                for (px in cLeft..cRight) {
                    val isBorder = (px == cLeft || px == cRight || py == cTop || py == cBottom ||
                            px == cLeft + 1 || px == cRight - 1 || py == cTop + 1 || py == cBottom - 1)
                    val dxCenter = px - card.x
                    val dyCenter = py - (card.y + cardH * 0.08f)
                    val isCenterSuit = (dxCenter * dxCenter + dyCenter * dyCenter) < (cardW * 0.22f) * (cardW * 0.22f)
                    val isCornerRank = (px in (cLeft + 4)..(cLeft + 14) && py in (cTop + 4)..(cTop + 24))

                    pixels[rowOffset + px] = when {
                        isBorder -> borderColor
                        isCenterSuit || isCornerRank -> suitColor
                        else -> Color.WHITE
                    }
                }
            }
        }

        // 4. Draw Hand Occlusion if in occlusion benchmark or stress test
        var handActive = false
        var handPosX = 0f
        var handPosY = 0f
        var handDimW = 0f
        var handDimH = 0f

        if (benchmarkId == "occlusion_monte") {
            // Dealer hand sweeps across cards between 0.22 .. 0.55
            if (progress in 0.22f..0.55f) {
                val handProgress = (progress - 0.22f) / 0.33f
                handPosX = lerp(w * 0.35f, w * 0.85f, handProgress)
                handPosY = tableY - h * 0.05f + sin(handProgress * Math.PI.toFloat()) * (h * 0.06f)
                handDimW = w * 0.24f
                handDimH = h * 0.30f
                handActive = true
                drawDealerHand(canvas, handPosX, handPosY, handDimW, handDimH)
            }
            // Second dealer hand sweep
            if (progress in 0.60f..0.80f) {
                val handProgress = (progress - 0.60f) / 0.20f
                handPosX = lerp(w * 0.65f, w * 0.20f, handProgress)
                handPosY = tableY - h * 0.03f
                handDimW = w * 0.22f
                handDimH = h * 0.28f
                handActive = true
                drawDealerHand(canvas, handPosX, handPosY, handDimW, handDimH)
            }
        } else if (benchmarkId == "rapid_cross_monte") {
            if (progress in 0.45f..0.65f) {
                val handProgress = (progress - 0.45f) / 0.20f
                handPosX = lerp(w * 0.80f, w * 0.25f, handProgress)
                handPosY = tableY - h * 0.02f
                handDimW = w * 0.20f
                handDimH = h * 0.25f
                handActive = true
                drawDealerHand(canvas, handPosX, handPosY, handDimW, handDimH)
            }
        }

        if (handActive) {
            val skinTone = Color.rgb(228, 178, 142)
            val radX = handDimW * 0.45f
            val radY = handDimH * 0.45f
            val minX = (handPosX - radX).toInt().coerceIn(0, w - 1)
            val maxX = (handPosX + radX).toInt().coerceIn(0, w - 1)
            val minY = (handPosY - radY).toInt().coerceIn(0, h - 1)
            val maxY = (handPosY + radY).toInt().coerceIn(0, h - 1)

            for (py in minY..maxY) {
                val rowOffset = py * w
                val dy = (py - handPosY) / radY
                for (px in minX..maxX) {
                    val dx = (px - handPosX) / radX
                    if (dx * dx + dy * dy <= 1.0f) {
                        pixels[rowOffset + px] = skinTone
                    }
                }
            }
        }

        // Sync pixel array into bitmap for direct memory reader access
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)

        // 5. Draw Header HUD info (time, frame, diagnostic label)
        val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255)
            textSize = 14f
            typeface = Typeface.MONOSPACE
        }
        canvas.drawText("FRAME: ${frameIndex + 1} | TIME: ${(progress * 3.0f).format(2)}s | SPEED: ${(progress * 100).toInt()}%", 28f, 38f, hudPaint)

        return bitmap
    }

    private fun drawPlayingCard(canvas: Canvas, card: CardState, w: Float, h: Float) {
        val left = card.x - w / 2f
        val top = card.y - h / 2f
        val right = card.x + w / 2f
        val bottom = card.y + h / 2f
        val rect = RectF(left, top, right, bottom)

        // Drop shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
        }
        val shadowRect = RectF(left + 4f, top + 6f, right + 4f, bottom + 6f)
        canvas.drawRoundRect(shadowRect, 10f, 10f, shadowPaint)

        // White card body
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, 10f, 10f, bodyPaint)

        // Card Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (card.isRed) Color.rgb(220, 20, 60) else Color.rgb(40, 40, 40)
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(rect, 10f, 10f, borderPaint)

        // Inner ornate card frame
        val innerRect = RectF(left + 6f, top + 6f, right - 6f, bottom - 6f)
        val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 0, 0, 0)
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(innerRect, 6f, 6f, innerBorderPaint)

        // Rank and Suit Text
        val suitColor = if (card.isRed) Color.rgb(210, 20, 40) else Color.rgb(20, 20, 20)

        val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = suitColor
            textSize = h * 0.18f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        val suitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = suitColor
            textSize = h * 0.18f
            textAlign = Paint.Align.LEFT
        }

        // Top-left corner
        canvas.drawText(card.rank, left + 9f, top + h * 0.22f, rankPaint)
        canvas.drawText(card.suit, left + 9f, top + h * 0.38f, suitPaint)

        // Center big symbol / illustration
        val centerSuitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = suitColor
            textSize = h * 0.36f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(card.suit, card.x, card.y + h * 0.12f, centerSuitPaint)

        // Bottom-right corner (inverted)
        canvas.save()
        canvas.rotate(180f, right - 16f, bottom - 16f)
        canvas.drawText(card.rank, right - 24f, bottom - 16f, rankPaint)
        canvas.restore()
    }

    private fun drawDealerHand(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 0, 0, 0)
        }
        canvas.drawOval(x - w * 0.45f + 8f, y - h * 0.45f + 12f, x + w * 0.45f + 8f, y + h * 0.45f + 12f, shadowPaint)

        // Skin tone paint
        val skinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(228, 178, 142) // Natural skin tone
            style = Paint.Style.FILL
        }

        // Draw palm / fingers
        val handPath = Path()
        handPath.moveTo(x - w * 0.4f, y - h * 0.4f)
        handPath.lineTo(x + w * 0.4f, y - h * 0.3f)
        handPath.lineTo(x + w * 0.35f, y + h * 0.4f)
        handPath.lineTo(x - w * 0.35f, y + h * 0.4f)
        handPath.close()

        canvas.drawRoundRect(x - w * 0.4f, y - h * 0.4f, x + w * 0.4f, y + h * 0.4f, 20f, 20f, skinPaint)

        // Draw fingers
        val fingerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(215, 165, 130)
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        for (i in -1..2) {
            val fx = x + i * (w * 0.18f)
            canvas.drawLine(fx, y - h * 0.2f, fx, y + h * 0.35f, fingerPaint)
        }
    }

    private fun lerp(start: Float, stop: Float, amount: Float): Float {
        return start + (stop - start) * amount
    }

    private fun easeInOutCubic(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f
    }

    private fun Float.format(digits: Int) = String.format("%.${digits}f", this)
}
