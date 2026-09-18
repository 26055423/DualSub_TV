package com.dualsub.tv.core

import android.content.Context
import com.dualsub.tv.ai.AiSubtitleGenerator
import com.dualsub.tv.data.SettingsStore
import com.dualsub.tv.media.LocalMediaDataSource
import com.dualsub.tv.network.MediaSourceProvider
import com.dualsub.tv.network.RemoteBrowserFactory
import com.dualsub.tv.network.dlna.DlnaDiscovery
import com.dualsub.tv.network.smb.SmbDiscovery
import com.dualsub.tv.network.smb.SmbLocationRegistry
import com.dualsub.tv.network.smb.SmbMediaDataSource
import com.dualsub.tv.network.smb.SmbPaths
import com.dualsub.tv.network.smb.SmbSessionPool
import com.dualsub.tv.network.webdrive.AliAuthManager
import com.dualsub.tv.network.webdrive.BaiduAuthManager
import com.dualsub.tv.network.webdrive.QuarkAuthManager

/**
 * 应用级的依赖容器。
 *
 * 规模还不到需要 Hilt 的程度，手写一个集中式容器即可：所有跨页面的长生命周期对象
 * （SMB 连接池、位置注册表、数据源工厂、偏好存储）都在这里创建一次。
 */
class AppServices(context: Context) {

    val appContext: Context = context.applicationContext

    val settings = SettingsStore(appContext)

    /** SMB 连接按主机复用，浏览与播放共享。 */
    val smbPool = SmbSessionPool()

    /** 播放器只拿到 `smb://host/...`，账号密码靠这里按主机反查。 */
    val smbRegistry = SmbLocationRegistry()

    /** 夸克网盘登录状态管理（Cookie 持久化由 NetworkScreen 写入 SettingsStore）。 */
    val quarkAuth = QuarkAuthManager()

    /** 百度网盘登录状态管理（Token 持久化由 NetworkScreen 写入 SettingsStore）。 */
    val baiduAuth = BaiduAuthManager()

    /** 阿里云盘登录状态管理（Token 持久化由 NetworkScreen 写入 SettingsStore）。 */
    val aliAuth = AliAuthManager()

    val browserFactory = RemoteBrowserFactory(smbPool, quarkAuth, baiduAuth, aliAuth)

    val dlnaDiscovery = DlnaDiscovery(appContext)

    /** 扫描局域网里的 SMB 服务器，省得用户自己去查 NAS 的 IP。 */
    val smbDiscovery = SmbDiscovery(appContext)

    /**
     * 抽内嵌字幕用的随机读数据源。
     *
     * DLNA 走 http，要支持随机读得自行实现 Range 请求，本版本没有做，
     * 因此这类片源暂时无法读取内嵌字幕轨（外挂字幕不受影响）。
     */
    val mediaSources = MediaSourceProvider { uri ->
        when (uri.scheme?.lowercase()) {
            SmbPaths.SCHEME -> {
                val parsed = SmbPaths.parse(uri.toString())
                val location = parsed?.let { smbRegistry[it.host] }
                if (parsed == null || location == null) {
                    null
                } else {
                    SmbMediaDataSource(smbPool, location, parsed.path)
                }
            }

            "content", "file", null -> runCatching { LocalMediaDataSource(appContext, uri) }.getOrNull()

            else -> null
        }
    }

    val aiSubtitleGenerator = AiSubtitleGenerator(appContext, mediaSources)
}
