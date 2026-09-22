package com.dualsub.tv.ui.network

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.dualsub.tv.core.AppServices
import com.dualsub.tv.network.RemoteLocation
import com.dualsub.tv.network.dlna.DlnaDevice
import com.dualsub.tv.ui.shell.BeiCard
import com.dualsub.tv.ui.shell.BeiPageHeader
import com.dualsub.tv.ui.shell.BeiPillButton
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * 「本地网络」子页 —— 局域网里**两种发现协议一起扫**：SMB（445 端口）与 DLNA（SSDP 多播）。
 *
 * ## 为什么 DLNA 也收进这一页
 *
 * 原先 DLNA 是网络主页上的**一张独立卡**，点了就地扫描，扫到的设备作为主页上的一行。
 * 但它和「本地网络」回答的其实是**同一个问题**：「这台电视旁边还有什么？」
 * —— 一个用 TCP 探 445，一个用 SSDP 收多播，都是"局域网发现"，只是协议不同。
 * 分开摆还有个副作用：主页一行里既有"入口"又有"扫描结果"，两类东西混在一起。
 *
 * 合并之后 —— **主页只留接入方式**（本地网络 / 云盘 / NAS / WebDAV 四个入口），
 * **扫描结果全部落在本页**，扫一次就看到 SMB 设备与 DLNA 设备两行。
 *
 * ## 为什么两种扫描并行
 *
 * SMB 要并发探整个网段的 445（约 5~10 秒），DLNA 要等 SSDP 多播回音（约 3 秒）。
 * 两者都是纯网络等待，串起来用户得干等十几秒 —— 所以 `async` 并行，总耗时取慢的那个。
 *
 * ## 布局：横向行
 *
 * 「SMB 设备」「DLNA 设备」「已保存」各占一行、左右滑动，与网络主页、媒体库首页共用同一套
 * 移动语义（左右浏览内容、上下换分组）。扫描中 / 扫描无结果的提示是整行文字，不算分组。
 *
 * ## 交互
 *
 * - 进入即扫描（`LaunchedEffect(Unit)`），顶栏有「重新扫描」；
 * - SMB 设备：点一下 → 带着主机名打开 SMB 表单（回调交回网络主页处理）；
 * - DLNA 设备：点一下 → 保存成位置并直接进去浏览（它不需要账号，没有"填表单"这一步）；
 * - 已保存的本地网络位置也列在这里，**带「进入 / 编辑 / 删除」** —— 主页那一行是子弹带、
 *   只管进入，所以 SMB 的改地址 / 改密码 / 换共享就落在这里；
 * - 右上角「＋ 手动配置」→ 打开空的 SMB 表单。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LocalNetworkScreen(
    services: AppServices,
    savedLocations: List<RemoteLocation>,
    onPickHost: (String) -> Unit,
    onOpenSaved: (RemoteLocation) -> Unit,
    onEditSaved: (RemoteLocation) -> Unit,
    onDeleteSaved: (RemoteLocation) -> Unit,
    onOpenDlna: (DlnaDevice) -> Unit,
    onManualAdd: () -> Unit,
    onExit: () -> Unit
) {
    // 返回键回上级（网络主页），而不是退出应用
    BackHandler(onBack = onExit)

    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var hosts by remember { mutableStateOf<List<String>>(emptyList()) }
    var devices by remember { mutableStateOf<List<DlnaDevice>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun scan() {
        if (scanning) return
        scanning = true
        message = null
        error = null
        scope.launch {
            runCatching {
                // 两个 await 都要写出来：只 await 一个的话，另一个协程里抛的异常会被静默吞掉。
                val smb = async { services.smbDiscovery.scan() }
                val dlna = async { services.dlnaDiscovery.discover() }
                hosts = smb.await()
                devices = dlna.await()
            }.onFailure { e ->
                error = "扫描出错：${e.message ?: "未知错误"}。请检查网络连接后重试。"
            }
            scanning = false
            if (error == null) {
                message = if (hosts.isEmpty() && devices.isEmpty()) {
                    "没发现 SMB（445 端口）也没有 DLNA 设备。请确认 NAS 已开启文件共享 / DLNA、" +
                        "且与电视在同一网段；地址已知的话，点右上角「＋ 手动配置」直接填。"
                } else {
                    null
                }
            }
        }
    }

    // 进入即自动扫描 —— 这正是这张卡存在的意义：不用先点一次"扫描"
    LaunchedEffect(Unit) { scan() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BeiPageHeader(
                title = "本地网络",
                subtitle = "扫描局域网里的 SMB（445 端口）与 DLNA 设备；点一下即可接入"
            )
            Spacer(modifier = Modifier.weight(1f))
            BeiPillButton(
                label = if (scanning) "扫描中…" else "重新扫描",
                onClick = { scan() },
                enabled = !scanning
            )
            Spacer(modifier = Modifier.width(10.dp))
            BeiPillButton(label = "＋ 手动配置", onClick = onManualAdd)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp, start = 2.dp, end = 2.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (scanning) {
                item(key = "scanning") {
                    Text(
                        text = "正在扫描局域网（SMB 约 5~10 秒、DLNA 约 3 秒，两者同时进行）…",
                        color = BeiGlass.TextSecondary,
                        fontSize = BeiDims.BodySize
                    )
                }
            }

            message?.let { text ->
                item(key = "message") {
                    Text(
                        text = text,
                        color = BeiGlass.TextMuted,
                        fontSize = BeiDims.BodySize
                    )
                }
            }

            error?.let { text ->
                item(key = "error") {
                    Text(
                        text = text,
                        color = BeiGlass.Danger,
                        fontSize = BeiDims.BodySize
                    )
                }
            }

            if (hosts.isNotEmpty()) {
                item(key = "hosts") {
                    NetworkRow(title = "SMB 设备 · ${hosts.size} 台") {
                        items(hosts, key = { "host-" + it }) { host ->
                            BeiCard(
                                onClick = { onPickHost(host) },
                                modifier = Modifier.width(HostCardWidth)
                            ) {
                                Text(
                                    text = host,
                                    color = BeiGlass.TextPrimary,
                                    fontSize = BeiDims.CardTitleSize,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "SMB 已开放 · 点一下填账号",
                                    color = BeiGlass.TextSecondary,
                                    fontSize = BeiDims.CaptionSize
                                )
                            }
                        }
                    }
                }
            }

            if (devices.isNotEmpty()) {
                item(key = "dlna") {
                    NetworkRow(title = "DLNA 设备 · ${devices.size} 台") {
                        items(devices, key = { "dlna-" + it.descriptionUrl }) { device ->
                            BeiCard(
                                onClick = { onOpenDlna(device) },
                                modifier = Modifier.width(HostCardWidth)
                            ) {
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
                }
            }

            if (savedLocations.isNotEmpty()) {
                item(key = "saved") {
                    // 这里用**完整版**卡片（带「进入 / 编辑 / 删除」）—— 主页那一行是子弹带、
                    // 只管进入，SMB 的改地址 / 改密码 / 换共享就落在这里。
                    NetworkRow(title = "已保存 · ${savedLocations.size}") {
                        items(savedLocations, key = { "saved-" + it.id }) { location ->
                            LocationCard(
                                location = location,
                                onOpen = { onOpenSaved(location) },
                                onEdit = { onEditSaved(location) },
                                onLogout = null,
                                onDelete = { onDeleteSaved(location) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 横向行里卡片的固定宽度 —— 横向行里不定宽的话，每张卡会按自己内容的长短长得不一样。 */
private val HostCardWidth = 320.dp
