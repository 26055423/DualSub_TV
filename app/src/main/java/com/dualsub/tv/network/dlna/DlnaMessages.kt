package com.dualsub.tv.network.dlna

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/**
 * DLNA/UPnP 的报文构造与解析。
 *
 * 这一层刻意做成纯字符串处理，因为真正的网络交互（UDP 多播、SOAP over HTTP）
 * 没法在 JVM 单测里跑，但报文格式是最容易出错的地方，值得单独覆盖。
 */
object DlnaMessages {

    const val SSDP_ADDRESS = "239.255.255.250"
    const val SSDP_PORT = 1900

    /** 只找媒体服务器；[MediaRenderer] 是「渲染器」，不是片源。 */
    const val SEARCH_TARGET_MEDIA_SERVER = "urn:schemas-upnp-org:device:MediaServer:1"

    const val CONTENT_DIRECTORY_TYPE = "urn:schemas-upnp-org:service:ContentDirectory:1"

    /** 构造 SSDP M-SEARCH 请求。 */
    fun buildSearchRequest(searchTarget: String = SEARCH_TARGET_MEDIA_SERVER): ByteArray = buildString {
        append("M-SEARCH * HTTP/1.1\r\n")
        append("HOST: ").append(SSDP_ADDRESS).append(':').append(SSDP_PORT).append("\r\n")
        append("MAN: \"ssdp:discover\"\r\n")
        append("MX: 3\r\n")
        append("ST: ").append(searchTarget).append("\r\n")
        append("\r\n")
    }.toByteArray(Charsets.UTF_8)

    /** SSDP 响应里我们关心的字段。 */
    data class SsdpResponse(
        val location: String,
        val server: String?,
        val searchTarget: String?,
        val uniqueServiceName: String?
    )

    /**
     * 解析 SSDP 响应。没有 LOCATION 的响应（例如设备下线通知）返回 null。
     */
    fun parseSsdpResponse(text: String?): SsdpResponse? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        // 必须是响应行，忽略 NOTIFY 之类的主动通告
        if (!raw.startsWith("HTTP/1.1 200", ignoreCase = true) && !raw.startsWith("HTTP/1.0 200", ignoreCase = true)) {
            return null
        }

        val headers = raw.split("\r\n", "\n")
            .drop(1)
            .mapNotNull { line ->
                val colon = line.indexOf(':')
                if (colon <= 0) null else line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
            }
            .toMap()

        val location = headers["location"]?.takeIf { it.isNotBlank() } ?: return null
        return SsdpResponse(
            location = location,
            server = headers["server"],
            searchTarget = headers["st"],
            uniqueServiceName = headers["usn"]
        )
    }

    /** 从设备描述 XML 里取出 ContentDirectory 服务的 controlURL（相对或绝对）。 */
    fun parseContentDirectoryControlUrl(deviceDescriptionXml: String?): String? {
        val root = parseXml(deviceDescriptionXml) ?: return null
        val services = root.getElementsByTagName("service")
        for (index in 0 until services.length) {
            val service = services.item(index) as? Element ?: continue
            val type = service.childText("serviceType")
            if (type?.trim()?.equals(CONTENT_DIRECTORY_TYPE, ignoreCase = true) == true) {
                return service.childText("controlURL")?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    /** 从设备描述 XML 里取出友好名称。 */
    fun parseFriendlyName(deviceDescriptionXml: String?): String? {
        val root = parseXml(deviceDescriptionXml) ?: return null
        val devices = root.getElementsByTagName("device")
        for (index in 0 until devices.length) {
            val device = devices.item(index) as? Element ?: continue
            val name = device.childText("friendlyName")?.trim()
            if (!name.isNullOrEmpty()) return name
        }
        return null
    }

    /** 构造 ContentDirectory Browse 的 SOAP 请求体。 */
    fun buildBrowseEnvelope(
        objectId: String,
        startIndex: Int = 0,
        requestedCount: Int = 500
    ): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
        append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
        append("<s:Body>")
        append("<u:Browse xmlns:u=\"").append(CONTENT_DIRECTORY_TYPE).append("\">")
        append("<ObjectID>").append(escapeXml(objectId)).append("</ObjectID>")
        append("<BrowseFlag>BrowseDirectChildren</BrowseFlag>")
        append("<Filter>*</Filter>")
        append("<StartingIndex>").append(startIndex).append("</StartingIndex>")
        append("<RequestedCount>").append(requestedCount).append("</RequestedCount>")
        append("<SortCriteria></SortCriteria>")
        append("</u:Browse>")
        append("</s:Body>")
        append("</s:Envelope>")
    }

    /**
     * 解析 Browse 响应：返回「总条目数」与 DIDL-Lite 里的条目列表。
     *
     * `<Result>` 的内容是**被转义过的一整段 XML**，需要再解析一次。
     */
    fun parseBrowseResponse(responseXml: String?): BrowseResult {
        val root = parseXml(responseXml) ?: return BrowseResult(0, emptyList())
        val totalMatches = root.getElementsByTagName("TotalMatches")
            .let { if (it.length > 0) it.item(0)?.textContent?.trim()?.toIntOrNull() ?: 0 else 0 }

        val resultNode = root.getElementsByTagName("Result")
            .let { if (it.length > 0) it.item(0)?.textContent else null }
            ?: return BrowseResult(totalMatches, emptyList())

        return BrowseResult(totalMatches, parseDidl(resultNode))
    }

    data class BrowseResult(val totalMatches: Int, val entries: List<DidlEntry>)

    data class DidlEntry(
        val objectId: String,
        val title: String,
        val isContainer: Boolean,
        val upnpClass: String?,
        /** 第一条 `res` 的地址，容器为空。 */
        val resourceUrl: String?,
        val sizeBytes: Long,
        val mimeType: String?
    ) {
        /** 按 UPnP class 或 MIME 判断是不是视频。 */
        val isVideo: Boolean
            get() = !isContainer && (
                upnpClass?.contains("videoItem", ignoreCase = true) == true ||
                    mimeType?.startsWith("video/", ignoreCase = true) == true
                )
    }

    /** 解析 DIDL-Lite 文档（`<container>` 与 `<item>`）。 */
    fun parseDidl(didlXml: String?): List<DidlEntry> {
        val root = parseXml(didlXml) ?: return emptyList()
        val out = ArrayList<DidlEntry>()

        val containers = root.getElementsByTagName("container")
        for (index in 0 until containers.length) {
            val element = containers.item(index) as? Element ?: continue
            out += DidlEntry(
                objectId = element.getAttribute("id").orEmpty(),
                title = element.childText("title")?.trim().orEmpty().ifEmpty { "未命名" },
                isContainer = true,
                upnpClass = element.childText("class"),
                resourceUrl = null,
                sizeBytes = 0L,
                mimeType = null
            )
        }

        val items = root.getElementsByTagName("item")
        for (index in 0 until items.length) {
            val element = items.item(index) as? Element ?: continue
            val res = firstRes(element)
            out += DidlEntry(
                objectId = element.getAttribute("id").orEmpty(),
                title = element.childText("title")?.trim().orEmpty().ifEmpty { "未命名" },
                isContainer = false,
                upnpClass = element.childText("class"),
                resourceUrl = res?.first,
                sizeBytes = res?.second?.toLongOrNull() ?: 0L,
                mimeType = res?.third
            )
        }
        return out
    }

    /** 取第一条 `<res>`：地址、size 属性、protocolInfo 里的 MIME。 */
    private fun firstRes(item: Element): Triple<String, String?, String?>? {
        val nodes = item.getElementsByTagName("res")
        if (nodes.length == 0) return null
        val res = nodes.item(0) as? Element ?: return null
        val url = res.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val protocolInfo = res.getAttribute("protocolInfo").orEmpty()
        // 形如 http-get:*:video/x-matroska:*
        val mime = protocolInfo.split(':').getOrNull(2)?.takeIf { it.isNotBlank() && it != "*" }
        return Triple(url, res.getAttribute("size").takeIf { it.isNotBlank() }, mime)
    }

    private fun Element.childText(localName: String): String? {
        // DIDL-Lite 用 dc:title / upnp:class 这样的前缀，所以按 localName 找
        val byLocal = getElementsByTagName(localName)
        if (byLocal.length > 0) return byLocal.item(0)?.textContent
        val children = childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node.nodeType == Node.ELEMENT_NODE && node.localName == localName) {
                return node.textContent
            }
        }
        return null
    }

    private fun parseXml(xml: String?): Element? {
        if (xml.isNullOrBlank()) return null
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // 显式关掉外部实体，避免被恶意描述文件触发 XXE
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            val builder = factory.newDocumentBuilder()
            builder.parse(InputSource(StringReader(xml))).documentElement
        }.getOrNull()
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
