package com.dualsub.tv.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.dualsub.tv.network.nas.NasVendor
import com.dualsub.tv.network.nas.NasWebDavProbe
import com.dualsub.tv.ui.shell.BeiPillButton
import com.dualsub.tv.ui.shell.BeiTextField
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass
import kotlinx.coroutines.launch

/**
 * 按品牌接入一台 NAS。
 *
 * ## 它和通用「WebDAV」表单的区别
 *
 * 走的是同一个协议（见 [NasWebDavProbe]），但通用表单要求用户把
 * `http://192.168.1.252:5005` 这种完整地址敲进电视遥控器 —— 对"接我自己那台 NAS"这件事
 * 毫无必要。这里按 [vendor] 预填该家默认端口、印出该家的开启路径与注意事项，
 * 用户只要 **地址 + 账号 + 密码**，剩下的由探测决定。
 *
 * 四家共用这一个表单：差异全在 [NasVendor] 的数据里（端口、菜单路径、坑），
 * 不各写一遍 —— 那样改一处必漏三处。
 *
 * 「连接」按不动 / 连不上时给的是可执行的下一步（去后台哪儿开、检查是否同网段），
 * 而不是把异常抛给用户看。
 *
 * 视觉与 [WebDavServerForm] 同源：深墨夜景底 + 无色玻璃输入框（[BeiTextField]）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NasServerForm(
    vendor: NasVendor,
    initial: RemoteLocation? = null,
    onCancel: () -> Unit,
    onSave: (RemoteLocation) -> Unit
) {
    val scope = rememberCoroutineScope()
    // 编辑已有位置时 host 存的是完整 URL（http://ip:5005），拆回主机和端口再预填。
    val parsed = remember(initial) { NasWebDavProbe.parse(initial?.host.orEmpty()) }
    var hostField by remember { mutableStateOf(parsed.host.atEnd()) }
    var portField by remember {
        // 新加时预填该家的默认端口；编辑时用位置里实际存下来的那个。
        mutableStateOf((parsed.port?.toString() ?: vendor.defaultPortText).atEnd())
    }
    var usernameField by remember { mutableStateOf(initial?.username.orEmpty().atEnd()) }
    var passwordField by remember { mutableStateOf(initial?.password.orEmpty().atEnd()) }

    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val firstField = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstField.requestFocus() } }

    fun connect() {
        if (connecting) return
        val entered = hostField.text.trim()
        if (entered.isBlank()) {
            error = "请先填写 NAS 的地址"
            return
        }
        val username = usernameField.text.trim().takeIf { it.isNotBlank() }
        val password = passwordField.text.takeIf { it.isNotBlank() }
        connecting = true
        error = null
        scope.launch {
            val outcome = NasWebDavProbe.probe(
                rawHost = entered,
                rawPort = portField.text,
                username = username,
                password = password,
                httpPort = vendor.httpPort,
                httpsPort = vendor.httpsPort
            )
            connecting = false
            when (outcome) {
                is NasWebDavProbe.Outcome.Reachable -> onSave(
                    RemoteLocation(
                        // id 里带上品牌，编辑时才知道该回哪个品牌的表单。
                        id = "nas:${vendor.name}:${outcome.baseUrl}",
                        type = RemoteType.NAS,
                        displayName = "${vendor.label} · ${NasWebDavProbe.parse(entered).host}",
                        host = outcome.baseUrl,
                        username = username,
                        password = password
                    )
                )

                NasWebDavProbe.Outcome.AuthFailed ->
                    error = "服务在，但账号或密码不对。请填登录 ${vendor.systemName} 的那个账号。"

                NasWebDavProbe.Outcome.NotWebDav ->
                    error = "地址通了，但那个端口不是 WebDAV。请确认已在「${vendor.enablePath}」开启。"

                is NasWebDavProbe.Outcome.Unreachable -> error =
                    "${outcome.reason}。请确认已开启 WebDAV：${vendor.enablePath}。"
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BeiGlass.Night)
            .imePadding(),
        contentPadding = PaddingValues(
            horizontal = BeiDims.ScreenStart,
            vertical = BeiDims.ScreenVertical
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "title") {
            Text(
                text = if (initial == null) "接入${vendor.label}" else "编辑${vendor.label}",
                fontSize = BeiDims.TitleSize,
                fontWeight = FontWeight.Bold,
                color = BeiGlass.TextPrimary
            )
        }
        item(key = "desc") {
            Text(
                text = "${vendor.systemName}：先在「${vendor.enablePath}」开启 WebDAV，" +
                    "然后填下面的地址与账号即可 —— 端口已按该家默认值预填，探测会自动校正。",
                fontSize = BeiDims.BodySize,
                color = BeiGlass.TextSecondary
            )
        }
        if (vendor.caveat.isNotBlank()) {
            item(key = "caveat") {
                Text(
                    text = vendor.caveat,
                    fontSize = BeiDims.CaptionSize,
                    color = BeiGlass.TextMuted
                )
            }
        }
        item(key = "host") {
            BeiTextField(
                label = "NAS 地址（IP 或主机名）",
                value = hostField,
                onValueChange = { hostField = it },
                focusRequester = firstField
            )
        }
        item(key = "port") {
            BeiTextField(
                label = "端口（默认 ${vendor.httpPort} / ${vendor.httpsPort}）",
                value = portField,
                onValueChange = { portField = it }
            )
        }
        item(key = "username") {
            BeiTextField(
                label = "用户名（${vendor.systemName} 的登录账号）",
                value = usernameField,
                onValueChange = { usernameField = it }
            )
        }
        item(key = "password") {
            BeiTextField(
                label = "密码",
                value = passwordField,
                isPassword = true,
                onValueChange = { passwordField = it }
            )
        }
        error?.let { text ->
            item(key = "error") {
                Text(text = text, fontSize = BeiDims.BodySize, color = BeiGlass.Danger)
            }
        }
        item(key = "actions") {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BeiPillButton(
                    label = if (connecting) "正在连接…" else "连接",
                    onClick = { connect() }
                )
                Spacer(Modifier.width(4.dp))
                BeiPillButton(label = "取消", onClick = onCancel)
            }
        }
    }
}

/** 初始光标落在末尾 —— 电视上接着往下打字时不用先按方向键把光标挪过去。 */
private fun String.atEnd() = TextFieldValue(this, selection = TextRange(length))
