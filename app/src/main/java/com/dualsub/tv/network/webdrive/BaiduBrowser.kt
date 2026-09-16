package com.dualsub.tv.network.webdrive

import com.dualsub.tv.network.RemoteBrowser
import com.dualsub.tv.network.RemoteEntry

/**
 * 百度网盘目录浏览实现。
 *
 * [path] 为绝对路径字符串（如 "/"、"/视频"），根目录固定为 "/"。
 * 文件的 [RemoteEntry.playableUri] 使用 "baidu://fsid" 占位，播放时由
 * DataSource 层通过 [BaiduApiClient.getDlink] 延迟解析为实际 http 直链。
 */
class BaiduBrowser(
    private val auth: BaiduAuthManager
) : RemoteBrowser {

    override val rootPath: String = "/"

    override suspend fun list(path: String): List<RemoteEntry> {
        val token = auth.accessToken ?: throw IllegalStateException("未登录百度网盘")
        val entries = try {
            auth.apiClient().listFiles(token, path)
        } catch (e: BaiduTokenExpiredException) {
            val newToken = auth.tryRefreshToken()
                ?: throw IllegalStateException("百度网盘授权已过期，请重新登录")
            auth.apiClient().listFiles(newToken, path)
        }

        return entries.map { entry ->
            RemoteEntry(
                name = entry.name,
                location = entry.path,
                isDirectory = entry.isDir,
                sizeBytes = entry.size,
                playableUri = if (!entry.isDir) "baidu://${entry.fsId}" else null
            )
        }
    }

    override fun close() = Unit
}
