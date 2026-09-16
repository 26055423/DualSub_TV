package com.dualsub.tv.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.network.webdrive.BaiduAuthManager
import com.dualsub.tv.network.webdrive.BaiduAuthState
import com.dualsub.tv.network.webdrive.BaiduDeviceCodeResult

/**
 * 百度网盘 Device Code 登录页。
 *
 * TV 屏幕展示 user_code 和验证 URL，用户在手机/PC 上输入码或扫描二维码授权。
 * 授权成功后调用 [onSuccess]，携带 access_token 和 refresh_token。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BaiduLoginScreen(
    baiduAuth: BaiduAuthManager,
    onSuccess: (accessToken: String, refreshToken: String) -> Unit,
    onCancel: () -> Unit
) {
    val state by baiduAuth.state.collectAsState()
    var errorMsg by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        baiduAuth.startDeviceLogin(
            onCodeReady = { /* state 更新会触发 UI 重组 */ },
            onSuccess = { access, refresh -> onSuccess(access, refresh) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Text(
                text = "登录百度网盘",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            when (val s = state) {
                is BaiduAuthState.LoggedOut, is BaiduAuthState.Loading -> {
                    Text(
                        text = "正在获取授权码…",
                        fontSize = 16.sp,
                        color = Color(0xFF90A4AE)
                    )
                }

                is BaiduAuthState.WaitingForUser -> {
                    DeviceCodePanel(s.codeResult)
                }

                is BaiduAuthState.LoggedIn -> {
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DeviceCodePanel(result: BaiduDeviceCodeResult) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "请在手机或电脑浏览器中访问：",
            fontSize = 16.sp,
            color = Color(0xFF90A4AE)
        )

        // 突出显示验证 URL
        Box(
            modifier = Modifier
                .background(Color(0xFF1A237E), RoundedCornerShape(8.dp))
                .padding(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Text(
                text = result.verificationUrl,
                fontSize = 16.sp,
                color = Color(0xFFBBDEFB),
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            text = "然后输入以下验证码：",
            fontSize = 16.sp,
            color = Color(0xFF90A4AE)
        )

        // 大字显示验证码
        Box(
            modifier = Modifier
                .background(Color(0xFF0D47A1), RoundedCornerShape(12.dp))
                .padding(horizontal = 40.dp, vertical = 20.dp)
        ) {
            Text(
                text = result.userCode,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 8.sp
            )
        }

        Text(
            text = "授权成功后本页面将自动跳转",
            fontSize = 14.sp,
            color = Color(0xFF607D8B),
            textAlign = TextAlign.Center
        )

        Text(
            text = "验证码有效期 ${result.expiresIn / 60} 分钟",
            fontSize = 12.sp,
            color = Color(0xFF455A64),
            textAlign = TextAlign.Center
        )
    }
}
