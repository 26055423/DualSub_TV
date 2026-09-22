package com.dualsub.tv.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
 * ## 键盘弹出时机
 *
 * 只在用户**主动按 OK/Enter** 时才弹软键盘，而不是焦点一到就弹。
 * 遥控器 D-pad 移动焦点时键盘保持隐藏，用户可以自由浏览各字段、跳过不想填的项，
 * 停在目标字段再按 OK 才进入输入状态。键盘上按「完成」会收起键盘，焦点留在字段上。
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun BeiTextField(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
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
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BeiGlass.Glass)
                .border(BeiDims.Border, BeiGlass.Border, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                // 只在按 OK/Enter 时才弹出键盘，D-pad 移动焦点时不弹，可以自由跳过字段
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionCenter || event.key == Key.Enter)
                    ) {
                        keyboard?.show()
                        false
                    } else false
                }
        )
    }
}
