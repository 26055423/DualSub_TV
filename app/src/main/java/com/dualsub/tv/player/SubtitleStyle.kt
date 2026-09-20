package com.dualsub.tv.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 一路字幕的显示样式。
 *
 * 字段刻意使用基本类型（Int/Float/Boolean），既便于直接持久化，
 * 也避免把 Compose 类型泄漏到存储层。
 */
data class SubtitleStyle(
    val fontSizeSp: Int = 26,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val outlineColor: Int = 0xFF000000.toInt(),
    val outlineWidth: Float = 3f,
    /**
     * 距屏幕底部的距离（dp）。
     *
     * **只对次字幕有效**：主字幕改由 libVLC 的 libass 定位之后，这个值不再影响它的画面位置
     * （[PRIMARY] 里保留它只为结构对称）。次字幕正是靠它避开主字幕，见 [SECONDARY]。
     */
    val bottomPaddingDp: Int = 48,
    val bold: Boolean = true
) {
    val textColorCompose: Color get() = Color(textColor)
    val outlineColorCompose: Color get() = Color(outlineColor)
    val fontSize: TextUnit get() = fontSizeSp.sp

    companion object {
        /**
         * 主字幕：由 libVLC 的 libass 渲染，位置由**片源自带的特效字幕**决定。
         *
         * 这里的 `bottomPaddingDp` 已不影响画面（保留只为与次字幕结构对称）；
         * 字号 / 颜色 / 描边同样不再生效，所以主字幕设置页里也不再摆这些控件。
         */
        val PRIMARY = SubtitleStyle(
            fontSizeSp = 28,
            textColor = 0xFFFFFFFF.toInt(),
            outlineColor = 0xFF000000.toInt(),
            outlineWidth = 3f,
            bottomPaddingDp = 40,
            bold = true
        )

        /**
         * 次字幕：金色，叠在主字幕**上方**。
         *
         * `bottomPaddingDp` 在这里还兼任「避开主字幕」的职责：主字幕画在 VLCVideoLayout
         * 内部、高度拿不到，所以只能留一个**固定余量** —— 112dp 约合两行主字幕再加一条
         * 间隙，常见特效字幕不会与它重叠。
         *
         * 若片源的主字幕特别高（三行以上，或 `\pos` 顶到画面中部），把「底部距离」再调大；
         * 反过来想让两行贴得更近，也可以调小。
         */
        val SECONDARY = SubtitleStyle(
            fontSizeSp = 24,
            textColor = 0xFFFFE082.toInt(),
            outlineColor = 0xFF000000.toInt(),
            outlineWidth = 3f,
            bottomPaddingDp = 112,
            bold = true
        )

        /**
         * 卡拉OK（`\k` / `\kf`）在**还没唱到**的那一段上用的高亮色。
         *
         * 它**不属于 UI 主题**（界面主题色在 `ui/theme/BeiGlass.kt`），而是**字幕渲染语义**
         * 的一部分：片源用 `\k` 标出"这一段该被扫亮"，扫亮前后的颜色是渲染器的事，
         * 与界面风格无关 —— 所以它跟着字幕样式走，而不是跟着色板走。
         *
         * 放在这里还有一个好处：字幕渲染器里不再出现裸色。
         */
        val KARAOKE_HIGHLIGHT_ARGB = 0xFFFFD54F.toInt()
    }
}
