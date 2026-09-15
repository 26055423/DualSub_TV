# DualSub TV

面向 Android TV / 大屏电视的播放器，核心能力是**同时挂载主字幕与次字幕**。

两路字幕在来源、样式、位置、时间轴上完全独立：可以一路用视频内嵌轨道、另一路用外挂文件，
也可以各挂一个外挂文件，各自调字号 / 底部距离 / 时间偏移，互不影响。
片源同样不限于本机 —— 支持局域网 SMB 共享与 DLNA 媒体服务器。

---

## 功能

**媒体库**
- 基于 `MediaStore` 扫描本机与 U 盘上的视频，网格布局，遥控器 D-pad 可导航
- Android 13+ 使用 `READ_MEDIA_VIDEO`，旧版本回落 `READ_EXTERNAL_STORAGE`
- 支持从文件管理器「用 DualSub TV 打开」直接进播放页（`ACTION_VIEW` + `video/*`）

**局域网资源**（首页「网络位置」入口）
- **扫描局域网 SMB**：并发探测本机所在网段的 445 端口，省得自己去查 NAS 的 IP
- **列出共享**：填好账号后直接向服务器要共享清单，点选即可 —— **不需要事先知道共享名**
- **DLNA/UPnP**：一键扫描局域网内的媒体服务器，浏览并播放
- 已添加的服务器可**编辑**（改 IP、改密码、换共享）与删除
- 片源与本地视频走同一个播放器，断点续播、双字幕一视同仁

**播放器**
- Media3 / ExoPlayer 播放，硬解码走系统 `MediaCodec`
- 自绘控制条：播放/暂停、±10s、进度与时间
- 断点续播：每 5 秒落盘，下次打开自动跳回（`smb://` 片源同样记住）

**双字幕**
- 主字幕、次字幕各一个独立的字幕轨状态（来源 / 字幕条目 / 时间偏移 / 样式）
- 字幕来源：
  - 视频**内嵌字幕轨**（用 `MediaExtractor` 读取，不依赖播放引擎的字幕渲染）
  - **外挂字幕文件**（系统文件选择器，`srt` / `vtt` / `ass` / `ssa`，读取权限持久化）
- 每路可单独设置字号、底部距离、描边强度、时间偏移（±100ms 步进）
- 每个视频各自记住「上次用的哪两路字幕」

**字幕解析**（`com.dualsub.tv.subtitle`，纯 Kotlin，不依赖 Android 框架与 media3）
- `SrtParser`：容错序号缺失、CRLF、老式坐标后缀、内联 HTML 标签
- `VttParser`：跳过 `NOTE`/`STYLE`/`REGION` 块，支持 cue settings、`MM:SS.mmm`、`<v>`/`<c>` 标记
- `AssParser`：按 `Format:` 声明切分字段（`Text` 里的逗号不会被截断），展开 `\N`，剥离 `{\...}` 覆盖指令
- `SubtitleTextDecoder`：BOM → 严格 UTF-8 → GB18030 回退，处理中文圈常见的无 BOM GBK 字幕

---

## 双字幕是怎么做到的

ExoPlayer 的内建文本渲染器**同一时刻只能输出一路字幕**，无法直接满足需求。项目的做法是：

1. **关掉内建字幕渲染** —— 在 `PlayerController` 里把 `TRACK_TYPE_TEXT` 与 `TRACK_TYPE_METADATA` 都禁用，
   画面上不会出现任何 ExoPlayer 自己画的字幕。
2. **视频内嵌字幕轨仍可读** —— `EmbeddedSubtitleReader` 用框架的 `MediaExtractor` 打开视频，
   枚举 `text/*`、`application/x-subrip` 等字幕轨并抽取样本，转成统一的 `List<SubtitleCue>`。
   数据源是注入的 `MediaDataSource`，所以本地文件与 SMB 文件共用同一套逻辑。
   不同容器的样本形态差异都做了兼容：完整字幕文档 / 逐条纯文本（时间取样本时间戳）/ 空样本清屏。
3. **两路都用 Compose 自绘** —— 主、次字幕各是一个 `SubtitleOverlay`，由各自的 `bottomPaddingDp`
   决定叠放位置，因此不会互相遮挡。
4. **同步** —— 一条 50ms 的 ticker 按 `player.currentPosition` 分别对两路做二分查找定位
   （`SubtitleCueIndex`），各自叠加自己的时间偏移。

代价是明确接受的：ASS 的复杂特效（`{\pos}`、卡拉OK、精确定位）不做还原，只取时间轴与文本。
后续如需增强，解析层是独立的 `SubtitleParser` 接口，替换或新增实现不影响上层。

## 局域网播放是怎么做到的

- **SMB 用两个库，职责分开**：
  - [smbj]（`com.hierynomus:smbj`）负责**浏览与播放**。播放走自写的 `SmbDataSource`
    （一个 ExoPlayer `DataSource`），直接把 `smb://host/share/path` 交给播放器 ——
    相比「起本地 HTTP 代理再转发」，少一个组件、没有端口冲突，而且天然支持随机读，
    所以**拖进度条与断点续播都能正常工作**。
  - [jcifs-ng]（`eu.agno3.jcifs:jcifs-ng`）**只负责列举共享**。smbj 只暴露文件级 API
    （`Session.connectShare`），没有 SRVSVC 的 `NetShareEnumAll`；而 jcifs-ng 对
    `smb://host/` 调 `listFiles()` 得到的就是共享清单 —— 这正是其他播放器
    「只要用户名密码」的原因。播放链路不碰这个库，避免动到已验证的部分。
- **服务器发现**（`SmbDiscovery`）对当前网段的 445 端口做并发 TCP 探测（并发 32、超时 500ms），
  比逐台尝试 SMB 握手快得多，也不依赖 NetBIOS/mDNS —— 这两者在家庭网络里常被路由器拦掉。
- **枚举受限时的兜底**（`SmbSession.probeShares`）：少数服务器组策略会禁止枚举，
  这时退回逐个尝试 23 个常见共享名（覆盖群晖 / 威联通 / Windows 的默认与惯用命名）。
- **DLNA** 不引入 Cling / jUPnP（Cling 已 EOL，jUPnP 会带进 OSGi/javax 依赖），
  而是自己实现了最小客户端：SSDP 多播发现 → 解析设备描述拿 ContentDirectory 的 controlURL
  → SOAP `Browse` 列目录。DLNA 的资源地址本身就是普通 `http://`，ExoPlayer 原生支持。
- **账号密码**按既定选择以**明文**存在应用私有目录（`DataStore` 所在目录），
  非 root 设备上其他应用读不到。播放器只拿到 `smb://host/...`，凭据由
  `SmbLocationRegistry` 按主机反查。改了密码后记得用「编辑」更新，并会主动丢弃旧连接。

---

## 构建

前置：JDK 17、Android SDK（`platforms;android-35`，`build-tools;34.0.0`）。
`local.properties` 里的 `sdk.dir` 指向本机 SDK。

```powershell
# 单元测试（字幕解析、SMB 路径编解码、子网枚举、DLNA 报文解析、时间轴定位）
.\gradlew.bat :app:testDebugUnitTest

# 打包
.\gradlew.bat :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 安装到电视

```powershell
adb connect <电视IP>:5555
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

**关于两个启动入口**：`MainActivity` 刻意声明了**两个独立的 `intent-filter`**
（`LEANBACK_LAUNCHER` 与 `LAUNCHER`），这不是冗余：

- Google 认证的 Android TV / Google TV 桌面只认 `LEANBACK_LAUNCHER`；
- 国内 TCL / 海信 / 小米等**基于 AOSP 定制的厂商桌面**只认标准 `LAUNCHER`，
  只声明前者会出现「装上了但桌面上找不到图标」。

同理，`android.software.leanback` 声明为 `required="false"`，避免厂商系统因未声明该 feature 而被安装渠道过滤。

> `category` 在同一个 `intent-filter` 内是「全部满足」语义，所以两个入口必须拆开写；
> 合并成一组会导致两边都匹配不上。

## 首次使用：接你的 NAS

**不知道 NAS 的 IP？**

1. 首页点「网络位置」→「扫描局域网 SMB」
2. 扫出来的整条设备卡片选中即进配置（卡片可点，不必去找小按钮）
3. 只填**用户名/密码**（没设密码就留空 = 匿名），主机已按本机网段预填前三位
4. 点「列出共享」→ 直接选中你的共享文件夹 → 保存

**已经知道 IP 和共享名？** 直接「添加 SMB 服务器」手工填。

**要改 IP / 密码 / 换共享？** 在列表里点该条目的「编辑」。

如果 NAS 开的是 DLNA 而不是 SMB，点「扫描 DLNA 设备」，保存后直接进入。

## 技术选型

| 项 | 选择 | 说明 |
|---|---|---|
| 播放引擎 | Media3 / ExoPlayer 1.4.1 | 纯 JVM 依赖；双字幕由自绘实现 |
| SMB 浏览播放 | `com.hierynomus:smbj` 0.13.0 | SMB2/SMB3；需排除其传递的 bcprov，见下 |
| SMB 列举共享 | `eu.agno3.jcifs:jcifs-ng` 2.1.10 | 提供 SRVSVC `NetShareEnumAll`，smbj 没有这层 |
| 摘要算法 | `org.bouncycastle:bcprov-jdk15to18` | NTLM 需要 MD4；选 15to18 变体是因为它不依赖 `java.lang.invoke`，且两个 SMB 库传递的 `jdk18on` 都已排除 |
| DLNA | 自实现（SSDP + SOAP） | 避免 Cling（已 EOL）与 jUPnP 的依赖负担 |
| 服务器发现 | 自实现（子网 + 445 端口并发探测） | 不依赖 NetBIOS/mDNS，家庭网络里更可靠 |
| UI | Compose for TV（`androidx.tv:tv-material` 1.0.0） | 卡片自带 D-pad 焦点态 |
| 列表 | Compose `LazyVerticalGrid` | `tv-foundation:1.0.0` **不提供** lazy 网格组件（只有 list 系列），故用标准库网格 + TV 卡片 |
| 构建 | AGP 8.7.3 / Kotlin 2.0.21 / compileSdk 35 | `tv-foundation:1.0.0` 要求 compileSdk ≥ 35 且 AGP ≥ 8.6.0 |
| 依赖注入 | 手写 `AppServices` 容器 | 规模不需要 Hilt，省掉注解处理器 |
| 存储 | DataStore Preferences | 断点、字幕来源、样式、网络位置；无需 Room |
| 最低版本 | `minSdk 23` | 受 `tv-foundation:1.0.0` 限制 |

## 已知限制

- **少数服务器会拒绝枚举共享**（组策略或 NAS 设置里关掉了）。这时应用会退化为逐个尝试
  23 个常见共享名；两者都没命中也**不代表服务器没有共享**，请到设备后台查看实际名称后手工填写。
- MP4 的 `tx3g` 二进制字幕轨能被列出，但不解析出文本
- ASS 特效不做还原（见上文取舍）
- 字幕颜色目前不可调（字号 / 位置 / 描边 / 偏移可调）
- **DLNA 片源暂时读不到内嵌字幕轨**：那需要对 http 资源实现基于 Range 的随机读，
  本版本没做。SMB 与本地片源不受影响；任何片源都可以挂外挂字幕。
- **Dolby Vision 能否播放取决于设备的解码能力**：ExoPlayer 走系统 `MediaCodec`，
  DV 需要厂商提供对应 profile 的解码器；HDR10 / HLG 一般没问题。
  遇到 DV 片源黑屏或偏色，可先在电视上关掉 DV 输出再验证
- 网络位置用明文保存密码（可在应用私有目录找到）；介意的话可以改用只读的访客账号

[smbj]: https://github.com/hierynomus/smbj
[jcifs-ng]: https://github.com/AgNO3/jcifs-ng
