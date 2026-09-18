package com.dualsub.tv.network.smb

import com.dualsub.tv.network.RemoteEntry
import com.dualsub.tv.network.RemoteLocation
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * 一个 SMB 服务器的连接会话。
 *
 * smbj 的 API 是阻塞式的，所以所有对外方法都跑在 [Dispatchers.IO] 上。
 * 连接本身是线程安全的，可以被 Media3 DataSource 与浏览界面同时使用。
 *
 * `RemoteLocation.share` 为空时只建立会话、不打开共享 —— 这条路径专门用于
 * 「先登录，再找出这台服务器上有哪些共享」的配置流程。
 */
class SmbSession(private val location: RemoteLocation) : Closeable {

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    /** 建立连接并按需打开共享；重复调用是幂等的。 */
    suspend fun ensureConnected() = withContext(Dispatchers.IO) {
        SmbSecurity.install()
        if (session != null) return@withContext

        val config = SmbConfig.builder()
            .withTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withSoTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val newClient = SMBClient(config)
        try {
            val newConnection = newClient.connect(location.host)
            val auth = AuthenticationContext(
                location.username.orEmpty(),
                location.password.orEmpty().toCharArray(),
                location.domain
            )
            val newSession = newConnection.authenticate(auth)

            client = newClient
            connection = newConnection
            session = newSession

            val shareName = location.share.orEmpty()
            if (shareName.isNotEmpty()) {
                openShareInternal(shareName)
            }
        } catch (error: Throwable) {
            runCatching { newClient.close() }
            client = null
            connection = null
            session = null
            share = null
            throw SmbException(describe(error), error)
        }
    }

    /**
     * 找出这台服务器上可访问的共享文件夹。
     *
     * 这是**探测而非枚举**：smbj 只实现了 SMB2/3，而真正枚举共享清单要走 SRVSVC 的
     * DCE/RPC（或者早已淘汰的 SMB1 Trans2），为配置流程实现整套 NDR 编解码并不划算。
     * 所以这里改为逐个尝试常见的共享名 —— NAS 与 Windows 的默认共享基本都在候选里，
     * 能成功打开的就会被返回。
     *
     * 一个都没命中时界面会如实提示用户去设备后台查实际共享名，而不是假装服务器没有共享。
     */
    suspend fun probeShares(candidates: List<String> = COMMON_SHARE_NAMES): List<String> =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val current = session ?: throw SmbException("尚未连接到「${location.host}」")
            candidates
                .distinct()
                .filter { name ->
                    runCatching {
                        val opened = current.connectShare(name)
                        runCatching { opened.close() }
                        true
                    }.getOrDefault(false)
                }
        }

    /** 列出共享根目录或某个子目录。 */
    suspend fun list(path: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        ensureConnected()
        val diskShare = share ?: throw SmbException("尚未连接到「${location.host}」")
        val normalized = SmbPaths.normalizePath(path)

        val raw = try {
            diskShare.list(normalized)
        } catch (error: Throwable) {
            throw SmbException("读取目录失败：${describe(error)}", error)
        }

        raw.mapNotNull { it.toRemoteEntry(location, normalized) }
            .sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /** 打开一个文件用于随机读取。调用方负责关闭。 */
    suspend fun openFile(path: String): File = withContext(Dispatchers.IO) {
        ensureConnected()
        val diskShare = share ?: throw SmbException("尚未连接到「${location.host}」")
        try {
            diskShare.openFile(
                SmbPaths.normalizePath(path),
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
        } catch (error: Throwable) {
            throw SmbException("打开文件失败：${describe(error)}", error)
        }
    }

    private fun openShareInternal(shareName: String): DiskShare {
        runCatching { share?.close() }
        val opened = session?.connectShare(shareName) as? DiskShare
            ?: throw SmbException("「${location.host}」上的「$shareName」不是可浏览的磁盘共享")
        share = opened
        return opened
    }

    override fun close() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client?.close() }
        share = null
        session = null
        connection = null
        client = null
    }

    private fun FileIdBothDirectoryInformation.toRemoteEntry(
        location: RemoteLocation,
        parentPath: String
    ): RemoteEntry? {
        val fileName = fileName ?: return null
        // `list` 会带回 `.` 与 `..` 两个伪条目
        if (fileName == "." || fileName == "..") return null

        val attributes = fileAttributes
        val isDirectory = (attributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L

        val childPath = SmbPaths.join(parentPath, fileName)
        return RemoteEntry(
            name = fileName,
            location = childPath,
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) 0L else endOfFile,
            modifiedAt = runCatching { lastWriteTime.toEpochMillis() }.getOrDefault(0L),
            playableUri = if (isDirectory) null else SmbPaths.build(location.host, location.share.orEmpty(), childPath)
        )
    }

    private fun describe(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 30L

        /**
         * 常见共享名候选：覆盖群晖（video / photo / music / homes）、
         * 威联通（Multimedia / Download / Public）以及 Windows 的惯用命名。
         */
        val COMMON_SHARE_NAMES = listOf(
            "video", "videos", "movies", "movie", "media", "multimedia", "tv", "tvshows",
            "anime", "public", "share", "shared", "data", "download", "downloads",
            "photo", "photos", "music", "homes", "home", "users", "backup", "nas"
        )
    }
}

/** SMB 层的可读异常，界面直接把 message 显示给用户。 */
class SmbException(message: String, cause: Throwable? = null) : Exception(message, cause)
