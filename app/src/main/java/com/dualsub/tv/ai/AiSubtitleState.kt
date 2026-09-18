package com.dualsub.tv.ai

sealed class AiSubtitleState {
    object Idle : AiSubtitleState()
    data class Running(val current: Int, val total: Int, val phase: String) : AiSubtitleState()
    data class Done(val srtPath: String) : AiSubtitleState()
    data class Failed(val reason: String) : AiSubtitleState()
}
