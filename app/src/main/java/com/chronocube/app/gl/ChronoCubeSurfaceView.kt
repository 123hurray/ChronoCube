package com.chronocube.app.gl

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.atan2
import kotlin.math.min

class ChronoCubeSurfaceView(context: Context) : GLSurfaceView(context) {
    private val cubeRenderer = ChronoCubeRenderer()
    private var boundGeneration = Long.MIN_VALUE
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastSingleX = 0f
    private var lastSingleY = 0f
    private var lastTwistDegrees: Float? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                cubeRenderer.zoomBy(detector.scaleFactor)
                requestRender()
                return true
            }

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onDoubleTap(event: MotionEvent): Boolean {
                cubeRenderer.resetCamera()
                requestRender()
                return true
            }
        },
    )

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(cubeRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        contentDescription = "单指轨道旋转、双指缩放和扭转方向的时空立方体"
    }

    fun bindFrames(
        generation: Long,
        frames: List<Bitmap>,
        aspectRatio: Float,
    ) {
        if (generation == boundGeneration) return
        boundGeneration = generation
        cubeRenderer.setFrames(generation, frames, aspectRatio)
        requestRender()
    }

    fun updateSettings(
        playhead: Float,
        cubeDepth: Float,
        backgroundBrightness: Float,
        backgroundTransparency: Float,
        highlightTransparency: Float,
        motionBoost: Float,
    ) {
        cubeRenderer.setRenderSettings(
            playhead = playhead,
            cubeDepth = cubeDepth,
            backgroundBrightness = backgroundBrightness,
            backgroundTransparency = backgroundTransparency,
            highlightTransparency = highlightTransparency,
            motionBoost = motionBoost,
        )
        requestRender()
    }

    fun resetCamera() {
        cubeRenderer.resetCamera()
        requestRender()
    }

    fun releaseRenderer() {
        queueEvent { cubeRenderer.release() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastSingleX = event.x
                lastSingleY = event.y
                lastTwistDegrees = null
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    lastTwistDegrees = twoFingerAngleDegrees(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val currentTwist = twoFingerAngleDegrees(event)
                    lastTwistDegrees?.let { previousTwist ->
                        val delta = normalizedAngleDelta(currentTwist - previousTwist)
                        // Android screen Y points down, so invert the angle for OpenGL's Z axis.
                        cubeRenderer.rollBy(-delta)
                        requestRender()
                    }
                    lastTwistDegrees = currentTwist
                } else if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                        .takeIf { it >= 0 }
                        ?: 0
                    val currentX = event.getX(pointerIndex)
                    val currentY = event.getY(pointerIndex)
                    val degreesPerPixel = 180f / min(width, height).coerceAtLeast(1)
                    cubeRenderer.orbitBy(
                        deltaYawDegrees = (currentX - lastSingleX) * degreesPerPixel,
                        deltaPitchDegrees = (currentY - lastSingleY) * degreesPerPixel,
                    )
                    requestRender()
                    lastSingleX = currentX
                    lastSingleY = currentY
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                lastTwistDegrees = null
                if (event.pointerCount - 1 == 1) {
                    val remainingIndex = if (event.actionIndex == 0) 1 else 0
                    activePointerId = event.getPointerId(remainingIndex)
                    lastSingleX = event.getX(remainingIndex)
                    lastSingleY = event.getY(remainingIndex)
                }
            }

            MotionEvent.ACTION_UP -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                lastTwistDegrees = null
                performClick()
            }

            MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                lastTwistDegrees = null
            }
        }
        return true
    }

    private fun twoFingerAngleDegrees(event: MotionEvent): Float {
        val deltaX = event.getX(1) - event.getX(0)
        val deltaY = event.getY(1) - event.getY(0)
        return Math.toDegrees(atan2(deltaY, deltaX).toDouble()).toFloat()
    }

    private fun normalizedAngleDelta(value: Float): Float {
        var result = value
        while (result > 180f) result -= 360f
        while (result < -180f) result += 360f
        return result
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
