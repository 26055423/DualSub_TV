package com.dualsub.tv.network.nas

/**
 * 各家用 NAS 的接入预设。
 *
 * ## 为什么值得单独列出来
 *
 * 「接一台 NAS」在不同品牌上其实**是同一件事的五种叫法**：都要在后台某个角落打开文件共享
 * 协议，都有一组默认端口。让用户自己去查"我家这台应该填几号端口"正是这个页面要消灭的痛苦 ——
 * 所以每家一个入口，进去只填 IP 和账号，端口按 [httpPort] / [httpsPort] 预填并自动探测。
 *
 * 四家的共同点是都提供 **WebDAV**（这也是本项目统一走的协议），差别在默认端口和开启路径。
 *
 * ## 端口数据的来源与风险
 *
 * 下面这些端口取自各家官方文档 / 知识库（2026-09 核对）：
 *
 * | 品牌 | WebDAV |
 * |---|---|
 * | 飞牛 fnOS | 5005 / 5006 |
 * | 群晖 DSM | 5005 / 5006 |
 * | 威联通 QTS | **5000 / 5001** |
 * | 绿联 UGOS Pro | 5005 / 5006 |
 *
 * ⚠️ **威联通那个最容易错**：网上常有人写 8080，但 8080 是 QTS **管理界面**的端口，
 * WebDAV 官方默认是 5000 / 5001。写错的话用户会怎么也连不上。
 *
 * 用户改过端口是常态（尤其是暴露过公网的人），所以表单里端口**可以改**，
 * 这里的值只是默认预填 —— 别把它当成事实来源。
 */
internal enum class NasVendor(
    /** 卡片与表单标题上的名字。 */
    val label: String,
    /** 该家系统的名字，用作副标题（飞牛叫 fnOS、群晖叫 DSM …）。 */
    val systemName: String,
    /** 在该家后台开启 WebDAV 的菜单路径 —— 直接印在表单上，用户照着点就行。 */
    val enablePath: String,
    /** 默认 HTTP 端口。 */
    val httpPort: Int,
    /** 默认 HTTPS 端口。 */
    val httpsPort: Int,
    /** 这一家特有的坑；空串表示没有额外要注意的。 */
    val caveat: String
) {
    FEINIU(
        label = "飞牛 NAS",
        systemName = "fnOS",
        enablePath = "系统设置 → 文件共享协议 → WebDAV",
        httpPort = 5005,
        httpsPort = 5006,
        caveat = "团队文件夹要在它的共享设置里勾选「允许通过文件共享协议挂载」，否则连上了也看不到目录。"
    ),

    SYNOLOGY(
        label = "群晖 NAS",
        systemName = "DSM",
        enablePath = "套件中心安装 WebDAV Server → 设置 → 启用 HTTP / HTTPS",
        httpPort = 5005,
        httpsPort = 5006,
        caveat = "WebDAV 不支持 QuickConnect 地址，请填 IP 或 DDNS 域名。"
    ),

    QNAP(
        label = "威联通 NAS",
        systemName = "QTS",
        enablePath = "控制面板 → 网络和文件服务 → Win/MAC/NFS/WebDAV → WebDAV",
        httpPort = 5000,
        httpsPort = 5001,
        // 这条是四家里最容易踩的：8080 是管理界面，不是 WebDAV。
        caveat = "WebDAV 默认 5000 / 5001，别和 8080 的管理界面混了。"
    ),

    UGREEN(
        label = "绿联 NAS",
        systemName = "UGOS Pro",
        enablePath = "控制面板 → 文件服务 → WebDAV",
        httpPort = 5005,
        httpsPort = 5006,
        caveat = "管理界面是 9999 端口，与 WebDAV 无关。"
    );

    /** 端口预填值：用 HTTP 那一个（探测也是先试 HTTP）。 */
    val defaultPortText: String get() = httpPort.toString()
}
