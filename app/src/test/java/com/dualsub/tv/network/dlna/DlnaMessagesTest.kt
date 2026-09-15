package com.dualsub.tv.network.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaMessagesTest {

    @Test
    fun `search request targets media servers`() {
        val request = String(DlnaMessages.buildSearchRequest(), Charsets.UTF_8)

        assertTrue(request.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertTrue(request.contains("HOST: 239.255.255.250:1900\r\n"))
        assertTrue(request.contains("MAN: \"ssdp:discover\"\r\n"))
        assertTrue(request.contains("ST: ${DlnaMessages.SEARCH_TARGET_MEDIA_SERVER}\r\n"))
        // 头部必须以空行收尾
        assertTrue(request.endsWith("\r\n\r\n"))
    }

    @Test
    fun `parses ssdp response`() {
        val raw = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("CACHE-CONTROL: max-age=1800\r\n")
            append("LOCATION: http://192.168.1.5:8200/rootDesc.xml\r\n")
            append("SERVER: Linux/4 UPnP/1.0 NAS/1.0\r\n")
            append("ST: urn:schemas-upnp-org:device:MediaServer:1\r\n")
            append("USN: uuid:abc::urn:schemas-upnp-org:device:MediaServer:1\r\n")
            append("\r\n")
        }

        val parsed = DlnaMessages.parseSsdpResponse(raw)!!

        assertEquals("http://192.168.1.5:8200/rootDesc.xml", parsed.location)
        assertEquals("Linux/4 UPnP/1.0 NAS/1.0", parsed.server)
        assertEquals("urn:schemas-upnp-org:device:MediaServer:1", parsed.searchTarget)
    }

    @Test
    fun `ignores notifications and responses without location`() {
        val notify = "NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nNT: upnp:rootdevice\r\n\r\n"
        assertNull(DlnaMessages.parseSsdpResponse(notify))

        val noLocation = "HTTP/1.1 200 OK\r\nST: upnp:rootdevice\r\n\r\n"
        assertNull(DlnaMessages.parseSsdpResponse(noLocation))

        assertNull(DlnaMessages.parseSsdpResponse(""))
        assertNull(DlnaMessages.parseSsdpResponse(null))
    }

    @Test
    fun `extracts content directory control url and friendly name`() {
        val xml = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <friendlyName>客厅 NAS</friendlyName>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
                    <controlURL>/ctl/ConnMgr</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>
                    <controlURL>/ctl/ContentDir</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()

        assertEquals("/ctl/ContentDir", DlnaMessages.parseContentDirectoryControlUrl(xml))
        assertEquals("客厅 NAS", DlnaMessages.parseFriendlyName(xml))
    }

    @Test
    fun `returns null for malformed device description`() {
        assertNull(DlnaMessages.parseContentDirectoryControlUrl("not xml at all"))
        assertNull(DlnaMessages.parseContentDirectoryControlUrl("<root/>"))
        assertNull(DlnaMessages.parseContentDirectoryControlUrl(null))
    }

    @Test
    fun `browse envelope carries object id and paging`() {
        val envelope = DlnaMessages.buildBrowseEnvelope("64", startIndex = 10, requestedCount = 20)

        assertTrue(envelope.contains("<ObjectID>64</ObjectID>"))
        assertTrue(envelope.contains("<BrowseFlag>BrowseDirectChildren</BrowseFlag>"))
        assertTrue(envelope.contains("<StartingIndex>10</StartingIndex>"))
        assertTrue(envelope.contains("<RequestedCount>20</RequestedCount>"))
        assertTrue(envelope.contains(DlnaMessages.CONTENT_DIRECTORY_TYPE))
    }

    @Test
    fun `parses browse response with escaped didl`() {
        // <Result> 里是被转义的一整段 DIDL-Lite，需要再解析一次
        val response = """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
                  <Result>&lt;DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"&gt;
                    &lt;container id="64" parentID="0" childCount="3"&gt;&lt;dc:title&gt;电影&lt;/dc:title&gt;&lt;upnp:class&gt;object.container.storageFolder&lt;/upnp:class&gt;&lt;/container&gt;
                    &lt;item id="128" parentID="0"&gt;&lt;dc:title&gt;流浪地球 2.mkv&lt;/dc:title&gt;&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;&lt;res protocolInfo="http-get:*:video/x-matroska:*" size="12345"&gt;http://192.168.1.5:8200/MediaItems/128.mkv&lt;/res&gt;&lt;/item&gt;
                  &lt;/DIDL-Lite&gt;</Result>
                  <NumberReturned>2</NumberReturned>
                  <TotalMatches>2</TotalMatches>
                </u:BrowseResponse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val result = DlnaMessages.parseBrowseResponse(response)

        assertEquals(2, result.totalMatches)
        assertEquals(2, result.entries.size)

        val container = result.entries[0]
        assertTrue(container.isContainer)
        assertEquals("电影", container.title)
        assertEquals("64", container.objectId)
        assertFalse(container.isVideo)

        val item = result.entries[1]
        assertFalse(item.isContainer)
        assertEquals("流浪地球 2.mkv", item.title)
        assertEquals("http://192.168.1.5:8200/MediaItems/128.mkv", item.resourceUrl)
        assertEquals("video/x-matroska", item.mimeType)
        assertEquals(12_345L, item.sizeBytes)
        assertTrue(item.isVideo)
    }

    @Test
    fun `audio items are not treated as video`() {
        val didl = """
            <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
              <item id="1" parentID="0">
                <dc:title>song.mp3</dc:title>
                <upnp:class>object.item.audioItem.musicTrack</upnp:class>
                <res protocolInfo="http-get:*:audio/mpeg:*">http://nas/1.mp3</res>
              </item>
            </DIDL-Lite>
        """.trimIndent()

        val entries = DlnaMessages.parseDidl(didl)

        assertEquals(1, entries.size)
        assertFalse(entries[0].isVideo)
    }

    @Test
    fun `empty or broken responses do not throw`() {
        assertEquals(0, DlnaMessages.parseBrowseResponse(null).entries.size)
        assertEquals(0, DlnaMessages.parseBrowseResponse("<broken").entries.size)
        assertTrue(DlnaMessages.parseDidl("").isEmpty())
    }
}
