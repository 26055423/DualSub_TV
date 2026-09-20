@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.dualsub.tv.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.ai.AiSubtitleState
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass

/**
 * 右上角小悬浮条：AI 字幕进度提示。
 *
 * Running → 黑底半透明面板 + 香槟进度条
 * Live    → 成功色胶囊（无进度条）
 * LiveFailed → 面板 + 警告色标题
 *
 * ## 配色按"压在视频画面上"的规则走
 *
 * 这里背后是**视频画面**，不是我们自己铺的夜景，所以面板用 [BeiGlass.Panel]（黑 55%）
 * 而不是外壳那套 [BeiGlass.Glass]（白 6%）—— 规范 § 四 的硬约束是"视频画面上叠白 ≤ 12%、
 * 叠黑 ≤ 60%"，叠白多一点画面就发灰看不清了。
 *
 * 只有成功 / 警告这类**状态**才借用语义色，装饰一律无色。
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

/** 画面上悬浮面板的统一样式（黑底半透明 + 1dp 白 15% 描边 + 16dp 圆角）。 */
private val OverlayShape = RoundedCornerShape(BeiDims.PanelRadius)

@Composable
private fun RunningCard(state: AiSubtitleState.Running) {
    val fraction = if (state.total > 0) state.current.toFloat() / state.total else 0f
    Box(
        modifier = Modifier
            .padding(top = 32.dp, end = 32.dp)
            .widthIn(max = 320.dp)
            .clip(OverlayShape)
            .background(BeiGlass.Panel)
            .border(BeiDims.Border, BeiGlass.Border, OverlayShape)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "AI 生成字幕", fontSize = BeiDims.BodySize, color = BeiGlass.TextSecondary)
                Text(
                    text = "${state.current}/${state.total}",
                    fontSize = BeiDims.CaptionSize,
                    color = BeiGlass.TextMuted
                )
            }
            Text(text = state.phase, fontSize = BeiDims.CaptionSize, color = BeiGlass.TextPrimary)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BeiGlass.Border)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(BeiGlass.Accent)
                )
            }
        }
    }
}

@Composable
private fun LiveCapsule(state: AiSubtitleState.Live) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .padding(top = 32.dp, end = 32.dp)
            .clip(shape)
            .background(BeiGlass.Glass)
            .border(BeiDims.Border, BeiGlass.Success.copy(alpha = 0.45f), shape)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "▶ 实时 AI · 已缓冲 ${state.bufferedMs / 1000}s",
            fontSize = BeiDims.BodySize,
            color = BeiGlass.Success
        )
    }
}

@Composable
private fun LiveFailedCard(state: AiSubtitleState.LiveFailed) {
    Box(
        modifier = Modifier
            .padding(top = 32.dp, end = 32.dp)
            .widthIn(max = 340.dp)
            .clip(OverlayShape)
            .background(BeiGlass.Panel)
            .border(BeiDims.Border, BeiGlass.Border, OverlayShape)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "⚠ 实时模式失败",
                fontSize = BeiDims.BodySize,
                color = BeiGlass.Warning
            )
            Text(
                text = state.reason.take(60),
                fontSize = BeiDims.CaptionSize,
                color = BeiGlass.TextPrimary
            )
            if (state.canFallback) {
                Text(
                    text = "建议改用「AI 自动生成字幕」批处理模式",
                    fontSize = BeiDims.TinySize,
                    color = BeiGlass.TextSecondary
                )
            }
        }
    }
}
