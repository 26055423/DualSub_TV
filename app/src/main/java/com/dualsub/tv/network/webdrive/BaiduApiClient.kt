package com.dualsub.tv.network.webdrive

import com.dualsub.tv.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 百度网盘 Open API 封装。
 *
 * 认证走 Device Flow（设备码模式）：TV 显示二维码，用户用手机扫码授权，无需跳转浏览器。
 * 流程：getDeviceCode() → TV 显示 qrcode_url → 每隔 interval 秒 pollDeviceToken() → 授权后直接拿到 access_token。
 * 文件下载链接通过 filemetas 接口获取（dlink=1），播放需携带 Authorization 头。
 */
class BaiduApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // ── 设备码登录（Device Flow）───────────────────────────────

    /**
     * 第一步：获取设备码。
     * 返回 [BaiduQrSession]，qrcodeUrl 直接是可扫的二维码图片地址，expiresIn 单位秒。
     */
    fun getQrCode(): BaiduQrSession {
        val url = buildString {
            append("https://openapi.baidu.com/oauth/2.0/device/code")
            append("?client_id=$APP_KEY")
            append("&response_type=device_code")
            append("&scope=basic,netdisk")
        }
        val request = Request.Builder().url(url).get()
            .addHeader("User-Agent", BAIDU_UA)
            .build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            if (root.has("error")) {
                throw BaiduApiException("获取设备码失败：${root.optString("error_description", text)}")
            }
            BaiduQrSession(
                qrcodeKey = root.getString("device_code"),
                qrcodeUrl = root.getString("qrcode_url"),
                expiresIn = root.optInt("expires_in", 300),
                interval = root.optInt("interval", 5)
            )
        }
    }

    /**
     * 第二步：轮询授权状态（每隔 [BaiduQrSession.interval] 秒调用一次）。
     * - [BaiduQrStatus.Waiting]：用户尚未扫码或授权
     * - [BaiduQrStatus.Authorized]：授权成功，直接携带 access_token 和 refresh_token
     * - [BaiduQrStatus.Expired]：设备码已过期
     * - [BaiduQrStatus.Denied]：用户拒绝授权
     */
    fun pollQrStatus(deviceCode: String): BaiduQrStatus {
        val url = buildString {
            append("https://openapi.baidu.com/oauth/2.0/token")
            append("?grant_type=device_token")
            append("&code=$deviceCode")
            append("&client_id=$APP_KEY")
            append("&client_secret=$APP_SECRET")
        }
        val request = Request.Builder().url(url).get()
            .addHeader("User-Agent", BAIDU_UA)
            .build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            when {
                root.has("access_token") -> BaiduQrStatus.Authorized(
                    accessToken = root.getString("access_token"),
                    refreshToken = root.optString("refresh_token", ""),
                    expiresIn = root.optLong("expires_in", 2592000L)
                )
                else -> when (root.optString("error")) {
                    "authorization_pending" -> BaiduQrStatus.Waiting
                    "expired_token" -> BaiduQrStatus.Expired
                    "access_denied" -> BaiduQrStatus.Denied
                    else -> BaiduQrStatus.Waiting
                }
            }
        }
    }

    /** Device Flow 授权成功后直接拿到 token，不需要单独的 exchangeToken 步骤。保留此方法以兼容调用方。 */
    fun exchangeToken(accessToken: String, refreshToken: String, expiresIn: Long): BaiduTokenResult =
        BaiduTokenResult(accessToken = accessToken, refreshToken = refreshToken, expiresIn = expiresIn)

    /**
     * 刷新 access_token（refresh_token 有效期 10 年）。
     */
    fun refreshToken(refreshToken: String): BaiduTokenResult {
        val url = buildString {
            append("https://openapi.baidu.com/oauth/2.0/token")
            append("?grant_type=refresh_token")
            append("&refresh_token=$refreshToken")
            append("&client_id=$APP_KEY")
            append("&client_secret=$APP_SECRET")
        }
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            if (!root.has("access_token")) {
                throw BaiduApiException("刷新 Token 失败：${root.optString("error_description", text)}")
            }
            BaiduTokenResult(
                accessToken = root.getString("access_token"),
                refreshToken = root.optString("refresh_token", refreshToken),
                expiresIn = root.optLong("expires_in", 2592000L)
            )
        }
    }

    // ── 文件操作 ─────────────────────────────────────────────

    /**
     * 列出指定目录下的文件（dir 为路径字符串，如 "/"）。
     */
    fun listFiles(accessToken: String, dir: String, start: Int = 0): List<BaiduEntry> {
        val url = buildString {
            append("https://pan.baidu.com/rest/2.0/xpan/file")
            append("?method=list")
            append("&access_token=$accessToken")
            append("&dir=${java.net.URLEncoder.encode(dir, "UTF-8")}")
            append("&order=name")
            append("&start=$start")
            append("&limit=100")
            append("&web=1")
            append("&folder=0")
            append("&showempty=1")
        }
        val request = Request.Builder().url(url).get()
            .addHeader("User-Agent", BAIDU_UA)
            .build()

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            val errno = root.optInt("errno", -1)
            if (errno != 0) {
                if (errno == -6) throw BaiduTokenExpiredException()
                throw BaiduApiException("列目录失败（errno=$errno）：$text")
            }
            val list = root.optJSONArray("list") ?: return emptyList()
            (0 until list.length()).map { i ->
                val item = list.getJSONObject(i)
                BaiduEntry(
                    fsId = item.getLong("fs_id"),
                    path = item.getString("path"),
                    name = item.getString("server_filename"),
                    isDir = item.optInt("isdir", 0) == 1,
                    size = item.optLong("size", 0L),
                    category = item.optInt("category", 0)
                )
            }
        }
    }

    /**
     * 获取文件的 dlink（间接下载链接）。
     * 使用 dlink 播放时需要携带 Authorization 和 User-Agent 头。
     */
    fun getDlink(accessToken: String, fsId: Long): String {
        val url = buildString {
            append("https://pan.baidu.com/rest/2.0/xpan/multimedia")
            append("?method=filemetas")
            append("&access_token=$accessToken")
            append("&fsids=[${fsId}]")
            append("&dlink=1")
            append("&thumb=0")
        }
        val request = Request.Builder().url(url).get()
            .addHeader("User-Agent", BAIDU_UA)
            .build()

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            val errno = root.optInt("errno", -1)
            if (errno != 0) {
                if (errno == -6) throw BaiduTokenExpiredException()
                throw BaiduApiException("获取下载链接失败（errno=$errno）")
            }
            val list = root.optJSONArray("list")
                ?: throw BaiduApiException("返回数据格式错误")
            list.getJSONObject(0).getString("dlink")
        }
    }

    companion object {
        val APP_KEY: String get() = BuildConfig.BAIDU_APP_KEY
        val APP_SECRET: String get() = BuildConfig.BAIDU_APP_SECRET
        const val BAIDU_UA = "pan.baidu.com"
    }
}

data class BaiduQrSession(
    val qrcodeKey: String,
    val qrcodeUrl: String,
    val expiresIn: Int,
    val interval: Int = 5
)

sealed class BaiduQrStatus {
    data object Waiting : BaiduQrStatus()
    /** Device Flow 授权成功，token 直接在这里，无需再调 exchangeToken。 */
    data class Authorized(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long
    ) : BaiduQrStatus()
    data object Expired : BaiduQrStatus()
    data object Denied : BaiduQrStatus()
}

data class BaiduTokenResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)

data class BaiduEntry(
    val fsId: Long,
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val category: Int
)

class BaiduApiException(message: String) : Exception(message)
class BaiduTokenExpiredException : Exception("Access Token 已过期")
