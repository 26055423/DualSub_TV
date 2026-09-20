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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.dualsub.tv.ui.shell.BeiCard
import com.dualsub.tv.ui.shell.BeiPageHeader
import com.dualsub.tv.ui.shell.BeiPillButton
import com.dualsub.tv.ui.shell.BeiSectionTitle
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass
import kotlinx.coroutines.launch

/**
 * 「本地网络」子页 —— 局域网 SMB 主机发现 + 手动配置入口。
 *
 * ## 为什么把原来的「SMB 共享」和「扫描局域网」合并成一页
 *
 * 原先网络主页上是**两张卡**：一张「SMB 共享」（点开是空的手填表单）、一张「扫描局域网」。
 * 但用户想要的顺序其实是"先看看网络里有什么，再决定填什么" —— 于是合并成这一张
 * 「本地网络」卡，**进来就自动开扫**，扫到的设备点一下即可填账号；
 * 手填表单退居成右上角的 **「＋ 手动配置」**（只有扫描不灵、或地址已知时才需要它）。
 *
 * 这样主页从 7 张卡减到 6 张，也少了一次"该点哪张卡"的判断。
 *
 * ## 交互
 *
 * - 进入即扫描（`LaunchedEffect(Unit)`），顶栏有「重新扫描」；
 * - 扫到的设备：点一下 → 回主页时带着主机名打开 SMB 表单；
 * - 已保存的本地网络位置也列在这里，点一下直接进目录浏览；
 * - 右上角「＋ 手动配置」→ 打开空的 SMB 表单。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LocalNetworkScreen(
    services: AppServices,
    savedLocations: List<RemoteLocation>,
    onPickHost: (String) -> Unit,
    onOpenSaved: (RemoteLocation) -> Unit,
    onManualAdd: () -> Unit,
    onExit: () -> Unit
) {
    // 返回键回上级（网络主页），而不是退出应用
    BackHandler(onBack = onExit)

    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var hosts by remember { mutableStateOf<List<String>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    fun scan() {
        if (scanning) return
        scanning = true
        message = null
        scope.launch {
            val found = services.smbDiscovery.scan()
            hosts = found
            scanning = false
            message = if (found.isEmpty()) {
                "没有发现开放的 445 端口。请确认 NAS 已开启 SMB、且与电视在同一网段；" +
                    "地址已知的话，点右上角「＋ 手动配置」直接填。"
            } else {
                null
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
                subtitle = "扫描局域网里开放 SMB（445 端口）的设备；点一下设备即可填账号接入"
            )
            Spacer(modifier = Modifier.weight(1f))
            BeiPillButton(label = "重新扫描", onClick = { scan() })
            Spacer(modifier = Modifier.width(10.dp))
            BeiPillButton(label = "＋ 手动配置", onClick = onManualAdd)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(BeiDims.CardGap),
            verticalArrangement = Arrangement.spacedBy(BeiDims.CardGap)
        ) {
            if (scanning) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "正在扫描局域网（约 5~10 秒）…",
                        color = BeiGlass.TextSecondary,
                        fontSize = BeiDims.BodySize
                    )
                }
            }

            message?.let { text ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = text,
                        color = BeiGlass.TextMuted,
                        fontSize = BeiDims.BodySize
                    )
                }
            }

            if (hosts.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BeiSectionTitle("发现的设备 · ${hosts.size} 台")
                }
                items(hosts, key = { "host-" + it }) { host ->
                    BeiCard(onClick = { onPickHost(host) }) {
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

            if (savedLocations.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BeiSectionTitle("已保存")
                }
                items(savedLocations, key = { "saved-" + it.id }) { location ->
                    BeiCard(onClick = { onOpenSaved(location) }) {
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
                                append(location.host)
                                location.share?.takeIf { it.isNotBlank() }?.let { append("/").append(it) }
                            },
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
}
