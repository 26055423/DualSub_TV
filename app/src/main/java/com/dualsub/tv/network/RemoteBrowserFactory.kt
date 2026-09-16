package com.dualsub.tv.network

import com.dualsub.tv.network.dlna.DlnaBrowser
import com.dualsub.tv.network.dlna.DlnaDevice
import com.dualsub.tv.network.smb.SmbBrowser
import com.dualsub.tv.network.smb.SmbSessionPool
import com.dualsub.tv.network.webdrive.BaiduAuthManager
import com.dualsub.tv.network.webdrive.BaiduBrowser
import com.dualsub.tv.network.webdrive.QuarkApiClient
import com.dualsub.tv.network.webdrive.QuarkAuthManager
import com.dualsub.tv.network.webdrive.QuarkBrowser

/**
 * 按 [RemoteLocation] 的类型造出对应的浏览实现，UI 层不必区分 SMB 与 DLNA。
 */
class RemoteBrowserFactory(
    private val smbPool: SmbSessionPool,
    private val quarkAuth: QuarkAuthManager,
    private val baiduAuth: BaiduAuthManager
) {

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
        RemoteType.QUARK -> QuarkBrowser(quarkAuth.apiClient())
        RemoteType.BAIDU -> BaiduBrowser(baiduAuth)
    }
}

