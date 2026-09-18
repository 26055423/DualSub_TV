package com.dualsub.tv.network.webdrive

import com.dualsub.tv.network.RemoteBrowser
import com.dualsub.tv.network.RemoteEntry
import com.dualsub.tv.network.RemoteLocation
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebDavBrowser(private val location: RemoteLocation) : RemoteBrowser {

    private val sardine = OkHttpSardine().apply {
        if (!location.isAnonymous) {
            setCredentials(location.username.orEmpty(), location.password.orEmpty())
        }
    }

    override val rootPath: String = location.host

    override suspend fun list(path: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        sardine.list(path)
            .drop(1) // 第一条是目录自身，跳过
            .map { res ->
                val href = res.href?.toString()?.let { h ->
                    // sardine 有时返回相对路径，需要补全 host
                    if (h.startsWith("http")) h else {
                        val base = location.host.trimEnd('/')
                        base + "/" + h.trimStart('/')
                    }
                } ?: path
                RemoteEntry(
                    name = res.name ?: href.substringAfterLast('/'),
                    location = href,
                    isDirectory = res.isDirectory,
                    sizeBytes = res.contentLength ?: 0L,
                    playableUri = if (!res.isDirectory) href else null
                )
            }
    }

    override fun close() = Unit
}
