package com.dualsub.tv.core

import android.content.Context
import androidx.media3.datasource.DataSource
import com.dualsub.tv.data.SettingsStore
import com.dualsub.tv.media.LocalMediaDataSource
import com.dualsub.tv.network.DualSubDataSourceFactory
import com.dualsub.tv.network.MediaSourceProvider
import com.dualsub.tv.network.RemoteBrowserFactory
import com.dualsub.tv.network.dlna.DlnaDiscovery
import com.dualsub.tv.network.smb.SmbDiscovery
import com.dualsub.tv.network.smb.SmbLocationRegistry
import com.dualsub.tv.network.smb.SmbMediaDataSource
import com.dualsub.tv.network.smb.SmbPaths
import com.dualsub.tv.network.smb.SmbSessionPool

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

    val dataSourceFactory: DataSource.Factory = DualSubDataSourceFactory(appContext, smbPool, smbRegistry)

    val browserFactory = RemoteBrowserFactory(smbPool)

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
}
