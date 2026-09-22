package com.dualsub.tv.ui.player.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass

/**
 * 播放页菜单的**一行内容**。
 *
 * 菜单只负责「怎么显示、哪一行能被选中」，行为由调用方在 lambda 里给出。
 */
sealed interface MenuEntry {
    /** 小节标题，不可选中。 */
    data class Header(val text: String) : MenuEntry

    /** 单选项：按 OK **切换**（选中 / 取消选中）。 */
    data class Choice(
        val label: String,
        val detail: String? = null,
        val selected: Boolean,
        val onSelect: () -> Unit
    ) : MenuEntry

    /**
     * 数值项（字号、时间偏移、倍速这类）。
     *
     * 交互是**两段式**：先按 OK 把它**激活**，激活态下 ←/→ 直接调值，
     * 再按 OK 或返回键退出激活。这样连调十下只需要一次"进入"和一次"退出"，
     * 不必每调一下都按 OK —— 电视上按键次数就是成本。
     */
    data class Adjust(
        val label: String,
        val value: String,
        val onDecrease: () -> Unit,
        val onIncrease: () -> Unit,
        val detail: String? = null
    ) : MenuEntry

    /** 只读信息行，不可选中。 */
    data class Info(val label: String, val value: String) : MenuEntry

    /** 可点击的动作项（例如「选择外挂字幕文件…」「选择字幕轨…」）。 */
    data class Action(
        val label: String,
        val onClick: () -> Unit,
        val detail: String? = null
    ) : MenuEntry
}

/** 一级菜单的一项：标题 + 它对应的二级内容。 */
data class PlayerMenuGroup(
    val title: String,
    val entries: List<MenuEntry>
)

/** 某一层里可以停留的行下标（跳过 Header / Info）。 */
fun List<MenuEntry>.selectableIndices(): List<Int> = indices.filter { this[it].isSelectable() }

private fun MenuEntry.isSelectable(): Boolean = when (this) {
    is MenuEntry.Choice, is MenuEntry.Adjust, is MenuEntry.Action -> true
    is MenuEntry.Header, is MenuEntry.Info -> false
}

/**
 * 播放页的侧边菜单：**两级，从右往左展开**（一级分组在右，二级内容在左）。
 *
 * ```
 * 一级（分组）      二级（当前分组的项）
 *   字幕设置  ‹  ←── 当前 / 来源 / 显示样式 …
 *   音频设置         选择字幕轨…（条数多时改成弹页，见 [PlayerChoicePickerOverlay]）
 * ```
 *
 * 键盘语义（由调用方 `PlayerScreen` 实现，这里只负责画）：
 * - 上下：在当前层里移动；
 * - **左**：进入二级（数值项是"激活"）；
 * - **右**：回一级；数值项激活时是"增大"；
 * - **OK**：切换选中 / 执行动作 / 数值项激活与退出激活。
 *
 * 字号刻意压小（15/14/13/12/11sp 一路往下）：面板高度是固定的，字小了同一屏能多显示
 * 三四行 —— 真机反馈就是「显示的信息太少，还得来回滚」。
 *
 * ## 配色：压画面上的黑玻璃
 *
 * 面板底用 [BeiGlass.Panel] / [BeiGlass.PanelDeep]（黑 55% / 72%）而不是外壳那套白玻璃 ——
 * 背后是视频画面，规范里"叠白 ≤ 12%"的红线不允许白底半透明面板。
 * 高亮行**不再刷成实色**（旧版是整行铺满靛蓝）：规范禁止给面板上色，改为
 * **香槟 22% 底 + 2dp 香槟描边**（焦点态"底色 + 描边"两样）。
 *
 * 这里**不使用 Compose 的自动焦点系统**，而是完全由状态驱动：TV 上自动焦点在嵌套容器里
 * 很容易跑飞，显式状态更可控 —— 代价是列表不会自己滚动，必须像下面这样手动跟随。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerMenuOverlay(
    groups: List<PlayerMenuGroup>,
    selectedGroup: Int,
    selectedEntry: Int,
    focusOnEntries: Boolean,
    /** 当前二级项是否处于「激活调节」态（数值项激活后，←/→ 直接调值）。 */
    adjustActive: Boolean,
    title: String,
    modifier: Modifier = Modifier
) {
    val entries = groups.getOrNull(selectedGroup)?.entries.orEmpty()

    // 二级面板用 LazyColumn 而不是 Column + verticalScroll：
    // 后者不会跟着焦点滚动，光标移到屏幕外的项时菜单纹丝不动。
    val listState = rememberLazyListState()

    // 因为没有真实焦点，LazyColumn 不会自己滚 —— 选中项一变就手动滚过去。
    // 前面还有标题和一个间距占位，所以下标要加 OFFSET_BEFORE_ENTRIES。
    LaunchedEffect(selectedGroup, selectedEntry, focusOnEntries) {
        if (!focusOnEntries) return@LaunchedEffect
        runCatching {
            listState.animateScrollToItem((selectedEntry + OFFSET_BEFORE_ENTRIES).coerceAtLeast(0))
        }
    }

    val entryPanelShape = RoundedCornerShape(topStart = BeiDims.PanelRadius, bottomStart = BeiDims.PanelRadius)
    val groupPanelShape = RoundedCornerShape(topEnd = BeiDims.PanelRadius, bottomEnd = BeiDims.PanelRadius)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(BeiGlass.Scrim)
    ) {
        // 只剩两层，所以两块都能给得更宽（字小了 + 更宽 = 一屏信息量翻倍）。
        val availableWidth = maxWidth * 0.85f
        val entryPanelWidth = availableWidth * 0.58f
        val groupPanelWidth = availableWidth * 0.42f

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = BeiDims.ScreenStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ---- 二级面板（在一级菜单的左边，比一级矮一档）
            Box(
                modifier = Modifier
                    .width(entryPanelWidth)
                    .fillMaxHeight(0.72f)
                    .clip(entryPanelShape)
                    .background(BeiGlass.Panel)
                    .border(BeiDims.Border, BeiGlass.Border, entryPanelShape)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    item(key = "panelTitle") {
                        Text(
                            text = groups.getOrNull(selectedGroup)?.title ?: "",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = BeiGlass.TextPrimary
                        )
                    }

                    item(key = "panelGap") {
                        Box(modifier = Modifier.padding(top = 6.dp))
                    }

                    itemsIndexed(entries) { index, entry ->
                        val isCurrent = focusOnEntries && index == selectedEntry
                        MenuEntryRow(
                            entry = entry,
                            highlighted = isCurrent,
                            adjustActive = isCurrent && adjustActive
                        )
                    }
                }
            }

            // ---- 一级菜单（最右）
            //
            // **高度跟着内容走**（原先固定 `fillMaxHeight(0.80f)`）：分组本来就只有几个，
            // 固定高度会在最后一项下面留一大片空白。外层 Row 已经是 `fillMaxHeight` +
            // `CenterVertically`，所以这里不设高度就自然垂直居中。
            //
            // 二级面板**相反** —— 它的条目可能几十条（内嵌字幕轨 26 条），必须固定高度 + 滚动。
            //
            // ⚠️ 若将来分组数量明显变多（> 8），这里要补一个 `heightIn(max = …)` 上限。
            Column(
                modifier = Modifier
                    .width(groupPanelWidth)
                    .clip(groupPanelShape)
                    .background(BeiGlass.PanelDeep)
                    .border(BeiDims.Border, BeiGlass.Border, groupPanelShape)
                    .padding(horizontal = 10.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = BeiDims.CaptionSize,
                    color = BeiGlass.TextSecondary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                    maxLines = 2
                )

                groups.forEachIndexed { index, group ->
                    val isCurrent = index == selectedGroup
                    val dimmed = focusOnEntries
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                when {
                                    // 焦点已经进二级面板时，一级**不留任何底色** —— 高亮只属于
                                    // 用户此刻真正在操作的那一层（真机反馈："焦点到二级菜单时，
                                    // 一级菜单对应的位置还是高亮"）。分组归属交给下面的亮香槟
                                    // 文字与「‹」箭头表达，不必再占一块底。
                                    isCurrent && dimmed -> Color.Transparent
                                    isCurrent -> BeiGlass.AccentFill
                                    else -> Color.Transparent
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = group.title,
                                fontSize = 14.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isCurrent -> BeiGlass.AccentBright
                                    else -> BeiGlass.TextSecondary
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (isCurrent) {
                                Text(
                                    text = "‹",
                                    fontSize = 14.sp,
                                    color = BeiGlass.AccentBright
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuEntryRow(
    entry: MenuEntry,
    highlighted: Boolean,
    adjustActive: Boolean
) {
    /** 高亮行的形状：底色 + 2dp 香槟描边（焦点态"底色 + 描边"两样，不刷实色）。 */
    val highlightShape = RoundedCornerShape(8.dp)

    when (entry) {
        is MenuEntry.Header -> {
            Text(
                text = entry.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = BeiGlass.TextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 1.dp)
            )
        }

        is MenuEntry.Info -> {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    text = entry.label,
                    fontSize = 12.sp,
                    color = BeiGlass.TextMuted,
                    modifier = Modifier.width(72.dp)
                )
                Text(text = entry.value, fontSize = 12.sp, color = BeiGlass.TextPrimary)
            }
        }

        is MenuEntry.Choice -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(highlightShape)
                .background(if (highlighted) BeiGlass.AccentFillStrong else Color.Transparent)
                .then(
                    if (highlighted) {
                        Modifier.border(BeiDims.BorderFocus, BeiGlass.AccentBorderStrong, highlightShape)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dotColor = if (highlighted) BeiGlass.AccentBright else BeiGlass.TextSecondary
            Canvas(modifier = Modifier.size(8.dp)) {
                val r = this.size.minDimension / 2f
                if (entry.selected) {
                    drawCircle(color = dotColor, radius = r)
                } else {
                    drawCircle(
                        color = dotColor,
                        radius = r - 1.dp.toPx(),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
            Box(modifier = Modifier.width(7.dp))
            Text(
                text = entry.label,
                fontSize = 13.sp,
                color = if (highlighted) BeiGlass.AccentBright else BeiGlass.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            entry.detail?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    color = if (highlighted) {
                        BeiGlass.AccentBright.copy(alpha = 0.7f)
                    } else {
                        BeiGlass.TextMuted
                    }
                )
            }
        }

        is MenuEntry.Adjust -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(highlightShape)
                .background(
                    when {
                        adjustActive -> BeiGlass.AccentFillStrong
                        highlighted -> BeiGlass.AccentFill
                        else -> Color.Transparent
                    }
                )
                .then(
                    if (adjustActive || highlighted) {
                        Modifier.border(
                            BeiDims.BorderFocus,
                            if (adjustActive) BeiGlass.AccentBorderStrong else BeiGlass.AccentBorder,
                            highlightShape
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.label,
                fontSize = 13.sp,
                color = if (adjustActive) BeiGlass.AccentBright else BeiGlass.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (adjustActive) {
                Text(
                    text = "调节中",
                    fontSize = 12.sp,
                    color = BeiGlass.AccentBright
                )
                Box(modifier = Modifier.width(6.dp))
            }
            Text(
                text = if (adjustActive) "◀ ${entry.value} ▶" else "‹ ${entry.value} ›",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = BeiGlass.AccentBright
            )
        }

        is MenuEntry.Action -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(highlightShape)
                .background(if (highlighted) BeiGlass.AccentFillStrong else Color.Transparent)
                .then(
                    if (highlighted) {
                        Modifier.border(BeiDims.BorderFocus, BeiGlass.AccentBorderStrong, highlightShape)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.label,
                fontSize = 13.sp,
                color = if (highlighted) BeiGlass.AccentBright else BeiGlass.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            entry.detail?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    color = if (highlighted) {
                        BeiGlass.AccentBright.copy(alpha = 0.7f)
                    } else {
                        BeiGlass.TextMuted
                    }
                )
            }
            if (highlighted) {
                Text(text = "OK", fontSize = 11.sp, color = BeiGlass.AccentBright.copy(alpha = 0.7f))
            }
        }
    }
}

/** 二级面板里，第一条 entry 之前还有标题与一个间距占位。 */
private const val OFFSET_BEFORE_ENTRIES = 2
