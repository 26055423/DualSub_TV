package com.dualsub.tv.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.ui.format.formatTime
import com.dualsub.tv.ui.theme.BeiPalette

/**
 * 播放控制条：进度、时间、播放/暂停、快进退，以及两路字幕的入口。
 *
 * 播放控制刻意排在字幕入口**上一行** —— 焦点默认落在第一个可聚焦元素上，
 * 这样按遥控器确认键时最常用的操作就是「播放/暂停」，而不是先掉进字幕面板。
 *
 * 进度条本身也可聚焦：焦点移到进度条后，←/→ 方向键触发 onSeekBackward/Forward。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    primaryLabel: String,
    secondaryLabel: String,
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onConfigurePrimary: () -> Unit,
    onConfigureSecondary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            SeekableProgressBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeekBackward = onSeekBackward,
                onSeekForward = onSeekForward
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                    fontSize = 14.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ControlButton(if (isPlaying) "暂停" else "播放", onTogglePlayPause)
                    ControlButton("−10s", onSeekBackward)
                    ControlButton("+10s", onSeekForward)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ControlButton("主字幕：$primaryLabel", onConfigurePrimary)
                    ControlButton("次字幕：$secondaryLabel", onConfigureSecondary)
                }
            }
        }
    }
}

@Composable
private fun SeekableProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit
) {
    val fraction = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.22f))
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeekBackward(); true }
                    Key.DirectionRight -> { onSeekForward(); true }
                    Key.Enter, Key.DirectionCenter -> { onSeekForward(); true }
                    else -> false
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(BeiPalette.Accent)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ControlButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(text = label, fontSize = 15.sp)
    }
}
