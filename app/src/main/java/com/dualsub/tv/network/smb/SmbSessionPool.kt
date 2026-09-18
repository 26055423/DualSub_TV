package com.dualsub.tv.network.smb

import com.dualsub.tv.network.RemoteLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * 按主机复用 SMB 连接。
 *
 * Media3 DataSource 在每次 seek 后会重新 `open`，如果每次都重新握手会明显卡顿，
 * 因此把 [SmbSession] 缓存起来按主机复用。浏览界面与播放器共用同一份连接。
 */
class SmbSessionPool {

    private val sessions = ConcurrentHashMap<String, SmbSession>()

    fun session(location: RemoteLocation): SmbSession {
        val key = location.host.lowercase()
        return sessions.computeIfAbsent(key) { SmbSession(location) }
    }

    /** 服务器配置变了（换了账号/共享）时丢弃旧连接。 */
    fun invalidate(host: String) {
        sessions.remove(host.lowercase())?.close()
    }

    fun clear() {
        sessions.values.forEach { runCatching { it.close() } }
        sessions.clear()
    }
}

/**
 * 供 DataSource 按主机名反查登录信息。
 *
 * 播放器只拿到 `smb://host/share/path` 这个 URI，账号密码必须靠浏览时登记的信息补上。
 */
class SmbLocationRegistry {

    private val byHost = ConcurrentHashMap<String, RemoteLocation>()

    fun register(location: RemoteLocation) {
        byHost[location.host.lowercase()] = location
    }

    fun registerAll(locations: List<RemoteLocation>) {
        locations.forEach { register(it) }
    }

    fun clear() = byHost.clear()

    operator fun get(host: String): RemoteLocation? = byHost[host.lowercase()]
}
