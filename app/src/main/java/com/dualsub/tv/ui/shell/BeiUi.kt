package com.dualsub.tv.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
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
    onFocusedChange: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged {
            focused = it.isFocused
            // 焦点状态只有这里拿得到（在 Surface 内部），而卡片内容偶尔要跟着它做事
            // —— `BeiIconCard` 要在聚焦时才把描述文字滚出来。所以往外递一份。
            onFocusedChange?.invoke(it.isFocused)
        },
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
 * 「大图标卡」：**图标 + 一行文字**。
 *
 * 那行文字平时是**标题**（"这张卡叫什么"），聚焦时换成**描述**并跑马灯
 * （"它到底是什么"）—— 两者**字体、字号、字重、颜色完全一致**。
 *
 * ## 为什么描述和标题共用一行
 *
 * 第一版把描述做成"永远占一行、平时空白"：一排卡片是齐的，但那一行 16dp 白养着。
 * 第二版让它浮在卡片顶部 —— 又得在顶上预留一条内边距，同样白养。
 *
 * 现在直接把那一行**复用**掉：同一行、同一套字体，聚焦时只是"内容换了"。好处有三 ——
 * 卡片比前两版都矮（省下的高度给了图标，24 → 28dp）；不会出现"聚焦时卡片长高一截"
 * 的抖动；而描述**必须**用主标题的字号 —— 用小一号的字，聚焦瞬间这张卡会显得矮了一截，
 * 像换了一张卡，用同一套字体才只是"这一行的内容换了"。
 *
 * 信息没丢，只是从"一直摆着"改成"点到才说"：三米外看的是图标，
 * 真要确认细节时，遥控器已经停在它上面了。
 *
 * **尺寸按 120dp 宽的卡片配**（见 `NetworkRows.SourceCardWidth`）：内边距与字距都收到
 * 原来的一半左右；**字号保持在电视的舒适下限**（`BodySize` = 13sp），再往下就不是
 * "卡片小"，而是三米外读不出字了。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BeiIconCard(
    onClick: () -> Unit,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    BeiCard(
        onClick = onClick,
        modifier = modifier,
        padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        onFocusedChange = { focused = it }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon()
            // 标题与描述**共用同一行**：平时显示标题，聚焦时那一行换成描述跑马灯。
            //
            // 两者**字体、字号、字重、行高完全一致**（`BodySize` + SemiBold、同为 TextPrimary）。
            // 描述若用小一号的字，聚焦瞬间这张卡会"矮一截"，看着像换了一张卡；
            // 用主标题的字号，视觉上才只是"这一行的内容换了"。
            val showingSubtitle = focused && !subtitle.isNullOrBlank()
            Text(
                text = if (showingSubtitle) subtitle!! else title,
                color = BeiGlass.TextPrimary,
                fontSize = BeiDims.BodySize,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (showingSubtitle) Modifier.basicMarquee() else Modifier)
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
            .border(BeiDims.Border, BeiGlass.Border, CardShape)
            .background(BeiGlass.Glass)
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(50)
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = BeiGlass.AccentFill,
            contentColor = BeiGlass.AccentBright,
            focusedContainerColor = BeiGlass.AccentFillStrong,
            focusedContentColor = BeiGlass.AccentBright,
            pressedContainerColor = BeiGlass.AccentFillStrong,
            pressedContentColor = BeiGlass.AccentBright,
            disabledContainerColor = BeiGlass.AccentFill.copy(alpha = 0.30f),
            disabledContentColor = BeiGlass.AccentBright.copy(alpha = 0.40f)
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

/**
 * 网络页的几类来源，用来选图标。
 *
 * [Feiniu] / [Synology] / [Qnap] / [Ugreen] 是 NAS 四家的**品牌轮廓**：
 * 「NAS」入口下要选品牌，品牌卡得一眼认得出是哪一家，所以四家各有一个图标。
 * 注意**图标种类 ≠ 数据模型** —— `RemoteType` 仍然只有一个 `NAS`
 * （四家的接入协议完全一样，只是显示不同，见 `network/nas/NasVendor.kt`）。
 */
enum class SourceKind {
    Smb, Nas, CloudDrive, WebDav, Dlna,
    Quark, Baidu, Ali,
    Feiniu, Synology, Qnap, Ugreen,
    Scan
}

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
                // ---- 网盘三家：各自一个品牌轮廓。
                // ⚠️ 三家**都不画云** —— 上面已经有一个 WebDAV 的云，而百度 / 阿里的标志
                // 核心本来就是云，全画成云就分不出来了。改用各家**非云**的关键部件：
                // 夸克的折角、百度的熊掌、阿里的折面三角。
                SourceKind.Quark -> {
                    drawCircle(ink, radius = w * 0.38f, style = Stroke(width = stroke))
                    drawPath(
                        Path().apply {
                            moveTo(w * 0.30f, h * 0.62f)
                            lineTo(w * 0.50f, h * 0.36f)
                            lineTo(w * 0.70f, h * 0.62f)
                        },
                        ink, style = Stroke(width = stroke)
                    )
                }
                // 百度网盘：熊掌 —— 一个掌垫 + 三趾。三只圆点在任何尺寸下都认得出。
                SourceKind.Baidu -> {
                    drawCircle(
                        color = ink, radius = w * 0.20f,
                        center = Offset(w * 0.50f, h * 0.70f), style = Stroke(width = stroke)
                    )
                    drawCircle(
                        color = ink, radius = w * 0.09f,
                        center = Offset(w * 0.22f, h * 0.34f), style = Stroke(width = stroke)
                    )
                    drawCircle(
                        color = ink, radius = w * 0.09f,
                        center = Offset(w * 0.50f, h * 0.24f), style = Stroke(width = stroke)
                    )
                    drawCircle(
                        color = ink, radius = w * 0.09f,
                        center = Offset(w * 0.78f, h * 0.34f), style = Stroke(width = stroke)
                    )
                }
                // 阿里云盘：正三角 + 中线（它的折面感），同样避开云。
                SourceKind.Ali -> {
                    drawPath(
                        Path().apply {
                            moveTo(w * 0.50f, h * 0.14f)
                            lineTo(w * 0.88f, h * 0.80f)
                            lineTo(w * 0.12f, h * 0.80f)
                            close()
                        },
                        ink, style = Stroke(width = stroke)
                    )
                    drawLine(ink, Offset(w * 0.50f, h * 0.14f), Offset(w * 0.50f, h * 0.80f), stroke)
                }
                // ---- NAS 四家：品牌轮廓。
                // 「NAS」子页那一排是要**选你是哪一家**，四家共用上面那个双格箱体不够用。
                // 四家的标志形状差异够大，画成线条仍然分得开。
                //
                // 飞牛 fnOS：牛头 —— 一对上翘的角 + 圆角脸。四家里只有它是动物标，
                // 也正因为如此，其余三家一律走"器物/字母"，不跟它抢。
                SourceKind.Feiniu -> {
                    drawPath(
                        Path().apply {
                            moveTo(w * 0.28f, h * 0.46f)
                            lineTo(w * 0.72f, h * 0.46f)
                            lineTo(w * 0.64f, h * 0.86f)
                            lineTo(w * 0.36f, h * 0.86f)
                            close()
                        },
                        ink, style = Stroke(width = stroke)
                    )
                    drawPath(
                        Path().apply {
                            moveTo(w * 0.28f, h * 0.58f)
                            quadraticTo(w * 0.14f, h * 0.46f, w * 0.18f, h * 0.18f)
                        },
                        ink, style = Stroke(width = stroke)
                    )
                    drawPath(
                        Path().apply {
                            moveTo(w * 0.72f, h * 0.58f)
                            quadraticTo(w * 0.86f, h * 0.46f, w * 0.82f, h * 0.18f)
                        },
                        ink, style = Stroke(width = stroke)
                    )
                }
                // 群晖 DSM：圆环开一个口 + 中心实心方块。
                SourceKind.Synology -> {
                    drawArc(
                        color = ink, startAngle = 40f, sweepAngle = 280f, useCenter = false,
                        topLeft = Offset(w * 0.10f, h * 0.10f), size = Size(w * 0.80f, h * 0.80f),
                        style = Stroke(width = stroke)
                    )
                    drawRect(
                        color = ink,
                        topLeft = Offset(w * 0.38f, h * 0.38f),
                        size = Size(w * 0.24f, h * 0.24f)
                    )
                }
                // 威联通 QTS：一个 Q（圆 + 右下斜尾）—— QNAP 的首字母，够简洁。
                SourceKind.Qnap -> {
                    drawCircle(
                        color = ink, radius = w * 0.28f,
                        center = Offset(w * 0.44f, h * 0.44f), style = Stroke(width = stroke)
                    )
                    drawLine(ink, Offset(w * 0.56f, h * 0.58f), Offset(w * 0.82f, h * 0.86f), stroke)
                }
                // 绿联：一个 U（它的名字首字母），底弧收圆。
                SourceKind.Ugreen -> {
                    drawPath(
                        Path().apply {
                            moveTo(w * 0.24f, h * 0.14f)
                            lineTo(w * 0.24f, h * 0.60f)
                            quadraticTo(w * 0.50f, h * 0.92f, w * 0.76f, h * 0.60f)
                            lineTo(w * 0.76f, h * 0.14f)
                        },
                        ink, style = Stroke(width = stroke)
                    )
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

/**
 * 通用确认框：**半透明遮罩 + 居中玻璃面板 + 两个可聚焦按钮**。
 *
 * ## 与 `PlayerExitConfirmOverlay` 的关系
 *
 * 长相一致，但**不能共用** —— 那个是给播放页的「显示态」浮层：播放页整屏是一个自管按键的
 * `focusable()`，按钮不在 Compose 焦点系统里，高亮靠一个布尔参数画出来。
 * 外壳页面本来就是焦点驱动的，所以这一版用**真正的 `BeiPillButton`**，
 * 遥控器自己就能在两项之间移动。
 *
 * 两条纪律在这里同样成立：
 * - **默认焦点落在「取消」** —— 破坏性操作不该是落点；
 * - **返回键等于取消**（由调用方的 `BackHandler` 处理），不必去够按钮。
 *
 * 面板底色用 `Glass`（外壳系的白玻璃），**不是**播放页那套 `Panel` 黑底 ——
 * 后者是为了压在视频画面上（见 `UI_STYLE_REFERENCE.md` §四 的红线），
 * 外壳底下本来就是我们自己铺的夜景，用白玻璃才对。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BeiConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(BeiDims.CardRadius)
    val cancelFocus = remember { FocusRequester() }

    // 焦点必须抢过来：确认框是叠在页面上的，不抢的话用户按 OK 会打到底层那个按钮上。
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BeiGlass.Scrim),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(shape)
                .background(BeiGlass.Glass)
                .border(BeiDims.Border, BeiGlass.Border, shape)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                color = BeiGlass.TextPrimary,
                fontSize = BeiDims.CardTitleSize,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                color = BeiGlass.TextSecondary,
                fontSize = BeiDims.BodySize,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                BeiPillButton(label = confirmLabel, onClick = onConfirm)
                BeiPillButton(
                    label = "取消",
                    onClick = onCancel,
                    modifier = Modifier.focusRequester(cancelFocus)
                )
            }
        }
    }
}
