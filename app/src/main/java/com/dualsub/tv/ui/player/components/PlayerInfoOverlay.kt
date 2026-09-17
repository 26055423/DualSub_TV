@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.dualsub.tv.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/**
 * 「按上/下显示的播放信息层」。
 *
 * 设计取舍：
 * - 放在**左上角**而不是居中，避免遮住画面主体和底部的字幕；
 * - 半透明深色背板，扫一眼就能读，不打断观看；
 * - 只读快照数据（`PlaybackStats` / StateFlow），**组合期不碰 JNI** —— 这一点很要紧，
 *   之前就是因为每次重组都去调 libVLC 取值，把 UI 线程卡死过。
 */
@Composable
fun PlayerInfoOverlay(
    title: String,
    container: String?,
    videoSize: String,
    streamSummary: String?,
    audioLabel: String?,
    primaryLabel: String,
    secondaryLabel: String,
    positionMs: Long,
    durationMs: Long,
    volume: Int,
    rate: Float,
    /** 容器/编码那一行诊断文本，可能为 null。 */
    trackSummary: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(start = 40.dp, top = 36.dp)
            .width(560.dp)
            .background(Color(0xE0101418), RoundedCornerShape(12.dp))
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 2
        )

        InfoRow("播放进度", "${formatTime(positionMs)} / ${formatTime(durationMs)}")
        InfoRow("容器", container ?: "未识别")
        InfoRow("视频", videoSize)
        InfoRow("码率", streamSummary ?: "读取中…（刚起播时可能还没有）")
        InfoRow("音轨", audioLabel ?: "无")
        InfoRow("主字幕", primaryLabel)
        InfoRow("次字幕", secondaryLabel)
        InfoRow("音量", "$volume%")
        InfoRow("倍速", "%.2fx".format(rate))
        trackSummary?.takeIf { it.isNotBlank() }?.let { InfoRow("轨道", it) }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color(0xFF90A4AE),
            modifier = Modifier.width(88.dp)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = Color(0xFFE0E0E0),
            modifier = Modifier.weight(1f)
        )
    }
}

/** 把毫秒格式化成 `H:MM:SS` 或 `M:SS`。 */
internal fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
