# DualSub TV — 架构文档

> 版本：2026-09-18  
> 适用代码基线：code review 清理完成后的当前主分支

---

## 目录

1. [产品定位](#1-产品定位)
2. [技术栈总览](#2-技术栈总览)
3. [模块结构](#3-模块结构)
4. [核心架构决策](#4-核心架构决策)
   - 4.1 播放引擎：为何从 Media3 换到 libVLC
   - 4.2 双字幕分工
   - 4.3 手写 DI 容器
   - 4.4 导航：状态机而非 Navigation 组件
5. [数据流](#5-数据流)
6. [网络来源体系](#6-网络来源体系)
7. [字幕系统](#7-字幕系统)
8. [播放器层](#8-播放器层)
9. [持久化](#9-持久化)
10. [UI 层](#10-ui-层)
11. [扩展指南：新增网络来源](#11-扩展指南新增网络来源)

---

## 1. 产品定位

Android TV / 大屏电视播放器，核心功能是同时渲染**两路相互独立的字幕**（主字幕 + 次字幕）。典型场景：看外语片时主字幕显示母语、次字幕显示目标语言，方便对照学习。

支持的视频来源：本地存储、SMB（NAS）、DLNA、夸克网盘、百度网盘、WebDAV（AList/Nextcloud 等）、阿里云盘。

---

## 2. 技术栈总览

| 类别 | 库 / 框架 | 版本 |
|---|---|---|
| 语言 | Kotlin | 2.0.21 |
| 构建 | AGP | 8.7.3 |
| compileSdk / minSdk | — | 35 / 23 |
| UI | Compose for TV (`tv-material` / `tv-foundation`) | 1.0.0 |
| 播放引擎 | libVLC | 3.6.5 |
| Media3（辅助） | ExoPlayer / Common | 1.6.0 |
| FFmpeg 软解扩展 | lib-decoder-ffmpeg（本地 AAR） | — |
| SMB 浏览/播放 | smbj | 0.13.0 |
| SMB 列举共享 | jcifs-ng | 2.1.10 |
| NTLM 加密 | bcprov-jdk15to18 | 1.78.1 |
| 网盘 HTTP 客户端 | OkHttp | 4.12.0 |
| WebDAV 客户端 | sardine-android (JitPack) | 0.9 |
| 二维码生成 | ZXing Core | 3.5.3 |
| 持久化 | DataStore Preferences | 1.1.1 |
| 协程 | kotlinx-coroutines-android | 1.9.0 |
| DI | 手写 `AppServices` 容器 | — |

**编译 JDK 要求**：当前构建机上的 JDK 25（SapMachine）与 Kotlin 编译器不兼容，需显式指定 JDK 17：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  bash gradlew :app:compileDebugKotlin
```

---

## 3. 模块结构

```
app/src/main/java/com/dualsub/tv/
│
├── DualSubApp.kt            # Application，启动 InAppLog
├── MainActivity.kt          # 单 Activity，渲染 AppRoot
│
├── core/
│   ├── AppServices.kt       # 手写 DI 容器（Application 级单例）
│   └── InAppLog.kt          # 内存环形日志，TV 上取代 Logcat
│
├── data/
│   └── SettingsStore.kt     # DataStore 持久化（字幕样式、字幕选择、网络位置）
│
├── media/                   # 本地媒体相关
│   ├── EmbeddedSubtitleReader.kt   # 用 MediaExtractor 提取内嵌字幕轨
│   ├── LocalMediaDataSource.kt     # content:// / file:// 随机读
│   ├── MediaLibraryScanner.kt      # 扫描本地视频库
│   ├── RandomAccessExtractorInput.kt
│   ├── SparseMatroskaReader.kt     # 快速读 MKV 附件（内嵌字体）
│   └── VideoItem.kt                # 视频元数据（URI、标题、时长）
│
├── network/                 # 网络浏览与数据源路由
│   ├── RemoteEntry.kt       # RemoteType 枚举 + RemoteLocation + RemoteEntry
│   ├── RemoteBrowser.kt     # 统一浏览接口（list / rootPath / close）
│   ├── RemoteBrowserFactory.kt     # 按 RemoteType 构造对应 Browser
│   ├── DualSubDataSourceFactory.kt # Media3 DataSource.Factory，按 scheme 分发
│   ├── MediaSourceProvider.kt      # 按 URI scheme 选随机读数据源（字幕提取用）
│   ├── dlna/
│   │   ├── DlnaBrowser.kt          # SSDP 发现 + SOAP Browse
│   │   └── DlnaMessages.kt         # SSDP/SOAP 报文构造
│   ├── smb/
│   │   ├── SmbBrowser.kt           # SMB 目录浏览
│   │   ├── SmbDataSource.kt        # Media3 DataSource，SMB 随机读
│   │   ├── SmbMediaDataSource.kt   # 字幕提取用的 SMB 随机读
│   │   ├── SmbDiscovery.kt         # 局域网 SMB 服务器扫描
│   │   ├── SmbShareLister.kt       # 列举服务器上的共享（jcifs-ng）
│   │   ├── SmbSession.kt           # 单台服务器的连接会话
│   │   ├── SmbSessionPool.kt       # 按主机复用连接（seek 时不重连）
│   │   ├── SmbPaths.kt             # SMB URI 编解码工具
│   │   ├── SmbSecurity.kt          # BouncyCastle NTLM 注入
│   │   ├── SmbLocationRegistry.kt  # 播放器按 host 反查凭据
│   │   ├── ReadFullyAt.kt          # SMB 随机读辅助
│   │   └── SubnetHosts.kt          # 子网主机枚举（SMB 扫描）
│   └── webdrive/            # 云网盘
│       ├── QuarkApiClient.kt / QuarkAuthManager.kt / QuarkBrowser.kt
│       ├── BaiduApiClient.kt / BaiduAuthManager.kt / BaiduBrowser.kt
│       ├── AliApiClient.kt / AliAuthManager.kt / AliBrowser.kt
│       └── WebDavBrowser.kt # sardine-android，Basic Auth
│
├── player/                  # 播放引擎封装与字幕数据结构
│   ├── VlcPlayerController.kt      # libVLC 封装（为何换引擎见 §4.1）
│   ├── SubtitleTrack.kt            # SubtitleSource（sealed）+ SubtitleTrack 数据类
│   ├── SubtitleCueIndex.kt         # 二分查找当前 cue
│   ├── SubtitleStyle.kt            # 字体/颜色/描边/位置样式
│   ├── EmbeddedCueCache.kt         # 内嵌字幕轨解码结果缓存
│   └── (已删) PlayerController.kt  # ExoPlayer 封装，迁移 libVLC 后已移除
│
├── subtitle/                # 字幕解析器
│   ├── SrtParser.kt / VttParser.kt / AssParser.kt
│   ├── EmbeddedAssParser.kt        # 从 MediaExtractor 读出的 ASS 流解析
│   ├── AssOverride.kt              # ASS override tag 解析（颜色/字体/位置）
│   ├── SubtitleCue.kt              # startMs / endMs / text
│   ├── SubtitleFormat.kt           # 格式枚举
│   ├── SubtitleParser.kt           # 解析器接口
│   ├── SubtitleParsers.kt          # 按扩展名/MIME 分发
│   ├── SubtitleTextDecoder.kt      # BOM 识别 + GBK 容错
│   └── SubtitleTime.kt             # 时间戳字符串解析
│
└── ui/
    ├── AppRoot.kt                  # 顶层导航状态机（不使用 Navigation 组件）
    ├── format/TimeFormat.kt        # formatTime(ms) 共享函数
    ├── theme/                      # Compose 主题（TV 大屏配色）
    ├── library/LibraryScreen.kt    # 本地视频库浏览
    ├── network/
    │   ├── NetworkScreen.kt        # 网络来源管理（SMB/DLNA/云盘入口）
    │   ├── RemoteBrowseScreen.kt   # 通用远程目录浏览（所有来源复用）
    │   ├── WebDavServerForm.kt     # WebDAV 服务器添加/编辑表单
    │   ├── QuarkLoginScreen.kt     # 夸克扫码登录
    │   ├── BaiduLoginScreen.kt     # 百度扫码登录
    │   └── AliLoginScreen.kt       # 阿里云盘扫码登录
    └── player/
        ├── PlayerScreen.kt         # 播放主界面（键盘/遥控处理、层叠组合）
        ├── PlayerViewModel.kt      # 播放状态持有，StateFlow 驱动 UI
        └── components/
            ├── PlayerControls.kt   # 进度条 + 时间 + 播放按钮
            ├── PlayerMenuOverlay.kt # 右侧滑出菜单（字幕/音轨/倍速/样式）
            ├── PlayerInfoOverlay.kt # 左上角详情层（按上下键呼出）
            └── SubtitleOverlay.kt  # 次字幕自绘层（Canvas + Compose）
```

---

## 4. 核心架构决策

### 4.1 播放引擎：为何从 Media3 换到 libVLC

**起因**：Media3 无法播放 RM / RMVB。Media3 的 FFmpeg 扩展（`decoder_ffmpeg`）只提供解码器、不解析容器，而没有任何电视芯片能硬解 RealVideo，软解则重新卡死。libVLC 自带完整 FFmpeg，容器解析与软解码两样都有。

**影响范围**：
- 播放引擎从 `PlayerController`（ExoPlayer 封装）换为 `VlcPlayerController`（libVLC 封装）
- Media3 库保留，但仅用于 `SmbDataSource` / `DualSubDataSourceFactory` 等周边（字幕提取、数据源接口）
- libVLC 3.x（非 4.x EAP），通过 `SurfaceView` 渲染视频

**DTS / TrueHD / EAC3**：本地引入 `lib-decoder-ffmpeg-release.aar` 提供 FFmpeg 软解扩展，补充芯片不支持的音频格式。

### 4.2 双字幕分工

```
┌───────────────────────────────┐
│         VLCVideoLayout        │  ← libVLC 管理的视图层
│   ┌───────────────────────┐   │
│   │   视频 SurfaceView     │   │
│   ├───────────────────────┤   │
│   │  主字幕 SubtitlesSurface│  │  ← libVLC 内建 libass 渲染
│   └───────────────────────┘   │
└───────────────────────────────┘
           ↑ 嵌入 Compose
┌───────────────────────────────┐
│       SubtitleOverlay         │  ← Compose Canvas 自绘
│    （次字幕 + ASS override）   │
└───────────────────────────────┘
```

**主字幕**（内嵌 / 外挂 ASS、SRT、图片字幕）：交给 libVLC 的 libass 渲染层。优势：ASS 特效/定位/卡拉OK、容器内嵌字体提取、PGS/DVD SPU 图片字幕，自绘层无法实现。

**次字幕**（外挂文字字幕）：Compose `SubtitleOverlay` 自绘。`PlayerViewModel` 在 50ms ticker 中调 `SubtitleCueIndex.find()` 二分查找当前 cue，通过 `StateFlow<SubtitleCue?>` 推送给 UI。

**结果**：两路字幕完全独立。主字幕切到内嵌 ASS，次字幕外挂 SRT，互不干扰。

### 4.3 手写 DI 容器

`AppServices` 是 Application 级单例，在 `MainActivity` 构造一次，通过 `ViewModel` 工厂传入各页面。规模不需要 Hilt，手写更透明：所有长生命周期依赖的构造顺序和持有关系一目了然。

### 4.4 导航：状态机而非 Navigation 组件

`AppRoot` 用 `sealed class Screen` + `mutableStateOf<Screen>` 实现页面切换。好处：TV 遥控的 Back 键处理更直接，不需要处理 Navigation 的 back stack；无需 parcelable 传参，`VideoItem` 等对象直接传引用。

---

## 5. 数据流

### 视频播放流

```
用户选择视频（RemoteEntry.playableUri）
    │
    ▼
PlayerViewModel.restoreAndOpen(video)
    │
    ├─ 读取 SettingsStore：上次选的字幕 source
    │
    ▼
VlcPlayerController.open(uri)
    │
    ├─ scheme = smb://   ──► SmbDataSource（Media3，随机读）
    ├─ scheme = quark://  ──► runBlocking → QuarkApiClient.getDownloadUrl()
    ├─ scheme = baidu://  ──► runBlocking → BaiduApiClient.getDownloadUrl()
    ├─ scheme = ali://    ──► runBlocking → AliApiClient.getTranscodingUrl() → 降级 getDownloadUrl()
    ├─ scheme = http://   ──► DefaultDataSource（DLNA 直接播）
    └─ scheme = content:/ ──► DefaultDataSource
         │
         ▼
    libVLC 接管渲染
```

> 注：`DualSubDataSourceFactory` 在 SMB 字幕提取路径上使用。主播放由 `VlcPlayerController` 直接接管 URI，不经过 Media3 的数据源管线。

### 字幕加载流

```
PlayerViewModel.loadSubtitle(slot, source)
    │
    ├─ ExternalFile(uri) ─► 读取文件字节 ─► SubtitleParsers.parse()
    │                                         │
    ├─ EmbeddedTrack     ─► EmbeddedSubtitleReader ─► MediaExtractor
    │                         └─ SMB 用 SmbMediaDataSource
    │                         └─ local 用 LocalMediaDataSource
    │
    ▼
SubtitleTrack(cues = List<SubtitleCue>, style, offsetMs)
    │
    ▼
_primary / _secondary StateFlow
    │
    ▼
50ms ticker → SubtitleCueIndex.find(cues, positionMs - offsetMs)
    │
    ├─ 主字幕：VlcPlayerController 加载字幕文件（libass 渲染）
    └─ 次字幕：_secondaryCue StateFlow → SubtitleOverlay 自绘
```

### UI 状态流

```
VlcPlayerController 事件回调
    │
    ▼
PlayerViewModel（ViewModel）
    │
    ├─ positionMs / durationMs / isPlaying / isBuffering  ─► PlayerControls
    ├─ stats（PlaybackStats）                              ─► PlayerInfoOverlay / PlayerMenuOverlay
    ├─ primary / secondary（SubtitleTrack）                ─► SubtitleOverlay + PlayerMenuOverlay
    ├─ playerError（PlaybackFailure?）                     ─► 全屏错误遮罩
    └─ notice                                              ─► 非致命提示文字

注：PlaybackStats 在 ticker 里预算好（调 JNI），组合期只读 StateFlow —— 防止重组时大量 JNI 卡死 UI 线程。
```

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

所有来源（SMB / DLNA / 云盘）实现同一接口，`RemoteBrowseScreen` 无需感知差异。

### 来源矩阵

| RemoteType | Browser 实现 | 播放 URI | 认证方式 |
|---|---|---|---|
| SMB | `SmbBrowser` + `SmbSession` | `smb://host/share/path` | 用户名/密码，SmbLocationRegistry 反查 |
| DLNA | `DlnaBrowser`（SSDP+SOAP） | `http://...`（直链） | 无 |
| QUARK | `QuarkBrowser` | `quark://fid` → 运行时解析为直链 | Cookie（扫码获取） |
| BAIDU | `BaiduBrowser` | `baidu://fsid` → 运行时解析为直链 | access_token + refresh_token（扫码） |
| WEBDAV | `WebDavBrowser`（sardine-android） | `http(s)://...`（href 即直链） | Basic Auth（用户名/密码） |
| ALI | `AliBrowser` | `ali://driveId/fileId` → 运行时解析 | OAuth access_token（扫码） |

### 云盘播放链路

```
ali://driveId/fileId
    │
    ▼
DualSubDataSourceFactory.open()
    │
    ├─ 优先：AliApiClient.getTranscodingUrl()（转码流，无限速）
    │
    └─ 降级：AliApiClient.getDownloadUrl()
              ├─ 若 AliTokenExpiredException → tryRefreshToken() → 重试
              └─ 注入 Referer: https://www.aliyundrive.com/
```

百度类似，注入 `User-Agent` + `Authorization: Bearer` 头。夸克走 Cookie。

### 持久化：RemoteLocation

所有来源统一序列化为 `RemoteLocation`，字段按来源复用：

| 字段 | SMB | DLNA | 云盘 |
|---|---|---|---|
| `host` | 服务器地址 | SSDP 主机 | WebDAV URL（完整） |
| `share` | 共享名 | — | 阿里云盘 drive_id |
| `username/password` | SMB 凭据 | — | WebDAV 凭据 |
| `token` | — | — | 夸克 Cookie / 百度/阿里 access_token |
| `refreshToken` | — | — | 百度/阿里 refresh_token |
| `descriptionUrl` | — | SSDP LOCATION | — |
| `controlUrl` | — | ContentDirectory URL | — |

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
├── source: SubtitleSource
├── cues: List<SubtitleCue>        ← 解析后按 startMs 升序排列
├── offsetMs: Long                 ← 用户可调时间偏移
├── style: SubtitleStyle           ← 字体/颜色/描边/底部间距
├── isLoading: Boolean
└── error: String?
        │
        ▼
SubtitleCue
├── startMs / endMs
└── text: String                   ← 已剥离格式标签（ASS override 按需保留）
```

### SubtitleCueIndex：二分查找

```
find(cues, positionMs):
  1. 二分找最后一个 startMs <= positionMs 的候选
  2. 向前最多 MAX_LOOKBACK=32 步扫描
     条件：startMs <= positionMs <= endMs
  3. 返回第一个满足条件的 cue，或 null
```

向前回看设计：兼容少量重叠 cue 和轻微乱序（如 ASS 多行对白 startMs 相同时的排列）。

### 支持格式

| 格式 | 解析器 | 说明 |
|---|---|---|
| SRT | `SrtParser` | 带 BOM / GBK 容错 |
| VTT | `VttParser` | 支持 cue settings（position/line） |
| ASS / SSA | `AssParser` | override tag 解析（颜色/字体/位置/粗斜体） |
| 内嵌 ASS | `EmbeddedAssParser` | 从 MediaExtractor 码流重建 |
| 内嵌文字轨 | `EmbeddedSubtitleReader` | MediaExtractor，支持 SMB 随机读 |

### SubtitleStyle

两套预设（`PRIMARY` / `SECONDARY`）各自持有：`fontSizeSp`、`textColor`、`outlineColor`、`outlineWidth`、`bottomPaddingDp`、`bold`。全局持久化，不按视频区分。

---

## 8. 播放器层

### VlcPlayerController

```
VlcPlayerController
├── LibVLC 实例（传入 --vout=android_window 等选项）
├── MediaPlayer
├── VLCVideoLayout（渲染容器，含 SubtitlesSurfaceView）
│
├── open(uri)         ← 设置 Media，启动播放
├── attachViews(layout, enableSubtitles)
├── seekTo(ms)        ← 精确跳转
├── setTime(ms, fast) ← trick mode seek（fast=false 走关键帧）
├── setRate(rate)
├── setVolume(percent)
├── setAudioTrack(id)
├── addSubtitleFile(uri) ← 加载外挂字幕给 libVLC
│
└── 事件回调（Coroutine Channel）
    ├── Playing / Paused / Stopped
    ├── TimeChanged(ms)
    ├── Buffering(percent)
    └── EncounteredError
```

### seekPreview / resumeFromPreview（长按快进预览）

```
长按方向键（repeat > 0）
    │
    ▼
PlayerViewModel.seekPreview(deltaMs)
    │
    ├─ 首次调用：记录 _wasPlayingBeforePreview，暂停播放
    │
    └─ controller.setTime(targetMs, fast=false)
       （关键帧 seek，libVLC 解码并渲染该帧，视觉上即预览画面）

松键（KeyUp）或 300ms 无 KeyDown
    │
    ▼
PlayerViewModel.resumeFromPreview()
    │
    └─ 若 _wasPlayingBeforePreview → controller.play()
```

KeyUp 两路触发机制：`onPreviewKeyEvent` 捕获 `KeyUp` 立即恢复；300ms coroutine timer 作为蓝牙遥控器不上报 KeyUp 时的兜底。

### PlaybackStats 快照防抖

`startTicker()` 每 50ms 触发一次，在 ticker 协程里调 JNI（`audioTrackOptions`、`videoSizeText`、`streamStats` 等）后写入 `_stats: MutableStateFlow<PlaybackStats>`。Compose 组合期只读 StateFlow，不直接碰 JNI，防止每秒数十次 JNI 压死 UI 线程。

### PlaybackFailure 结构化错误

```kotlin
data class PlaybackFailure(
    val title: String,          // 结论，如「无法播放此文件」
    val reason: String,         // 原因，如「找不到支持的解码器」
    val suggestion: String?,    // 建议操作（可 null）
    val detail: String,         // 原始异常 + 容器 + 轨道信息
    val mediaInfo: String?      // libVLC 返回的媒体信息
)
```

---

## 9. 持久化

`SettingsStore`（DataStore Preferences），全部存入 `dualsub_settings`。

| Key 规则 | 内容 |
|---|---|
| `primary_style_*` / `secondary_style_*` | 字幕样式（字体/颜色等 6 个字段） |
| `primary_source{videoKey}` | 该片主字幕选择（ExternalFile / EmbeddedTrack / None） |
| `secondary_source{videoKey}` | 该片次字幕选择 |
| `remote_locations` | 所有 RemoteLocation，`` 分隔多条，每条内 `` 分隔字段 |

**videoKey**：视频 URI 的字符串形式，用 Unit Separator `` 作字段分隔符（不会出现在 URI 里）。

**播放进度**：不落盘（旧版曾落盘，init 块会在首次启动时清理遗留 key）。

**密码存储**：明文存于应用私有目录（与 Kodi 行为一致）。如需加密，可接入 Android Keystore，但当前优先级不高。

---

## 10. UI 层

### 页面结构

```
AppRoot（状态机）
│
├── LibraryScreen       # 本地视频库（MediaLibraryScanner 扫描）
│
├── NetworkScreen       # 网络来源管理
│   ├── SMB 服务器列表 + 扫描
│   ├── DLNA 发现
│   ├── WebDAV 表单（WebDavServerForm）
│   └── 云盘入口（扫码登录 / 已登录状态）
│
├── RemoteBrowseScreen  # 通用目录浏览（所有来源复用）
│   └── 上/下导航，OK 键进入或播放
│
└── PlayerScreen        # 播放器主界面
    ├── VLCVideoLayout（视频+主字幕）
    ├── SubtitleOverlay（次字幕）
    ├── PlayerControls（进度条，按 OK/方向键显示）
    ├── PlayerMenuOverlay（右侧菜单，按 Menu 键）
    └── PlayerInfoOverlay（左上详情层，按上/下键）
```

### PlayerScreen 键位映射

| 按键 | 行为 |
|---|---|
| OK / Dpad Center | 暂停/恢复，显示控件层 |
| 左 / 右（单击） | 快退/快进 10s，显示控件层 |
| 左 / 右（长按，repeat>0） | seekPreview（关键帧预览） |
| 上 / 下 | 切换 PlayerInfoOverlay 开关 |
| Menu / Settings | 打开右侧菜单 |
| 返回 | 退出播放器 |
| 媒体键 FastForward / Rewind | 快进/快退 |

### Compose TV 注意事项

- 使用 `androidx.tv.material3` 组件（`Button`、`Surface`、`Text` 等），不使用 Material 3
- `BasicTextField` 替代 `OutlinedTextField`（TV Material3 无 `OutlinedTextField`）
- 焦点管理依赖 `FocusRequester` + `Modifier.focusTarget()`，TV 上无触摸，焦点即光标
- 动画使用 `AnimatedVisibility` + `Crossfade`，避免非 TV 环境常用的触摸手势动画

---

## 11. 扩展指南：新增网络来源

以"再加一个网盘"为例，最少需要改动以下文件：

### 1. `RemoteEntry.kt` — 加枚举值

```kotlin
enum class RemoteType(val displayName: String) {
    // ... 现有值
    NEW_DRIVE("新网盘")
}
```

### 2. 新建 `network/webdrive/NewDriveXxx.kt`

- `NewDriveApiClient`：HTTP 请求（OkHttp），实现登录、文件列表、下载/播放链接获取
- `NewDriveAuthManager`：状态机（`StateFlow<AuthState>`），扫码登录 + token 刷新
- `NewDriveBrowser`：实现 `RemoteBrowser`，调 `apiClient().listFiles()`

### 3. `RemoteBrowserFactory.kt` — 加 when 分支

```kotlin
RemoteType.NEW_DRIVE -> NewDriveBrowser(newDriveAuth)
```

### 4. `AppServices.kt` — 注册 AuthManager

```kotlin
val newDriveAuth = NewDriveAuthManager()
val browserFactory = RemoteBrowserFactory(..., newDriveAuth)
```

### 5. `DualSubDataSourceFactory.kt` — 加 scheme 分支

```kotlin
"newdrive" -> {
    val resolved = runBlocking { newDriveAuth.apiClient().getDownloadUrl(fileId) }
    dataSpec.buildUpon().setUri(Uri.parse(resolved)).build()
}
```

### 6. `ui/network/NetworkScreen.kt` — 加入口按钮与登录页

参考 `AliLoginScreen` / `QuarkLoginScreen` 实现扫码页，在 `NetworkScreen` 的 `when(form)` 分支里挂上。

### 无需改动

- `RemoteBrowseScreen`：通用浏览，不感知来源类型
- `SubtitleOverlay` / 字幕系统：与来源无关
- `SettingsStore`：`token` / `refreshToken` / `share` 字段已预留，无需新增序列化字段
