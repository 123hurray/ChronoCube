package com.chronocube.app.gl

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

class ChronoCubeSurfaceView(context: Context) : GLSurfaceView(context) {
    private val cubeRenderer = ChronoCubeRenderer()
    private var boundGeneration = Long.MIN_VALUE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                cubeRenderer.zoomBy(detector.scaleFactor)
                return true
            }
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onDoubleTap(event: MotionEvent): Boolean {
                cubeRenderer.resetCamera()
                return true
            }
        },
    )

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(cubeRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        contentDescription = "可拖动旋转、双指缩放的时空立方体"
    }

    fun bindFrames(
        generation: Long,
        frames: List<Bitmap>,
        aspectRatio: Float,
    ) {
        if (generation == boundGeneration) return
        boundGeneration = generation
        cubeRenderer.setFrames(generation, frames, aspectRatio)
    }

    fun updateSettings(
        playhead: Float,
        cubeDepth: Float,
        sliceOpacity: Float,
        motionBoost: Float,
    ) {
        cubeRenderer.setRenderSettings(
            playhead = playhead,
            cubeDepth = cubeDepth,
            sliceOpacity = sliceOpacity,
            motionBoost = motionBoost,
        )
    }

    fun resetCamera() {
        cubeRenderer.resetCamera()
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
                lastTouchX = event.x
                lastTouchY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val deltaX = event.x - lastTouchX
                    val deltaY = event.y - lastTouchY
                    cubeRenderer.rotateBy(deltaX, deltaY)
                }
                lastTouchX = event.x
                lastTouchY = event.y
            }

            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
