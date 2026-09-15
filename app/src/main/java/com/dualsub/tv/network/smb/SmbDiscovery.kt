package com.dualsub.tv.network.smb

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

/** 本机当前的 IPv4 地址与前缀长度。 */
data class LocalIpv4(val address: String, val prefixLength: Int)

/**
 * 在局域网里找 SMB 服务器。
 *
 * 做法是对本机所在 C 段并发探测 445 端口 —— 比逐台尝试 SMB 握手快得多，
 * 而且不依赖 NetBIOS/mDNS（家庭网络里这两者经常被路由器拦掉）。
 * 探到之后再让用户填账号密码，由 [SmbSession.probeShares] 找出可访问的共享。
 */
class SmbDiscovery(private val context: Context) {

    /** 探测到的主机地址，按 IP 排序。 */
    suspend fun scan(connectTimeoutMs: Int = 500): List<String> = withContext(Dispatchers.IO) {
        val local = localIpv4() ?: return@withContext emptyList()
        val candidates = SubnetHosts.enumerate(local.address)
        if (candidates.isEmpty()) return@withContext emptyList()

        val semaphore = Semaphore(MAX_CONCURRENT_PROBES)
        val reachable = ArrayList<String>()
        coroutineScope {
            val jobs = candidates.map { host ->
                launch {
                    semaphore.withPermit {
                        if (probe(host, connectTimeoutMs)) {
                            synchronized(reachable) { reachable += host }
                        }
                    }
                }
            }
            jobs.joinAll()
        }
        reachable.sortedBy { SubnetHosts.toSortableLong(it) }
    }

    /** 只做一次 TCP 连接探测；445 通了就认为可能是 SMB 服务器。 */
    private fun probe(host: String, timeoutMs: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, SMB_PORT), timeoutMs)
            true
        }
    }.getOrDefault(false)

    /**
     * 本机所在网段的前缀，形如 `192.168.1.`；取不到（无活动网络）时返回空串。
     *
     * 用来在填写 SMB 服务器地址时预填前三位 —— 只涉及一次轻量的系统查询，
     * 可以直接在点击处理里同步调用。
     */
    fun localSubnetPrefix(): String = SubnetHosts.subnetPrefix(localIpv4()?.address)

    /** 取当前活动网络的 IPv4 地址；Wi-Fi 与有线都会走这里。 */
    fun localIpv4(): LocalIpv4? {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
            ?: return null
        val network = manager.activeNetwork ?: return null
        val properties = manager.getLinkProperties(network) ?: return null

        val link = properties.linkAddresses.firstOrNull { linkAddress ->
            val address = linkAddress.address
            address is Inet4Address && !address.isLoopbackAddress && !address.isAnyLocalAddress
        } ?: return null

        val address = link.address as Inet4Address
        val host = address.hostAddress ?: return null
        return LocalIpv4(host, link.prefixLength)
    }

    private companion object {
        const val SMB_PORT = 445

        /** 并发太高会拖垮老旧路由器，太低则扫完要几十秒。 */
        const val MAX_CONCURRENT_PROBES = 32
    }
}
