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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.dualsub.tv.network.RemoteLocation
import com.dualsub.tv.network.RemoteType
import com.dualsub.tv.network.nas.NasVendor
import com.dualsub.tv.ui.shell.BeiCard
import com.dualsub.tv.ui.shell.BeiIconCard
import com.dualsub.tv.ui.shell.BeiPillButton
import com.dualsub.tv.ui.shell.BeiSectionTitle
import com.dualsub.tv.ui.shell.SourceIcon
import com.dualsub.tv.ui.shell.SourceKind
import com.dualsub.tv.ui.theme.BeiDims
import com.dualsub.tv.ui.theme.BeiGlass

/**
 * 网络页横向行里要复用的几个件。
 *
 * ## 为什么从 `NetworkScreen.kt` 里拆出来
 *
 * 一是它把那个文件顶到了 680 行 —— 项目的惯例是"能拆就拆，`PlayerScreen.kt` 那种
 * 1400 行的大文件是教训不是范例"。二是这几件**本来就不只这一页在用**：
 * `LocalNetworkScreen` 也有一行"已保存"。
 */

/**
 * 横向行里卡片的固定宽度。
 *
 * [SourceCardWidth] 的量法值得记一笔：Android TV 上 1dp = 2px（xhdpi），
 * 1080p 屏的**可用宽度只有约 960dp**（还要减掉外壳左右各 40dp 的安全边距）。
 * 原先写成 240dp 时，网络主页 5 张入口卡 = 240×5 + 18×4 = **1272dp**，
 * 必然把第 4、5 张挤出屏幕 —— 「一屏显示不下」就是这个原因，跟图标大小无关。
 * 收到 120dp 后是 672dp，约占七成宽，右边留白正好透气。
 *
 * 横向行里再 `fillMaxWidth` 会让每张卡都撑满，一行只剩一张 —— 所以必须定宽。
 */
internal val SourceCardWidth = 120.dp

/**
 * 带操作按钮的完整位置卡宽度 —— **只给子页用**（「本地网络」「云盘」「NAS」）。
 *
 * 主页那一行不用它：主页要的是"子弹带"，见 [SavedLocationCard]。
 */
internal val LocationCardWidth = 340.dp

/** 「已保存」那一行的小卡：与入口卡同尺寸。 */
internal val SavedCardWidth = SourceCardWidth

/** 子弹带里的小卡图标，比入口卡（[BeiDims.IconSourceCard]）再小一号。 */
private val SavedIconSize = 24.dp

/** 「已保存」那一行的行距 —— 比 [BeiDims.CardGap] 紧得多，一串小卡挨成"子弹带"。 */
private val SavedCardGap = 8.dp

/**
 * 一个横向分组：小标题 + 可左右滑动的一排卡片。
 *
 * [content] 收的是 `LazyListScope`（参数与 `LazyRow` 完全一致），所以调用方直接用
 * `item { … }` / `items(…)` 往里放卡片，不需要另记一套 API。
 * 两端各留 2dp：卡片获得焦点会放大 1.03，贴边时描边不该被裁掉。
 *
 * [gap] 默认走全局卡片间距；「已保存」那一行传一个更小的值来做子弹带效果。
 */
@Composable
internal fun NetworkRow(
    title: String,
    gap: Dp = BeiDims.CardGap,
    content: LazyListScope.() -> Unit
) {
    Column {
        BeiSectionTitle(title, modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            content = content
        )
    }
}

/**
 * 一条已保存的位置（**完整版**：可点进入，卡内还有「进入 / 编辑 / 退出登录 / 删除」）。
 *
 * 只给**子页**用 —— 「本地网络」看 SMB、「云盘」看三家网盘、「NAS」看四家 NAS，
 * 每个子页只管自己那一类，所以在那里编辑 / 删除最自然。
 *
 * 网盘条目没有「编辑」（除了重新登录没别的可改）但有「退出登录」；SMB / WebDAV / NAS 反过来。
 *
 * 默认宽度是 [LocationCardWidth]：横向行里必须定宽，否则每张卡会按自己内容的长短长得
 * 不一样，一行会参差不齐。
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
            text = location.subtitleText(),
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
            BeiPillButton(label = "删除", onClick = onDelete, contentColor = BeiGlass.Danger)
        }
    }
}

/**
 * 「已保存」行里的小卡 —— **子弹带**尺寸，**卡里没有任何按钮**。
 *
 * ## 为什么没有按钮
 *
 * 早先主页的位置卡是 340dp 宽、塞着「进入 / 编辑 / 退出登录 / 删除」四个胶囊。
 * 可主页那一行要和上面的入口卡排在一起，还要能一眼扫过所有已保存的位置 ——
 * 340dp 一屏只放得下 2.8 张。收成 [SourceCardWidth] 之后那四个按钮必然放不下，
 * 而主页本来也不该是"管理位置"的地方。
 *
 * 所以这里**只管进入**：点一下直接浏览。编辑 / 删除 / 退出登录都挪到**各自的子页**
 * （「本地网络」「云盘」「NAS」），那几页用的是完整版 [LocationCard]。
 *
 * 信息没丢：图标认来源，名称平时就在，聚焦时那一行换成「类型 · host」（`BeiIconCard`
 * 的行为），共享名与用户名也都在里面。
 */
@Composable
internal fun SavedLocationCard(
    location: RemoteLocation,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier.width(SavedCardWidth)
) {
    BeiIconCard(
        title = location.displayName,
        subtitle = location.subtitleText(),
        onClick = onOpen,
        modifier = modifier
    ) { SourceIcon(kind = location.sourceKind, size = SavedIconSize) }
}

/** 「已保存」那一行的行距，供调用方传给 [NetworkRow]。 */
internal val SavedRowGap: Dp get() = SavedCardGap

/** 卡片上那行"次级信息"：类型 + 地址（+ 共享名 / 用户名）。列表卡与子弹带卡共用。 */
private fun RemoteLocation.subtitleText(): String = buildString {
    append(type.displayName)
    append(" · ").append(host)
    share?.takeIf { it.isNotBlank() }?.let { append("/").append(it) }
    if (type.isSmbLike) {
        append(" · ")
        append(if (isAnonymous) "匿名" else username)
    }
}

/** 只有 SMB 才显示「匿名 / 用户名」—— 别的类型没有这回事。 */
internal val RemoteType.isSmbLike: Boolean get() = this == RemoteType.SMB

/**
 * 一条位置该用哪个图标。
 *
 * NAS 需要**再往下分一层**：四家的接入协议完全一样，但品牌卡要一眼认得出是哪一家，
 * 所以从 `RemoteLocation.id` 里还原品牌（见 [vendorOf]）。还原不出来时退回通用的
 * [SourceKind.Nas]（双格箱体），不至于没图标。
 */
internal val RemoteLocation.sourceKind: SourceKind
    get() = when (type) {
        RemoteType.SMB -> SourceKind.Smb
        RemoteType.WEBDAV -> SourceKind.WebDav
        RemoteType.DLNA -> SourceKind.Dlna
        RemoteType.QUARK -> SourceKind.Quark
        RemoteType.BAIDU -> SourceKind.Baidu
        RemoteType.ALI -> SourceKind.Ali
        RemoteType.NAS -> when (vendorOf(this)) {
            NasVendor.FEINIU -> SourceKind.Feiniu
            NasVendor.SYNOLOGY -> SourceKind.Synology
            NasVendor.QNAP -> SourceKind.Qnap
            NasVendor.UGREEN -> SourceKind.Ugreen
            null -> SourceKind.Nas
        }
    }

/**
 * 从 `RemoteLocation.id` 里还原 NAS 品牌。
 *
 * id 形如 `nas:SYNOLOGY:http://192.168.1.252:5005` —— 品牌名嵌在里面，这样「编辑」才知道
 * 该回到哪一家的表单，而不必给 `RemoteType` 加四个语义完全相同的枚举值。
 */
internal fun vendorOf(location: RemoteLocation): NasVendor? = location.id
    .removePrefix("nas:")
    .substringBefore(':')
    .let { name -> NasVendor.entries.firstOrNull { it.name == name } }
