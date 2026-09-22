package com.chronocube.app.video

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameTimelineTest {
    @Test
    fun samplesWholeVideoWithoutRequestingPastEnd() {
        val samples = FrameTimeline.sampleTimesUs(durationMs = 1_000L, requestedCount = 5)

        assertArrayEquals(
            longArrayOf(0L, 249_750L, 499_500L, 749_250L, 999_000L),
            samples,
        )
    }

    @Test
    fun samplesAreMonotonic() {
        val samples = FrameTimeline.sampleTimesUs(durationMs = 12_345L, requestedCount = 48)

        assertEquals(48, samples.size)
        assertEquals(0L, samples.first())
        assertTrue(
            (1 until samples.size).all { index ->
                samples[index - 1] <= samples[index]
            },
        )
        assertTrue(samples.last() < 12_345_000L)
    }
}
