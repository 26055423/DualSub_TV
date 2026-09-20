package com.dualsub.tv.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.dualsub.tv.network.RemoteLocation
import com.dualsub.tv.network.RemoteType
import com.dualsub.tv.ui.shell.BeiCard
import com.dualsub.tv.ui.shell.BeiPillButton
import com.dualsub.tv.ui.shell.BeiSectionTitle
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass

/**
 * 网络页横向行里要复用的几个件。
 *
 * ## 为什么从 `NetworkScreen.kt` 里拆出来
 *
 * 一是它把那个文件顶到了 680 行 —— 项目的惯例是"能拆就拆，`PlayerScreen.kt` 那种
 * 1400 行的大文件是教训不是范例"。二是这几件**本来就不只这一页在用**：
 * `LocalNetworkScreen` 也有一行"发现的设备"和一行"已保存"。
 *
 * [NetworkRow] 与 `LocalNetworkScreen` 里那个同名私有函数仍然重复（那边的版本只服务
 * 一个调用点，暂时留在原地）；等第三个调用方出现，那边也该改成用这一份。
 */

/** 横向行里卡片的固定宽度 —— 横向行里再 `fillMaxWidth` 会让每张卡都撑满，一行只剩一张。 */
internal val SourceCardWidth = 240.dp
internal val LocationCardWidth = 340.dp

/**
 * 一个横向分组：小标题 + 可左右滑动的一排卡片。
 *
 * [content] 收的是 `LazyListScope`（参数与 `LazyRow` 完全一致），所以调用方直接用
 * `item { … }` / `items(…)` 往里放卡片，不需要另记一套 API。
 * 两端各留 2dp：卡片获得焦点会放大 1.03，贴边时描边不该被裁掉。
 */
@Composable
internal fun NetworkRow(
    title: String,
    content: LazyListScope.() -> Unit
) {
    Column {
        BeiSectionTitle(title, modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(BeiDims.CardGap),
            content = content
        )
    }
}

/**
 * 一条已保存的位置。
 *
 * 卡片本身可点（＝进入浏览），卡内再放「进入 / 编辑 / 退出登录 / 删除」。
 * 网盘条目没有「编辑」（没东西可编辑）但有「退出登录」；SMB / WebDAV / DLNA 反过来。
 *
 * 默认宽度给的是 [LocationCardWidth]：横向行里必须定宽，否则每张卡会按自己内容的长短
 * 长得不一样，「已保存」那一行会参差不齐。
 */
@Composable
internal fun LocationCard(
    location: RemoteLocation,
    onOpen: () -> Unit,
    onEdit: (() -> Unit)?,
    onLogout: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier.width(LocationCardWidth)
) {
    BeiCard(onClick = onOpen, modifier = modifier) {
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
internal val RemoteType.isSmbLike: Boolean get() = this == RemoteType.SMB
