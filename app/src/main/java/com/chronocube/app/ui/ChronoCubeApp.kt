package com.chronocube.app.ui

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chronocube.app.gl.ChronoCubeSurfaceView
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun ChronoCubeApp(
    viewModel: ChronoCubeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var resetCameraSignal by remember { mutableLongStateOf(0L) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.loadVideo(
                uri = uri,
                displayName = queryDisplayName(context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )),
            )
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    LaunchedEffect(uiState.isPlaying) {
        if (!uiState.isPlaying) return@LaunchedEffect
        var previousNanos = withFrameNanos { it }
        while (isActive) {
            val nowNanos = withFrameNanos { it }
            val deltaSeconds = (nowNanos - previousNanos) / 1_000_000_000f
            previousNanos = nowNanos
            viewModel.advancePlayback(deltaSeconds.coerceAtMost(0.1f))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
        ) {
            CubeViewport(
                state = uiState,
                resetCameraSignal = resetCameraSignal,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
            )
            CompactHeader(
                sourceName = uiState.sourceName,
                onSelectVideo = { videoPicker.launch(arrayOf("video/*")) },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            ControlPanel(
                state = uiState,
                onSelectVideo = { videoPicker.launch(arrayOf("video/*")) },
                onTogglePlayback = viewModel::togglePlayback,
                onPlayheadChange = viewModel::setPlayhead,
                onResetPlayback = viewModel::resetPlayback,
                onResetCamera = { resetCameraSignal += 1L },
                onRequestedSlicesChange = viewModel::setRequestedSlices,
                onRegenerate = viewModel::regenerateVideo,
                onCubeDepthChange = viewModel::setCubeDepth,
                onBackgroundBrightnessChange = viewModel::setBackgroundBrightness,
                onBackgroundTransparencyChange = viewModel::setBackgroundTransparency,
                onHighlightTransparencyChange = viewModel::setHighlightTransparency,
                onMotionBoostChange = viewModel::setMotionBoost,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .widthIn(max = 760.dp),
            )
        }
    }
}

@Composable
private fun CompactHeader(
    sourceName: String?,
    onSelectVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .widthIn(max = 760.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ChronoCube",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = sourceName ?: "视频时空切片",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            TextButton(onClick = onSelectVideo) {
                Text(if (sourceName == null) "导入" else "更换")
            }
        }
    }
}

@Composable
private fun CubeViewport(
    state: ChronoCubeUiState,
    resetCameraSignal: Long,
    modifier: Modifier = Modifier,
) {
    var surfaceView by remember { mutableStateOf<ChronoCubeSurfaceView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, surfaceView) {
        val view = surfaceView
        if (view == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> view.onResume()
                    Lifecycle.Event.ON_PAUSE -> view.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                view.onResume()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                view.onPause()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            surfaceView?.releaseRenderer()
        }
    }

    LaunchedEffect(resetCameraSignal) {
        if (resetCameraSignal > 0L) surfaceView?.resetCamera()
    }

    Box(
        modifier = modifier
            .background(Color.Black, RoundedCornerShape(22.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                shape = RoundedCornerShape(22.dp),
            ),
    ) {
        if (state.hasVideo) {
            AndroidView(
                factory = { context ->
                    ChronoCubeSurfaceView(context).also { surfaceView = it }
                },
                update = { view ->
                    view.bindFrames(
                        generation = state.frameGeneration,
                        frames = state.frames,
                        aspectRatio = state.videoAspectRatio,
                    )
                    view.updateSettings(
                        playhead = state.playhead,
                        cubeDepth = state.cubeDepth,
                        backgroundBrightness = state.backgroundBrightness,
                        backgroundTransparency = state.backgroundTransparency,
                        highlightTransparency = state.highlightTransparency,
                        motionBoost = state.motionBoost,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 66.dp),
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.55f),
            ) {
                Text(
                    text = "单指轨道旋转 · 双指缩放/扭转 · 双击复位",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        } else {
            EmptyViewport()
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 210.dp)
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text("正在提取视频帧")
                        LinearProgressIndicator(
                            progress = { state.loadingProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = (state.loadingProgress * 100).roundToInt().toString() + "%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyViewport() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(
            modifier = Modifier
                .width(180.dp)
                .height(130.dp),
        ) {
            val lineColor = Color(0xFF8FB5FF)
            val fillColor = Color(0xFF61DFC0).copy(alpha = 0.12f)
            val frontTopLeft = Offset(size.width * 0.24f, size.height * 0.30f)
            val frontTopRight = Offset(size.width * 0.82f, size.height * 0.30f)
            val frontBottomRight = Offset(size.width * 0.82f, size.height * 0.82f)
            val frontBottomLeft = Offset(size.width * 0.24f, size.height * 0.82f)
            val backOffset = Offset(-size.width * 0.14f, -size.height * 0.14f)

            val face = Path().apply {
                moveTo(frontTopLeft.x, frontTopLeft.y)
                lineTo(frontTopRight.x, frontTopRight.y)
                lineTo(frontBottomRight.x, frontBottomRight.y)
                lineTo(frontBottomLeft.x, frontBottomLeft.y)
                close()
            }
            drawPath(face, fillColor)
            drawPath(
                face,
                lineColor,
                style = Stroke(width = 2.dp.toPx()),
            )
            listOf(
                frontTopLeft,
                frontTopRight,
                frontBottomRight,
                frontBottomLeft,
            ).forEach { point ->
                drawLine(
                    color = lineColor.copy(alpha = 0.72f),
                    start = point,
                    end = point + backOffset,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            drawLine(
                lineColor.copy(alpha = 0.72f),
                frontTopLeft + backOffset,
                frontTopRight + backOffset,
                2.dp.toPx(),
            )
            drawLine(
                lineColor.copy(alpha = 0.72f),
                frontTopRight + backOffset,
                frontBottomRight + backOffset,
                2.dp.toPx(),
            )
            drawLine(
                lineColor.copy(alpha = 0.72f),
                frontBottomRight + backOffset,
                frontBottomLeft + backOffset,
                2.dp.toPx(),
            )
            drawLine(
                lineColor.copy(alpha = 0.72f),
                frontBottomLeft + backOffset,
                frontTopLeft + backOffset,
                2.dp.toPx(),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "导入视频，生成可交互的时空立方体",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "切片位置保持固定，播放时高亮帧沿时间轴向后推进",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ControlPanel(
    state: ChronoCubeUiState,
    onSelectVideo: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPlayheadChange: (Float) -> Unit,
    onResetPlayback: () -> Unit,
    onResetCamera: () -> Unit,
    onRequestedSlicesChange: (Float) -> Unit,
    onRegenerate: () -> Unit,
    onCubeDepthChange: (Float) -> Unit,
    onBackgroundBrightnessChange: (Float) -> Unit,
    onBackgroundTransparencyChange: (Float) -> Unit,
    onHighlightTransparencyChange: (Float) -> Unit,
    onMotionBoostChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var settingsExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    onClick = if (state.hasVideo) onTogglePlayback else onSelectVideo,
                    enabled = !state.isLoading,
                ) {
                    Text(
                        when {
                            !state.hasVideo -> "导入"
                            state.isPlaying -> "暂停"
                            else -> "播放"
                        },
                    )
                }
                Slider(
                    value = state.playhead,
                    onValueChange = onPlayheadChange,
                    enabled = state.hasVideo && !state.isLoading,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = state.timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                TextButton(
                    onClick = { settingsExpanded = !settingsExpanded },
                ) {
                    Text(if (settingsExpanded) "收起" else "参数")
                }
            }

            AnimatedVisibility(visible = settingsExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedButton(
                            onClick = onSelectVideo,
                            enabled = !state.isLoading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("更换视频")
                        }
                        OutlinedButton(
                            onClick = onResetPlayback,
                            enabled = state.hasVideo && !state.isLoading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("时间复位")
                        }
                        OutlinedButton(
                            onClick = onResetCamera,
                            enabled = state.hasVideo,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("视角复位")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "切片固定在时间轴上",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = state.frames.size.toString() + " 帧",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    SettingSlider(
                        label = "时间深度",
                        valueLabel = "%.2f".format(state.cubeDepth),
                        value = state.cubeDepth,
                        valueRange = 0.35f..2.6f,
                        onValueChange = onCubeDepthChange,
                    )
                    SettingSlider(
                        label = "非高亮亮度",
                        valueLabel =
                            (state.backgroundBrightness * 100).roundToInt().toString() + "%",
                        value = state.backgroundBrightness,
                        valueRange = 0.10f..1.20f,
                        onValueChange = onBackgroundBrightnessChange,
                    )
                    SettingSlider(
                        label = "非高亮透明度",
                        valueLabel =
                            (state.backgroundTransparency * 100).roundToInt().toString() + "%",
                        value = state.backgroundTransparency,
                        valueRange = 0f..0.98f,
                        onValueChange = onBackgroundTransparencyChange,
                    )
                    SettingSlider(
                        label = "高亮帧透明度",
                        valueLabel =
                            (state.highlightTransparency * 100).roundToInt().toString() + "%",
                        value = state.highlightTransparency,
                        valueRange = 0f..0.95f,
                        onValueChange = onHighlightTransparencyChange,
                    )
                    SettingSlider(
                        label = "运动轨迹增强",
                        valueLabel = (state.motionBoost * 100).roundToInt().toString() + "%",
                        value = state.motionBoost,
                        valueRange = 0f..1f,
                        onValueChange = onMotionBoostChange,
                    )
                    SettingSlider(
                        label = "提取切片数",
                        valueLabel = state.requestedSlices.toString(),
                        value = state.requestedSlices.toFloat(),
                        valueRange = 16f..256f,
                        steps = 239,
                        onValueChange = onRequestedSlicesChange,
                    )
                    FilledTonalButton(
                        onClick = onRegenerate,
                        enabled = state.hasVideo && !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("按 " + state.requestedSlices + " 帧重新生成")
                    }
                    Text(
                        text = "最多 256 帧；切片较多时会自动降低纹理分辨率以控制内存。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    steps: Int = 0,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun queryDisplayName(cursor: Cursor?): String? {
    cursor.use {
        if (it == null || !it.moveToFirst()) return null
        val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        return if (columnIndex >= 0) it.getString(columnIndex) else null
    }
}
