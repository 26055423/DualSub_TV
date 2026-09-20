package com.dualsub.tv.ui.network

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.core.AppServices
import com.dualsub.tv.media.VideoItem
import com.dualsub.tv.network.RemoteLocation
import com.dualsub.tv.network.RemoteType
import com.dualsub.tv.network.dlna.DlnaDevice
import com.dualsub.tv.network.webdrive.AliAuthState
import com.dualsub.tv.network.webdrive.BaiduAuthState
import com.dualsub.tv.network.webdrive.QuarkAuthState
import com.dualsub.tv.ui.shell.BeiCard
import com.dualsub.tv.ui.shell.BeiIconCard
import com.dualsub.tv.ui.shell.BeiPageHeader
import com.dualsub.tv.ui.shell.BeiPillButton
import com.dualsub.tv.ui.shell.BeiSectionTitle
import com.dualsub.tv.ui.shell.SourceIcon
import com.dualsub.tv.ui.shell.SourceKind
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass
import kotlinx.coroutines.launch

/** 表单当前要处理的对象。 */
private sealed interface FormMode {
    data object Closed : FormMode

    /** 新增；[host] 来自局域网扫描结果时预填主机名。 */
    data class Create(val host: String?) : FormMode

    /** 编辑已有条目 —— 改 IP、改密码、换共享都走这里。 */
    data class Edit(val location: RemoteLocation) : FormMode

    /** 夸克网盘扫码登录。 */
    data object QuarkLogin : FormMode

    /** 百度网盘 Device Code 登录。 */
    data object BaiduLogin : FormMode

    /** 添加 WebDAV 服务器。 */
    data object WebDavCreate : FormMode

    /** 编辑已有 WebDAV 服务器。 */
    data class WebDavEdit(val location: RemoteLocation) : FormMode

    /** 阿里云盘扫码登录。 */
    data object AliLogin : FormMode
}

/**
 * 「网络位置」：管理 SMB / WebDAV / DLNA / 网盘来源，并进入目录浏览。
 *
 * 版面是**大图标卡网格**："能做什么"平铺在用户面前，每张卡一个图标 + 名称 + 一行状态。
 * 原有能力一个不少 —— 添加、扫描、扫码登录、进入、编辑、退出登录、删除都在卡片上。
 *
 * 图标底色**不是品牌色**（早先是 SMB 蓝 / WebDAV 绿 / DLNA 紫 各一套渐变）：规范要求
 * 面板不上色、强调色只有一个，所以统一成无色玻璃底 + 香槟金符号，靠**符号形状**分辨来源。
 *
 * ## 为什么是 6 张卡、3 列
 *
 * 早先是 7 张（「SMB 共享」与「扫描局域网」分立）。合并成一张 **「本地网络」**
 * （见 [LocalNetworkScreen]）之后正好 6 张 —— 3 列 × 2 行，比 4 列那种"最后一行只两张"均衡。
 * 也因此主页上不再需要「扫描结果」分区：扫描这件事整体挪进了子页。
 *
 * 本页**不画标题栏，也不放「AI 字幕设置」入口** —— 前者由外壳提供，后者已收进「设置」里
 * （见 [com.dualsub.tv.ui.shell.ShellTab]）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NetworkScreen(
    services: AppServices,
    onOpenVideo: (VideoItem) -> Unit
) {
    val scope = rememberCoroutineScope()
    var locations by remember { mutableStateOf<List<RemoteLocation>>(emptyList()) }
    var browsing by remember { mutableStateOf<RemoteLocation?>(null) }
    var form by remember { mutableStateOf<FormMode>(FormMode.Closed) }
    var busy by remember { mutableStateOf(false) }
    var discoveredDlna by remember { mutableStateOf<List<DlnaDevice>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    /** 「本地网络」子页是否打开（子页里进去就自动扫描 SMB 主机）。 */
    var localNetworkOpen by remember { mutableStateOf(false) }

    val quarkState by services.quarkAuth.state.collectAsState()
    val baiduState by services.baiduAuth.state.collectAsState()
    val aliState by services.aliAuth.state.collectAsState()

    LaunchedEffect(Unit) {
        services.settings.remoteLocations().collect { list ->
            locations = list
            services.smbRegistry.registerAll(list)
            // 从持久化的 RemoteLocation 恢复网盘登录态
            list.firstOrNull { it.type == RemoteType.QUARK && !it.token.isNullOrBlank() }?.let {
                if (!services.quarkAuth.isLoggedIn) services.quarkAuth.restoreFromCookie(it.token!!)
            }
            list.firstOrNull {
                it.type == RemoteType.BAIDU && !it.token.isNullOrBlank()
            }?.let {
                if (!services.baiduAuth.isLoggedIn) {
                    services.baiduAuth.restoreFromToken(it.token!!, it.refreshToken.orEmpty())
                }
            }
            list.firstOrNull {
                it.type == RemoteType.ALI && !it.token.isNullOrBlank()
            }?.let {
                if (!services.aliAuth.isLoggedIn) {
                    services.aliAuth.restoreFromToken(
                        it.token!!,
                        it.refreshToken.orEmpty(),
                        it.share.orEmpty()
                    )
                }
            }
        }
    }

    val current = browsing
    if (current != null) {
        RemoteBrowseScreen(
            services = services,
            location = current,
            onOpenVideo = onOpenVideo,
            onExit = { browsing = null }
        )
        return
    }

    // ---- 「本地网络」子页：进去就自动扫描，扫到的设备点一下回填主机名
    if (localNetworkOpen) {
        LocalNetworkScreen(
            services = services,
            savedLocations = locations.filter { it.type == RemoteType.SMB },
            onPickHost = { host ->
                localNetworkOpen = false
                form = FormMode.Create(host)
            },
            onOpenSaved = { location ->
                localNetworkOpen = false
                browsing = location
            },
            onManualAdd = {
                localNetworkOpen = false
                form = FormMode.Create(null)
            },
            onExit = { localNetworkOpen = false }
        )
        return
    }

    // 表单打开时，返回键先把表单关掉（回到卡片网格），而不是退出这一页
    BackHandler {
        if (form != FormMode.Closed) form = FormMode.Closed
    }

    fun persistAll(updated: List<RemoteLocation>) {
        locations = updated
        services.smbRegistry.registerAll(updated)
        scope.launch { services.settings.saveRemoteLocations(updated) }
    }

    fun openDlna(device: DlnaDevice) {
        val location = RemoteLocation(
            id = "dlna:${device.descriptionUrl}",
            type = RemoteType.DLNA,
            displayName = device.friendlyName,
            host = device.host,
            descriptionUrl = device.descriptionUrl,
            controlUrl = device.controlUrl
        )
        persistAll(locations.filterNot { it.id == location.id } + location)
        discoveredDlna = discoveredDlna.filterNot { it.descriptionUrl == device.descriptionUrl }
        browsing = location
    }

    fun scanDlna() {
        busy = true
        discoveredDlna = emptyList()
        message = "正在搜索局域网内的 DLNA 设备…"
        scope.launch {
            val found = services.dlnaDiscovery.discover()
            discoveredDlna = found
            busy = false
            message = if (found.isEmpty()) {
                "没有发现 DLNA 设备（请确认 NAS 已开启 DLNA / 媒体服务器）"
            } else {
                null
            }
        }
    }

    /** 网盘卡片的统一点击行为：已登录就直接浏览，没登录就走登录流程。 */
    fun openCloud(type: RemoteType, id: String, name: String, host: String, login: FormMode) {
        val loggedIn = when (type) {
            RemoteType.QUARK -> quarkState is QuarkAuthState.LoggedIn
            RemoteType.BAIDU -> baiduState is BaiduAuthState.LoggedIn
            RemoteType.ALI -> aliState is AliAuthState.LoggedIn
            else -> false
        }
        if (loggedIn) {
            browsing = locations.firstOrNull { it.type == type }
                ?: RemoteLocation(id = id, type = type, displayName = name, host = host)
        } else {
            form = login
        }
    }

    val mode = form
    when (mode) {
        FormMode.Closed -> Unit

        is FormMode.Create -> {
            SmbServerForm(
                services = services,
                title = "添加 SMB 服务器",
                initial = mode.host?.let {
                    RemoteLocation(id = "", type = RemoteType.SMB, displayName = it, host = it)
                },
                onCancel = { form = FormMode.Closed },
                onSave = { location ->
                    persistAll(locations.filterNot { it.id == location.id } + location)
                    form = FormMode.Closed
                    browsing = location
                }
            )
            return
        }

        is FormMode.Edit -> {
            SmbServerForm(
                services = services,
                title = "编辑 SMB 服务器",
                initial = mode.location,
                onCancel = { form = FormMode.Closed },
                onSave = { location ->
                    persistAll(locations.map { if (it.id == mode.location.id) location else it })
                    services.smbPool.invalidate(mode.location.host)
                    form = FormMode.Closed
                }
            )
            return
        }

        is FormMode.QuarkLogin -> {
            QuarkLoginScreen(
                quarkAuth = services.quarkAuth,
                onSuccess = { cookie ->
                    val existing = locations.firstOrNull { it.type == RemoteType.QUARK }
                    val location = (existing ?: RemoteLocation(
                        id = "quark:account",
                        type = RemoteType.QUARK,
                        displayName = "夸克网盘",
                        host = "drive.quark.cn"
                    )).copy(token = cookie)
                    persistAll(locations.filterNot { it.id == location.id } + location)
                    form = FormMode.Closed
                    browsing = location
                },
                onCancel = { form = FormMode.Closed }
            )
            return
        }

        is FormMode.BaiduLogin -> {
            BaiduLoginScreen(
                baiduAuth = services.baiduAuth,
                onSuccess = { access, refresh ->
                    val existing = locations.firstOrNull { it.type == RemoteType.BAIDU }
                    val location = (existing ?: RemoteLocation(
                        id = "baidu:account",
                        type = RemoteType.BAIDU,
                        displayName = "百度网盘",
                        host = "pan.baidu.com"
                    )).copy(token = access, refreshToken = refresh)
                    persistAll(locations.filterNot { it.id == location.id } + location)
                    form = FormMode.Closed
                    browsing = location
                },
                onCancel = { form = FormMode.Closed }
            )
            return
        }

        is FormMode.WebDavCreate -> {
            WebDavServerForm(
                onCancel = { form = FormMode.Closed },
                onSave = { location ->
                    persistAll(locations.filterNot { it.id == location.id } + location)
                    form = FormMode.Closed
                    browsing = location
                }
            )
            return
        }

        is FormMode.WebDavEdit -> {
            WebDavServerForm(
                initial = mode.location,
                onCancel = { form = FormMode.Closed },
                onSave = { location ->
                    persistAll(locations.map { if (it.id == mode.location.id) location else it })
                    form = FormMode.Closed
                }
            )
            return
        }

        is FormMode.AliLogin -> {
            AliLoginScreen(
                aliAuth = services.aliAuth,
                onSuccess = { access, refresh, driveId ->
                    val existing = locations.firstOrNull { it.type == RemoteType.ALI }
                    val location = (existing ?: RemoteLocation(
                        id = "ali:account",
                        type = RemoteType.ALI,
                        displayName = "阿里云盘",
                        host = "api.aliyundrive.com"
                    )).copy(token = access, refreshToken = refresh, share = driveId)
                    persistAll(locations.filterNot { it.id == location.id } + location)
                    form = FormMode.Closed
                    browsing = location
                },
                onCancel = { form = FormMode.Closed }
            )
            return
        }
    }

    val quarkLoggedIn = quarkState is QuarkAuthState.LoggedIn
    val baiduLoggedIn = baiduState is BaiduAuthState.LoggedIn
    val aliLoggedIn = aliState is AliAuthState.LoggedIn

    val smbCount = locations.count { it.type == RemoteType.SMB }
    val webDavCount = locations.count { it.type == RemoteType.WEBDAV }
    val dlnaCount = locations.count { it.type == RemoteType.DLNA }

    Column(modifier = Modifier.fillMaxSize()) {
        BeiPageHeader(
            title = "网络位置",
            subtitle = "本地网络（SMB）· WebDAV · DLNA 媒体服务器 · 夸克 / 百度 / 阿里云盘"
        )

        message?.let { text ->
            Text(
                text = text,
                color = BeiGlass.TextSecondary,
                fontSize = BeiDims.BodySize,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // 3 列：六张来源卡正好两行排满。
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(BeiDims.CardGap),
            verticalArrangement = Arrangement.spacedBy(BeiDims.CardGap)
        ) {
            item {
                BeiIconCard(
                    title = "本地网络",
                    subtitle = if (smbCount > 0) "已添加 $smbCount 个" else "扫描并接入 NAS 共享",
                    onClick = { localNetworkOpen = true }
                ) { SourceIcon(kind = SourceKind.Smb) }
            }
            item {
                BeiIconCard(
                    title = "WebDAV",
                    subtitle = if (webDavCount > 0) "已添加 $webDavCount 个" else "坚果云 / 群晖等",
                    onClick = { form = FormMode.WebDavCreate }
                ) { SourceIcon(kind = SourceKind.WebDav) }
            }
            item {
                BeiIconCard(
                    title = "DLNA",
                    subtitle = when {
                        busy -> "正在搜索…"
                        dlnaCount > 0 -> "已保存 $dlnaCount 个设备"
                        else -> "发现局域网媒体服务器"
                    },
                    onClick = { scanDlna() }
                ) { SourceIcon(kind = SourceKind.Dlna) }
            }
            item {
                BeiIconCard(
                    title = "夸克网盘",
                    subtitle = if (quarkLoggedIn) "已登录" else "扫码登录",
                    onClick = {
                        openCloud(
                            type = RemoteType.QUARK,
                            id = "quark:account",
                            name = "夸克网盘",
                            host = "drive.quark.cn",
                            login = FormMode.QuarkLogin
                        )
                    }
                ) { SourceIcon(kind = SourceKind.Quark) }
            }
            item {
                BeiIconCard(
                    title = "百度网盘",
                    subtitle = if (baiduLoggedIn) "已登录" else "设备码登录",
                    onClick = {
                        openCloud(
                            type = RemoteType.BAIDU,
                            id = "baidu:account",
                            name = "百度网盘",
                            host = "pan.baidu.com",
                            login = FormMode.BaiduLogin
                        )
                    }
                ) { SourceIcon(kind = SourceKind.Baidu) }
            }
            item {
                BeiIconCard(
                    title = "阿里云盘",
                    subtitle = if (aliLoggedIn) "已登录" else "扫码登录",
                    onClick = {
                        openCloud(
                            type = RemoteType.ALI,
                            id = "ali:account",
                            name = "阿里云盘",
                            host = "api.aliyundrive.com",
                            login = FormMode.AliLogin
                        )
                    }
                ) { SourceIcon(kind = SourceKind.Ali) }
            }

            // DLNA 的扫描结果仍留在主页（它是"点一下卡就地扫出来"的轻交互，不值得为它开子页）
            if (discoveredDlna.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { BeiSectionTitle("发现的 DLNA 设备") }

                items(discoveredDlna, key = { "dlna-" + it.descriptionUrl }) { device ->
                    BeiCard(onClick = { openDlna(device) }) {
                        Text(
                            text = device.friendlyName,
                            color = BeiGlass.TextPrimary,
                            fontSize = BeiDims.CardTitleSize,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "DLNA · ${device.host} · 点一下保存并进入",
                            color = BeiGlass.TextSecondary,
                            fontSize = BeiDims.CaptionSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (locations.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { BeiSectionTitle("已保存") }

                items(locations, key = { it.id }) { location ->
                    LocationCard(
                        location = location,
                        onOpen = { browsing = location },
                        onEdit = when (location.type) {
                            RemoteType.SMB -> { { form = FormMode.Edit(location) } }
                            RemoteType.WEBDAV -> { { form = FormMode.WebDavEdit(location) } }
                            else -> null
                        },
                        onLogout = when (location.type) {
                            RemoteType.QUARK -> {
                                {
                                    services.quarkAuth.logout()
                                    persistAll(locations.filterNot { it.id == location.id })
                                }
                            }

                            RemoteType.BAIDU -> {
                                {
                                    services.baiduAuth.logout()
                                    persistAll(locations.filterNot { it.id == location.id })
                                }
                            }

                            RemoteType.ALI -> {
                                {
                                    services.aliAuth.logout()
                                    persistAll(locations.filterNot { it.id == location.id })
                                }
                            }

                            else -> null
                        },
                        onDelete = {
                            services.smbPool.invalidate(location.host)
                            persistAll(locations.filterNot { it.id == location.id })
                        }
                    )
                }
            }

            if (locations.isEmpty() && discoveredDlna.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "还没有添加任何网络位置。片子在 NAS 上的话，点「本地网络」——" +
                            "进去会自动扫描同一网段里的共享，扫到就能填账号接入。",
                        color = BeiGlass.TextMuted,
                        fontSize = BeiDims.BodySize,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 一条已保存的位置。
 *
 * 卡片本身可点（＝进入浏览），卡内再放「进入 / 编辑 / 退出登录 / 删除」。
 * 网盘条目没有「编辑」（没东西可编辑）但有「退出登录」；SMB / WebDAV / DLNA 反过来。
 */
@Composable
private fun LocationCard(
    location: RemoteLocation,
    onOpen: () -> Unit,
    onEdit: (() -> Unit)?,
    onLogout: (() -> Unit)?,
    onDelete: () -> Unit
) {
    BeiCard(onClick = onOpen) {
        Text(
            text = location.displayName,
            color = BeiGlass.TextPrimary,
            fontSize = BeiDims.CardTitleSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = buildString {
                append(location.type.displayName)
                append(" · ").append(location.host)
                location.share?.takeIf { it.isNotBlank() }?.let { append("/").append(it) }
                if (location.type.isSmbLike) {
                    append(" · ")
                    append(if (location.isAnonymous) "匿名" else location.username)
                }
            },
            color = BeiGlass.TextSecondary,
            fontSize = BeiDims.CaptionSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BeiPillButton(label = "进入", onClick = onOpen)
            onEdit?.let { BeiPillButton(label = "编辑", onClick = it) }
            onLogout?.let { BeiPillButton(label = "退出登录", onClick = it) }
            BeiPillButton(label = "删除", onClick = onDelete)
        }
    }
}

/** 只有 SMB 才显示「匿名 / 用户名」—— 别的类型没有这回事。 */
private val RemoteType.isSmbLike: Boolean get() = this == RemoteType.SMB
