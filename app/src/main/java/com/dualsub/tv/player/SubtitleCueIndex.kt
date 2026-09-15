package com.dualsub.tv.player

import com.dualsub.tv.subtitle.SubtitleCue

/**
 * 字幕定位。
 *
 * cues 已按开始时间升序排列，用二分找到最后一个 startMs <= position 的候选，
 * 再在有限步数内向前回看，兼容少量重叠或轻微乱序的字幕。
 * 相比每帧线性扫描，播放长片时开销可以忽略。
 */
internal object SubtitleCueIndex {

    /** 回看上限，防止极端重叠字幕导致退化成线性扫描。 */
    private const val MAX_LOOKBACK = 32

    fun find(cues: List<SubtitleCue>, positionMs: Long): SubtitleCue? {
        var low = 0
        var high = cues.size - 1
        var candidate = -1

        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (cues[mid].startMs <= positionMs) {
                candidate = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (candidate < 0) return null

        var index = candidate
        var steps = 0
        while (index >= 0 && steps < MAX_LOOKBACK && cues[index].startMs <= positionMs) {
            if (positionMs <= cues[index].endMs) return cues[index]
            index--
            steps++
        }
        return null
    }
}
