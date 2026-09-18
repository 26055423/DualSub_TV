package com.dualsub.tv.ai

sealed class AiSubtitleState {
    object Idle : AiSubtitleState()
    data class Running(val current: Int, val total: Int, val phase: String) : AiSubtitleState()
    data class Done(val srtPath: String) : AiSubtitleState()
    data class Failed(val reason: String) : AiSubtitleState()
    /** 实时流模式进行中：[bufferedMs] 已完成提取的时间戳，[phase] 当前阶段说明。 */
    data class Live(val bufferedMs: Long, val phase: String) : AiSubtitleState()
    /** 实时流模式失败；[canFallback] 为 true 时 UI 引导用户切换批处理模式。 */
    data class LiveFailed(val reason: String, val canFallback: Boolean) : AiSubtitleState()
}
