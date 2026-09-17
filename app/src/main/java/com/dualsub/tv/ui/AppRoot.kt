package com.dualsub.tv.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dualsub.tv.core.AppServices
import com.dualsub.tv.media.VideoItem
import com.dualsub.tv.ui.library.LibraryScreen
import com.dualsub.tv.ui.network.NetworkScreen
import com.dualsub.tv.ui.player.PlayerScreen
import com.dualsub.tv.ui.player.PlayerViewModel

/**
 * 极简导航：媒体库 ⇄ 网络位置 ⇄ 播放页。
 *
 * 目的地很少，手写状态机比引入 navigation-compose 更轻，
 * 也避免在 TV 上额外处理返回栈与转场动画。
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
    var browsingNetwork by remember { mutableStateOf(false) }

    LaunchedEffect(externalVideoUri) {
        if (externalVideoUri != null && playing == null) {
            playing = videoItemFrom(context, externalVideoUri)
        }
    }

    val current = playing
    when {
        current != null -> {
            val playerViewModel = remember(current.storageKey) {
                PlayerViewModel(context.applicationContext, services, current)
            }
            DisposableEffect(playerViewModel) {
                onDispose {
                    // 离开页面即释放播放器和字幕任务，不保存进度
                    playerViewModel.release()
                }
            }
            PlayerScreen(viewModel = playerViewModel, onBack = { playing = null })
        }

        browsingNetwork -> NetworkScreen(
            services = services,
            onOpenVideo = { playing = it },
            onExit = { browsingNetwork = false }
        )

        else -> LibraryScreen(
            onOpenVideo = { playing = it },
            onOpenNetwork = { browsingNetwork = true }
        )
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
