package com.dualsub.tv.network.smb

import android.media.MediaDataSource
import com.dualsub.tv.network.RemoteLocation
import com.hierynomus.smbj.share.File
import kotlinx.coroutines.runBlocking

/**
 * 把 SMB 上的文件包装成 [MediaDataSource]，供 `MediaExtractorCompat` 读取内嵌字幕轨。
 *
 * `MediaExtractor.setDataSource` 需要随机读能力，而 `ContentResolver.openFileDescriptor`
 * 只能处理本地内容 URI，因此网络源必须走这条桥。
 *
 * **为什么要做多块缓存**
 *
 * 解析 MKV 的 EBML 结构（找 track entry、读文件末尾的 Cues 索引、读长度前缀）会产生大量
 * **零散且跳跃**的小读取。本地文件无所谓，但在 SMB 上每一次小读都是一次完整的网络往返。
 *
 * 只缓存「最后读的那一块」是不够的：解析器会在文件头与文件尾之间来回跳，一跳就换块、
 * 缓存立刻失效，等于没缓存。所以这里用 **LRU 多块缓存**，把跳跃访问过的块都留住。
 *
 * **跨块请求必须一次读满**
 *
 * `MediaDataSource` 的语义虽然允许只返回一部分，但 `MediaExtractorCompat` 在部分读时
 * 会反复重试同一段（每次都算一次网络往返），实测会让 SMB 上的耗时爆炸。
 * 所以这里对跨块请求做拼接，尽量把调用方要的字节一次给足。
 */
class SmbMediaDataSource(
    private val pool: SmbSessionPool,
    private val location: RemoteLocation,
    private val path: String,
    private val blockSize: Int = 4 * 1024
) : MediaDataSource() {

    private var file: File? = null
    private var size = -1L
    internal var networkReadCount = 0L
        private set
    internal var networkBytesRead = 0L
        private set

    private fun readRemote(opened: File, target: ByteArray, pos: Long, off: Int, count: Int): Int {
        networkReadCount++
        return opened.read(target, pos, off, count).also { if (it > 0) networkBytesRead += it }
    }

    // ---------------------------------------------------------------- 块缓存

    /**
     * 字幕稀疏读取只需 EBML 头部。小块避免读几个字节却预取整 MB 视频数据。
     */

    /** 每个活动字幕窗口最多缓存 2 MiB。 */
    private val maxCachedBytes = 2 * 1024 * 1024

    /** 已缓存块占用的总字节数，用于淘汰。 */
    private var cachedBytes = 0
    private var nextSequentialBlock = -1L

    /**
     * 块号 → 块内容。[accessOrder] = true 使其成为 LRU：
     * 每次 `get` 都会把该块移到队尾，淘汰时从队首（最久未用）开始。
     */
    private val blocks = object : LinkedHashMap<Long, CachedBlock>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, CachedBlock>): Boolean {
            if (cachedBytes <= maxCachedBytes) return false
            cachedBytes -= eldest.value.bytes.size
            return true
        }
    }

    private class CachedBlock(val bytes: ByteArray, val length: Int)

    private fun handle(): File {
        file?.let { return it }
        val opened = runBlocking { pool.session(location).openFile(path) }
        file = opened
        size = opened.fileInformation.standardInformation.endOfFile
        return opened
    }

    /** 求出这次请求实际能读多少字节；越界或空请求返回 null。 */
    private fun resolveWanted(position: Long, length: Int): Int? {
        if (length <= 0 || position < 0) return null
        val total = getSize()
        if (total in 0 until position) return null
        val wanted = if (total >= 0) minOf(length.toLong(), total - position).toInt() else length
        return if (wanted <= 0) null else wanted
    }

    /**
     * 直接读进目标缓冲区。用 smbj 的 `read(buffer, fileOffset, bufferOffset, length)` 精确读，
     * 而不是 `read(buffer, fileOffset)` —— 后者会把**整个数组**读满，返回值可能大于实际想要的长度。
     */
    private fun readDirect(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        val wanted = resolveWanted(position, length) ?: return -1
        val opened = handle()
        val read = readFullyAt(position, buffer, offset, wanted) { target, pos, off, count ->
            readRemote(opened, target, pos, off, count)
        }
        if (read < wanted && size >= 0) {
            throw java.io.EOFException("SMB 文件提前结束：位置 $position，预期 $wanted 字节，实际 $read")
        }
        return read
    }

    /** 取某一块（优先缓存）；失败返回 null。 */
    private fun block(blockIndex: Long): CachedBlock? {
        blocks[blockIndex]?.let { return it }

        val start = blockIndex * blockSize
        // Consecutive misses mean metadata/index traversal. Batch those reads only;
        // random subtitle/video jumps retain small blocks instead of downloading video payload.
        val fetchSize = if (blockIndex == nextSequentialBlock) maxOf(blockSize, 64 * 1024) else blockSize
        val wanted = resolveWanted(start, fetchSize) ?: return null
        val storage = ByteArray(wanted)
        val opened = handle()
        val read = readFullyAt(start, storage, 0, wanted) { target, pos, off, count ->
            readRemote(opened, target, pos, off, count)
        }
        if (read <= 0) return null
        if (read < wanted && size >= 0) {
            throw java.io.EOFException("SMB 文件提前结束：位置 $start，预期 $wanted 字节，实际 $read")
        }
        var offset = 0
        var index = blockIndex
        while (offset < read) {
            val length = minOf(blockSize, read - offset)
            val bytes = storage.copyOfRange(offset, offset + length)
            blocks.remove(index)?.let { cachedBytes -= it.bytes.size }
            cachedBytes += length
            blocks[index] = CachedBlock(bytes, length)
            offset += length
            index++
        }
        nextSequentialBlock = index
        return blocks[blockIndex]
    }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        return try {
            readAtOnce(position, buffer, offset, length)
        } catch (error: Throwable) {
            // 共享句柄失效（SMB 重连 / 池被 invalidate）后，缓存里的 File 就不再可用 ——
            // 这正是真机上「打开文件失败：DiskShare has already been closed」的来源。
            // 丢掉句柄重新打开一次再试，避免一次重连就把整条字幕读取链报废。
            close()
            readAtOnce(position, buffer, offset, length)
        }
    }

    private fun readAtOnce(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position < 0) return -1

        // 请求本身就很大（≥ 一块）时不必走缓存，直接读更省事
        if (length >= blockSize) return readDirect(position, buffer, offset, length)

        var remaining = length
        var written = 0
        var current = position

        // 跨块时逐块拼接，尽量一次给足 —— 见类注释里关于「部分读会引发重试风暴」的说明
        while (remaining > 0) {
            val blockIndex = current / blockSize
            val startInBlock = (current - blockIndex * blockSize).toInt()
            val entry = block(blockIndex) ?: break

            val available = entry.length - startInBlock
            if (available <= 0) break

            val take = minOf(remaining, available)
            System.arraycopy(entry.bytes, startInBlock, buffer, offset + written, take)

            written += take
            remaining -= take
            current += take

            // 只有刚好读满整块时才可能继续往下一块走
            if (take < available) break
        }

        return if (written > 0) written else -1
    }

    override fun getSize(): Long {
        return try {
            handle()
            size
        } catch (error: Throwable) {
            close()
            handle()
            size
        }
    }

    override fun close() {
        runCatching { file?.close() }
        file = null
        blocks.clear()
        cachedBytes = 0
        nextSequentialBlock = -1
    }
}
