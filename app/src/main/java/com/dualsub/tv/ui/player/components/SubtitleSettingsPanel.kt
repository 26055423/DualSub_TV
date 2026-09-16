package com.dualsub.tv.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.media.EmbeddedSubtitleReader
import com.dualsub.tv.player.SubtitleStyle
import com.dualsub.tv.player.SubtitleTrack
import com.dualsub.tv.ui.format.formatOffset

/**
 * 字幕设置面板：左栏主字幕、右栏次字幕，两栏结构完全对称。
 *
 * 每一栏都能独立设置来源（关闭 / 内嵌轨道 / 外挂文件）、字号、底部距离和时间偏移 ——
 * 这正是「双字幕」最需要的操作面。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SubtitleSettingsPanel(
    primary: SubtitleTrack,
    secondary: SubtitleTrack,
    embeddedTracks: List<EmbeddedSubtitleReader.TrackInfo>,
    onClosePanel: () -> Unit,
    onSelectNone: (forPrimary: Boolean) -> Unit,
    onSelectEmbedded: (forPrimary: Boolean, track: EmbeddedSubtitleReader.TrackInfo) -> Unit,
    onPickFile: (forPrimary: Boolean) -> Unit,
    onStyleChange: (forPrimary: Boolean, transform: (SubtitleStyle) -> SubtitleStyle) -> Unit,
    onOffsetDelta: (forPrimary: Boolean, deltaMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.93f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            TrackColumn(
                title = "主字幕",
                track = primary,
                isPrimary = true,
                embeddedTracks = embeddedTracks,
                onSelectNone = onSelectNone,
                onSelectEmbedded = onSelectEmbedded,
                onPickFile = onPickFile,
                onStyleChange = onStyleChange,
                onOffsetDelta = onOffsetDelta,
                modifier = Modifier.weight(1f)
            )
            TrackColumn(
                title = "次字幕",
                track = secondary,
                isPrimary = false,
                embeddedTracks = embeddedTracks,
                onSelectNone = onSelectNone,
                onSelectEmbedded = onSelectEmbedded,
                onPickFile = onPickFile,
                onStyleChange = onStyleChange,
                onOffsetDelta = onOffsetDelta,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(40.dp)
        ) {
            Button(onClick = onClosePanel) {
                Text(text = "返回播放", fontSize = 15.sp)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackColumn(
    title: String,
    track: SubtitleTrack,
    isPrimary: Boolean,
    embeddedTracks: List<EmbeddedSubtitleReader.TrackInfo>,
    onSelectNone: (Boolean) -> Unit,
    onSelectEmbedded: (Boolean, EmbeddedSubtitleReader.TrackInfo) -> Unit,
    onPickFile: (Boolean) -> Unit,
    onStyleChange: (Boolean, (SubtitleStyle) -> SubtitleStyle) -> Unit,
    onOffsetDelta: (Boolean, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = title, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Text(
            text = buildString {
                append("当前：").append(track.label)
                if (track.cueCount > 0) append("（").append(track.cueCount).append(" 条）")
                if (track.isLoading) append(" 载入中…")
            },
            fontSize = 13.sp,
            color = Color(0xFFB0BEC5)
        )

        track.error?.let { error ->
            Text(text = "⚠ $error", fontSize = 13.sp, color = Color(0xFFFF8A80))
        }

        Text(text = "字幕来源", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

        LabeledButton("不使用该路字幕") { onSelectNone(isPrimary) }

        embeddedTracks.forEach { embedded ->
            LabeledButton(embedded.label) { onSelectEmbedded(isPrimary, embedded) }
        }

        LabeledButton("选择外挂字幕文件…") { onPickFile(isPrimary) }

        Text(text = "显示样式", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

        StepperRow(
            label = "字号",
            value = "${track.style.fontSizeSp}",
            onDecrease = { onStyleChange(isPrimary) { it.copy(fontSizeSp = (it.fontSizeSp - 2).coerceAtLeast(12)) } },
            onIncrease = { onStyleChange(isPrimary) { it.copy(fontSizeSp = (it.fontSizeSp + 2).coerceAtMost(60)) } }
        )

        StepperRow(
            label = "底部距离",
            value = "${track.style.bottomPaddingDp}",
            onDecrease = {
                onStyleChange(isPrimary) { it.copy(bottomPaddingDp = (it.bottomPaddingDp - 8).coerceAtLeast(0)) }
            },
            onIncrease = {
                onStyleChange(isPrimary) { it.copy(bottomPaddingDp = (it.bottomPaddingDp + 8).coerceAtMost(400)) }
            }
        )

        StepperRow(
            label = "时间偏移",
            value = formatOffset(track.offsetMs),
            onDecrease = { onOffsetDelta(isPrimary, -100L) },
            onIncrease = { onOffsetDelta(isPrimary, 100L) }
        )

        StepperRow(
            label = "描边强度",
            value = "${track.style.outlineWidth.toInt()}",
            onDecrease = {
                onStyleChange(isPrimary) { it.copy(outlineWidth = (it.outlineWidth - 1f).coerceAtLeast(0f)) }
            },
            onIncrease = {
                onStyleChange(isPrimary) { it.copy(outlineWidth = (it.outlineWidth + 1f).coerceAtMost(10f)) }
            }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LabeledButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(text = label, fontSize = 14.sp, maxLines = 1)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            modifier = Modifier.width(74.dp)
        )
        Button(onClick = onDecrease) { Text(text = "−", fontSize = 14.sp) }
        Text(
            text = value,
            fontSize = 13.sp,
            modifier = Modifier.width(72.dp)
        )
        Button(onClick = onIncrease) { Text(text = "+", fontSize = 14.sp) }
    }
}
