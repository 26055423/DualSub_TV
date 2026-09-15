package com.dualsub.tv.ui.library

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.media.MediaLibraryScanner
import com.dualsub.tv.media.VideoItem
import com.dualsub.tv.ui.format.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 媒体库首页：扫描 MediaStore 并用遥控器可操作的网格展示。
 *
 * 网格用的是 Compose 标准库的 [LazyVerticalGrid] 而不是 TV 版本 ——
 * `androidx.tv:tv-foundation:1.0.0` 并不提供 lazy 网格组件（只有 list 系列）。
 * 每个卡片本身是 `androidx.tv.material3.Card`，自带焦点态与缩放，D-pad 导航依然可用。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenVideo: (VideoItem) -> Unit,
    onOpenNetwork: () -> Unit
) {
    val context = LocalContext.current
    val permission = remember { requiredVideoPermission() }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result -> granted = result }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(permission)
    }

    LaunchedEffect(granted, refreshToken) {
        if (granted) {
            scanning = true
            videos = withContext(Dispatchers.IO) { MediaLibraryScanner.scan(context) }
            scanning = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E16))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp, end = 40.dp, top = 32.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "DualSub TV",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "同时挂载主字幕与次字幕的播放器",
                        fontSize = 14.sp,
                        color = Color(0xFF90A4AE)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onOpenNetwork) {
                        Text(text = "网络位置", fontSize = 15.sp)
                    }
                    Button(onClick = { refreshToken++ }) {
                        Text(text = "重新扫描", fontSize = 15.sp)
                    }
                }
            }

            when {
                !granted -> PermissionPrompt(
                    onRequest = { permissionLauncher.launch(permission) }
                )

                scanning -> StatusText("正在扫描媒体库…")

                videos.isEmpty() -> Column(
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "本机没有扫描到视频。",
                        fontSize = 16.sp
                    )
                    Text(
                        text = "如果片子都在 NAS 或电脑共享里，请点上方「网络位置」添加 SMB 服务器；" +
                            "插了 U 盘的话可以点「重新扫描」让系统重新索引。",
                        fontSize = 14.sp,
                        color = Color(0xFFB0BEC5)
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 8.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(videos, key = { it.storageKey }) { video ->
                        VideoCard(video = video, onClick = { onOpenVideo(video) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VideoCard(video: VideoItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1B2436), Color(0xFF2C3E5A))
                    )
                ),
            contentAlignment = Alignment.BottomStart
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = video.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        if (video.folderName.isNotBlank()) append(video.folderName).append(" · ")
                        append(formatTime(video.durationMs))
                    },
                    fontSize = 12.sp,
                    color = Color(0xFFB0BEC5),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "需要读取本地视频的权限才能建立媒体库。",
            fontSize = 16.sp
        )
        Button(onClick = onRequest) {
            Text(text = "授予权限", fontSize = 15.sp)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StatusText(message: String) {
    Text(
        text = message,
        fontSize = 16.sp,
        color = Color(0xFFB0BEC5),
        modifier = Modifier.padding(40.dp)
    )
}

/** Android 13 起改用细分的媒体权限。 */
private fun requiredVideoPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
