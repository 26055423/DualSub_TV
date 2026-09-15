package com.dualsub.tv.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTimeTest {

    @Test
    fun `parses hh mm ss with comma`() {
        assertEquals(3_661_500L, SubtitleTime.parse("01:01:01,500"))
    }

    @Test
    fun `parses hh mm ss with dot`() {
        assertEquals(3_661_500L, SubtitleTime.parse("01:01:01.500"))
    }

    @Test
    fun `parses mm ss without hours`() {
        assertEquals(90_500L, SubtitleTime.parse("01:30.500"))
    }

    @Test
    fun `pads short fractional part`() {
        // 一位小数按毫秒补齐
        assertEquals(500L, SubtitleTime.parse("00:00:00,5"))
        // 两位小数同样按毫秒补齐，因此 ASS 的厘秒写法天然正确
        assertEquals(500L, SubtitleTime.parse("0:00:00.50"))
        assertEquals(50L, SubtitleTime.parse("0:00:00.05"))
    }

    @Test
    fun `rejects malformed input`() {
        assertNull(SubtitleTime.parse(""))
        assertNull(SubtitleTime.parse("abc"))
        assertNull(SubtitleTime.parse("00:00"))
        assertNull(SubtitleTime.parse("--:--:--"))
    }

    @Test
    fun `format detection by file name`() {
        assertEquals(SubtitleFormat.SRT, SubtitleFormat.fromFileName("movie.zh.srt"))
        assertEquals(SubtitleFormat.VTT, SubtitleFormat.fromFileName("MOVIE.VTT"))
        assertEquals(SubtitleFormat.ASS, SubtitleFormat.fromFileName("ep01.ass"))
        assertEquals(SubtitleFormat.ASS, SubtitleFormat.fromFileName("ep01.ssa"))
        assertEquals(SubtitleFormat.UNKNOWN, SubtitleFormat.fromFileName("video.mkv"))
        assertEquals(SubtitleFormat.UNKNOWN, SubtitleFormat.fromFileName(null))
    }

    @Test
    fun `format detection by mime type`() {
        assertEquals(SubtitleFormat.SRT, SubtitleFormat.fromMimeType(SubtitleFormat.MIME_SUBRIP))
        assertEquals(SubtitleFormat.VTT, SubtitleFormat.fromMimeType("text/vtt; charset=utf-8"))
        assertEquals(SubtitleFormat.ASS, SubtitleFormat.fromMimeType("text/x-ssa"))
        assertEquals(SubtitleFormat.UNKNOWN, SubtitleFormat.fromMimeType("video/avc"))
    }

    @Test
    fun `cue visibility is inclusive`() {
        val cue = SubtitleCue(1_000L, 2_000L, "x")
        assertTrue(cue.isVisibleAt(1_000L))
        assertTrue(cue.isVisibleAt(2_000L))
        assertFalse(cue.isVisibleAt(999L))
        assertFalse(cue.isVisibleAt(2_001L))
    }
}
