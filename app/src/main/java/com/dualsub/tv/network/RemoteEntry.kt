package com.dualsub.tv.network

/** 局域网资源的类型。 */
enum class RemoteType(val displayName: String) {
    SMB("SMB / 共享文件夹"),
    DLNA("DLNA / 媒体服务器"),
    QUARK("夸克网盘"),
    BAIDU("百度网盘")
}

/**
 * 一个网络位置（SMB 服务器、DLNA 媒体服务器或云网盘账号）。
 *
 * 密码按既定选择以**明文**保存在应用私有目录，非 root 设备上其他应用读不到；
 * 与 Kodi 等播放器的默认做法一致。
 * 云网盘的认证凭据（夸克 Cookie / 百度 access_token）存于 [token] 字段，
 * 百度的 refresh_token 存于 [refreshToken]。
 */
data class RemoteLocation(
    /** 稳定标识，用于持久化与去重。 */
    val id: String,
    val type: RemoteType,
    val displayName: String,
    val host: String,
    /** SMB 需要共享名；DLNA 为空。 */
    val share: String? = null,
    val username: String? = null,
    val password: String? = null,
    val domain: String? = null,
    /** DLNA 的设备描述 URL（SSDP 响应里的 LOCATION）。 */
    val descriptionUrl: String? = null,
    /** DLNA 的 ContentDirectory controlURL（来自设备描述，常为相对路径）。 */
    val controlUrl: String? = null,
    /** 云网盘认证凭据：夸克存 Cookie 串，百度存 access_token。 */
    val token: String? = null,
    /** 百度网盘的 refresh_token，其余来源为 null。 */
    val refreshToken: String? = null
) {
    /** 匿名登录（不填账号）时大多数 NAS 也允许访客访问。 */
    val isAnonymous: Boolean get() = username.isNullOrBlank()
}

/** 浏览结果里的一个条目：目录或文件。 */
data class RemoteEntry(
    val name: String,
    /** 该条目自身的定位串。SMB 是共享内相对路径；DLNA 是容器的 ObjectID。 */
    val location: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val modifiedAt: Long = 0L,
    /** 可直接交给播放器的 URI（SMB 的 `smb://`、DLNA 的 `http://`）。目录为 null。 */
    val playableUri: String? = null
) {
    /** 是否为可播放的视频。 */
    val isVideo: Boolean
        get() = !isDirectory && (playableUri != null || VIDEO_EXTENSIONS.any { name.lowercase().endsWith(it) })

    /** 去掉扩展名的展示标题。 */
    val title: String get() = name.substringBeforeLast('.', name)

    companion object {
        val VIDEO_EXTENSIONS = setOf(
            ".mkv", ".mp4", ".avi", ".mov", ".m4v", ".ts", ".m2ts",
            ".wmv", ".flv", ".webm", ".mpg", ".mpeg", ".rmvb"
        )
    }
}
