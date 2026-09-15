package com.dualsub.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.dualsub.tv.ui.AppRoot
import com.dualsub.tv.ui.theme.DualSubTVTheme

/**
 * 单一 Activity：内部由 [AppRoot] 的状态机在「媒体库」与「播放页」之间切换，
 * 不引入 navigation 组件，也不需要额外的返回栈处理。
 */
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 支持从文件管理器「用 DualSub TV 打开」直接进播放页
        val externalVideoUri = intent?.data

        setContent {
            DualSubTVTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(externalVideoUri = externalVideoUri)
                }
            }
        }
    }
}
