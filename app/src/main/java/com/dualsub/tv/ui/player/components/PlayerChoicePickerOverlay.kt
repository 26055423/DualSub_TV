package com.dualsub.tv.ui.player.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass

/**
 * **选择页**：条目太多时（典型是内嵌字幕轨，本片 38 条）改用整页弹出选择。
 *
 * 为什么不再往侧边菜单后面接一层：侧边菜单的宽度就那么多，再开一层只能把条目压成
 * 更窄的一条竖列表，38 条要滚很久、还看不清是哪个语言。换成整页 + **多列网格**之后，
 * 一屏能扫到十几条，选完自动关闭 —— 这也正是当贝那套的做法。
 *
 * 只显示 [MenuEntry.Choice] 类型的条目（其它类型在列表型选择里没有意义）。
 *
 * ## 配色
 *
 * 压在视频画面上，所以面板用 [BeiGlass.Panel]（黑 55%）而不是外壳的白玻璃；
 * 网格里"当前在用的那条"用香槟 15% 底，**高亮项**再叠到 22% 底 + 55% 描边
 * （焦点态三件套里的"底 + 描边"两样，网格没有缩放动画）。
 *
 * @param selectedIndex 当前高亮项在**可选项序列**里的下标（调用方负责把它初始化到
 *   已经选中的那一条，这样一打开就能看到"现在用的是哪个"）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerChoicePickerOverlay(
    title: String,
    entries: List<MenuEntry>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    columns: Int = PICKER_COLUMNS
) {
    val selectable = entries.selectableIndices()
    val gridState = rememberLazyGridState()

    // 没有真实焦点，网格不会自己滚 —— 高亮项一变就手动滚到它所在的那一行。
    LaunchedEffect(selectedIndex, columns) {
        runCatching { gridState.animateScrollToItem((selectedIndex / columns).coerceAtLeast(0)) }
    }

    val panelShape = RoundedCornerShape(BeiDims.CardRadius)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BeiGlass.Scrim),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(0.86f)
                .clip(panelShape)
                .background(BeiGlass.Panel)
                .border(BeiDims.Border, BeiGlass.Border, panelShape)
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            Text(
                text = title,
                fontSize = BeiDims.CardTitleSize,
                fontWeight = FontWeight.Bold,
                color = BeiGlass.TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "方向键选择 · OK 选中并返回 · 按返回键取消",
                fontSize = BeiDims.CaptionSize,
                color = BeiGlass.TextMuted
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(selectable, key = { _, entryIndex -> entryIndex }) { position, entryIndex ->
                    val entry = entries[entryIndex] as? MenuEntry.Choice ?: return@itemsIndexed
                    PickerCell(
                        label = entry.label,
                        detail = entry.detail,
                        selected = entry.selected,
                        highlighted = position == selectedIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerCell(
    label: String,
    detail: String?,
    selected: Boolean,
    highlighted: Boolean
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    highlighted -> BeiGlass.AccentFillStrong
                    selected -> BeiGlass.AccentFill
                    // 没选中也没高亮的格子给一层极淡的白，让网格有"格子"的边界
                    else -> BeiGlass.Glass
                }
            )
            .border(
                width = if (highlighted) BeiDims.BorderFocus else BeiDims.Border,
                color = when {
                    highlighted -> BeiGlass.AccentBorderStrong
                    selected -> BeiGlass.AccentBorder
                    else -> BeiGlass.Border
                },
                shape = shape
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selected) "●" else "○",
            fontSize = BeiDims.CaptionSize,
            color = if (highlighted) BeiGlass.AccentBright else BeiGlass.TextSecondary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = BeiDims.BodySize,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (highlighted) BeiGlass.AccentBright else BeiGlass.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            detail?.let {
                Text(
                    text = it,
                    fontSize = BeiDims.TinySize,
                    color = if (highlighted) {
                        BeiGlass.AccentBright.copy(alpha = 0.65f)
                    } else {
                        BeiGlass.TextMuted
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 选择页的列数 —— 与 `PlayerScreen` 的网格导航步长**必须一致**，否则上下键会跳错格。 */
const val PICKER_COLUMNS = 3
