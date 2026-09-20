package com.dualsub.tv.media

import android.media.MediaDataSource
import androidx.media3.extractor.ExtractorInput
import java.io.EOFException
import java.io.IOException

/** Unlike DefaultExtractorInput, skipping never downloads discarded media payloads. */
internal class RandomAccessExtractorInput(
    private val source: MediaDataSource,
    private var checkActive: () -> Unit,
    private val byteBudget: Long = 8L * 1024 * 1024
) : ExtractorInput {
    private val fileSize = source.size.also { require(it >= 0) { "字幕随机读取需要已知文件长度" } }
    private var cursor = 0L
    private var peekCursor = 0L
    private val cache = ByteArray(1024)
    private var cacheStart = -1L
    private var cacheSize = 0
    var bytesRead = 0L
        private set

    /** A session keeps its source/cache, but each operation has its own cancellation and budget. */
    fun beginRead(checkActive: () -> Unit) {
        this.checkActive = checkActive
        bytesRead = 0L
    }

    fun endRead() { checkActive = {} }

    fun reposition(position: Long) {
        require(position in 0..fileSize)
        checkActive()
        cursor = position
        peekCursor = position
    }

    private fun copyAt(position: Long, target: ByteArray, offset: Int, length: Int): Int {
        checkActive()
        require(offset >= 0 && length >= 0 && offset <= target.size - length)
        if (length == 0) return 0
        if (position >= fileSize) return -1
        if (position < cacheStart || position >= cacheStart + cacheSize) {
            val wanted = minOf(cache.size.toLong(), fileSize - position).toInt()
            if (bytesRead + wanted > byteBudget) throw IOException("字幕读取超过本次流量上限，请重试或使用外挂字幕")
            cacheSize = source.readAt(position, cache, 0, wanted)
            if (cacheSize <= 0) throw EOFException("媒体在 $position 提前结束")
            bytesRead += cacheSize
            cacheStart = position
        }
        val at = (position - cacheStart).toInt()
        val count = minOf(length, cacheSize - at)
        cache.copyInto(target, offset, at, at + count)
        return count
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        val count = copyAt(cursor, target, offset, length)
        if (count > 0) { cursor += count; peekCursor = cursor }
        return count
    }
    override fun peek(target: ByteArray, offset: Int, length: Int): Int {
        val count = copyAt(peekCursor, target, offset, length)
        if (count > 0) peekCursor += count
        return count
    }
    private fun fully(target: ByteArray, offset: Int, length: Int, allowEnd: Boolean, peek: Boolean): Boolean {
        var done = 0
        while (done < length) {
            val count = if (peek) this.peek(target, offset + done, length - done)
                else read(target, offset + done, length - done)
            if (count == -1) {
                if (allowEnd && done == 0) return false
                throw EOFException()
            }
            done += count
        }
        return true
    }
    override fun readFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean) =
        fully(target, offset, length, allowEndOfInput, false)
    override fun readFully(target: ByteArray, offset: Int, length: Int) { readFully(target, offset, length, false) }
    override fun peekFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean) =
        fully(target, offset, length, allowEndOfInput, true)
    override fun peekFully(target: ByteArray, offset: Int, length: Int) { peekFully(target, offset, length, false) }

    override fun skip(length: Int): Int {
        require(length >= 0)
        checkActive()
        val count = minOf(length.toLong(), fileSize - cursor).toInt()
        cursor += count
        peekCursor = cursor
        return count
    }
    override fun skipFully(length: Int, allowEndOfInput: Boolean): Boolean {
        if (length > 0 && cursor == fileSize && allowEndOfInput) return false
        if (skip(length) != length) throw EOFException()
        return true
    }
    override fun skipFully(length: Int) { skipFully(length, false) }
    override fun advancePeekPosition(length: Int, allowEndOfInput: Boolean): Boolean {
        require(length >= 0)
        checkActive()
        if (length > 0 && peekCursor == fileSize && allowEndOfInput) return false
        if (length.toLong() > fileSize - peekCursor) throw EOFException()
        peekCursor += length
        return true
    }
    override fun advancePeekPosition(length: Int) { advancePeekPosition(length, false) }
    override fun resetPeekPosition() { peekCursor = cursor }
    override fun getPeekPosition() = peekCursor
    override fun getPosition() = cursor
    override fun getLength() = fileSize
    override fun <E : Throwable> setRetryPosition(position: Long, e: E) { reposition(position); throw e }
}
