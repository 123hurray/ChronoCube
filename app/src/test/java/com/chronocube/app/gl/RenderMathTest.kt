package com.chronocube.app.gl

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow

class RenderMathTest {
    @Test
    fun stackTransparencyDoesNotChangeWithSliceCount() {
        val targetTransparency = 0.90f
        val layerAlpha = RenderMath.backgroundLayerAlpha(targetTransparency, 256)
        val resultingTransparency = (1f - layerAlpha).pow(256)

        assertEquals(targetTransparency, resultingTransparency, 0.0001f)
    }

    @Test
    fun frameBlendInterpolatesBetweenAdjacentFrames() {
        val blend = RenderMath.frameBlend(playhead = 0.5f, frameCount = 4)

        assertEquals(1, blend.currentIndex)
        assertEquals(2, blend.nextIndex)
        assertEquals(0.5f, blend.fraction, 0.0001f)
    }

    @Test
    fun frameBlendStopsAtLastFrame() {
        val blend = RenderMath.frameBlend(playhead = 1f, frameCount = 256)

        assertEquals(255, blend.currentIndex)
        assertEquals(255, blend.nextIndex)
        assertEquals(0f, blend.fraction, 0f)
    }
}
