package com.chronocube.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chronocube.app.video.VideoFrameExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

data class ChronoCubeUiState(
    val sourceName: String? = null,
    val durationMs: Long = 0L,
    val frames: List<Bitmap> = emptyList(),
    val videoAspectRatio: Float = 16f / 9f,
    val frameGeneration: Long = 0L,
    val isLoading: Boolean = false,
    val loadingProgress: Float = 0f,
    val errorMessage: String? = null,
    val isPlaying: Boolean = false,
    val playhead: Float = 0f,
    val requestedSlices: Int = 48,
    val cubeDepth: Float = 1.45f,
    val backgroundBrightness: Float = 0.55f,
    val backgroundTransparency: Float = 0.90f,
    val highlightTransparency: Float = 0.12f,
    val motionBoost: Float = 0.72f,
) {
    val hasVideo: Boolean get() = frames.isNotEmpty()

    val timeLabel: String
        get() {
            if (durationMs <= 0L) return "00:00 / 00:00"
            val currentMs = (durationMs * playhead).roundToInt().toLong()
            return formatDuration(currentMs) + " / " + formatDuration(durationMs)
        }

    private fun formatDuration(valueMs: Long): String {
        val totalSeconds = valueMs / 1_000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

class ChronoCubeViewModel(application: Application) : AndroidViewModel(application) {
    private val extractor = VideoFrameExtractor()
    private val _uiState = MutableStateFlow(ChronoCubeUiState())
    val uiState: StateFlow<ChronoCubeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var requestGeneration = 0L
    private var currentUri: Uri? = null
    private var currentDisplayName: String? = null

    fun loadVideo(uri: Uri, displayName: String?) {
        currentUri = uri
        currentDisplayName = displayName
        loadJob?.cancel()
        val requestId = ++requestGeneration

        _uiState.update {
            it.copy(
                sourceName = displayName ?: "已选择的视频",
                durationMs = 0L,
                frames = emptyList(),
                frameGeneration = it.frameGeneration + 1L,
                isLoading = true,
                loadingProgress = 0f,
                errorMessage = null,
                isPlaying = false,
                playhead = 0f,
            )
        }

        loadJob = viewModelScope.launch {
            try {
                val requestedFrames = _uiState.value.requestedSlices
                val result = extractor.extract(
                    context = getApplication(),
                    uri = uri,
                    requestedFrames = requestedFrames,
                    onProgress = { progress ->
                        if (requestId == requestGeneration) {
                            _uiState.update { current ->
                                current.copy(loadingProgress = progress)
                            }
                        }
                    },
                )

                if (requestId == requestGeneration) {
                    _uiState.update {
                        it.copy(
                            durationMs = result.durationMs,
                            frames = result.frames,
                            videoAspectRatio = result.aspectRatio,
                            frameGeneration = it.frameGeneration + 1L,
                            isLoading = false,
                            loadingProgress = 1f,
                            errorMessage = null,
                            isPlaying = false,
                            playhead = 0f,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (requestId == requestGeneration) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingProgress = 0f,
                            errorMessage = error.message ?: "视频处理失败",
                        )
                    }
                }
            }
        }
    }

    fun regenerateVideo() {
        currentUri?.let { uri ->
            loadVideo(uri, currentDisplayName)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun togglePlayback() {
        _uiState.update {
            if (!it.hasVideo) it else it.copy(isPlaying = !it.isPlaying)
        }
    }

    fun setPlayhead(value: Float) {
        _uiState.update {
            it.copy(playhead = value.coerceIn(0f, 1f), isPlaying = false)
        }
    }

    fun advancePlayback(deltaSeconds: Float) {
        _uiState.update { current ->
            if (!current.isPlaying || !current.hasVideo) {
                current
            } else {
                val loopSeconds = 6f
                current.copy(playhead = (current.playhead + deltaSeconds / loopSeconds) % 1f)
            }
        }
    }

    fun resetPlayback() {
        _uiState.update { it.copy(playhead = 0f, isPlaying = false) }
    }

    fun setRequestedSlices(value: Float) {
        _uiState.update { it.copy(requestedSlices = value.roundToInt().coerceIn(16, 256)) }
    }

    fun setCubeDepth(value: Float) {
        _uiState.update { it.copy(cubeDepth = value.coerceIn(0.35f, 2.6f)) }
    }

    fun setBackgroundBrightness(value: Float) {
        _uiState.update { it.copy(backgroundBrightness = value.coerceIn(0.10f, 1.20f)) }
    }

    fun setBackgroundTransparency(value: Float) {
        _uiState.update { it.copy(backgroundTransparency = value.coerceIn(0f, 0.98f)) }
    }

    fun setHighlightTransparency(value: Float) {
        _uiState.update { it.copy(highlightTransparency = value.coerceIn(0f, 0.95f)) }
    }

    fun setMotionBoost(value: Float) {
        _uiState.update { it.copy(motionBoost = value.coerceIn(0f, 1f)) }
    }
}
