package com.dualsub.tv.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import com.dualsub.tv.ui.shell.ShellTab

/**
 * 顶层导航：**外壳（左侧导航 + 内容区）⇄ 播放页**。
 *
 * 目的地结构在 [ShellTab] 里定义，这里只负责把「当前选中哪个 tab」和「是否正在播放」
 * 两个状态接上去。仍然手写状态机、不引 navigation-compose：目的地只有 3 个，而且
 * **播放页不在外壳里**（见下），navigation 的返回栈在这里反而是负担。
 *
 * 播放页**不走 [AppShell]** —— 看片是沉浸式场景，左侧常驻一条导航栏只会碍事，
 * 所以它由这里直接整屏渲染，返回即回到原来的 tab。
 *
 * 播放页的 ViewModel **不用 `viewModel()` 创建**：那个 API 把实例存进 Activity 级的
 * ViewModelStore，离开播放页时不会清除 —— 于是「返回后视频还在放」，再选另一个视频
 * 又会建出第二个播放器同时播。这里改成跟着视频走的 `remember`，并在离开时显式释放。
 */
@Composable
fun AppRoot(externalVideoUri: Uri? = null) {
    val context = LocalContext.current
    val services = remember { AppServices(context) }
    var playing by remember { mutableStateOf<VideoItem?>(null) }
    var tab by remember { mutableStateOf(ShellTab.Local) }

    LaunchedEffect(externalVideoUri) {
        if (externalVideoUri != null && playing == null) {
            playing = videoItemFrom(context, externalVideoUri)
        }
    }

    val current = playing
    if (current != null) {
        val playerViewModel = remember(current.storageKey) {
            PlayerViewModel(context.applicationContext, services, current)
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
        PlayerScreen(viewModel = playerViewModel, onBack = { playing = null })
        return
    }

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
