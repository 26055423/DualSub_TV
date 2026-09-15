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
    /** 距屏幕底部的距离，主字幕小、次字幕大（叠在主字幕上方）。 */
    val bottomPaddingDp: Int = 48,
    val bold: Boolean = true
) {
    val textColorCompose: Color get() = Color(textColor)
    val outlineColorCompose: Color get() = Color(outlineColor)
    val fontSize: TextUnit get() = fontSizeSp.sp

    companion object {
        /** 主字幕：贴底显示。 */
        val PRIMARY = SubtitleStyle(
            fontSizeSp = 28,
            textColor = 0xFFFFFFFF.toInt(),
            outlineColor = 0xFF000000.toInt(),
            outlineWidth = 3f,
            bottomPaddingDp = 40,
            bold = true
        )

        /** 次字幕：金色，叠在主字幕上方。 */
        val SECONDARY = SubtitleStyle(
            fontSizeSp = 24,
            textColor = 0xFFFFE082.toInt(),
            outlineColor = 0xFF000000.toInt(),
            outlineWidth = 3f,
            bottomPaddingDp = 96,
            bold = true
        )
    }
}
