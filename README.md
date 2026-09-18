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
- 片源与本地视频走同一个播放器，双字幕功能一视同仁

**播放器**
- **libVLC 播放**（内置完整 FFmpeg，含 RealMedia 容器解析），硬解优先、解不动自动回退软解
- 自绘控制条：播放/暂停、±10s、进度与时间
- 播放进度仅在当前播放页保留；退出或换片后从头播放，首次打开会清理旧版进度记录

**双字幕**（机制详见下一节）
- 主字幕、次字幕各一个独立状态（来源 / 时间偏移 / 样式）
- **主字幕由 libVLC 的 libass 渲染** → 完整 ASS 特效、容器内嵌字体、图片字幕（PGS / DVD SPU / 蓝光）
- **次字幕由 Compose 自绘** → 字号 / 底部距离 / 描边 / 时间偏移都能单独调
- 字幕来源：
  - 视频**内嵌字幕轨**（主字幕交给 libVLC 选轨；次字幕用 Media3 读原始 cues）
  - **外挂字幕文件**（系统文件选择器，`srt` / `vtt` / `ass` / `ssa`，读取权限持久化）
- 每个视频各自记住「上次用的哪两路字幕」

**字幕解析**（`com.dualsub.tv.subtitle`，纯 Kotlin，不依赖 Android 框架与 media3）
- `SrtParser`：容错序号缺失、CRLF、老式坐标后缀、内联 HTML 标签
- `VttParser`：跳过 `NOTE`/`STYLE`/`REGION` 块，支持 cue settings、`MM:SS.mmm`、`<v>`/`<c>` 标记
- `AssParser`：按 `Format:` 声明切分字段（`Text` 里的逗号不会被截断），展开 `\N`，剥离 `{\...}` 覆盖指令
- `SubtitleTextDecoder`：BOM → 严格 UTF-8 → GB18030 回退，处理中文圈常见的无 BOM GBK 字幕

---

## 双字幕是怎么做到的

**两路字幕走两条完全不同的链路** —— 这是本项目的核心设计：

| | 主字幕 | 次字幕 |
|---|---|---|
| 渲染者 | **libVLC**（内建 libass） | 应用自己（Compose 叠加层） |
| 画在哪 | `VLCVideoLayout` 内部 | Compose 层，位于主字幕**之上** |
| 能拿到 | 完整 ASS 特效 / 定位 / 卡拉 OK、**容器内嵌字体**、**图片字幕**（PGS / DVD SPU / 蓝光） | 只有文本，但字号 / 底部距离 / 描边 / 时间偏移**都能独立调** |
| 拿不到 | 字号 / 位置 / 描边不可调（由片源特效字幕决定） | 图片字幕、完整 ASS 排版 |

为什么这样分工：**特效字幕（ASS）只有 libass 渲染得对**，自绘层要追上 libass 是个无底洞；
反过来次字幕通常只要「多显示一行字」，交给自绘反而换来完全可控的样式与位置。

具体做法：

1. **主字幕交给 libVLC** —— `VlcPlayerController.attachViews(layout, null, **true**, false)`
   打开字幕 Surface 之后，VLC 会自己在 `VLCVideoLayout` 里挂一层 `SubtitlesSurfaceView` 渲染字幕。
   选轨走 `MediaPlayer.setSpuTrack(id)`，时间偏移走 `setSpuDelay(ms)`（**这个可以动态调**）；
   外挂字幕文件走 `addSlave(Type.Subtitle, path, …)`，**必须是本地路径**
   （SAF 给的 `content://` libVLC 不认，要先落到 cacheDir）。

   > ⚠️ **`id` 必须取自 `MediaPlayer.spuTracks`。** `IMedia.SubtitleTrack.id` 是**另一套编号**，
   > 拿它去 `setSpuTrack()` 会直接失败 —— 真机现象是「libVLC 选不中该字幕轨」，
   > 而 VLC 会退回它自己自动选的那条，在多字幕轨的片源上就表现为「字幕内容不对/像乱码」。
   > 音轨那边 `IMedia.Track.id` 与 `TrackDescription.id` 不同源，是同一个坑。

2. **次字幕仍由自绘链路处理** —— MKV 用 Media3 `MatroskaExtractor` 配合随机读输入，
   其他容器用 `MediaExtractorCompat`；内嵌 ASS/SSA 复用项目自己的 `AssParser`，
   其余按 MIME 交给 `CueDecoder`。本地与 SMB 共用随机读接口，SMB 短读会继续补齐。

3. **同步** —— 一条 50ms 的 ticker 按 `player.currentPosition` 对次字幕做二分查找定位
   （`SubtitleCueIndex`），叠加它自己的时间偏移；主字幕的偏移由 libVLC 自己算。

4. **两路避让靠位置错开、不靠层级** —— 次字幕画在 Compose 层、天然盖在主字幕之上，
   所以它用 `bottomPaddingDp` 留一个**固定余量**（默认 112dp，约两行主字幕加一条间隙）
   来避开主字幕。主字幕特别高（三行以上或 `\pos` 到画面中部）时，到字幕设置里把这个值调大。

MKV 先读轨道元数据，再按 Cues 索引读取播放位置附近的字幕窗口；跳过未选中轨道的音视频负载，
读到字幕即交付显示，无需等整个窗口扫描完成；最多缓存四个窗口。快进、切换来源和离开播放页会取消旧任务，旧结果不会覆盖新选择。
无 Cues 索引的 MKV 暂不支持跳转后的字幕窗口，界面会显示错误；跨越窗口起点之前很久的长字幕可能缺失。
播放位置每 50ms 检查一次，在当前窗口结束前 15 秒请求下一窗口；每次按 30 秒分桶读取前 30 秒到后 60 秒。
超时和读取失败会显示状态，菜单可重新读取字幕轨。

字幕回归：`testDebugUnitTest` 覆盖 ASS 样本适配、SMB 短读与共享加载；
`connectedDebugAndroidTest` 验证 ASS/SRT、短读、取消及中途断网，并用逻辑大小超过 20GiB 的
稀疏测试容器验证跳过视频负载、双轨定位与无台词窗口；可传入 `mediaPath` 验证实片窗口与完整解析一致。

## 局域网播放是怎么做到的

- **SMB 用两个库，职责分开**：
  - [smbj]（`com.hierynomus:smbj`）负责**浏览与播放**。播放走自写的 `SmbDataSource`
    （一个 ExoPlayer `DataSource`），直接把 `smb://host/share/path` 交给播放器 ——
    相比「起本地 HTTP 代理再转发」，少一个组件、没有端口冲突，而且天然支持随机读，
    所以支持拖动进度条。
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
| 播放引擎 | libVLC 3.6.5；次字幕提取用 Media3 1.6.0 | **主字幕交给 libVLC 的 libass 渲染**，次字幕自绘 —— 见「双字幕是怎么做到的」 |
| 音频输出 | `--aout=opensles_android` | **只出 PCM（即下混）**。唯一支持 IEC61937 直通的 `android_audiotrack` 在本机起不来，详见「已知限制」 |
| SMB 浏览播放 | `com.hierynomus:smbj` 0.13.0 | SMB2/SMB3；需排除其传递的 bcprov，见下 |
| SMB 列举共享 | `eu.agno3.jcifs:jcifs-ng` 2.1.10 | 提供 SRVSVC `NetShareEnumAll`，smbj 没有这层 |
| 摘要算法 | `org.bouncycastle:bcprov-jdk15to18` | NTLM 需要 MD4；选 15to18 变体是因为它不依赖 `java.lang.invoke`，且两个 SMB 库传递的 `jdk18on` 都已排除 |
| DLNA | 自实现（SSDP + SOAP） | 避免 Cling（已 EOL）与 jUPnP 的依赖负担 |
| 服务器发现 | 自实现（子网 + 445 端口并发探测） | 不依赖 NetBIOS/mDNS，家庭网络里更可靠 |
| UI | Compose for TV（`androidx.tv:tv-material` 1.0.0） | 卡片自带 D-pad 焦点态 |
| 列表 | Compose `LazyVerticalGrid` | `tv-foundation:1.0.0` **不提供** lazy 网格组件（只有 list 系列），故用标准库网格 + TV 卡片 |
| 构建 | AGP 8.7.3 / Kotlin 2.0.21 / compileSdk 35 | `tv-foundation:1.0.0` 要求 compileSdk ≥ 35 且 AGP ≥ 8.6.0 |
| 依赖注入 | 手写 `AppServices` 容器 | 规模不需要 Hilt，省掉注解处理器 |
| 存储 | DataStore Preferences | 字幕来源、样式、网络位置；不保存播放进度，无需 Room |
| 最低版本 | `minSdk 23` | 受 `tv-foundation:1.0.0` 限制 |

## 已知限制

- **少数服务器会拒绝枚举共享**（组策略或 NAS 设置里关掉了）。这时应用会退化为逐个尝试
  23 个常见共享名；两者都没命中也**不代表服务器没有共享**，请到设备后台查看实际名称后手工填写。
- **拿不到 Dolby / DTS 直通（Atmos 等），多声道被下混成立体声**：libVLC 的 Android 音频输出里
  只有 `android_audiotrack` 支持 IEC61937 直通，而它在这台 TCL/MTK 设备上初始化失败
  （`too low audio sample frequency (0)` → `module not functional`，症状是**完全无声**）；
  能出声的 `opensles_android` 只出 PCM。作为对照：**当贝播放器在同一台电视上能正常输出 Atmos**，
  说明设备具备直通能力，差距在 libVLC 这一侧（它自研内核、自己控 AudioTrack 直通）。
  后续可试：改走 libmpv（Android 音频输出是原生 AudioTrack，支持 `--audio-spdif`）。
  **注意「升级 libVLC」这条路走不通** —— `libvlc-all:3.7.6` 要求 `compileSdk >= 36`，
  而本项目钉在 35（受 AGP 8.7.3 与 `tv-foundation:1.0.0` 约束），**3.6.5 已能用的最高 3.x**。
- **主字幕的字号 / 位置 / 描边不可调**：它由 libass 按片源的特效字幕渲染，只有**时间偏移**能改；
  次字幕不受此限。
- **图片字幕（PGS / DVD SPU / 蓝光）只能挂在主字幕位**：由 libVLC 渲染；次字幕是自绘的文本层，
  选到图片轨时会提示不支持。
- **DLNA 片源的内嵌字幕只有主字幕读得到**：主字幕走 libVLC 自己解封装，所以 DLNA 也能用；
  次字幕需要对 http 资源实现基于 Range 的随机读，本版本没做。任何片源都可以给次字幕挂外挂文件。
- **两路字幕的位置靠手调**：主字幕由 libass 定位、拿不到它的实际高度，所以次字幕只能靠「底部距离」
  这个固定余量（默认 112dp）错开；片源字幕特别高时到字幕设置里调大。
- **播放 Dolby Vision 片源取决于设备解码能力**：走系统 `MediaCodec`，
  DV 需要厂商提供对应 profile 的解码器；HDR10 / HLG 一般没问题。
  遇到 DV 片源黑屏或偏色，可先在电视上关掉 DV 输出再验证。
- 网络位置用明文保存密码（可在应用私有目录找到）；介意的话可以改用只读的访客账号

[smbj]: https://github.com/hierynomus/smbj
[jcifs-ng]: https://github.com/AgNO3/jcifs-ng
