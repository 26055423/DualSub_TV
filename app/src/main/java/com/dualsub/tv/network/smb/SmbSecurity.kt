package com.dualsub.tv.network.smb

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * BouncyCastle provider 的注册。
 *
 * smbj 的 NTLM 认证需要 MD4，而 Android 内置的 BouncyCastle 是 `com.android.org.bouncycastle`
 * 且缺少 smbj 需要的 `org.bouncycastle.crypto` 类，所以必须显式注册随 APK 打包进来的那个 provider。
 *
 * 注意 Android 内置的 provider 也叫 "BC"，名字会冲突，必须先移除再添加。
 */
internal object SmbSecurity {

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            runCatching {
                Security.removeProvider("BC")
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(BouncyCastleProvider())
                }
            }
            // 即使注册失败也不抛：多数 NAS 走 NTLMv2 + 未加密 SMB 时仍可工作，
            // 真正失败会在连接阶段给出可读的错误。
            installed = true
        }
    }
}
