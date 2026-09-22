package com.chronocube.app.video

object FrameTimeline {
    fun sampleTimesUs(durationMs: Long, requestedCount: Int): LongArray {
        require(durationMs > 0) { "Video duration must be positive" }
        require(requestedCount >= 2) { "At least two frames are required" }

        val lastUs = (durationMs * 1_000L - 1_000L).coerceAtLeast(0L)
        return LongArray(requestedCount) { index ->
            lastUs * index / (requestedCount - 1)
        }
    }
}
