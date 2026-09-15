package com.dualsub.tv.network

import android.media.MediaDataSource
import android.net.Uri

/**
 * 为不同来源的视频提供随机读能力，供 `MediaExtractor` 抽取内嵌字幕使用。
 *
 * 本地文件与 SMB 文件各有实现；DLNA 的 http 资源当前返回 null
 * （那需要基于 Range 请求的实现，尚未提供）。
 */
fun interface MediaSourceProvider {
    fun create(uri: Uri): MediaDataSource?
}
