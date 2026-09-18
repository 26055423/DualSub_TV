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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.core.InAppLog
import com.dualsub.tv.player.VlcPlayerController
import com.dualsub.tv.ui.format.formatOffset
import com.dualsub.tv.ui.format.formatTime
import com.dualsub.tv.ui.player.components.MenuEntry
import com.dualsub.tv.ui.player.components.PlayerControls
import com.dualsub.tv.ui.player.components.PlayerMenuGroup
import com.dualsub.tv.ui.player.components.PlayerMenuOverlay
import com.dualsub.tv.ui.player.components.PlayerInfoOverlay
import com.dualsub.tv.ui.player.components.SubtitleOverlay
import com.dualsub.tv.ui.player.components.selectableIndices
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * 播放页。
 *
 * 层次自下而上：视频画布（[VLCVideoLayout]，**主字幕由 libVLC 的 libass 画在它内部**）
 * → 次字幕层（Compose）→ 控制条 / 侧边菜单 / 提示层。
 *
 * **两路字幕走两条不同的链路**（这是本项目的核心设计，别改回去）：
 * - **主字幕交给 libVLC 渲染** —— 换来完整 libass（ASS 特效 / 定位 / 卡拉 OK）、容器内嵌
 *   字体提取，以及自绘层做不到的图片字幕（PGS / DVD SPU / 蓝光）。代价是它的字号 / 位置 /
 *   描边由 libass 说了算，只有**时间偏移**能动态调（`setSpuDelay`）。
 * - **次字幕由 Compose 叠加层自绘** —— 它只需要显示一行文字，字号 / 底部距离 / 描边 /
 *   时间偏移都能独立调。
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

    // ---- 侧边菜单状态
    var menuOpen by remember { mutableStateOf(false) }
    var menuGroup by remember { mutableStateOf(0) }
    var menuEntry by remember { mutableStateOf(0) }          // 在「可选项」序列里的下标
    var menuFocusDeep by remember { mutableStateOf(false) }  // 焦点是否已进入二级面板

    // 焦点握在自己手里，按键才一定到这个 Box
    val focusRequester = remember { FocusRequester() }

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
    val menuGroups = buildMenuGroups(
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
        onShowLog = { menuOpen = false; logOverlay = true }
    )

    val currentSelectable = menuGroups.getOrNull(menuGroup)?.entries?.selectableIndices() ?: emptyList()

    BackHandler {
        when {
            menuOpen -> menuOpen = false
            else -> onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // 松开左/右键时结束长按预览，恢复播放
                if (event.type == KeyEventType.KeyUp) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight -> {
                            viewModel.resumeFromPreview()
                            true
                        }
                        else -> false
                    }
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // 记下每一次按键的键名，供「影片详情」显示 —— 用来确认 ☰ 实际发的是什么
                lastKeyName = android.view.KeyEvent
                    .keyCodeToString(event.nativeKeyEvent.keyCode)
                    .removePrefix("KEYCODE_")

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

                // ---------- 1) 菜单打开时由菜单全权接管方向键
                if (menuOpen) {
                    val groupCount = menuGroups.size
                    return@onPreviewKeyEvent when (event.key) {
                        Key.Menu, Key.Info -> {
                            menuOpen = false
                            true
                        }

                        Key.DirectionUp -> {
                            if (menuFocusDeep) {
                                val idx = currentSelectable.indexOf(menuEntry)
                                val next = if (idx <= 0) currentSelectable.lastIndex else idx - 1
                                menuEntry = currentSelectable.getOrElse(next) { menuEntry }
                            } else {
                                menuGroup = (menuGroup - 1 + groupCount) % groupCount
                                // 定位到该组第一个**可选项**的下标。写死 0 会落在 Header 上，
                                // 而高亮条件是 index == menuEntry，所以焦点会「看不见」。
                                menuEntry = menuGroups.getOrNull(menuGroup)?.entries?.selectableIndices()?.firstOrNull() ?: 0
                            }
                            true
                        }

                        Key.DirectionDown -> {
                            if (menuFocusDeep) {
                                val idx = currentSelectable.indexOf(menuEntry)
                                val next = if (idx >= currentSelectable.lastIndex) 0 else idx + 1
                                menuEntry = currentSelectable.getOrElse(next) { menuEntry }
                            } else {
                                menuGroup = (menuGroup + 1) % groupCount
                                menuEntry = menuGroups.getOrNull(menuGroup)?.entries?.selectableIndices()?.firstOrNull() ?: 0
                            }
                            true
                        }

                        // 二级面板弹在**左边**，所以方向键按「空间位置」来：
                        // 「右」= 退回一级；「左」= 进入二级（对数值项是减小，增大用确认键）。
                        Key.DirectionRight -> {
                            if (menuFocusDeep) {
                                // 二级面板弹在左边，按右键就是「退回一级」—— 一律如此，
                                // 数值项也不例外（数值的增减用左键减小、确认键增大）。
                                menuFocusDeep = false
                            } else {
                                menuFocusDeep = currentSelectable.isNotEmpty()
                            }
                            true
                        }

                        Key.DirectionLeft -> {
                            if (menuFocusDeep) {
                                val entry = menuGroups.getOrNull(menuGroup)?.entries?.getOrNull(menuEntry)
                                if (entry is MenuEntry.Stepper) entry.onDecrease() else menuFocusDeep = false
                            } else {
                                // 焦点在一级时，左键进入二级 —— 这是用户明确要求的操作
                                menuFocusDeep = currentSelectable.isNotEmpty()
                            }
                            true
                        }

                        Key.Enter, Key.DirectionCenter -> {
                            if (menuFocusDeep) {
                                val entry = menuGroups.getOrNull(menuGroup)?.entries?.getOrNull(menuEntry)
                                when (entry) {
                                    is MenuEntry.Choice -> entry.onSelect()
                                    is MenuEntry.Action -> entry.onClick()
                                    is MenuEntry.Stepper -> entry.onIncrease()
                                    else -> Unit
                                }
                            } else {
                                menuFocusDeep = currentSelectable.isNotEmpty()
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
                    // 松键时 KeyUp 分支会调 resumeFromPreview 恢复播放。
                    Key.DirectionLeft -> {
                        val repeat = event.nativeKeyEvent.repeatCount
                        if (repeat == 0) {
                            viewModel.seekBy(-SEEK_STEP_MS)
                            seekHint = "⏪ -10s"
                        } else {
                            val step = longPressSeekMs(repeat)
                            viewModel.seekPreview(-step)
                            seekHint = "⏪ -${step / 1000}s"
                        }
                        showControls = true
                        true
                    }

                    Key.DirectionRight -> {
                        val repeat = event.nativeKeyEvent.repeatCount
                        if (repeat == 0) {
                            viewModel.seekBy(SEEK_STEP_MS)
                            seekHint = "⏩ +10s"
                        } else {
                            val step = longPressSeekMs(repeat)
                            viewModel.seekPreview(step)
                            seekHint = "⏩ +${step / 1000}s"
                        }
                        showControls = true
                        true
                    }

                    Key.MediaFastForward -> {
                        viewModel.seekBy(SEEK_STEP_MS)
                        seekHint = "⏩ +10s"
                        showControls = true
                        true
                    }

                    Key.MediaRewind -> {
                        viewModel.seekBy(-SEEK_STEP_MS)
                        seekHint = "⏪ -10s"
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

        // 主字幕已交给 libVLC 用 libass 渲染，画在 VLCVideoLayout 内部（见 VlcPlayerController），
        // 这里只画次字幕。
        //
        // 层级上次字幕在 Compose 层、位于主字幕**之上**，所以两路位置重叠时是次字幕盖住主字幕 ——
        // 避让只能靠**位置错开**：次字幕的「底部距离」在字幕设置面板里按主字幕实际高度调大即可。
        // 不再像以前那样按主字幕实测高度自动避位：主字幕已不由我们绘制，拿不到它的高度。
        SubtitleOverlay(
            cue = secondaryCue,
            style = secondary.style,
            positionMs = positionMs - secondary.offsetMs
        )

        if (showControls && !menuOpen) {
            PlayerControls(
                title = viewModel.video.title,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                primaryLabel = primary.label,
                secondaryLabel = secondary.label,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeekBackward = {
                    viewModel.seekBy(-SEEK_STEP_MS)
                    seekHint = "⏪ -10s"
                },
                onSeekForward = {
                    viewModel.seekBy(SEEK_STEP_MS)
                    seekHint = "⏩ +10s"
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
                trackSummary = null
            )
        }

        // ---- 侧边菜单：一级在右，二级向左弹出
        if (menuOpen) {
            PlayerMenuOverlay(
                groups = menuGroups,
                selectedGroup = menuGroup,
                selectedEntry = menuEntry,
                focusOnEntries = menuFocusDeep,
                title = viewModel.video.title
            )
        }

        // ---- 运行日志覆盖层：把 logcat 里 VLC/音频相关的行直接显示在电视上
        if (logOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF0000000))
                    .padding(horizontal = 28.dp, vertical = 24.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "运行日志（按任意键关闭）· 共 ${logLines.size} 行 · 构建 ${com.dualsub.tv.BuildConfig.BUILD_TIME}",
                        fontSize = 14.sp,
                        color = Color(0xFFFFD54F)
                    )
                    Text(
                        text = "LibVLC 参数：" + VlcPlayerController.LAUNCH_OPTIONS.joinToString(" "),
                        fontSize = 11.sp,
                        color = Color(0xFF80CBC4)
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
                                    line.contains("E/VLC") || line.contains("E/") -> Color(0xFFFF8A80)
                                    line.contains("DualSubTV") -> Color(0xFF81D4FA)
                                    else -> Color(0xFFB0BEC5)
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
                    .background(Color.Black.copy(alpha = 0.88f))
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
                        color = Color(0xFFFF8A80),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = failure.reason,
                        fontSize = 16.sp,
                        color = Color(0xFFECEFF1),
                        textAlign = TextAlign.Center
                    )

                    failure.suggestion?.let { suggestion ->
                        Box(
                            modifier = Modifier
                                .background(Color(0x33FFD54F), RoundedCornerShape(8.dp))
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "👉 $suggestion",
                                fontSize = 15.sp,
                                color = Color(0xFFFFD54F),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    failure.mediaInfo?.let { info ->
                        Text(
                            text = "已解出的轨道：$info",
                            fontSize = 13.sp,
                            color = Color(0xFF90A4AE),
                            textAlign = TextAlign.Center
                        )
                    }

                    Text(
                        text = "详情：${failure.detail}",
                        fontSize = 12.sp,
                        color = Color(0xFF607D8B),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "按「返回」退出本片；按「☰ 菜单」键可打开设置菜单",
                        fontSize = 13.sp,
                        color = Color(0xFF78909C),
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
                        Text(text = "正在缓冲…", fontSize = 14.sp, color = Color(0xFFB0BEC5))
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
                        .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text(text = seekHint ?: "", fontSize = 22.sp, color = Color.White)
                }
            }

            // 非致命提示：顶部一行小字，不遮挡画面与控制条
            val infoText = noticeText
            if (infoText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 40.dp, top = 28.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(text = "ℹ $infoText", fontSize = 14.sp, color = Color(0xFFFFD54F))
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
    onShowLog: () -> Unit
): List<PlayerMenuGroup> {

    // ---- 1) 字幕设置：主字幕 / 次字幕两段，结构完全对称
    fun subtitleEntries(track: com.dualsub.tv.player.SubtitleTrack, isPrimary: Boolean): List<MenuEntry> = buildList {
        add(MenuEntry.Info("当前", buildString {
            append(track.label)
            if (track.cueCount > 0) append("（").append(track.cueCount).append(" 条）")
            if (track.isLoading) append(" 载入中…")
        }))
        track.error?.let { add(MenuEntry.Info("状态", "⚠ $it")) }

        add(MenuEntry.Header("来源"))
        add(MenuEntry.Choice(
            label = "不使用该路字幕",
            selected = track.source is com.dualsub.tv.player.SubtitleSource.None,
            onSelect = { viewModel.selectNone(isPrimary) }
        ))
        embeddedTracks.forEach { embedded ->
            if (embedded.isBitmap) {
                // 图片字幕（PGS / DVD SPU / 蓝光）**主字幕位是支持的** —— 它由 libVLC 自己
                // 解封装并用 SPU/PGS 解码器渲染，这正是把主字幕交给它的收益之一。
                // 次字幕走的是自绘文本层，没有位图渲染能力，所以只在那一页提示不支持。
                if (isPrimary) {
                    add(MenuEntry.Choice(
                        label = embedded.label,
                        detail = "图片字幕 · 播放器渲染",
                        selected = (track.source as? com.dualsub.tv.player.SubtitleSource.EmbeddedTrack)?.trackIndex == embedded.index,
                        onSelect = { viewModel.selectEmbedded(embedded, isPrimary) }
                    ))
                } else {
                    add(MenuEntry.Info(embedded.label, "图片字幕，次字幕无法渲染"))
                }
                return@forEach
            }
            add(MenuEntry.Choice(
                label = embedded.label,
                detail = "内嵌",
                selected = (track.source as? com.dualsub.tv.player.SubtitleSource.EmbeddedTrack)?.trackIndex == embedded.index,
                onSelect = { viewModel.selectEmbedded(embedded, isPrimary) }
            ))
        }
        add(MenuEntry.Action(label = "选择外挂字幕文件…", onClick = { onPickFile(isPrimary) }))

        add(MenuEntry.Header("显示样式"))
        if (isPrimary) {
            // 主字幕由 libVLC 的 libass 渲染：字号 / 位置 / 颜色 / 描边都由**片源自带的
            // 特效字幕**决定，改这里的数字不会有任何效果。所以主字幕页不摆这些控件，
            // 免得给出「调了没用」的假开关 —— 只有时间偏移是动态可调的（走 setSpuDelay）。
            add(MenuEntry.Info(
                "主字幕样式",
                "由播放器按片源特效字幕渲染，此处不可调（仅时间偏移可调）"
            ))
        } else {
            add(MenuEntry.Info(
                "避让主字幕",
                "次字幕叠在主字幕上方；两者重叠时把下面的「底部距离」调大"
            ))
            add(MenuEntry.Stepper(
                label = "字号",
                value = "${track.style.fontSizeSp}",
                onDecrease = { viewModel.changeStyle(isPrimary) { it.copy(fontSizeSp = (it.fontSizeSp - 2).coerceAtLeast(12)) } },
                onIncrease = { viewModel.changeStyle(isPrimary) { it.copy(fontSizeSp = (it.fontSizeSp + 2).coerceAtMost(60)) } }
            ))
            add(MenuEntry.Stepper(
                label = "底部距离",
                value = "${track.style.bottomPaddingDp}",
                onDecrease = { viewModel.changeStyle(isPrimary) { it.copy(bottomPaddingDp = (it.bottomPaddingDp - 8).coerceAtLeast(0)) } },
                onIncrease = { viewModel.changeStyle(isPrimary) { it.copy(bottomPaddingDp = (it.bottomPaddingDp + 8).coerceAtMost(400)) } }
            ))
        }
        add(MenuEntry.Stepper(
            label = "时间偏移",
            value = formatOffset(track.offsetMs),
            onDecrease = { viewModel.adjustOffset(isPrimary, -100L) },
            onIncrease = { viewModel.adjustOffset(isPrimary, 100L) }
        ))
        if (!isPrimary) {
            add(MenuEntry.Stepper(
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
            add(MenuEntry.Stepper(
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
            color = Color.White,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
