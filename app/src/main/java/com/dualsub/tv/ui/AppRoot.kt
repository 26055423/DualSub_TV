package com.dualsub.tv.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dualsub.tv.core.AppServices
import com.dualsub.tv.media.VideoItem
import com.dualsub.tv.ui.library.LibraryScreen
import com.dualsub.tv.ui.network.NetworkScreen
import com.dualsub.tv.ui.player.PlayerScreen
import com.dualsub.tv.ui.player.PlayerViewModel
import com.dualsub.tv.ui.settings.SettingsScreen
import com.dualsub.tv.ui.shell.AppShell
import com.dualsub.tv.ui.shell.BeiConfirmDialog
import com.dualsub.tv.ui.shell.ShellTab

/**
 * 顶层导航：**外壳（顶部标签 + 内容区）⇄ 播放页**。
 *
 * ## 播放页为什么是"叠上去"而不是"换掉"
 *
 * 早先写的是 `if (playing != null) { PlayerScreen(); return }` —— 那个 `return` 会让整个
 * `AppShell` 子树**退出组合**，于是里面所有 `remember` 状态都被丢掉：浏览到第几层目录、
 * 列表滚到哪儿、焦点在谁身上……退出播放回来时全部重建，用户就从"刚播完的那个文件夹"
 * 被打回了主页。
 *
 * 现在改成**外壳始终渲染、播放页叠在它上面**：外壳的状态原封不动，退出播放直接回到原处。
 * 代价是底层页面在播放期间仍留在组合树里（不在屏幕上，但仍会重组）—— 对一个静态列表页
 * 而言这点开销可以接受，换来的是"从哪儿来、回哪儿去"。
 *
 * 焦点不会被底层抢走：`PlayerScreen` 自己是整屏 `focusable()`、内部会请求焦点，
 * 遥控器事件都落在它身上；外壳那个返回键的 `BackHandler` 也显式在播放期间禁用。
 *
 * ## 其它仍然手写状态机的理由
 *
 * 目的地只有 3 个，而且播放页不属于任何 tab —— navigation-compose 的返回栈在这里是负担。
 *
 * 播放页的 ViewModel **不用 `viewModel()` 创建**：那个 API 把实例存进 Activity 级的
 * ViewModelStore，离开播放页时不会清除 —— 于是「返回后视频还在放」，再选另一个视频
 * 又会建出第二个播放器同时播。这里改成跟着视频走的 `remember`，并在离开时显式释放
 * （见 [PlayerLayer]）。
 *
 * ## 返回键的层序（外壳这一层只兜最后一步）
 *
 * 各页自己的 `BackHandler` **优先级更高**，会先吃掉返回键：目录浏览 → 上/子页；
 * NAS / 云盘子页 → 网络主页；SMB / WebDAV / NAS 表单 → 卡片行。走到这里就说明
 * **已经站在外壳首页了**，此时：
 *
 * 1. 确认框开着 → 关掉它（＝取消）；
 * 2. 否则 → 弹出「退出应用？」确认框。
 *
 * 确认框走的是**正常的 Compose 焦点**（`BeiConfirmDialog`），默认焦点在「取消」——
 * 退出应用是破坏性操作，不该是落点。
 */
@Composable
fun AppRoot(externalVideoUri: Uri? = null) {
    val context = LocalContext.current
    val services = remember { AppServices(context) }
    var playing by remember { mutableStateOf<VideoItem?>(null) }
    var tab by remember { mutableStateOf(ShellTab.Local) }
    var exitConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(externalVideoUri) {
        if (externalVideoUri != null && playing == null) {
            playing = videoItemFrom(context, externalVideoUri)
        }
    }

    val current = playing

    Box(modifier = Modifier.fillMaxSize()) {
        AppShell(current = tab, onSelect = { tab = it }) { padding ->
            // 外壳只给四周基准留白，页面自己决定内部怎么排（它们更清楚自己要多宽）。
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    ShellTab.Local -> LibraryScreen(onOpenVideo = { playing = it })

                    ShellTab.Network -> NetworkScreen(
                        services = services,
                        onOpenVideo = { playing = it }
                    )

                    ShellTab.Settings -> SettingsScreen(settings = services.settings)
                }
            }
        }

        // 播放页叠在外壳之上（同一个 Box 里后写的画得更高）。
        // **不要**在这里"换掉"外壳 —— 那会丢掉它的全部状态，见类注释。
        if (current != null) {
            PlayerLayer(
                context = context,
                services = services,
                video = current,
                onExit = { playing = null }
            )
        }
    }

    // 外壳这一层的返回键（内层页面自己的 BackHandler 优先级更高，见上方说明）。
    // 播放期间**禁用** —— 那一层的返回键由 PlayerScreen 自己管（含「退出播放？」确认框）。
    BackHandler(enabled = current == null) {
        if (exitConfirm) exitConfirm = false else exitConfirm = true
    }

    // 画在最上面 —— Compose 里后写的在同一层画得更高，确认框才会盖住页面。
    if (exitConfirm) {
        BeiConfirmDialog(
            title = "退出 DualSub TV？",
            message = "确认后回到电视桌面。",
            confirmLabel = "退出",
            onConfirm = {
                exitConfirm = false
                // finish() 而不是杀进程：让系统按正常流程回收，回到桌面。
                context.findActivity()?.finish()
            },
            onCancel = { exitConfirm = false }
        )
    }
}

/**
 * 播放页那一层：ViewModel 的生命周期、屏幕常亮、前后台暂停恢复都挂在这里。
 *
 * 单独拆成一个 composable，是为了让这些 `remember` / `DisposableEffect` **跟着视频走**
 * （`remember(video.storageKey)`），而不是跟着 `AppRoot` 的重组走 —— 后者会让同一段
 * 视频在每次重组时都被重建一次播放器。
 */
@Composable
private fun PlayerLayer(
    context: Context,
    services: AppServices,
    video: VideoItem,
    onExit: () -> Unit
) {
    val playerViewModel = remember(video.storageKey) {
        PlayerViewModel(context.applicationContext, services, video)
    }

    DisposableEffect(playerViewModel) {
        onDispose {
            // 离开页面即释放播放器和字幕任务，不保存进度
            playerViewModel.release()
        }
    }

    // 播放期间保持屏幕常亮。
    //
    // 电视自己的自动屏保会在**长时间没有按键交互**时熄屏，顺带销毁 SurfaceView 的
    // surface —— 解除之后 libVLC 的视频输出未必能自动接回，表现为「声音正常、画面全黑」。
    // 这里是第一道防线（从源头不让屏保来）；[PlayerViewModel.onAppForeground] 里的
    // vout 重挂是第二道，兜住"还是被打断了"的情况。
    val rootView = LocalView.current
    DisposableEffect(rootView) {
        val previous = rootView.keepScreenOn
        rootView.keepScreenOn = true
        onDispose { rootView.keepScreenOn = previous }
    }

    // 监听 Lifecycle：Home 键切到后台时暂停，回到前台时恢复
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(playerViewModel, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP  -> playerViewModel.onAppBackground()
                Lifecycle.Event.ON_START -> playerViewModel.onAppForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PlayerScreen(viewModel = playerViewModel, onBack = onExit)
}

/**
 * 沿 `ContextWrapper` 往上找宿主 Activity。
 *
 * `LocalContext.current` 拿到的**未必直接是 Activity** —— 在 Compose 里它常常是
 * 一层 `ContextThemeWrapper` 之类包着 Activity 的壳，直接 `as Activity` 会 `ClassCastException`。
 * 只有真的拿到 Activity 才谈得上 `finish()`。
 */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** 为「从文件管理器用本应用打开」进来的 Uri 构造一个条目。 */
private fun videoItemFrom(context: Context, uri: Uri): VideoItem {
    var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "外部视频"
    var sizeBytes = 0L

    runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameColumn >= 0) {
                        cursor.getString(nameColumn)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeColumn >= 0) sizeBytes = cursor.getLong(sizeColumn)
                }
            }
    }

    return VideoItem(
        uri = uri,
        displayName = displayName,
        durationMs = 0L,
        sizeBytes = sizeBytes,
        folderName = "外部打开"
    )
}
