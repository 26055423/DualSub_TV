package com.dualsub.tv.network.smb

/**
 * 从本机 IPv4 地址推导出网段与候选主机。
 *
 * 纯字符串/数值运算，单独抽出来是为了能在 JVM 单测里覆盖 —— 真正的端口探测没法测。
 */
object SubnetHosts {

    /** 单次扫描的主机数量上限。 */
    const val HOSTS_PER_SCAN = 254

    /**
     * 取地址所属 C 段的前缀，形如 `192.168.1.`。
     *
     * 用于在「添加 SMB 服务器」时按本机网段预填前三位 —— 家用 NAS 几乎都在同一网段，
     * 用户只需要补最后一段。非法输入返回空串，调用方据此决定不预填。
     */
    fun subnetPrefix(address: String?): String {
        val octets = parseIpv4(address) ?: return ""
        return "${octets[0]}.${octets[1]}.${octets[2]}."
    }

    /**
     * 列出本机所在 C 段可供扫描的地址（`x.y.z.1` ~ `x.y.z.254`）。
     *
     * 即便本机处于 /16 这类更大的网段，也只扫同一个 C 段：
     * 家庭网络几乎不会跨段，而全扫会慢到不可用。
     */
    fun enumerate(localAddress: String?, exclude: String? = null): List<String> {
        val prefix = subnetPrefix(localAddress)
        if (prefix.isEmpty()) return emptyList()

        val hosts = ArrayList<String>(HOSTS_PER_SCAN)
        for (last in 1..254) {
            val candidate = "$prefix$last"
            if (candidate == exclude) continue
            hosts += candidate
        }
        return hosts
    }

    /** 解析点分十进制 IPv4，非法输入返回 null。 */
    fun parseIpv4(address: String?): IntArray? {
        val parts = address?.trim()?.split('.') ?: return null
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for (index in 0..3) {
            val value = parts[index].toIntOrNull() ?: return null
            if (value !in 0..255) return null
            octets[index] = value
        }
        return octets
    }

    /** 把 IPv4 转成可排序的数值。 */
    fun toSortableLong(address: String?): Long {
        val octets = parseIpv4(address) ?: return 0L
        return ((octets[0].toLong() shl 24) or
            (octets[1].toLong() shl 16) or
            (octets[2].toLong() shl 8) or
            octets[3].toLong())
    }
}
