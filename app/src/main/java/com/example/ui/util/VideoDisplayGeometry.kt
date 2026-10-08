package com.example.ui.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect

/**
 * Utility to accurately map coordinates between letterboxed/pillarboxed video frames
 * (rendered via ContentScale.Fit) and screen touch/Canvas coordinates.
 */
object VideoDisplayGeometry {

    /**
     * Calculates the exact bounding rectangle of an image rendered with ContentScale.Fit
     * inside a container of size [containerSize].
     */
    fun calculateFitRect(containerSize: Size, imageW: Int, imageH: Int): Rect {
        if (containerSize.width <= 0f || containerSize.height <= 0f || imageW <= 0 || imageH <= 0) {
            return Rect(0f, 0f, containerSize.width, containerSize.height)
        }
        val containerRatio = containerSize.width / containerSize.height
        val imageRatio = imageW.toFloat() / imageH.toFloat()

        val (renderW, renderH) = if (containerRatio > imageRatio) {
            Pair(containerSize.height * imageRatio, containerSize.height)
        } else {
            Pair(containerSize.width, containerSize.width / imageRatio)
        }
        val offsetX = (containerSize.width - renderW) / 2f
        val offsetY = (containerSize.height - renderH) / 2f
        return Rect(offsetX, offsetY, offsetX + renderW, offsetY + renderH)
    }

    /**
     * Maps normalized point (0..1) on the video frame to screen pixel offset.
     */
    fun toScreenOffset(point: NormalizedPoint, fitRect: Rect): Offset {
        return Offset(
            fitRect.left + point.x * fitRect.width,
            fitRect.top + point.y * fitRect.height
        )
    }

    /**
     * Maps normalized rect (0..1) on the video frame to screen pixel rect.
     */
    fun toScreenRect(rect: NormalizedRect, fitRect: Rect): Rect {
        val left = fitRect.left + rect.left * fitRect.width
        val top = fitRect.top + rect.top * fitRect.height
        val width = rect.width * fitRect.width
        val height = rect.height * fitRect.height
        return Rect(left, top, left + width, top + height)
    }

    /**
     * Maps screen touch coordinate to normalized video frame point (0..1).
     */
    fun toNormalizedPoint(screenOffset: Offset, fitRect: Rect): NormalizedPoint {
        if (fitRect.width <= 0f || fitRect.height <= 0f) return NormalizedPoint(0.5f, 0.5f)
        val normX = ((screenOffset.x - fitRect.left) / fitRect.width).coerceIn(0f, 1f)
        val normY = ((screenOffset.y - fitRect.top) / fitRect.height).coerceIn(0f, 1f)
        return NormalizedPoint(normX, normY)
    }
}
