package com.dualsub.tv.ui.network

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
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
 * 「网络位置」：管理 SMB / DLNA / 网盘来源，并进入目录浏览。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NetworkScreen(
    services: AppServices,
    onOpenVideo: (VideoItem) -> Unit,
    onOpenAiSettings: () -> Unit,
    onExit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var locations by remember { mutableStateOf<List<RemoteLocation>>(emptyList()) }
    var browsing by remember { mutableStateOf<RemoteLocation?>(null) }
    var form by remember { mutableStateOf<FormMode>(FormMode.Closed) }
    var busy by remember { mutableStateOf(false) }
    var discoveredDlna by remember { mutableStateOf<List<DlnaDevice>>(emptyList()) }
    var discoveredSmb by remember { mutableStateOf<List<String>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

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

    BackHandler {
        if (form != FormMode.Closed) form = FormMode.Closed else onExit()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E16))
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "网络位置", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "支持 SMB 共享（Windows / NAS）、DLNA 媒体服务器、夸克网盘、百度网盘。",
            fontSize = 13.sp,
            color = Color(0xFF90A4AE)
        )

        // LAN 按钮行
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val prefix = services.smbDiscovery.localSubnetPrefix()
                    form = FormMode.Create(prefix.ifEmpty { null })
                }
            ) { Text("添加 SMB 服务器", fontSize = 14.sp) }
            Button(onClick = { form = FormMode.WebDavCreate }) {
                Text("添加 WebDAV", fontSize = 14.sp)
            }
            Button(
                onClick = {
                    busy = true
                    discoveredSmb = emptyList()
                    message = "正在扫描局域网（约 5~10 秒）…"
                    scope.launch {
                        val hosts = services.smbDiscovery.scan()
                        discoveredSmb = hosts
                        busy = false
                        message = if (hosts.isEmpty()) {
                            "没有发现开放的 445 端口。请确认 NAS 已开启 SMB，且与电视在同一网段。"
                        } else {
                            "发现 ${hosts.size} 台设备，选中后填入账号即可。"
                        }
                    }
                },
                enabled = !busy
            ) { Text("扫描局域网 SMB", fontSize = 14.sp) }

            Button(
                onClick = {
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
                },
                enabled = !busy
            ) { Text("扫描 DLNA 设备", fontSize = 14.sp) }

            Button(onClick = onOpenAiSettings) { Text("AI 字幕设置", fontSize = 14.sp) }
            Button(onClick = onExit) { Text("返回", fontSize = 14.sp) }
        }

        // 网盘按钮行
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val quarkLoggedIn = quarkState is QuarkAuthState.LoggedIn
            Button(
                onClick = {
                    if (quarkLoggedIn) {
                        browsing = locations.firstOrNull { it.type == RemoteType.QUARK }
                            ?: RemoteLocation(
                                id = "quark:account", type = RemoteType.QUARK,
                                displayName = "夸克网盘", host = "drive.quark.cn"
                            )
                    } else {
                        form = FormMode.QuarkLogin
                    }
                }
            ) {
                Text(
                    text = if (quarkLoggedIn) "夸克网盘（已登录）" else "登录夸克网盘",
                    fontSize = 14.sp
                )
            }

            val baiduLoggedIn = baiduState is BaiduAuthState.LoggedIn
            Button(
                onClick = {
                    if (baiduLoggedIn) {
                        browsing = locations.firstOrNull { it.type == RemoteType.BAIDU }
                            ?: RemoteLocation(
                                id = "baidu:account", type = RemoteType.BAIDU,
                                displayName = "百度网盘", host = "pan.baidu.com"
                            )
                    } else {
                        form = FormMode.BaiduLogin
                    }
                }
            ) {
                Text(
                    text = if (baiduLoggedIn) "百度网盘（已登录）" else "登录百度网盘",
                    fontSize = 14.sp
                )
            }

            // 退出登录按钮（仅已登录时显示）
            if (quarkLoggedIn) {
                Button(
                    onClick = {
                        services.quarkAuth.logout()
                        persistAll(locations.filterNot { it.type == RemoteType.QUARK })
                    }
                ) { Text("退出夸克", fontSize = 14.sp) }
            }
            if (baiduLoggedIn) {
                Button(
                    onClick = {
                        services.baiduAuth.logout()
                        persistAll(locations.filterNot { it.type == RemoteType.BAIDU })
                    }
                ) { Text("退出百度", fontSize = 14.sp) }
            }

            val aliLoggedIn = aliState is AliAuthState.LoggedIn
            Button(
                onClick = {
                    if (aliLoggedIn) {
                        browsing = locations.firstOrNull { it.type == RemoteType.ALI }
                            ?: RemoteLocation(
                                id = "ali:account", type = RemoteType.ALI,
                                displayName = "阿里云盘", host = "api.aliyundrive.com"
                            )
                    } else {
                        form = FormMode.AliLogin
                    }
                }
            ) {
                Text(
                    text = if (aliLoggedIn) "阿里云盘（已登录）" else "登录阿里云盘",
                    fontSize = 14.sp
                )
            }
            if (aliLoggedIn) {
                Button(
                    onClick = {
                        services.aliAuth.logout()
                        persistAll(locations.filterNot { it.type == RemoteType.ALI })
                    }
                ) { Text("退出阿里", fontSize = 14.sp) }
            }
        }

        message?.let {
            Text(text = it, fontSize = 14.sp, color = Color(0xFFFFD54F))
        }

        if (locations.isEmpty() && discoveredSmb.isEmpty() && discoveredDlna.isEmpty()) {
            Text(
                text = "还没有添加任何网络位置。",
                fontSize = 14.sp,
                color = Color(0xFFB0BEC5),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(discoveredSmb, key = { "smb-found-" + it }) { host ->
                Card(onClick = { form = FormMode.Create(host) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = host, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "发现 SMB 服务", fontSize = 12.sp, color = Color(0xFF90A4AE))
                        }
                        Text(text = "选择以配置 →", fontSize = 13.sp, color = Color(0xFFFFD54F))
                    }
                }
            }

            items(discoveredDlna, key = { "dlna-" + it.descriptionUrl }) { device ->
                Card(onClick = { openDlna(device) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = device.friendlyName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(text = "DLNA · ${device.host}", fontSize = 12.sp, color = Color(0xFF90A4AE))
                        }
                        Text(text = "选择以保存并进入 →", fontSize = 13.sp, color = Color(0xFFFFD54F))
                    }
                }
            }

            items(locations, key = { it.id }) { location ->
                // 网盘账号在按钮行已有入口，列表里跳过避免重复
                if (location.type == RemoteType.QUARK
                    || location.type == RemoteType.BAIDU
                    || location.type == RemoteType.ALI) return@items

                Card(onClick = { browsing = location }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = location.displayName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(location.type.displayName)
                                    append(" · ").append(location.host)
                                    location.share?.takeIf { it.isNotBlank() }?.let { append("/").append(it) }
                                    append(" · ")
                                    append(if (location.isAnonymous) "匿名" else location.username)
                                },
                                fontSize = 12.sp,
                                color = Color(0xFF90A4AE),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { browsing = location }) { Text("进入", fontSize = 14.sp) }
                            Button(onClick = {
                                form = if (location.type == RemoteType.WEBDAV) {
                                    FormMode.WebDavEdit(location)
                                } else {
                                    FormMode.Edit(location)
                                }
                            }) { Text("编辑", fontSize = 14.sp) }
                            Button(onClick = {
                                services.smbPool.invalidate(location.host)
                                persistAll(locations.filterNot { it.id == location.id })
                            }) { Text("删除", fontSize = 14.sp) }
                        }
                    }
                }
            }
        }
    }
}

