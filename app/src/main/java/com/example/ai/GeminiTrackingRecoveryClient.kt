package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Candidate card detected in the scene during ambiguity or tracking error.
 */
data class CardCandidateDescriptor(
    val id: Int,
    val center: NormalizedPoint,
    val rect: NormalizedRect,
    val opticalFlowScore: Float,
    val isOccluded: Boolean,
    val label: String
)

/**
 * Result of Gemini Visual Verification and Target Decision Layer.
 */
data class GeminiRecoveryDecision(
    val success: Boolean,
    val chosenCandidateId: Int?,
    val correctedCenter: NormalizedPoint?,
    val confidence: Float,
    val reason: String,
    val rawModelResponse: String? = null
)

/**
 * Production-ready, non-blocking Gemini AI Decision Layer.
 * Selectively called only on tracking errors, occlusions, crossing swaps, or low confidence.
 * Always handles network errors, timeout, missing keys, and invalid responses gracefully without crashing.
 */
class GeminiTrackingRecoveryClient {

    private val tag = "GeminiRecoveryClient"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    /**
     * Inspects candidate cards and visual frame using Gemini 3.5 Flash multimodal vision.
     * Evaluates target appearance, motion history, card suit/value, and temporal consistency
     * to pick the true original target card and output the corrected coordinates.
     */
    suspend fun recoverTarget(
        currentFrameBitmap: Bitmap,
        referenceCardBitmap: Bitmap?,
        candidates: List<CardCandidateDescriptor>,
        lastConfirmedPosition: NormalizedPoint?,
        motionTrajectoryDescription: String,
        errorReason: String
    ): GeminiRecoveryDecision = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNullOrBlank() || apiKey == "placeholder_api_key") {
            Log.w(tag, "Gemini API key is not configured or is placeholder. Using smart local kinematic fallback.")
            return@withContext localHeuristicFallback(candidates, lastConfirmedPosition, "Gemini API key not configured")
        }

        try {
            // Encode bitmaps to compact JPEG Base64
            val frameBase64 = currentFrameBitmap.toCompressedBase64(maxDimension = 640, quality = 82)
            val refBase64 = referenceCardBitmap?.toCompressedBase64(maxDimension = 280, quality = 85)

            val candidateListText = candidates.mapIndexed { idx, c ->
                "Candidate #${c.id}: Center=(x=${String.format("%.3f", c.center.x)}, y=${String.format("%.3f", c.center.y)}), " +
                "Box=[left=${String.format("%.3f", c.rect.left)}, top=${String.format("%.3f", c.rect.top)}, right=${String.format("%.3f", c.rect.right)}, bottom=${String.format("%.3f", c.rect.bottom)}], " +
                "FlowScore=${String.format("%.2f", c.opticalFlowScore)}, Occluded=${c.isOccluded}, Slot=${c.label}"
            }.joinToString("\n")

            val prompt = """
You are an expert computer vision AI tracking arbiter specializing in Three-Card Monte, card game shuffles, and high-speed occlusion recovery.
An optical-flow tracker encountered a tracking anomaly: $errorReason.

Motion History & Context:
- Last confirmed target position: ${lastConfirmedPosition?.let { "x=${String.format("%.3f", it.x)}, y=${String.format("%.3f", it.y)}" } ?: "unknown"}
- Trajectory context: $motionTrajectoryDescription

Detected Candidate Cards in current frame:
$candidateListText

TASK:
Analyze the video frame image (and reference target card if provided).
1. Compare appearance, card suit/face, brightness, and position continuity with the original target.
2. If cards crossed, swapped, or were momentarily occluded by hands, determine which candidate is the TRUE original target card.
3. Return the chosen candidate ID and exact normalized center coordinates (x between 0.08 and 0.92, y between 0.18 and 0.82).

Respond ONLY with valid JSON conforming to this exact structure:
{
  "chosenCandidateId": 1,
  "correctedCenterX": 0.50,
  "correctedCenterY": 0.55,
  "confidence": 0.95,
  "reason": "Clear visual match with original target card after swap"
}
""".trimIndent()

            val contentsParts = JSONArray()

            // 1. Text Prompt
            val textPart = JSONObject()
            textPart.put("text", prompt)
            contentsParts.put(textPart)

            // 2. Reference Card Image if available
            if (refBase64 != null) {
                val refPart = JSONObject()
                val refInline = JSONObject()
                refInline.put("mimeType", "image/jpeg")
                refInline.put("data", refBase64)
                refPart.put("inlineData", refInline)
                contentsParts.put(refPart)
            }

            // 3. Current Video Frame Image
            val framePart = JSONObject()
            val frameInline = JSONObject()
            frameInline.put("mimeType", "image/jpeg")
            frameInline.put("data", frameBase64)
            framePart.put("inlineData", frameInline)
            contentsParts.put(framePart)

            val contentObj = JSONObject()
            contentObj.put("parts", contentsParts)

            val requestJson = JSONObject()
            val contentsArray = JSONArray()
            contentsArray.put(contentObj)
            requestJson.put("contents", contentsArray)

            // Generation config for deterministic JSON response
            val genConfig = JSONObject()
            genConfig.put("temperature", 0.1)
            genConfig.put("responseMimeType", "application/json")
            requestJson.put("generationConfig", genConfig)

            // Supported modern model for multimodal vision
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val body = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                Log.e(tag, "Gemini API error code: ${response.code}, message: $responseBody")
                return@withContext localHeuristicFallback(candidates, lastConfirmedPosition, "HTTP ${response.code}")
            }

            // Parse response
            val root = JSONObject(responseBody)
            val candidatesJson = root.optJSONArray("candidates")
            val firstCandidate = candidatesJson?.optJSONObject(0)
            val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
            val responseText = parts?.optJSONObject(0)?.optString("text")

            if (responseText.isNullOrBlank()) {
                return@withContext localHeuristicFallback(candidates, lastConfirmedPosition, "Empty Gemini response text")
            }

            val cleanedJson = responseText.trim().removeSurrounding("```json", "```").trim()
            val decisionObj = JSONObject(cleanedJson)

            val chosenId = decisionObj.optInt("chosenCandidateId", -1)
            val chosenX = decisionObj.optDouble("correctedCenterX", -1.0).toFloat()
            val chosenY = decisionObj.optDouble("correctedCenterY", -1.0).toFloat()
            val conf = decisionObj.optDouble("confidence", 0.85).toFloat()
            val reason = decisionObj.optString("reason", "Gemini recovery decision")

            if (chosenX in 0.05f..0.95f && chosenY in 0.10f..0.90f) {
                GeminiRecoveryDecision(
                    success = true,
                    chosenCandidateId = if (chosenId > 0) chosenId else null,
                    correctedCenter = NormalizedPoint(chosenX.coerceIn(0.08f, 0.92f), chosenY.coerceIn(0.18f, 0.82f)),
                    confidence = conf.coerceIn(0.5f, 1.0f),
                    reason = reason,
                    rawModelResponse = responseText
                )
            } else {
                localHeuristicFallback(candidates, lastConfirmedPosition, "Invalid coordinates from Gemini")
            }

        } catch (e: Exception) {
            Log.e(tag, "Exception during Gemini visual verification", e)
            localHeuristicFallback(candidates, lastConfirmedPosition, "Exception: ${e.message}")
        }
    }

    /**
     * Resilient offline fallback using kinematics, optical-flow score, and spatial continuity
     * ensuring zero crash or pause if internet is offline or API fails.
     */
    private fun localHeuristicFallback(
        candidates: List<CardCandidateDescriptor>,
        lastConfirmedPosition: NormalizedPoint?,
        fallbackReason: String
    ): GeminiRecoveryDecision {
        if (candidates.isEmpty()) {
            return GeminiRecoveryDecision(
                success = false,
                chosenCandidateId = null,
                correctedCenter = lastConfirmedPosition,
                confidence = 0.5f,
                reason = "Fallback: No candidates available ($fallbackReason)"
            )
        }

        // Rank candidates using optical flow score + distance penalty to last known position
        val best = candidates.maxByOrNull { c ->
            val dist = if (lastConfirmedPosition != null) {
                val dx = c.center.x - lastConfirmedPosition.x
                val dy = c.center.y - lastConfirmedPosition.y
                kotlin.math.sqrt(dx * dx + dy * dy)
            } else 0f
            val proximityBonus = (1.0f - dist.coerceIn(0f, 1f)) * 0.45f
            c.opticalFlowScore * 0.55f + proximityBonus
        } ?: candidates.first()

        return GeminiRecoveryDecision(
            success = true,
            chosenCandidateId = best.id,
            correctedCenter = best.center,
            confidence = best.opticalFlowScore.coerceIn(0.5f, 0.85f),
            reason = "Adaptive local recovery: selected Candidate #${best.id} ($fallbackReason)"
        )
    }

    private fun Bitmap.toCompressedBase64(maxDimension: Int, quality: Int): String {
        val scale = if (width > maxDimension || height > maxDimension) {
            val maxD = maxOf(width, height)
            maxDimension.toFloat() / maxD
        } else 1.0f

        val targetW = (width * scale).toInt().coerceAtLeast(1)
        val targetH = (height * scale).toInt().coerceAtLeast(1)

        val scaledBmp = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(this, targetW, targetH, true)
        } else this

        val stream = ByteArrayOutputStream()
        scaledBmp.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        val bytes = stream.toByteArray()
        if (scaledBmp !== this) {
            scaledBmp.recycle()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
