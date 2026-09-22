package com.chronocube.app.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ExtractedVideo(
    val frames: List<Bitmap>,
    val durationMs: Long,
    val aspectRatio: Float,
)

class VideoFrameExtractor(
    private val maxTextureSide: Int = 512,
    private val textureMemoryBudgetBytes: Long = 64L * 1024L * 1024L,
) {
    suspend fun extract(
        context: Context,
        uri: Uri,
        requestedFrames: Int,
        onProgress: (Float) -> Unit,
    ): ExtractedVideo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?: error("无法读取视频时长")

            val rawWidth = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: error("无法读取视频宽度")
            val rawHeight = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: error("无法读取视频高度")
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toFloatOrNull()
                ?: 0f

            val count = requestedFrames.coerceIn(8, 256)
            val times = FrameTimeline.sampleTimesUs(durationMs, count)
            val budgetedSide = sqrt(
                textureMemoryBudgetBytes.toDouble() / count / BYTES_PER_PIXEL,
            ).toInt().coerceAtLeast(MIN_TEXTURE_SIDE)
            val targetMaxSide = min(maxTextureSide, budgetedSide)
            val scale = (targetMaxSide.toFloat() / max(rawWidth, rawHeight))
                .coerceAtMost(1f)
            val targetWidth = (rawWidth * scale).roundToInt().coerceAtLeast(2)
            val targetHeight = (rawHeight * scale).roundToInt().coerceAtLeast(2)
            val frames = ArrayList<Bitmap>(count)

            times.forEachIndexed { index, timeUs ->
                coroutineContext.ensureActive()
                val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        targetWidth,
                        targetHeight,
                    )
                } else {
                    retriever.getFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                    )?.let { source ->
                        val scaled = Bitmap.createScaledBitmap(
                            source,
                            targetWidth,
                            targetHeight,
                            true,
                        )
                        if (scaled !== source) source.recycle()
                        scaled
                    }
                }

                if (decoded != null) {
                    frames += rotateIfNeeded(decoded, rotation)
                }
                onProgress((index + 1f) / count)
            }

            check(frames.size >= 2) { "视频解码失败，请尝试标准 H.264/MP4 视频" }
            val first = frames.first()
            ExtractedVideo(
                frames = frames,
                durationMs = durationMs,
                aspectRatio = first.width.toFloat() / first.height.toFloat(),
            )
        } finally {
            retriever.release()
        }
    }

    private fun rotateIfNeeded(source: Bitmap, degrees: Float): Bitmap {
        val normalized = ((degrees % 360f) + 360f) % 360f
        if (normalized == 0f) return source

        val matrix = Matrix().apply { postRotate(normalized) }
        val rotated = Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            true,
        )
        if (rotated !== source) source.recycle()
        return rotated
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4
        const val MIN_TEXTURE_SIDE = 160
    }
}
