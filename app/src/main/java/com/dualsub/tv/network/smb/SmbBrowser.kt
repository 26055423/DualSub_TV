package com.dualsub.tv.network.smb

import com.dualsub.tv.network.RemoteBrowser
import com.dualsub.tv.network.RemoteEntry

/** SMB 的浏览实现，直接复用 [SmbSession]。 */
class SmbBrowser(private val session: SmbSession) : RemoteBrowser {

    override val rootPath: String = ""

    override suspend fun list(path: String): List<RemoteEntry> = session.list(path)

    override fun close() {
        session.close()
    }
}
