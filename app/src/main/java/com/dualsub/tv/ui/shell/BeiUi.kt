package com.dualsub.tv.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass
import com.dualsub.tv.ui.theme.BeiMotion

/**
 * 外壳里各个页面复用的基础件。
 *
 * 单独放一个文件的原因：媒体库、网络、设置、AI 字幕几个页面要用**同一套卡片与控件**，
 * 各写一份迟早会走样；而 [AppShell] 只负责导航，不该往它里面塞这些内容件。
 *
 * ## 视觉（`docs/UI_STYLE_REFERENCE.md`）
 *
 * 全部走**无色玻璃**：面板底是白 6%（焦点白 10%），描边 1dp 白 15%（焦点 2dp），
 * 尺寸走 [BeiDims] 的电视档位。
 *
 * 三个刻意的取舍：
 * - **没有投影**（早先是 `Modifier.shadow` 抬白卡）。玻璃的层级靠描边与底色表达，
 *   而 `shadow` 是性能红线（动 shadow 会触发整块重绘），静态的也不该用 ——
 *   浅色外壳时代白卡压在灰底上才需要投影，深色玻璃上没有"浮起来"这回事。
 * - **可点击件**用 `androidx.tv.material3.Surface(onClick = …)`：`shape` / `colors` / `scale`
 *   都走 [ClickableSurfaceDefaults]，能把常态 / 焦点 / 按下三态一次说清。
 * - **描边不交给 tv-material3**：它的 `border` 参数要的是自家的 `androidx.tv.material3.Border`
 *   类型（不是 `androidx.compose.foundation.BorderStroke`），传错会连带报一个**位置具有
 *   误导性**的 `@Composable` 错误（见 `docs/HANDOVER.md` 第四节）。这里统一用 foundation 的
 *   `Modifier.border`，并且把描边**叠在 `Surface` 的 content 里**（用 `matchParentSize()`
 *   与内容同尺寸）—— 这样它会跟着 `ClickableSurfaceDefaults.scale` 的放大一起缩放，
 *   不会在焦点态错位。
 */

/** 页面大标题 + 一行说明。外壳已提供导航，页面自己只需要标题。 */
@Composable
fun BeiPageHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = BeiGlass.TextPrimary,
            fontSize = BeiDims.TitleSize,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = BeiGlass.TextSecondary,
            fontSize = BeiDims.BodySize
        )
    }
}

/** 内容区的小节标题（「已保存」「扫描结果」这种）。 */
@Composable
fun BeiSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = BeiGlass.TextSecondary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 6.dp)
    )
}

/** 卡片统一的圆角 —— 所有卡片走同一个常量，避免各页各圆角。 */
private val CardShape = RoundedCornerShape(BeiDims.CardRadius)

/**
 * 可点击的玻璃卡片。
 *
 * 焦点态是**白 10% 底 + 2dp 白 30% 描边 + 微放大**（三件套）—— 遥控器在网格里移动时
 * 一眼看得出焦点在哪，文字不变色、不反色。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BeiCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(
        horizontal = BeiDims.CardPaddingH,
        vertical = BeiDims.CardPaddingV
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = BeiGlass.Glass,
            contentColor = BeiGlass.TextPrimary,
            focusedContainerColor = BeiGlass.GlassFocused,
            focusedContentColor = BeiGlass.TextPrimary,
            pressedContainerColor = BeiGlass.GlassBright,
            pressedContentColor = BeiGlass.TextPrimary
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = BeiMotion.FOCUS_SCALE)
    ) {
        Box {
            Column(
                modifier = Modifier.padding(padding),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = if (focused) BeiDims.BorderFocus else BeiDims.Border,
                        color = if (focused) BeiGlass.BorderBright else BeiGlass.Border,
                        shape = CardShape
                    )
            )
        }
    }
}

/**
 * 「大图标卡」：**居中一个大图标 + 名称（+ 一行小字状态）**。
 *
 * 用它的理由：来源 / 入口这类卡片，用户认的是**图标**，不是那一行行文字说明。
 * 图标放大居中之后（[BeiDims.IconSourceCard] 56dp），隔三米扫一眼就能找到"网盘"在哪；
 * 状态留在名称下方一行小字里，需要时看得到，不需要时不抢戏。
 */
@Composable
fun BeiIconCard(
    onClick: () -> Unit,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    BeiCard(
        onClick = onClick,
        modifier = modifier,
        padding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            icon()
            Text(
                text = title,
                color = BeiGlass.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 状态永远占一行（即使是空串）：卡片高度一致，网格才不会参差。
            Text(
                text = subtitle.orEmpty(),
                color = BeiGlass.TextSecondary,
                fontSize = BeiDims.CaptionSize,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 静态卡片：承载设置项、说明等**固定内容**，不参与遥控器焦点。 */
@Composable
fun BeiStaticCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(BeiGlass.Glass)
            .border(BeiDims.Border, BeiGlass.Border, CardShape)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/**
 * 「胶囊」按钮：卡片内的次级操作（进入 / 编辑 / 删除）、页面右上角的动作都用它。
 *
 * 常态是**香槟 15% 底 + 亮香槟字**：一眼看得出这是可点的动作，压在玻璃卡上也不显脏。
 * 焦点态把底提到 22%、描边提到 55%（规范里"主操作填充 alpha 0.15–0.22"那一档）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BeiPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = BeiGlass.AccentFill,
            contentColor = BeiGlass.AccentBright,
            focusedContainerColor = BeiGlass.AccentFillStrong,
            focusedContentColor = BeiGlass.AccentBright,
            pressedContainerColor = BeiGlass.AccentFillStrong,
            pressedContentColor = BeiGlass.AccentBright
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = BeiMotion.FOCUS_SCALE)
    ) {
        Box {
            Text(
                text = label,
                fontSize = BeiDims.BodySize,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = if (focused) BeiDims.BorderFocus else BeiDims.Border,
                        color = if (focused) BeiGlass.AccentBorderStrong else BeiGlass.AccentBorder,
                        shape = shape
                    )
            )
        }
    }
}

/**
 * 一枚「选项胶囊」：**选中的是香槟 15% 底 + 香槟描边 + ✓**，没选中的是无色玻璃。
 *
 * 这是设置页的基本单位：一行标签 + 一排这样的胶囊，
 * 比「－ 值 ＋」步进器少按好几下遥控器 —— 电视上每多按一次键都是成本。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) BeiGlass.AccentFill else BeiGlass.Glass,
            contentColor = if (selected) BeiGlass.AccentBright else BeiGlass.TextSecondary,
            focusedContainerColor = if (selected) BeiGlass.AccentFillStrong else BeiGlass.GlassFocused,
            focusedContentColor = BeiGlass.AccentBright,
            pressedContainerColor = BeiGlass.AccentFillStrong,
            pressedContentColor = BeiGlass.AccentBright
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = BeiMotion.FOCUS_SCALE)
    ) {
        Box {
            Text(
                text = if (selected) "✓ $text" else text,
                fontSize = BeiDims.BodySize,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = if (focused) BeiDims.BorderFocus else BeiDims.Border,
                        color = when {
                            focused -> BeiGlass.AccentBorderStrong
                            selected -> BeiGlass.AccentBorder
                            else -> BeiGlass.Border
                        },
                        shape = shape
                    )
            )
        }
    }
}

/**
 * 「名称 + 选项胶囊」行：设置页的标准排版。
 *
 * [selectedIndex] 传 -1 表示当前值不在这几个档位里（界面会一个都不选中）——
 * 这种情况应当尽量少：调用方要么给全档位，要么用 `nearestIndex` 兜到最近的一档。
 */
@Composable
fun BeiChoiceRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    detail: String? = null,
    labelWidth: Int = 116
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = label,
                color = BeiGlass.TextPrimary,
                fontSize = 15.sp,
                modifier = Modifier.width(labelWidth.dp)
            )
            options.forEachIndexed { index, option ->
                ChoiceChip(
                    text = option,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) }
                )
            }
        }
        detail?.let {
            Text(
                text = it,
                color = BeiGlass.TextMuted,
                fontSize = BeiDims.TinySize,
                modifier = Modifier.padding(start = labelWidth.dp)
            )
        }
    }
}

/** 网络页的几类来源，用来选图标。 */
enum class SourceKind { Smb, Nas, CloudDrive, WebDav, Dlna, Quark, Baidu, Ali, Scan }

/**
 * 来源图标：**玻璃圆角方块 + 香槟金符号**。
 *
 * ## 为什么不再是「品牌彩色渐变底」
 *
 * 早先每类来源一组品牌渐变色（SMB 蓝 / WebDAV 绿 / DLNA 紫 …）。新规范的两条硬纪律
 * 与此冲突：**面板不上色**（颜色属于背景），且**强调色只能有一个**。所以底改成无色玻璃
 * （白 6%）、符号用唯一的强调色香槟金。
 *
 * 识别度靠**符号形状**而不是颜色 —— 规范也是这么要求的（"大图标卡：一眼能认出这是网盘 /
 * 这是 SMB"）。这样还顺带解决了"7 种品牌色里混进一个紫粉"的观感问题。
 *
 * 为什么不用 emoji 也不用图标库：不同电视对字形覆盖不一样，缺字就是方框（本项目刚在字幕上
 * 踩过这个坑）；而图标库为一个页面引入一整套依赖不值当。这里全是最简单的几何，
 * 香槟线条压在玻璃底上，任何设备画出来都一样。
 */
@Composable
fun SourceIcon(kind: SourceKind, modifier: Modifier = Modifier, size: Dp = BeiDims.IconSourceCard) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(BeiGlass.Glass)
            .border(BeiDims.Border, BeiGlass.Border, shape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.52f)) {
            val ink = BeiGlass.Accent
            val w = this.size.width
            val h = this.size.height
            val stroke = w * 0.10f
            // ⚠️ 各图标的**外接尺寸统一到约 0.10–0.90**（0.80 的方形包围盒）——
            // 否则同一排卡片里有的"显大"有的"显小"（真机反馈过：WebDAV 的云偏小、
            // 网盘的实心圆偏大）。描边粗细也统一走同一个 `stroke`。
            when (kind) {
                // 云 + 中心实心点：云盘（夸克 / 百度 / 阿里）。与 WebDAV 的纯云形只差中间
                // 那一点 —— 两者的区别正是「云上有他的一份内容」vs「一条连到某台机器的协议」。
                SourceKind.CloudDrive -> {
                    drawCircle(
                        color = ink, radius = w * 0.22f,
                        center = Offset(w * 0.30f, h * 0.58f), style = Stroke(stroke)
                    )
                    drawCircle(
                        color = ink, radius = w * 0.28f,
                        center = Offset(w * 0.52f, h * 0.46f), style = Stroke(stroke)
                    )
                    drawCircle(
                        color = ink, radius = w * 0.20f,
                        center = Offset(w * 0.74f, h * 0.58f), style = Stroke(stroke)
                    )
                    drawLine(ink, Offset(w * 0.22f, h * 0.74f), Offset(w * 0.82f, h * 0.74f), stroke)
                    drawCircle(ink, radius = w * 0.09f, center = Offset(w * 0.52f, h * 0.50f))
                }
                // 上下两块盘位：NAS（飞牛 / 群晖 / 威联通 / 绿联 共用这一个）。
                // 刻意做成"双格箱体"而不是再画一台屏幕 —— SMB 那张卡就是"屏幕 + 底座"，
                // 两者在同一页挨着，形状必须一眼分得开。
                SourceKind.Nas -> {
                    drawRoundRect(
                        color = ink,
                        topLeft = Offset(w * 0.16f, h * 0.16f),
                        size = Size(w * 0.68f, h * 0.30f),
                        cornerRadius = CornerRadius(w * 0.08f),
                        style = Stroke(width = stroke)
                    )
                    drawRoundRect(
                        color = ink,
                        topLeft = Offset(w * 0.16f, h * 0.54f),
                        size = Size(w * 0.68f, h * 0.30f),
                        cornerRadius = CornerRadius(w * 0.08f),
                        style = Stroke(width = stroke)
                    )
                }
                // 显示器 + 底座：局域网里的一台机器
                SourceKind.Smb -> {
                    drawRoundRect(
                        color = ink,
                        topLeft = Offset(w * 0.12f, h * 0.16f),
                        size = Size(w * 0.76f, h * 0.50f),
                        cornerRadius = CornerRadius(w * 0.10f),
                        style = Stroke(width = stroke)
                    )
                    drawLine(ink, Offset(w * 0.50f, h * 0.66f), Offset(w * 0.50f, h * 0.80f), stroke)
                    drawLine(ink, Offset(w * 0.32f, h * 0.82f), Offset(w * 0.68f, h * 0.82f), stroke)
                }
                // 云：WebDAV
                SourceKind.WebDav -> {
                    drawCircle(
                        color = ink, radius = w * 0.22f,
                        center = Offset(w * 0.30f, h * 0.58f), style = Stroke(stroke)
                    )
                    drawCircle(
                        color = ink, radius = w * 0.28f,
                        center = Offset(w * 0.52f, h * 0.46f), style = Stroke(stroke)
                    )
                    drawCircle(
                        color = ink, radius = w * 0.20f,
                        center = Offset(w * 0.74f, h * 0.58f), style = Stroke(stroke)
                    )
                    drawLine(ink, Offset(w * 0.22f, h * 0.74f), Offset(w * 0.82f, h * 0.74f), stroke)
                }
                // 同心弧：DLNA 的广播发现
                SourceKind.Dlna -> {
                    drawCircle(ink, radius = w * 0.08f, center = Offset(w * 0.30f, h * 0.70f))
                    drawArc(
                        color = ink, startAngle = -75f, sweepAngle = 75f, useCenter = false,
                        topLeft = Offset(w * 0.08f, h * 0.30f), size = Size(w * 0.44f, h * 0.44f),
                        style = Stroke(width = stroke)
                    )
                    drawArc(
                        color = ink, startAngle = -75f, sweepAngle = 75f, useCenter = false,
                        topLeft = Offset(w * 0.08f, h * 0.10f), size = Size(w * 0.84f, h * 0.84f),
                        style = Stroke(width = stroke)
                    )
                }
                // 网盘三家：外圈 + 内芯（靠符号与名称区分，不靠颜色）
                SourceKind.Quark, SourceKind.Baidu, SourceKind.Ali -> {
                    drawCircle(ink, radius = w * 0.38f, style = Stroke(width = stroke))
                    drawCircle(ink, radius = w * 0.13f)
                }
                // 雷达扫描
                SourceKind.Scan -> {
                    drawCircle(ink, radius = w * 0.38f, style = Stroke(width = stroke))
                    drawLine(ink, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.50f, h * 0.18f), stroke)
                    drawLine(ink, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.78f, h * 0.64f), stroke)
                }
            }
        }
    }
}
