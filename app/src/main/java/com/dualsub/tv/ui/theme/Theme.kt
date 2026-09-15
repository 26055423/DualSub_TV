package com.dualsub.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val DualSubColorScheme: ColorScheme
    get() = darkColorScheme()

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DualSubTVTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DualSubColorScheme,
        content = content
    )
}
