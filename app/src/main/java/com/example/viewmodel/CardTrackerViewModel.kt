package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.BenchmarkVideoProvider
import com.example.engine.OpticalFlowTracker
import com.example.model.BenchmarkVideo
import com.example.model.ConfidenceLevel
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.model.TrackedFrame
import com.example.model.TrackingResult
import com.example.model.TrailEffectStyle
import com.example.model.TrailLengthMode
import com.example.video.VideoFrameExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

enum class AppScreen {
    UPLOAD,
    SELECT_TARGET,
    TRACKING_PROGRESS,
    RESULT_VIEW
}

data class TargetSelectionState(
    val firstFrame: Bitmap? = null,
    val selectedBox: NormalizedRect? = null,
    val selectedPoint: NormalizedPoint? = null,
    val targetSizeScale: Float = 1.0f
)

const val CARD_ASPECT_RATIO = 2.5f / 3.5f // Standard playing card ratio (width / height = 0.714)

enum class TargetSizeOption(val label: String, val scale: Float, val labelAr: String) {
    SMALL("Small", 0.75f, "صغير"),
    STANDARD("Standard", 1.0f, "قياسي"),
    LARGE("Large", 1.30f, "كبير"),
    EXTRA_LARGE("XL", 1.60f, "كبير جداً")
}

data class TrackingProgressState(
    val currentFrame: Int = 0,
    val totalFrames: Int = 0,
    val progress: Float = 0f,
    val currentConfidence: Float = 1f,
    val isOccluded: Boolean = false,
    val currentFps: Float = 0f,
    val statusMessage: String = "Initializing optical flow...",
    val previewBitmap: Bitmap? = null
)

data class ScrubberPlaybackState(
    val isPlaying: Boolean = false,
    val currentFrameIndex: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val trailEffect: TrailEffectStyle = TrailEffectStyle.NEON_GLOW,
    val trailLength: TrailLengthMode = TrailLengthMode.FULL,
    val showBoundingBox: Boolean = true,
    val showArrow: Boolean = true,
    val showTelemetryHud: Boolean = true
)

class CardTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "CardTrackerViewModel"
    private val frameExtractor = VideoFrameExtractor(application.applicationContext)
    private var trackingJob: Job? = null
    private var playbackJob: Job? = null

    // Navigation state
    private val _currentScreen = MutableStateFlow(AppScreen.UPLOAD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Loading indicator when user picks a video
    private val _isImportingVideo = MutableStateFlow(false)
    val isImportingVideo: StateFlow<Boolean> = _isImportingVideo.asStateFlow()

    // Video source info
    private val _activeVideoTitle = MutableStateFlow("Benchmark Shuffle")
    val activeVideoTitle: StateFlow<String> = _activeVideoTitle.asStateFlow()

    private var loadedFrames: List<Bitmap> = emptyList()
    private var cachedVideoFile: File? = null
    private var cachedVideoUri: Uri? = null
    private var isUsingBenchmark: Boolean = false
    private var activeBenchmarkId: String? = null

    // Target Selection state
    private val _selectionState = MutableStateFlow(TargetSelectionState())
    val selectionState: StateFlow<TargetSelectionState> = _selectionState.asStateFlow()

    // Tracking Progress state
    private val _progressState = MutableStateFlow(TrackingProgressState())
    val progressState: StateFlow<TrackingProgressState> = _progressState.asStateFlow()

    // Final result
    private val _trackingResult = MutableStateFlow<TrackingResult?>(null)
    val trackingResult: StateFlow<TrackingResult?> = _trackingResult.asStateFlow()

    // Scrubber / Playback state for reviewing the tracked trajectory
    private val _playbackState = MutableStateFlow(ScrubberPlaybackState())
    val playbackState: StateFlow<ScrubberPlaybackState> = _playbackState.asStateFlow()

    // Error message banner
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun dismissError() {
        _errorMessage.value = null
    }

    /**
     * Loads a pre-built benchmark video sequence.
     */
    fun selectBenchmarkVideo(benchmark: BenchmarkVideo) {
        viewModelScope.launch {
            _activeVideoTitle.value = benchmark.title
            isUsingBenchmark = true
            activeBenchmarkId = benchmark.id
            cachedVideoFile = null
            cachedVideoUri = null

            // Generate frames in background
            val frames = withContext(Dispatchers.Default) {
                BenchmarkVideoProvider.generateFrames(benchmark.id)
            }
            loadedFrames = frames

            if (frames.isNotEmpty()) {
                val first = frames.first()
                val defaultBox = BenchmarkVideoProvider.getInitialTargetBox(benchmark.id)
                _selectionState.value = TargetSelectionState(
                    firstFrame = first,
                    selectedBox = defaultBox,
                    selectedPoint = NormalizedPoint(defaultBox.centerX, defaultBox.centerY),
                    targetSizeScale = 1.0f
                )
                _currentScreen.value = AppScreen.SELECT_TARGET
            } else {
                _errorMessage.value = "Failed to load benchmark frames."
            }
        }
    }

    /**
     * Loads a user-uploaded video via Content Uri (MP4, MOV).
     * Copies video to cache file to ensure persistent, reliable frame access.
     */
    fun selectUploadedVideo(uri: Uri, displayName: String) {
        viewModelScope.launch {
            _isImportingVideo.value = true
            _activeVideoTitle.value = displayName
            isUsingBenchmark = false
            activeBenchmarkId = null
            cachedVideoUri = uri
            loadedFrames = emptyList()

            try {
                Log.d(tag, "Importing video URI: $uri ($displayName)")
                val cachedFile = frameExtractor.copyUriToCache(uri, displayName)
                if (cachedFile == null || !cachedFile.exists()) {
                    _errorMessage.value = "Unable to read video file. Please check file access permissions or choose another file."
                    _isImportingVideo.value = false
                    return@launch
                }
                cachedVideoFile = cachedFile

                val metadata = frameExtractor.getMetadata(cachedFile, uri)
                if (metadata != null && metadata.durationMs > 90_000L) {
                    _errorMessage.value = "Video is long (${(metadata.durationMs / 1000)}s). For optimal mobile tracking performance, clips under 60 seconds are recommended."
                }

                // Extract first frame immediately with multi-tier fallback for all video formats
                val firstFrame = frameExtractor.extractFirstFrame(cachedFile, uri)
                if (firstFrame == null) {
                    _errorMessage.value = "Could not decode video frames. Please check if the video file is corrupted or choose another clip."
                    _isImportingVideo.value = false
                    return@launch
                }

                // Default card box centered on frame with fixed playing card aspect ratio
                val cardWidth = 0.20f
                val cardHeight = (cardWidth / CARD_ASPECT_RATIO).coerceAtMost(0.65f)
                val defaultBox = NormalizedRect.fromCenter(
                    NormalizedPoint(0.5f, 0.5f),
                    cardWidth,
                    cardHeight
                )

                _selectionState.value = TargetSelectionState(
                    firstFrame = firstFrame,
                    selectedBox = defaultBox,
                    selectedPoint = NormalizedPoint(0.5f, 0.5f),
                    targetSizeScale = 1.0f
                )
                loadedFrames = listOf(firstFrame)

                // Ready to mark target card!
                _currentScreen.value = AppScreen.SELECT_TARGET
            } catch (e: Exception) {
                Log.e(tag, "Exception importing video", e)
                _errorMessage.value = "Error processing video: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isImportingVideo.value = false
            }
        }
    }

    /**
     * User tapped on the first frame to select target position.
     * Positions the fixed-size card bounding box directly centered on the tap point.
     */
    fun onFrameTapped(normalizedX: Float, normalizedY: Float) {
        val tapPt = NormalizedPoint(normalizedX.coerceIn(0.05f, 0.95f), normalizedY.coerceIn(0.05f, 0.95f))
        val current = _selectionState.value
        val width = (0.20f * current.targetSizeScale).coerceIn(0.08f, 0.48f)
        val height = (width / CARD_ASPECT_RATIO).coerceIn(0.12f, 0.70f)

        val newBox = NormalizedRect.fromCenter(tapPt, width, height)
        _selectionState.value = current.copy(
            selectedPoint = tapPt,
            selectedBox = newBox
        )
    }

    /**
     * User drew a custom ROI box (kept for compatibility).
     */
    fun onBoxDrawn(rect: NormalizedRect) {
        val current = _selectionState.value
        _selectionState.value = current.copy(
            selectedBox = rect,
            selectedPoint = NormalizedPoint(rect.centerX, rect.centerY)
        )
    }

    /**
     * User adjusts the target size scale (Predefined Size option).
     */
    fun setTargetSizeScale(scale: Float) {
        val current = _selectionState.value
        val pt = current.selectedPoint ?: NormalizedPoint(0.5f, 0.5f)
        val width = (0.20f * scale).coerceIn(0.08f, 0.48f)
        val height = (width / CARD_ASPECT_RATIO).coerceIn(0.12f, 0.70f)
        val newBox = NormalizedRect.fromCenter(pt, width, height)

        _selectionState.value = current.copy(
            targetSizeScale = scale,
            selectedBox = newBox
        )
    }

    /**
     * Alias for setTargetSizeScale to support legacy callers.
     */
    fun setBoxScale(scale: Float) {
        setTargetSizeScale(scale)
    }

    /**
     * Starts the optical flow tracking analysis.
     */
    fun startAnalysis() {
        val targetBox = _selectionState.value.selectedBox ?: return

        _currentScreen.value = AppScreen.TRACKING_PROGRESS
        _progressState.value = TrackingProgressState(
            totalFrames = if (loadedFrames.size > 1) loadedFrames.size else 60,
            statusMessage = "Preparing video sequence for optical flow..."
        )

        trackingJob?.cancel()
        trackingJob = viewModelScope.launch(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()

            // If this is an uploaded video and we only have the first frame, extract full tracking sequence now
            val framesToProcess = if (isUsingBenchmark) {
                loadedFrames
            } else {
                val file = cachedVideoFile
                if (file != null && file.exists()) {
                    withContext(Dispatchers.Main) {
                        _progressState.value = _progressState.value.copy(
                            statusMessage = "Extracting video frames..."
                        )
                    }
                    val extracted = frameExtractor.extractTrackingFrames(file, cachedVideoUri, targetFps = 20) { current, total ->
                        _progressState.value = _progressState.value.copy(
                            currentFrame = current,
                            totalFrames = total,
                            progress = (current.toFloat() / total) * 0.35f,
                            statusMessage = "Extracting frame $current of $total..."
                        )
                    }
                    if (extracted.isNotEmpty()) extracted else loadedFrames
                } else {
                    loadedFrames
                }
            }

            if (framesToProcess.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "No video frames could be extracted for tracking."
                    _currentScreen.value = AppScreen.UPLOAD
                }
                return@launch
            }

            loadedFrames = framesToProcess

            val tracker = OpticalFlowTracker()
            val trackedFrames = mutableListOf<TrackedFrame>()

            // Initialize on Frame 0
            val firstBmp = framesToProcess[0]
            tracker.initialize(firstBmp, targetBox)

            val initialTracked = TrackedFrame(
                frameIndex = 0,
                timestampMs = 0L,
                rect = targetBox,
                center = NormalizedPoint(targetBox.centerX, targetBox.centerY),
                confidence = 1.0f,
                trackingState = com.example.model.TrackingState.TRACKING_LOCKED,
                isOccluded = false,
                inlierCount = 36,
                bitmap = firstBmp
            )
            trackedFrames.add(initialTracked)

            var occlusionCount = 0
            var prevOccluded = false
            var lostLock = false

            val totalFramesCount = framesToProcess.size
            for (i in 1 until totalFramesCount) {
                val frameBmp = framesToProcess[i]
                val frameTimestamp = (i * 33L)
                val tracked = tracker.processFrame(frameBmp, i, frameTimestamp)
                trackedFrames.add(tracked)

                if (tracked.isOccluded && !prevOccluded) {
                    occlusionCount++
                }
                prevOccluded = tracked.isOccluded

                if (tracked.confidence < 0.25f) {
                    lostLock = true
                }

                // Update progress UI
                val progress = 0.35f + (i.toFloat() / (totalFramesCount - 1).coerceAtLeast(1)) * 0.65f
                val statusText = when {
                    tracked.isOccluded -> "Occlusion detected: Coasting along motion trajectory"
                    tracked.confidence < 0.40f -> "Low confidence: Re-evaluating candidate matches"
                    else -> "Optical flow locked (${tracked.inlierCount} active feature vectors)"
                }

                _progressState.value = TrackingProgressState(
                    currentFrame = i + 1,
                    totalFrames = totalFramesCount,
                    progress = progress.coerceIn(0f, 1f),
                    currentConfidence = tracked.confidence,
                    isOccluded = tracked.isOccluded,
                    currentFps = if (i > 0) (i * 1000f) / (System.currentTimeMillis() - startTime).coerceAtLeast(1) else 30f,
                    statusMessage = statusText,
                    previewBitmap = frameBmp
                )

                delay(12)
            }

            val totalDurationMs = System.currentTimeMillis() - startTime
            val finalFrame = trackedFrames.last()

            // Calculate trajectory kinematics metrics
            var totalDistance = 0f
            for (k in 1 until trackedFrames.size) {
                val p0 = trackedFrames[k - 1].center
                val p1 = trackedFrames[k].center
                val dx = p1.x - p0.x
                val dy = p1.y - p0.y
                totalDistance += kotlin.math.sqrt(dx * dx + dy * dy)
            }
            val peakSpeed = trackedFrames.maxOfOrNull { it.speed } ?: 0f
            val avgSpeed = if (trackedFrames.isNotEmpty()) trackedFrames.map { it.speed }.average().toFloat() else 0f

            // Calculate confidence metric
            val avgConf = trackedFrames.map { it.confidence }.average().toFloat()
            val confLevel = when {
                avgConf >= 0.70f && !lostLock -> ConfidenceLevel.HIGH
                avgConf >= 0.45f -> ConfidenceLevel.MEDIUM
                else -> ConfidenceLevel.LOW
            }

            // Estimate final resting slot
            val finalSlot = determineSlot(finalFrame.center.x)

            val diagnosticNotes = mutableListOf<String>()
            diagnosticNotes.add("Processed ${trackedFrames.size} frames in ${totalDurationMs}ms (${String.format("%.1f", (trackedFrames.size * 1000f) / totalDurationMs)} FPS).")
            diagnosticNotes.add("Final object coordinates: x=${(finalFrame.center.x * 100).toInt()}%, y=${(finalFrame.center.y * 100).toInt()}% ($finalSlot).")
            diagnosticNotes.add("Trajectory Analysis: Total displacement ${(totalDistance * 100).toInt()}% screen, Peak speed ${String.format("%.2f", peakSpeed * 30)} screens/s.")
            diagnosticNotes.add("Occlusion events handled: $occlusionCount.")
            if (isUsingBenchmark && activeBenchmarkId != null) {
                val groundTruth = BenchmarkVideoProvider.getGroundTruthFinalSlot(activeBenchmarkId!!)
                diagnosticNotes.add("Benchmark Ground Truth: $groundTruth (Result matches: ${groundTruth == finalSlot}).")
            }

            val result = TrackingResult(
                videoTitle = _activeVideoTitle.value,
                totalFrames = trackedFrames.size,
                trackedFrames = trackedFrames,
                finalFrameIndex = trackedFrames.size - 1,
                finalRect = finalFrame.rect,
                finalCenter = finalFrame.center,
                finalSlot = finalSlot,
                averageConfidence = avgConf,
                confidenceLevel = confLevel,
                occlusionEventsCount = occlusionCount,
                totalProcessingTimeMs = totalDurationMs,
                processingFps = (trackedFrames.size * 1000f) / max(1L, totalDurationMs),
                lostLockFlag = lostLock,
                diagnosticNotes = diagnosticNotes,
                totalDistanceTraveled = totalDistance,
                peakSpeed = peakSpeed,
                averageSpeed = avgSpeed
            )

            withContext(Dispatchers.Main) {
                _trackingResult.value = result
                _playbackState.value = ScrubberPlaybackState(
                    isPlaying = false,
                    currentFrameIndex = trackedFrames.size - 1,
                    playbackSpeed = 1.0f,
                    trailEffect = TrailEffectStyle.NEON_GLOW,
                    trailLength = TrailLengthMode.FULL,
                    showBoundingBox = true,
                    showArrow = true,
                    showTelemetryHud = true
                )
                _currentScreen.value = AppScreen.RESULT_VIEW
            }
        }
    }

    private fun determineSlot(centerX: Float): String {
        return when {
            centerX < 0.38f -> "Left Position"
            centerX in 0.38f..0.62f -> "Middle Position"
            else -> "Right Position"
        }
    }

    /**
     * Interactive Scrubber & Video Player Controls
     */
    fun seekToFrame(frameIndex: Int) {
        val result = _trackingResult.value ?: return
        val clamped = frameIndex.coerceIn(0, result.totalFrames - 1)
        _playbackState.value = _playbackState.value.copy(
            currentFrameIndex = clamped,
            isPlaying = false
        )
    }

    fun togglePlayback() {
        val current = _playbackState.value
        if (current.isPlaying) {
            playbackJob?.cancel()
            _playbackState.value = current.copy(isPlaying = false)
        } else {
            startPlaybackLoop()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
    }

    fun setTrailEffect(effect: TrailEffectStyle) {
        _playbackState.value = _playbackState.value.copy(trailEffect = effect)
    }

    fun setTrailLength(lengthMode: TrailLengthMode) {
        _playbackState.value = _playbackState.value.copy(trailLength = lengthMode)
    }

    fun toggleBoundingBox() {
        val curr = _playbackState.value.showBoundingBox
        _playbackState.value = _playbackState.value.copy(showBoundingBox = !curr)
    }

    fun toggleArrow() {
        val curr = _playbackState.value.showArrow
        _playbackState.value = _playbackState.value.copy(showArrow = !curr)
    }

    fun toggleTelemetryHud() {
        val curr = _playbackState.value.showTelemetryHud
        _playbackState.value = _playbackState.value.copy(showTelemetryHud = !curr)
    }

    private fun startPlaybackLoop() {
        val result = _trackingResult.value ?: return
        playbackJob?.cancel()
        _playbackState.value = _playbackState.value.copy(isPlaying = true)

        playbackJob = viewModelScope.launch {
            while (_playbackState.value.isPlaying) {
                val currentIdx = _playbackState.value.currentFrameIndex
                val nextIdx = if (currentIdx >= result.totalFrames - 1) 0 else currentIdx + 1
                _playbackState.value = _playbackState.value.copy(currentFrameIndex = nextIdx)

                val frameDelayMs = (40L / _playbackState.value.playbackSpeed).toLong().coerceAtLeast(15L)
                delay(frameDelayMs)
            }
        }
    }

    /**
     * Navigates back to target selection to pick another card or re-adjust box.
     */
    fun repickTarget() {
        playbackJob?.cancel()
        _currentScreen.value = AppScreen.SELECT_TARGET
    }

    /**
     * Resets back to upload screen.
     */
    fun resetToUpload() {
        playbackJob?.cancel()
        trackingJob?.cancel()
        _currentScreen.value = AppScreen.UPLOAD
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        trackingJob?.cancel()
    }
}
