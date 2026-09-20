package com.dualsub.tv.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * **全局唯一调色板** —— 深墨夜景 + 无色玻璃 + 单一强调色（Nocturne Glassmorphism / TV 版）。
 *
 * 规范来源：`docs/UI_STYLE_REFERENCE.md`。原先项目有两套色板
 * （`BeiLightPalette` 浅蓝外壳 + `BeiPalette` 深蓝播放页），现已合并到这里，
 * **外壳与播放页共用同一套颜色**。
 *
 * ## 简约原则（2026-09-19 第二轮收敛）
 *
 * 第一版落地后色阶偏多（玻璃 3 档、描边 2 档、香槟填充 2 档 + 描边 2 档……），
 * 一屏里"灰阶层次"太多，看着复杂。**这一版只保留必要的语义，其余档位合并**：
 *
 * | 项 | 收敛为 |
 * |---|---|
 * | 玻璃底 | **一档**（焦点 / 按下**不再换底色**，见下） |
 * | 描边 | **两种颜色**：常态白 15%、焦点香槟（实色） |
 * | 香槟 | 实色 + 15% 填充，各一档 |
 * | 播放页面板 | **一档**黑 55% |
 * | 夜景底 | `Ink` + `Night` 两档（`Elevated` 归并到 `Night`） |
 * | 光斑 | **一个色** |
 *
 * **token 名全部保留** —— 它们是语义槽位（`GlassFocused` 是"焦点时的面板底"，
 * 将来真要区分时改这一行即可，不必动二十个调用点）。所以你会看到若干"名字不同、值相同"
 * 的 token，那是刻意的，不是漏改。
 *
 * 焦点态的简化最值得记一笔：现在只用**「2dp 强调色描边 + 微放大」两样**
 * （规范本来就要求"底 / 描边 / 放大"三选二），不再叠一层更亮的底色。
 *
 * ## 三条不能破的纪律
 *
 * 1. **强调色只有一个**（香槟金 [Accent]），只出现在主操作 / 当前选中 / 焦点 / 关键数字上；
 * 2. **面板无色**（[Glass] 白 6%），颜色属于背景，不给面板本身着色；
 * 3. **播放页压画面上的面板用黑底半透明**（[Panel]，黑 55%）—— 视频上叠白最多 12%，
 *    叠黑最多 60%，再多就看不清画面。
 *
 * 玻璃的"光学感"靠**配色与层级**表达，**不做任何模糊**（性能红线见 [BeiMotion]）。
 */
object BeiGlass {

    // ------------------------------------------------------------------ 夜景底色

    /** 近黑：最深的底、页面边缘（播放器底色、日志层）。 */
    val Ink = Color(0xFF060A13)

    /** 主背景 —— 外壳与播放页共用的夜景底。 */
    val Night = Color(0xFF0B1322)

    /** 光源光斑（**唯一色** —— 两处柔光同色，界面才不会"发花"）。 */
    val GlowDeep = Color(0xFF33517A)

    /** 分区 / 抬升区域。**当前与 [Night] 同值**：简约化后不再靠"另一种底色"分层。 */
    val Elevated = Night

    /** 光斑（亮）。**当前与 [GlowDeep] 同值**。 */
    val Glow = GlowDeep

    // ------------------------------------------------------------------ 无色玻璃表面
    //
    // **只有一档玻璃**。焦点 / 按下**不换底色** —— 焦点靠「2dp 强调色描边 + 微放大」表达，
    // 按下靠 scale 缩回（规范要求"底 / 描边 / 放大"三选二，两样就够）。
    // 少一档底色，整屏就少一层灰阶 —— 这是本次简约化最主要的一刀。

    /** 面板底：深色底上的玻璃（白 6%）。 */
    val Glass = Color(0x0FFFFFFF)

    /** 焦点态面板底。**当前 = [Glass]**（焦点不换底色，只加描边与放大）。 */
    val GlassFocused = Glass

    /** 按下态面板底。**当前 = [Glass]**（反馈由 `BeiMotion.PRESS_SCALE` 承担）。 */
    val GlassBright = Glass

    /** 默认描边（白 15%），1dp。 */
    val Border = Color(0x26FFFFFF)

    /** 焦点态描边色。**当前 = [Accent]** —— **所有焦点一律香槟色**，不再有第二种描边色。 */
    val BorderBright: Color get() = Accent

    // ------------------------------------------------------------------ 文字（三档白）

    /** 主文字：标题（白 100%）。 */
    val TextPrimary = Color.White

    /** 次级文字：正文 / 小节标题（白 60%）。 */
    val TextSecondary = Color(0x99FFFFFF)

    /** 弱化文字：说明 / 字段名（白 40%）。 */
    val TextMuted = Color(0x66FFFFFF)

    // ------------------------------------------------------------------ 唯一强调色：香槟金

    /** 强调色本体：图标符号、选中指示、进度条已播段、焦点描边、关键数字。 */
    val Accent = Color(0xFFE4B863)

    /** 香槟底上的文字、高亮词。 */
    val AccentBright = Color(0xFFF3DCA8)

    /** 强调底填充（香槟 15%）—— 常态按钮、焦点、选中**共用这一个值**。 */
    val AccentFill = Color(0x26E4B863)

    /** 更亮一档的强调底。**当前 = [AccentFill]**（焦点不再靠"更亮的底"表达）。 */
    val AccentFillStrong = AccentFill

    /** 强调描边（常态）。**当前 = 实色 [Accent]**。 */
    val AccentBorder: Color get() = Accent

    /** 强调描边（焦点）。**当前 = 实色 [Accent]**（与常态同色，靠 2dp 宽度区分）。 */
    val AccentBorderStrong: Color get() = Accent

    /** 可点击动作的文字色。**当前 = [AccentBright]**。 */
    val Link = AccentBright

    /** 焦点已在二级面板时，一级当前项只做浅色底，避免"两处抢注意力"。**当前 = [AccentFill]**。 */
    val AccentDim = AccentFill

    // ------------------------------------------------------------------ 播放页：压在视频画面上的面板
    //
    // 与 [Glass] 的区别是"底色来源"：外壳背后是我们自己铺的夜景，
    // 播放页背后是视频画面 —— 那里只能用**黑底半透明**（§ 四 红线）。

    /** 压画面上的面板底：黑 55%。 */
    val Panel = Color(0x8C000000)

    /** 一级菜单底。**当前 = [Panel]**（两级菜单同色，层次交给位置而非色值）。 */
    val PanelDeep = Panel

    /** 面板打开时压在画面上的遮罩。**当前 = [Panel]**。 */
    val Scrim = Panel

    // ------------------------------------------------------------------ 状态语义色
    //
    // 这两个不是"第二个强调色"：只在"成功 / 错误"发生时短暂出现，
    // 不参与常态视觉，也不用于装饰。警告色直接用 [Accent]（香槟本来就是警示色系）。

    /** 成功（如「配置已更新」）。 */
    val Success = Color(0xFF6FD08C)

    /** 警告（如「已扫码，请确认授权」）。**当前 = [Accent]**。 */
    val Warning = Accent

    /** 错误（如「⚠ 打开文件失败」）。 */
    val Danger = Color(0xFFFF8A80)

    // ------------------------------------------------------------------ 功能性固定色
    //
    // 下面这个不是"主题色"，而是**由外部标准决定的**颜色 —— 二维码识别要求纯白底 +
    // 纯黑码点，换成任何带透明度的色都会扫不出来。放进色板，只为守住
    // "全项目不出现裸色"这一条纪律。

    /** 二维码的纯白底（扫码识别要求，不能带透明度）。 */
    val QrSurface = Color.White
}

/**
 * TV 尺寸档位（10-foot UI）—— 三米外看、只有遥控器。
 *
 * 经验值：**电视上的"信息密度"是靠字号省的，不是靠缩小间距**。
 * 正文低于 13sp、次级低于 11sp 在三米外基本读不清。
 */
object BeiDims {

    // ---- 屏幕安全边距（避免电视过扫描把内容切掉）

    /** 左右安全边距。 */
    val ScreenStart = 40.dp

    /** 右侧安全边距。 */
    val ScreenEnd = 40.dp

    /** 上下安全边距。 */
    val ScreenVertical = 28.dp

    // ---- 圆角 / 描边 / 间距

    /** 卡片圆角。 */
    val CardRadius = 18.dp

    /** 面板圆角（贴屏幕边缘那一侧可只圆外侧）。 */
    val PanelRadius = 16.dp

    /** 默认描边。 */
    val Border = 1.dp

    /** 焦点态描边。 */
    val BorderFocus = 2.dp

    /** 卡片间距。 */
    val CardGap = 18.dp

    /** 卡片内边距（横 / 纵）。 */
    val CardPaddingH = 20.dp
    val CardPaddingV = 16.dp

    /** 遥控器焦点框至少要能框住它。 */
    val MinTouchTarget = 48.dp

    // ---- 字号档位

    /** 页面大标题。 */
    val TitleSize = 28.sp

    /** 面板 / 卡片标题。 */
    val CardTitleSize = 16.sp

    /** 正文（遥控器界面里的舒适下限）。 */
    val BodySize = 13.sp

    /** 次级 / 状态文字。 */
    val CaptionSize = 12.sp

    /** 极弱提示（尽量少用）。 */
    val TinySize = 11.sp

    // ---- 图标尺寸

    /** 来源 / 功能大图标卡。 */
    val IconSourceCard = 56.dp

    /** 导航栏图标。 */
    val IconNav = 22.dp

    /** 状态 / 提示图标。 */
    val IconStatus = 18.dp
}

/**
 * 动效常量。
 *
 * **性能红线（MTK 电视 SoC，本项目实测）**：动画只动 `alpha` / `scale` / 颜色 ——
 * 动 shadow / blur / 裁剪会触发整块重绘，逐帧模糊直接把帧率吃光。
 * 过渡曲线统一 [Easing]，时长 [DURATION_MS]；**不要 bounce / elastic**。
 */
object BeiMotion {

    /** 统一的过渡曲线（淡入快、收尾缓）。 */
    val Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** 常态过渡时长（ms）。 */
    const val DURATION_MS = 350

    /** 按下反馈的时长（ms）—— 要在 200ms 内完成，给"按到了"的即时感。 */
    const val PRESS_DURATION_MS = 160

    /** 焦点态微放大：**不要**用模糊或大范围阴影动画。 */
    const val FOCUS_SCALE = 1.03f

    /** 按下缩回。 */
    const val PRESS_SCALE = 0.97f
}
