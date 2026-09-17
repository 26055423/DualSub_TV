package com.dualsub.tv.network.smb

import java.io.IOException

/** SMB may return less than requested without reaching EOF. */
internal fun readFullyAt(
    position: Long,
    buffer: ByteArray,
    offset: Int,
    length: Int,
    read: (ByteArray, Long, Int, Int) -> Int
): Int {
    require(position >= 0 && offset >= 0 && length >= 0 && offset <= buffer.size - length)
    if (length == 0) return 0
    var total = 0
    while (total < length) {
        val count = read(buffer, position + total, offset + total, length - total)
        if (count == -1) break
        if (count <= 0 || count > length - total) throw IOException("SMB 返回无效读取长度：$count")
        total += count
    }
    return if (total == 0) -1 else total
}
