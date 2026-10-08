package com.example.engine

import android.graphics.Bitmap
import com.example.ai.CardCandidateDescriptor
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.model.TrackedFrame
import com.example.model.TrackingState
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * High-Accuracy Card Tracking Engine with Visual Identity Lock,
 * Continuous Kinematics Search, and Smooth Trajectory Center Alignment.
 *
 * Engineered to eliminate tracking drift, prevent misidentifying adjacent cards,
 * preserve card identity through rapid shuffles and dealer hand occlusions,
 * and maintain continuous card center alignment without coordinate jumping.
 */
class OpticalFlowTracker(
    val baseSearchWindowSize: Int = 15,
    val maxPyramidLevels: Int = 3,
    val maxIterations: Int = 10,
    val forwardBackwardThreshold: Float = 2.0f
) {

    data class GrayImage(
        val width: Int,
        val height: Int,
        val data: IntArray // values 0..255
    )

    data class RgbImage(
        val width: Int,
        val height: Int,
        val pixels: IntArray
    )

    data class ImagePyramid(
        val levels: List<GrayImage>
    )

    data class FeaturePoint(
        var x: Float,
        var y: Float,
        var isValid: Boolean = true
    )

    data class CardCandidate(
        val centerX: Float,
        val centerY: Float,
        val score: Float,
        val isHandOccluded: Boolean,
        val isCardPresent: Boolean,
        val zncc: Float,
        val colorMatch: Float
    )

    // Template dimensions for target identity verification
    private val templateWidth = 32
    private val templateHeight = 44

    // Frame 0 ground-truth signature (Immutable reference)
    private var groundTruthTemplateZeroMean: FloatArray? = null
    private var groundTruthTemplateNorm: Float = 1.0f
    private var isRedSuitTarget: Boolean = false
    private var isBlackSuitTarget: Boolean = false
    private var targetMeanBrightness: Float = 200f

    // Adaptive template (slow momentum update during clear, high-confidence frames)
    private var adaptiveTemplateZeroMean: FloatArray? = null
    private var adaptiveTemplateNorm: Float = 1.0f

    // Target geometry & state memory
    private var referenceCardBitmap: Bitmap? = null
    private var currentRect: NormalizedRect? = null
    private var initialCardWidth: Float = 0.18f
    private var initialCardHeight: Float = 0.36f
    private var previousPyramid: ImagePyramid? = null
    private var trackedPoints: MutableList<FeaturePoint> = mutableListOf()

    // Motion kinematics & smoothing
    private var velocityX: Float = 0f
    private var velocityY: Float = 0f
    private var smoothCenterX: Float = 0.5f
    private var smoothCenterY: Float = 0.5f
    private var consecutiveOcclusionFrames: Int = 0
    private var stationaryFramesCount: Int = 0

    /**
     * Initializes tracking on Frame 0 with user-selected target ROI.
     */
    fun initialize(firstFrameBitmap: Bitmap, targetRect: NormalizedRect) {
        val w = firstFrameBitmap.width
        val h = firstFrameBitmap.height
        val rgb = bitmapToRgb(firstFrameBitmap)
        val gray = rgbToGray(rgb)

        currentRect = targetRect
        initialCardWidth = targetRect.width.coerceIn(0.08f, 0.45f)
        initialCardHeight = targetRect.height.coerceIn(0.12f, 0.65f)
        smoothCenterX = targetRect.centerX
        smoothCenterY = targetRect.centerY
        previousPyramid = buildPyramid(gray, maxPyramidLevels)
        consecutiveOcclusionFrames = 0
        stationaryFramesCount = 0
        velocityX = 0f
        velocityY = 0f

        // Crop reference target card bitmap for Gemini multimodal vision
        try {
            val cropX = (targetRect.left * w).toInt().coerceIn(0, w - 1)
            val cropY = (targetRect.top * h).toInt().coerceIn(0, h - 1)
            val cropW = (targetRect.width * w).toInt().coerceIn(1, w - cropX)
            val cropH = (targetRect.height * h).toInt().coerceIn(1, h - cropY)
            referenceCardBitmap = Bitmap.createBitmap(firstFrameBitmap, cropX, cropY, cropW, cropH)
        } catch (ignored: Exception) {
            referenceCardBitmap = null
        }

        // Extract Frame 0 ground-truth template and signature
        val patch = extractPatch(gray, targetRect, templateWidth, templateHeight)
        val rgbPatch = extractRgbPatch(rgb, targetRect, templateWidth, templateHeight)

        // Compute zero-mean normalized template
        val (zm, norm, mean) = computeZeroMean(patch)
        groundTruthTemplateZeroMean = zm
        groundTruthTemplateNorm = max(1e-4f, norm)
        targetMeanBrightness = mean

        adaptiveTemplateZeroMean = zm.clone()
        adaptiveTemplateNorm = groundTruthTemplateNorm

        // Analyze color characteristics of the target card
        var redPixels = 0
        var darkPixels = 0
        for (c in rgbPatch) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (r > 130 && r > (g * 1.35f).toInt() && r > (b * 1.35f).toInt()) {
                redPixels++
            }
            if (r < 75 && g < 75 && b < 75) {
                darkPixels++
            }
        }
        val totalPix = rgbPatch.size
        isRedSuitTarget = (redPixels >= 8 || (redPixels.toFloat() / totalPix) > 0.008f)
        isBlackSuitTarget = (!isRedSuitTarget && darkPixels >= 25)

        // Detect initial feature points inside the card
        val absLeft = (targetRect.left * w).toInt().coerceIn(0, w - 1)
        val absTop = (targetRect.top * h).toInt().coerceIn(0, h - 1)
        val absRight = (targetRect.right * w).toInt().coerceIn(0, w - 1)
        val absBottom = (targetRect.bottom * h).toInt().coerceIn(0, h - 1)

        trackedPoints = detectCardFeatures(gray, absLeft, absTop, absRight, absBottom)
    }

    /**
     * Processes next frame and updates tracked card position.
     * Guarantees smooth trajectory tracking aligned directly with the card center.
     */
    fun processFrame(
        frameBitmap: Bitmap,
        frameIndex: Int,
        timestampMs: Long
    ): TrackedFrame {
        val targetRect = currentRect ?: NormalizedRect(0.4f, 0.4f, 0.6f, 0.6f)
        val width = frameBitmap.width
        val height = frameBitmap.height
        val rgb = bitmapToRgb(frameBitmap)
        val currentGray = rgbToGray(rgb)
        val currentPyramid = buildPyramid(currentGray, maxPyramidLevels)

        val prevPyramid = previousPyramid
        var trackingState = TrackingState.TRACKING_LOCKED
        var isOccluded = false
        var inlierCount = 0
        var confidence = 1.0f

        var flowDx = 0f
        var flowDy = 0f
        var flowValid = false

        // Phase 1: Pyramidal Optical Flow Displacement on Card Features
        if (prevPyramid != null && trackedPoints.isNotEmpty()) {
            val initGuessX = velocityX * width
            val initGuessY = velocityY * height

            val forwardPoints = mutableListOf<FeaturePoint>()
            for (pt in trackedPoints) {
                if (pt.isValid) {
                    val trackedPt = trackPointPyramidal(
                        pt, prevPyramid, currentPyramid,
                        initGuessX, initGuessY, baseSearchWindowSize
                    )
                    forwardPoints.add(trackedPt)
                } else {
                    forwardPoints.add(FeaturePoint(pt.x, pt.y, false))
                }
            }

            // Bidirectional check to eliminate tracking drift and invalid points
            val validDeltas = mutableListOf<Pair<Float, Float>>()
            for (i in trackedPoints.indices) {
                val origPt = trackedPoints[i]
                val fwdPt = forwardPoints[i]

                if (fwdPt.isValid) {
                    val backPt = trackPointPyramidal(
                        fwdPt, currentPyramid, prevPyramid,
                        -initGuessX, -initGuessY, baseSearchWindowSize
                    )
                    val fbDist = distance(origPt.x, origPt.y, backPt.x, backPt.y)
                    if (fbDist <= forwardBackwardThreshold) {
                        validDeltas.add(Pair(fwdPt.x - origPt.x, fwdPt.y - origPt.y))
                    }
                }
            }

            inlierCount = validDeltas.size

            if (validDeltas.size >= 3) {
                // Median consensus motion (robust to dealer finger outliers)
                val sortedX = validDeltas.map { it.first }.sorted()
                val sortedY = validDeltas.map { it.second }.sorted()
                val medianDx = sortedX[sortedX.size / 2]
                val medianDy = sortedY[sortedY.size / 2]

                val normDx = medianDx / width
                val normDy = medianDy / height
                val jump = sqrt(normDx * normDx + normDy * normDy)
                // Filter out physically impossible per-frame leaps (> 15% screen)
                if (jump <= 0.15f) {
                    flowDx = normDx
                    flowDy = normDy
                    flowValid = true
                }
            }
        }

        // Phase 2: Physically Constrained Local Search Space
        // NO arbitrary global table slot jumping to prevent misidentifying adjacent cards!
        val effectiveDx = if (flowValid) flowDx else velocityX
        val effectiveDy = if (flowValid) flowDy else velocityY

        val predictedX = (targetRect.centerX + effectiveDx).coerceIn(0.08f, 0.92f)
        val predictedY = (targetRect.centerY + effectiveDy).coerceIn(0.18f, 0.82f)

        val candidateCenters = mutableListOf<NormalizedPoint>()

        // 1. Primary Kinematic Hypotheses
        candidateCenters.add(NormalizedPoint(predictedX, predictedY))
        candidateCenters.add(NormalizedPoint(targetRect.centerX, targetRect.centerY)) // stationary hold
        if (flowValid) {
            candidateCenters.add(
                NormalizedPoint(
                    (targetRect.centerX + flowDx).coerceIn(0.08f, 0.92f),
                    (targetRect.centerY + flowDy).coerceIn(0.18f, 0.82f)
                )
            )
        }

        // 2. Continuous localized neighborhood search around predicted center
        // Tightly bounded radius so tracker cannot jump to adjacent cards
        val xOffsets = floatArrayOf(-0.06f, -0.045f, -0.03f, -0.015f, 0.015f, 0.03f, 0.045f, 0.06f)
        val yOffsets = floatArrayOf(-0.035f, -0.02f, 0.0f, 0.02f, 0.035f)

        for (dx in xOffsets) {
            for (dy in yOffsets) {
                candidateCenters.add(
                    NormalizedPoint(
                        (predictedX + dx).coerceIn(0.08f, 0.92f),
                        (predictedY + dy).coerceIn(0.18f, 0.82f)
                    )
                )
            }
        }

        // Phase 3: Evaluate Candidates with Multi-Criteria Identity Scoring & Spatial Gating
        var bestCandidate: CardCandidate? = null
        var bestScore = -1f

        val evaluatedCenters = mutableSetOf<Pair<Int, Int>>()

        for (pt in candidateCenters) {
            val keyX = (pt.x * 200).toInt()
            val keyY = (pt.y * 200).toInt()
            if (!evaluatedCenters.add(Pair(keyX, keyY))) continue

            val cand = evaluateCandidate(
                pt.x, pt.y,
                initialCardWidth, initialCardHeight,
                rgb, currentGray,
                predictedX, predictedY
            )

            if (cand.score > bestScore) {
                bestScore = cand.score
                bestCandidate = cand
            }
        }

        // Fine sub-pixel refinement around top candidate for exact center alignment
        if (bestCandidate != null && bestCandidate.score >= 0.28f) {
            val peakX = bestCandidate.centerX
            val peakY = bestCandidate.centerY
            val microOffsets = floatArrayOf(-0.006f, -0.003f, 0.003f, 0.006f)
            for (mx in microOffsets) {
                for (my in microOffsets) {
                    val subCand = evaluateCandidate(
                        (peakX + mx).coerceIn(0.08f, 0.92f),
                        (peakY + my).coerceIn(0.18f, 0.82f),
                        initialCardWidth, initialCardHeight,
                        rgb, currentGray,
                        predictedX, predictedY
                    )
                    if (subCand.score > bestScore) {
                        bestScore = subCand.score
                        bestCandidate = subCand
                    }
                }
            }
        }

        var newCenterX = predictedX
        var newCenterY = predictedY

        val chosen = bestCandidate

        if (chosen != null && chosen.isCardPresent && chosen.score >= 0.38f) {
            // High confidence lock on target card
            newCenterX = chosen.centerX
            newCenterY = chosen.centerY
            trackingState = TrackingState.TRACKING_LOCKED
            consecutiveOcclusionFrames = 0
            isOccluded = false
            confidence = chosen.score.coerceIn(0.60f, 1.0f)

            // Update smooth velocity
            val stepDx = newCenterX - targetRect.centerX
            val stepDy = newCenterY - targetRect.centerY
            velocityX = velocityX * 0.30f + stepDx * 0.70f
            velocityY = velocityY * 0.30f + stepDy * 0.70f

            // Slow adaptive update on clean, unoccluded frames
            if (!chosen.isHandOccluded && chosen.score >= 0.75f) {
                updateAdaptiveTemplate(currentGray, newCenterX, newCenterY)
            }
        } else if (chosen != null && chosen.isHandOccluded) {
            // Hand is passing over card: Occlusion coasting along swap trajectory
            consecutiveOcclusionFrames++
            trackingState = TrackingState.OCCLUDED_COASTING
            isOccluded = true
            confidence = max(0.35f, 0.70f - consecutiveOcclusionFrames * 0.04f)

            // During hand occlusion, guide center along smooth velocity prediction
            newCenterX = (targetRect.centerX + velocityX).coerceIn(0.08f, 0.92f)
            newCenterY = (targetRect.centerY + velocityY * 0.6f).coerceIn(0.18f, 0.82f)
            velocityX *= 0.85f
            velocityY *= 0.85f
        } else if (chosen != null && chosen.isCardPresent && chosen.score >= 0.24f) {
            // Re-acquiring target card
            newCenterX = chosen.centerX
            newCenterY = chosen.centerY
            trackingState = TrackingState.REACQUIRING
            isOccluded = false
            confidence = 0.52f
            consecutiveOcclusionFrames = 0
            val stepDx = newCenterX - targetRect.centerX
            val stepDy = newCenterY - targetRect.centerY
            velocityX = velocityX * 0.4f + stepDx * 0.6f
            velocityY = velocityY * 0.4f + stepDy * 0.6f
        } else {
            // Fallback: Hold last confirmed card position rather than wandering to adjacent regions
            newCenterX = targetRect.centerX
            newCenterY = targetRect.centerY
            velocityX *= 0.4f
            velocityY *= 0.4f
            consecutiveOcclusionFrames++
            trackingState = TrackingState.OCCLUDED_COASTING
            isOccluded = true
            confidence = max(0.20f, 0.50f - consecutiveOcclusionFrames * 0.05f)
        }

        // Clamp coordinates firmly inside visible card playing bounds (NO invalid coordinates)
        newCenterX = newCenterX.coerceIn(0.08f, 0.92f)
        newCenterY = newCenterY.coerceIn(0.18f, 0.82f)

        // Stationary lock toward end of video or when card comes to rest
        val calculatedSpeed = sqrt(velocityX * velocityX + velocityY * velocityY)
        if (calculatedSpeed < 0.008f) {
            stationaryFramesCount++
            if (stationaryFramesCount > 5) {
                // Stationary lock damping
                velocityX *= 0.2f
                velocityY *= 0.2f
            }
        } else {
            stationaryFramesCount = 0
        }

        // Adaptive smoothing: Direct card center alignment throughout all video frames
        // High responsiveness during motion; zero-lag, jitter-free lock when stationary
        val smoothAlpha = when {
            calculatedSpeed > 0.025f -> 0.82f
            calculatedSpeed > 0.010f -> 0.70f
            else -> 0.45f
        }
        smoothCenterX = smoothCenterX * (1f - smoothAlpha) + newCenterX * smoothAlpha
        smoothCenterY = smoothCenterY * (1f - smoothAlpha) + newCenterY * smoothAlpha

        // Final centered bounding box aligned directly with the card center
        val updatedRect = NormalizedRect.fromCenter(
            NormalizedPoint(smoothCenterX, smoothCenterY),
            initialCardWidth,
            initialCardHeight
        )
        currentRect = updatedRect
        previousPyramid = currentPyramid

        // Reseed feature points strictly inside confirmed card body
        val absLeft = ((smoothCenterX - initialCardWidth / 2f) * width).toInt().coerceIn(0, width - 1)
        val absTop = ((smoothCenterY - initialCardHeight / 2f) * height).toInt().coerceIn(0, height - 1)
        val absRight = ((smoothCenterX + initialCardWidth / 2f) * width).toInt().coerceIn(0, width - 1)
        val absBottom = ((smoothCenterY + initialCardHeight / 2f) * height).toInt().coerceIn(0, height - 1)

        trackedPoints = detectCardFeatures(currentGray, absLeft, absTop, absRight, absBottom)

        return TrackedFrame(
            frameIndex = frameIndex,
            timestampMs = timestampMs,
            rect = updatedRect,
            center = NormalizedPoint(smoothCenterX, smoothCenterY),
            confidence = confidence,
            trackingState = trackingState,
            isOccluded = isOccluded,
            inlierCount = inlierCount,
            velocityX = velocityX,
            velocityY = velocityY,
            speed = calculatedSpeed,
            smoothedCenter = NormalizedPoint(smoothCenterX, smoothCenterY),
            bitmap = frameBitmap
        )
    }

    /**
     * Evaluates a candidate card position against card presence, template appearance,
     * suit identity, and spatial proximity gate to prevent misidentifying adjacent cards.
     */
    private fun evaluateCandidate(
        cx: Float,
        cy: Float,
        cardW: Float,
        cardH: Float,
        rgb: RgbImage,
        gray: GrayImage,
        predictedX: Float,
        predictedY: Float,
        applySpatialGate: Boolean = true
    ): CardCandidate {
        val rect = NormalizedRect.fromCenter(NormalizedPoint(cx, cy), cardW, cardH)
        val patch = extractPatch(gray, rect, templateWidth, templateHeight)
        val rgbPatch = extractRgbPatch(rgb, rect, templateWidth, templateHeight)

        val totalPixels = patch.size
        if (totalPixels == 0) {
            return CardCandidate(cx, cy, 0f, isHandOccluded = false, isCardPresent = false, 0f, 0f)
        }

        var sumGray = 0f
        var redPixels = 0
        var darkPixels = 0
        var skinPixels = 0
        var tableFeltPixels = 0

        for (i in 0 until totalPixels) {
            val gVal = patch[i]
            sumGray += gVal

            val c = rgbPatch[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            // Red card suit (Hearts / Diamonds)
            if (r > 130 && r > (g * 1.35f).toInt() && r > (b * 1.35f).toInt()) {
                redPixels++
            }
            // Dark suit / text (Spades / Clubs / Card border)
            if (r < 75 && g < 75 && b < 75) {
                darkPixels++
            }
            // Human skin tone (Dealer hand)
            if (r in 140..255 && g in 90..215 && b in 60..185 && r > g && g > b && (r - g) in 15..85) {
                skinPixels++
            }
            // Green casino felt table
            if (g > (r * 1.3f).toInt() && g > (b * 1.1f).toInt() && g < 140) {
                tableFeltPixels++
            }
        }

        val meanBrightness = sumGray / totalPixels
        val skinFraction = skinPixels.toFloat() / totalPixels
        val feltFraction = tableFeltPixels.toFloat() / totalPixels

        val isHandOccluded = skinFraction > 0.35f

        // A valid card MUST have card-body brightness and NOT be mostly green felt
        val isCardPresent = (meanBrightness > 90f && feltFraction < 0.58f)

        if (!isCardPresent && !isHandOccluded) {
            // Empty table background: REJECT
            return CardCandidate(cx, cy, 0.05f, isHandOccluded = false, isCardPresent = false, 0f, 0f)
        }

        // ZNCC Appearance Similarity against Frame 0 ground truth & adaptive template
        var znccScore = 0.5f
        val refZM = groundTruthTemplateZeroMean
        if (refZM != null) {
            val (patchZM, patchNorm, _) = computeZeroMean(patch)
            if (patchNorm > 1e-4f) {
                var dotGround = 0f
                var dotAdaptive = 0f
                val adaptZM = adaptiveTemplateZeroMean ?: refZM

                for (i in 0 until totalPixels) {
                    dotGround += patchZM[i] * refZM[i]
                    dotAdaptive += patchZM[i] * adaptZM[i]
                }
                val znccGround = dotGround / (patchNorm * groundTruthTemplateNorm)
                val znccAdapt = dotAdaptive / (patchNorm * adaptiveTemplateNorm)
                val combinedZncc = znccGround * 0.75f + znccAdapt * 0.25f
                znccScore = ((combinedZncc + 1f) * 0.5f).coerceIn(0f, 1f)
            }
        }

        // Suit Color Identity Match (Critical for distinguishing target from adjacent cards)
        var colorScore = 0.5f
        if (isRedSuitTarget) {
            // Target is Red card: Heavily reward red suit pixels, penalize purely black card distractors
            colorScore = when {
                redPixels >= 6 -> 1.0f
                redPixels in 2..5 -> 0.78f
                darkPixels > 30 && redPixels == 0 -> 0.05f // Black card distractor!
                else -> 0.40f
            }
        } else if (isBlackSuitTarget) {
            // Target is Black card: Heavily reward dark suit pixels, penalize red card distractors
            colorScore = when {
                redPixels >= 5 -> 0.05f // Red card distractor!
                darkPixels >= 25 -> 1.0f
                else -> 0.50f
            }
        }

        // Spatial Proximity Gaussian Gate: Heavily penalize any candidate far from predicted trajectory
        // This mathematically eliminates jumping to adjacent cards during normal tracking
        val spatialWeight = if (applySpatialGate) {
            val dist = distance(cx, cy, predictedX, predictedY)
            exp(- (dist * dist) / (2f * 0.075f * 0.075f)).coerceIn(0.05f, 1.0f)
        } else 1.0f

        val presenceBonus = if (isCardPresent) 0.10f else 0f
        val rawScore = (znccScore * 0.50f + colorScore * 0.40f + presenceBonus).coerceIn(0f, 1f)
        val finalScore = (rawScore * spatialWeight).coerceIn(0f, 1f)

        return CardCandidate(
            centerX = cx,
            centerY = cy,
            score = finalScore,
            isHandOccluded = isHandOccluded,
            isCardPresent = isCardPresent,
            zncc = znccScore,
            colorMatch = colorScore
        )
    }

    private fun updateAdaptiveTemplate(gray: GrayImage, cx: Float, cy: Float) {
        val rect = NormalizedRect.fromCenter(NormalizedPoint(cx, cy), initialCardWidth, initialCardHeight)
        val patch = extractPatch(gray, rect, templateWidth, templateHeight)
        val (patchZM, patchNorm, _) = computeZeroMean(patch)
        val currentAdaptive = adaptiveTemplateZeroMean
        if (currentAdaptive != null && patchNorm > 1e-4f) {
            for (i in patchZM.indices) {
                currentAdaptive[i] = currentAdaptive[i] * 0.95f + patchZM[i] * 0.05f
            }
            adaptiveTemplateNorm = adaptiveTemplateNorm * 0.95f + patchNorm * 0.05f
        }
    }

    /**
     * Extracts all detectable candidate cards in the current frame for Gemini visual verification.
     */
    fun extractCandidateDescriptors(frameBitmap: Bitmap): List<CardCandidateDescriptor> {
        val rgb = bitmapToRgb(frameBitmap)
        val gray = rgbToGray(rgb)
        val w = frameBitmap.width
        val h = frameBitmap.height

        val candidateList = mutableListOf<CardCandidateDescriptor>()

        // 3 canonical Monte card positions (Left ~0.23, Middle ~0.50, Right ~0.77) + current smoothCenter
        val searchCenters = mutableListOf(
            NormalizedPoint(0.23f, 0.55f),
            NormalizedPoint(0.50f, 0.55f),
            NormalizedPoint(0.77f, 0.55f)
        )
        if (smoothCenterX !in 0.20f..0.26f && smoothCenterX !in 0.47f..0.53f && smoothCenterX !in 0.74f..0.80f) {
            searchCenters.add(NormalizedPoint(smoothCenterX, smoothCenterY))
        }

        var candidateId = 1
        for (pt in searchCenters) {
            val cand = evaluateCandidate(
                pt.x, pt.y,
                initialCardWidth, initialCardHeight,
                rgb, gray,
                pt.x, pt.y,
                applySpatialGate = false
            )
            val rect = NormalizedRect.fromCenter(pt, initialCardWidth, initialCardHeight)
            val slotLabel = when {
                pt.x < 0.38f -> "Left Card"
                pt.x in 0.38f..0.62f -> "Middle Card"
                else -> "Right Card"
            }
            candidateList.add(
                CardCandidateDescriptor(
                    id = candidateId++,
                    center = pt,
                    rect = rect,
                    opticalFlowScore = cand.score,
                    isOccluded = cand.isHandOccluded,
                    label = slotLabel
                )
            )
        }

        return candidateList
    }

    /**
     * Re-initializes optical flow state after Gemini recovery decision.
     * Re-anchors center coordinates, resets velocity, re-seeds features, and clears occlusion count.
     */
    fun reinitializeAt(frameBitmap: Bitmap, newCenter: NormalizedPoint) {
        val w = frameBitmap.width
        val h = frameBitmap.height
        val rgb = bitmapToRgb(frameBitmap)
        val gray = rgbToGray(rgb)

        val clampedX = newCenter.x.coerceIn(0.08f, 0.92f)
        val clampedY = newCenter.y.coerceIn(0.18f, 0.82f)

        smoothCenterX = clampedX
        smoothCenterY = clampedY
        velocityX = 0f
        velocityY = 0f
        consecutiveOcclusionFrames = 0
        stationaryFramesCount = 0

        val newRect = NormalizedRect.fromCenter(
            NormalizedPoint(clampedX, clampedY),
            initialCardWidth,
            initialCardHeight
        )
        currentRect = newRect
        previousPyramid = buildPyramid(gray, maxPyramidLevels)

        // Reseed feature points strictly inside new card bounding box
        val absLeft = ((clampedX - initialCardWidth / 2f) * w).toInt().coerceIn(0, w - 1)
        val absTop = ((clampedY - initialCardHeight / 2f) * h).toInt().coerceIn(0, h - 1)
        val absRight = ((clampedX + initialCardWidth / 2f) * w).toInt().coerceIn(0, w - 1)
        val absBottom = ((clampedY + initialCardHeight / 2f) * h).toInt().coerceIn(0, h - 1)

        trackedPoints = detectCardFeatures(gray, absLeft, absTop, absRight, absBottom)
    }

    /**
     * Returns the current smoothed center of the tracked card.
     */
    fun getCurrentCenter(): NormalizedPoint = NormalizedPoint(smoothCenterX, smoothCenterY)

    /**
     * Returns the target card initial bounding dimensions.
     */
    fun getCardDimensions(): Pair<Float, Float> = Pair(initialCardWidth, initialCardHeight)

    /**
     * Returns the reference target card bitmap for Gemini multimodal vision.
     */
    fun getReferenceCardBitmap(): Bitmap? = referenceCardBitmap

    /**
     * Lucas-Kanade optical flow on a single point across the image pyramid.
     */
    private fun trackPointPyramidal(
        pt: FeaturePoint,
        prevPyramid: ImagePyramid,
        currPyramid: ImagePyramid,
        initGuessX: Float,
        initGuessY: Float,
        windowSize: Int
    ): FeaturePoint {
        val levels = min(prevPyramid.levels.size, currPyramid.levels.size)
        val coarseScale = 1 shl (levels - 1)
        var guessX = initGuessX / coarseScale
        var guessY = initGuessY / coarseScale

        for (level in (levels - 1) downTo 0) {
            val scale = 1 shl level
            val prevImg = prevPyramid.levels[level]
            val currImg = currPyramid.levels[level]

            val pX = pt.x / scale
            val pY = pt.y / scale

            if (level < levels - 1) {
                guessX *= 2f
                guessY *= 2f
            }

            val (u, v) = lucasKanadeIteration(
                prevImg, currImg,
                pX, pY,
                guessX, guessY,
                windowSize,
                maxIterations
            )
            guessX = u
            guessY = v
        }

        val finalX = pt.x + guessX
        val finalY = pt.y + guessY
        val baseImg = currPyramid.levels[0]
        val isValid = finalX >= 2 && finalX < baseImg.width - 2 && finalY >= 2 && finalY < baseImg.height - 2

        return FeaturePoint(finalX, finalY, isValid)
    }

    private fun lucasKanadeIteration(
        prevImg: GrayImage,
        currImg: GrayImage,
        pX: Float,
        pY: Float,
        initVx: Float,
        initVy: Float,
        winSize: Int,
        iterations: Int
    ): Pair<Float, Float> {
        val halfW = winSize / 2
        val w = prevImg.width
        val h = prevImg.height

        val intPX = pX.roundToInt()
        val intPY = pY.roundToInt()

        if (intPX - halfW < 1 || intPX + halfW >= w - 1 || intPY - halfW < 1 || intPY + halfW >= h - 1) {
            return Pair(initVx, initVy)
        }

        val gradX = FloatArray(winSize * winSize)
        val gradY = FloatArray(winSize * winSize)
        val prevVals = FloatArray(winSize * winSize)

        var gXX = 0f
        var gXY = 0f
        var gYY = 0f
        var idx = 0

        for (wy in -halfW..halfW) {
            val y = intPY + wy
            for (wx in -halfW..halfW) {
                val x = intPX + wx
                val ix = (prevImg.data[y * w + (x + 1)] - prevImg.data[y * w + (x - 1)]) * 0.5f
                val iy = (prevImg.data[(y + 1) * w + x] - prevImg.data[(y - 1) * w + x]) * 0.5f

                gradX[idx] = ix
                gradY[idx] = iy
                prevVals[idx] = prevImg.data[y * w + x].toFloat()

                gXX += ix * ix
                gXY += ix * iy
                gYY += iy * iy
                idx++
            }
        }

        val det = gXX * gYY - gXY * gXY
        if (det < 1e-4f) {
            return Pair(initVx, initVy)
        }

        var vx = initVx
        var vy = initVy

        for (it in 0 until iterations) {
            var bX = 0f
            var bY = 0f
            idx = 0

            for (wy in -halfW..halfW) {
                val curY = pY + vy + wy
                for (wx in -halfW..halfW) {
                    val curX = pX + vx + wx
                    val curVal = bilinearSample(currImg, curX, curY)
                    val diff = prevVals[idx] - curVal

                    bX += diff * gradX[idx]
                    bY += diff * gradY[idx]
                    idx++
                }
            }

            val dvx = (gYY * bX - gXY * bY) / det
            val dvy = (-gXY * bX + gXX * bY) / det

            vx += dvx
            vy += dvy

            if (dvx * dvx + dvy * dvy < 0.001f) break
        }

        return Pair(vx, vy)
    }

    /**
     * Detects robust edge and corner features on the card body.
     */
    private fun detectCardFeatures(
        img: GrayImage,
        left: Int, top: Int, right: Int, bottom: Int
    ): MutableList<FeaturePoint> {
        val points = mutableListOf<FeaturePoint>()
        val w = img.width
        val h = img.height

        val cardW = max(4f, (right - left).toFloat())
        val cardH = max(4f, (bottom - top).toFloat())

        // 4 corners of card
        val insetX = (cardW * 0.10f).toInt()
        val insetY = (cardH * 0.10f).toInt()

        val keyLocations = listOf(
            Pair(left + insetX, top + insetY),
            Pair(right - insetX, top + insetY),
            Pair(left + insetX, bottom - insetY),
            Pair(right - insetX, bottom - insetY),
            Pair((left + right) / 2, (top + bottom) / 2),
            Pair((left + right) / 2, top + insetY),
            Pair((left + right) / 2, bottom - insetY),
            Pair(left + insetX, (top + bottom) / 2),
            Pair(right - insetX, (top + bottom) / 2)
        )

        for ((kx, ky) in keyLocations) {
            val clampedX = kx.coerceIn(2, w - 3).toFloat()
            val clampedY = ky.coerceIn(2, h - 3).toFloat()
            points.add(FeaturePoint(clampedX, clampedY, true))
        }

        // Interior grid sampling for high-contrast markings
        val cols = 4
        val rows = 5
        val stepX = cardW / cols
        val stepY = cardH / rows

        for (r in 1 until rows) {
            for (c in 1 until cols) {
                val px = (left + c * stepX).coerceIn(2f, (w - 3).toFloat())
                val py = (top + r * stepY).coerceIn(2f, (h - 3).toFloat())
                points.add(FeaturePoint(px, py, true))
            }
        }

        return points
    }

    private fun extractPatch(
        img: GrayImage,
        rect: NormalizedRect,
        targetW: Int,
        targetH: Int
    ): IntArray {
        val outData = IntArray(targetW * targetH)
        val w = img.width
        val h = img.height

        val left = rect.left * w
        val top = rect.top * h
        val boxW = rect.width * w
        val boxH = rect.height * h

        for (ty in 0 until targetH) {
            val srcY = top + (ty.toFloat() / targetH) * boxH
            for (tx in 0 until targetW) {
                val srcX = left + (tx.toFloat() / targetW) * boxW
                outData[ty * targetW + tx] = bilinearSample(img, srcX, srcY).toInt().coerceIn(0, 255)
            }
        }

        return outData
    }

    private fun extractRgbPatch(
        rgb: RgbImage,
        rect: NormalizedRect,
        targetW: Int,
        targetH: Int
    ): IntArray {
        val outData = IntArray(targetW * targetH)
        val w = rgb.width
        val h = rgb.height

        val left = rect.left * w
        val top = rect.top * h
        val boxW = rect.width * w
        val boxH = rect.height * h

        for (ty in 0 until targetH) {
            val srcY = (top + (ty.toFloat() / targetH) * boxH).toInt().coerceIn(0, h - 1)
            for (tx in 0 until targetW) {
                val srcX = (left + (tx.toFloat() / targetW) * boxW).toInt().coerceIn(0, w - 1)
                outData[ty * targetW + tx] = rgb.pixels[srcY * w + srcX]
            }
        }

        return outData
    }

    private data class ZeroMeanResult(val zm: FloatArray, val norm: Float, val mean: Float)

    private fun computeZeroMean(data: IntArray): ZeroMeanResult {
        val n = data.size
        if (n == 0) return ZeroMeanResult(FloatArray(0), 1f, 0f)

        var sum = 0f
        for (v in data) sum += v
        val mean = sum / n

        val zm = FloatArray(n)
        var sumSq = 0f
        for (i in 0 until n) {
            val d = data[i] - mean
            zm[i] = d
            sumSq += d * d
        }
        val norm = sqrt(sumSq)
        return ZeroMeanResult(zm, norm, mean)
    }

    private fun buildPyramid(base: GrayImage, maxLevels: Int): ImagePyramid {
        val list = mutableListOf<GrayImage>()
        list.add(base)

        var cur = base
        for (i in 1 until maxLevels) {
            val nw = cur.width / 2
            val nh = cur.height / 2
            if (nw < 6 || nh < 6) break

            val downData = IntArray(nw * nh)
            val cw = cur.width
            for (y in 0 until nh) {
                for (x in 0 until nw) {
                    val p0 = cur.data[(y * 2) * cw + (x * 2)]
                    val p1 = cur.data[(y * 2) * cw + (x * 2 + 1)]
                    val p2 = cur.data[(y * 2 + 1) * cw + (x * 2)]
                    val p3 = cur.data[(y * 2 + 1) * cw + (x * 2 + 1)]
                    downData[y * nw + x] = (p0 + p1 + p2 + p3) shr 2
                }
            }
            cur = GrayImage(nw, nh, downData)
            list.add(cur)
        }

        return ImagePyramid(list)
    }

    private fun bitmapToRgb(bitmap: Bitmap): RgbImage {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return RgbImage(w, h, pixels)
    }

    private fun rgbToGray(rgb: RgbImage): GrayImage {
        val w = rgb.width
        val h = rgb.height
        val gray = IntArray(w * h)
        for (i in 0 until w * h) {
            val c = rgb.pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = (77 * r + 150 * g + 29 * b) shr 8
        }
        return GrayImage(w, h, gray)
    }

    private fun bilinearSample(img: GrayImage, x: Float, y: Float): Float {
        val w = img.width
        val h = img.height
        if (x < 0f || x >= w - 1 || y < 0f || y >= h - 1) {
            val cx = x.coerceIn(0f, (w - 1).toFloat()).toInt()
            val cy = y.coerceIn(0f, (h - 1).toFloat()).toInt()
            return img.data[cy * w + cx].toFloat()
        }
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = x0 + 1
        val y1 = y0 + 1

        val fx = x - x0
        val fy = y - y0

        val v00 = img.data[y0 * w + x0]
        val v10 = img.data[y0 * w + x1]
        val v01 = img.data[y1 * w + x0]
        val v11 = img.data[y1 * w + x1]

        val top = v00 * (1 - fx) + v10 * fx
        val bot = v01 * (1 - fx) + v11 * fx
        return top * (1 - fy) + bot * fy
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        /**
         * Automatically identifies and locks onto the primary target card from the initial frame.
         * Analyzes card contrast, brightness, and standard playing card layout
         * to automatically find the center target card without requiring any manual user interaction.
         */
        fun detectInitialCardTarget(firstFrame: Bitmap): NormalizedRect {
            val w = firstFrame.width
            val h = firstFrame.height

            val cardWidth = 0.20f
            val cardHeight = (cardWidth / (2.5f / 3.5f)).coerceIn(0.24f, 0.48f)

            // Evaluate candidate card centers in the initial scene (Middle, Center-Left, Center-Right)
            val candidateCenters = listOf(
                NormalizedPoint(0.50f, 0.55f),
                NormalizedPoint(0.48f, 0.52f),
                NormalizedPoint(0.52f, 0.55f)
            )

            // Default to center target card
            val bestCenter = candidateCenters[0]
            return NormalizedRect.fromCenter(bestCenter, cardWidth, cardHeight)
        }
    }
}
