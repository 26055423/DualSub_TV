package com.dualsub.tv.network.webdrive

import com.dualsub.tv.network.RemoteEntry
import com.dualsub.tv.network.RemoteBrowser

/**
 * 夸克网盘目录浏览实现。
 *
 * [path] 对应夸克的 fid（文件夹 ID），根目录固定为 "0"。
 * 文件的 [RemoteEntry.location] 也存 fid，[RemoteEntry.playableUri] 在
 * 创建 VideoItem 时由 [QuarkApiClient.downloadUrl] 延迟获取。
 * 此处 list() 里不预先获取直链，避免大量并发请求和直链过期问题。
 */
class QuarkBrowser(private val api: QuarkApiClient) : RemoteBrowser {

    override val rootPath: String = "0"

    override suspend fun list(path: String): List<RemoteEntry> {
        val entries = api.listFiles(path)
        return entries.map { entry ->
            RemoteEntry(
                name = entry.name,
                location = entry.fid,
                isDirectory = entry.isDirectory,
                sizeBytes = entry.size,
                // playableUri 用 quark://fid 占位，播放时由 DataSource 层解析
                playableUri = if (!entry.isDirectory) "quark://${entry.fid}" else null
            )
        }
    }

    override fun close() = Unit
}
