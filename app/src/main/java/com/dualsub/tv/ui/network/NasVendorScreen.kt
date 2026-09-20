package com.dualsub.tv.ui.network

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.dualsub.tv.network.RemoteLocation
import com.dualsub.tv.network.nas.NasVendor
import com.dualsub.tv.ui.shell.BeiIconCard
import com.dualsub.tv.ui.shell.BeiPageHeader
import com.dualsub.tv.ui.shell.SourceIcon
import com.dualsub.tv.ui.shell.SourceKind

/**
 * 「NAS」子页：按品牌接入一台家用 NAS。
 *
 * ## 为什么按品牌分，而不是给一个空表单
 *
 * 「接一台 NAS」在四个品牌上其实是同一件事的四套叫法 —— 都要在后台某个角落开启
 * WebDAV，都有一条自己的默认端口。让用户自己去查"我家这台填几号端口"正是这个页面
 * 要消灭的痛苦：点品牌 → 只填地址与账号 → 端口预填并自动探测。
 *
 * 四家的端口、开启路径与各自的坑全部收在 [NasVendor] 的数据里，本页只负责列出来。
 * 这样以后加第五家（极空间、铁威马…）只需要往那个枚举里加一项。
 *
 * ## 各家进去之后
 *
 * 「各家 NAS 的特色配置」目前落在**预填 + 探测**这一层：默认端口、开启菜单路径、
 * 该家特有的注意事项。再往下就是各家专有 API（DSM / QTS / UGOS）了，
 * 那是另一个量级的工程，且随固件变动，现在不做。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NasVendorScreen(
    savedLocations: List<RemoteLocation>,
    onVendor: (NasVendor) -> Unit,
    onOpenSaved: (RemoteLocation) -> Unit,
    onEditSaved: (RemoteLocation) -> Unit,
    onDelete: (RemoteLocation) -> Unit,
    onExit: () -> Unit
) {
    BackHandler(onBack = onExit)

    Column(modifier = Modifier.fillMaxSize()) {
        BeiPageHeader(
            title = "NAS",
            subtitle = "按品牌接入：只填地址与账号，端口按各家默认值预填并自动探测"
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp, start = 2.dp, end = 2.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "vendors") {
                NetworkRow(title = "品牌") {
                    NasVendor.entries.forEach { vendor ->
                        item(key = vendor.name) {
                            BeiIconCard(
                                title = vendor.label,
                                subtitle = vendor.systemName,
                                onClick = { onVendor(vendor) },
                                modifier = Modifier.width(SourceCardWidth)
                            ) { SourceIcon(kind = SourceKind.Nas) }
                        }
                    }
                }
            }

            if (savedLocations.isNotEmpty()) {
                item(key = "saved") {
                    NetworkRow(title = "已保存 · ${savedLocations.size}") {
                        items(savedLocations, key = { "nas-" + it.id }) { location ->
                            LocationCard(
                                location = location,
                                onOpen = { onOpenSaved(location) },
                                onEdit = { onEditSaved(location) },
                                onLogout = null,
                                onDelete = { onDelete(location) }
                            )
                        }
                    }
                }
            }
        }
    }
}
