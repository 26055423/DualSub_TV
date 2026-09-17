package com.dualsub.tv.media

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import java.io.FileInputStream
import java.io.IOException

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
        ?: throw IOException("无法打开本地媒体")

    private val stream = FileInputStream(descriptor.fileDescriptor)

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position < 0) return -1
        stream.channel.position(position)
        return stream.read(buffer, offset, length)
    }

    override fun getSize(): Long = stream.channel.size()

    override fun close() {
        runCatching { stream.close() }
        runCatching { descriptor.close() }
    }
}
