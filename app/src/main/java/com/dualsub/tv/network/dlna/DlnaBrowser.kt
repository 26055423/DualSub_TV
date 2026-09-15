package com.dualsub.tv.network.dlna

import android.content.Context
import android.net.wifi.WifiManager
import com.dualsub.tv.network.RemoteBrowser
import com.dualsub.tv.network.RemoteEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.URI
import java.net.URL

/** 一台被发现的 DLNA 媒体服务器。 */
data class DlnaDevice(
    val friendlyName: String,
    val descriptionUrl: String,
    val controlUrl: String,
    val host: String
)

/**
 * 最小可用的 DLNA/UPnP 客户端。
 *
 * 只做三件必要的事：SSDP 多播发现媒体服务器、解析设备描述拿到 ContentDirectory 的
 * controlURL、用 SOAP `Browse` 列目录。**不引入 Cling/jUPnP** ——
 * Cling 已停止维护，jUPnP 又会带进 OSGi/javax 一堆依赖，而这里需要的功能很少。
 *
 * 拉流本身用不到它：DLNA 的资源地址就是普通 `http://`，ExoPlayer 原生支持。
 */
class DlnaDiscovery(private val context: Context) {

    /** 在局域网内广播 M-SEARCH 并收集响应；[timeoutMs] 内没回应的设备不会出现。 */
    suspend fun discover(timeoutMs: Long = 4000L): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val lock = acquireMulticastLock()
        try {
            MulticastSocket().use { socket ->
                socket.soTimeout = 800
                socket.reuseAddress = true

                val group = InetAddress.getByName(DlnaMessages.SSDP_ADDRESS)
                val request = DlnaMessages.buildSearchRequest()
                socket.send(DatagramPacket(request, request.size, group, DlnaMessages.SSDP_PORT))

                val found = LinkedHashMap<String, DlnaDevice>()
                val buffer = ByteArray(8 * 1024)
                val deadline = System.currentTimeMillis() + timeoutMs

                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    val received = runCatching { socket.receive(packet) }
                        .fold(onSuccess = { true }, onFailure = { false })
                    if (!received) continue

                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val ssdp = DlnaMessages.parseSsdpResponse(text) ?: continue
                    if (found.containsKey(ssdp.location)) continue

                    describeDevice(ssdp.location)?.let { found[ssdp.location] = it }
                }
                found.values.toList()
            }
        } finally {
            runCatching { lock?.release() }
        }
    }

    /** 读取设备描述，拿到 ContentDirectory 的 controlURL 与友好名。 */
    private fun describeDevice(descriptionUrl: String): DlnaDevice? {
        val xml = httpGet(descriptionUrl) ?: return null
        val controlUrl = DlnaMessages.parseContentDirectoryControlUrl(xml) ?: return null
        val host = runCatching { URI(descriptionUrl).host }.getOrNull() ?: return null
        return DlnaDevice(
            friendlyName = DlnaMessages.parseFriendlyName(xml) ?: host,
            descriptionUrl = descriptionUrl,
            controlUrl = controlUrl,
            host = host
        )
    }

    /**
     * SSDP 走组播，Android 上必须持有 MulticastLock 才能收到回包，
     * 否则表现就是「一个设备都发现不了」。
     */
    private fun acquireMulticastLock(): WifiManager.MulticastLock? = runCatching {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifi?.createMulticastLock("dualsub-dlna")?.apply {
            setReferenceCounted(true)
            acquire()
        }
    }.getOrNull()

    private fun httpGet(url: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 8000
        connection.requestMethod = "GET"
        try {
            connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

/** 用 ContentDirectory 的 SOAP `Browse` 浏览内容目录。 */
class DlnaBrowser(private val device: DlnaDevice) : RemoteBrowser {

    override val rootPath: String = "0"

    override suspend fun list(path: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val url = resolveUrl(device.descriptionUrl, device.controlUrl)
            ?: throw IOException("无法解析 DLNA 控制地址")

        val body = soapPost(url, DlnaMessages.buildBrowseEnvelope(path))
            ?: throw IOException("DLNA 服务器没有响应 Browse 请求")

        DlnaMessages.parseBrowseResponse(body).entries
            // 只保留容器与视频：音乐、图片在这个播放器里没有意义
            .filter { it.isContainer || it.isVideo }
            .map { entry ->
                RemoteEntry(
                    name = entry.title,
                    location = entry.objectId,
                    isDirectory = entry.isContainer,
                    sizeBytes = entry.sizeBytes,
                    playableUri = entry.resourceUrl
                )
            }
            .sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override fun close() = Unit

    private fun soapPost(url: String, envelope: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 15000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        connection.setRequestProperty("SOAPAction", "\"${DlnaMessages.CONTENT_DIRECTORY_TYPE}#Browse\"")
        connection.outputStream.use { it.write(envelope.toByteArray(Charsets.UTF_8)) }
        try {
            connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /** controlURL 常常是相对路径，需要相对设备描述地址解析。 */
    private fun resolveUrl(base: String, control: String): String? =
        runCatching { URI(base).resolve(control).toString() }.getOrNull()
}
