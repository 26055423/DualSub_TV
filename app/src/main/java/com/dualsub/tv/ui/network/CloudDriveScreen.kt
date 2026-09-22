package com.dualsub.tv.ui.network

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.dualsub.tv.network.RemoteLocation
import com.dualsub.tv.network.RemoteType
import com.dualsub.tv.ui.shell.BeiIconCard
import com.dualsub.tv.ui.shell.BeiPageHeader
import com.dualsub.tv.ui.shell.SourceIcon
import com.dualsub.tv.ui.shell.SourceKind
import com.dualsub.tv.ui.theme.BeiGlass

/**
 * 「云盘」子页：夸克 / 百度 / 阿里云盘。
 *
 * ## 为什么要分组
 *
 * 网络主页原先把 7 类来源平铺在一排，其中三家网盘占了近一半位置，而它们**是同一类东西**：
 * 都要扫码/设备码登录、文件在互联网上、进去之后的操作也一模一样（浏览 → 播放）。
 * 收进一个子页之后主页腾出来的位置给了 NAS / WebDAV / DLNA 这些"接自己设备"的入口。
 *
 * ## 登录态在页面外
 *
 * 本页**不持有** `*AuthManager`，只接收三个布尔值和几个回调 —— 登录流程要跳整屏的
 * `QuarkLoginScreen` 等，那属于 [NetworkScreen] 的表单分派，子页自己开一套会打架。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CloudDriveScreen(
    quarkLoggedIn: Boolean,
    baiduLoggedIn: Boolean,
    aliLoggedIn: Boolean,
    savedLocations: List<RemoteLocation>,
    onCloud: (RemoteType) -> Unit,
    onOpenSaved: (RemoteLocation) -> Unit,
    onLogout: (RemoteLocation) -> Unit,
    onDelete: (RemoteLocation) -> Unit,
    onExit: () -> Unit
) {
    BackHandler(onBack = onExit)

    Column(modifier = Modifier.fillMaxSize()) {
        BeiPageHeader(
            title = "云盘",
            subtitle = "夸克 / 百度 / 阿里云盘；登录后即可浏览并播放"
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp, start = 2.dp, end = 2.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "drives") {
                NetworkRow(title = "网盘") {
                    item {
                        BeiIconCard(
                            title = "夸克网盘",
                            subtitle = if (quarkLoggedIn) "已登录" else "扫码登录",
                            onClick = { onCloud(RemoteType.QUARK) },
                            modifier = Modifier.width(SourceCardWidth)
                        ) {
                            Box {
                                SourceIcon(kind = SourceKind.Quark)
                                if (quarkLoggedIn) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .align(Alignment.TopEnd)
                                            .background(BeiGlass.Success, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                    item {
                        BeiIconCard(
                            title = "百度网盘",
                            subtitle = if (baiduLoggedIn) "已登录" else "设备码登录",
                            onClick = { onCloud(RemoteType.BAIDU) },
                            modifier = Modifier.width(SourceCardWidth)
                        ) {
                            Box {
                                SourceIcon(kind = SourceKind.Baidu)
                                if (baiduLoggedIn) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .align(Alignment.TopEnd)
                                            .background(BeiGlass.Success, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                    item {
                        BeiIconCard(
                            title = "阿里云盘",
                            subtitle = if (aliLoggedIn) "已登录" else "扫码登录",
                            onClick = { onCloud(RemoteType.ALI) },
                            modifier = Modifier.width(SourceCardWidth)
                        ) {
                            Box {
                                SourceIcon(kind = SourceKind.Ali)
                                if (aliLoggedIn) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .align(Alignment.TopEnd)
                                            .background(BeiGlass.Success, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (savedLocations.isNotEmpty()) {
                item(key = "saved") {
                    NetworkRow(title = "已保存 · ${savedLocations.size}") {
                        items(savedLocations, key = { "cloud-" + it.id }) { location ->
                            // 云盘条目没有「编辑」（除了重新登录没别的可改），但有「退出登录」。
                            LocationCard(
                                location = location,
                                onOpen = { onOpenSaved(location) },
                                onEdit = null,
                                onLogout = { onLogout(location) },
                                onDelete = { onDelete(location) }
                            )
                        }
                    }
                }
            }
        }
    }
}
