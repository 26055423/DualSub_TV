# DualSub TV — 架构文档

> 版本：2026-09-20
> 适用代码基线：UI 风格统一（深墨夜景 + 无色玻璃 + 香槟金）之后的当前工作区
>
> 相关文档：`docs/UI_STYLE_REFERENCE.md`（视觉规范与层序）、`docs/HANDOVER.md`（交接与踩坑）、
> `README.md`（功能与已知限制）

---

## 目录

1. [产品定位](#1-产品定位)
2. [技术栈总览](#2-技术栈总览)
3. [模块结构](#3-模块结构)
4. [核心架构决策](#4-核心架构决策)
5. [数据流](#5-数据流)
6. [网络来源体系](#6-网络来源体系)
7. [字幕系统](#7-字幕系统)
8. [播放器层](#8-播放器层)
9. [持久化](#9-持久化)
10. [UI 层](#10-ui-层)
11. [扩展指南：新增网络来源](#11-扩展指南新增网络来源)

---

## 1. 产品定位

Android TV / 大屏电视播放器，核心功能是同时渲染**两路相互独立的字幕**（主字幕 + 次字幕）。
典型场景：看外语片时主字幕显示母语、次字幕显示目标语言，方便对照学习。

支持的视频来源：本地存储、SMB（NAS）、DLNA、WebDAV（AList / Nextcloud 等）、
夸克网盘、百度网盘、阿里云盘。

---

## 2. 技术栈总览

| 类别 | 库 / 框架 | 版本 |
|---|---|---|
| 语言 | Kotlin | 2.0.21 |
| 构建 | AGP | 8.7.3 |
| compileSdk / minSdk / targetSdk | — | 35 / 23 / **34** |
| UI | Compose for TV（`tv-material` / `tv-foundation`） | 1.0.0 |
| 播放引擎 | libVLC | 3.6.5 |
| Media3（周边） | exoplayer / common | 1.6.0 |
| SMB 浏览/播放 | smbj | 0.13.0 |
| SMB 列举共享 | jcifs-ng | 2.1.10 |
| NTLM 加密 | bcprov-jdk15to18 | 1.78.1 |
| HTTP 客户端 | OkHttp | 4.12.0 |
| WebDAV 客户端 | sardine-android（JitPack） | 0.9 |
| 二维码生成 | ZXing Core | 3.5.3 |
| AI 配置用的内嵌 HTTP server | NanoHTTPD | 2.3.1 |
| 持久化 | DataStore Preferences | 1.1.1 |
| 协程 | kotlinx-coroutines-android | 1.9.0 |
| DI | 手写 `AppServices` 容器 | — |

### 构建配置里几处**刻意**的设置（改之前先看理由）

| 设置 | 理由 |
|---|---|
| `targetSdk = 34`（不是 35） | 升到 35 会引入强制 edge-to-edge 等行为变更，与项目目标无关 |
| `abiFilters += "arm64-v8a"` | libVLC 的 AAR 打包了全部 4 个 ABI（AAR 本身 83 MB），不过滤时 debug APK 从 21 MB 涨到 **210 MB**；电视是 arm64，其余三个纯属浪费 |
| debug 额外 `abiFilters += "x86_64"` | 电脑上的模拟器是 x86_64，缺了它 libVLC 的 native 库装不进去、APP 起不来；release 仍只留 arm64 |
| `buildConfigField BUILD_TIME` | 界面与日志里显示构建时刻 —— 真机来回调试最耗时的一环是"确认装的是不是最新版" |
| `kotlin.compiler.execution.strategy=in-process`（gradle.properties） | 本机 Kotlin 编译进程起不来（详见 `HANDOVER.md` 第六节），堆上限 4096m |

### 编译 JDK

需要 **JDK 17**。若环境默认 JDK 过高，显式指定：

```bash
JAVA_HOME=/path/to/jdk-17 bash gradlew :app:compileDebugKotlin
```

---

## 3. 模块结构

```
app/src/main/java/com/dualsub/tv/
│
├── DualSubApp.kt            # Application，启动 InAppLog
├── MainActivity.kt          # 单 Activity，渲染 AppRoot（两个入口 filter，见 README）
│
├── core/
│   ├── AppServices.kt       # 手写 DI 容器（Application 级单例）
│   └── InAppLog.kt          # 内存环形日志，TV 上取代 Logcat
│
├── data/
│   └── SettingsStore.kt     # DataStore 持久化（字幕样式/选择、网络位置、AI 配置、缓冲）
│
├── media/                   # 本地媒体与容器解析
│   ├── EmbeddedSubtitleReader.kt   # 用 MediaExtractor(C) 提取内嵌字幕轨
│   ├── LocalMediaDataSource.kt     # content:// / file:// 随机读
│   ├── MediaLibraryScanner.kt      # 扫描本地视频库（MediaStore）
│   ├── RandomAccessExtractorInput.kt
│   ├── SparseMatroskaReader.kt     # 按 Cues 稀疏跳读 MKV 的字幕块
│   └── VideoItem.kt                # 视频元数据（URI、标题、时长）
│
├── network/                 # 网络浏览与数据源路由
│   ├── RemoteEntry.kt       # RemoteType 枚举 + RemoteLocation + RemoteEntry
│   ├── RemoteBrowser.kt     # 统一浏览接口（rootPath / list / close）
│   ├── RemoteBrowserFactory.kt     # 按 RemoteType 构造对应 Browser
│   ├── DualSubDataSourceFactory.kt # Media3 DataSource.Factory，按 scheme 分发
│   ├── MediaSourceProvider.kt      # 按 URI scheme 选随机读数据源（字幕提取用）
│   ├── dlna/                # DlnaBrowser（SSDP + SOAP）/ DlnaMessages
│   ├── smb/
│   │   ├── SmbBrowser.kt           # SMB 目录浏览
│   │   ├── SmbDataSource.kt        # Media3 DataSource，SMB 随机读（播放链路）
│   │   ├── SmbMediaDataSource.kt   # 字幕提取用的 SMB 随机读（带 LRU 块缓存）
│   │   ├── SmbDiscovery.kt         # 局域网 445 端口并发探测
│   │   ├── SmbShareLister.kt       # 列举服务器共享（jcifs-ng）
│   │   ├── SmbSession.kt           # 单台服务器的连接会话（含两层自愈，见 §6）
│   │   ├── SmbSessionPool.kt       # 按主机复用连接 + SmbLocationRegistry
│   │   ├── SmbPaths.kt / ReadFullyAt.kt / SubnetHosts.kt / SmbSecurity.kt
│   └── webdrive/            # 云网盘：Quark / Baidu / Ali 各三个文件 + WebDavBrowser
│
├── player/                  # 播放引擎封装与字幕数据结构
│   ├── VlcPlayerController.kt      # libVLC 封装（为何换引擎见 §4.1）
│   ├── SubtitleTrack.kt            # SubtitleSource（sealed）+ SubtitleTrack
│   ├── SubtitleCueIndex.kt         # 二分查找当前 cue
│   ├── SubtitleStyle.kt            # 字号/颜色/描边/底部距离 + PRIMARY / SECONDARY 预设
│   └── EmbeddedCueCache.kt         # 内嵌字幕轨窗口缓存
│
├── subtitle/                # 字幕解析器（纯 Kotlin，不依赖 Android 框架与 media3）
│   ├── SrtParser.kt / VttParser.kt / AssParser.kt / EmbeddedAssParser.kt
│   ├── AssOverride.kt              # ASS override tag（颜色/字体/位置/卡拉OK）
│   ├── SubtitleCue.kt              # startMs / endMs / text（**text 可能含 \n**）
│   ├── SubtitleFormat.kt / SubtitleParser.kt / SubtitleParsers.kt
│   ├── SubtitleTextDecoder.kt      # BOM → UTF-8 → GB18030 容错
│   └── SubtitleTime.kt
│
└── ui/
    ├── AppRoot.kt                  # 顶层导航状态机（不使用 Navigation 组件）
    ├── format/TimeFormat.kt
    ├── theme/
    │   ├── BeiGlass.kt             # **唯一色板** + BeiDims（尺寸）+ BeiMotion（动效）
    │   └── Theme.kt                # tv-material3 darkColorScheme()
    ├── shell/
    │   ├── AppShell.kt             # 左侧导航 + 夜景观底/光斑 + ShellTab 枚举
    │   └── BeiUi.kt                # 复用基础件：标题/卡片/胶囊按钮/选项胶囊/来源图标
    ├── library/LibraryScreen.kt
    ├── settings/
    │   ├── SettingsScreen.kt       # 视频缓冲 + 主/次字幕默认样式（含 AI 字幕入口）
    │   └── AiSettingsScreen.kt     # AI 字幕配置（扫码填表）
    ├── network/
    │   ├── NetworkScreen.kt        # 来源管理（6 张大图标卡）
    │   ├── LocalNetworkScreen.kt   # 「本地网络」子页：进入即扫描 + 手动配置
    │   ├── RemoteBrowseScreen.kt   # 通用目录浏览 + SmbServerForm
    │   ├── WebDavServerForm.kt
    │   └── Quark/Baidu/AliLoginScreen.kt
    └── player/
        ├── PlayerScreen.kt         # 键盘/遥控处理、层叠组合（最复杂的一个文件）
        ├── PlayerViewModel.kt      # 播放状态持有，StateFlow 驱动 UI
        └── components/
            ├── PlayerControls.kt           # 进度条 + 两段时间 + 字幕入口
            ├── PlayerStatusBar.kt          # 顶栏：文件名 / 网速 / 时钟
            ├── PlayerMenuOverlay.kt        # 两级侧边菜单 + MenuEntry 模型
            ├── PlayerChoicePickerOverlay.kt# 条目过多时的整页选择页
            ├── PlayerInfoOverlay.kt        # 左上详情层（按上/下呼出）
            ├── PlayerExitConfirmOverlay.kt # 退出播放确认框
            ├── AiSubtitleProgressOverlay.kt# 右上角 AI 进度/实时胶囊
            └── SubtitleOverlay.kt          # 两路字幕的自绘层
```

---

## 4. 核心架构决策

### 4.1 播放引擎：为何从 Media3 换到 libVLC

**起因**：Media3 无法播放 RM / RMVB。Media3 的 FFmpeg 扩展（`decoder_ffmpeg`）只提供解码器、
不解析容器，而没有任何电视芯片能硬解 RealVideo，软解则重新卡死。libVLC 自带完整 FFmpeg，
容器解析与软解码两样都有。

**影响范围**：

- 播放引擎从 `PlayerController`（ExoPlayer 封装）换成 `VlcPlayerController`（libVLC 封装）
- Media3 保留，但只用于周边（数据源接口、字幕提取）
- 渲染容器是 **`VLCVideoLayout`**（不是裸 `SurfaceView`）—— 它内部会挂视频面与字幕面
- 仓库里还留着一个 `libs/lib-decoder-ffmpeg-release.aar`：改用 libVLC 后它对播放链路
  **已不是必需**（VLC 自带 FFmpeg），仅因还有其它引用点而暂时保留

**音频输出**：`--aout=opensles_android`。代价是**拿不到 Dolby/DTS 直通、多声道下混**，
详见 README 的「已知限制」。

### 4.2 双字幕分工

```
┌────────────────────────────────────┐
│           VLCVideoLayout           │  ← libVLC 管理的视图层
│   ┌────────────────────────────┐   │
│   │      视频 Surface          │   │
│   ├────────────────────────────┤   │
│   │ 图片字幕（PGS/SPU）subtitle │   │  ← 只有位图字幕走这里
│   └────────────────────────────┘   │
└────────────────────────────────────┘
            ↑ 嵌入 Compose
┌────────────────────────────────────┐
│ SubtitleOverlay（主字幕）           │  ← Compose 自绘
├────────────────────────────────────┤
│ SubtitleOverlay（次字幕）           │  ← Compose 自绘，叠在主字幕之上
└────────────────────────────────────┘
```

| | 主字幕 | 次字幕 |
|---|---|---|
| **文本字幕** | **Compose 自绘**（`SubtitleOverlay`） | **Compose 自绘** |
| **图片字幕**（PGS / DVD SPU / 蓝光） | **libVLC**（`setSpuTrack`，Compose 画不了位图） | 不支持（选到图片轨会提示） |
| 数据来源 | `embeddedCues` 窗口读取 / 外挂文件 | 同左 |
| 能调 | 字号 / 颜色 / 描边 / 底部距离 / 时间偏移 | 同左 |

**为什么后来把主字幕也改成自绘**：真机上调不出「指定字幕字体」，中文字幕整片渲染成方框（tofu）；
而自绘走 Compose 的系统字体栈，任何能正常显示中文界面的电视都不会方框。
代价是放弃 libass 的 ASS 特效排版 —— 但那正是次字幕本来就有的限制，两路统一反而简单。

> ⚠️ **图片字幕是唯一仍交 libVLC 的情形**。此时 `primaryCue` 恒为 null，自绘层自然不画。

**同步**：一条 50ms 的 ticker 按 `player.currentPosition` 对两路字幕各做二分查找定位
（`SubtitleCueIndex`），叠加各自的 `offsetMs`。

**两路避让靠位置错开、不靠层级**：次字幕天然画在主字幕之上，所以它用 `bottomPaddingDp`
留一个固定余量（默认 112dp，约两行主字幕加一条间隙）避开主字幕。主字幕特别高时到设置页调大。

**字幕尽量单行**：SRT / ASS 里字幕组按语义断的行（`SubtitleCue.text` 里的 `\n`），在电视上
往往一行放得下（可用宽度 ≈ 屏宽 − 80dp）。`SubtitleOverlay` 用 `TextMeasurer` **先测一次**：
合并换行后只占一行就用合并版，否则**回到原文**按片源断行显示。合并用等长替换（`\n` → 空格），
以免打乱 `assOverride` 里按字符计的 span 与卡拉OK偏移。

### 4.3 手写 DI 容器

`AppServices` 是 Application 级单例，在 `AppRoot` 里构造一次，通过构造函数传给各页面 /
ViewModel。规模不需要 Hilt，手写更透明：所有长生命周期依赖的构造顺序和持有关系一目了然。

### 4.4 导航：状态机而非 Navigation 组件

`AppRoot` 用 `mutableStateOf` 维护「当前 tab」与「正在播放的视频」两个状态。好处：
TV 遥控的返回键处理更直接，不需要处理 Navigation 的 back stack；`VideoItem` 等对象直接传引用。

**播放页不在外壳里** —— 看片是沉浸式场景，左侧常驻导航栏只会碍事，所以 `PlayerScreen`
由 `AppRoot` 直接整屏渲染。播放页的 ViewModel 也**不用 `viewModel()` 创建**（那个 API 把实例
存进 Activity 级 store、离开时不清除，会导致"返回后视频还在放"），而是跟着视频走的 `remember`
+ 离开时显式 `release()`。

---

## 5. 数据流

### 视频播放流

```
用户选择视频（RemoteEntry.playableUri / VideoItem.uri）
    │
    ▼
PlayerViewModel（构造时创建 VlcPlayerController）
    │
    ├─ 读取 SettingsStore：上次选的哪两路字幕
    │
    ▼
VlcPlayerController.open(uri)
    │
    ├─ smb://    ──► 凭据由 SmbLocationRegistry 按 host 反查
    ├─ quark://  ──► QuarkApiClient.getDownloadUrl()
    ├─ baidu://  ──► BaiduApiClient.getDownloadUrl()
    ├─ ali://    ──► AliApiClient.getTranscodingUrl() → 降级 getDownloadUrl()
    ├─ http(s)://──► DLNA / WebDAV 直链
    └─ content://──► 本地文件
         │
         ▼
    libVLC 接管解码与渲染
```

### 字幕加载流

```
PlayerViewModel.loadSubtitle(slot, source)
    │
    ├─ ExternalFile(uri) ─► 读取字节 ─► SubtitleParsers.parse()
    │
    ├─ EmbeddedTrack     ─► EmbeddedSubtitleReader（MediaExtractorCompat）
    │                         └─ SMB  用 SmbMediaDataSource
    │                         └─ 本地 用 LocalMediaDataSource
    │
    ▼
SubtitleTrack(cues, style, offsetMs)
    │
    ▼
_primary / _secondary StateFlow
    │
    ▼
50ms ticker → SubtitleCueIndex.find(cues, positionMs - offsetMs)
    │
    ├─ 文本轨 → _primaryCue / _secondaryCue → SubtitleOverlay 自绘
    └─ 图片轨 → controller.setSpuTrack(vlcSpuId)（交给 libVLC）
```

### UI 状态流

```
VlcPlayerController 事件回调
    │
    ▼
PlayerViewModel
    │
    ├─ positionMs / durationMs / isPlaying / isBuffering ─► PlayerControls
    ├─ stats（PlaybackStats 快照）                      ─► 顶栏 / 信息层 / 菜单
    ├─ primary / secondary（SubtitleTrack）             ─► SubtitleOverlay + 菜单
    ├─ embeddedTracks / embeddedTrackStatus             ─► 字幕设置页
    ├─ playerError（PlaybackFailure?）                  ─► 全屏错误层
    └─ notice                                           ─► 非致命提示条（返回键可立即收掉）
```

> **组合期绝不调 JNI**：`PlaybackStats` 在 ticker 里预算好（每秒节流），组合期只读 StateFlow。
> 这条纪律是被"每次重组都调 libVLC、UI 线程被压死"教出来的。

---

## 6. 网络来源体系

### 核心抽象

```kotlin
interface RemoteBrowser {
    val rootPath: String
    suspend fun list(path: String): List<RemoteEntry>
    fun close()
}
```

所有来源实现同一接口，`RemoteBrowseScreen` 无需感知差异。

### 来源矩阵

| RemoteType | Browser | 播放 URI | 认证 |
|---|---|---|---|
| SMB | `SmbBrowser` + `SmbSession` | `smb://host/share/path` | 用户名/密码，`SmbLocationRegistry` 反查 |
| DLNA | `DlnaBrowser`（SSDP + SOAP） | `http://…`（直链） | 无 |
| QUARK | `QuarkBrowser` | `quark://fid` → 运行时解析 | Cookie（扫码） |
| BAIDU | `BaiduBrowser` | `baidu://fsid` → 运行时解析 | access + refresh token（扫码） |
| WEBDAV | `WebDavBrowser`（sardine） | `http(s)://…` | Basic Auth |
| ALI | `AliBrowser` | `ali://driveId/fileId` → 运行时解析 | OAuth token（扫码） |

### SMB 的两层自愈（**踩过两次，别合并成一层**）

| 症状 | 断在哪 | 怎么救 |
|---|---|---|
| `DiskShare has already been closed` | **共享句柄**失效（池被 `invalidate`、或发生过重连），连接还活着 | `reopenShare()`：只重建共享句柄 |
| `Cannot write Signed(SMB2_TREE_CONNECT …) as transport is disconnected` | **传输层**断了（NAS 主动断开 / 空闲超时 / 网络抖动） | `reconnect()`：整条连接作废（TCP + 认证 + 句柄）后重连 |

`SmbSession.withShareRecovery(label, operation)` 把两者串成"先轻后重"：
常态 → 失败则第 1 层 `reopenShare()` → 再失败则第 2 层 `reconnect()` 后重试最后一次。
三层都失败才抛错，且**文案取第 1 次的 cause**（后两次的失败常是重连本身的次生错误）。

`SmbMediaDataSource`（字幕提取用）的 `readAt` / `getSize` 也有自己的重开逻辑，
它重开时会走到 `SmbSession.openFile`，从而自动获得上面这两层恢复。

### 服务器发现与共享枚举

- **发现**（`SmbDiscovery`）：对当前网段的 445 端口做并发 TCP 探测（并发 32、超时 500ms），
  不依赖 NetBIOS / mDNS —— 这两者在家庭网络里常被路由器拦掉
- **枚举共享**：smbj 只暴露文件级 API，没有 SRVSVC 的 `NetShareEnumAll`；所以列举交给
  **jcifs-ng**（`SmbShareLister`），播放链路完全不碰它
- **兜底**（`SmbSession.probeShares`）：少数服务器组策略禁止枚举，此时退回逐个尝试 23 个
  常见共享名（覆盖群晖 / 威联通 / Windows 的惯用命名）

### DLNA

不引入 Cling（已 EOL）或 jUPnP（会带进 OSGi / javax 依赖），而是自己实现最小客户端：
SSDP 多播发现 → 解析设备描述拿 ContentDirectory 的 controlURL → SOAP `Browse` 列目录。

### 持久化：RemoteLocation

所有来源统一序列化为 `RemoteLocation`，字段按来源复用：

| 字段 | SMB | DLNA | 云盘 / WebDAV |
|---|---|---|---|
| `host` | 服务器地址 | SSDP 主机 | WebDAV URL（完整） |
| `share` | 共享名 | — | 阿里云盘 drive_id |
| `username` / `password` | SMB 凭据 | — | WebDAV 凭据 |
| `token` | — | — | 夸克 Cookie / 百度 / 阿里 access_token |
| `refreshToken` | — | — | 百度 / 阿里 refresh_token |
| `descriptionUrl` / `controlUrl` | — | SSDP LOCATION / ContentDirectory | — |

---

## 7. 字幕系统

### 数据结构层次

```
SubtitleSource（sealed interface）
├── None
├── ExternalFile(uri, displayName)
└── EmbeddedTrack(trackIndex, mimeType, language, label)
        │
        ▼
SubtitleTrack
├── source / cues / offsetMs / style / isLoading / error
        │
        ▼
SubtitleCue
├── startMs / endMs
├── text: String           ← **可能含 \n**（字幕组的语义断行原样保留）
└── assOverride: AssOverride?   ← SRT / VTT 恒为 null
```

> `EmbeddedTrack.trackIndex` 承载**两种语义**：文本轨存 Media3 索引，图片主字幕存 VLC spu id。
> 有条件判断与注释保证自洽，但有风险；将来若拆字段，建议 `media3Index` / `vlcSpuId` 分开。

### SubtitleCueIndex：二分查找

```
find(cues, positionMs):
  1. 二分找最后一个 startMs <= positionMs 的候选
  2. 向前最多 MAX_LOOKBACK=32 步扫描，条件 startMs <= positionMs <= endMs
  3. 返回第一个满足条件的 cue，或 null
```

向前回看是为了兼容少量重叠与轻微乱序（如 ASS 多行对白 `startMs` 相同时的排列）。

### 支持格式

| 格式 | 解析器 | 说明 |
|---|---|---|
| SRT | `SrtParser` | 容错序号缺失 / CRLF / 老式坐标后缀 / 内联 HTML |
| VTT | `VttParser` | 跳过 NOTE/STYLE/REGION，支持 cue settings 与 `<v>`/`<c>` |
| ASS / SSA | `AssParser` | `Format:` 声明切分字段，展开 `\N`，剥离 `{\…}` |
| 内嵌 ASS | `EmbeddedAssParser` | 从 MediaExtractor 码流重建 |
| 内嵌文字轨 | `EmbeddedSubtitleReader` | MediaExtractorCompat，支持 SMB 随机读 |
| 编码 | `SubtitleTextDecoder` | BOM → 严格 UTF-8 → GB18030 回退 |

### SubtitleStyle

两套预设（`PRIMARY` / `SECONDARY`）各自持有 `fontSizeSp` / `textColor` / `outlineColor` /
`outlineWidth` / `bottomPaddingDp` / `bold`，全局持久化（不按视频区分）。
另有 `KARAOKE_HIGHLIGHT_ARGB`：卡拉OK 未唱段的扫亮色 —— 属于**字幕渲染语义**而非 UI 主题，
所以放在这里而不是 `BeiGlass`。

---

## 8. 播放器层

### VlcPlayerController

```
VlcPlayerController
├── LibVLC 实例 + MediaPlayer + VLCVideoLayout
│
├── open(uri)             ← 设置 Media，启动播放
├── attachViews(layout)   ← 挂渲染容器
├── seekTo(ms) / setTime(ms, fast)   ← fast=false 走关键帧（用于长按预览）
├── setRate / setVolume / setAudioTrack / setSpuTrack / setSpuDelay
├── inputBytesPerSec()    ← 顶栏网速（input_bitrate / 8）
├── audioTracks / videoSizeText / streamStats …（供 PlaybackStats 快照）
└── 事件回调（Coroutine Channel）：Playing / Paused / Stopped / TimeChanged / Buffering / EncounteredError
```

**音轨自动选择**：起播后按"能解的优先"挑一条挂上；若原本没挂上就自动挂最优。
这个自动行为**只写日志、不弹提示** —— 用户既没要求、也不需要知道（真机反馈被提示条打扰过）。

> 排查音频问题时把 `--verbose` 临时调回 2，才能在 logcat 看到
> `audio output: using module …` 这类关键行。

### 长按快进预览（seekPreview / resumeFromPreview）

```
长按方向键（repeat > 0）
    ▼
seekPreview(deltaMs)：首次记录 _wasPlayingBeforePreview 并暂停，随后 setTime(target, fast=false)
松键（KeyUp）或 300ms 无 KeyDown
    ▼
resumeFromPreview()：原先在播就 play()
```

KeyUp 两路触发：`onPreviewKeyEvent` 立即恢复 + 300ms 定时器兜底（蓝牙遥控器可能不上报 KeyUp）。

### PlaybackStats 快照

`startTicker()` 每 50ms 一跳；在 ticker 协程里调 JNI 后写入 `_stats: StateFlow<PlaybackStats>`。
**组合期只读它**。网速是**每秒节流**刷新的（`SPEED_POLL_INTERVAL_MS`），不要改成每跳都取 ——
那是每秒几十次 JNI。

### PlaybackFailure 结构化错误

```kotlin
data class PlaybackFailure(
    val title: String,        // 结论，如「无法播放此文件」
    val reason: String,       // 原因，人话
    val suggestion: String?,  // 建议操作
    val detail: String,       // 原始异常 + 容器 + 轨道信息（小字）
    val mediaInfo: String?    // libVLC 返回的媒体信息
)
```

---

## 9. 持久化

`SettingsStore`（DataStore Preferences，文件 `dualsub_settings`）。

| Key | 内容 |
|---|---|
| `primary_style_*` / `secondary_style_*` | 字幕默认样式（各 6 个字段） |
| `primary_source{videoKey}` / `secondary_source{videoKey}` | 该片两路字幕的选择 |
| `remote_locations` | 所有 `RemoteLocation`（分隔符拼串） |
| `ai_config` | AI 字幕：baseUrl / apiKey / model / targetLang / prompt / 实时窗口 / 超前缓冲 |
| `video_caching_ms` | 起播前的预取缓冲 |

- **videoKey**：视频 URI 的字符串形式，用不会出现在 URI 里的 Unit Separator 拼字段
- **播放进度**：**不落盘**（旧版曾落盘，init 块会在首次启动时清理遗留 key）
- **密码 / API Key**：明文存于应用私有目录（与 Kodi 行为一致）。介意的话用只读的访客账号

---

## 10. UI 层

### 视觉规范

**全部集中在 `docs/UI_STYLE_REFERENCE.md`** —— 配色 token、尺寸档位、焦点态规则、
覆盖层与返回键层序都在那里，本节只讲结构。

### 页面结构

```
AppRoot（状态机）
│
├── AppShell（左侧导航 + 内容区；播放页不进外壳）
│   ├── ShellTab.Local    → LibraryScreen
│   ├── ShellTab.Network  → NetworkScreen
│   │     ├── LocalNetworkScreen（本地网络子页：进入即扫描 + 「＋ 手动配置」）
│   │     ├── SmbServerForm / WebDavServerForm
│   │     ├── Quark / Baidu / AliLoginScreen
│   │     └── RemoteBrowseScreen（通用目录浏览）
│   └── ShellTab.Settings → SettingsScreen
│         └── AiSettingsScreen（二级页）
│
└── PlayerScreen（整屏，不走外壳）
    ├── VLCVideoLayout（视频 + 图片字幕）
    ├── SubtitleOverlay ×2（主 / 次字幕文本）
    ├── PlayerStatusBar / PlayerControls
    ├── PlayerMenuOverlay / PlayerChoicePickerOverlay
    ├── PlayerInfoOverlay / AiSubtitleProgressOverlay
    └── PlayerExitConfirmOverlay
```

### 播放页键位映射

| 按键 | 行为 |
|---|---|
| OK / Dpad Center | 播放 / 暂停，并保证控制条可见 |
| 左 / 右（单击） | 快退 / 快进 10s |
| 左 / 右（长按） | seekPreview（关键帧预览） |
| 上 / 下 | 开关信息层 |
| Menu / Info | 开关侧边菜单（厂商遥控器这两种键码都有，所以都接） |
| MediaPlayPause / Play / Pause | 播放 / 暂停 |
| **返回** | **逐层退出**，见下 |

**返回键层序**（由内到外，没别的层了才问退出）：

```
退出确认框 → AI 配置层 → 整页选择页 → 数值激活态 → 菜单二级 → 菜单一级
          → 提示条 → 控制条 → 退出确认框（默认高亮「取消」）
```

两条规则写在 `UI_STYLE_REFERENCE.md` §2.5：**覆盖层互斥不叠透明层**、
**破坏性操作要确认**。

### Compose TV 注意事项

- 用 `androidx.tv.material3` 的 `Surface` / `Text`；**`Surface` 的 `border` 参数要它自家的
  `Border` 类型**（不是 foundation 的 `BorderStroke`），传错会连带报一个位置误导的
  `@Composable` 错误 —— 本项目改为把描边叠在 content 里画，彻底绕开
- 点击件用 `Surface(onClick = …)` + `ClickableSurfaceDefaults`；静态件直接用 foundation
- **焦点态自己管**：`onFocusChanged` 维护 `focused`，底色 / 竖线 / 描边都从它来
  （`Surface` 的 `focusedContainerColor` 与真实焦点不同步，踩过坑）
- `BasicTextField` 替代 `OutlinedTextField`（tv-material3 没有后者）
- 播放页整屏自管按键：根节点用 `focusRequester` + `focusable` 显式持有焦点，
  否则 `onPreviewKeyEvent` 收不到任何键（表现为"只有返回键能用"）
- **不用 emoji 当图标**（不同电视字形覆盖不同，缺字就是方框）；全部自绘 `Canvas`

---

## 11. 扩展指南：新增网络来源

以"再加一个网盘"为例：

1. **`RemoteEntry.kt`** — 加枚举值 `NEW_DRIVE("新网盘")`
2. **新建 `network/webdrive/NewDriveXxx.kt`** — `ApiClient`（OkHttp）/ `AuthManager`
   （`StateFlow<AuthState>` + 扫码 + token 刷新）/ `Browser`（实现 `RemoteBrowser`）
3. **`RemoteBrowserFactory.kt`** — 加 when 分支
4. **`AppServices.kt`** — 注册 AuthManager
5. **`DualSubDataSourceFactory.kt`** — 加 scheme 分支（把 `newdrive://` 解析成直链）
6. **`ui/network/NetworkScreen.kt`** — 加一张 `BeiIconCard` + 在 `when (form)` 里挂上登录页

**无需改动**：`RemoteBrowseScreen`（通用浏览，不感知来源）、字幕系统（与来源无关）、
`SettingsStore`（`token` / `refreshToken` / `share` 字段已预留）。
