package com.dualsub.tv.ui.network

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.network.webdrive.QuarkAuthManager
import com.dualsub.tv.network.webdrive.QuarkAuthState
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 夸克网盘扫码登录页。
 *
 * TV 屏幕展示二维码，用户用手机夸克 App 扫码授权。
 * 授权成功后调用 [onSuccess]，携带获得的 Cookie 字符串。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QuarkLoginScreen(
    quarkAuth: QuarkAuthManager,
    onSuccess: (cookie: String) -> Unit,
    onCancel: () -> Unit
) {
    val state by quarkAuth.state.collectAsState()
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        quarkAuth.startQrLogin(
            onQrReady = { qrUrl ->
                // 在 IO 线程生成二维码 bitmap
                val bitmap = withContext(Dispatchers.Default) {
                    generateQrBitmap(qrUrl, 400)
                }
                qrBitmap = bitmap
            },
            onScanned = { /* state 已更新，UI 响应 */ },
            onSuccess = { cookie -> onSuccess(cookie) },
            onError = { msg -> errorMsg = msg }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E16)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Text(
                text = "登录夸克网盘",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            when (val s = state) {
                is QuarkAuthState.LoggedOut, is QuarkAuthState.Loading -> {
                    Text(
                        text = "正在获取二维码…",
                        fontSize = 16.sp,
                        color = Color(0xFF90A4AE)
                    )
                }

                is QuarkAuthState.QrReady -> {
                    val bitmap = qrBitmap
                    if (bitmap != null) {
                        Box(
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "夸克网盘扫码登录",
                                modifier = Modifier.size(200.dp)
                            )
                        }
                    }
                    Text(
                        text = "请用手机夸克 App 扫描上方二维码",
                        fontSize = 16.sp,
                        color = Color(0xFF90A4AE),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "扫码后在手机上确认授权",
                        fontSize = 14.sp,
                        color = Color(0xFF607D8B),
                        textAlign = TextAlign.Center
                    )
                }

                is QuarkAuthState.Scanned -> {
                    Text(
                        text = "已扫码，请在手机上确认授权…",
                        fontSize = 18.sp,
                        color = Color(0xFFFFD54F),
                        textAlign = TextAlign.Center
                    )
                }

                is QuarkAuthState.LoggedIn -> {
                    Text(
                        text = "授权成功！正在进入…",
                        fontSize = 18.sp,
                        color = Color(0xFF66BB6A),
                        textAlign = TextAlign.Center
                    )
                }
            }

            val err = errorMsg
            if (err != null) {
                Text(
                    text = "⚠ $err",
                    fontSize = 14.sp,
                    color = Color(0xFFFF8A80),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = onCancel) {
                Text("取消", fontSize = 14.sp)
            }
        }
    }
}

private fun generateQrBitmap(content: String, sizePx: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bmp
}
