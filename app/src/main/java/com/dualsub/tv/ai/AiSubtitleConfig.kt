package com.dualsub.tv.ai

data class AiSubtitleConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val targetLang: String,
    val prompt: String
)
