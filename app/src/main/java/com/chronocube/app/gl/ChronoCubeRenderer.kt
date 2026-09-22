package com.chronocube.app.gl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.roundToInt

internal class ChronoCubeRenderer : android.opengl.GLSurfaceView.Renderer {
    @Volatile
    private var sourceFrames: List<Bitmap> = emptyList()

    @Volatile
    private var sourceGeneration: Long = -1L

    @Volatile
    private var videoAspectRatio: Float = 16f / 9f

    @Volatile
    private var playhead: Float = 0f

    @Volatile
    private var cubeDepth: Float = 1.45f

    @Volatile
    private var sliceOpacity: Float = 0.13f

    @Volatile
    private var motionBoost: Float = 0.72f

    @Volatile
    private var yawDegrees: Float = -28f

    @Volatile
    private var pitchDegrees: Float = 18f

    @Volatile
    private var zoom: Float = 1f

    private var uploadedGeneration = Long.MIN_VALUE
    private var textures = IntArray(0)
    private var textureProgram = 0
    private var lineProgram = 0
    private var surfaceWidth = 1
    private var surfaceHeight = 1

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val globalModel = FloatArray(16)
    private val localModel = FloatArray(16)
    private val combinedModel = FloatArray(16)
    private val modelView = FloatArray(16)
    private val mvp = FloatArray(16)

    private var planeBuffer: FloatBuffer = floatBuffer(FloatArray(20))
    private var planeAspect = -1f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        textureProgram = GlProgram.create(TEXTURE_VERTEX_SHADER, TEXTURE_FRAGMENT_SHADER)
        lineProgram = GlProgram.create(LINE_VERTEX_SHADER, LINE_FRAGMENT_SHADER)
        uploadedGeneration = Long.MIN_VALUE

        GLES30.glClearColor(0.012f, 0.016f, 0.025f, 1f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        Matrix.perspectiveM(
            projection,
            0,
            42f,
            surfaceWidth.toFloat() / surfaceHeight,
            0.1f,
            100f,
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        uploadFramesIfNeeded()
        if (textures.isEmpty()) return

        updatePlaneGeometry()
        updateViewMatrix()
        drawSlices()
        drawHighlightOutline()
        drawCubeOutline()
    }

    fun setFrames(generation: Long, frames: List<Bitmap>, aspectRatio: Float) {
        sourceFrames = frames
        sourceGeneration = generation
        videoAspectRatio = aspectRatio.coerceIn(0.2f, 5f)
    }

    fun setRenderSettings(
        playhead: Float,
        cubeDepth: Float,
        sliceOpacity: Float,
        motionBoost: Float,
    ) {
        this.playhead = playhead.coerceIn(0f, 1f)
        this.cubeDepth = cubeDepth.coerceIn(0.35f, 2.6f)
        this.sliceOpacity = sliceOpacity.coerceIn(0.04f, 0.35f)
        this.motionBoost = motionBoost.coerceIn(0f, 1f)
    }

    fun rotateBy(deltaX: Float, deltaY: Float) {
        yawDegrees = (yawDegrees + deltaX * 0.28f) % 360f
        pitchDegrees = (pitchDegrees + deltaY * 0.28f).coerceIn(-82f, 82f)
    }

    fun zoomBy(scaleFactor: Float) {
        zoom = (zoom * scaleFactor).coerceIn(0.55f, 2.4f)
    }

    fun resetCamera() {
        yawDegrees = -28f
        pitchDegrees = 18f
        zoom = 1f
    }

    fun release() {
        deleteTextures()
        if (textureProgram != 0) GLES30.glDeleteProgram(textureProgram)
        if (lineProgram != 0) GLES30.glDeleteProgram(lineProgram)
        textureProgram = 0
        lineProgram = 0
    }

    private fun uploadFramesIfNeeded() {
        if (uploadedGeneration == sourceGeneration) return
        deleteTextures()

        val frames = sourceFrames
        if (frames.isEmpty()) {
            uploadedGeneration = sourceGeneration
            return
        }

        val newTextures = IntArray(frames.size)
        GLES30.glGenTextures(newTextures.size, newTextures, 0)
        try {
            frames.forEachIndexed { index, bitmap ->
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, newTextures[index])
                GLES30.glTexParameteri(
                    GLES30.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_MIN_FILTER,
                    GLES30.GL_LINEAR,
                )
                GLES30.glTexParameteri(
                    GLES30.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_MAG_FILTER,
                    GLES30.GL_LINEAR,
                )
                GLES30.glTexParameteri(
                    GLES30.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_WRAP_S,
                    GLES30.GL_CLAMP_TO_EDGE,
                )
                GLES30.glTexParameteri(
                    GLES30.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_WRAP_T,
                    GLES30.GL_CLAMP_TO_EDGE,
                )
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            }
            textures = newTextures
            uploadedGeneration = sourceGeneration
        } catch (error: Throwable) {
            GLES30.glDeleteTextures(newTextures.size, newTextures, 0)
            textures = IntArray(0)
            Log.e(TAG, "Unable to upload video frames", error)
        } finally {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        }
    }

    private fun deleteTextures() {
        if (textures.isNotEmpty()) {
            GLES30.glDeleteTextures(textures.size, textures, 0)
            textures = IntArray(0)
        }
    }

    private fun updatePlaneGeometry() {
        val aspect = videoAspectRatio
        if (aspect == planeAspect) return
        planeAspect = aspect

        val halfWidth: Float
        val halfHeight: Float
        if (aspect >= 1f) {
            halfWidth = 1.05f
            halfHeight = 1.05f / aspect
        } else {
            halfWidth = 1.05f * aspect
            halfHeight = 1.05f
        }

        planeBuffer = floatBuffer(
            floatArrayOf(
                -halfWidth, -halfHeight, 0f, 0f, 1f,
                halfWidth, -halfHeight, 0f, 1f, 1f,
                -halfWidth, halfHeight, 0f, 0f, 0f,
                halfWidth, halfHeight, 0f, 1f, 0f,
            ),
        )
    }

    private fun updateViewMatrix() {
        Matrix.setLookAtM(
            view,
            0,
            0f,
            0f,
            4.6f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f,
        )
        Matrix.setIdentityM(globalModel, 0)
        Matrix.rotateM(globalModel, 0, pitchDegrees, 1f, 0f, 0f)
        Matrix.rotateM(globalModel, 0, yawDegrees, 0f, 1f, 0f)
        Matrix.scaleM(globalModel, 0, zoom, zoom, zoom)
    }

    private fun drawSlices() {
        GLES30.glUseProgram(textureProgram)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)

        val positionLocation = GLES30.glGetAttribLocation(textureProgram, "aPosition")
        val textureCoordinateLocation =
            GLES30.glGetAttribLocation(textureProgram, "aTextureCoordinate")
        val mvpLocation = GLES30.glGetUniformLocation(textureProgram, "uMvp")
        val opacityLocation = GLES30.glGetUniformLocation(textureProgram, "uOpacity")
        val motionBoostLocation = GLES30.glGetUniformLocation(textureProgram, "uMotionBoost")
        val highlightLocation = GLES30.glGetUniformLocation(textureProgram, "uHighlight")
        val currentTextureLocation = GLES30.glGetUniformLocation(textureProgram, "uFrame")
        val referenceTextureLocation = GLES30.glGetUniformLocation(textureProgram, "uReference")

        planeBuffer.position(0)
        GLES30.glEnableVertexAttribArray(positionLocation)
        GLES30.glVertexAttribPointer(
            positionLocation,
            3,
            GLES30.GL_FLOAT,
            false,
            5 * Float.SIZE_BYTES,
            planeBuffer,
        )
        planeBuffer.position(3)
        GLES30.glEnableVertexAttribArray(textureCoordinateLocation)
        GLES30.glVertexAttribPointer(
            textureCoordinateLocation,
            2,
            GLES30.GL_FLOAT,
            false,
            5 * Float.SIZE_BYTES,
            planeBuffer,
        )

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures.first())
        GLES30.glUniform1i(referenceTextureLocation, 1)
        GLES30.glUniform1f(opacityLocation, sliceOpacity)
        GLES30.glUniform1f(motionBoostLocation, motionBoost)

        val zPositions = FloatArray(textures.size) { index -> sliceZ(index, textures.size) }
        val facing = (
            cos(Math.toRadians(yawDegrees.toDouble())) *
                cos(Math.toRadians(pitchDegrees.toDouble()))
            ).toFloat()
        val drawOrder = textures.indices.sortedBy { zPositions[it] * facing }
        val selectedIndex = selectedFrameIndex(textures.size)

        drawOrder.forEach { index ->
            Matrix.setIdentityM(localModel, 0)
            Matrix.translateM(localModel, 0, 0f, 0f, zPositions[index])
            buildMvp(localModel)
            GLES30.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0)
            GLES30.glUniform1f(highlightLocation, if (index == selectedIndex) 1f else 0f)

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[index])
            GLES30.glUniform1i(currentTextureLocation, 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }

        GLES30.glDisableVertexAttribArray(positionLocation)
        GLES30.glDisableVertexAttribArray(textureCoordinateLocation)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun drawHighlightOutline() {
        if (textures.isEmpty()) return

        val aspect = videoAspectRatio
        val halfWidth = if (aspect >= 1f) 1.065f else 1.065f * aspect
        val halfHeight = if (aspect >= 1f) 1.065f / aspect else 1.065f
        val highlightZ = sliceZ(selectedFrameIndex(textures.size), textures.size)
        val vertices = floatBuffer(
            floatArrayOf(
                -halfWidth, -halfHeight, 0f, halfWidth, -halfHeight, 0f,
                halfWidth, -halfHeight, 0f, halfWidth, halfHeight, 0f,
                halfWidth, halfHeight, 0f, -halfWidth, halfHeight, 0f,
                -halfWidth, halfHeight, 0f, -halfWidth, -halfHeight, 0f,
            ),
        )

        GLES30.glUseProgram(lineProgram)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glLineWidth(3f)
        Matrix.setIdentityM(localModel, 0)
        Matrix.translateM(localModel, 0, 0f, 0f, highlightZ)
        buildMvp(localModel)

        val positionLocation = GLES30.glGetAttribLocation(lineProgram, "aPosition")
        val mvpLocation = GLES30.glGetUniformLocation(lineProgram, "uMvp")
        val colorLocation = GLES30.glGetUniformLocation(lineProgram, "uColor")
        GLES30.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0)
        GLES30.glUniform4f(colorLocation, 0.37f, 0.96f, 0.78f, 0.96f)
        GLES30.glEnableVertexAttribArray(positionLocation)
        GLES30.glVertexAttribPointer(
            positionLocation,
            3,
            GLES30.GL_FLOAT,
            false,
            3 * Float.SIZE_BYTES,
            vertices,
        )
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, 8)
        GLES30.glDisableVertexAttribArray(positionLocation)
    }

    private fun drawCubeOutline() {
        val aspect = videoAspectRatio
        val halfWidth = if (aspect >= 1f) 1.08f else 1.08f * aspect
        val halfHeight = if (aspect >= 1f) 1.08f / aspect else 1.08f
        val halfDepth = cubeDepth / 2f
        val lineVertices = floatBuffer(
            floatArrayOf(
                -halfWidth, -halfHeight, -halfDepth, halfWidth, -halfHeight, -halfDepth,
                halfWidth, -halfHeight, -halfDepth, halfWidth, halfHeight, -halfDepth,
                halfWidth, halfHeight, -halfDepth, -halfWidth, halfHeight, -halfDepth,
                -halfWidth, halfHeight, -halfDepth, -halfWidth, -halfHeight, -halfDepth,
                -halfWidth, -halfHeight, halfDepth, halfWidth, -halfHeight, halfDepth,
                halfWidth, -halfHeight, halfDepth, halfWidth, halfHeight, halfDepth,
                halfWidth, halfHeight, halfDepth, -halfWidth, halfHeight, halfDepth,
                -halfWidth, halfHeight, halfDepth, -halfWidth, -halfHeight, halfDepth,
                -halfWidth, -halfHeight, -halfDepth, -halfWidth, -halfHeight, halfDepth,
                halfWidth, -halfHeight, -halfDepth, halfWidth, -halfHeight, halfDepth,
                halfWidth, halfHeight, -halfDepth, halfWidth, halfHeight, halfDepth,
                -halfWidth, halfHeight, -halfDepth, -halfWidth, halfHeight, halfDepth,
            ),
        )

        GLES30.glUseProgram(lineProgram)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glLineWidth(1.5f)
        Matrix.setIdentityM(localModel, 0)
        buildMvp(localModel)

        val positionLocation = GLES30.glGetAttribLocation(lineProgram, "aPosition")
        val mvpLocation = GLES30.glGetUniformLocation(lineProgram, "uMvp")
        val colorLocation = GLES30.glGetUniformLocation(lineProgram, "uColor")
        GLES30.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0)
        GLES30.glUniform4f(colorLocation, 0.48f, 0.72f, 1f, 0.72f)
        GLES30.glEnableVertexAttribArray(positionLocation)
        GLES30.glVertexAttribPointer(
            positionLocation,
            3,
            GLES30.GL_FLOAT,
            false,
            3 * Float.SIZE_BYTES,
            lineVertices,
        )
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, 24)
        GLES30.glDisableVertexAttribArray(positionLocation)
    }

    private fun buildMvp(local: FloatArray) {
        Matrix.multiplyMM(combinedModel, 0, globalModel, 0, local, 0)
        Matrix.multiplyMM(modelView, 0, view, 0, combinedModel, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0)
    }

    private fun sliceZ(index: Int, count: Int): Float {
        if (count <= 1) return 0f
        val frameTime = index.toFloat() / (count - 1)
        return cubeDepth * (0.5f - frameTime)
    }

    private fun selectedFrameIndex(count: Int): Int {
        if (count <= 1) return 0
        return (playhead * (count - 1)).roundToInt().coerceIn(0, count - 1)
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }

    private companion object {
        const val TAG = "ChronoCubeRenderer"

        const val TEXTURE_VERTEX_SHADER = """
            #version 300 es
            uniform mat4 uMvp;
            in vec3 aPosition;
            in vec2 aTextureCoordinate;
            out vec2 vTextureCoordinate;

            void main() {
                vTextureCoordinate = aTextureCoordinate;
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """

        const val TEXTURE_FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            uniform sampler2D uFrame;
            uniform sampler2D uReference;
            uniform float uOpacity;
            uniform float uMotionBoost;
            uniform float uHighlight;
            in vec2 vTextureCoordinate;
            out vec4 outputColor;

            void main() {
                vec3 frameColor = texture(uFrame, vTextureCoordinate).rgb;
                vec3 referenceColor = texture(uReference, vTextureCoordinate).rgb;
                float difference = length(frameColor - referenceColor);
                float moving = smoothstep(0.045, 0.30, difference);
                float motionAlpha = mix(0.055, 1.0, moving);
                float alphaMask = mix(1.0, motionAlpha, uMotionBoost);
                float highlightGain = mix(1.0, 3.1, uHighlight);
                float finalAlpha = clamp(uOpacity * alphaMask * highlightGain, 0.0, 0.84);
                vec3 accent = vec3(0.37, 0.96, 0.78);
                vec3 liftedColor = mix(frameColor, accent, uHighlight * 0.16);
                outputColor = vec4(liftedColor, finalAlpha);
            }
        """

        const val LINE_VERTEX_SHADER = """
            #version 300 es
            uniform mat4 uMvp;
            in vec3 aPosition;

            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """

        const val LINE_FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            uniform vec4 uColor;
            out vec4 outputColor;

            void main() {
                outputColor = uColor;
            }
        """
    }
}
