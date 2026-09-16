package com.dualsub.tv.network.webdrive

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 夸克网盘 HTTP API 封装。
 *
 * 所有接口均为非官方逆向工程，参考自 AList quark driver。
 * API 端点或参数随时可能改变。
 */
class QuarkApiClient(private val cookie: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * 列出指定目录的文件（pdir_fid = 目录 fid，"0" 表示根目录）。
     * 返回 [QuarkEntry] 列表，失败抛 [QuarkApiException]。
     */
    fun listFiles(pdirFid: String, page: Int = 1): List<QuarkEntry> {
        val body = JSONObject().apply {
            put("pdir_fid", pdirFid)
            put("_page", page)
            put("_size", 100)
            put("_fetch_total", "1")
            put("_sort", "file_type:asc,file_name:asc")
        }.toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url("https://drive.quark.cn/1/clouddrive/file/sort?pr=ucpro&fr=pc")
            .post(body)
            .headers(commonHeaders())
            .build()

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            if (root.optInt("code", -1) != 0) {
                throw QuarkApiException("列目录失败：${root.optString("message", text)}")
            }
            val list = root.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
            (0 until list.length()).map { i ->
                val item = list.getJSONObject(i)
                val isDir = item.optInt("file_type", 1) == 1
                QuarkEntry(
                    fid = item.getString("fid"),
                    name = item.getString("file_name"),
                    isDirectory = isDir,
                    size = item.optLong("size", 0L),
                    category = item.optString("format_type", "")
                )
            }
        }
    }

    /**
     * 获取文件的临时下载直链（有效期约 1 小时）。
     */
    fun downloadUrl(fid: String): String {
        val body = JSONObject().apply {
            put("fids", JSONArray().put(fid))
        }.toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url("https://drive.quark.cn/1/clouddrive/file/download?pr=ucpro&fr=pc")
            .post(body)
            .headers(commonHeaders())
            .build()

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            if (root.optInt("code", -1) != 0) {
                throw QuarkApiException("获取下载链接失败：${root.optString("message", text)}")
            }
            val data = root.optJSONArray("data") ?: throw QuarkApiException("返回数据格式错误")
            data.getJSONObject(0).getString("download_url")
        }
    }

    // ── 扫码登录 ────────────────────────────────────────────

    /**
     * 申请一个扫码登录会话，返回二维码内容 URL 和令牌 [QuarkQrSession]。
     */
    fun requestQrSession(): QuarkQrSession {
        val request = Request.Builder()
            .url(
                "https://uniapp-sts.quark.cn/ad/callback/qr_login/getQrPage?t=${System.currentTimeMillis()}"
            )
            .get()
            .addHeader("Referer", "https://pan.quark.cn/")
            .addHeader("User-Agent", PC_UA)
            .build()

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            if (root.optInt("code", -1) != 0) {
                throw QuarkApiException("获取二维码失败：${root.optString("message", text)}")
            }
            val data = root.getJSONObject("data")
            QuarkQrSession(
                token = data.getString("member_id"),
                qrUrl = data.getString("url")
            )
        }
    }

    /**
     * 轮询二维码扫码状态。
     * 返回 [QuarkQrStatus]，已授权时包含 Cookie。
     */
    fun pollQrStatus(token: String): QuarkQrStatus {
        val request = Request.Builder()
            .url(
                "https://uniapp-sts.quark.cn/ad/callback/qr_login/queryQrResult?member_id=$token&t=${System.currentTimeMillis()}"
            )
            .get()
            .addHeader("Referer", "https://pan.quark.cn/")
            .addHeader("User-Agent", PC_UA)
            .build()

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            val code = root.optInt("code", -1)
            val data = root.optJSONObject("data")
            when {
                code == 0 && data != null -> {
                    val loginStatus = data.optInt("status", 0)
                    when (loginStatus) {
                        2 -> {
                            // 已授权，从 Set-Cookie 或 data 里取凭据
                            val rawCookie = response.headers("Set-Cookie").joinToString("; ") { c ->
                                c.substringBefore(';')
                            }
                            val cookieFromBody = data.optString("cookie", "")
                            val cookie = rawCookie.ifBlank { cookieFromBody }
                            QuarkQrStatus.Authorized(cookie)
                        }
                        1 -> QuarkQrStatus.Scanned
                        else -> QuarkQrStatus.Waiting
                    }
                }
                code == 41006 -> QuarkQrStatus.Expired
                else -> QuarkQrStatus.Waiting
            }
        }
    }

    private fun commonHeaders() = okhttp3.Headers.Builder()
        .add("Cookie", cookie)
        .add("Referer", "https://pan.quark.cn/")
        .add("User-Agent", PC_UA)
        .add("Content-Type", "application/json")
        .build()

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        const val PC_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }
}

data class QuarkEntry(
    val fid: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val category: String
)

sealed class QuarkQrStatus {
    data object Waiting : QuarkQrStatus()
    data object Scanned : QuarkQrStatus()
    data class Authorized(val cookie: String) : QuarkQrStatus()
    data object Expired : QuarkQrStatus()
}

data class QuarkQrSession(val token: String, val qrUrl: String)

class QuarkApiException(message: String) : Exception(message)
