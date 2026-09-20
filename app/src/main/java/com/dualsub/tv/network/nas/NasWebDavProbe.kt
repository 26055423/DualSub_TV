package com.dualsub.tv.network.nas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * NAS 的 WebDAV 接入探测（四家品牌共用一套）。
 *
 * ## 为什么单独做一层探测
 *
 * 各家的 WebDAV 都藏在后台某个菜单里，且各有一条自己的默认端口（见 [NasVendor]）。
 * 让用户拿电视遥控器敲出 `http://192.168.1.252:5005` 这种地址，正是本页当初想消灭的痛苦 ——
 * 所以「NAS」入口只让用户填 IP、账号、密码，**端口与协议由这里试出来**。
 *
 * ## 候选顺序为什么是「先 HTTP 后 HTTPS」
 *
 * 各家的 HTTPS 都用自己的自签证书，在本项目的严格 TLS 校验下必然握手失败；
 * 而 HTTP 那条几乎总是通的。所以先把最可能通的那条挑出来，命中即用。
 * 用户自己填了端口时，同一个端口两种协议都试。
 *
 * 这里只回答一个问题：**这个地址上有没有一个认下这组凭据的 WebDAV**。
 * 真正的目录浏览仍然交给 `WebDavBrowser`，两者用同一套 host / username / password。
 */
internal object NasWebDavProbe {

    /** 探测是「用户点了按钮在等」的场景，超时压短，宁可快点报错让用户改地址。 */
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    /** 从用户输入拆出来的目标；端口为 null 表示「按该品牌的默认端口猜」。 */
    data class Target(val host: String, val port: Int?)

    sealed interface Outcome {
        /** 找到一个认下凭据的 WebDAV；[baseUrl] 可直接存进 `RemoteLocation.host`。 */
        data class Reachable(val baseUrl: String) : Outcome

        /** 服务在，但账号密码不对 —— 和「连不上」是两回事，提示必须分开。 */
        data object AuthFailed : Outcome

        /** 地址通了，但那个端口不是 WebDAV（多半是 WebDAV 服务没开）。 */
        data object NotWebDav : Outcome

        /** 没连上；[reason] 是人话，可直接显示给用户。 */
        data class Unreachable(val reason: String) : Outcome
    }

    /**
     * 把用户输入拆成主机与端口。
     *
     * 接受 `192.168.1.252`、`192.168.1.252:5005`、`http://192.168.1.252:5005/` 三种写法 ——
     * 编辑已有位置时传进来的是完整 URL，不该再让用户自己删一遍协议和斜杠。
     */
    fun parse(raw: String): Target {
        val trimmed = raw.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
            .substringBefore('/')
        val separator = trimmed.lastIndexOf(':')
        // 只有一个冒号才算「主机:端口」；IPv6 字面量含多个冒号，整体当主机名处理。
        if (separator > 0 && trimmed.indexOf(':') == separator) {
            trimmed.substring(separator + 1).toIntOrNull()?.let { port ->
                return Target(trimmed.substring(0, separator), port)
            }
        }
        return Target(trimmed, null)
    }

    /**
     * 依次试候选地址，返回第一个能用的（或第一个明确报鉴权失败的）结果。
     *
     * [httpPort] / [httpsPort] 由调用方按品牌给出（见 [NasVendor]）—— 用户自己填了端口时
     * 优先用用户填的，两条协议都试。
     */
    suspend fun probe(
        rawHost: String,
        rawPort: String?,
        username: String?,
        password: String?,
        httpPort: Int,
        httpsPort: Int
    ): Outcome = withContext(Dispatchers.IO) {
        val parsed = parse(rawHost)
        if (parsed.host.isBlank()) return@withContext Outcome.Unreachable("请先填写 NAS 的地址")
        val port = rawPort?.trim()?.toIntOrNull() ?: parsed.port
        val attempts = if (port != null) {
            listOf("http" to port, "https" to port)
        } else {
            listOf("http" to httpPort, "https" to httpsPort)
        }
        var last: Outcome = Outcome.Unreachable("没有可用的地址")
        for ((scheme, candidate) in attempts) {
            val base = "$scheme://${parsed.host}:$candidate"
            when (val outcome = request(base, username, password)) {
                is Outcome.Reachable -> return@withContext outcome
                // 凭据不对说明服务确实在这儿，换个协议再试只会得到同样的 401。
                Outcome.AuthFailed -> return@withContext outcome
                else -> last = outcome
            }
        }
        last
    }

    /**
     * 一次 PROPFIND Depth:0。
     *
     * 用裸 OkHttp 而不是复用 sardine：需要的是**HTTP 状态码**（401 和「连不上」对用户的含义
     * 完全不同），而 sardine 会把它们统一包成异常，区分起来要靠字符串匹配。
     */
    private fun request(base: String, username: String?, password: String?): Outcome {
        val builder = Request.Builder()
            .url("${base.trimEnd('/')}/")
            .method("PROPFIND", null)
            .header("Depth", "0")
        if (!username.isNullOrBlank()) {
            builder.header("Authorization", Credentials.basic(username, password.orEmpty(), Charsets.UTF_8))
        }
        return try {
            client.newCall(builder.build()).execute().use { response ->
                when (response.code) {
                    // WebDAV 成功应答是 207 Multi-Status；少数实现根目录返回 200。
                    207 -> Outcome.Reachable(base)
                    in 200..299 -> Outcome.Reachable(base)
                    401, 403 -> Outcome.AuthFailed
                    404, 405 -> Outcome.NotWebDav
                    else -> Outcome.Unreachable("服务返回 HTTP ${response.code}")
                }
            }
        } catch (error: Exception) {
            Outcome.Unreachable(describe(error))
        }
    }

    private fun describe(error: Exception): String = when (error) {
        is UnknownHostException -> "找不到这个地址，检查 IP 是否写对"
        is SocketTimeoutException -> "连接超时，检查电视和 NAS 是否在同一网段"
        is ConnectException -> "端口没有开"
        is SSLException -> "HTTPS 证书不受信任"
        else -> error.message ?: error.javaClass.simpleName
    }
}
