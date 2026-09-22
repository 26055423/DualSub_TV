package com.dualsub.tv.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dualsub.tv.ui.theme.BeiGlass
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.core.InAppLog
import com.dualsub.tv.player.VlcPlayerController
import com.dualsub.tv.ui.format.formatOffset
import com.dualsub.tv.ui.format.formatTime
import com.dualsub.tv.ui.player.components.AiSubtitleProgressOverlay
import com.dualsub.tv.ui.player.components.MenuEntry
import com.dualsub.tv.ui.player.components.PICKER_COLUMNS
import com.dualsub.tv.ui.player.components.PlayerChoicePickerOverlay
import com.dualsub.tv.ui.player.components.PlayerControls
import com.dualsub.tv.ui.player.components.PlayerExitConfirmOverlay
import com.dualsub.tv.ui.player.components.PlayerMenuGroup
import com.dualsub.tv.ui.player.components.PlayerMenuOverlay
import com.dualsub.tv.ui.player.components.PlayerInfoOverlay
import com.dualsub.tv.ui.player.components.PlayerStatusBar
import com.dualsub.tv.ui.player.components.SubtitleOverlay
import com.dualsub.tv.ui.player.components.selectableIndices
import com.dualsub.tv.ui.settings.AiSettingsScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * 播放页。
 *
 * 层次自下而上：视频画布（[VLCVideoLayout]）→ **两路字幕的自绘层**（Compose）
 * → 控制条 / 侧边菜单 / 提示层 / 退出确认框。
 *
 * **两路文本字幕都走 Compose 自绘**（这是本项目的核心设计，别改回去）：
 * - **文本字幕（主 / 次）由 `SubtitleOverlay` 自绘** —— 字号 / 颜色 / 描边 / 底部距离 /
 *   时间偏移都能各自独立调；而且中文走 Compose 的系统字体栈，**不会出现方框（tofu）**。
 *   主字幕**早先是交给 libVLC 的 libass 渲染的** —— 真机上中文字幕整片渲染成方框，才改成自绘。
 * - **唯一的例外是图片字幕**（PGS / DVD SPU / 蓝光）：位图 Compose 画不了，仍交 libVLC
 *   渲染在 `VLCVideoLayout` 内部；此时 `primaryCue` 恒为 null，上面那层自然不画。
 *
 * 代价要认：自绘拿不到 libass 的完整 ASS 排版（矢量绘图、复杂动画组、精细的 `\pos` / `\move`）。
 *
 * 两路的避让靠**位置错开**：次字幕画在 Compose 层、天然盖在主字幕之上，所以位置重叠时
 * 不靠层级，而是由用户把次字幕的「底部距离」按主字幕实际高度调大（字幕设置面板里）。
 *
 * **两个必须守住的约束**（都踩过坑，别再退回去）：
 *
 * 1. **组合期绝不能调 JNI**。音轨选项、分辨率、音量这些都要走 libVLC 的 native，
 *    如果在组合函数里直接调，每次重组都会跑一遍，每秒几十次 JNI 会把 UI 线程压死，
 *    表现为「按什么都没反应」。所以统一从 `viewModel.stats` 这个预先算好的快照里读。
 *
 * 2. **根节点必须自己持有焦点**。`onPreviewKeyEvent` 只在节点位于焦点路径上时才触发；
 *    画布被设为不可聚焦后，若没有任何节点持有焦点，这个 Box 就收不到任何按键
 *    （表现为「只有返回键能用」—— 返回走 Activity 层的 BackHandler，不经过焦点系统）。
 *    所以这里用 [focusRequester] + [focusable] 显式把焦点握在自己手里。
 *
 * **菜单键的兼容处理**：各厂商遥控器上那个「☰」（三条横杠）键的映射并不统一 ——
 * 有的发 `KEYCODE_MENU`(82)，有的发 `KEYCODE_INFO`(165)。所以 [Key.Menu] 与 [Key.Info]
 * **都用来开关侧边菜单**；`lastKeyName` 会记下最近一次按键的键名并显示在「影片详情」里，
 * 方便一次试出实际键码。
 *
 * 播放失败时**覆盖整屏显示根因**：结论用大字，原因说人话，建议用醒目底色，
 * 排查详情用小字 —— 播不出来可以，但不能让人猜。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val primary by viewModel.primary.collectAsState()
    val secondary by viewModel.secondary.collectAsState()
    // 两路字幕都由自绘渲染，所以两路都有「当前该显示的那一条」。
    val primaryCue by viewModel.primaryCue.collectAsState()
    val secondaryCue by viewModel.secondaryCue.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val embeddedTracks by viewModel.embeddedTracks.collectAsState()
    val embeddedTrackStatus by viewModel.embeddedTrackStatus.collectAsState()
    val playerError by viewModel.playerError.collectAsState()
    val noticeText by viewModel.notice.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val mediaInfo by viewModel.mediaInfo.collectAsState()
    val container by viewModel.container.collectAsState()
    val audioTracks by viewModel.audioTracks.collectAsState()
    // libVLC 的只读快照（音轨/分辨率/音量/倍速）—— 组合期只读它，不再直接调 JNI
    val stats by viewModel.stats.collectAsState()
    val aiSubtitleState by viewModel.aiSubtitleState.collectAsState()

    // 右上角的「实时网速」：**只对网络片源显示** —— 本地文件的读盘速率不是「网速」，
    // 摆一个恒定的数字只会误导。真正的取值在 ViewModel 的 ticker 里每秒做一次。
    val isRemoteSource = viewModel.video.uri.scheme?.let { it != "file" && it != "content" } ?: false
    val networkSpeedText = if (isRemoteSource) stats.inputBytesPerSec?.let(::formatSpeed) else null

    var showControls by remember { mutableStateOf(true) }
    var pickForPrimary by remember { mutableStateOf(true) }
    var showBuffering by remember { mutableStateOf(false) }
    var seekHint by remember { mutableStateOf<String?>(null) }
    // 最近一次按键的键名 —— 用来确认遥控器上那个「☰」键实际发的是什么
    var lastKeyName by remember { mutableStateOf("-") }
    // 显示比例是纯 UI 选择，libVLC 那边读回来无法区分「跟随原始」与「显式 16:9」，所以本地记
    var aspectIndex by remember { mutableStateOf(0) }
    // 字幕设置分两页：0 = 主字幕，1 = 次字幕（主+次混排太长，用户要求拆开）
    var subtitlePage by remember { mutableStateOf(0) }
    // 应用内日志覆盖层：把 logcat 关键行直接画在电视上，不必连电脑抓日志
    var logOverlay by remember { mutableStateOf(false) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    // 按上/下显示的播放信息层（当前音轨/字幕轨/视频格式/码率…）
    var infoOverlay by remember { mutableStateOf(false) }

    // 右上角的当前时间（24 小时制）。与播放位置无关，所以在界面这一层每秒刷一次，
    // 不走 ViewModel 那个 50ms 的 ticker。
    val clockFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var clockText by remember { mutableStateOf(clockFormat.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            clockText = clockFormat.format(Date())
            delay(CLOCK_TICK_MS)
        }
    }

    // ---- 侧边菜单状态
    var menuOpen by remember { mutableStateOf(false) }
    var menuGroup by remember { mutableStateOf(0) }
    var menuEntry by remember { mutableStateOf(0) }          // 二级里选中的 entries 原始下标
    var menuFocusDeep by remember { mutableStateOf(false) }  // 焦点是否已进入二级面板
    // 「选择页」—— 条目太多时（内嵌字幕轨常有几十条）改用整页弹出选择。
    // 它盖在菜单之上，选完 / 取消后回到菜单，方便接着调别的。
    var pickerOpen by remember { mutableStateOf(false) }
    var pickerTitle by remember { mutableStateOf("") }
    var pickerEntries by remember { mutableStateOf<List<MenuEntry>>(emptyList()) }
    var pickerIndex by remember { mutableStateOf(0) }
    // 数值项的「激活调节」态：激活后 ←/→ 直接调值，OK / 返回退出
    var adjustActive by remember { mutableStateOf(false) }
    // AI 字幕配置层（压在画面上，不退出播放）
    var aiConfigOpen by remember { mutableStateOf(false) }

    // 焦点握在自己手里，按键才一定到这个 Box
    val focusRequester = remember { FocusRequester() }

    // 长按预览的超时兜底：停止收到 KeyDown 超过 300ms 则视为松键并恢复播放。
    // 部分蓝牙遥控不上报 KeyUp，这个 Job 作为保险。
    val previewTimeoutScope = rememberCoroutineScope()
    val previewTimeoutJob = remember { androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.selectExternalFile(uri, displayNameOf(context, uri), pickForPrimary)
        }
    }

    // 进入页面、以及菜单开关导致子节点焦点变化后，都把焦点收回到根节点
    LaunchedEffect(menuOpen, showControls) {
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(showControls, isPlaying, menuOpen) {
        if (showControls && isPlaying && !menuOpen) {
            delay(AUTO_HIDE_DELAY_MS)
            showControls = false
        }
    }

    // 拖动进度时会短暂进入缓冲，直接显示会闪，所以延迟一点再报出来
    LaunchedEffect(isBuffering) {
        if (isBuffering) {
            delay(BUFFERING_HINT_DELAY_MS)
            showBuffering = true
        } else {
            showBuffering = false
        }
    }

    LaunchedEffect(logOverlay) {
        while (logOverlay) {
            logLines = InAppLog.snapshot()
            delay(700)
        }
    }

    LaunchedEffect(seekHint) {
        if (seekHint != null) {
            delay(SEEK_HINT_DURATION_MS)
            seekHint = null
        }
    }

    // ---- 组装侧边菜单的五个分组（全部数据取自 stats / State，无 JNI）
    val menuGroups = if (menuOpen) buildMenuGroups(
        viewModel = viewModel,
        stats = stats,
        primary = primary,
        secondary = secondary,
        embeddedTracks = embeddedTracks,
        embeddedTrackStatus = embeddedTrackStatus,
        onPickFile = { forPrimary ->
            pickForPrimary = forPrimary
            filePicker.launch(arrayOf("*/*"))
        },
        mediaInfo = mediaInfo,
        container = container,
        audioTracks = audioTracks,
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        lastKeyName = lastKeyName,
        aspectIndex = aspectIndex,
        onAspectChange = { aspectIndex = it },
        subtitlePage = subtitlePage,
        onSubtitlePageChange = { subtitlePage = it },
        onShowLog = { menuOpen = false; logOverlay = true },
        onOpenAiSettings = { menuOpen = false; aiConfigOpen = true },
        onOpenTrackPicker = { choices, pickerName ->
            pickerEntries = choices
            pickerTitle = pickerName
            // 打开时高亮「当前正在用的那一条」，而不是从头开始
            val selectable = choices.selectableIndices()
            val currentPos = selectable.indexOfFirst { idx ->
                (choices[idx] as? MenuEntry.Choice)?.selected == true
            }
            pickerIndex = if (currentPos >= 0) currentPos else 0
            pickerOpen = true
        },
        aiSubtitleState = aiSubtitleState
    ) else emptyList()

    val currentSelectable = menuGroups.getOrNull(menuGroup)?.entries?.selectableIndices() ?: emptyList()
    LaunchedEffect(menuOpen, menuGroup, currentSelectable) {
        if (menuOpen && menuEntry !in currentSelectable) menuEntry = currentSelectable.firstOrNull() ?: 0
    }

    /** 退出播放的确认框是否打开。返回键在没有叠加层时不再直接退出，而是先问一句。 */
    var exitConfirmOpen by remember { mutableStateOf(false) }

    /** 确认框里当前高亮的是不是「确认退出」。默认 false = 高亮「取消」。 */
    var exitConfirmConfirm by remember { mutableStateOf(false) }

    BackHandler {
        when {
            // 确认框在最上层：返回键 = 取消（继续看）
            exitConfirmOpen -> exitConfirmOpen = false
            // 逐层退出：配置层 → 选择页 → 激活态 → 二级 → 菜单 → 提示条 → 控制条 → 退出确认框
            aiConfigOpen -> aiConfigOpen = false
            pickerOpen -> pickerOpen = false
            adjustActive -> adjustActive = false
            menuFocusDeep -> menuFocusDeep = false
            menuOpen -> menuOpen = false
            // 提示条：自动出现的那种（自动选轨 / 字幕轨读取失败…），按返回直接收掉，
            // 不用干等它自己消失。
            noticeText != null -> viewModel.dismissNotice()
            // 控制条（连带顶栏）还显示着：按返回先把这一屏控件收掉，**不该直接问
            // "要不要退出"**。它本来就是 6 秒后自动隐藏的东西，返回键只是"提前收"。
            showControls -> showControls = false
            // 没有叠加层了 —— **不再直接退出播放**，先弹确认框（默认高亮「取消」）。
            // 一次误按就退片、进度还不保存，代价太大。
            else -> {
                exitConfirmConfirm = false
                exitConfirmOpen = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BeiGlass.Ink)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // 松开左/右键时结束长按预览，恢复播放
                if (event.type == KeyEventType.KeyUp) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight -> {
                            previewTimeoutJob.value?.cancel()
                            previewTimeoutJob.value = null
                            viewModel.resumeFromPreview()
                            true
                        }
                        else -> false
                    }
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // 返回键一律不在这里消费，交给 BackHandler 逐层退出
                // （配置层 → 激活态 → 三级 → 二级 → 菜单 → 离开播放页）。
                // 否则它会被下面那个「任意键唤出控制条」的兜底分支吃掉 —— 真机上表现就是
                // 「按返回没反应」（实测：AI 配置层开着时按返回关不掉）。
                if (event.key == Key.Back) return@onPreviewKeyEvent false

                // 记下每一次按键的键名，供「影片详情」显示 —— 用来确认 ☰ 实际发的是什么
                lastKeyName = android.view.KeyEvent
                    .keyCodeToString(event.nativeKeyEvent.keyCode)
                    .removePrefix("KEYCODE_")

                // ---------- 退出确认框：它是最上层，独占按键
                //
                // 左右切换「确认退出 / 取消」，OK 执行当前高亮的那一个，其余一律吞掉
                // （免得误触到播放控制）。返回键在上面已经放行给 BackHandler = 取消。
                if (exitConfirmOpen) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight -> {
                            exitConfirmConfirm = !exitConfirmConfirm
                            true
                        }
                        Key.Enter, Key.DirectionCenter -> {
                            exitConfirmOpen = false
                            if (exitConfirmConfirm) onBack()
                            true
                        }
                        else -> true
                    }
                }

                // ---------- 0) 信息层：任意键关闭
                if (infoOverlay) {
                    infoOverlay = false
                    return@onPreviewKeyEvent true
                }

                // ---------- 0) 日志覆盖层优先级最高：任意键关闭
                if (logOverlay && event.key != Key.Menu) {
                    logOverlay = false
                    return@onPreviewKeyEvent true
                }

                // ---------- 0) 选择页：条目太多时弹出的整页选择，优先于菜单接管按键
                if (pickerOpen) {
                    val pickerSelectable = pickerEntries.selectableIndices()
                    val pickerLast = pickerSelectable.lastIndex.coerceAtLeast(0)
                    return@onPreviewKeyEvent when (event.key) {
                        // 返回 / 菜单键：取消（不选），回到菜单
                        Key.Back, Key.Menu, Key.Info -> {
                            pickerOpen = false
                            true
                        }

                        // 网格导航：上下跨行、左右逐个；步长与组件里的 PICKER_COLUMNS 必须一致
                        Key.DirectionUp -> {
                            pickerIndex = (pickerIndex - PICKER_COLUMNS).coerceAtLeast(0)
                            true
                        }

                        Key.DirectionDown -> {
                            pickerIndex = (pickerIndex + PICKER_COLUMNS).coerceAtMost(pickerLast)
                            true
                        }

                        Key.DirectionLeft -> {
                            pickerIndex = (pickerIndex - 1).coerceAtLeast(0)
                            true
                        }

                        Key.DirectionRight -> {
                            pickerIndex = (pickerIndex + 1).coerceAtMost(pickerLast)
                            true
                        }

                        // OK = 选中当前项并关闭 —— 这就是「选完再关闭」
                        Key.Enter, Key.DirectionCenter -> {
                            val entryIndex = pickerSelectable.getOrNull(pickerIndex)
                            val entry = entryIndex?.let { pickerEntries.getOrNull(it) }
                            if (entry is MenuEntry.Choice) entry.onSelect()
                            pickerOpen = false
                            true
                        }

                        // 弹页打开时把其余按键都吞掉：免得误触播放 / 快进，也免得穿透到菜单
                        else -> true
                    }
                }

                // ---------- 1) 菜单打开时由菜单全权接管方向键
                //
                // 语义（用户定的）：
                //   一级 ──左键──▶ 二级（条目多时再开整页选择页）
                //   上下：当前层里移动；
                //   左键：**非激活态**进二级 / 激活数值项；激活态则是「减小」；
                //   右键：**激活态**是「增大」；非激活态才回一级；
                //   返回键：逐层退出（激活态 → 二级 → 菜单）；
                //   OK：单选项「切换」；动作项「执行」；数值项「激活 / 退出激活」。
                //
                // 「激活态下右=增大」是用户真机试用后明确要求的：调一个数值时左右就是减/增，
                // 退出激活交给 OK / 返回 —— 否则右一按就把激活撤了，看着像“跳回上级菜单”。
                if (menuOpen) {
                    val groupCount = menuGroups.size
                    val groupEntries = menuGroups.getOrNull(menuGroup)?.entries.orEmpty()
                    val groupSelectable = groupEntries.selectableIndices()
                    val currentEntry = groupEntries.getOrNull(menuEntry)

                    return@onPreviewKeyEvent when (event.key) {
                        Key.Menu, Key.Info -> {
                            menuOpen = false
                            adjustActive = false
                            true
                        }

                        Key.DirectionUp, Key.DirectionDown -> {
                            val step = if (event.key == Key.DirectionUp) -1 else 1
                            if (menuFocusDeep) {
                                // 激活态下按上下 = 先退出激活再移动（不响应反而像遥控器坏了）
                                adjustActive = false
                                val pos = groupSelectable.indexOf(menuEntry)
                                if (pos >= 0) {
                                    val next = if (step < 0) {
                                        if (pos <= 0) groupSelectable.lastIndex else pos - 1
                                    } else {
                                        if (pos >= groupSelectable.lastIndex) 0 else pos + 1
                                    }
                                    menuEntry = groupSelectable.getOrElse(next) { menuEntry }
                                }
                            } else {
                                menuGroup = (menuGroup + step + groupCount) % groupCount
                                adjustActive = false
                                // 定位到该组第一个**可选项**：写死 0 会落在 Header 上，高亮就「看不见」了。
                                menuEntry = menuGroups.getOrNull(menuGroup)?.entries?.selectableIndices()?.firstOrNull() ?: 0
                            }
                            true
                        }

                        // 「左」= 往里走一层（数值项是激活；激活态则是减小）
                        Key.DirectionLeft -> {
                            when {
                                adjustActive -> (currentEntry as? MenuEntry.Adjust)?.onDecrease()

                                menuFocusDeep -> when (currentEntry) {
                                    is MenuEntry.Adjust -> adjustActive = true
                                    // 其余项（单选项 / 动作项）没有更深一层，左键就回一级
                                    else -> menuFocusDeep = false
                                }

                                else -> menuFocusDeep = groupSelectable.isNotEmpty()
                            }
                            true
                        }

                        // 「右」：激活态 = 增大；否则回一级
                        Key.DirectionRight -> {
                            when {
                                adjustActive -> (currentEntry as? MenuEntry.Adjust)?.onIncrease()
                                menuFocusDeep -> menuFocusDeep = false
                                else -> Unit
                            }
                            true
                        }

                        Key.Enter, Key.DirectionCenter -> {
                            when {
                                menuFocusDeep -> when (currentEntry) {
                                    is MenuEntry.Choice -> currentEntry.onSelect()
                                    is MenuEntry.Action -> currentEntry.onClick()
                                    // 数值项：OK 是「激活 / 退出激活」，调值交给 ←/→
                                    is MenuEntry.Adjust -> adjustActive = !adjustActive
                                    else -> Unit
                                }

                                else -> menuFocusDeep = groupSelectable.isNotEmpty()
                            }
                            true
                        }

                        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                            viewModel.togglePlayPause()
                            true
                        }

                        else -> false
                    }
                }

                // ---------- 2) 菜单未打开
                when (event.key) {
                    // ☰ 键在不同遥控器上可能是 Menu，也可能是 Info，两个都当菜单键
                    Key.Menu, Key.Info -> {
                        menuOpen = true
                        menuFocusDeep = false
                        menuGroup = 0
                        menuEntry = menuGroups.getOrNull(0)?.entries?.selectableIndices()?.firstOrNull() ?: 0
                        showControls = true
                        true
                    }

                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        viewModel.togglePlayPause()
                        true
                    }

                    // OK 键 = 播放/暂停；同时保证控制条可见（可能已自动隐藏）
                    Key.Enter, Key.DirectionCenter -> {
                        viewModel.togglePlayPause()
                        showControls = true
                        true
                    }

                    // 上/下 = 播放信息层。信息比菜单里的「影片详情」更常用，所以给它一个直接入口：
                    // 按一下出现，任意键消失，不打断观看。
                    Key.DirectionUp, Key.DirectionDown -> {
                        if (!infoOverlay) viewModel.refreshStatsNow()
                        infoOverlay = true
                        true
                    }

                    // 左/右：短按跳 10 秒；长按进入逐帧预览模式（暂停播放、每次解码目标帧）。
                    // 松键时 KeyUp 分支会调 resumeFromPreview 恢复播放；
                    // 同时每次 KeyDown 都重置 300ms 定时器，兜底蓝牙遥控不上报 KeyUp 的情况。
                    Key.DirectionLeft -> {
                        val repeat = event.nativeKeyEvent.repeatCount
                        if (repeat == 0) {
                            viewModel.seekBy(-SEEK_STEP_MS)
                            seekHint = "← -10s"
                        } else {
                            val step = longPressSeekMs(repeat)
                            viewModel.seekPreview(-step)
                            seekHint = "← -${step / 1000}s"
                            previewTimeoutJob.value?.cancel()
                            previewTimeoutJob.value = previewTimeoutScope.launch {
                                delay(PREVIEW_RELEASE_TIMEOUT_MS)
                                viewModel.resumeFromPreview()
                            }
                        }
                        showControls = true
                        true
                    }

                    Key.DirectionRight -> {
                        val repeat = event.nativeKeyEvent.repeatCount
                        if (repeat == 0) {
                            viewModel.seekBy(SEEK_STEP_MS)
                            seekHint = "→ +10s"
                        } else {
                            val step = longPressSeekMs(repeat)
                            viewModel.seekPreview(step)
                            seekHint = "→ +${step / 1000}s"
                            previewTimeoutJob.value?.cancel()
                            previewTimeoutJob.value = previewTimeoutScope.launch {
                                delay(PREVIEW_RELEASE_TIMEOUT_MS)
                                viewModel.resumeFromPreview()
                            }
                        }
                        showControls = true
                        true
                    }

                    Key.MediaFastForward -> {
                        viewModel.seekBy(SEEK_STEP_MS)
                        seekHint = "→ +10s"
                        showControls = true
                        true
                    }

                    Key.MediaRewind -> {
                        viewModel.seekBy(-SEEK_STEP_MS)
                        seekHint = "← -10s"
                        showControls = true
                        true
                    }

                    else -> if (!showControls) {
                        showControls = true
                        true
                    } else {
                        false
                    }
                }
            }
    ) {
        // libVLC 需要 VLCVideoLayout 作为渲染目标（它内部据此创建 video output）。
        //
        // **这里必须关掉焦点能力**：VLCVideoLayout 继承自 FrameLayout，默认会参与焦点竞争；
        // 它一旦拿到焦点，上面 Box 的 onPreviewKeyEvent 就收不到任何遥控器按键了。
        // 外层 Box 自己持有焦点（见 focusRequester），所以画布必须让开。
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    viewModel.attachVideoLayout(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // **两路字幕现在都由 Compose 自绘** —— 字体走系统字体栈，任何能正常显示中文界面的
        // 电视都不会再出现方框，两路的样式与位置也完全对称。
        //
        // 顺序：先主后次。层级上次字幕在上，两路位置重叠时由次字幕盖住主字幕，
        // 所以避让靠**位置错开** —— 把次字幕的「底部距离」在字幕设置面板里调大即可。
        //
        // 例外：**图片字幕**（PGS / VOBSUB / DVBSUB）仍由 libVLC 渲染在 VLCVideoLayout 内，
        // 此时 primaryCue 恒为 null，这一层自然不画。
        SubtitleOverlay(
            cue = primaryCue,
            style = primary.style,
            positionMs = positionMs - primary.offsetMs
        )
        SubtitleOverlay(
            cue = secondaryCue,
            style = secondary.style,
            positionMs = positionMs - secondary.offsetMs
        )

        if (showControls && !menuOpen) {
            // 顶栏：左＝文件名，右＝实时网速 · 当前时间。与控制条同步出现，
            // 但因为「播放中才会自动隐藏」（见上面的自动隐藏条件），暂停时它们是常驻的。
            PlayerStatusBar(
                title = viewModel.video.title,
                speedText = networkSpeedText,
                clockText = clockText
            )
            PlayerControls(
                positionMs = positionMs,
                durationMs = durationMs,
                primaryLabel = primary.label,
                secondaryLabel = secondary.label,
                onSeekBackward = {
                    viewModel.seekBy(-SEEK_STEP_MS)
                    seekHint = "← -10s"
                },
                onSeekForward = {
                    viewModel.seekBy(SEEK_STEP_MS)
                    seekHint = "→ +10s"
                },
                onConfigurePrimary = {
                    pickForPrimary = true
                    menuOpen = true
                    menuGroup = 0
                    menuEntry = menuGroups.getOrNull(0)?.entries?.selectableIndices()?.firstOrNull() ?: 0
                    menuFocusDeep = false
                },
                onConfigureSecondary = {
                    pickForPrimary = false
                    menuOpen = true
                    menuGroup = 0
                    menuEntry = menuGroups.getOrNull(0)?.entries?.selectableIndices()?.firstOrNull() ?: 0
                    menuFocusDeep = false
                }
            )
        }

        // ---- 播放信息层（按上/下切换，任意键关闭）
        //
        // 与菜单互斥：菜单开着时不叠信息层，避免两层文字打在一起。
        // ---- AI 字幕生成进度悬浮条（右上角，仅生成中时可见）
        AiSubtitleProgressOverlay(
            state = aiSubtitleState,
            modifier = Modifier.align(Alignment.TopEnd)
        )

        if (infoOverlay && !menuOpen && !logOverlay) {
            PlayerInfoOverlay(
                title = viewModel.video.title,
                container = container,
                videoSize = stats.videoSize,
                streamSummary = stats.streamSummary,
                audioLabel = stats.audioOptions
                    .firstOrNull { it.first == stats.currentAudioId }
                    ?.second,
                primaryLabel = primary.label,
                secondaryLabel = secondary.label,
                positionMs = positionMs,
                durationMs = durationMs,
                volume = stats.volume,
                rate = stats.rate,
            )
        }

        // ---- 侧边菜单：一级在右、二级在左（两级）
        //
        // **选择页打开时不再画菜单** —— 选择页的遮罩是半透明的，两层叠在一起会互相透出来
        // （真机反馈："几个透明页面叠加在一起"）。规范 §四 也要求覆盖层**互斥显示**：
        // 层数少了合成更便宜，观感也不会糊成一团。
        if (menuOpen && !pickerOpen) {
            PlayerMenuOverlay(
                groups = menuGroups,
                selectedGroup = menuGroup,
                selectedEntry = menuEntry,
                focusOnEntries = menuFocusDeep,
                adjustActive = adjustActive,
                title = viewModel.video.title
            )
        }

        // ---- 选择页（条目太多时）：**替换**菜单显示，而不是叠在它上面。
        // 选完 / 取消后 `pickerOpen` 归 false，菜单自然又画出来（menuGroup / menuEntry 都还在）。
        if (pickerOpen) {
            PlayerChoicePickerOverlay(
                title = pickerTitle,
                entries = pickerEntries,
                selectedIndex = pickerIndex
            )
        }

        // ---- AI 字幕配置层：从菜单「AI 字幕设置…」跳过来。
        // 播放只暂停不释放，返回即回到画面 —— 用户要的是「能跳过去配一下再回来」。
        if (aiConfigOpen) {
            Box(modifier = Modifier.fillMaxSize().background(BeiGlass.Night)) {
                AiSettingsScreen(
                    settings = viewModel.settings,
                    onBack = { aiConfigOpen = false }
                )
            }
        }

        // ---- 退出确认框：盖在所有东西之上（它就是"没有别的层"时才会出现的那一层）
        if (exitConfirmOpen) {
            PlayerExitConfirmOverlay(
                confirmSelected = exitConfirmConfirm,
                onConfirm = {
                    exitConfirmOpen = false
                    onBack()
                },
                onCancel = { exitConfirmOpen = false }
            )
        }

        // ---- 运行日志覆盖层：把 logcat 里 VLC/音频相关的行直接显示在电视上
        // （这是开发者排查用的层，整屏不透明夜景底，不需要透出画面）
        if (logOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BeiGlass.Ink)
                    .padding(horizontal = 28.dp, vertical = 24.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "运行日志（按任意键关闭）· 共 ${logLines.size} 行 · 构建 ${com.dualsub.tv.BuildConfig.BUILD_TIME}",
                        fontSize = 14.sp,
                        color = BeiGlass.AccentBright
                    )
                    Text(
                        text = "LibVLC 参数：" + VlcPlayerController.LAUNCH_OPTIONS.joinToString(" "),
                        fontSize = 11.sp,
                        color = BeiGlass.TextSecondary
                    )
                    Box(modifier = Modifier.padding(top = 6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        logLines.takeLast(60).forEach { line ->
                            Text(
                                text = line,
                                fontSize = 11.sp,
                                color = when {
                                    line.contains("E/VLC") || line.contains("E/") -> BeiGlass.Danger
                                    line.contains("DualSubTV") -> BeiGlass.Accent
                                    else -> BeiGlass.TextSecondary
                                }
                            )
                        }
                    }
                }
            }
        }

        // 播放失败要**看得见，而且看得懂** —— 结论 / 原因 / 建议 / 详情 分层展示
        val failure = playerError
        if (failure != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BeiGlass.Ink.copy(alpha = 0.88f))
                    .padding(horizontal = 64.dp, vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "⚠ ${failure.title}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = BeiGlass.Danger,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = failure.reason,
                        fontSize = 16.sp,
                        color = BeiGlass.TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    failure.suggestion?.let { suggestion ->
                        Box(
                            modifier = Modifier
                                .background(BeiGlass.AccentFill, RoundedCornerShape(8.dp))
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "> $suggestion",
                                fontSize = 15.sp,
                                color = BeiGlass.AccentBright,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    failure.mediaInfo?.let { info ->
                        Text(
                            text = "已解出的轨道：$info",
                            fontSize = 13.sp,
                            color = BeiGlass.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Text(
                        text = "详情：${failure.detail}",
                        fontSize = 12.sp,
                        color = BeiGlass.TextMuted,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "按「返回」退出本片；按「☰ 菜单」键可打开设置菜单",
                        fontSize = 13.sp,
                        color = BeiGlass.TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (!menuOpen) {
            if (showBuffering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SpinningProgressIndicator()
                        Text(text = "正在缓冲…", fontSize = 14.sp, color = BeiGlass.TextSecondary)
                    }
                }
            }

            // 快进/快退中央 overlay，600ms 淡出
            AnimatedVisibility(
                visible = seekHint != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .background(BeiGlass.Panel, RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text(text = seekHint ?: "", fontSize = 22.sp, color = BeiGlass.TextPrimary)
                }
            }

            // 非致命提示：顶部一行小字。**让位给顶栏** —— 向下错开，否则会压在文件名上。
            val infoText = noticeText
            if (infoText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 40.dp, top = 88.dp)
                        .background(BeiGlass.Panel, RoundedCornerShape(6.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(text = "ℹ $infoText", fontSize = 14.sp, color = BeiGlass.Warning)
                }
            }
        }
    }
}

/**
 * 组装侧边菜单的五个分组。
 *
 * **所有数据都从传入的参数（state 快照）里取，绝不在这里调 viewModel 的 JNI 方法** ——
 * 这个函数会在每次重组时执行，一旦混进 native 调用就会把 UI 线程拖死。
 */
private fun buildMenuGroups(
    viewModel: PlayerViewModel,
    stats: PlaybackStats,
    primary: com.dualsub.tv.player.SubtitleTrack,
    secondary: com.dualsub.tv.player.SubtitleTrack,
    embeddedTracks: List<com.dualsub.tv.media.EmbeddedSubtitleReader.TrackInfo>,
    embeddedTrackStatus: String,
    onPickFile: (Boolean) -> Unit,
    mediaInfo: String?,
    container: String?,
    audioTracks: String,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isBuffering: Boolean,
    lastKeyName: String,
    aspectIndex: Int,
    onAspectChange: (Int) -> Unit,
    subtitlePage: Int,
    onSubtitlePageChange: (Int) -> Unit,
    onShowLog: () -> Unit,
    onOpenAiSettings: () -> Unit,
    /** 打开「选择页」：条目太多时（内嵌字幕轨）把选择挪到整页里做。 */
    onOpenTrackPicker: (List<MenuEntry>, String) -> Unit,
    aiSubtitleState: com.dualsub.tv.ai.AiSubtitleState = com.dualsub.tv.ai.AiSubtitleState.Idle
): List<PlayerMenuGroup> {

    // 内嵌字幕轨不超过这个条数就直接平铺在二级菜单里（省一次按键）；
    // 超过则改用整页选择页 —— 侧边面板塞不下三十条。
    val inlineTrackLimit = 8

    val shortLabels = embeddedShortLabels(embeddedTracks)

    // ---- 1) 字幕设置：主字幕 / 次字幕两段，结构完全对称
    fun subtitleEntries(track: com.dualsub.tv.player.SubtitleTrack, isPrimary: Boolean): List<MenuEntry> = buildList {
        add(MenuEntry.Info("当前", buildString {
            append(track.label)
            if (track.cueCount > 0) append("（").append(track.cueCount).append(" 条）")
            if (track.isLoading) append(" 载入中…")
        }))
        track.error?.let { add(MenuEntry.Info("状态", "⚠ $it")) }

        add(MenuEntry.Header("来源"))
        // 字幕轨选择器：条数少就直接平铺（省一次按键）；多了就换成整页弹页 ——
        // 侧边面板塞不下三十条，硬塞只会让人滚半天还分不清是哪个语言。
        val trackChoices = buildList {
            add(MenuEntry.Choice(
                label = "不使用该路字幕",
                selected = track.source is com.dualsub.tv.player.SubtitleSource.None,
                onSelect = { viewModel.selectNone(isPrimary) }
            ))
            embeddedTracks.forEach { embedded ->
                val shortLabel = shortLabels[embedded] ?: embedded.label
                if (embedded.isBitmap) {
                    // 图片字幕（PGS / DVD SPU / 蓝光）**主字幕位是支持的** —— 它由 libVLC 自己
                    // 解封装并用 SPU/PGS 解码器渲染，这正是把主字幕交给它的收益之一。
                    // 次字幕走的是自绘文本层，没有位图渲染能力，所以这里只标出来。
                    if (isPrimary) {
                        add(MenuEntry.Choice(
                            label = shortLabel,
                            detail = "图片字幕 · 播放器渲染",
                            selected = (track.source as? com.dualsub.tv.player.SubtitleSource.EmbeddedTrack)?.trackIndex == embedded.index,
                            onSelect = { viewModel.selectEmbedded(embedded, isPrimary) }
                        ))
                    } else {
                        add(MenuEntry.Info(shortLabel, "图片字幕，次字幕无法渲染"))
                    }
                    return@forEach
                }
                add(MenuEntry.Choice(
                    label = shortLabel,
                    detail = "内嵌",
                    selected = (track.source as? com.dualsub.tv.player.SubtitleSource.EmbeddedTrack)?.trackIndex == embedded.index,
                    onSelect = { viewModel.selectEmbedded(embedded, isPrimary) }
                ))
            }
        }
        if (embeddedTracks.size <= inlineTrackLimit) {
            addAll(trackChoices)
        } else {
            add(MenuEntry.Action(
                label = "选择字幕轨…",
                detail = "${embeddedTracks.size} 条 · 整页选择",
                onClick = {
                    onOpenTrackPicker(
                        trackChoices,
                        if (isPrimary) "主字幕 · 选择字幕轨" else "次字幕 · 选择字幕轨"
                    )
                }
            ))
        }
        add(MenuEntry.Action(label = "选择外挂字幕文件…", onClick = { onPickFile(isPrimary) }))

        // 次字幕位可以用 AI 生成
        if (!isPrimary) {
            val aiLabel = when (val s = aiSubtitleState) {
                is com.dualsub.tv.ai.AiSubtitleState.Idle -> "AI 自动生成字幕"
                is com.dualsub.tv.ai.AiSubtitleState.Running -> "AI 生成中… ${s.current}/${s.total}"
                is com.dualsub.tv.ai.AiSubtitleState.Done -> "AI 已完成，点击重新生成"
                is com.dualsub.tv.ai.AiSubtitleState.Failed -> "AI 失败：${s.reason.take(30)}"
                is com.dualsub.tv.ai.AiSubtitleState.Live -> "AI 自动生成字幕（实时模式进行中）"
                is com.dualsub.tv.ai.AiSubtitleState.LiveFailed -> "AI 批处理字幕（实时失败，点此改用）"
            }
            add(MenuEntry.Action(label = aiLabel, onClick = { viewModel.generateAiSubtitle() }))

            val isLive = aiSubtitleState is com.dualsub.tv.ai.AiSubtitleState.Live
            val liveLabel = when (val s = aiSubtitleState) {
                is com.dualsub.tv.ai.AiSubtitleState.Live ->
                    "■ 停止实时 AI 字幕（已缓冲 ${s.bufferedMs / 1000}s）"
                is com.dualsub.tv.ai.AiSubtitleState.LiveFailed ->
                    "实时模式：${s.reason.take(28)}…"
                else -> "AI 实时字幕（边看边生成）"
            }
            add(MenuEntry.Action(
                label = liveLabel,
                onClick = {
                    if (isLive) viewModel.stopLiveAiSubtitle()
                    else viewModel.startLiveAiSubtitle()
                }
            ))
            // AI 的配置（API Key / 模型）不在菜单里编辑，而是跳到设置里的 AI 页 ——
            // 扫码、输入这类交互适合全屏页面，插在侧边菜单里会很挤。
            add(MenuEntry.Action(
                label = "AI 字幕设置…",
                detail = "API Key / 模型",
                onClick = onOpenAiSettings
            ))
        }

        add(MenuEntry.Header("显示样式"))
        if (isPrimary) {
            // 主字幕与次字幕现在都由 Compose 自绘（走系统字体栈），所以样式是**可调**的 ——
            // 但播放页只保留最常用的「时间偏移」，字号/颜色这些放到「设置」页里调，
            // 免得侧边菜单一进去就是七八个数值项。
            add(MenuEntry.Info(
                "主字幕样式",
                "字号 / 颜色 / 位置在「设置」页的字幕样式里调，这里只调时间偏移"
            ))
        } else {
            add(MenuEntry.Info(
                "避让主字幕",
                "次字幕叠在主字幕上方；两者重叠时把下面的「底部距离」调大"
            ))
            add(MenuEntry.Adjust(
                label = "字号",
                value = "${track.style.fontSizeSp}",
                onDecrease = { viewModel.changeStyle(isPrimary) { it.copy(fontSizeSp = (it.fontSizeSp - 2).coerceAtLeast(12)) } },
                onIncrease = { viewModel.changeStyle(isPrimary) { it.copy(fontSizeSp = (it.fontSizeSp + 2).coerceAtMost(60)) } }
            ))
            add(MenuEntry.Adjust(
                label = "底部距离",
                value = "${track.style.bottomPaddingDp}",
                onDecrease = { viewModel.changeStyle(isPrimary) { it.copy(bottomPaddingDp = (it.bottomPaddingDp - 8).coerceAtLeast(0)) } },
                onIncrease = { viewModel.changeStyle(isPrimary) { it.copy(bottomPaddingDp = (it.bottomPaddingDp + 8).coerceAtMost(400)) } }
            ))
        }
        add(MenuEntry.Adjust(
            label = "时间偏移",
            value = formatOffset(track.offsetMs),
            onDecrease = { viewModel.adjustOffset(isPrimary, -100L) },
            onIncrease = { viewModel.adjustOffset(isPrimary, 100L) },
            detail = "按 OK 激活后用 ←/→ 调"
        ))
        if (!isPrimary) {
            add(MenuEntry.Adjust(
                label = "描边强度",
                value = "${track.style.outlineWidth.toInt()}",
                onDecrease = { viewModel.changeStyle(isPrimary) { it.copy(outlineWidth = (it.outlineWidth - 1f).coerceAtLeast(0f)) } },
                onIncrease = { viewModel.changeStyle(isPrimary) { it.copy(outlineWidth = (it.outlineWidth + 1f).coerceAtMost(10f)) } }
            ))
        }
    }

    // 字幕设置拆成两页：主字幕与次字幕混排的话，二级面板会有二十多项，滚起来很累。
    val subtitleGroup = PlayerMenuGroup(
        title = "字幕设置",
        entries = if (subtitlePage == 0) {
            buildList {
                // 切页入口放最上面 —— 原先放最底部，要滚到底才看得见
                add(MenuEntry.Action(label = "切换到次字幕设置…", onClick = { onSubtitlePageChange(1) }))
                add(MenuEntry.Info(
                    "内嵌字幕轨",
                    embeddedTrackStatus
                ))
                add(MenuEntry.Action(label = "重新读取字幕轨", onClick = viewModel::retryEmbeddedTracks))
                add(MenuEntry.Header("主字幕"))
                addAll(subtitleEntries(primary, true))
            }
        } else {
            buildList {
                add(MenuEntry.Action(label = "切换到主字幕设置…", onClick = { onSubtitlePageChange(0) }))
                add(MenuEntry.Info(
                    "内嵌字幕轨",
                    embeddedTrackStatus
                ))
                add(MenuEntry.Action(label = "重新读取字幕轨", onClick = viewModel::retryEmbeddedTracks))
                add(MenuEntry.Header("次字幕"))
                addAll(subtitleEntries(secondary, false))
            }
        }
    )

    // ---- 2) 音频设置：音轨切换是 MKV「有画面没声音」的主要解法
    val audioGroup = PlayerMenuGroup(
        title = "音频设置",
        entries = buildList {
            add(MenuEntry.Header("音轨"))
            if (stats.audioOptions.isEmpty()) {
                add(MenuEntry.Info("音轨", "尚未取得（可能仍在解析）"))
            } else {
                stats.audioOptions.forEach { (id, name) ->
                    add(MenuEntry.Choice(
                        label = name,
                        selected = id == stats.currentAudioId,
                        onSelect = { viewModel.selectAudioTrack(id) }
                    ))
                }
            }
            add(MenuEntry.Header("输出"))
            add(MenuEntry.Adjust(
                label = "音量",
                value = "${stats.volume}%",
                onDecrease = { viewModel.setVolume(stats.volume - 10) },
                onIncrease = { viewModel.setVolume(stats.volume + 10) }
            ))
            add(MenuEntry.Info("音轨概览", audioTracks))
        }
    )

    // ---- 3) 视频设置：显示比例
    val ratioOptions = listOf(
        "跟随原始比例" to null,
        "16:9" to "16:9",
        "4:3" to "4:3",
        "1:1" to "1:1",
        "21:9" to "21:9"
    )
    val videoGroup = PlayerMenuGroup(
        title = "视频设置",
        entries = buildList {
            add(MenuEntry.Header("显示比例"))
            ratioOptions.forEachIndexed { index, (label, ratio) ->
                add(MenuEntry.Choice(
                    label = label,
                    selected = index == aspectIndex,
                    onSelect = {
                        viewModel.setAspectRatio(ratio)
                        onAspectChange(index)
                    }
                ))
            }
            add(MenuEntry.Header("画面"))
            add(MenuEntry.Info("分辨率", stats.videoSize))
            add(MenuEntry.Info("容器", container ?: "未识别"))
        }
    )

    // ---- 4) 倍速播放
    val rateOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    val rateGroup = PlayerMenuGroup(
        title = "倍速播放",
        entries = buildList {
            add(MenuEntry.Header("播放速度"))
            rateOptions.forEach { r ->
                add(MenuEntry.Choice(
                    label = if (r == 1f) "1.0x（原速）" else "${r}x",
                    selected = kotlin.math.abs(r - stats.rate) < 0.01f,
                    onSelect = { viewModel.setRate(r) }
                ))
            }
        }
    )

    // ---- 5) 影片详情（只读）—— 原先挂在信息键上的屏上诊断已并入这里
    val stateText = when {
        isBuffering -> "缓冲中（等待数据）"
        isPlaying -> "播放中"
        durationMs > 0L -> "已暂停 / 就绪"
        else -> "未开始（可能仍在连接片源）"
    }
    val detailGroup = PlayerMenuGroup(
        title = "影片详情",
        entries = listOf(
            MenuEntry.Info("标题", viewModel.video.title),
            MenuEntry.Info("片源", viewModel.video.uri.toString()),
            MenuEntry.Info("容器", container ?: "未识别"),
            MenuEntry.Info("编码", mediaInfo ?: "尚未取得"),
            MenuEntry.Info("分辨率", stats.videoSize),
            MenuEntry.Info("音轨", audioTracks),
            MenuEntry.Info("音轨 id", if (stats.currentAudioId < 0) "未挂载（-1）" else stats.currentAudioId.toString()),
            MenuEntry.Info("音量", "${stats.volume}%"),
            MenuEntry.Info("倍速", "${stats.rate}x"),
            MenuEntry.Info("内嵌字幕", "${embeddedTracks.size} 条"),
            MenuEntry.Info("时长", formatTime(durationMs)),
            MenuEntry.Info("当前进度", formatTime(positionMs)),
            MenuEntry.Info("状态", stateText),
            MenuEntry.Info("最后按键", lastKeyName),
            MenuEntry.Info("引擎", "libVLC 3.6.5"),
            MenuEntry.Action(label = "显示运行日志…", onClick = onShowLog)
        )
    )

    return listOf(subtitleGroup, audioGroup, videoGroup, rateGroup, detailGroup)
}

/** 从 Uri 解析出用于显示的文件名。 */
private fun displayNameOf(context: Context, uri: Uri): String {
    runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "外挂字幕"
}

private const val AUTO_HIDE_DELAY_MS = 6000L

/**
 * 右上角时钟的刷新间隔。
 *
 * 一分钟才变一次字，但按秒刷才能保证跨分钟时不迟滞。
 */
private const val CLOCK_TICK_MS = 1000L
/**
 * 按住左/右时的跳转步长。
 *
 * 遥控器长按会**连发同一个键**（`KeyEvent.repeatCount` 递增），这里据此放大步长：
 * 短按（repeatCount = 0）保持 10 秒的精细步进；按住不放逐步加大，最多 60 秒。
 * 这样既能贴着片头微调，也能几秒跨过一整部电影，而不必反复按键。
 */
private fun longPressSeekMs(repeatCount: Int): Long {
    if (repeatCount <= 0) return SEEK_STEP_MS
    val level = (repeatCount / 4).coerceAtMost(5)
    return SEEK_STEP_MS * (level + 1)
}

private const val SEEK_STEP_MS = 10_000L
private const val BUFFERING_HINT_DELAY_MS = 700L
private const val SEEK_HINT_DURATION_MS = 600L

/**
 * 把字节/秒格式化成右上角那行小字：**统一用 `MB/s`**（例如 `2.56 MB/s`）。
 *
 * 不按量级在 kB/s 与 MB/s 之间来回切：同一屏上单位变来变去，扫一眼还得多想一下
 * 「这到底算快不快」。固定成 MB/s 之后，**数字本身大小就是结论**（0.27 → 2.56 → 12.40）。
 * 保留两位小数是为了低速时也看得出变化（一位小数会把 0.27 和 0.31 都抹成 0.3）。
 * 用 US locale 固定小数点，免得某些区域设置把 `.` 显示成 `,`。
 */
private fun formatSpeed(bytesPerSec: Long): String =
    "%.2f MB/s".format(Locale.US, bytesPerSec / 1024.0 / 1024.0)
private const val PREVIEW_RELEASE_TIMEOUT_MS = 300L

/** 无依赖的旋转圆弧缓冲指示器（tv-material 1.0.0 没有 CircularProgressIndicator）。 */
@Composable
private fun SpinningProgressIndicator() {
    val transition = rememberInfiniteTransition(label = "buf")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "buf_angle"
    )
    Canvas(modifier = Modifier.size(48.dp).graphicsLayer { rotationZ = angle }) {
        drawArc(
            color = BeiGlass.Accent,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

// -------- 字幕轨道名称简化工具 --------

private fun normalizeLanguage(code: String?): String? {
    if (code.isNullOrBlank() || code == "und") return null
    return when (code.lowercase().trim()) {
        "chi", "zho", "zh", "zh-hans", "zh-cn", "cmn"  -> "中文"
        "zh-hant", "zh-tw", "zh-hk"                     -> "繁體中文"
        "eng", "en"                                      -> "English"
        "jpn", "ja"                                      -> "日本語"
        "kor", "ko"                                      -> "한국어"
        "fra", "fre", "fr"                               -> "Français"
        "deu", "ger", "de"                               -> "Deutsch"
        "spa", "es"                                      -> "Español"
        "rus", "ru"                                      -> "Русский"
        "por", "pt"                                      -> "Português"
        "ita", "it"                                      -> "Italiano"
        "ara", "ar"                                      -> "العربية"
        "tha", "th"                                      -> "ไทย"
        "vie", "vi"                                      -> "Tiếng Việt"
        "ind", "id"                                      -> "Bahasa Indonesia"
        "cat", "ca"                                      -> "Català"
        "cze", "ces", "cs"                               -> "Čeština"
        "dan", "da"                                      -> "Dansk"
        "dut", "nld", "nl"                               -> "Nederlands"
        "fin", "fi"                                      -> "Suomi"
        "gre", "ell", "el"                               -> "Ελληνικά"
        "heb", "he"                                      -> "עברית"
        "hin", "hi"                                      -> "हिन्दी"
        "hrv", "hr"                                      -> "Hrvatski"
        "hun", "hu"                                      -> "Magyar"
        "kan", "kn"                                      -> "ಕನ್ನಡ"
        "mal", "ml"                                      -> "മലയാളം"
        "may", "msa", "ms"                               -> "Bahasa Melayu"
        "nob", "nb"                                      -> "Norsk bokmål"
        "pol", "pl"                                      -> "Polski"
        "rum", "ron", "ro"                               -> "Română"
        "swe", "sv"                                      -> "Svenska"
        "tam", "ta"                                      -> "தமிழ்"
        "tel", "te"                                      -> "తెలుగు"
        "tur", "tr"                                      -> "Türkçe"
        "ukr", "uk"                                      -> "Українська"
        "fil"                                            -> "Filipino"
        "glg", "gl"                                      -> "Galego"
        "baq", "eus", "eu"                               -> "Euskara"
        "bul", "bg"                                      -> "Български"
        "est", "et"                                      -> "Eesti"
        "lit", "lt"                                      -> "Lietuvių"
        "lav", "lv"                                      -> "Latviešu"
        "slv", "sl"                                      -> "Slovenščina"
        "ice", "isl", "is"                               -> "Íslenska"
        "mac", "mkd", "mk"                               -> "Македонски"
        "srp", "sr"                                      -> "Српски"
        "slo", "slk", "sk"                               -> "Slovenčina"
        "nor", "no"                                      -> "Norsk"
        "mon", "khk", "mn"                               -> "Монгол хэл"
        else                                             -> code
    }
}

// 若 title 是 language 规范名的细化（以规范名开头），则只保留 title 本身（含地区信息）；
// 若 title 与 language 完全重复，则省略 title；否则原样保留。
private val NOISE_TITLES = setOf("subtitlehandler", "subtitle", "texthandler", "text handler")

private fun resolvedTitle(language: String?, title: String?): String? {
    if (title.isNullOrBlank()) return null
    val t = title.trim()
    if (NOISE_TITLES.contains(t.lowercase()) || t.endsWith("Handler", ignoreCase = true)) return null
    val normLang = normalizeLanguage(language) ?: return t
    return when {
        t.startsWith(normLang, ignoreCase = true) -> t
        normalizeLanguage(t) == normLang -> null
        else -> t
    }
}

private fun embeddedShortLabels(
    tracks: List<com.dualsub.tv.media.EmbeddedSubtitleReader.TrackInfo>
): Map<com.dualsub.tv.media.EmbeddedSubtitleReader.TrackInfo, String> {
    if (tracks.isEmpty()) return emptyMap()

    data class Parts(val format: String?, val lang: String?, val title: String?)
    val parts = tracks.map { t ->
        val lang  = normalizeLanguage(t.language)
        val title = resolvedTitle(t.language, t.title)
        // title 已包含 lang 信息（如 "Deutsch (Deutschland)"）→ 不再单独显示 lang
        val effectiveLang = if (title != null && lang != null &&
            title.startsWith(lang, ignoreCase = true)) null else lang
        Parts(
            format = t.format.displayName.takeIf { it.isNotBlank() },
            lang   = effectiveLang,
            title  = title
        )
    }

    val allSameFormat = parts.map { it.format }.toSet().size <= 1
    val allSameLang   = parts.map { it.lang   }.toSet().size <= 1
    val allSameTitle  = parts.map { it.title  }.toSet().size <= 1
    val single        = tracks.size == 1

    return tracks.zip(parts).associate { (track, p) ->
        val kept = buildList {
            if (!allSameFormat || single) p.format?.let { add(it) }
            if (!allSameLang   || single) p.lang?.let { add(it) }
            if (!allSameTitle  || single) p.title?.let { add(it) }
        }
        val base = if (kept.isEmpty()) {
            listOfNotNull(p.lang, p.format, p.title).firstOrNull() ?: "字幕"
        } else {
            kept.joinToString(" · ")
        }
        track to "$base（内嵌 #${track.index}）"
    }
}
