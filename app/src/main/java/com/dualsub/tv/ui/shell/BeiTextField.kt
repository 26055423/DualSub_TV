package com.dualsub.tv.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass

/**
 * 表单里的一行输入框：标签在上、玻璃底在下。
 *
 * 输入框的**描边比外壳卡片亮一档**（`BeiGlass.Border` 而非更淡的那档）是刻意的 ——
 * 表单里眼睛要找的是"能打字的地方"，它得比背景上的装饰面板更明确。
 *
 * 焦点落到输入框上时主动拉起软键盘：电视上光标移动和键盘是两件事，
 * 不主动拉的话用户会以为"点不进去"。
 *
 * 原先这套样式内联在 `WebDavServerForm` 的私有 `WdField` 里；「飞牛 NAS」表单要用同一套，
 * 就提出来共用，避免两份"看起来一样但改一处漏一处"的输入框。
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun BeiTextField(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester? = null,
    isPassword: Boolean = false
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = BeiDims.BodySize, color = BeiGlass.TextSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = BeiGlass.TextPrimary, fontSize = 16.sp),
            cursorBrush = SolidColor(BeiGlass.AccentBright),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BeiGlass.Glass)
                .border(BeiDims.Border, BeiGlass.Border, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .onFocusChanged { if (it.isFocused) keyboard?.show() }
        )
    }
}
