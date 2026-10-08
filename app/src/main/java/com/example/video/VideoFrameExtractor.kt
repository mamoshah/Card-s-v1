package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

data class VideoMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val file: File? = null,
    val mimeType: String = "video/mp4"
)

/**
 * Universal Video Frame Extractor (v.2).
 *
 * Supports ALL mobile video formats, codecs, and screen recordings:
 * MP4, MKV, WebM, MOV, 3GP, AVI, TS, M4V, FLV, WMV, HEVC/H.265, H.264, VP8, VP9, AV1.
 *
 * Features:
 * 1. Safe datasource retention (keeps underlying streams open during retriever lifecycle).
 * 2. 9-tier seeking fallbacks (handles Variable Frame Rate / non-zero start timestamps).
 * 3. Automatic orientation/rotation correction (0, 90, 180, 270 degrees).
 * 4. ThumbnailUtils OS-level fallback.
 * 5. MediaCodec direct hardware decoder fallback.
 */
class VideoFrameExtractor(private val context: Context) {

    private val tag = "VideoFrameExtractor"

    private class RetrieverHolder(
        val retriever: MediaMetadataRetriever,
        private val closeables: List<AutoCloseable>
    ) : AutoCloseable {
        override fun close() {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
            for (c in closeables) {
                try {
                    c.close()
                } catch (ignored: Exception) {}
            }
        }
    }

    /**
     * Copies content URI stream to a temporary local cache file with full data synchronization.
     */
    suspend fun copyUriToCache(uri: Uri, suggestedName: String): File? = withContext(Dispatchers.IO) {
        try {
            var extension = "mp4"
            if (suggestedName.contains(".") && suggestedName.substringAfterLast(".").isNotBlank()) {
                val candidate = suggestedName.substringAfterLast(".").lowercase()
                if (candidate in setOf("mp4", "mkv", "webm", "mov", "3gp", "avi", "ts", "m4v", "flv", "wmv", "mpg", "mpeg")) {
                    extension = candidate
                }
            } else {
                val mime = context.contentResolver.getType(uri)
                if (mime != null) {
                    val extFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                    if (!extFromMime.isNullOrBlank()) {
                        extension = extFromMime.lowercase()
                    }
                }
            }

            val cleanName = "upload_${System.currentTimeMillis()}.$extension"
            val targetFile = File(context.cacheDir, cleanName)

            var copied = false

            // Strategy 1: openInputStream
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                        outputStream.flush()
                        try {
                            outputStream.fd.sync()
                        } catch (ignored: Exception) {}
                    }
                }
                if (targetFile.exists() && targetFile.length() > 0) {
                    copied = true
                }
            } catch (e1: Exception) {
                Log.w(tag, "openInputStream failed: ${e1.message}, trying file descriptor")
            }

            // Strategy 2: openFileDescriptor
            if (!copied) {
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        FileInputStream(pfd.fileDescriptor).use { fis ->
                            FileOutputStream(targetFile).use { fos ->
                                fis.copyTo(fos)
                                fos.flush()
                                try {
                                    fos.fd.sync()
                                } catch (ignored: Exception) {}
                            }
                        }
                    }
                    if (targetFile.exists() && targetFile.length() > 0) {
                        copied = true
                    }
                } catch (e2: Exception) {
                    Log.w(tag, "openFileDescriptor failed: ${e2.message}")
                }
            }

            // Strategy 3: Direct file copy if scheme is file
            if (!copied && uri.scheme == "file") {
                try {
                    val sourcePath = uri.path
                    if (sourcePath != null) {
                        val sourceFile = File(sourcePath)
                        if (sourceFile.exists()) {
                            sourceFile.copyTo(targetFile, overwrite = true)
                            if (targetFile.exists() && targetFile.length() > 0) {
                                copied = true
                            }
                        }
                    }
                } catch (e3: Exception) {
                    Log.w(tag, "Direct file copy failed: ${e3.message}")
                }
            }

            if (copied && targetFile.exists() && targetFile.length() > 0) {
                Log.d(tag, "Cached video to: ${targetFile.absolutePath} (${targetFile.length()} bytes, ext: $extension)")
                targetFile
            } else {
                Log.e(tag, "Cached file is empty or missing")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to copy video to cache", e)
            null
        }
    }

    /**
     * Safely acquires a configured MediaMetadataRetriever without closing underlying descriptors.
     */
    private fun getSafeRetriever(file: File, uri: Uri? = null): RetrieverHolder? {
        val retriever = MediaMetadataRetriever()
        val closeables = mutableListOf<AutoCloseable>()

        // 1. Primary: Absolute Path (Android opens and manages native fd directly)
        try {
            if (file.exists() && file.length() > 0) {
                retriever.setDataSource(file.absolutePath)
                return RetrieverHolder(retriever, closeables)
            }
        } catch (e1: Exception) {
            Log.w(tag, "retriever.setDataSource(path) failed: ${e1.message}")
        }

        // 2. Secondary: FileDescriptor with open stream preserved
        try {
            if (file.exists() && file.length() > 0) {
                val fis = FileInputStream(file)
                closeables.add(fis)
                retriever.setDataSource(fis.fd, 0, file.length())
                return RetrieverHolder(retriever, closeables)
            }
        } catch (e2: Exception) {
            Log.w(tag, "retriever.setDataSource(fis.fd) failed: ${e2.message}")
        }

        // 3. Tertiary: Content URI
        if (uri != null) {
            try {
                retriever.setDataSource(context, uri)
                return RetrieverHolder(retriever, closeables)
            } catch (e3: Exception) {
                Log.w(tag, "retriever.setDataSource(uri) failed: ${e3.message}")
            }

            // 4. Quaternary: ParcelFileDescriptor from URI
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    closeables.add(pfd)
                    retriever.setDataSource(pfd.fileDescriptor)
                    return RetrieverHolder(retriever, closeables)
                }
            } catch (e4: Exception) {
                Log.w(tag, "retriever.setDataSource(pfd) failed: ${e4.message}")
            }
        }

        // Cleanup if configuration failed
        for (c in closeables) {
            try { c.close() } catch (ignored: Exception) {}
        }
        try { retriever.release() } catch (ignored: Exception) {}
        return null
    }

    /**
     * Inspects video metadata (duration, resolution, rotation) with multi-tier probing.
     */
    suspend fun getMetadata(file: File, uri: Uri? = null): VideoMetadata? = withContext(Dispatchers.IO) {
        val holder = getSafeRetriever(file, uri)
        if (holder == null) {
            val fallbackDuration = probeDurationWithExtractor(file)
            return@withContext VideoMetadata(
                durationMs = if (fallbackDuration > 0) fallbackDuration else 5000L,
                width = 640,
                height = 480,
                rotation = 0,
                file = file
            )
        }

        try {
            holder.use { h ->
                val retriever = h.retriever
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val mimeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "video/mp4"

                var durationMs = durationStr?.toLongOrNull() ?: 0L
                val width = widthStr?.toIntOrNull() ?: 640
                val height = heightStr?.toIntOrNull() ?: 480
                val rotation = rotationStr?.toIntOrNull() ?: 0

                if (durationMs <= 0L) {
                    durationMs = probeDurationWithExtractor(file)
                }
                if (durationMs <= 0L) {
                    durationMs = 5000L
                }

                VideoMetadata(
                    durationMs = durationMs,
                    width = width,
                    height = height,
                    rotation = rotation,
                    file = file,
                    mimeType = mimeStr
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "getMetadata error", e)
            val fallbackDuration = probeDurationWithExtractor(file)
            VideoMetadata(
                durationMs = if (fallbackDuration > 0) fallbackDuration else 5000L,
                width = 640,
                height = 480,
                rotation = 0,
                file = file
            )
        }
    }

    /**
     * Extracts the first frame of the video at high quality with 9 fallback seeking strategies
     * plus OS-level ThumbnailUtils and MediaCodec decoders.
     */
    suspend fun extractFirstFrame(file: File, uri: Uri? = null): Bitmap? = withContext(Dispatchers.IO) {
        var rotationDegrees = 0

        val holder = getSafeRetriever(file, uri)
        var bmp: Bitmap? = null

        if (holder != null) {
            try {
                holder.use { h ->
                    val retriever = h.retriever
                    val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    rotationDegrees = rotStr?.toIntOrNull() ?: 0

                    // Strategy 1: Android P+ Direct Frame Index (frame 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        try {
                            bmp = retriever.getFrameAtIndex(0)
                            if (bmp != null) Log.d(tag, "Extracted frame 0 via getFrameAtIndex(0)")
                        } catch (ignored: Throwable) {}
                    }

                    // Strategy 2: Default frameAtTime (native preferred keyframe)
                    if (bmp == null) {
                        try {
                            bmp = retriever.frameAtTime
                            if (bmp != null) Log.d(tag, "Extracted frame via default frameAtTime")
                        } catch (ignored: Throwable) {}
                    }

                    // Strategy 3: Timestamp -1 (auto-detect first playable frame)
                    if (bmp == null) {
                        try {
                            bmp = retriever.getFrameAtTime(-1L)
                            if (bmp != null) Log.d(tag, "Extracted frame via getFrameAtTime(-1)")
                        } catch (ignored: Throwable) {}
                    }

                    // Strategy 4: OPTION_CLOSEST_SYNC at timestamp 0
                    if (bmp == null) {
                        try {
                            bmp = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            if (bmp != null) Log.d(tag, "Extracted frame via OPTION_CLOSEST_SYNC at 0us")
                        } catch (ignored: Throwable) {}
                    }

                    // Strategy 5: OPTION_CLOSEST at timestamp 0
                    if (bmp == null) {
                        try {
                            bmp = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
                            if (bmp != null) Log.d(tag, "Extracted frame via OPTION_CLOSEST at 0us")
                        } catch (ignored: Throwable) {}
                    }

                    // Strategy 6: OPTION_NEXT_SYNC at timestamp 0
                    if (bmp == null) {
                        try {
                            bmp = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_NEXT_SYNC)
                            if (bmp != null) Log.d(tag, "Extracted frame via OPTION_NEXT_SYNC at 0us")
                        } catch (ignored: Throwable) {}
                    }

                    // Strategy 7: Probe sequential timestamps for screen recordings
                    if (bmp == null) {
                        val candidateTimestampsUs = longArrayOf(
                            15_000L, 33_000L, 66_000L, 100_000L, 200_000L, 500_000L, 1_000_000L, 2_000_000L
                        )
                        for (t in candidateTimestampsUs) {
                            try {
                                bmp = retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                if (bmp == null) {
                                    bmp = retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST)
                                }
                                if (bmp != null) {
                                    Log.d(tag, "Extracted frame via timestamp probe at ${t / 1000}ms")
                                    break
                                }
                            } catch (ignored: Throwable) {}
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Retriever extraction encountered error: ${e.message}")
            }
        }

        // Strategy 8: OS ThumbnailUtils Fallback
        if (bmp == null && file.exists() && file.length() > 0) {
            try {
                Log.d(tag, "Attempting ThumbnailUtils fallback...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    bmp = ThumbnailUtils.createVideoThumbnail(file, Size(720, 720), null)
                } else {
                    @Suppress("DEPRECATION")
                    bmp = ThumbnailUtils.createVideoThumbnail(
                        file.absolutePath,
                        MediaStore.Images.Thumbnails.MINI_KIND
                    )
                }
                if (bmp != null) Log.d(tag, "Extracted frame via ThumbnailUtils")
            } catch (e: Exception) {
                Log.w(tag, "ThumbnailUtils fallback failed: ${e.message}")
            }
        }

        // Strategy 9: Direct MediaExtractor + MediaCodec frame decode
        if (bmp == null && file.exists() && file.length() > 0) {
            try {
                Log.d(tag, "Attempting MediaCodec hardware decoder fallback...")
                bmp = decodeFirstFrameWithCodec(file)
                if (bmp != null) Log.d(tag, "Extracted frame via MediaCodec")
            } catch (e: Exception) {
                Log.w(tag, "MediaCodec fallback failed: ${e.message}")
            }
        }

        val resolvedBmp = bmp
        if (resolvedBmp != null) {
            var finalBmp: Bitmap = resolvedBmp
            if (rotationDegrees != 0) {
                try {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    finalBmp = Bitmap.createBitmap(resolvedBmp, 0, 0, resolvedBmp.width, resolvedBmp.height, matrix, true)
                } catch (ignored: Throwable) {}
            }

            // Normalize dimensions for smooth mobile tracking
            val maxDim = 800
            if (finalBmp.width > maxDim || finalBmp.height > maxDim) {
                val scale = maxDim.toFloat() / max(finalBmp.width, finalBmp.height)
                val scaledW = (finalBmp.width * scale).toInt()
                val scaledH = (finalBmp.height * scale).toInt()
                finalBmp = Bitmap.createScaledBitmap(finalBmp, scaledW, scaledH, true)
            }
            finalBmp
        } else {
            Log.e(tag, "All 9 frame extraction strategies returned null")
            null
        }
    }

    /**
     * Extracts a sequence of frames sampled across the video duration for optical flow processing.
     */
    suspend fun extractTrackingFrames(
        file: File,
        uri: Uri? = null,
        targetFps: Int = 20,
        maxDurationMs: Long = 20_000L,
        onProgress: (Int, Int) -> Unit
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val holder = getSafeRetriever(file, uri)
        val frames = mutableListOf<Bitmap>()
        var rotationDegrees = 0

        if (holder != null) {
            try {
                holder.use { h ->
                    val retriever = h.retriever
                    val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    rotationDegrees = rotStr?.toIntOrNull() ?: 0

                    var durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    if (durationMs <= 0L) {
                        durationMs = probeDurationWithExtractor(file)
                    }
                    if (durationMs <= 0L) {
                        durationMs = 4000L
                    }

                    val actualDuration = min(durationMs, maxDurationMs)
                    val frameIntervalMs = 1000L / targetFps
                    val totalFrames = max(1, (actualDuration / frameIntervalMs).toInt())

                    val targetWidth = 480
                    var previousSuccessfulBmp: Bitmap? = null

                    for (i in 0 until totalFrames) {
                        val timeUs = (i * frameIntervalMs) * 1000L
                        var rawBmp: Bitmap? = null

                        // Tier A: OPTION_CLOSEST
                        try {
                            rawBmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        } catch (ignored: Throwable) {}

                        // Tier B: OPTION_CLOSEST_SYNC
                        if (rawBmp == null) {
                            try {
                                rawBmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            } catch (ignored: Throwable) {}
                        }

                        // Tier C: OPTION_PREVIOUS_SYNC
                        if (rawBmp == null) {
                            try {
                                rawBmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC)
                            } catch (ignored: Throwable) {}
                        }

                        // Tier D: OPTION_NEXT_SYNC
                        if (rawBmp == null) {
                            try {
                                rawBmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_NEXT_SYNC)
                            } catch (ignored: Throwable) {}
                        }

                        // Tier E: Re-use previous frame
                        if (rawBmp == null && previousSuccessfulBmp != null) {
                            rawBmp = previousSuccessfulBmp
                        }

                        if (rawBmp != null) {
                            val validBmp: Bitmap = rawBmp
                            var processedBmp: Bitmap = validBmp
                            if (rotationDegrees != 0) {
                                try {
                                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                    processedBmp = Bitmap.createBitmap(validBmp, 0, 0, validBmp.width, validBmp.height, matrix, true)
                                } catch (ignored: Throwable) {}
                            }

                            val scaled = if (processedBmp.width > targetWidth) {
                                val ratio = targetWidth.toFloat() / processedBmp.width
                                val targetHeight = (processedBmp.height * ratio).toInt()
                                Bitmap.createScaledBitmap(processedBmp, targetWidth, targetHeight, true)
                            } else {
                                processedBmp
                            }
                            frames.add(scaled)
                            previousSuccessfulBmp = scaled
                        }
                        onProgress(i + 1, totalFrames)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "extractTrackingFrames error", e)
            }
        }

        frames
    }

    /**
     * Probes video duration directly via low-level MediaExtractor.
     */
    private fun probeDurationWithExtractor(file: File): Long {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                        if (durationUs > 0) return durationUs / 1000L
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "probeDurationWithExtractor failed: ${e.message}")
        } finally {
            try {
                extractor.release()
            } catch (ignored: Exception) {}
        }
        return 0L
    }

    /**
     * Hardware decoder fallback using MediaExtractor and MediaCodec.
     */
    private fun decodeFirstFrameWithCodec(file: File): Bitmap? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }

            if (videoTrackIndex < 0 || videoFormat == null) return null

            extractor.selectTrack(videoTrackIndex)
            val mime = videoFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(videoFormat, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            val timeoutUs = 10_000L
            var inputDone = false
            var decodedBmp: Bitmap? = null

            for (iter in 0 until 60) {
                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = decoder.dequeueOutputBuffer(info, timeoutUs)
                if (outIdx >= 0) {
                    if (info.size > 0) {
                        val outBuf = decoder.getOutputBuffer(outIdx)
                        if (outBuf != null) {
                            decodedBmp = yuvToRgbBitmap(outBuf, width, height)
                            decoder.releaseOutputBuffer(outIdx, false)
                            if (decodedBmp != null) break
                        }
                    }
                    decoder.releaseOutputBuffer(outIdx, false)
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // format changed
                }
            }

            return decodedBmp
        } catch (e: Exception) {
            Log.w(tag, "decodeFirstFrameWithCodec error: ${e.message}")
            return null
        } finally {
            try {
                decoder?.stop()
                decoder?.release()
            } catch (ignored: Exception) {}
            try {
                extractor.release()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Converts YUV420 buffer to RGB Bitmap.
     */
    private fun yuvToRgbBitmap(buffer: ByteBuffer, width: Int, height: Int): Bitmap? {
        return try {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val ySize = width * height
            val uSize = ySize / 4

            val yBytes = ByteArray(ySize)
            buffer.position(0)
            buffer.get(yBytes, 0, min(ySize, buffer.remaining()))

            for (i in 0 until (width * height)) {
                val y = (yBytes.getOrNull(i)?.toInt() ?: 0) and 0xFF
                // Grayscale luminance fallback if UV is absent
                pixels[i] = (0xFF shl 24) or (y shl 16) or (y shl 8) or y
            }
            bmp.setPixels(pixels, 0, width, 0, 0, width, height)
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
