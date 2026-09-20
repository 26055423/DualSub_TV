package com.dualsub.tv.network

import com.dualsub.tv.network.dlna.DlnaBrowser
import com.dualsub.tv.network.dlna.DlnaDevice
import com.dualsub.tv.network.smb.SmbBrowser
import com.dualsub.tv.network.smb.SmbSessionPool
import com.dualsub.tv.network.webdrive.AliAuthManager
import com.dualsub.tv.network.webdrive.AliBrowser
import com.dualsub.tv.network.webdrive.BaiduAuthManager
import com.dualsub.tv.network.webdrive.BaiduBrowser
import com.dualsub.tv.network.webdrive.QuarkAuthManager
import com.dualsub.tv.network.webdrive.QuarkBrowser
import com.dualsub.tv.network.webdrive.WebDavBrowser

/**
 * 按 [RemoteLocation] 的类型造出对应的浏览实现，UI 层不必区分 SMB 与 DLNA。
 */
class RemoteBrowserFactory(
    private val smbPool: SmbSessionPool,
    private val quarkAuth: QuarkAuthManager,
    private val baiduAuth: BaiduAuthManager,
    private val aliAuth: AliAuthManager
) {

    fun create(location: RemoteLocation): RemoteBrowser = when (location.type) {
        RemoteType.SMB -> SmbBrowser(smbPool.session(location))
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
        RemoteType.WEBDAV -> WebDavBrowser(location)
        // 各家 NAS（飞牛 / 群晖 / 威联通 / 绿联）的接入协议都是它们自带的 WebDAV。
        RemoteType.NAS -> WebDavBrowser(location)
        RemoteType.ALI -> AliBrowser(aliAuth)
    }
}

