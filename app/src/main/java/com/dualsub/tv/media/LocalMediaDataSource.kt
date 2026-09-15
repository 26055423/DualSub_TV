package com.dualsub.tv.media

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import java.io.FileInputStream

/**
 * 本地 `content://` / `file://` 的随机读实现。
 *
 * 之所以不直接用 `MediaExtractor.setDataSource(FileDescriptor)`：
 * [EmbeddedSubtitleReader] 现在对「本地文件」和「SMB 网络文件」一视同仁，
 * 需要一个两边都能提供的统一抽象，而 [MediaDataSource] 正是这个抽象。
 *
 * 注意这里用 [FileInputStream] 而不是 `RandomAccessFile` —— 后者只有接收
 * `File` / `String` 的构造器，没有接收 `FileDescriptor` 的版本。
 */
class LocalMediaDataSource(context: Context, uri: Uri) : MediaDataSource() {

    private val descriptor = context.contentResolver.openFileDescriptor(uri, "r")

    private val stream: FileInputStream? = descriptor?.let {
        runCatching { FileInputStream(it.fileDescriptor) }.getOrNull()
    }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        val handle = stream ?: return -1
        if (position < 0) return -1
        return try {
            handle.channel.position(position)
            val read = handle.read(buffer, offset, length)
            if (read <= 0) -1 else read
        } catch (_: Exception) {
            -1
        }
    }

    override fun getSize(): Long = try {
        stream?.channel?.size() ?: -1L
    } catch (_: Exception) {
        -1L
    }

    override fun close() {
        runCatching { stream?.close() }
        runCatching { descriptor?.close() }
    }
}
