package com.dualsub.tv.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.media.MediaLibraryScanner
import com.dualsub.tv.media.VideoItem
import com.dualsub.tv.ui.format.formatTime
import com.dualsub.tv.ui.shell.BeiCard
import com.dualsub.tv.ui.shell.BeiPageHeader
import com.dualsub.tv.ui.shell.BeiPillButton
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 媒体库首页：扫描 MediaStore 并用遥控器可操作的**横向行**展示。
 *
 * 页面自己**不画标题栏，也不画「网络位置」入口** —— 那两样都在 [com.dualsub.tv.ui.shell.AppShell]
 * 里（顶部标签常驻、内容区只放内容）。
 *
 * ## 为什么是「横向行」而不是「网格」
 *
 * 网格的移动语义是"上下左右都算移动"，遥控器上要看清自己在第几行第几列；横向行把
 * **主移动方向统一成左右**（一屏内滑过更多片子），上下只用来**换分组** —— 这也是
 * 主机游戏机那套界面的做法。分组用**文件夹**：扫描结果本来就带 `folderName`，
 * 而且"同一部剧的几集在一起"正是用户找片子时的真实心理单位。
 *
 * 只有**一个**文件夹时不再额外加一行"全部视频" —— 那两行内容会一模一样。
 *
 * 行用 [LazyRow]、整体用 [LazyColumn]：两者方向不同，嵌套是允许的，而且各自只组合
 * 屏幕内的项。`androidx.tv:tv-foundation:1.0.0` 不提供 lazy 网格/行组件，所以都用
 * Compose 标准库那套；卡片本身是自绘的 [BeiCard]，焦点态与 D-pad 导航都由它保证。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenVideo: (VideoItem) -> Unit
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

    val rows = remember(videos) { libraryRows(videos) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BeiPageHeader(
                title = "本地媒体库",
                subtitle = "本机与外接存储里的视频；片子在 NAS 上请到顶部「网络」里添加共享"
            )
            Spacer(modifier = Modifier.weight(1f))
            BeiPillButton(label = "重新扫描", onClick = { refreshToken++ })
        }

        when {
            !granted -> PermissionPrompt(onRequest = { permissionLauncher.launch(permission) })

            scanning -> StatusText("正在扫描媒体库…")

            videos.isEmpty() -> EmptyLibrary()

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp, start = 2.dp, end = 2.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(rows, key = { it.title }) { row ->
                    LibraryRowSection(
                        title = row.title,
                        videos = row.videos,
                        onOpenVideo = onOpenVideo
                    )
                }
            }
        }
    }
}

/** 一行：分组标题 + 该组内的片子。 */
private data class LibraryRow(val title: String, val videos: List<VideoItem>)

/**
 * 把扫描结果切成横向行。
 *
 * 多文件夹时首行是「全部视频」（最常用的入口，找刚下好的片子不用先想起它在哪个文件夹），
 * 之后每个文件夹一行、按名字排序 —— 排序是为了让行序在两次扫描之间保持稳定，
 * 否则每次进页面各行的位置都在变，遥控器上很难形成肌肉记忆。
 */
private fun libraryRows(videos: List<VideoItem>): List<LibraryRow> {
    if (videos.isEmpty()) return emptyList()
    val byFolder = videos.groupBy { it.folderName.ifBlank { "本机" } }.toSortedMap()
    return buildList {
        if (byFolder.size > 1) add(LibraryRow("全部视频", videos))
        byFolder.forEach { (folder, items) -> add(LibraryRow(folder, items)) }
    }
}

/** 一个横向行：小标题 + 可左右滑动的一排卡片。 */
@Composable
private fun LibraryRowSection(
    title: String,
    videos: List<VideoItem>,
    onOpenVideo: (VideoItem) -> Unit
) {
    Column {
        Text(
            text = "$title · ${videos.size}",
            color = BeiGlass.TextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(
            // 两端各留 2dp：卡片获得焦点会放大 1.03，贴边时描边不该被裁掉。
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(BeiDims.CardGap)
        ) {
            items(videos, key = { it.storageKey }) { video ->
                VideoCard(video = video, onClick = { onOpenVideo(video) })
            }
        }
    }
}

/**
 * 视频卡片：**16:9 封面占位 + 片名 + 一行小字**。
 *
 * 封面按 16:9 出（视频本来就是 16:9），比固定高度更像"视频缩略图"；本机扫描不做
 * 缩略图解码（TV 上逐帧解码很贵），所以用**夜景抬升色渐变** + 一个香槟金播放符号占位。
 *
 * **宽度固定成 [VIDEO_CARD_WIDTH]**：横向行里再 `fillMaxWidth` 会让每个卡片都撑满，
 * 一行只剩一张。320dp 在 1080p 电视上一屏能滑过五张半，既看得清片名也还有"后面还有"的暗示。
 *
 * 早先这里是一块浅蓝渐变（浅色外壳时代"白卡 + 淡蓝封面"的做法）。深墨夜景下那块浅蓝
 * 会亮得像没加载出来，所以改成与背景同族、只抬一档的钢蓝渐变 —— 占位块应该"安静"。
 */
@Composable
private fun VideoCard(video: VideoItem, onClick: () -> Unit) {
    BeiCard(
        onClick = onClick,
        modifier = Modifier.width(VIDEO_CARD_WIDTH),
        padding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = BeiDims.CardRadius, topEnd = BeiDims.CardRadius))
                .background(
                    // 只用两档夜景底做一点明暗差，不再引入第三种"抬升色"
                    Brush.linearGradient(
                        colors = listOf(BeiGlass.Night, BeiGlass.Ink)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // 播放符号：暗示"这是视频"，也避免大片空白显得没加载出来
            Canvas(modifier = Modifier.size(26.dp)) {
                drawPath(
                    path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(0f, size.height)
                        close()
                    },
                    color = BeiGlass.Accent.copy(alpha = 0.9f)
                )
            }
            // 右下角时长角标
            if (video.durationMs > 0L) {
                Text(
                    text = formatTime(video.durationMs),
                    color = BeiGlass.TextPrimary,
                    fontSize = BeiDims.TinySize,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BeiGlass.Panel)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = video.title,
                color = BeiGlass.TextPrimary,
                fontSize = BeiDims.CardTitleSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = video.folderName.ifBlank { "本机" },
                color = BeiGlass.TextMuted,
                fontSize = BeiDims.CaptionSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 横向行里一张卡的固定宽度 —— 见 [VideoCard] 的说明。 */
private val VIDEO_CARD_WIDTH = 320.dp

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 26.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "需要读取本地视频的权限才能建立媒体库。",
            color = BeiGlass.TextPrimary,
            fontSize = BeiDims.CardTitleSize
        )
        Text(
            text = "电视只会读取视频文件本身，不会上传任何内容。",
            color = BeiGlass.TextMuted,
            fontSize = BeiDims.BodySize
        )
        BeiPillButton(label = "授予权限", onClick = onRequest)
    }
}

@Composable
private fun EmptyLibrary() {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "本机没有扫描到视频。",
            color = BeiGlass.TextPrimary,
            fontSize = BeiDims.CardTitleSize
        )
        Text(
            text = "片子都在 NAS 或电脑共享里的话，请在顶部「网络」里添加 SMB 服务器；" +
                "插了 U 盘可以点右上角「重新扫描」让系统重新索引。",
            color = BeiGlass.TextMuted,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun StatusText(message: String) {
    Text(
        text = message,
        color = BeiGlass.TextSecondary,
        fontSize = BeiDims.CardTitleSize,
        modifier = Modifier.padding(top = 26.dp)
    )
}

/** Android 13 起改用细分的媒体权限。 */
private fun requiredVideoPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
