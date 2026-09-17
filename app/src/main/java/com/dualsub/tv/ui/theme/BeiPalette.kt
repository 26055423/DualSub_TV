package com.dualsub.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 「当贝风」调色板 —— 只给**播放器界面**用。
 *
 * 参照当贝播放器的观感：**深蓝半透明面板 + 亮黄高亮 + 黑字压黄底**，圆角偏大。
 *
 * 原先这些颜色是散落在各组件里的硬编码（`#0A0E14` / `#141A22` 这类纯灰黑，偏「工程师配色」，
 * 缺少当贝那种偏蓝的层次感）。统一收到这里之后，以后想微调只改这一个文件。
 *
 * 首页媒体库不在这里的范围内，仍走 TV Material 的默认深色方案
 *（见 `DualSubTVTheme`）。等播放器界面定稿了再考虑是否把整套主题一起换掉。
 */
object BeiPalette {

    // ---------------------------------------------------------------- 面板

    /** 二级面板底：深蓝，约 94% 不透明（留一点画面透出来）。 */
    val Panel = Color(0xF01A2942)

    /** 一级菜单底：比 [Panel] 更深一档，形成「向外退一层」的层次。 */
    val PanelDeep = Color(0xF5121D33)

    /** 面板打开时压在画面上的遮罩。 */
    val Scrim = Color(0x8C000000)

    // ---------------------------------------------------------------- 高亮

    /** 高亮 / 焦点色 —— 当贝的标志性亮黄。 */
    val Accent = Color(0xFFFFC53D)

    /** 焦点在二级面板时，一级当前项只做浅色底，避免「两处都在抢注意力」。 */
    val AccentDim = Color(0x38FFC53D)

    /** 压在 [Accent] 上的前景色（黄底黑字）。 */
    val OnAccent = Color(0xFF10182A)

    // ---------------------------------------------------------------- 文字

    /** 正文。 */
    val TextPrimary = Color(0xFFF2F5F9)

    /** 次级文字（小节标题等）。 */
    val TextSecondary = Color(0xFFB4C2D6)

    /** 弱化文字（字段名、说明）。 */
    val TextMuted = Color(0xFF8296B0)

    /** 可点击动作项（如「选择外挂字幕文件…」）的强调色。 */
    val Link = Color(0xFF7FB4FF)
}
