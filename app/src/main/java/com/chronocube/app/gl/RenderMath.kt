package com.chronocube.app.gl

import kotlin.math.pow

internal data class FrameBlend(
    val currentIndex: Int,
    val nextIndex: Int,
    val fraction: Float,
)

internal object RenderMath {
    /**
     * Converts a transparency target for the complete stack into a per-slice alpha.
     * This keeps the perceived opacity stable when the number of slices changes.
     */
    fun backgroundLayerAlpha(stackTransparency: Float, sliceCount: Int): Float {
        val transparency = stackTransparency.coerceIn(0f, 1f)
        val count = sliceCount.coerceAtLeast(1)
        return 1f - transparency.pow(1f / count)
    }

    fun frameBlend(playhead: Float, frameCount: Int): FrameBlend {
        require(frameCount > 0) { "At least one frame is required" }
        val position = playhead.coerceIn(0f, 1f) * (frameCount - 1)
        val currentIndex = position.toInt().coerceIn(0, frameCount - 1)
        return FrameBlend(
            currentIndex = currentIndex,
            nextIndex = (currentIndex + 1).coerceAtMost(frameCount - 1),
            fraction = position - currentIndex,
        )
    }
}
