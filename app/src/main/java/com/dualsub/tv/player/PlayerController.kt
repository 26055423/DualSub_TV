package com.dualsub.tv.player

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * ExoPlayer 的薄封装。
 *
 * 关键设计一：**内建文本渲染被显式关闭**（`TRACK_TYPE_TEXT` 与 `TRACK_TYPE_METADATA` 均禁用）。
 * ExoPlayer 的字幕渲染器同一时刻只能输出一路字幕，无法满足「主 + 次」同时显示。
 * 禁用之后，视频内嵌字幕轨依然能被 [com.dualsub.tv.media.EmbeddedSubtitleReader]
 * 用 MediaExtractor 读出来，但画面上不会出现任何 ExoPlayer 自己渲染的字幕，
 * 双字幕完全由我们的叠加层绘制，两路样式/位置/偏移才能各自独立。
 *
 * 关键设计二：**尽量把能播的都播出来**。
 * - [DefaultRenderersFactory.setEnableDecoderFallback] 让首选解码器初始化失败时继续枚举
 *   设备上的其他解码器（含厂商软解），而不是直接判失败 —— 这对 HEVC 的各种 profile 尤其重要；
 * - 扩展渲染器模式设为 [DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON]，
 *   一旦将来能接入软解扩展（Media3 的 ffmpeg/av1 扩展未发布到 Maven，需自行编译）即可自动生效。
 *
 * [dataSourceFactory] 用于让播放器认识 `smb://` 这类自有 scheme；
 * 传 null 时 Media3 的内置数据源会处理 `content://`、`file://`、`http://`。
 */
class PlayerController(
    context: Context,
    dataSourceFactory: DataSource.Factory? = null
) {

    private val appContext = context.applicationContext

    val player: ExoPlayer = ExoPlayer.Builder(
        appContext,
        DefaultRenderersFactory(appContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    )
        .apply {
            if (dataSourceFactory != null) {
                setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            }
        }
        .build()
        .apply {
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_METADATA, true)
                .build()
        }

    fun open(uri: Uri, startPositionMs: Long = 0L) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        if (startPositionMs > 0L) {
            player.seekTo(startPositionMs)
        }
        player.playWhenReady = true
    }

    /**
     * 关掉音频轨并重新准备。
     *
     * 用于「音频编码设备不支持，但画面本身能播」的情况 —— MKV 里常见的 DTS / TrueHD
     * 属于这种：ExoPlayer 默认会因为音频渲染器初始化失败而判整片播放失败，
     * 退化成静音至少能让画面放出来。
     */
    @UnstableApi
    fun disableAudioAndRetry() {
        Log.i(TAG, "禁用音频轨后重新准备")
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
        player.prepare()
        player.playWhenReady = true
    }

    fun attachSurface(surfaceView: SurfaceView) {
        player.setVideoSurfaceView(surfaceView)
    }

    fun release() {
        // 无参的 clearVideoSurface()；clearVideoSurfaceView 需要传回原 SurfaceView
        runCatching { player.clearVideoSurface() }
        runCatching { player.release() }
    }

    private companion object {
        const val TAG = "DualSubTV"
    }
}
