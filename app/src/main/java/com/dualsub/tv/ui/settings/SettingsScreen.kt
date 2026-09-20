package com.dualsub.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.dualsub.tv.ai.AiSubtitleConfig
import com.dualsub.tv.data.SettingsStore
import com.dualsub.tv.player.SubtitleStyle
import com.dualsub.tv.ui.shell.BeiCard
import com.dualsub.tv.ui.shell.BeiChoiceRow
import com.dualsub.tv.ui.shell.BeiPageHeader
import com.dualsub.tv.ui.shell.BeiPillButton
import com.dualsub.tv.ui.shell.BeiStaticCard
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass
import kotlinx.coroutines.launch

/**
 * 「设置」页：**全局**的播放与显示参数，外加 **AI 字幕配置的入口**。
 *
 * 控件样式：**一行一个「名称 + 一排选项胶囊」**，选中的那枚是香槟金底 + ✓。
 * 为什么不用「－ 值 ＋」步进器：电视上每多按一次遥控器都是成本，而这类参数本来就只需要
 * 四五个常用档位 —— 一步到位比"按七下才从 1500ms 调到 3000ms"友好得多。
 *
 * **AI 字幕为什么收在这里**（用户要求）：它的「用」在播放页菜单的字幕二级里（生成 /
 * 实时翻译），「配」就该离「用」最近的地方放 —— 而不是单占一个一级导航项，
 * 点进去却是一张扫码图。所以这一页里点「AI 字幕」进二级页 [AiSettingsScreen]。
 */
@Composable
fun SettingsScreen(settings: SettingsStore) {
    var showAiConfig by remember { mutableStateOf(false) }

    // AI 配置是这一页的二级页：进去之后整屏交给它（它自带标题与「返回」）。
    if (showAiConfig) {
        AiSettingsScreen(settings = settings, onBack = { showAiConfig = false })
        return
    }

    val scope = rememberCoroutineScope()
    val videoCachingMs by settings.videoCachingMs.collectAsState(initial = 1500)
    val primaryStyle by settings.primaryStyle.collectAsState(initial = SubtitleStyle.PRIMARY)
    val secondaryStyle by settings.secondaryStyle.collectAsState(initial = SubtitleStyle.SECONDARY)
    val aiConfig by settings.aiConfig.collectAsState(
        initial = AiSubtitleConfig(
            baseUrl = "",
            apiKey = "",
            model = "",
            targetLang = "",
            prompt = ""
        )
    )

    Column(modifier = Modifier.fillMaxSize()) {
        BeiPageHeader(
            title = "设置",
            subtitle = "视频缓冲 · 字幕默认样式 · AI 字幕 —— 这里改的是所有视频的默认值"
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AiEntryCard(config = aiConfig, onOpen = { showAiConfig = true })
            }
            item {
                VideoCachingCard(
                    current = videoCachingMs,
                    onSelect = { ms -> scope.launch { settings.setVideoCachingMs(ms) } }
                )
            }
            item {
                SubtitleStyleCard(
                    title = "主字幕样式",
                    detail = "主字幕由本应用自绘（走系统字体栈），所以这里的字号 / 颜色 / 位置都生效。",
                    style = primaryStyle,
                    onStyleChange = { style -> scope.launch { settings.setPrimaryStyle(style) } },
                    onReset = { scope.launch { settings.setPrimaryStyle(SubtitleStyle.PRIMARY) } }
                )
            }
            item {
                SubtitleStyleCard(
                    title = "次字幕样式",
                    detail = "次字幕是自绘的译文层，叠在主字幕上方；两者重叠时把「底部距离」调大。",
                    style = secondaryStyle,
                    onStyleChange = { style -> scope.launch { settings.setSecondaryStyle(style) } },
                    onReset = { scope.launch { settings.setSecondaryStyle(SubtitleStyle.SECONDARY) } }
                )
            }
        }
    }
}

/**
 * AI 字幕配置入口卡：整卡可点，进二级页。
 *
 * 卡上直接写出**当前状态**（配没配 Key、用的哪个模型）—— 不然用户点进去才知道没配，
 * 那一步是白费的。
 */
@Composable
private fun AiEntryCard(config: AiSubtitleConfig, onOpen: () -> Unit) {
    val configured = config.apiKey.isNotBlank()

    BeiCard(onClick = onOpen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "AI 字幕",
                    color = BeiGlass.TextPrimary,
                    fontSize = BeiDims.CardTitleSize,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (configured) {
                        "已配置 · ${config.model.ifBlank { "未指定模型" }} · 目标语言 ${config.targetLang.ifBlank { "中文" }}"
                    } else {
                        "还没配 API Key —— 点一下去填（播放时可在菜单里直接调用）"
                    },
                    color = if (configured) BeiGlass.TextSecondary else BeiGlass.Link,
                    fontSize = BeiDims.CaptionSize
                )
            }
            Text(text = "›", fontSize = 20.sp, color = BeiGlass.Accent)
        }
    }
}

@Composable
private fun VideoCachingCard(current: Int, onSelect: (Int) -> Unit) {
    val index = nearestIndex(CACHING_OPTIONS, current)

    BeiStaticCard {
        Text(
            text = "视频播放缓冲",
            color = BeiGlass.TextPrimary,
            fontSize = BeiDims.CardTitleSize,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "起播前预取的数据量。调大能减少网络视频的卡顿，调小降低起播延迟；" +
                "改动在下次打开视频时生效（已打开的视频不会中途换缓冲）。",
            color = BeiGlass.TextMuted,
            fontSize = BeiDims.CaptionSize
        )
        BeiChoiceRow(
            label = "缓冲大小",
            options = CACHING_OPTIONS.map { formatCaching(it) },
            selectedIndex = if (CACHING_OPTIONS.contains(current)) index else -1,
            onSelect = { onSelect(CACHING_OPTIONS[it]) },
            detail = "SMB 大码率片源卡顿时挑更大的那档"
        )
    }
}

@Composable
private fun SubtitleStyleCard(
    title: String,
    detail: String,
    style: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    onReset: () -> Unit
) {
    BeiStaticCard {
        Text(
            text = title,
            color = BeiGlass.TextPrimary,
            fontSize = BeiDims.CardTitleSize,
            fontWeight = FontWeight.SemiBold
        )
        Text(text = detail, color = BeiGlass.TextMuted, fontSize = BeiDims.CaptionSize)

        BeiChoiceRow(
            label = "字号",
            options = FONT_SIZE_OPTIONS.map { "$it" },
            selectedIndex = exactIndex(FONT_SIZE_OPTIONS, style.fontSizeSp),
            onSelect = { onStyleChange(style.copy(fontSizeSp = FONT_SIZE_OPTIONS[it])) },
            detail = "单位 sp"
        )
        BeiChoiceRow(
            label = "底部距离",
            options = BOTTOM_PADDING_OPTIONS.map { "$it" },
            selectedIndex = exactIndex(BOTTOM_PADDING_OPTIONS, style.bottomPaddingDp),
            onSelect = { onStyleChange(style.copy(bottomPaddingDp = BOTTOM_PADDING_OPTIONS[it])) },
            detail = "距屏幕底部的高度（dp）；次字幕靠它避开主字幕"
        )
        BeiChoiceRow(
            label = "描边强度",
            options = OUTLINE_LABELS,
            selectedIndex = exactIndex(OUTLINE_VALUES, style.outlineWidth.toInt()),
            onSelect = { onStyleChange(style.copy(outlineWidth = OUTLINE_VALUES[it].toFloat())) },
            detail = "描边让字在亮画面里也看得清"
        )

        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "颜色",
                color = BeiGlass.TextPrimary,
                fontSize = 15.sp,
                modifier = Modifier.width(116.dp)
            )
            TEXT_COLORS.forEach { (argb, _) ->
                ColorDot(
                    argb = argb,
                    selected = style.textColor == argb,
                    onClick = { onStyleChange(style.copy(textColor = argb)) }
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = TEXT_COLORS.firstOrNull { it.first == style.textColor }?.second ?: "自定义",
                color = BeiGlass.TextMuted,
                fontSize = BeiDims.CaptionSize
            )
        }

        BeiChoiceRow(
            label = "加粗",
            options = listOf("开启", "关闭"),
            selectedIndex = if (style.bold) 0 else 1,
            onSelect = { onStyleChange(style.copy(bold = it == 0)) },
            detail = "电视上离得远，加粗通常更好读"
        )

        Row(modifier = Modifier.padding(top = 6.dp)) {
            BeiPillButton(label = "恢复默认样式", onClick = onReset)
        }
    }
}

/**
 * 一个可选的颜色圆点。
 *
 * 这里**没用** tv-material3 的 `Surface`：它的描边参数要的是自家的 `Border` 类型，
 * 为了一个选中圈引入那套抽象不划算。改为 foundation 的 `background` + `border` +
 * `clickable` 自绘 —— 三态（常态 / 选中 / 焦点）一眼就看得清。
 *
 * **外层留 48dp 的可点区**：色点本身只有 30dp，按规范"最小可点区域 ≥48dp"是给遥控器
 * 焦点框用的 —— 焦点框比色点大一圈，才不会出现"明明框住了却点不动"。
 */
@Composable
private fun ColorDot(
    argb: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val highlighted = selected || focused

    Box(
        modifier = Modifier
            .size(BeiDims.MinTouchTarget)
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(
                    width = if (highlighted) 3.dp else 1.dp,
                    color = if (highlighted) BeiGlass.Accent else BeiGlass.Border,
                    shape = CircleShape
                )
        )
    }
}

/** 视频缓冲档位（毫秒）。 */
private val CACHING_OPTIONS = listOf(500, 1000, 1500, 3000, 6000)

private fun formatCaching(ms: Int): String =
    if (ms % 1000 == 0) "${ms / 1000}s" else "%.1fs".format(ms / 1000.0)

/** 字幕档位：字号（sp）/ 底部距离（dp）/ 描边（dp）。 */
private val FONT_SIZE_OPTIONS = listOf(20, 24, 28, 32, 36)
private val BOTTOM_PADDING_OPTIONS = listOf(24, 40, 64, 96, 128)
private val OUTLINE_VALUES = listOf(0, 2, 4, 6)
private val OUTLINE_LABELS = listOf("无", "细", "中", "粗")

/** 预设字幕颜色（Android 是 ARGB Int，字符串只是给设置页显示的色名）。 */
private val TEXT_COLORS: List<Pair<Int, String>> = listOf(
    0xFFFFFFFF.toInt() to "白色",
    0xFFFFE082.toInt() to "奶黄",
    0xFF80DEEA.toInt() to "青色",
    0xFFA5D6A7.toInt() to "浅绿",
    0xFFF48FB1.toInt() to "粉色"
)

/**
 * 精确匹配档位：命中返回下标，**没命中返回 -1**（界面上一个都不选中）。
 *
 * 这里刻意不做"就近取整"：设置页显示的就是用户当前的真实值，把 26sp 悄悄显示成
 * "24 已选中"会撒谎。用户一按胶囊就会被改成精确档位，届时就对上了。
 */
private fun exactIndex(options: List<Int>, value: Int): Int = options.indexOf(value)

/**
 * 取 [value] 在 [options] 里最接近的下标 —— 只有「视频缓冲」用它：
 * 那个值可能有旧版本写下的其它数字，此时也要有一档处于选中态，否则整行看着像坏了。
 */
private fun nearestIndex(options: List<Int>, value: Int): Int {
    val exact = options.indexOf(value)
    if (exact >= 0) return exact
    val upper = options.indexOfFirst { it >= value }
    return if (upper >= 0) upper else options.lastIndex
}
