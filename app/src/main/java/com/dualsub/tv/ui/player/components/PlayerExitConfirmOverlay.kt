package com.dualsub.tv.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass

/**
 * 退出播放的**确认浮层**。
 *
 * ## 为什么要有
 *
 * 返回键原先在"没有叠加层"时直接 `onBack()` —— 一次误按就把片子退了，进度还不保存。
 * 现在返回键最后落到这里：先问一句再走。
 *
 * ## 交互（按键由 `PlayerScreen` 统一处理，本组件只负责画）
 *
 * - 左右：在两个按钮之间切换；
 * - OK：执行当前高亮的那一个；
 * - 返回键：等于「取消」（关掉确认框，继续看）。
 *
 * **默认高亮「取消」** —— 确认退出是破坏性操作，不该是落点。
 *
 * 两个按钮**不参与 Compose 焦点系统**，因为播放页整屏是一个自管按键的 `focusable()`
 * （见 `PlayerScreen` 顶部的说明）。所以它们只是"看起来像按钮"的显示态，
 * 高亮由 [confirmSelected] 决定。
 */
@Composable
fun PlayerExitConfirmOverlay(
    /** true = 高亮「确认退出」；false = 高亮「取消」（默认）。 */
    confirmSelected: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BeiGlass.Scrim),
        contentAlignment = Alignment.Center
    ) {
        val shape = RoundedCornerShape(BeiDims.CardRadius)
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(shape)
                .background(BeiGlass.Panel)
                .border(BeiDims.Border, BeiGlass.Border, shape)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "退出播放？",
                color = BeiGlass.TextPrimary,
                fontSize = BeiDims.CardTitleSize,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "当前播放进度不会保存。",
                color = BeiGlass.TextSecondary,
                fontSize = BeiDims.BodySize,
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DialogChoice(label = "确认退出", selected = confirmSelected, onClick = onConfirm)
                DialogChoice(label = "取消", selected = !confirmSelected, onClick = onCancel)
            }
        }
    }
}

/** 确认框里的一个选项：**只显示态**，按键由外层处理（见组件说明）。 */
@Composable
private fun DialogChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .width(150.dp)
            .clip(shape)
            .background(if (selected) BeiGlass.AccentFill else BeiGlass.Glass)
            .border(
                width = if (selected) BeiDims.BorderFocus else BeiDims.Border,
                color = if (selected) BeiGlass.AccentBorderStrong else BeiGlass.Border,
                shape = shape
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) BeiGlass.AccentBright else BeiGlass.TextSecondary,
            fontSize = BeiDims.BodySize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}
