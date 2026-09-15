package com.dualsub.tv.ui.network

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.core.AppServices
import com.dualsub.tv.media.VideoItem
import com.dualsub.tv.network.RemoteEntry
import com.dualsub.tv.network.RemoteLocation
import com.dualsub.tv.network.RemoteType
import com.dualsub.tv.network.smb.SmbSession
import com.dualsub.tv.network.smb.SmbShareLister
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 网络目录浏览。SMB 与 DLNA 共用这一页 —— 差别都被 [com.dualsub.tv.network.RemoteBrowser] 吸收掉了。
 *
 * 用「路径栈」而不是字符串父路径来逐级前进/后退，因为 DLNA 的目录标识是 ObjectID，没有层级语义。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RemoteBrowseScreen(
    services: AppServices,
    location: RemoteLocation,
    onOpenVideo: (VideoItem) -> Unit,
    onExit: () -> Unit
) {
    val browser = remember(location.id) { services.browserFactory.create(location) }
    val stack = remember(location.id) { mutableStateListOf(browser.rootPath) }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val currentPath = stack.last()

    LaunchedEffect(currentPath) {
        loading = true
        error = null
        runCatching { withContext(Dispatchers.IO) { browser.list(currentPath) } }
            .onSuccess {
                entries = it
                loading = false
            }
            .onFailure {
                entries = emptyList()
                error = it.message ?: "读取目录失败"
                loading = false
            }
    }

    androidx.compose.runtime.DisposableEffect(browser) {
        onDispose { runCatching { browser.close() } }
    }

    // 返回键先退回上一级目录，到根目录才离开这一页（否则遥控器返回会直接退出应用）
    BackHandler {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else onExit()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E16))
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = location.displayName,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (currentPath.isBlank()) "根目录" else currentPath,
            fontSize = 13.sp,
            color = Color(0xFF90A4AE),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                enabled = stack.size > 1
            ) { Text("上一级", fontSize = 15.sp) }
            Button(onClick = onExit) { Text("返回", fontSize = 15.sp) }
        }

        error?.let {
            Text(text = "⚠ $it", fontSize = 14.sp, color = Color(0xFFFF8A80))
        }

        when {
            loading -> Text(
                text = "正在读取…",
                fontSize = 15.sp,
                color = Color(0xFFB0BEC5),
                modifier = Modifier.padding(top = 8.dp)
            )

            entries.isEmpty() && error == null -> Text(
                text = "这个目录里没有可播放的内容。",
                fontSize = 15.sp,
                color = Color(0xFFB0BEC5),
                modifier = Modifier.padding(top = 8.dp)
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.location + it.name }) { entry ->
                    Card(
                        onClick = {
                            if (entry.isDirectory) {
                                stack.add(entry.location)
                            } else {
                                entry.playableUri?.let { uri ->
                                    onOpenVideo(
                                        VideoItem(
                                            uri = android.net.Uri.parse(uri),
                                            displayName = entry.name,
                                            durationMs = 0L,
                                            sizeBytes = entry.sizeBytes,
                                            folderName = location.displayName
                                        )
                                    )
                                }
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = (if (entry.isDirectory) "📁  " else "▶  ") + entry.name,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (entry.isDirectory) "目录" else formatSize(entry.sizeBytes),
                                fontSize = 12.sp,
                                color = Color(0xFF90A4AE)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * SMB 服务器的添加 / 编辑表单。
 *
 * 共享名不必事先知道 —— 填好主机与账号后点「列出共享」，会直接向服务器要共享清单
 * （即其他播放器那套做法），选一个即可；服务器禁止枚举时会自动退化为尝试常见共享名，
 * 也允许完全手工输入。传入 [initial] 即为编辑模式（改 IP、改密码、换共享都走这里）。
 *
 * 内容用 [LazyColumn] 而不是普通 `Column`：共享列出来之后整页会超过一屏，
 * 而 `Column` 不可滚动 —— TV 上焦点移到屏幕外的元素时什么都不会发生，
 * 表现为「保存 / 取消点不到」。Lazy 列表对焦点的 bring-into-view 有内建支持，会自动滚。
 *
 * 输入框用 [TextFieldValue] 而不是裸 `String`：后者的初始 selection 落在文本开头，
 * 于是预填 `192.168.1.` 之后光标停在第一位，用户得先把光标挪到末尾才能接着输最后一段。
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SmbServerForm(
    services: AppServices,
    title: String,
    initial: RemoteLocation? = null,
    onCancel: () -> Unit,
    onSave: (RemoteLocation) -> Unit
) {
    val scope = rememberCoroutineScope()
    var hostField by remember { mutableStateOf(initial?.host.orEmpty().asFieldValueAtEnd()) }
    var shareField by remember { mutableStateOf(initial?.share.orEmpty().asFieldValueAtEnd()) }
    var usernameField by remember { mutableStateOf(initial?.username.orEmpty().asFieldValueAtEnd()) }
    var passwordField by remember { mutableStateOf(initial?.password.orEmpty().asFieldValueAtEnd()) }

    var listing by remember { mutableStateOf(false) }
    var shares by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    val host = hostField.text
    val share = shareField.text
    val username = usernameField.text
    val password = passwordField.text

    val canSave = host.isNotBlank() && share.isNotBlank()

    val firstField = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // 表单一打开就把焦点放到主机输入框，省得用户在整页里先找一遍
        runCatching { firstField.requestFocus() }
    }

    fun cleanHost(): String = host.trim().removePrefix("smb://").trimEnd('/')

    fun save() {
        val cleanHostValue = cleanHost()
        val cleanShare = share.trim().trim('/')
        if (cleanHostValue.isEmpty() || cleanShare.isEmpty()) return
        onSave(
            RemoteLocation(
                id = "smb:$cleanHostValue/$cleanShare",
                type = RemoteType.SMB,
                displayName = "$cleanHostValue/$cleanShare",
                host = cleanHostValue,
                share = cleanShare,
                username = username.takeIf { it.isNotBlank() },
                password = password.takeIf { it.isNotBlank() },
                domain = initial?.domain
            )
        )
    }

    fun loadShares() {
        val target = cleanHost()
        if (target.isEmpty()) return
        listing = true
        error = null
        scope.launch {
            val user = username.takeIf { it.isNotBlank() }
            val pass = password.takeIf { it.isNotBlank() }

            // 第一选择：直接向服务器要共享清单（SRVSVC NetShareEnumAll，jcifs-ng 自带）
            val enumerated = runCatching {
                withContext(Dispatchers.IO) { SmbShareLister.listShares(target, user, pass) }
            }.getOrNull().orEmpty()

            // 兜底：有些服务器禁用了枚举，这时退回「逐个尝试常见共享名」（smbj 侧）
            val outcome: Result<List<String>> = if (enumerated.isNotEmpty()) {
                Result.success(enumerated)
            } else {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val probe = SmbSession(
                            RemoteLocation(
                                id = "probe:$target",
                                type = RemoteType.SMB,
                                displayName = target,
                                host = target,
                                username = user,
                                password = pass
                            )
                        )
                        try {
                            probe.probeShares()
                        } finally {
                            runCatching { probe.close() }
                        }
                    }
                }
            }

            listing = false
            outcome
                .onSuccess { found ->
                    shares = found
                    when {
                        found.isEmpty() -> error =
                            "服务器没有返回共享清单，常见共享名也没命中。这不代表没有共享 —— " +
                                "请到 NAS / Windows 的共享设置里查看实际名称，然后在下面手工填写。"
                        share.isBlank() -> shareField = found.first().asFieldValueAtEnd()
                    }
                }
                .onFailure { error = it.message ?: "连接失败，请检查账号密码与网络" }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E16))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 60.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "title") {
                Text(text = title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            item(key = "hint") {
                Text(
                    text = "填好主机和账号后点「列出共享」，会直接读取服务器上的共享文件夹；" +
                        "若服务器禁止枚举，会自动退化为尝试常见共享名。也可以直接手工填写。",
                    fontSize = 12.sp,
                    color = Color(0xFF90A4AE)
                )
            }

            item(key = "host") {
                LabeledField("主机（IP 或主机名）", hostField, focusRequester = firstField) { hostField = it }
            }

            item(key = "username") {
                LabeledField("用户名（可留空 = 匿名）", usernameField) { usernameField = it }
            }

            item(key = "password") {
                LabeledField("密码", passwordField, isPassword = true) { passwordField = it }
            }

            item(key = "list-action") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { loadShares() }, enabled = host.isNotBlank() && !listing) {
                        Text(if (listing) "正在连接…" else "列出共享", fontSize = 15.sp)
                    }
                    if (shares.isNotEmpty()) {
                        Text(text = "点选下面任意一项即可", fontSize = 13.sp, color = Color(0xFF90A4AE))
                    }
                }
            }

            if (shares.isNotEmpty()) {
                item(key = "shares") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(shares, key = { it }) { name ->
                            Button(onClick = { shareField = name.asFieldValueAtEnd() }) {
                                Text(text = if (name == share) "✓ $name" else name, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            item(key = "share") {
                LabeledField("共享名（也可手工输入）", shareField) { shareField = it }
            }

            error?.let { message ->
                item(key = "error") {
                    Text(text = "⚠ $message", fontSize = 14.sp, color = Color(0xFFFF8A80))
                }
            }

            // 保存 / 取消放在最后一项：LazyColumn 会随焦点自动滚到这里
            item(key = "actions") {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { save() }, enabled = canSave) {
                        Text("保存", fontSize = 15.sp)
                    }
                    Button(onClick = onCancel) { Text("取消", fontSize = 15.sp) }
                }
            }
        }
    }
}

/** 把字符串包成「光标停在末尾」的输入框状态。 */
private fun String.asFieldValueAtEnd(): TextFieldValue =
    TextFieldValue(this, selection = TextRange(length))

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LabeledField(
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
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E2635))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                // 遥控器把焦点移到输入框时直接唤起软键盘，省掉「再按一次确认」的摸索
                .onFocusChanged { if (it.isFocused) keyboard?.show() }
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes.toDouble() / (1L shl 20))
    else -> "%.0f KB".format(bytes.toDouble() / 1024.0)
}
