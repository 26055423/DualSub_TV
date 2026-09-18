@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.dualsub.tv.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.ai.AiSubtitleConfig
import com.dualsub.tv.ai.AiSubtitleConfigServer
import com.dualsub.tv.data.SettingsStore
import com.dualsub.tv.ui.util.generateQrBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * AI 字幕配置页。
 *
 * 显示本机局域网 IP + 18080 端口的二维码，手机扫码后打开浏览器填写 API Key 等参数。
 * 提交后 TV 端通过 configReceived StateFlow 感知更新并刷新当前配置显示。
 */
@Composable
fun AiSettingsScreen(
    settings: SettingsStore,
    onExit: () -> Unit
) {
    val server = remember { AiSubtitleConfigServer(settings) }
    val configReceived by server.configReceived.collectAsState()
    val currentConfig by settings.aiConfig.collectAsState(initial = AiSubtitleConfig(
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        apiKey = "",
        model = "qwen3.8-omni-flash",
        targetLang = "中文",
        prompt = ""
    ))

    val lanIp = remember { getLanIpAddress() }
    val serverUrl = if (lanIp != null) "http://$lanIp:${AiSubtitleConfigServer.PORT}" else null
    val scope = rememberCoroutineScope()

    val qrBitmap: Bitmap? = remember(serverUrl) {
        serverUrl?.let { runCatching { generateQrBitmap(it, 400) }.getOrNull() }
    }

    // 启动 / 停止 HTTP server 跟随页面生命周期
    DisposableEffect(server) {
        runCatching { server.start() }
        onDispose { runCatching { server.stop() } }
    }

    // 每次 configReceived 变化时短暂显示「已更新」提示
    var justSaved by remember { mutableStateOf(false) }
    LaunchedEffect(configReceived) {
        if (configReceived > 0) {
            justSaved = true
            delay(3000)
            justSaved = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E16))
            .padding(horizontal = 48.dp, vertical = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 左：二维码 + 说明
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.width(280.dp)
        ) {
            Text(text = "AI 字幕设置", fontSize = 22.sp, fontWeight = FontWeight.Bold)

            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "配置二维码",
                        modifier = Modifier.size(200.dp)
                    )
                }
                Text(
                    text = "用手机扫码，在浏览器填写 API Key 并提交",
                    fontSize = 13.sp,
                    color = Color(0xFF90A4AE)
                )
                Text(
                    text = serverUrl ?: "",
                    fontSize = 12.sp,
                    color = Color(0xFF546E7A)
                )
            } else {
                Text(
                    text = if (lanIp == null) "未连接局域网，无法显示二维码。\n请确认电视已接入 Wi-Fi 或以太网。"
                    else "生成二维码失败",
                    fontSize = 13.sp,
                    color = Color(0xFFFFD54F)
                )
            }

            Button(onClick = onExit) { Text("返回") }
        }

        // 右：当前配置预览
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (justSaved) "✅ 配置已更新" else "当前配置",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (justSaved) Color(0xFF81C784) else Color.White
            )

            ConfigRow("Base URL", currentConfig.baseUrl.ifBlank { "（未设置）" })
            ConfigRow("API Key", if (currentConfig.apiKey.isBlank()) "（未设置）" else "••••••••${currentConfig.apiKey.takeLast(4)}")
            ConfigRow("模型", currentConfig.model.ifBlank { "（未设置）" })
            ConfigRow("目标语言", currentConfig.targetLang.ifBlank { "中文" })
            if (currentConfig.prompt.isNotBlank()) {
                ConfigRow("提示词", currentConfig.prompt)
            }

            Text(
                text = "提示：API Key 通过百炼控制台获取，按量计费约 0.8 元/百万 tokens。",
                fontSize = 12.sp,
                color = Color(0xFF546E7A)
            )

            // ---- 实时字幕参数
            Text(
                text = "实时字幕参数",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF90A4AE),
                modifier = androidx.compose.ui.Modifier.padding(top = 8.dp)
            )
            val liveWindowOptions = listOf(10, 20, 40, 60)
            val liveLeadOptions   = listOf(20, 40, 60)
            StepperRow(
                label = "窗口大小",
                value = "${currentConfig.liveWindowSec}s",
                detail = "每次提取并翻译的音频长度",
                onDecrease = {
                    val idx = liveWindowOptions.indexOf(currentConfig.liveWindowSec)
                    if (idx > 0) scope.launch {
                        settings.saveAiConfig(currentConfig.copy(liveWindowSec = liveWindowOptions[idx - 1]))
                    }
                },
                onIncrease = {
                    val idx = liveWindowOptions.indexOf(currentConfig.liveWindowSec)
                    if (idx < liveWindowOptions.lastIndex) scope.launch {
                        settings.saveAiConfig(currentConfig.copy(liveWindowSec = liveWindowOptions[idx + 1]))
                    }
                }
            )
            StepperRow(
                label = "超前缓冲",
                value = "${currentConfig.liveLeadSec}s",
                detail = "提前播放位置多少秒开始翻译",
                onDecrease = {
                    val idx = liveLeadOptions.indexOf(currentConfig.liveLeadSec)
                    if (idx > 0) scope.launch {
                        settings.saveAiConfig(currentConfig.copy(liveLeadSec = liveLeadOptions[idx - 1]))
                    }
                },
                onIncrease = {
                    val idx = liveLeadOptions.indexOf(currentConfig.liveLeadSec)
                    if (idx < liveLeadOptions.lastIndex) scope.launch {
                        settings.saveAiConfig(currentConfig.copy(liveLeadSec = liveLeadOptions[idx + 1]))
                    }
                }
            )

            // ---- 视频缓冲大小
            val videoCachingMs by settings.videoCachingMs.collectAsState(initial = 1500)
            val cachingOptions = listOf(500, 1000, 1500, 3000, 6000)
            Text(
                text = "视频播放缓冲",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF90A4AE),
                modifier = androidx.compose.ui.Modifier.padding(top = 8.dp)
            )
            StepperRow(
                label = "缓冲大小",
                value = "${videoCachingMs}ms",
                detail = "调大可减少卡顿，调小降低延迟；重新打开视频后生效",
                onDecrease = {
                    val idx = cachingOptions.indexOf(videoCachingMs)
                    if (idx > 0) scope.launch { settings.setVideoCachingMs(cachingOptions[idx - 1]) }
                },
                onIncrease = {
                    val idx = cachingOptions.indexOf(videoCachingMs)
                    if (idx < cachingOptions.lastIndex) scope.launch { settings.setVideoCachingMs(cachingOptions[idx + 1]) }
                }
            )
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF90A4AE),
            modifier = Modifier.width(80.dp)
        )
        Text(text = value, fontSize = 14.sp, color = Color(0xFFE0E0E0))
    }
}

private fun getLanIpAddress(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces()?.asSequence()
        ?.flatMap { it.inetAddresses.asSequence() }
        ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
        ?.hostAddress
}.getOrNull()

@Composable
private fun StepperRow(
    label: String,
    value: String,
    detail: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp, color = Color(0xFF90A4AE),
            modifier = androidx.compose.ui.Modifier.width(72.dp))
        Button(onClick = onDecrease) { Text("－") }
        Text(text = value, fontSize = 14.sp, color = Color(0xFFE0E0E0),
            modifier = androidx.compose.ui.Modifier.widthIn(min = 56.dp))
        Button(onClick = onIncrease) { Text("＋") }
        Text(text = detail, fontSize = 11.sp, color = Color(0xFF546E7A))
    }
}
