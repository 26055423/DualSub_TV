package com.dualsub.tv.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.network.RemoteLocation
import com.dualsub.tv.network.RemoteType
import java.util.UUID

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
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
            .background(Color(0xFF0B0E16))
            .padding(horizontal = 60.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (initial == null) "添加 WebDAV 服务器" else "编辑 WebDAV 服务器",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "支持 AList、Nextcloud、Nginx 等 WebDAV 服务。",
            fontSize = 13.sp,
            color = Color(0xFF90A4AE)
        )
        Spacer(Modifier.height(4.dp))

        WdField(
            label = "服务器地址（URL）",
            value = urlField,
            focusRequester = firstField,
            onValueChange = { urlField = it }
        )
        WdField(
            label = "显示名称（可选）",
            value = displayNameField,
            onValueChange = { displayNameField = it }
        )
        WdField(
            label = "用户名（匿名留空）",
            value = usernameField,
            onValueChange = { usernameField = it }
        )
        WdField(
            label = "密码（匿名留空）",
            value = passwordField,
            isPassword = true,
            onValueChange = { passwordField = it }
        )

        Spacer(Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
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
                },
                enabled = canSave
            ) { Text("保存", fontSize = 14.sp) }
            Spacer(Modifier.width(4.dp))
            Button(onClick = onCancel) { Text("取消", fontSize = 14.sp) }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WdField(
    label: String,
    value: TextFieldValue,
    focusRequester: FocusRequester? = null,
    isPassword: Boolean = false,
    onValueChange: (TextFieldValue) -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 13.sp, color = Color(0xFFB0BEC5))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Color.White),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E2635))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .onFocusChanged { if (it.isFocused) keyboard?.show() }
        )
    }
}
