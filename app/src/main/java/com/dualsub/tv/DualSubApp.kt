package com.dualsub.tv

import android.app.Application
import com.dualsub.tv.core.InAppLog

class DualSubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 尽早开始收集本进程日志（含 libVLC 的 native 报错），
        // 播放页可以把它直接显示在电视画面上，省掉连电脑抓 logcat。
        InAppLog.start()
        // 构建时刻进日志：排查时第一件事就是确认「跑的是不是最新编译的那个包」
        android.util.Log.i("DualSubTV", "DualSub TV 构建于 ${BuildConfig.BUILD_TIME}")
    }
}