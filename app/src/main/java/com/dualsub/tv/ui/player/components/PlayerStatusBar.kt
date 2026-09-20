package com.dualsub.tv.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass

/**
 * 播放页顶栏：**左＝文件名，右＝实时网速 · 当前时间（24 小时制）**。
 *
 * 位置：文件名贴左上角，状态信息贴右上角，中间大片留空 ——
 * 既不压住画面主体，也不跟底部的进度条/字幕打架。
 *
 * 这里只读快照：网速由 `PlayerViewModel` 的 ticker 每秒从 libVLC 取一次，
 * 时钟由界面自己每秒更新 —— **组合期绝不碰 JNI**（这条纪律是被真机卡死教出来的）。
 *
 * 胶囊底用 [BeiGlass.Panel]（黑 55%）：压在视频画面上，按规范只能用黑底半透明，
 * 叠白会把画面洗灰。
 *
 * @param speedText 形如 `2.56 MB/s`（单位固定 MB/s，见 `PlayerScreen.formatSpeed`）；
 *   **本地文件传 null**（「网速」对本地播放没有意义，不显示比显示一个恒定的假数字诚实）。
 */
@Composable
fun PlayerStatusBar(
    title: String,
    speedText: String?,
    clockText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = BeiDims.ScreenStart,
                vertical = BeiDims.ScreenVertical
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusPill(
            text = title,
            fontSize = 18,
            color = BeiGlass.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            speedText?.let {
                StatusPill(
                    text = it,
                    fontSize = 13,
                    color = BeiGlass.TextSecondary,
                    fontWeight = FontWeight.Normal
                )
            }
            StatusPill(
                text = clockText,
                fontSize = 15,
                color = BeiGlass.TextPrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** 半透明底的小胶囊：压在画面上也能看清，且不喧宾夺主。 */
@Composable
private fun StatusPill(
    text: String,
    fontSize: Int,
    color: Color,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = fontSize.sp,
        color = color,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(BeiGlass.Panel, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
