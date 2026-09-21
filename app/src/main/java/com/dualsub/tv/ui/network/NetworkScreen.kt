package com.dualsub.tv.ui.network

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.dualsub.tv.network.nas.NasVendor
import com.dualsub.tv.network.webdrive.AliAuthState
import com.dualsub.tv.network.webdrive.BaiduAuthState
import com.dualsub.tv.network.webdrive.QuarkAuthState
import com.dualsub.tv.ui.shell.BeiCard
import com.dualsub.tv.ui.shell.BeiIconCard
import com.dualsub.tv.ui.shell.BeiPageHeader
import com.dualsub.tv.ui.shell.SourceIcon
import com.dualsub.tv.ui.shell.SourceKind
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass
import kotlinx.coroutines.launch

/** 表单当前要处理的对象。 */
private sealed interface FormMode {
    data object Closed : FormMode

    /** 新增 SMB；[host] 来自局域网扫描结果时预填主机名。 */
    data class Create(val host: String?) : FormMode

    /** 编辑已有 SMB 条目 —— 改 IP、改密码、换共享都走这里。 */
    data class Edit(val location: RemoteLocation) : FormMode

    data object QuarkLogin : FormMode
    data object BaiduLogin : FormMode
    data object AliLogin : FormMode

    data object WebDavCreate : FormMode
    data class WebDavEdit(val location: RemoteLocation) : FormMode

    /** 按品牌接入 NAS —— 四家共用一套探测，差异全在 [NasVendor] 的数据里。 */
    data class NasCreate(val vendor: NasVendor) : FormMode
    data class NasEdit(val vendor: NasVendor, val location: RemoteLocation) : FormMode
}

/**
 * 「网络位置」：五个一级入口 —— **本地网络 / 云盘 / NAS / WebDAV / DLNA**。
 *
 * ## 为什么从「7 张来源卡平铺」改成「5 个入口」
 *
 * 原先 7 类来源平铺在一排，其中**三家网盘占了近一半位置**，而它们其实是同一类东西：
 * 都要登录、文件在互联网上、进去之后的操作完全一样。收进「云盘」一个子页之后腾出来的位置
 * 给了 NAS / WebDAV / DLNA 这些"接自己设备"的入口，五张卡一屏放得下。
 *
 * 图标也为此从 56dp 收到 44dp（见 `BeiDims.IconSourceCard`）：卡片矮一截，
 * 一行五个入口加一行「已保存」正好一屏。
 *
 * 分组：**接入方式**（5 张）→ **已保存**（所有类型混在一起，按用户要求留在主页）→
 * **发现的 DLNA 设备**（点 DLNA 卡就地扫出来的临时结果，排最后）。
 *
 * 图标底色**不是品牌色**：规范要求面板不上色、强调色只有一个，所以统一成无色玻璃底 +
 * 香槟金符号，靠**符号形状**分辨来源。横向行的容器与位置卡在 [NetworkRows]。
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
    var localNetworkOpen by remember { mutableStateOf(false) }
    var cloudOpen by remember { mutableStateOf(false) }
    var nasOpen by remember { mutableStateOf(false) }

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

    fun persistAll(updated: List<RemoteLocation>) {
        locations = updated
        services.smbRegistry.registerAll(updated)
        scope.launch { services.settings.saveRemoteLocations(updated) }
    }

    fun remove(location: RemoteLocation) = persistAll(locations - location)

    /** DLNA 设备没有账号可填 —— 扫到就直接存成一条位置并进去。 */
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
        browsing = location
    }

    // ---------------------------------------------------------------- 子页分派

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

    // ---- 「本地网络」：进去就自动扫 SMB + DLNA，扫描结果全部落在那一页
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
            // SMB 的编辑 / 删除只在这一页 —— 主页那一行是子弹带，卡里没有按钮。
            onEditSaved = { location ->
                localNetworkOpen = false
                form = FormMode.Edit(location)
            },
            onDeleteSaved = { location ->
                services.smbPool.invalidate(location.host)
                remove(location)
            },
            onOpenDlna = { device ->
                localNetworkOpen = false
                openDlna(device)
            },
            onManualAdd = {
                localNetworkOpen = false
                form = FormMode.Create(null)
            },
            onExit = { localNetworkOpen = false }
        )
        return
    }

    // ---- 「云盘」：夸克 / 百度 / 阿里
    if (cloudOpen) {
        CloudDriveScreen(
            quarkLoggedIn = quarkState is QuarkAuthState.LoggedIn,
            baiduLoggedIn = baiduState is BaiduAuthState.LoggedIn,
            aliLoggedIn = aliState is AliAuthState.LoggedIn,
            savedLocations = locations.filter { it.type in CLOUD_TYPES },
            onCloud = { type ->
                val (id, name, host) = cloudSpec(type)
                val loggedIn = when (type) {
                    RemoteType.QUARK -> quarkState is QuarkAuthState.LoggedIn
                    RemoteType.BAIDU -> baiduState is BaiduAuthState.LoggedIn
                    else -> aliState is AliAuthState.LoggedIn
                }
                cloudOpen = false
                if (loggedIn) {
                    browsing = locations.firstOrNull { it.type == type }
                        ?: RemoteLocation(id = id, type = type, displayName = name, host = host)
                } else {
                    form = when (type) {
                        RemoteType.QUARK -> FormMode.QuarkLogin
                        RemoteType.BAIDU -> FormMode.BaiduLogin
                        else -> FormMode.AliLogin
                    }
                }
            },
            onOpenSaved = { cloudOpen = false; browsing = it },
            onLogout = { location ->
                when (location.type) {
                    RemoteType.QUARK -> services.quarkAuth.logout()
                    RemoteType.BAIDU -> services.baiduAuth.logout()
                    else -> services.aliAuth.logout()
                }
                remove(location)
            },
            onDelete = { remove(it) },
            onExit = { cloudOpen = false }
        )
        return
    }

    // ---- 「NAS」：飞牛 / 群晖 / 威联通 / 绿联
    if (nasOpen) {
        NasVendorScreen(
            savedLocations = locations.filter { it.type == RemoteType.NAS },
            onVendor = { vendor ->
                nasOpen = false
                form = FormMode.NasCreate(vendor)
            },
            onOpenSaved = { location ->
                nasOpen = false
                browsing = location
            },
            onEditSaved = { location ->
                nasOpen = false
                vendorOf(location)?.let { form = FormMode.NasEdit(it, location) }
            },
            onDelete = { remove(it) },
            onExit = { nasOpen = false }
        )
        return
    }

    // 表单打开时，返回键先把表单关掉（回到卡片行），而不是退出这一页
    BackHandler {
        if (form != FormMode.Closed) form = FormMode.Closed
    }

    // ---------------------------------------------------------------- 表单分派

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
                    val updated = upsertAccount(RemoteType.QUARK, locations, token = cookie)
                    persistAll(updated)
                    form = FormMode.Closed
                    browsing = updated.firstOrNull { it.type == RemoteType.QUARK }
                },
                onCancel = { form = FormMode.Closed }
            )
            return
        }

        is FormMode.BaiduLogin -> {
            BaiduLoginScreen(
                baiduAuth = services.baiduAuth,
                onSuccess = { access, refresh ->
                    val updated = upsertAccount(RemoteType.BAIDU, locations, token = access, refresh = refresh)
                    persistAll(updated)
                    form = FormMode.Closed
                    browsing = updated.firstOrNull { it.type == RemoteType.BAIDU }
                },
                onCancel = { form = FormMode.Closed }
            )
            return
        }

        is FormMode.AliLogin -> {
            AliLoginScreen(
                aliAuth = services.aliAuth,
                onSuccess = { access, refresh, driveId ->
                    val updated = upsertAccount(
                        RemoteType.ALI, locations, token = access, refresh = refresh, driveId = driveId
                    )
                    persistAll(updated)
                    form = FormMode.Closed
                    browsing = updated.firstOrNull { it.type == RemoteType.ALI }
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

        is FormMode.NasCreate -> {
            NasServerForm(
                vendor = mode.vendor,
                onCancel = { form = FormMode.Closed },
                onSave = { location ->
                    persistAll(locations.filterNot { it.id == location.id } + location)
                    form = FormMode.Closed
                    browsing = location
                }
            )
            return
        }

        is FormMode.NasEdit -> {
            NasServerForm(
                vendor = mode.vendor,
                initial = mode.location,
                onCancel = { form = FormMode.Closed },
                onSave = { location ->
                    persistAll(locations.map { if (it.id == mode.location.id) location else it })
                    form = FormMode.Closed
                }
            )
            return
        }
    }

    // ---------------------------------------------------------------- 主页

    val smbCount = locations.count { it.type == RemoteType.SMB }
    val cloudCount = locations.count { it.type in CLOUD_TYPES }
    val nasCount = locations.count { it.type == RemoteType.NAS }
    val webDavCount = locations.count { it.type == RemoteType.WEBDAV }

    Column(modifier = Modifier.fillMaxSize()) {
        BeiPageHeader(
            title = "网络位置",
            subtitle = "本地网络 · 云盘 · NAS · WebDAV"
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp, start = 2.dp, end = 2.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "entries") {
                NetworkRow(title = "接入方式") {
                    item {
                        BeiIconCard(
                            title = "本地网络",
                            subtitle = if (smbCount > 0) "已添加 $smbCount 个" else "扫描并接入 NAS 共享",
                            onClick = { localNetworkOpen = true },
                            modifier = Modifier.width(SourceCardWidth)
                        ) { SourceIcon(kind = SourceKind.Smb) }
                    }
                    item {
                        BeiIconCard(
                            title = "云盘",
                            subtitle = if (cloudCount > 0) "已添加 $cloudCount 个" else "夸克 / 百度 / 阿里云盘",
                            onClick = { cloudOpen = true },
                            modifier = Modifier.width(SourceCardWidth)
                        ) { SourceIcon(kind = SourceKind.CloudDrive) }
                    }
                    item {
                        BeiIconCard(
                            title = "NAS",
                            subtitle = if (nasCount > 0) "已添加 $nasCount 个" else "飞牛 / 群晖 / 威联通 / 绿联",
                            onClick = { nasOpen = true },
                            modifier = Modifier.width(SourceCardWidth)
                        ) { SourceIcon(kind = SourceKind.Nas) }
                    }
                    item {
                        BeiIconCard(
                            title = "WebDAV",
                            subtitle = if (webDavCount > 0) "已添加 $webDavCount 个" else "AList / Nextcloud 等",
                            onClick = { form = FormMode.WebDavCreate },
                            modifier = Modifier.width(SourceCardWidth)
                        ) { SourceIcon(kind = SourceKind.WebDav) }
                    }
                }
            }

            if (locations.isNotEmpty()) {
                // 「已保存」用的是**子弹带小卡**（只管进入）—— 主页不是管理位置的地方，
                // 编辑 / 删除 / 退出登录都在各自的子页里做（见 [SavedLocationCard] 的说明）。
                item(key = "saved") {
                    NetworkRow(title = "已保存 · ${locations.size}", gap = SavedRowGap) {
                        items(locations, key = { "saved-" + it.id }) { location ->
                            SavedLocationCard(
                                location = location,
                                onOpen = { browsing = location }
                            )
                        }
                    }
                }
            }

            if (locations.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "还没有添加任何网络位置。片子在 NAS 上的话，点「本地网络」或「NAS」——" +
                            "前者扫描同网段的共享，后者按品牌接入并自动试出端口。",
                        color = BeiGlass.TextMuted,
                        fontSize = BeiDims.BodySize,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

/** 云盘三家 —— 子页筛选、计数、登录分派都用它，免得散成三处 `||`。 */
private val CLOUD_TYPES = setOf(RemoteType.QUARK, RemoteType.BAIDU, RemoteType.ALI)

/** 云盘账号位置的三要素：`RemoteLocation` 的 id / 显示名 / host。 */
private fun cloudSpec(type: RemoteType): Triple<String, String, String> = when (type) {
    RemoteType.QUARK -> Triple("quark:account", "夸克网盘", "drive.quark.cn")
    RemoteType.BAIDU -> Triple("baidu:account", "百度网盘", "pan.baidu.com")
    else -> Triple("ali:account", "阿里云盘", "api.aliyundrive.com")
}

/**
 * 云盘登录成功后把账号写回位置列表：已有同类型条目就更新 token，没有就新建一条。
 *
 * 三家共用这一个（阿里额外带 `driveId`）—— 早先三处各写一遍，加字段时极易漏掉其中一处。
 */
private fun upsertAccount(
    type: RemoteType,
    locations: List<RemoteLocation>,
    token: String,
    refresh: String? = null,
    driveId: String? = null
): List<RemoteLocation> {
    val (id, name, host) = cloudSpec(type)
    val existing = locations.firstOrNull { it.type == type }
    val location = (existing ?: RemoteLocation(id = id, type = type, displayName = name, host = host))
        .copy(
            token = token,
            refreshToken = refresh ?: existing?.refreshToken,
            share = driveId ?: existing?.share
        )
    return locations.filterNot { it.id == location.id } + location
}
