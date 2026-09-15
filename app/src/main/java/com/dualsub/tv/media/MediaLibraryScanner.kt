package com.dualsub.tv.media

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * 基于 MediaStore 的视频扫描。
 *
 * 不做全盘遍历：MediaStore 由系统维护索引，扫描快且天然遵守分区存储规则，
 * Android 10+ 也不需要任何「所有文件访问」权限。
 */
object MediaLibraryScanner {

    private val PROJECTION = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME
    )

    private val collection: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    fun scan(context: Context): List<VideoItem> {
        val items = ArrayList<VideoItem>()
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(collection, PROJECTION, null, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val bucketColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = if (idColumn >= 0) cursor.getLong(idColumn) else continue
                    val name = if (nameColumn >= 0) cursor.getString(nameColumn) else null
                    items += VideoItem(
                        uri = Uri.withAppendedPath(collection, id.toString()),
                        displayName = name ?: "未命名视频",
                        durationMs = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L,
                        sizeBytes = if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0L,
                        folderName = if (bucketColumn >= 0) cursor.getString(bucketColumn).orEmpty() else ""
                    )
                }
            }
        }

        return items
    }
}
