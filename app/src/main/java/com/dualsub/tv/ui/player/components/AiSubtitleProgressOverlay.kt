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
 * 右上角小悬浮条：AI 字幕生成进度提示。
 * 仅在 [state] 为 [AiSubtitleState.Running] 时可见。
 */
@Composable
fun AiSubtitleProgressOverlay(
    state: AiSubtitleState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state is AiSubtitleState.Running,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val running = state as? AiSubtitleState.Running ?: return@AnimatedVisibility
        val fraction = if (running.total > 0) running.current.toFloat() / running.total else 0f

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
                        text = "${running.current}/${running.total}",
                        fontSize = 12.sp,
                        color = Color(0xFF90A4AE)
                    )
                }
                Text(text = running.phase, fontSize = 12.sp, color = Color(0xFFE0E0E0))
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
}
