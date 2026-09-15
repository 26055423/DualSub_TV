package com.dualsub.tv.media

import android.net.Uri

/** 媒体库中的一个视频条目。 */
data class VideoItem(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val folderName: String
) {
    /** 用于偏好存储的稳定键。 */
    val storageKey: String get() = uri.toString()

    /** 去掉扩展名的标题，用于列表展示。 */
    val title: String
        get() = displayName.substringBeforeLast('.', displayName)
}
