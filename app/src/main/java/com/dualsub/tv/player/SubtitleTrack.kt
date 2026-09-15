package com.dualsub.tv.player

import com.dualsub.tv.subtitle.SubtitleCue

/** 一路字幕的来源。 */
sealed interface SubtitleSource {

    /** 该路字幕关闭。 */
    data object None : SubtitleSource

    /** 外挂字幕文件（通过系统文件选择器获得，持有持久化读权限）。 */
    data class ExternalFile(val uri: String, val displayName: String) : SubtitleSource

    /** 视频文件的内嵌字幕轨。 */
    data class EmbeddedTrack(
        val trackIndex: Int,
        val mimeType: String?,
        val language: String?,
        val label: String
    ) : SubtitleSource
}

/**
 * 一路字幕的完整状态。
 *
 * 主字幕与次字幕各持有一份，二者完全对称 —— 都能来自外挂文件或内嵌轨道，
 * 都有独立的时间偏移与样式。这是「同时挂载主副字幕」的核心数据结构。
 */
data class SubtitleTrack(
    val source: SubtitleSource = SubtitleSource.None,
    val cues: List<SubtitleCue> = emptyList(),
    /** 整轨时间偏移，正值表示该路字幕整体延后出现。 */
    val offsetMs: Long = 0L,
    val style: SubtitleStyle = SubtitleStyle.PRIMARY,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    /** 用于 UI 显示的来源名称。 */
    val label: String
        get() = when (val s = source) {
            SubtitleSource.None -> "关闭"
            is SubtitleSource.ExternalFile -> s.displayName
            is SubtitleSource.EmbeddedTrack -> s.label
        }

    /** 该路字幕共有多少条，供设置面板展示。 */
    val cueCount: Int get() = cues.size

    /** 取 [positionMs] 时刻应显示的字幕（已计入本路偏移）。 */
    fun cueAt(positionMs: Long): SubtitleCue? {
        if (cues.isEmpty()) return null
        return SubtitleCueIndex.find(cues, positionMs - offsetMs)
    }
}
