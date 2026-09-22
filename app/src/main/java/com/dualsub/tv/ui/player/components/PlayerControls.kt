package com.dualsub.tv.ui.player.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.ui.format.formatTime
import com.dualsub.tv.ui.shell.BeiPillButton
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass

/**
 * 播放控制条：进度、两段时间、以及两路字幕的入口。
 *
 * 版面排法：**进度条 → 左下「已播放」/ 右下「总时长」→ 字幕入口**。
 * 两段时间分列进度条两端（而不是挤成一个 `已播 / 总长` 字符串），是为了让「还剩多久」
 * 一眼可读 —— 电视上隔着三米看，字挤在一起都费劲。
 *
 * ## 为什么控制条上没有「播放 / −10s / +10s」这三个按钮
 *
 * 这三个操作**遥控器上本来就有键位**，屏上再放一遍纯属重复，还白占一整行高度：
 * - **OK 键**（`Key.Enter` / `Key.DirectionCenter`）= 播放 / 暂停
 *   （另有 `MediaPlayPause` / `MediaPlay` / `MediaPause` 媒体键，见 `PlayerScreen` 的按键分支）
 * - **← / → 短按** = −10s / +10s（长按进逐帧预览）
 *
 * 删掉这一行之后，**焦点默认落在「主字幕」上**（它成了第一个可聚焦元素）——
 * 屏上不再有"看不见的按钮"抢焦点。
 *
 * **文件名不在这里**，它由 [PlayerStatusBar] 画在左上角：那是常驻位置，
 * 而控制条会 6 秒自动隐藏，标题不该跟着一起消失再重排。
 *
 * 进度条本身也可聚焦：焦点移到进度条后，←/→ 方向键触发 onSeekBackward/Forward。
 *
 * ## 压在画面上的那条暗渐变
 *
 * 底部渐变刻意**收到黑 60%**（规范里"视频画面上叠色上限：黑 ≤ 60%"）：再深就把底部
 * 画面糊掉了。这条渐变的唯一职责是让控制条的文字在**亮画面上也读得清**，
 * 不是把下半屏压黑。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerControls(
    positionMs: Long,
    durationMs: Long,
    primaryLabel: String,
    secondaryLabel: String,
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
                    colors = listOf(Color.Transparent, BeiGlass.Ink.copy(alpha = 0.60f))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    horizontal = BeiDims.ScreenStart,
                    vertical = BeiDims.ScreenVertical
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SeekableProgressBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeekBackward = onSeekBackward,
                onSeekForward = onSeekForward
            )

            // 进度条下方：**左＝已播放时长，右＝视频总时长**
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(positionMs),
                    fontSize = 17.sp,
                    color = BeiGlass.TextPrimary
                )
                Text(
                    text = formatTime(durationMs),
                    fontSize = 17.sp,
                    color = BeiGlass.TextSecondary
                )
            }

            // 屏上唯一的一排入口：两路字幕。播放控制全在遥控器键位上（见 KDoc）。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BeiPillButton(label = "主字幕：$primaryLabel", onClick = onConfigurePrimary)
                BeiPillButton(label = "次字幕：$secondaryLabel", onClick = onConfigureSecondary)
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
            .background(BeiGlass.Border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(BeiGlass.Accent)
        )
    }
}
