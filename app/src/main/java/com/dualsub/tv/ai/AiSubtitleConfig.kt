package com.dualsub.tv.ai

data class AiSubtitleConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val targetLang: String,
    val prompt: String,
    /** 实时字幕每次提取的窗口大小（秒），可选 10 / 20 / 40 / 60，默认 20。 */
    val liveWindowSec: Int = 20,
    /** 实时字幕超前播放位置的缓冲量（秒），可选 20 / 40 / 60，默认 40。 */
    val liveLeadSec: Int = 40
)
