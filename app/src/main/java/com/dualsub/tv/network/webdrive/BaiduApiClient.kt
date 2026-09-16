package com.dualsub.tv.network.webdrive

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 百度网盘 Open API 封装。
 *
 * 使用官方开放平台接口，需要 AppKey。
 * 认证走 Device Code Flow（专为无浏览器设备设计）。
 * 文件下载链接通过 filemetas 接口获取（dlink=1），播放需携带 Authorization 头。
 *
 * 官方文档：https://pan.baidu.com/union/doc
 */
class BaiduApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // ── Device Code Flow ────────────────────────────────────

    /**
     * 第一步：获取 Device Code。
     * 返回 [BaiduDeviceCodeResult]，包含显示给用户的 user_code 和 device_code。
     */
    fun getDeviceCode(): BaiduDeviceCodeResult {
        val url = buildString {
            append("https://openapi.baidu.com/oauth/2.0/device/code")
            append("?response_type=device_code")
            append("&client_id=$APP_KEY")
            append("&scope=basic,netdisk")
        }
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            if (root.has("error")) {
                throw BaiduApiException("获取设备码失败：${root.optString("error_description", text)}")
            }
            BaiduDeviceCodeResult(
                deviceCode = root.getString("device_code"),
                userCode = root.getString("user_code"),
                verificationUrl = root.optString("verification_url", "https://openapi.baidu.com/device"),
                expiresIn = root.optInt("expires_in", 1800),
                interval = root.optInt("interval", 5)
            )
        }
    }

    /**
     * 第二步：轮询 Token（用 device_code 换 access_token）。
     * 返回 [BaiduTokenResult] 或 [BaiduPollStatus] 中间状态。
     */
    fun pollToken(deviceCode: String): BaiduPollResult {
        val url = buildString {
            append("https://openapi.baidu.com/oauth/2.0/token")
            append("?grant_type=device_token")
            append("&code=$deviceCode")
            append("&client_id=$APP_KEY")
            append("&client_secret=$APP_SECRET")
        }
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            when {
                root.has("access_token") -> BaiduPollResult.Success(
                    BaiduTokenResult(
                        accessToken = root.getString("access_token"),
                        refreshToken = root.optString("refresh_token", ""),
                        expiresIn = root.optLong("expires_in", 2592000L)
                    )
                )
                root.optString("error") == "authorization_pending" -> BaiduPollResult.Pending
                root.optString("error") == "slow_down" -> BaiduPollResult.SlowDown
                root.optString("error") == "access_denied" -> BaiduPollResult.Denied
                root.optString("error") == "expired_token" -> BaiduPollResult.Expired
                else -> BaiduPollResult.Error(root.optString("error_description", text))
            }
        }
    }

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
        // 申请 AppKey：https://pan.baidu.com/union
        // 请替换为你自己申请的 AppKey / AppSecret
        const val APP_KEY = "YOUR_BAIDU_APP_KEY"
        const val APP_SECRET = "YOUR_BAIDU_APP_SECRET"
        const val BAIDU_UA = "pan.baidu.com"
    }
}

data class BaiduDeviceCodeResult(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresIn: Int,
    val interval: Int
)

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

sealed class BaiduPollResult {
    data class Success(val token: BaiduTokenResult) : BaiduPollResult()
    data object Pending : BaiduPollResult()
    data object SlowDown : BaiduPollResult()
    data object Denied : BaiduPollResult()
    data object Expired : BaiduPollResult()
    data class Error(val message: String) : BaiduPollResult()
}

class BaiduApiException(message: String) : Exception(message)
class BaiduTokenExpiredException : Exception("Access Token 已过期")
