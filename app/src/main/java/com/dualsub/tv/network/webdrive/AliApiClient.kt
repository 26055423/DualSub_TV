package com.dualsub.tv.network.webdrive

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 阿里云盘非官方 API 封装（参考 AList aliyundrive 驱动）。
 *
 * 认证走扫码登录（openapi.alipan.com），文件操作走旧版 api.aliyundrive.com。
 * 视频转码流无限速，非会员也可用，优先使用；失败降级到直链。
 */
class AliApiClient(private var accessToken: String = "") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun updateToken(token: String) { accessToken = token }

    // ── 扫码登录 ────────────────────────────────────────────

    fun getQrCode(): AliQrSession {
        val body = JSONObject().apply {
            put("client_id", CLIENT_ID)
            put("scopes", org.json.JSONArray().put("user:base").put("file:all:read"))
            put("width", 430)
            put("height", 430)
        }.toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url("https://openapi.alipan.com/oauth/authorize/qrcode")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            if (!root.has("qrCodeUrl")) throw AliApiException("获取二维码失败：$text")
            AliQrSession(
                qrCodeUrl = root.getString("qrCodeUrl"),
                sid = root.getString("sid")
            )
        }
    }

    fun pollQrStatus(sid: String): AliQrStatus {
        val request = Request.Builder()
            .url("https://openapi.alipan.com/oauth/qrcode/$sid/status")
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            when (root.optString("status")) {
                "WaitLogin" -> AliQrStatus.Waiting
                "ScanSuccess" -> AliQrStatus.Scanned
                "LoginSuccess" -> AliQrStatus.Authorized(root.getString("authCode"))
                "QRCodeExpired" -> AliQrStatus.Expired
                else -> AliQrStatus.Waiting
            }
        }
    }

    fun exchangeToken(authCode: String): AliTokenResult {
        val body = JSONObject().apply {
            put("code", authCode)
            put("grant_type", "authorization_code")
        }.toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url("https://openapi.alipan.com/oauth/access_token")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            if (!root.has("access_token")) throw AliApiException("换取 Token 失败：$text")
            AliTokenResult(
                accessToken = root.getString("access_token"),
                refreshToken = root.optString("refresh_token", ""),
                expiresIn = root.optLong("expires_in", 7200L)
            )
        }
    }

    fun refreshToken(refreshToken: String): AliTokenResult {
        val body = JSONObject().apply {
            put("refresh_token", refreshToken)
            put("grant_type", "refresh_token")
        }.toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url("https://openapi.alipan.com/oauth/access_token")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            if (!root.has("access_token")) throw AliApiException("刷新 Token 失败：$text")
            AliTokenResult(
                accessToken = root.getString("access_token"),
                refreshToken = root.optString("refresh_token", refreshToken),
                expiresIn = root.optLong("expires_in", 7200L)
            )
        }
    }

    // ── 用户信息 ────────────────────────────────────────────

    fun getUserInfo(): AliUserInfo {
        val request = Request.Builder()
            .url("https://api.aliyundrive.com/adrive/v2/user/get")
            .post("{}".toRequestBody(JSON))
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val root = JSONObject(text)
            if (response.code == 401) throw AliTokenExpiredException()
            AliUserInfo(
                driveId = root.optString("default_drive_id", ""),
                nickname = root.optString("nick_name", "未知用户")
            )
        }
    }

    // ── 文件操作 ────────────────────────────────────────────

    fun listFiles(driveId: String, parentFileId: String = "root", marker: String = ""): AliFileList {
        val body = JSONObject().apply {
            put("drive_id", driveId)
            put("parent_file_id", parentFileId)
            put("limit", 100)
            put("order_by", "name")
            put("order_direction", "ASC")
            if (marker.isNotBlank()) put("marker", marker)
        }.toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url("https://api.aliyundrive.com/adrive/v3/file/list")
            .post(body)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            if (response.code == 401) throw AliTokenExpiredException()
            val root = JSONObject(text)
            val items = root.optJSONArray("items") ?: return AliFileList(emptyList(), "")
            val entries = (0 until items.length()).map { i ->
                val item = items.getJSONObject(i)
                AliEntry(
                    fileId = item.getString("file_id"),
                    driveId = item.getString("drive_id"),
                    name = item.getString("name"),
                    type = item.getString("type"),
                    size = item.optLong("size", 0L),
                    mimeType = item.optString("mime_type", "")
                )
            }
            AliFileList(entries, root.optString("next_marker", ""))
        }
    }

    /** 获取下载直链（有效期约 15 分钟，播放需 Referer 头）。 */
    fun getDownloadUrl(driveId: String, fileId: String): String {
        val body = JSONObject().apply {
            put("drive_id", driveId)
            put("file_id", fileId)
        }.toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url("https://api.aliyundrive.com/v2/file/get_download_url")
            .post(body)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            if (response.code == 401) throw AliTokenExpiredException()
            val root = JSONObject(text)
            root.optString("url").ifBlank { throw AliApiException("获取下载链接失败：$text") }
        }
    }

    /**
     * 获取视频转码流 URL（无限速，非会员可用）。
     * 返回最高可用分辨率的 m3u8 URL，无可用转码时返回 null。
     */
    fun getTranscodingUrl(driveId: String, fileId: String): String? {
        val body = JSONObject().apply {
            put("drive_id", driveId)
            put("file_id", fileId)
            put("category", "live_transcoding")
            put("template_id", "")
            put("get_subtitle_info", true)
        }.toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url("https://api.aliyundrive.com/v2/file/get_video_preview_play_info")
            .post(body)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            if (response.code == 401) throw AliTokenExpiredException()
            val root = JSONObject(text)
            val tasks = root
                .optJSONObject("video_preview_play_info")
                ?.optJSONArray("live_transcoding_task_list")
                ?: return null
            // 按分辨率排序，取最高的 finished 任务
            val resolutionOrder = listOf("UD", "QHD", "FHD", "HD", "SD", "LD")
            resolutionOrder.firstNotNullOfOrNull { res ->
                (0 until tasks.length())
                    .map { tasks.getJSONObject(it) }
                    .firstOrNull { it.optString("template_id") == res && it.optString("status") == "finished" }
                    ?.optString("url")
                    ?.takeIf { it.isNotBlank() }
            }
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        // 阿里云盘开放平台公共 client_id（AList 使用的同一个）
        const val CLIENT_ID = "76917ccccd4441c39457a1f75d82acd3"
        const val ALI_REFERER = "https://www.aliyundrive.com/"
    }
}

data class AliQrSession(val qrCodeUrl: String, val sid: String)

sealed class AliQrStatus {
    data object Waiting : AliQrStatus()
    data object Scanned : AliQrStatus()
    data class Authorized(val authCode: String) : AliQrStatus()
    data object Expired : AliQrStatus()
}

data class AliTokenResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)

data class AliUserInfo(val driveId: String, val nickname: String)

data class AliEntry(
    val fileId: String,
    val driveId: String,
    val name: String,
    val type: String,
    val size: Long,
    val mimeType: String
) {
    val isDirectory: Boolean get() = type == "folder"
}

data class AliFileList(val items: List<AliEntry>, val nextMarker: String)

class AliApiException(message: String) : Exception(message)
class AliTokenExpiredException : Exception("Access Token 已过期")
