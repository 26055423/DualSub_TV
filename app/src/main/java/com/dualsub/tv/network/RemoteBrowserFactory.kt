package com.dualsub.tv.network

import com.dualsub.tv.network.dlna.DlnaBrowser
import com.dualsub.tv.network.dlna.DlnaDevice
import com.dualsub.tv.network.smb.SmbBrowser
import com.dualsub.tv.network.smb.SmbSessionPool

/**
 * 按 [RemoteLocation] 的类型造出对应的浏览实现，UI 层不必区分 SMB 与 DLNA。
 */
class RemoteBrowserFactory(private val smbPool: SmbSessionPool) {

    fun create(location: RemoteLocation): RemoteBrowser = when (location.type) {
        RemoteType.SMB -> SmbBrowser(location, smbPool.session(location))
        RemoteType.DLNA -> DlnaBrowser(
            DlnaDevice(
                friendlyName = location.displayName,
                descriptionUrl = location.descriptionUrl.orEmpty(),
                controlUrl = location.controlUrl.orEmpty(),
                host = location.host
            )
        )
    }
}
