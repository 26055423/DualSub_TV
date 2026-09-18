@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.dualsub.tv.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.ai.AiSubtitleState
import com.dualsub.tv.ui.theme.BeiPalette

/**
 * 右上角小悬浮条：AI 字幕进度提示。
 * Running → 深灰背景 + 进度条
 * Live    → 绿色胶囊（无进度条）
 * LiveFailed(canFallback) → 橙色背景 + 两行提示
 */
@Composable
fun AiSubtitleProgressOverlay(
    state: AiSubtitleState,
    modifier: Modifier = Modifier
) {
    val visible = state is AiSubtitleState.Running
        || state is AiSubtitleState.Live
        || (state is AiSubtitleState.LiveFailed)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        when (state) {
            is AiSubtitleState.Running -> RunningCard(state)
            is AiSubtitleState.Live -> LiveCapsule(state)
            is AiSubtitleState.LiveFailed -> LiveFailedCard(state)
            else -> Box(modifier = Modifier.width(0.dp))
        }
    }
}

@Composable
private fun RunningCard(state: AiSubtitleState.Running) {
    val fraction = if (state.total > 0) state.current.toFloat() / state.total else 0f
    Box(
        modifier = Modifier
            .padding(top = 32.dp, end = 32.dp)
            .widthIn(max = 320.dp)
            .background(Color(0xE0101418), RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "AI 生成字幕", fontSize = 13.sp, color = Color(0xFF90A4AE))
                Text(
                    text = "${state.current}/${state.total}",
                    fontSize = 12.sp,
                    color = Color(0xFF90A4AE)
                )
            }
            Text(text = state.phase, fontSize = 12.sp, color = Color(0xFFE0E0E0))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(BeiPalette.Accent)
                )
            }
        }
    }
}

@Composable
private fun LiveCapsule(state: AiSubtitleState.Live) {
    Box(
        modifier = Modifier
            .padding(top = 32.dp, end = 32.dp)
            .background(Color(0xCC1B5E20), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "▶ 实时 AI · 已缓冲 ${state.bufferedMs / 1000}s",
            fontSize = 13.sp,
            color = Color(0xFF80FF80)
        )
    }
}

@Composable
private fun LiveFailedCard(state: AiSubtitleState.LiveFailed) {
    Box(
        modifier = Modifier
            .padding(top = 32.dp, end = 32.dp)
            .widthIn(max = 340.dp)
            .background(Color(0xE0301800), RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "⚠ 实时模式失败",
                fontSize = 13.sp,
                color = Color(0xFFFFB74D)
            )
            Text(
                text = state.reason.take(60),
                fontSize = 12.sp,
                color = Color(0xFFE0E0E0)
            )
            if (state.canFallback) {
                Text(
                    text = "建议改用「AI 自动生成字幕」批处理模式",
                    fontSize = 11.sp,
                    color = Color(0xFF90A4AE)
                )
            }
        }
    }
}
