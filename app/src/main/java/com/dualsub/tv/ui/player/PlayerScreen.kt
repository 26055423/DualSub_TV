package com.dualsub.tv.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.ui.format.formatTime
import com.dualsub.tv.ui.player.components.PlayerControls
import com.dualsub.tv.ui.player.components.SubtitleOverlay
import com.dualsub.tv.ui.player.components.SubtitleSettingsPanel
import kotlinx.coroutines.delay

/**
 * 播放页。
 *
 * 层次自下而上：视频 SurfaceView → 次字幕层 → 主字幕层 → 控制条 / 设置面板 / 提示与诊断层。
 * 两路字幕是各自独立的叠加层，位置由各自的 `bottomPaddingDp` 决定，因此能同时在屏幕上共存。
 *
 * 按键分三类处理：遥控器**媒体键**（⏯ / ⏪ / ⏩）直接作用于播放器；控制条隐藏时任意键唤出它；
 * 其余交给焦点系统在按钮之间移动。菜单 / 信息键用于开关屏上诊断信息。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val primary by viewModel.primary.collectAsState()
    val secondary by viewModel.secondary.collectAsState()
    val primaryText by viewModel.primaryText.collectAsState()
    val secondaryText by viewModel.secondaryText.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val embeddedTracks by viewModel.embeddedTracks.collectAsState()
    val playerError by viewModel.playerError.collectAsState()
    val noticeText by viewModel.notice.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val mediaInfo by viewModel.mediaInfo.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var panelOpen by remember { mutableStateOf(false) }
    var pickForPrimary by remember { mutableStateOf(true) }
    var showBuffering by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.selectExternalFile(uri, displayNameOf(context, uri), pickForPrimary)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.saveProgressNow() }
    }

    LaunchedEffect(showControls, isPlaying, panelOpen) {
        if (showControls && isPlaying && !panelOpen) {
            delay(AUTO_HIDE_DELAY_MS)
            showControls = false
        }
    }

    // 拖动进度时会短暂进入 BUFFERING，直接显示会闪，所以延迟一点再报出来
    LaunchedEffect(isBuffering) {
        if (isBuffering) {
            delay(BUFFERING_HINT_DELAY_MS)
            showBuffering = true
        } else {
            showBuffering = false
        }
    }

    BackHandler {
        if (panelOpen) panelOpen = false else onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (event.key) {
                    // 电视遥控器上的专用媒体键
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        viewModel.togglePlayPause()
                        true
                    }

                    Key.MediaFastForward -> {
                        viewModel.seekBy(SEEK_STEP_MS)
                        true
                    }

                    Key.MediaRewind -> {
                        viewModel.seekBy(-SEEK_STEP_MS)
                        true
                    }

                    // 屏上诊断：菜单键 / 信息键开关，用来在真机上直接看播放器卡在哪一步
                    Key.Menu, Key.Info -> {
                        showDiagnostics = !showDiagnostics
                        true
                    }

                    else -> if (!showControls && !panelOpen) {
                        // 控制条隐藏时，任意其他按键先把控制条唤出来
                        showControls = true
                        true
                    } else {
                        false
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { viewModel.attachSurface(it) }
            },
            modifier = Modifier.fillMaxSize()
        )

        SubtitleOverlay(text = secondaryText, style = secondary.style)
        SubtitleOverlay(text = primaryText, style = primary.style)

        if (showControls && !panelOpen) {
            PlayerControls(
                title = viewModel.video.title,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                primaryLabel = primary.label,
                secondaryLabel = secondary.label,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeekBackward = { viewModel.seekBy(-SEEK_STEP_MS) },
                onSeekForward = { viewModel.seekBy(SEEK_STEP_MS) },
                onConfigurePrimary = {
                    pickForPrimary = true
                    panelOpen = true
                },
                onConfigureSecondary = {
                    pickForPrimary = false
                    panelOpen = true
                }
            )
        }

        if (panelOpen) {
            SubtitleSettingsPanel(
                primary = primary,
                secondary = secondary,
                embeddedTracks = embeddedTracks,
                onClosePanel = { panelOpen = false },
                onSelectNone = { forPrimary -> viewModel.selectNone(forPrimary) },
                onSelectEmbedded = { forPrimary, track -> viewModel.selectEmbedded(track, forPrimary) },
                onPickFile = { forPrimary ->
                    pickForPrimary = forPrimary
                    filePicker.launch(arrayOf("*/*"))
                },
                onStyleChange = { forPrimary, transform -> viewModel.changeStyle(forPrimary, transform) },
                onOffsetDelta = { forPrimary, delta -> viewModel.adjustOffset(forPrimary, delta) }
            )
        }

        // 播放失败要**看得见** —— 错误不再被藏在字幕轨的字段里
        val errorText = playerError
        if (errorText != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "⚠ $errorText",
                        fontSize = 18.sp,
                        color = Color(0xFFFF8A80),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "按「返回」退出本片。若是 SMB 片源，请检查网络位置里的账号密码、" +
                            "共享权限，或该文件能否被其他播放器打开。",
                        fontSize = 14.sp,
                        color = Color(0xFFB0BEC5),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            if (showBuffering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "正在缓冲…", fontSize = 16.sp, color = Color.White)
                }
            }

            // 非致命提示（例如「音频编码不支持，已静音播放」）：顶部一行小字，不遮挡画面与控制条
            val infoText = noticeText
            if (infoText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 40.dp, top = 28.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(text = "ℹ $infoText", fontSize = 14.sp, color = Color(0xFFFFD54F))
                }
            }
        }

        if (showDiagnostics) {
            val stateText = when {
                errorText != null -> "出错"
                isBuffering -> "缓冲中（等待数据）"
                isPlaying -> "播放中"
                durationMs > 0L -> "已暂停 / 就绪"
                else -> "未开始（可能仍在连接片源）"
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xE6000000))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "诊断信息（按菜单 / 信息键关闭）",
                        fontSize = 13.sp,
                        color = Color(0xFFFFD54F)
                    )
                    DiagnosticRow("片源", viewModel.video.uri.toString())
                    // 同一容器有的能播有的不能时，看这一行最快
                    DiagnosticRow("编码", mediaInfo ?: "尚未取得")
                    DiagnosticRow("状态", stateText)
                    DiagnosticRow("进度", "${formatTime(positionMs)} / ${formatTime(durationMs)}")
                    DiagnosticRow("内嵌字幕轨", "${embeddedTracks.size} 条")
                    DiagnosticRow("主字幕", "${primary.label}（${primary.cueCount} 条）")
                    DiagnosticRow("次字幕", "${secondary.label}（${secondary.cueCount} 条）")
                    DiagnosticRow("提示", noticeText ?: "无")
                    DiagnosticRow("错误", errorText ?: "无")
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color(0xFF90A4AE),
            modifier = Modifier.width(110.dp)
        )
        Text(text = value, fontSize = 13.sp, color = Color.White)
    }
}

/** 从 Uri 解析出用于显示的文件名。 */
private fun displayNameOf(context: Context, uri: Uri): String {
    runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "外挂字幕"
}

private const val AUTO_HIDE_DELAY_MS = 6000L
private const val SEEK_STEP_MS = 10_000L
private const val BUFFERING_HINT_DELAY_MS = 700L
