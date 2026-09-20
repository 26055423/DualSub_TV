package com.dualsub.tv.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.network.RemoteLocation
import com.dualsub.tv.network.RemoteType
import com.dualsub.tv.ui.shell.BeiPillButton
import com.dualsub.tv.ui.shell.BeiTextField
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass
import java.util.UUID

/**
 * 添加 / 编辑 WebDAV 服务器表单（整屏，不进外壳）。
 *
 * 视觉：深墨夜景底 + 无色玻璃输入框（白 6% 底 + 白 15% 描边）。
 * 输入框样式走公用的 [BeiTextField]，「飞牛 NAS」表单用的是同一套。
 *
 * 「保存」按钮始终可点：原先靠 `enabled = canSave` 灰掉，但电视上没有 hover 提示，
 * 灰按钮反而像"界面坏了"；现在改成点击时静默校验（URL 为空则不保存），行为一致。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WebDavServerForm(
    initial: RemoteLocation? = null,
    onCancel: () -> Unit,
    onSave: (RemoteLocation) -> Unit
) {
    fun String.atEnd() = TextFieldValue(this, selection = TextRange(length))

    var urlField by remember { mutableStateOf(initial?.host.orEmpty().atEnd()) }
    var usernameField by remember { mutableStateOf(initial?.username.orEmpty().atEnd()) }
    var passwordField by remember { mutableStateOf(initial?.password.orEmpty().atEnd()) }
    var displayNameField by remember { mutableStateOf(initial?.displayName.orEmpty().atEnd()) }

    val canSave = urlField.text.isNotBlank()
    val firstField = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstField.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BeiGlass.Night)
            .padding(
                horizontal = BeiDims.ScreenStart,
                vertical = BeiDims.ScreenVertical
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (initial == null) "添加 WebDAV 服务器" else "编辑 WebDAV 服务器",
            fontSize = BeiDims.TitleSize,
            fontWeight = FontWeight.Bold,
            color = BeiGlass.TextPrimary
        )
        Text(
            text = "支持 AList、Nextcloud、Nginx 等 WebDAV 服务；接飞牛 NAS 请用「飞牛 NAS」入口，不必手填端口。",
            fontSize = BeiDims.BodySize,
            color = BeiGlass.TextSecondary
        )
        Spacer(Modifier.height(4.dp))

        BeiTextField(
            label = "服务器地址（URL）",
            value = urlField,
            onValueChange = { urlField = it },
            focusRequester = firstField
        )
        BeiTextField(
            label = "显示名称（可选）",
            value = displayNameField,
            onValueChange = { displayNameField = it }
        )
        BeiTextField(
            label = "用户名（匿名留空）",
            value = usernameField,
            onValueChange = { usernameField = it }
        )
        BeiTextField(
            label = "密码（匿名留空）",
            value = passwordField,
            isPassword = true,
            onValueChange = { passwordField = it }
        )

        Spacer(Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BeiPillButton(
                label = "保存",
                onClick = {
                    if (!canSave) return@BeiPillButton
                    val url = urlField.text.trim().trimEnd('/')
                    val name = displayNameField.text.trim().ifBlank {
                        url.removePrefix("http://").removePrefix("https://")
                    }
                    val id = initial?.id ?: "webdav:${UUID.randomUUID()}"
                    onSave(
                        RemoteLocation(
                            id = id,
                            type = RemoteType.WEBDAV,
                            displayName = name,
                            host = url,
                            username = usernameField.text.trim().takeIf { it.isNotBlank() },
                            password = passwordField.text.trim().takeIf { it.isNotBlank() }
                        )
                    )
                }
            )
            Spacer(Modifier.width(4.dp))
            BeiPillButton(label = "取消", onClick = onCancel)
        }
    }
}
