package com.dualsub.tv.network.webdrive

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 百度网盘 Open API 封装。
 *
 * 使用官方开放平台接口，需要 AppKey。
 * 认证走二维码登录流程（TV 显示二维码，用户用手机扫码授权）。
 * 文件下载链接通过 filemetas 接口获取（dlink=1），播放需携带 Authorization 头。
 *
 * 官方文档：https://pan.baidu.com/union/doc
 */
class BaiduApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // ── 二维码登录 ───────────────────────────────────────────

    /**
     * 第一步：获取二维码信息。
     * 返回 [BaiduQrSession]，包含显示给用户的二维码图片 URL 和轮询用的 qrcode_key。
     */
    fun getQrCode(): BaiduQrSession {
        val url = buildString {
            append("https://openapi.baidu.com/oauth/2.0/qrcode")
            append("?client_id=$APP_KEY")
            append("&response_type=code")
            append("&scope=basic,netdisk")
            append("&redirect_uri=oob")
            append("&display=tv")
        }
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            if (root.has("error")) {
                throw BaiduApiException("获取二维码失败：${root.optString("error_description", text)}")
            }
            BaiduQrSession(
                qrcodeKey = root.getString("qrcode_key"),
                qrcodeUrl = root.getString("qrcode_url"),
                expiresIn = root.optInt("expires_in", 300)
            )
        }
    }

    /**
     * 第二步：轮询二维码扫码状态。
     * 返回 [BaiduQrStatus]，授权后包含 authorization_code。
     */
    fun pollQrStatus(qrcodeKey: String): BaiduQrStatus {
        val url = buildString {
            append("https://openapi.baidu.com/rest/2.0/passport/users/qrlogin/query")
            append("?qrcode_key=$qrcodeKey")
        }
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            when (root.optString("status")) {
                "waiting" -> BaiduQrStatus.Waiting
                "scanned" -> BaiduQrStatus.Scanned
                "login_success" -> BaiduQrStatus.Authorized(root.getString("code"))
                "expired" -> BaiduQrStatus.Expired
                "cancel" -> BaiduQrStatus.Denied
                else -> BaiduQrStatus.Waiting
            }
        }
    }

    /**
     * 第三步：用 authorization_code 换 access_token。
     */
    fun exchangeToken(code: String): BaiduTokenResult {
        val url = buildString {
            append("https://openapi.baidu.com/oauth/2.0/token")
            append("?grant_type=authorization_code")
            append("&code=$code")
            append("&client_id=$APP_KEY")
            append("&client_secret=$APP_SECRET")
            append("&redirect_uri=oob")
        }
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            if (!root.has("access_token")) {
                throw BaiduApiException("换取 Token 失败：${root.optString("error_description", text)}")
            }
            BaiduTokenResult(
                accessToken = root.getString("access_token"),
                refreshToken = root.optString("refresh_token", ""),
                expiresIn = root.optLong("expires_in", 2592000L)
            )
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

data class BaiduQrSession(
    val qrcodeKey: String,
    val qrcodeUrl: String,
    val expiresIn: Int
)

sealed class BaiduQrStatus {
    data object Waiting : BaiduQrStatus()
    data object Scanned : BaiduQrStatus()
    data class Authorized(val code: String) : BaiduQrStatus()
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
