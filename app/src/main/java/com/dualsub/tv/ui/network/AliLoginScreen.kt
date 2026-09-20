package com.dualsub.tv.ui.network

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.network.webdrive.AliAuthManager
import com.dualsub.tv.network.webdrive.AliAuthState
import com.dualsub.tv.ui.shell.BeiPillButton
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 阿里云盘扫码登录页（外壳之外整屏渲染，见 `AppRoot` 的导航结构）。
 *
 * 深墨夜景底 + **无色玻璃面板**（`docs/UI_STYLE_REFERENCE.md`）：面板本身不上色，
 * 层级靠白 6% 底 + 白 15% 描边表达；状态文字走"白 100% / 60% / 40%"三档，
 * 只有成功 / 警告 / 错误才短暂借用语义色。
 *
 * 二维码那张**白底**是刻意保留的：扫码识别依赖白底黑块，那里不能套玻璃。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AliLoginScreen(
    aliAuth: AliAuthManager,
    onSuccess: (accessToken: String, refreshToken: String, driveId: String) -> Unit,
    onCancel: () -> Unit
) {
    val state by aliAuth.state.collectAsState()
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        aliAuth.startQrLogin(
            onSuccess = { access, refresh, driveId -> onSuccess(access, refresh, driveId) },
            onError = { msg -> errorMsg = msg }
        )
    }

    val panelShape = RoundedCornerShape(BeiDims.CardRadius)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BeiGlass.Night),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(panelShape)
                .background(BeiGlass.Glass)
                .border(BeiDims.Border, BeiGlass.Border, panelShape)
                .padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "登录阿里云盘",
                fontSize = BeiDims.TitleSize,
                fontWeight = FontWeight.Bold,
                color = BeiGlass.TextPrimary
            )

            when (val s = state) {
                is AliAuthState.LoggedOut, is AliAuthState.Loading -> {
                    Text(
                        text = "正在获取二维码…",
                        fontSize = BeiDims.CardTitleSize,
                        color = BeiGlass.TextSecondary
                    )
                }

                is AliAuthState.QrReady -> {
                    val bitmap = remember(s.qrUrl) { generateQrBitmapAli(s.qrUrl, 400) }
                    if (bitmap != null) {
                        Box(
                            modifier = Modifier
                                .background(BeiGlass.QrSurface, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "阿里云盘登录二维码",
                                modifier = Modifier.size(200.dp)
                            )
                        }
                    }
                    Text(
                        text = "请用阿里云盘 App 扫码登录",
                        fontSize = BeiDims.CardTitleSize,
                        color = BeiGlass.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "扫码后在 App 内点击「确认授权」",
                        fontSize = BeiDims.BodySize,
                        color = BeiGlass.TextMuted,
                        textAlign = TextAlign.Center
                    )
                }

                is AliAuthState.Scanned -> {
                    Text(
                        text = "已扫码，请在 App 内确认授权…",
                        fontSize = 18.sp,
                        color = BeiGlass.Warning,
                        textAlign = TextAlign.Center
                    )
                }

                is AliAuthState.LoggedIn -> {
                    Text(
                        text = "授权成功！正在进入…",
                        fontSize = 18.sp,
                        color = BeiGlass.Success,
                        textAlign = TextAlign.Center
                    )
                }
            }

            val err = errorMsg
            if (err != null) {
                Text(
                    text = "⚠ $err",
                    fontSize = 14.sp,
                    color = BeiGlass.Danger,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(8.dp))
            BeiPillButton(label = "取消", onClick = onCancel)
        }
    }
}

private fun generateQrBitmapAli(content: String, sizePx: Int): Bitmap? = runCatching {
    val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565).also { bmp ->
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (bits[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
}.getOrNull()
