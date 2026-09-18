package com.dualsub.tv.network.webdrive

import com.dualsub.tv.network.RemoteBrowser
import com.dualsub.tv.network.RemoteEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AliBrowser(private val auth: AliAuthManager) : RemoteBrowser {

    override val rootPath: String = "root"

    override suspend fun list(path: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val token = auth.accessToken ?: throw IllegalStateException("未登录阿里云盘")
        val driveId = auth.driveId ?: throw IllegalStateException("未获取到 drive_id")

        val fileList = try {
            auth.apiClient().listFiles(driveId, path)
        } catch (e: AliTokenExpiredException) {
            val newToken = auth.tryRefreshToken() ?: throw IllegalStateException("授权已过期，请重新登录")
            auth.apiClient().listFiles(driveId, path)
        }

        fileList.items.map { entry ->
            RemoteEntry(
                name = entry.name,
                location = entry.fileId,
                isDirectory = entry.isDirectory,
                sizeBytes = entry.size,
                playableUri = if (!entry.isDirectory) "ali://${entry.driveId}/${entry.fileId}" else null
            )
        }
    }

    override fun close() = Unit
}
