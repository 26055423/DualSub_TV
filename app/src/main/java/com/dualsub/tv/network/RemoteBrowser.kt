package com.dualsub.tv.network

/**
 * 局域网目录浏览的统一接口。SMB 与 DLNA 各有一个实现，
 * UI 侧只认这个接口，因此浏览页可以对两种来源完全复用。
 */
interface RemoteBrowser : AutoCloseable {

    /** 列出 [path] 下的条目。DLNA 的 path 是容器 ObjectID，SMB 的是共享内相对路径。 */
    suspend fun list(path: String): List<RemoteEntry>

    /** 根路径的表示。SMB 是空串，DLNA 是 "0"（ContentDirectory 的根容器）。 */
    val rootPath: String
}
