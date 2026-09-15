package com.dualsub.tv.network.smb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbPathsTest {

    @Test
    fun `builds plain uri`() {
        assertEquals(
            "smb://192.168.1.5/media/Movies/Interstellar.mkv",
            SmbPaths.build("192.168.1.5", "media", "Movies/Interstellar.mkv")
        )
    }

    @Test
    fun `builds share-root uri without trailing slash`() {
        assertEquals("smb://nas/media", SmbPaths.build("nas", "media", ""))
        assertEquals("smb://nas/media", SmbPaths.build("nas", "media", "/"))
    }

    @Test
    fun `encodes spaces and chinese so the uri stays parseable`() {
        val uri = SmbPaths.build("nas", "共享", "电影/流浪地球 2.mkv")

        assertTrue(uri.startsWith("smb://nas/"))
        assertFalse("空格必须被编码，否则 URI 解析会出错", uri.contains(' '))
        // `#` 和 `?` 若不编码会被当成 fragment/query，导致文件名被截断
        assertFalse(SmbPaths.build("nas", "m", "a#b?c.mkv").contains('#'))

        val parsed = SmbPaths.parse(uri)!!
        assertEquals("共享", parsed.share)
        assertEquals("电影/流浪地球 2.mkv", parsed.path)
    }

    @Test
    fun `round trips through parse and build`() {
        val path = "Season 01/Ep 01 - 标题.mkv"
        val uri = SmbPaths.build("nas", "video", path)

        val parsed = SmbPaths.parse(uri)!!
        assertEquals("nas", parsed.host)
        assertEquals("video", parsed.share)
        assertEquals(path, parsed.path)
        assertEquals(uri, parsed.uri)
    }

    @Test
    fun `parses host and share without path`() {
        val parsed = SmbPaths.parse("smb://nas/media")!!
        assertEquals("nas", parsed.host)
        assertEquals("media", parsed.share)
        assertEquals("", parsed.path)
    }

    @Test
    fun `parses host only`() {
        val parsed = SmbPaths.parse("smb://nas")!!
        assertEquals("nas", parsed.host)
        assertEquals("", parsed.share)
    }

    @Test
    fun `rejects foreign schemes and malformed input`() {
        assertNull(SmbPaths.parse("http://nas/media"))
        assertNull(SmbPaths.parse("content://media/external/video/1"))
        assertNull(SmbPaths.parse("smb://"))
        assertNull(SmbPaths.parse(""))
        assertNull(SmbPaths.parse(null))
    }

    @Test
    fun `is case insensitive on the scheme`() {
        assertEquals("nas", SmbPaths.parse("SMB://nas/media")?.host)
    }

    @Test
    fun `normalizes backslashes and trims slashes`() {
        assertEquals("a/b/c", SmbPaths.normalizePath("\\a\\b/c\\"))
        assertEquals("a", SmbPaths.normalizePath("/a/"))
        assertEquals("", SmbPaths.normalizePath(null))
        assertEquals("", SmbPaths.normalizePath("///"))
    }

    @Test
    fun `joins and walks up`() {
        assertEquals("a/b", SmbPaths.join("a", "b"))
        assertEquals("a/b", SmbPaths.join("a/", "/b"))
        assertEquals("b", SmbPaths.join("", "b"))
        assertEquals("a", SmbPaths.join("a", ""))

        assertEquals("a/b", SmbPaths.parent("a/b/c"))
        assertEquals("a", SmbPaths.parent("a/b"))
        assertEquals("", SmbPaths.parent("a"))
        assertEquals("", SmbPaths.parent(""))
    }

    @Test
    fun `segment encoding keeps unreserved characters`() {
        assertEquals("abc-_.~123", SmbPaths.encodeSegment("abc-_.~123"))
        assertEquals("%E4%B8%AD", SmbPaths.encodeSegment("中"))
        assertEquals("a%20b", SmbPaths.encodeSegment("a b"))
    }

    @Test
    fun `segment decoding tolerates malformed escapes`() {
        assertEquals("中", SmbPaths.decodeSegment("%E4%B8%AD"))
        // 不完整的转义按字面处理，而不是抛异常
        assertEquals("100%", SmbPaths.decodeSegment("100%"))
        assertEquals("a%zz", SmbPaths.decodeSegment("a%zz"))
        assertEquals("plain", SmbPaths.decodeSegment("plain"))
    }
}
