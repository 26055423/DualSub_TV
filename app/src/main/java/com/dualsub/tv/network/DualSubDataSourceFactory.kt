package com.dualsub.tv.network

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.dualsub.tv.network.smb.SmbDataSource
import com.dualsub.tv.network.smb.SmbLocationRegistry
import com.dualsub.tv.network.smb.SmbPaths
import com.dualsub.tv.network.smb.SmbSessionPool
import com.dualsub.tv.network.webdrive.BaiduApiClient
import com.dualsub.tv.network.webdrive.BaiduAuthManager
import com.dualsub.tv.network.webdrive.BaiduTokenExpiredException
import com.dualsub.tv.network.webdrive.QuarkAuthManager
import java.io.IOException
import kotlinx.coroutines.runBlocking

/**
 * 按 URI scheme 分发的数据源：
 * - `smb://` → SmbDataSource
 * - `quark://fid` → 实时获取夸克直链，再走 DefaultDataSource
 * - `baidu://fsid` → 实时获取百度 dlink，再走 DefaultDataSource（携带 UA 头）
 * - 其余（`content://`、`file://`、DLNA 的 `http://`）→ DefaultDataSource
 */
@UnstableApi
class DualSubDataSourceFactory(
    context: Context,
    private val pool: SmbSessionPool,
    private val registry: SmbLocationRegistry,
    private val quarkAuth: QuarkAuthManager,
    private val baiduAuth: BaiduAuthManager
) : DataSource.Factory {

    private val context = context.applicationContext
    private val listeners = mutableListOf<TransferListener>()

    override fun createDataSource(): DataSource =
        DualSubDataSource(context, pool, registry, quarkAuth, baiduAuth, listeners)

    fun addTransferListener(listener: TransferListener) {
        listeners += listener
    }
}

@UnstableApi
private class DualSubDataSource(
    private val context: Context,
    private val pool: SmbSessionPool,
    private val registry: SmbLocationRegistry,
    private val quarkAuth: QuarkAuthManager,
    private val baiduAuth: BaiduAuthManager,
    private val listeners: List<TransferListener>
) : DataSource {

    private val defaultFactory = DefaultDataSource.Factory(context)
    private var delegate: DataSource? = null

    override fun open(dataSpec: DataSpec): Long {
        val scheme = dataSpec.uri.scheme?.lowercase()
        Log.i(TAG, "数据源分发：scheme=$scheme，uri=${dataSpec.uri}")

        val effectiveSpec = when (scheme) {
            SmbPaths.SCHEME -> dataSpec // SMB 直接用，delegate 是 SmbDataSource

            "quark" -> {
                // quark://fid → 获取夸克直链
                val fid = dataSpec.uri.host ?: dataSpec.uri.path?.trimStart('/')
                    ?: throw IOException("无效的夸克 URI：${dataSpec.uri}")
                val url = runCatching {
                    quarkAuth.apiClient().downloadUrl(fid)
                }.getOrElse { throw IOException("夸克直链获取失败：${it.message}", it) }
                Log.i(TAG, "夸克直链：$url")
                dataSpec.withUri(android.net.Uri.parse(url))
            }

            "baidu" -> {
                // baidu://fsid → 获取百度 dlink
                val fsId = dataSpec.uri.host?.toLongOrNull()
                    ?: dataSpec.uri.path?.trimStart('/')?.toLongOrNull()
                    ?: throw IOException("无效的百度 URI：${dataSpec.uri}")
                val dlink = runCatching {
                    kotlinx.coroutines.runBlocking {
                        val token = baiduAuth.accessToken
                            ?: throw IOException("未登录百度网盘")
                        try {
                            baiduAuth.apiClient().getDlink(token, fsId)
                        } catch (e: BaiduTokenExpiredException) {
                            val newToken = baiduAuth.tryRefreshToken()
                                ?: throw IOException("百度网盘授权已过期，请重新登录")
                            baiduAuth.apiClient().getDlink(newToken, fsId)
                        }
                    }
                }.getOrElse { throw IOException("百度直链获取失败：${it.message}", it) }
                Log.i(TAG, "百度 dlink：$dlink")
                // 百度 dlink 需要 Authorization 头，用 buildUpon() 继承原 DataSpec 并追加
                dataSpec.buildUpon()
                    .setUri(android.net.Uri.parse(dlink))
                    .setHttpRequestHeaders(
                        mapOf(
                            "User-Agent" to BaiduApiClient.BAIDU_UA,
                            "Authorization" to "Bearer ${baiduAuth.accessToken}"
                        )
                    )
                    .build()
            }

            else -> dataSpec
        }

        val target: DataSource = if (effectiveSpec.uri.scheme?.lowercase() == SmbPaths.SCHEME) {
            SmbDataSource(pool, registry)
        } else {
            defaultFactory.createDataSource()
        }
        listeners.forEach { target.addTransferListener(it) }
        delegate = target
        return target.open(effectiveSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length) ?: throw IOException("数据源尚未打开")

    override fun getUri(): android.net.Uri? = delegate?.uri

    override fun close() {
        try {
            delegate?.close()
        } finally {
            delegate = null
        }
    }

    override fun addTransferListener(transferListener: TransferListener) {
        // 监听器由工厂统一持有，每次创建 delegate 时统一挂上
    }

    private companion object {
        const val TAG = "DualSubTV"
    }
}
