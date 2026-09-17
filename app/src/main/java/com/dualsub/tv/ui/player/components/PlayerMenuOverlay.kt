package com.dualsub.tv.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.ui.theme.BeiPalette

/**
 * 播放页侧边菜单的**一行内容**。
 *
 * 二级面板的内容差异很大（选项列表、增减数值、只读信息），所以用这个密封接口统一描述，
 * 菜单组件只负责「怎么显示、哪一行能被选中」，具体行为由调用方在 lambda 里给出。
 */
sealed interface MenuEntry {
    /** 小节标题，不可选中。 */
    data class Header(val text: String) : MenuEntry

    /** 单选项：例如某条音轨、某个倍速、某个显示比例。 */
    data class Choice(
        val label: String,
        val detail: String? = null,
        val selected: Boolean,
        val onSelect: () -> Unit
    ) : MenuEntry

    /** 可增减的数值项。 */
    data class Stepper(
        val label: String,
        val value: String,
        val onDecrease: () -> Unit,
        val onIncrease: () -> Unit
    ) : MenuEntry

    /** 只读信息行，不可选中。 */
    data class Info(val label: String, val value: String) : MenuEntry

    /** 可点击的动作项（例如「选择外挂字幕文件…」）。 */
    data class Action(val label: String, val onClick: () -> Unit) : MenuEntry
}

/** 一级菜单的一项：标题 + 它对应的二级内容。 */
data class PlayerMenuGroup(
    val title: String,
    val entries: List<MenuEntry>
)

/** 二级菜单里可以停留的行下标（跳过 Header / Info）。 */
fun List<MenuEntry>.selectableIndices(): List<Int> = indices.filter { this[it].isSelectable() }

private fun MenuEntry.isSelectable(): Boolean = when (this) {
    is MenuEntry.Choice, is MenuEntry.Stepper, is MenuEntry.Action -> true
    is MenuEntry.Header, is MenuEntry.Info -> false
}

/**
 * 播放页的侧边菜单。
 *
 * 布局是**一级在右、二级在左**：遥控器在一级菜单里上下移动，二级面板就跟着切到对应分组，
 * 视觉上是「从一级项向左弹出」。方向键按「空间位置」理解 —— 左键进入二级、右键退回一级，
 * 这个语义由调用方（`PlayerScreen`）处理。
 *
 * 焦点分两层，由调用方通过 [focusOnEntries] 控制：
 * - false：焦点在一级菜单，上下键切换分组；
 * - true ：焦点在二级面板，上下键在当前分组的可选项之间移动。
 *
 * 这里**不使用 Compose 的自动焦点系统**，而是完全由状态驱动（[selectedGroup] /
 * [selectedEntry] / [focusOnEntries]）。TV 上自动焦点在嵌套容器里很容易跑飞，
 * 显式状态更可控 —— 但代价是**列表不会自己滚动**，必须像下面这样手动跟随。
 *
 * 配色统一走 [BeiPalette]（当贝风：深蓝面板 + 亮黄高亮）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerMenuOverlay(
    groups: List<PlayerMenuGroup>,
    selectedGroup: Int,
    selectedEntry: Int,
    focusOnEntries: Boolean,
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BeiPalette.Scrim)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ---- 二级面板（在一级菜单的左边）
            Box(
                modifier = Modifier
                    .width(ENTRY_PANEL_WIDTH)
                    .fillMaxHeight(0.82f)
                    .background(BeiPalette.Panel, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item(key = "panelTitle") {
                        Text(
                            text = groups.getOrNull(selectedGroup)?.title ?: "",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = BeiPalette.TextPrimary
                        )
                    }

                    item(key = "panelGap") {
                        Box(modifier = Modifier.padding(top = 8.dp))
                    }

                    itemsIndexed(entries) { index, entry ->
                        val isCurrent = focusOnEntries && index == selectedEntry
                        MenuEntryRow(entry = entry, highlighted = isCurrent)
                    }
                }
            }

            // ---- 一级菜单（最右）
            Column(
                modifier = Modifier
                    .width(GROUP_PANEL_WIDTH)
                    .fillMaxHeight(0.82f)
                    .background(BeiPalette.PanelDeep, RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp))
                    .padding(horizontal = 12.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = BeiPalette.TextMuted,
                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                )

                groups.forEachIndexed { index, group ->
                    val isCurrent = index == selectedGroup
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                when {
                                    // 焦点在二级时，一级当前项只做浅色底，避免「两个高亮」抢注意力
                                    isCurrent && focusOnEntries -> BeiPalette.AccentDim
                                    isCurrent -> BeiPalette.Accent
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 11.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = group.title,
                                fontSize = 16.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isCurrent && focusOnEntries -> BeiPalette.Accent
                                    isCurrent -> BeiPalette.OnAccent
                                    else -> BeiPalette.TextSecondary
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (isCurrent) {
                                Text(
                                    text = "‹",
                                    fontSize = 16.sp,
                                    color = if (focusOnEntries) BeiPalette.Accent else BeiPalette.OnAccent
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
private fun MenuEntryRow(entry: MenuEntry, highlighted: Boolean) {
    when (entry) {
        is MenuEntry.Header -> {
            Text(
                text = entry.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = BeiPalette.TextSecondary,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
            )
        }

        is MenuEntry.Info -> {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    text = entry.label,
                    fontSize = 13.sp,
                    color = BeiPalette.TextMuted,
                    modifier = Modifier.width(76.dp)
                )
                Text(text = entry.value, fontSize = 13.sp, color = BeiPalette.TextPrimary)
            }
        }

        is MenuEntry.Choice -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (highlighted) BeiPalette.Accent else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (entry.selected) "●" else "○",
                    fontSize = 13.sp,
                    color = if (highlighted) BeiPalette.OnAccent else BeiPalette.TextSecondary
                )
                Box(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.label,
                    fontSize = 14.sp,
                    color = if (highlighted) BeiPalette.OnAccent else BeiPalette.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                entry.detail?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = if (highlighted) BeiPalette.OnAccent.copy(alpha = 0.6f) else BeiPalette.TextMuted
                    )
                }
            }
        }

        is MenuEntry.Stepper -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (highlighted) BeiPalette.Accent else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.label,
                    fontSize = 14.sp,
                    color = if (highlighted) BeiPalette.OnAccent else BeiPalette.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "‹ ${entry.value} ›",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (highlighted) BeiPalette.OnAccent else BeiPalette.Accent
                )
            }
        }

        is MenuEntry.Action -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (highlighted) BeiPalette.Accent else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.label,
                    fontSize = 14.sp,
                    color = if (highlighted) BeiPalette.OnAccent else BeiPalette.Link,
                    modifier = Modifier.weight(1f)
                )
                if (highlighted) {
                    Text(text = "OK", fontSize = 12.sp, color = BeiPalette.OnAccent.copy(alpha = 0.6f))
                }
            }
        }
    }
}

private val ENTRY_PANEL_WIDTH = 360.dp
private val GROUP_PANEL_WIDTH = 190.dp

/** 二级面板里，第一条 entry 之前还有标题与一个间距占位。 */
private const val OFFSET_BEFORE_ENTRIES = 2
