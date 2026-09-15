package com.dualsub.tv.network.smb

/**
 * `smb://host/share/path` 编解码。
 *
 * 单独抽出来是因为它纯粹是字符串处理，可以在 JVM 单测里完整覆盖；
 * 而真正连服务器的那部分没法在单测里跑。
 *
 * 刻意不使用 `android.net.Uri`：它在单元测试里只会返回默认值（null），
 * 且这套解析规则在 smbj 侧要求是确定的。
 */
object SmbPaths {

    const val SCHEME = "smb"

    data class SmbPath(val host: String, val share: String, val path: String) {
        /** 规范化后的完整 URI。 */
        val uri: String get() = build(host, share, path)
    }

    /** 组装 `smb://host/share/path`，各段做百分号编码。 */
    fun build(host: String, share: String, path: String): String {
        val normalized = normalizePath(path)
        val base = "$SCHEME://${host.trim()}/${encodeSegment(share)}"
        return if (normalized.isEmpty()) base else "$base/${encodePath(normalized)}"
    }

    /** 解析 `smb://host/share/path`；非法输入返回 null。 */
    fun parse(uri: String?): SmbPath? {
        val raw = uri?.trim().orEmpty()
        val prefix = "$SCHEME://"
        if (!raw.startsWith(prefix, ignoreCase = true)) return null

        val rest = raw.substring(prefix.length)
        if (rest.isEmpty()) return null

        val slash = rest.indexOf('/')
        val host = (if (slash < 0) rest else rest.substring(0, slash)).trim()
        if (host.isEmpty()) return null
        if (slash < 0) return SmbPath(host, "", "")

        val segments = rest.substring(slash + 1).split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return SmbPath(host, "", "")

        return SmbPath(
            host = host,
            share = decodeSegment(segments.first()),
            path = segments.drop(1).joinToString("/") { decodeSegment(it) }
        )
    }

    /** 去掉开头结尾的斜杠，并把反斜杠统一成正斜杠（SMB 客户端返回的路径常带 `\`）。 */
    fun normalizePath(path: String?): String =
        path.orEmpty().replace('\\', '/').trim('/')

    /** 在 [base] 下拼接 [child]，用于目录浏览的逐级下钻。 */
    fun join(base: String, child: String): String {
        val left = normalizePath(base)
        val right = normalizePath(child)
        return when {
            left.isEmpty() -> right
            right.isEmpty() -> left
            else -> "$left/$right"
        }
    }

    /** 取父路径；已在根时返回空串。 */
    fun parent(path: String?): String {
        val normalized = normalizePath(path)
        val index = normalized.lastIndexOf('/')
        return if (index < 0) "" else normalized.substring(0, index)
    }

    private fun encodePath(path: String): String =
        path.split('/').joinToString("/") { encodeSegment(it) }

    /**
     * 百分号编码。只放行 URL 安全字符，其余（空格、中文、`#`、`?` 等）一律编码，
     * 避免 ExoPlayer 解析 URI 时把 `#`、`?` 当成 fragment/query。
     */
    internal fun encodeSegment(segment: String): String = buildString {
        for (byte in segment.toByteArray(Charsets.UTF_8)) {
            val value = byte.toInt() and 0xFF
            val char = value.toChar()
            if (value < 0x80 && (char.isLetterOrDigit() || char == '-' || char == '_' || char == '.' || char == '~')) {
                append(char)
            } else {
                append('%')
                append(HEX[value ushr 4])
                append(HEX[value and 0x0F])
            }
        }
    }

    internal fun decodeSegment(segment: String): String {
        if ('%' !in segment) return segment
        val out = java.io.ByteArrayOutputStream(segment.length)
        var i = 0
        while (i < segment.length) {
            val c = segment[i]
            if (c == '%' && i + 2 < segment.length + 1 && i + 2 <= segment.length) {
                val hex = segment.substring(i + 1, minOf(i + 3, segment.length))
                val value = hex.toIntOrNull(16)
                if (value != null) {
                    out.write(value)
                    i += 3
                    continue
                }
            }
            out.write(c.code)
            i++
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private val HEX = "0123456789ABCDEF".toCharArray()
}
