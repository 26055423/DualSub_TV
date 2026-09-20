@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.dualsub.tv.ui.settings

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.ai.AiSubtitleConfig
import com.dualsub.tv.ai.AiSubtitleConfigServer
import com.dualsub.tv.data.SettingsStore
import com.dualsub.tv.ui.shell.BeiChoiceRow
import com.dualsub.tv.ui.shell.BeiPageHeader
import com.dualsub.tv.ui.shell.BeiPillButton
import com.dualsub.tv.ui.shell.BeiStaticCard
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass
import com.dualsub.tv.ui.util.generateQrBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * AI 字幕配置页 —— 「设置」里的**二级页**（早先它占过一个一级导航项，现按用户要求收回设置里）。
 *
 * 交互有意做成"电视只管用、配置交给手机"：TV 上用遥控器敲 Base URL / API Key 是折磨，
 * 所以电视起一个局域网 HTTP 服务并显示二维码，手机扫码在浏览器里填。配置提交后电视端
 * 通过 [AiSubtitleConfigServer.configReceived] 立刻感知并刷新本页显示。
 *
 * **AI 的「用」不在这里**：生成 / 实时翻译在播放页菜单的字幕二级里，本页只负责「配」。
 *
 * 视觉：深墨夜景底（由外壳提供）+ 无色玻璃卡；二维码那张**白底**刻意保留 ——
 * 扫码识别依赖白底黑块，那里不能套玻璃。
 *
 * @param onBack 返回上一页（设置主页，或播放中从菜单跳过来时的播放画面）。
 */
@Composable
fun AiSettingsScreen(
    settings: SettingsStore,
    onBack: () -> Unit = {}
) {
    // 返回键退回上一页，而不是直接退出应用 —— 这一页可能从设置进来，也可能从播放菜单跳过来。
    BackHandler(onBack = onBack)

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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BeiPageHeader(
                title = "AI 字幕",
                subtitle = "用 OpenAI 兼容接口（默认阿里云百炼 Qwen-Omni）把外语片译成字幕；配置只存在电视本地"
            )
            Spacer(modifier = Modifier.weight(1f))
            BeiPillButton(label = "返回", onClick = onBack)
        }

        Row(
            modifier = Modifier.padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            // ---- 左：手机扫码配置
            BeiStaticCard(modifier = Modifier.width(320.dp)) {
                Text(
                    text = "手机扫码配置",
                    color = BeiGlass.TextPrimary,
                    fontSize = BeiDims.CardTitleSize,
                    fontWeight = FontWeight.SemiBold
                )

                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .background(BeiGlass.QrSurface, RoundedCornerShape(14.dp))
                            .padding(10.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "配置二维码",
                            modifier = Modifier.size(200.dp)
                        )
                    }
                    Text(
                        text = "手机与电视连同一个 Wi-Fi，扫码后在浏览器里填 API Key 并提交。",
                        color = BeiGlass.TextSecondary,
                        fontSize = BeiDims.CaptionSize
                    )
                    Text(
                        text = serverUrl.orEmpty(),
                        color = BeiGlass.TextMuted,
                        fontSize = BeiDims.CaptionSize
                    )
                } else {
                    Text(
                        text = if (lanIp == null) {
                            "未连接局域网，无法显示二维码。请确认电视已接入 Wi-Fi 或网线。"
                        } else {
                            "二维码生成失败"
                        },
                        color = BeiGlass.Link,
                        fontSize = BeiDims.BodySize
                    )
                }
            }

            // ---- 右：当前配置 + 实时字幕参数
            BeiStaticCard(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (justSaved) "配置已更新" else "当前配置",
                    color = if (justSaved) BeiGlass.Success else BeiGlass.TextPrimary,
                    fontSize = BeiDims.CardTitleSize,
                    fontWeight = FontWeight.SemiBold
                )

                ConfigRow("Base URL", currentConfig.baseUrl.ifBlank { "（未设置）" })
                ConfigRow(
                    "API Key",
                    if (currentConfig.apiKey.isBlank()) "（未设置）"
                    else "••••••••${currentConfig.apiKey.takeLast(4)}"
                )
                ConfigRow("模型", currentConfig.model.ifBlank { "（未设置）" })
                ConfigRow("目标语言", currentConfig.targetLang.ifBlank { "中文" })
                if (currentConfig.prompt.isNotBlank()) {
                    ConfigRow("提示词", currentConfig.prompt)
                }

                Text(
                    text = "API Key 在百炼控制台获取，按量计费（约 0.8 元/百万 tokens）。",
                    color = BeiGlass.TextMuted,
                    fontSize = BeiDims.CaptionSize
                )

                Text(
                    text = "实时字幕参数",
                    color = BeiGlass.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp)
                )

                BeiChoiceRow(
                    label = "窗口大小",
                    options = LIVE_WINDOW_OPTIONS.map { "${it}s" },
                    selectedIndex = exactIndex(LIVE_WINDOW_OPTIONS, currentConfig.liveWindowSec),
                    onSelect = { index ->
                        scope.launch {
                            settings.saveAiConfig(
                                currentConfig.copy(liveWindowSec = LIVE_WINDOW_OPTIONS[index])
                            )
                        }
                    },
                    detail = "每次提取并翻译的音频长度"
                )

                BeiChoiceRow(
                    label = "超前缓冲",
                    options = LIVE_LEAD_OPTIONS.map { "${it}s" },
                    selectedIndex = exactIndex(LIVE_LEAD_OPTIONS, currentConfig.liveLeadSec),
                    onSelect = { index ->
                        scope.launch {
                            settings.saveAiConfig(
                                currentConfig.copy(liveLeadSec = LIVE_LEAD_OPTIONS[index])
                            )
                        }
                    },
                    detail = "提前播放位置多少秒开始翻译"
                )
            }
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            color = BeiGlass.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.width(84.dp)
        )
        Text(text = value, color = BeiGlass.TextPrimary, fontSize = 14.sp)
    }
}

private fun getLanIpAddress(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces()?.asSequence()
        ?.flatMap { it.inetAddresses.asSequence() }
        ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
        ?.hostAddress
}.getOrNull()

/** 实时字幕的窗口长度 / 超前缓冲档位（秒）。 */
private val LIVE_WINDOW_OPTIONS = listOf(10, 20, 40, 60)
private val LIVE_LEAD_OPTIONS = listOf(20, 40, 60)

/** 精确匹配档位；没命中返回 -1（界面一个都不选中，而不是偷偷就近取整）。 */
private fun exactIndex(options: List<Int>, value: Int): Int = options.indexOf(value)
