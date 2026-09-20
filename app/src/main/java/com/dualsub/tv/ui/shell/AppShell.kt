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
import androidx.compose.foundation.layout.fillMaxHeight
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
 * 应用外壳的导航目的地。**枚举顺序就是导航栏从上到下的顺序。**
 *
 * 关于「为什么没有独立的『媒体流』项」：DLNA 媒体服务器**已经在「网络」页里**
 * （`NetworkScreen` 负责 SSDP 发现与浏览，见 `DlnaBrowser`）。把它提成一级会与
 * 「网络」重复，而且设备发现本质上是网络行为，放一起更符合直觉。
 *
 * **AI 字幕不单列一级**：它的「用」（生成 / 实时翻译）在**播放页菜单的字幕二级**里，
 * 「配」（API Key / 模型）在**「设置 → AI 字幕」**里 —— 两边都是用户真正会用到的时机。
 * 早先它占过一个一级导航项，结果是既挤占导航、又和播放时的操作路径脱节。
 */
enum class ShellTab(val label: String, val subtitle: String) {
    Local("本地", "本机媒体库"),
    Network("网络", "SMB / 网盘 / DLNA / WebDAV"),
    Settings("设置", "播放与显示 · AI 字幕");
}

/**
 * 应用级外壳：**左侧竖向导航 + 右侧内容区**，底下一层夜景光斑。
 *
 * 为什么要有这层：原先 `AppRoot` 是「一个页面占满全屏」的手写状态机，每个页面各自
 * 画标题栏、各自处理返回。观感上应当是**导航常驻、内容区切换**，所以把导航提到
 * 外壳这一层，各页面只负责画自己的内容。
 *
 * 导航不覆盖播放页 —— 播放时整屏交给 `PlayerScreen`（那是沉浸式场景，左侧挂一条
 * 导航栏只会碍事）。所以播放页**不走这个外壳**，由 `AppRoot` 直接渲染。
 *
 * ## 视觉（`docs/UI_STYLE_REFERENCE.md`）
 *
 * 深墨夜景底（[BeiGlass.Night]）+ 两三处 `radialGradient` 柔光斑（**不做实时模糊** ——
 * MTK 电视 SoC 上逐帧模糊直接掉帧）+ **无色玻璃**导航项 + **唯一**强调色香槟金。
 *
 * 这里刻意**没有**实色导航底板：导航与内容同在夜景底上，只用一条 1dp 白 15%
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

        Row(modifier = Modifier.fillMaxSize()) {
            BeiNavRail(current = current, onSelect = onSelect)
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
 * 左侧竖向导航。
 *
 * 焦点与选中是**两个不同的概念**，别混：遥控器焦点会到处跑（用户可能正要去别处），
 * 而「选中」是当前真正在看的页面。两者**用不同的视觉语言**表达，别再让它们撞色：
 *
 * - **焦点**：香槟 15% 底 + **2dp 香槟描边** + 微放大 —— 焦点是唯一的"位置指示"，
 *   必须一眼看得出来（电视上没有鼠标指针可看）；
 * - **选中**：香槟 15% 底 + **左侧香槟短条**，**没有描边**。
 *
 * ⚠️ 这两者曾经**都套香槟描边**（简约化时把焦点描边色也改成了 `Accent`），于是焦点上下
 * 移动时看起来"边框在跳、颜色没变"。**描边现在只属于焦点**，这是两者唯一的区分依据 ——
 * 将来谁想给选中也加边框，先回来看这段。
 */
@Composable
private fun BeiNavRail(
    current: ShellTab,
    onSelect: (ShellTab) -> Unit
) {
    Column(
        modifier = Modifier
            .width(228.dp)
            .fillMaxHeight()
            // 只用一条 1dp 白 15% 的分隔线划出导航边界，不铺实色底板。
            .drawBehind {
                val x = size.width - 0.5.dp.toPx()
                drawLine(
                    color = BeiGlass.Border,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(horizontal = 16.dp, vertical = BeiDims.ScreenVertical),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BrandMark()

        ShellTab.entries.forEach { tab ->
            NavItem(
                tab = tab,
                selected = tab == current,
                onClick = { onSelect(tab) }
            )
        }
    }
}

/**
 * 左上角品牌标记：香槟金玻璃方块 + 播放符号。
 *
 * 早先这里是**靛蓝→紫的线性渐变方块** —— 新规范明确禁止多种强调色、也禁止
 * AI 紫粉渐变，所以改成"香槟 15% 填充 + 香槟 40% 描边"的玻璃块。
 */
@Composable
private fun BrandMark() {
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier.padding(start = 8.dp, bottom = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(shape)
                .background(BeiGlass.AccentFill)
                .border(BeiDims.Border, BeiGlass.AccentBorder, shape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(13.dp)) {
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
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 一个导航项。
 *
 * 焦点态按规范做**三件套**（底色 + 描边 + 微放大）—— 电视上「焦点」是唯一的位置
 * 指示，用户没有鼠标指针可看，所以列表 / 网格里的高亮项必须足够明显。
 *
 * 描边为什么画在 [Surface] 的 content 里、而不是用 tv-material3 的 `border` 参数：
 * 后者的类型是它自家的 `androidx.tv.material3.Border`（不是 foundation 的
 * `BorderStroke`，传错会报一个**位置具有误导性**的错 —— 见 `docs/HANDOVER.md` 第四节）。
 * 画在 content 里还有一个好处：**跟着 `Surface` 的 scale 一起缩放**，放大时不会错位。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavItem(
    tab: ShellTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(BeiDims.PanelRadius)
    var focused by remember { mutableStateOf(false) }

    val labelColor = when {
        selected || focused -> BeiGlass.AccentBright
        else -> BeiGlass.TextSecondary
    }
    val subtitleColor = when {
        selected || focused -> BeiGlass.AccentBright.copy(alpha = 0.70f)
        else -> BeiGlass.TextMuted
    }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        // ⚠️ 容器色**全部留透明**，背景改由 content 里那一层自绘 —— 原因见下方注释。
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = labelColor,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = labelColor,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = labelColor
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = BeiMotion.FOCUS_SCALE),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
    ) {
        Box {
            // ---- 背景层：**只由 `focused` 驱动**
            //
            // 这里连着踩过两次坑，根因是同一个 —— **高亮的来源不一致**：
            // 1. 起初底色走 `Surface` 的 `focusedContainerColor`，它与真实焦点不同步，
            //    焦点走了旧项不撤；
            // 2. 改成自绘之后底色对了，却又写成 `if (focused || selected)` ——
            //    于是"选中"那一项也占一块底色，焦点移开后它**赖着不走**
            //    （真机表现："背景先动，但原来的背景和 | 线没跟着动，按了 OK 才移动过去"）。
            //
            // 结论：**导航栏里只允许存在一块高亮，它跟焦点走**。
            // 「当前在哪一页」不再用底色表达，改用**文字色（亮香槟）+ 加粗**。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(if (focused) BeiGlass.AccentFill else Color.Transparent)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 焦点指示条：同样**只跟焦点**（早年它跟的是"选中"，所以按上下键它不动）
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(if (focused) 28.dp else 0.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BeiGlass.Accent)
                )
                Spacer(modifier = Modifier.width(11.dp))

                TabGlyph(tab = tab, tint = labelColor)

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = tab.label,
                        color = labelColor,
                        fontSize = 17.sp,
                        // 加粗 = "当前就在这一页"，这是"选中"唯一保留的表达
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = tab.subtitle,
                        color = subtitleColor,
                        fontSize = BeiDims.TinySize
                    )
                }
            }

            // 描边叠层：**只有焦点画边框**（2dp 香槟）。
            // 与底色、竖线**同源**（都看 `focused`），所以三者永远一起动。
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
 * 导航项的小图标 —— 自绘，不引图标库。
 *
 * 之所以不引 `material-icons`：本项目依赖里没有它，为一个导航栏引入整套图标包不划算，
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
