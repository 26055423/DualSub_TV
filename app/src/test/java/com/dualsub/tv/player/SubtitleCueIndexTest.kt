package com.dualsub.tv.player

import com.dualsub.tv.subtitle.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleCueIndexTest {

    private val cues = listOf(
        SubtitleCue(1_000L, 2_000L, "A"),
        SubtitleCue(3_000L, 4_000L, "B"),
        SubtitleCue(5_000L, 9_000L, "C")
    )

    @Test
    fun `finds cue inside range`() {
        assertEquals("A", SubtitleCueIndex.find(cues, 1_500L)?.text)
        assertEquals("B", SubtitleCueIndex.find(cues, 3_000L)?.text)
        assertEquals("C", SubtitleCueIndex.find(cues, 8_999L)?.text)
    }

    @Test
    fun `returns null in gaps`() {
        assertNull(SubtitleCueIndex.find(cues, 0L))
        assertNull(SubtitleCueIndex.find(cues, 2_500L))
        assertNull(SubtitleCueIndex.find(cues, 9_001L))
    }

    @Test
    fun `handles empty list`() {
        assertNull(SubtitleCueIndex.find(emptyList(), 1_000L))
    }

    @Test
    fun `handles single cue list`() {
        val single = listOf(SubtitleCue(500L, 1_500L, "只有一条"))

        assertEquals("只有一条", SubtitleCueIndex.find(single, 1_000L)?.text)
        assertNull(SubtitleCueIndex.find(single, 2_000L))
    }

    @Test
    fun `picks the later cue when ranges overlap`() {
        val overlapping = listOf(
            SubtitleCue(1_000L, 5_000L, "长"),
            SubtitleCue(2_000L, 3_000L, "短")
        )

        // 二分定位到最后一个 startMs <= position 的候选，重叠时优先返回它
        assertEquals("短", SubtitleCueIndex.find(overlapping, 2_500L)?.text)
        // 短字幕结束后，回落到仍在生效的长字幕
        assertEquals("长", SubtitleCueIndex.find(overlapping, 4_000L)?.text)
    }

    @Test
    fun `works with large lists`() {
        val many = (0 until 10_000).map { index ->
            SubtitleCue(index * 2_000L, index * 2_000L + 1_000L, "cue-$index")
        }

        assertEquals("cue-0", SubtitleCueIndex.find(many, 500L)?.text)
        assertEquals("cue-4999", SubtitleCueIndex.find(many, 9_999_000L)?.text)
        // 落在两条字幕之间的间隙
        assertNull(SubtitleCueIndex.find(many, 1_500L))
    }
}
