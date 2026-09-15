package com.dualsub.tv.ui.player.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.dualsub.tv.player.SubtitleStyle

/**
 * 一路字幕的显示层。
 *
 * 主字幕与次字幕各挂一个实例，[SubtitleStyle.bottomPaddingDp] 决定它们上下叠放的位置，
 * 因此两路不会互相遮挡。
 */
@Composable
fun SubtitleOverlay(
    text: String?,
    style: SubtitleStyle,
    modifier: Modifier = Modifier
) {
    if (text.isNullOrBlank()) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = style.bottomPaddingDp.dp, start = 40.dp, end = 40.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = TextStyle(
                color = style.textColorCompose,
                fontSize = style.fontSize,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                // Compose 没有真正的文字描边，用不带偏移的阴影模拟，保证浅色画面上也能看清
                shadow = Shadow(
                    color = style.outlineColorCompose,
                    offset = Offset.Zero,
                    blurRadius = style.outlineWidth * 2f
                )
            )
        )
    }
}
