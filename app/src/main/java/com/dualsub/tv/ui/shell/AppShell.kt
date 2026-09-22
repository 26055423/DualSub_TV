package com.dualsub.tv.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass
import com.dualsub.tv.ui.theme.BeiMotion
import androidx.compose.ui.text.font.FontWeight

/**
 * 应用外壳的导航目的地。**枚举顺序就是顶部标签从左到右的顺序。**
 *
 * 关于「为什么没有独立的『媒体流』项」：DLNA 媒体服务器**已经在「网络」页里**
 * （`NetworkScreen` 负责 SSDP 发现与浏览，见 `DlnaBrowser`）。把它提成一级会与
 * 「网络」重复，而且设备发现本质上是网络行为，放一起更符合直觉。
 *
 * **AI 字幕不单列一级**：它的「用」（生成 / 实时翻译）在**播放页菜单的字幕二级**里，
 * 「配」（API Key / 模型）在**「设置 → AI 字幕」**里 —— 两边都是用户真正会用到的时机。
 * 早先它占过一个一级导航项，结果是既挤占导航、又和播放时的操作路径脱节。
 *
 * [subtitle] 现在**不显示在顶部标签上**（横向标签放两行字会太高、也挤），
 * 保留在枚举里是为了"这个目的地是干什么的"有一处权威描述；各页自己的
 * `BeiPageHeader` 副标题才是用户看得见的那一份。
 */
enum class ShellTab(val label: String, val subtitle: String) {
    Local("本地", "本机媒体库"),
    Network("网络", "SMB / 网盘 / DLNA / WebDAV"),
    Settings("设置", "播放与显示 · AI 字幕");
}

/**
 * 应用级外壳：**顶部横向标签 + 下方内容区**，底下一层夜景光斑。
 *
 * ## 为什么从「左侧竖向导航」改成「顶部横向标签」
 *
 * 竖向导航是照搬手机/桌面的做法，而电视的遥控器**只有方向键**：左右是最自然的移动方向
 * （页面内容本身也是横向排布），把导航放在侧面等于要求用户频繁做"横向的左右"和
 * "纵向的上下"两种不同性质的移动。改成顶部标签之后：
 *
 * - 内容区拿到**整屏宽度**，横向行能多放下一两张卡；
 * - 「我在哪一页」用一排常驻标签回答，不再占用一块纵向空间；
 * - 移动语义统一成"上下换层级、左右换内容"。
 *
 * 导航不覆盖播放页 —— 播放时整屏交给 `PlayerScreen`（那是沉浸式场景，顶栏同样碍事）。
 * 所以播放页**不走这个外壳**，由 `AppRoot` 直接渲染。
 *
 * ## 视觉（`docs/UI_STYLE_REFERENCE.md`）
 *
 * 深墨夜景底（[BeiGlass.Night]）+ 两三处 `radialGradient` 柔光斑（**不做实时模糊** ——
 * MTK 电视 SoC 上逐帧模糊直接掉帧）+ **无色玻璃**标签 + **唯一**强调色香槟金。
 *
 * 这里刻意**没有**实色顶栏底板：标签与内容同在夜景底上，只用一条 1dp 白 15%
 * 的分隔线划界（早先是纯白面板 —— 那是浅色外壳的做法，新风格下不成立）。
 */
@Composable
fun AppShell(
    current: ShellTab,
    onSelect: (ShellTab) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(BeiGlass.Night)) {
        BeiGlowBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            BeiTopBar(current = current, onSelect = onSelect)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // 内容区四周留白交给具体页面（它们比外壳更清楚自己要多宽），
                // 这里只给一个统一的电视安全边距基准（40 / 28dp，避免过扫描切掉内容）。
                content(
                    PaddingValues(
                        top = BeiDims.ScreenVertical,
                        bottom = BeiDims.ScreenVertical,
                        start = BeiDims.ScreenStart,
                        end = BeiDims.ScreenEnd
                    )
                )
            }
        }
    }
}

/**
 * 夜景光斑：2–3 处**大半径、低透明度**的径向渐变，纯装饰、不承载信息。
 *
 * 这是玻璃拟态在电视上的正确做法 —— **不做实时模糊**（`backdrop-filter` 那套在
 * Compose 里没有跨版本 API，而且逐帧模糊在电视 SoC 上会把帧率吃光），
 * 只用 `Brush.radialGradient` 铺一层柔光，玻璃的"光学感"靠配色与层级表达。
 *
 * 用 [Canvas] 一次画完（比堆十几个 Box 便宜），也不响应任何状态，因此不会引起重组。
 */
@Composable
private fun BeiGlowBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // 右上：主光源（最深、最大的一处）
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(BeiGlass.GlowDeep.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(size.width * 0.82f, size.height * 0.02f),
                radius = size.maxDimension * 0.60f
            )
        )
        // 左上：补一处较弱的，避免整屏只有单侧发光
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(BeiGlass.GlowDeep.copy(alpha = 0.32f), Color.Transparent),
                center = Offset(size.width * 0.26f, size.height * -0.08f),
                radius = size.maxDimension * 0.40f
            )
        )
        // 左下：一点月光，压住暗角
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(BeiGlass.Glow.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(size.width * 0.12f, size.height * 1.02f),
                radius = size.maxDimension * 0.46f
            )
        )
    }
}

/**
 * 顶部横向标签栏：品牌标记 + 三个目的地标签，底部一条 1dp 分隔线划出边界。
 *
 * 焦点态与旧的竖向导航**同源**（都只看 `focused`），只是形状从"竖条 + 左竖线"变成
 * "横向胶囊" —— 横向排布里左侧竖线没有意义，所以**去掉了竖线**，
 * 焦点只靠「香槟 15% 底 + 2dp 香槟描边 + 微放大」三件套表达。
 * 「当前在哪一页」仍然是**亮香槟文字 + 加粗**，与焦点互不冲突（见 `UI_STYLE_REFERENCE.md` §2.4）。
 */
@Composable
private fun BeiTopBar(
    current: ShellTab,
    onSelect: (ShellTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // 只用一条 1dp 白 15% 的分隔线划出顶栏边界，不铺实色底板。
                val y = size.height - 0.5.dp.toPx()
                drawLine(
                    color = BeiGlass.Border,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(horizontal = BeiDims.ScreenStart, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandMark()
        Spacer(modifier = Modifier.width(18.dp))

        ShellTab.entries.forEach { tab ->
            TopTabItem(
                tab = tab,
                selected = tab == current,
                onClick = { onSelect(tab) }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

/**
 * 左上角品牌标记：香槟金玻璃方块 + 播放符号 + 产品名。
 *
 * 早先这里是**靛蓝→紫的线性渐变方块** —— 新规范明确禁止多种强调色、也禁止
 * AI 紫粉渐变，所以改成"香槟 15% 填充 + 香槟 40% 描边"的玻璃块。
 *
 * 从竖向导航搬到顶栏后**变紧凑了**：去掉了下方的间距，图标也从 30dp 收到 26dp ——
 * 顶栏是常驻的，它比内容区更该让位。
 */
@Composable
private fun BrandMark() {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier.padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(shape)
                .background(BeiGlass.AccentFill)
                .border(BeiDims.Border, BeiGlass.AccentBorder, shape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(11.dp)) {
                drawPath(
                    path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(0f, size.height)
                        close()
                    },
                    color = BeiGlass.AccentBright
                )
            }
        }
        Text(
            text = "DualSub TV",
            color = BeiGlass.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 一个顶部标签。
 *
 * 焦点态按规范做**三件套**（底色 + 描边 + 微放大）—— 电视上「焦点」是唯一的位置
 * 指示，用户没有鼠标指针可看，所以高亮必须足够明显。
 *
 * ⚠️ **只允许存在一块高亮，它跟焦点走**：底色、描边**全部只看 `focused`**，
 * 绝不能写成 `if (focused || selected)`，否则「当前页」那块底会赖着不走 ——
 * 竖向导航时代连着踩过两次，真机表现是"背景先动、原来那块和 | 线没跟着动"。
 * 「当前在哪一页」只用**文字色（亮香槟）+ 加粗**表达。
 *
 * 描边为什么画在 [Surface] 的 content 里、而不是用 tv-material3 的 `border` 参数：
 * 后者的类型是它自家的 `androidx.tv.material3.Border`（不是 foundation 的
 * `BorderStroke`，传错会报一个**位置具有误导性**的错 —— 见 `docs/HANDOVER.md` 第四节）。
 * 画在 content 里还有一个好处：**跟着 `Surface` 的 scale 一起缩放**，放大时不会错位。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopTabItem(
    tab: ShellTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(BeiDims.PanelRadius)
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        // ⚠️ 容器色**全部留透明**，背景改由 content 里那一层自绘 —— 原因见上方注释。
        // contentColor 也留保守值（TextSecondary），实际文字色在 content Box 里统一算。
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = BeiGlass.TextSecondary,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = BeiGlass.TextSecondary,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = BeiGlass.TextSecondary
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = BeiMotion.FOCUS_SCALE),
        modifier = Modifier.onFocusChanged { focused = it.isFocused }
    ) {
        Box {
            // 背景层、图标色、文字色三者**读同一个 focused State**，保证同帧更新。
            // 早先 labelColor 在函数体里同步计算（用参数 selected），而 focused 来自
            // onFocusChanged 异步回调——两条路径可能差一帧，切 Tab 时颜色会闪。
            val tint = if (focused || selected) BeiGlass.AccentBright else BeiGlass.TextSecondary

            // 背景层：**只由 `focused` 驱动**
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(if (focused) BeiGlass.AccentFill else Color.Transparent)
            )

            Row(
                modifier = Modifier
                    .height(44.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                TabGlyph(tab = tab, tint = tint)

                Text(
                    text = tab.label,
                    color = tint,
                    fontSize = 16.sp,
                    // 加粗 = "当前就在这一页"，这是"选中"唯一保留的表达
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }

            // 描边叠层：**只有焦点画边框**（2dp 香槟），与底色同源。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = if (focused) BeiDims.BorderFocus else BeiDims.Border,
                        color = if (focused) BeiGlass.AccentBorderStrong else Color.Transparent,
                        shape = shape
                    )
            )
        }
    }
}

/**
 * 标签的小图标 —— 自绘，不引图标库。
 *
 * 之所以不引 `material-icons`：本项目依赖里没有它，为一个顶栏引入整套图标包不划算，
 * 而且不同电视对 emoji 的渲染差异很大（可能显示成方框 —— 我们刚在字幕上吃过这个亏）。
 * 这几个图形都是简单几何，画出来在所有设备上都一样。
 */
@Composable
private fun TabGlyph(tab: ShellTab, tint: Color) {
    Canvas(modifier = Modifier.size(BeiDims.IconNav)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.09f
        when (tab) {
            // 本地：一个「房子」轮廓
            ShellTab.Local -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.08f, h * 0.45f), stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.92f, h * 0.45f), stroke)
                drawRect(
                    color = tint,
                    topLeft = Offset(w * 0.20f, h * 0.45f),
                    size = Size(w * 0.60f, h * 0.43f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                )
            }
            // 网络：一个地球（圆 + 一条经线）
            ShellTab.Network -> {
                drawCircle(tint, radius = w * 0.42f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
                drawOval(
                    color = tint,
                    topLeft = Offset(w * 0.30f, h * 0.08f),
                    size = Size(w * 0.40f, h * 0.84f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                )
                drawLine(tint, Offset(w * 0.08f, h * 0.5f), Offset(w * 0.92f, h * 0.5f), stroke)
            }
            // 设置：三横线滑块
            ShellTab.Settings -> {
                val ys = listOf(0.24f, 0.5f, 0.76f)
                ys.forEachIndexed { i, y ->
                    drawLine(tint, Offset(w * 0.08f, h * y), Offset(w * 0.92f, h * y), stroke)
                    val knobX = if (i % 2 == 0) w * 0.34f else w * 0.66f
                    drawCircle(tint, radius = w * 0.12f, center = Offset(knobX, h * y))
                }
            }
        }
    }
}
