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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
        topBar = {
            AppHeader(
                sourceName = uiState.sourceName,
                onSelectVideo = { videoPicker.launch(arrayOf("video/*")) },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val wideLayout = maxWidth >= 760.dp
            if (wideLayout) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CubeViewport(
                        state = uiState,
                        resetCameraSignal = resetCameraSignal,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
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
                        onOpacityChange = viewModel::setSliceOpacity,
                        onMotionBoostChange = viewModel::setMotionBoost,
                        modifier = Modifier
                            .width(370.dp)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CubeViewport(
                        state = uiState,
                        resetCameraSignal = resetCameraSignal,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
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
                        onOpacityChange = viewModel::setSliceOpacity,
                        onMotionBoostChange = viewModel::setMotionBoost,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppHeader(
    sourceName: String?,
    onSelectVideo: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ChronoCube",
                    style = MaterialTheme.typography.titleLarge,
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
                Text(if (sourceName == null) "导入视频" else "更换视频")
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
                        sliceOpacity = state.sliceOpacity,
                        motionBoost = state.motionBoost,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.55f),
            ) {
                Text(
                    text = "拖动旋转 · 双指缩放 · 双击复位",
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
            text = "视频帧会沿时间轴堆叠，并在播放时穿过立方体",
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
    onOpacityChange: (Float) -> Unit,
    onMotionBoostChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var settingsExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.timeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = state.frames.size.toString() + " 帧",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = state.playhead,
                onValueChange = onPlayheadChange,
                enabled = state.hasVideo && !state.isLoading,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = if (state.hasVideo) onTogglePlayback else onSelectVideo,
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        when {
                            !state.hasVideo -> "选择视频"
                            state.isPlaying -> "暂停"
                            else -> "播放"
                        },
                    )
                }
                OutlinedButton(
                    onClick = onResetPlayback,
                    enabled = state.hasVideo && !state.isLoading,
                ) {
                    Text("回到起点")
                }
                OutlinedButton(
                    onClick = onResetCamera,
                    enabled = state.hasVideo,
                ) {
                    Text("复位视角")
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 2.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            )
            TextButton(
                onClick = { settingsExpanded = !settingsExpanded },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (settingsExpanded) "收起参数" else "调整立方体参数")
            }

            AnimatedVisibility(visible = settingsExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingSlider(
                        label = "时间深度",
                        valueLabel = "%.2f".format(state.cubeDepth),
                        value = state.cubeDepth,
                        valueRange = 0.35f..2.6f,
                        onValueChange = onCubeDepthChange,
                    )
                    SettingSlider(
                        label = "切片透明度",
                        valueLabel = (state.sliceOpacity * 100).roundToInt().toString() + "%",
                        value = state.sliceOpacity,
                        valueRange = 0.04f..0.35f,
                        onValueChange = onOpacityChange,
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
                        valueRange = 16f..72f,
                        steps = 55,
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
                        text = "切片更多会提升时间细节，同时增加显存与处理时间。",
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
