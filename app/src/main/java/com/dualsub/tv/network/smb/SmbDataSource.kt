package com.dualsub.tv.network.smb

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.smbj.share.File
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * 让 ExoPlayer 直接播放 `smb://` 地址的 [androidx.media3.datasource.DataSource]。
 *
 * 相比「起一个本地 HTTP 代理再转发」的做法，这里省掉了一个组件、没有端口冲突，
 * 而且天然支持随机读，因此拖动进度条与断点续播都能正常工作。
 *
 * 关键步骤都打了日志（tag `DualSubTV`）：这条链路上出问题时电视上通常看不出来，
 * 得靠 `adb logcat` 判断是卡在「URI 解析 / 凭据查找 / 连接 / 读取」哪一环。
 */
@UnstableApi
class SmbDataSource(
    private val pool: SmbSessionPool,
    private val registry: SmbLocationRegistry
) : BaseDataSource(true) {

    private var file: File? = null
    private var currentUri: Uri? = null
    private var position = 0L

    /** 剩余可读字节数；[C.LENGTH_UNSET] 表示服务器没给出文件长度。 */
    private var bytesRemaining = 0L
    private var opened = false

    /** 中转缓冲。**每次按请求量精确重分配**，理由见 [read]。 */
    private var scratch = ByteArray(0)

    /** 读取失败只记一次日志，避免卡住时刷屏。 */
    private var readErrorLogged = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)

        val uri = dataSpec.uri
        val parsed = SmbPaths.parse(uri.toString())
        if (parsed == null) {
            Log.w(TAG, "open 失败：无法解析 SMB 地址 $uri")
            throw IOException("无法解析 SMB 地址：$uri")
        }

        val location = registry[parsed.host]
        if (location == null) {
            Log.w(TAG, "open 失败：注册表里没有主机「${parsed.host}」，取不到登录信息")
            throw IOException("没有「${parsed.host}」的登录信息，请先在「网络位置」里添加这台服务器")
        }

        Log.i(
            TAG,
            "打开 SMB 文件：host=${parsed.host} share=${location.share} path=${parsed.path} " +
                "用户=${if (location.isAnonymous) "匿名" else location.username}"
        )

        val session = pool.session(location)
        val startedAt = System.currentTimeMillis()
        val handle = try {
            runBlocking { session.openFile(parsed.path) }
        } catch (error: Throwable) {
            Log.w(TAG, "打开 SMB 文件失败（耗时 ${System.currentTimeMillis() - startedAt}ms）", error)
            throw IOException(error.message ?: "打开 SMB 文件失败", error)
        }
        Log.i(TAG, "SMB 文件已打开（耗时 ${System.currentTimeMillis() - startedAt}ms）")

        file = handle
        currentUri = uri
        opened = true

        val size = runCatching { handle.fileInformation.standardInformation.endOfFile }
            .getOrDefault(-1L)
        position = dataSpec.position
        bytesRemaining = when {
            size < 0L -> C.LENGTH_UNSET.toLong()
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            else -> (size - position).coerceAtLeast(0L)
        }
        Log.i(TAG, "文件大小=$size 起始位置=$position 本次可读=$bytesRemaining")

        transferStarted(dataSpec)

        // 长度未解析出来时如实返回 LENGTH_UNSET，交给播放器按「未知长度」处理，
        // 而不是伪造一个巨大的值（那会让进度条与 seek 计算失准）。
        return if (bytesRemaining == C.LENGTH_UNSET.toLong()) C.LENGTH_UNSET.toLong() else bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val handle = file ?: return C.RESULT_END_OF_INPUT
        val unknownLength = bytesRemaining == C.LENGTH_UNSET.toLong()
        val wanted = if (unknownLength) length else minOf(length.toLong(), bytesRemaining).toInt()
        if (wanted <= 0) return C.RESULT_END_OF_INPUT

        // smbj 的 read(byte[], long) 会把**整个数组**填满（或读到 EOF），没法只读一部分，
        // 所以中转缓冲必须按本次请求量精确分配。若图省事只增不减地复用（旧代码就是
        // `if (scratch.size < wanted)`），返回值会大于 wanted，下面按返回值拷贝时就越界 ——
        // 这正是真机上那个 ArrayIndexOutOfBoundsException 的成因。
        if (scratch.size != wanted) scratch = ByteArray(wanted)

        val read = try {
            handle.read(scratch, position)
        } catch (error: Throwable) {
            if (!readErrorLogged) {
                readErrorLogged = true
                Log.w(TAG, "读取 SMB 失败（position=$position，请求 $wanted 字节）", error)
            }
            throw IOException(error.message ?: "读取 SMB 文件失败", error)
        }

        if (read <= 0) return C.RESULT_END_OF_INPUT

        // 双保险：即便底层返回多于请求量，也只交付目标缓冲区放得下的部分
        val delivered = minOf(read, wanted)
        System.arraycopy(scratch, 0, buffer, offset, delivered)
        position += delivered
        if (!unknownLength) bytesRemaining -= delivered
        bytesTransferred(delivered)
        return delivered
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        try {
            runCatching { file?.close() }
        } finally {
            file = null
            currentUri = null
            position = 0L
            bytesRemaining = 0L
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    private companion object {
        const val TAG = "DualSubTV"
    }
}
