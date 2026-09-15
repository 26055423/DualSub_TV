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
import java.io.IOException

/**
 * 按 URI scheme 分发的数据源：`smb://` 走 SMB，其余（本地 `content://`、`file://`、
 * DLNA 给出的 `http://`）交给 Media3 内置实现。
 *
 * 这样播放器侧完全不需要知道片源来自哪里 —— 媒体库、SMB、DLNA 用的是同一个入口。
 */
@UnstableApi
class DualSubDataSourceFactory(
    context: Context,
    private val pool: SmbSessionPool,
    private val registry: SmbLocationRegistry
) : DataSource.Factory {

    private val context = context.applicationContext
    private val listeners = mutableListOf<TransferListener>()

    override fun createDataSource(): DataSource = DualSubDataSource(context, pool, registry, listeners)

    fun addTransferListener(listener: TransferListener) {
        listeners += listener
    }
}

@UnstableApi
private class DualSubDataSource(
    private val context: Context,
    private val pool: SmbSessionPool,
    private val registry: SmbLocationRegistry,
    private val listeners: List<TransferListener>
) : DataSource {

    private val defaultFactory = DefaultDataSource.Factory(context)
    private var delegate: DataSource? = null

    override fun open(dataSpec: DataSpec): Long {
        val scheme = dataSpec.uri.scheme?.lowercase()
        val useSmb = scheme == SmbPaths.SCHEME
        Log.i(
            TAG,
            "数据源分发：scheme=$scheme → ${if (useSmb) "SmbDataSource" else "DefaultDataSource"}，uri=${dataSpec.uri}"
        )

        val target: DataSource = if (useSmb) {
            SmbDataSource(pool, registry)
        } else {
            defaultFactory.createDataSource()
        }
        listeners.forEach { target.addTransferListener(it) }
        delegate = target
        return target.open(dataSpec)
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
        /** 与 SmbDataSource 共用同一个 tag，便于一次 logcat 过滤整条播放链路。 */
        const val TAG = "DualSubTV"
    }
}
