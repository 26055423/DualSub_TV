package com.dualsub.tv.ui.network

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.core.AppServices
import com.dualsub.tv.media.VideoItem
import com.dualsub.tv.network.RemoteEntry
import com.dualsub.tv.network.RemoteLocation
import com.dualsub.tv.network.RemoteType
import com.dualsub.tv.network.smb.SmbSession
import com.dualsub.tv.network.smb.SmbShareLister
import com.dualsub.tv.ui.shell.BeiCard
import com.dualsub.tv.ui.shell.BeiPillButton
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * 网络目录浏览。SMB 与 DLNA 共用这一页 —— 差别都被 [com.dualsub.tv.network.RemoteBrowser] 吸收掉了。
 *
 * 用「路径栈」而不是字符串父路径来逐级前进/后退，因为 DLNA 的目录标识是 ObjectID，没有层级语义。
 *
 * 视觉：深墨夜景底 + 玻璃行卡；目录项前面那个小图标是**自绘**的，
 * **不再用 📁 / ▶ emoji** —— 不同电视对 emoji 的字形覆盖不一样，缺字就是一个方框
 * （本项目刚在字幕方框上踩过这个坑），自绘的几何图形在任何设备上都一样。
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
            .onFailure { failure ->
                entries = emptyList()
                // 详情只进日志：SMB 抛出的 message 里可能夹着 TransportException 之类的类全名，
                // 用户看不懂、也照做不了。屏上只给一句能照着做的。
                android.util.Log.w("DualSubTV", "读取目录失败：$currentPath", failure)
                error = "读取目录失败 —— 连接可能已断开，返回后重进即可重试"
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
            .background(BeiGlass.Night)
            .padding(
                horizontal = BeiDims.ScreenStart,
                vertical = BeiDims.ScreenVertical
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = location.displayName,
            fontSize = BeiDims.TitleSize,
            fontWeight = FontWeight.Bold,
            color = BeiGlass.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (currentPath.isBlank()) "根目录" else currentPath,
            fontSize = BeiDims.BodySize,
            color = BeiGlass.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BeiPillButton(
                label = "上一级",
                onClick = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
            )
            BeiPillButton(label = "返回", onClick = onExit)
        }

        error?.let {
            Text(
                text = "⚠ $it",
                fontSize = 14.sp,
                color = BeiGlass.Danger
            )
        }

        when {
            loading -> Text(
                text = "正在读取…",
                fontSize = 15.sp,
                color = BeiGlass.TextSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )

            entries.isEmpty() && error == null -> Text(
                text = "这个目录里没有可播放的内容。",
                fontSize = 15.sp,
                color = BeiGlass.TextSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.location + it.name }) { entry ->
                    BeiCard(
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
                        },
                        padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                EntryGlyph(isDirectory = entry.isDirectory)
                                Text(
                                    text = entry.name,
                                    fontSize = BeiDims.CardTitleSize,
                                    color = BeiGlass.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = if (entry.isDirectory) "目录" else formatSize(entry.sizeBytes),
                                fontSize = BeiDims.CaptionSize,
                                color = BeiGlass.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 目录 / 文件的小图标 —— **自绘，不用 emoji**。
 *
 * 理由与导航图标相同：电视上 emoji 的字形覆盖差异很大，缺字就是一个方框；
 * 自绘的几何图形在所有设备上都一样，而且能直接用唯一强调色。
 */
@Composable
private fun EntryGlyph(isDirectory: Boolean) {
    Canvas(modifier = Modifier.size(BeiDims.IconStatus)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.10f
        val ink = BeiGlass.Accent
        if (isDirectory) {
            // 文件夹：一个带标签口的矩形轮廓
            drawRoundRect(
                color = ink,
                topLeft = Offset(w * 0.06f, h * 0.24f),
                size = Size(w * 0.88f, h * 0.58f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.10f),
                style = Stroke(width = stroke)
            )
            drawLine(ink, Offset(w * 0.22f, h * 0.24f), Offset(w * 0.34f, h * 0.10f), stroke)
            drawLine(ink, Offset(w * 0.34f, h * 0.10f), Offset(w * 0.52f, h * 0.10f), stroke)
            drawLine(ink, Offset(w * 0.52f, h * 0.10f), Offset(w * 0.60f, h * 0.24f), stroke)
        } else {
            // 文件：播放三角
            drawPath(
                path = Path().apply {
                    moveTo(w * 0.26f, h * 0.14f)
                    lineTo(w * 0.82f, h * 0.50f)
                    lineTo(w * 0.26f, h * 0.86f)
                    close()
                },
                color = ink
            )
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
    val saveFocus = remember { FocusRequester() }
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
            .background(BeiGlass.Night)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(
                horizontal = BeiDims.ScreenStart,
                vertical = BeiDims.ScreenVertical
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "title") {
                Text(
                    text = title,
                    fontSize = BeiDims.TitleSize,
                    fontWeight = FontWeight.Bold,
                    color = BeiGlass.TextPrimary
                )
            }

            item(key = "hint") {
                Text(
                    text = "填好主机和账号后点「列出共享」，会直接读取服务器上的共享文件夹；" +
                        "若服务器禁止枚举，会自动退化为尝试常见共享名。也可以直接手工填写。",
                    fontSize = BeiDims.CaptionSize,
                    color = BeiGlass.TextSecondary
                )
            }

            item(key = "host") {
                LabeledField("主机（IP 或主机名）", hostField, focusRequester = firstField, keyboardType = KeyboardType.Decimal) { hostField = it }
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
                    BeiPillButton(
                        label = if (listing) "正在连接…" else "列出共享",
                        onClick = { if (host.isNotBlank() && !listing) loadShares() },
                        enabled = host.isNotBlank() && !listing
                    )
                    if (shares.isNotEmpty()) {
                        Text(
                            text = "点选下面任意一项即可",
                            fontSize = BeiDims.BodySize,
                            color = BeiGlass.TextSecondary
                        )
                    }
                }
            }

            if (shares.isNotEmpty()) {
                item(key = "shares") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(shares, key = { it }) { name ->
                            BeiPillButton(
                                label = if (name == share) "✓ $name" else name,
                                onClick = {
                                    shareField = name.asFieldValueAtEnd()
                                    // 选好共享后直接把焦点给「保存」，跳过下方的共享名输入框
                                    runCatching { saveFocus.requestFocus() }
                                }
                            )
                        }
                    }
                }
            }

            item(key = "share") {
                LabeledField("共享名（也可手工输入）", shareField) { shareField = it }
            }

            error?.let { message ->
                item(key = "error") {
                    Text(text = "⚠ $message", fontSize = 14.sp, color = BeiGlass.Danger)
                }
            }

            // 保存 / 取消放在最后一项：LazyColumn 会随焦点自动滚到这里
            item(key = "actions") {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BeiPillButton(
                        label = "保存",
                        onClick = { if (canSave) save() },
                        modifier = Modifier.focusRequester(saveFocus),
                        enabled = canSave
                    )
                    BeiPillButton(label = "取消", onClick = onCancel)
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
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    onValueChange: (TextFieldValue) -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = BeiDims.BodySize, color = BeiGlass.TextSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = BeiGlass.TextPrimary, fontSize = 16.sp),
            cursorBrush = SolidColor(BeiGlass.AccentBright),
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BeiGlass.Glass)
                .border(
                    width = if (focused) BeiDims.BorderFocus else BeiDims.Border,
                    color = if (focused) BeiGlass.AccentBorderStrong else BeiGlass.Border,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .onFocusChanged { focused = it.isFocused }
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

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes.toDouble() / (1L shl 20))
    else -> "%.0f KB".format(bytes.toDouble() / 1024.0)
}
