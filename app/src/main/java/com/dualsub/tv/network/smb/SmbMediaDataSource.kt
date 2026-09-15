package com.dualsub.tv.network.smb

import android.media.MediaDataSource
import com.dualsub.tv.network.RemoteLocation
import com.hierynomus.smbj.share.File
import kotlinx.coroutines.runBlocking

/**
 * 把 SMB 上的文件包装成 [MediaDataSource]，供 `MediaExtractor` 读取内嵌字幕轨。
 *
 * `MediaExtractor.setDataSource` 需要随机读能力，而 `ContentResolver.openFileDescriptor`
 * 只能处理本地内容 URI，因此网络源必须走这条桥。
 */
class SmbMediaDataSource(
    private val pool: SmbSessionPool,
    private val location: RemoteLocation,
    private val path: String
) : MediaDataSource() {

    private var file: File? = null
    private var size = -1L

    /** 中转缓冲。**每次按请求量精确重分配**，理由见 [readAt]。 */
    private var scratch = ByteArray(0)

    private fun handle(): File {
        file?.let { return it }
        val opened = runBlocking { pool.session(location).openFile(path) }
        file = opened
        size = runCatching { opened.fileInformation.standardInformation.endOfFile }.getOrDefault(-1L)
        return opened
    }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position < 0) return -1
        val total = getSize()
        if (total in 0 until position) return -1

        val wanted = if (total >= 0) minOf(length.toLong(), total - position).toInt() else length
        if (wanted <= 0) return -1

        // 与 SmbDataSource.read 同一个坑：smbj 的 read(byte[], long) 会把**整个数组**读满，
        // 没法只读一部分。中转缓冲若只增不减地复用，返回值就会大于 wanted，拷贝时越界。
        if (scratch.size != wanted) scratch = ByteArray(wanted)

        val read = handle().read(scratch, position)
        if (read <= 0) return -1

        // 双保险：只交付目标缓冲区放得下的部分
        val delivered = minOf(read, wanted)
        System.arraycopy(scratch, 0, buffer, offset, delivered)
        return delivered
    }

    override fun getSize(): Long = runCatching { handle(); size }.getOrDefault(-1L)

    override fun close() {
        runCatching { file?.close() }
        file = null
    }
}
