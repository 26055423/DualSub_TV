package com.dualsub.tv.network.smb

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

/**
 * 真正列举服务器上的共享文件夹。
 *
 * 这是「为什么别的 App 只要用户名密码」的答案所在：它们会调用 SMB 之上的
 * SRVSVC `NetShareEnumAll` RPC，把共享清单拉回来让用户点选。
 * smbj 只暴露了文件级 API（`Session.connectShare`），没有这层能力，
 * 所以这里交给 jcifs-ng —— 对 `smb://host/` 调 `listFiles()` 得到的就是共享名列表。
 *
 * 播放路径**不使用**这个库，仍然走 smbj 的 [SmbDataSource]：
 * 两库职责分开，避免动到已经验证过的播放链路。
 */
object SmbShareLister {

    /**
     * 列出 [host] 上对当前账号可见的共享，已按名称排序、去掉管理共享（`C$` / `IPC$` 等）。
     *
     * 失败时抛异常，由调用方决定是否退化为常见共享名探测。
     */
    suspend fun listShares(
        host: String,
        username: String?,
        password: String?,
        domain: String? = null
    ): List<String> = withContext(Dispatchers.IO) {
        val target = host.trim().removePrefix("smb://").trimEnd('/')
        if (target.isEmpty()) return@withContext emptyList()

        val context = buildContext(username, password, domain)
        var root: SmbFile? = null
        try {
            root = SmbFile("smb://$target/", context)
            root.listFiles()
                .mapNotNull { it.name?.trim()?.trimEnd('/') }
                .filter { it.isNotEmpty() && !it.endsWith("$") }
                .distinct()
                .sorted()
        } finally {
            root?.let { file -> runCatching { file.close() } }
            runCatching { context.close() }
        }
    }

    private fun buildContext(username: String?, password: String?, domain: String?): CIFSContext {
        val properties = Properties().apply {
            // 默认超时太长，配置界面等不起
            setProperty("jcifs.smb.client.connTimeout", "5000")
            setProperty("jcifs.smb.client.responseTimeout", "8000")
            setProperty("jcifs.smb.client.soTimeout", "8000")
            // 家庭 NAS 基本都开 SMB2/3，同时允许回退到 SMB1 的老设备
            setProperty("jcifs.smb.client.minVersion", "SMB1")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
        }

        val base = BaseContext(PropertyConfiguration(properties))
        val user = username.orEmpty()
        if (user.isBlank()) return base
        return base.withCredentials(
            NtlmPasswordAuthenticator(domain.orEmpty(), user, password.orEmpty())
        )
    }
}
