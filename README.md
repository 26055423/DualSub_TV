# DualSub TV

面向 Android TV / 大屏电视的播放器，核心能力是**同时挂载主字幕与次字幕**。

两路字幕在来源、样式、位置、时间轴上完全独立：可以一路用视频内嵌轨道、另一路用外挂文件，
也可以各挂一个外挂文件，各自调字号 / 底部距离 / 时间偏移，互不影响。
片源同样不限于本机 —— 支持局域网 SMB 共享、DLNA 媒体服务器、WebDAV 与三家云网盘。

> 界面风格：**深墨夜景 + 无色玻璃 + 单一香槟金强调色**。
> 完整规范（配色 token / 尺寸档位 / 焦点态 / 覆盖层与返回键层序）见
> [`docs/UI_STYLE_REFERENCE.md`](docs/UI_STYLE_REFERENCE.md)；
> 代码结构与设计取舍见 [`ARCHITECTURE.md`](ARCHITECTURE.md)。

---

## 功能

**媒体库**

- 基于 `MediaStore` 扫描本机与 U 盘上的视频，4 列网格，遥控器 D-pad 可导航
- Android 13+ 使用 `READ_MEDIA_VIDEO`，旧版本回落 `READ_EXTERNAL_STORAGE`
- 支持从文件管理器「用 DualSub TV 打开」直接进播放页（`ACTION_VIEW` + `video/*`）

**网络位置**（五个入口：本地网络 / 云盘 / NAS / WebDAV / DLNA）

- **本地网络**：**进去就自动扫描**同一网段里开放 445 端口的设备，
  扫到的设备点一下即可填账号接入；右上角有「**＋ 手动配置**」留给已知地址的手填场景
- **列出共享**：填好账号后直接向服务器要共享清单，点选即可 —— **不需要事先知道共享名**
- **云盘**：夸克 / 百度 / 阿里云盘（扫码登录）
- **NAS**：按品牌接入 —— **飞牛 / 群晖 / 威联通 / 绿联**。各家默认端口已预填并自动探测，
  只填地址 / 账号 / 密码；表单上直接印着该家「去哪儿开 WebDAV」和它特有的坑
- **WebDAV**：AList / Nextcloud / Nginx 等通用 WebDAV 服务
- **DLNA / UPnP**：一键扫描局域网内的媒体服务器，浏览并播放
- 已添加的服务器可**编辑**（改 IP、改密码、换共享）与删除

**播放器**

- **libVLC 播放**（内置完整 FFmpeg，含 RealMedia 容器解析）
- 控制条：进度条 + 左「已播放」/ 右「总时长」+ 两路字幕入口
- 顶栏：左上文件名，右上「实时网速 · 当前时间（24 小时制）」，与控制条同步显隐
- 长按方向键进**逐帧预览**，松键恢复
- 播放进度仅在当前播放页保留；退出或换片后从头播放

**双字幕**（机制详见下一节）

- 主字幕、次字幕各一个独立状态（来源 / 时间偏移 / 样式）
- **两路文本字幕都由 Compose 自绘** → 字号 / 颜色 / 描边 / 底部距离 / 时间偏移都能单独调，
  且中文字形走系统字体栈，**不会出现方框**
- **图片字幕**（PGS / DVD SPU / 蓝光）由 libVLC 渲染 —— 位图 Compose 画不了
- 字幕来源：
  - 视频**内嵌字幕轨**（文本轨走自绘；图片轨交 libVLC 选轨）
  - **外挂字幕文件**（系统文件选择器，`srt` / `vtt` / `ass` / `ssa`，读取权限持久化）
- 每个视频各自记住「上次用的哪两路字幕」
- 字幕组在片源里断的行，**能一行放得下就合成一行**，放不下则保持原本的断行

**字幕解析**（`com.dualsub.tv.subtitle`，纯 Kotlin，不依赖 Android 框架与 media3）

- `SrtParser`：容错序号缺失、CRLF、老式坐标后缀、内联 HTML 标签
- `VttParser`：跳过 `NOTE`/`STYLE`/`REGION` 块，支持 cue settings、`MM:SS.mmm`、`<v>`/`<c>` 标记
- `AssParser`：按 `Format:` 声明切分字段（`Text` 里的逗号不会被截断），展开 `\N`，剥离 `{\...}` 覆盖指令
- `SubtitleTextDecoder`：BOM → 严格 UTF-8 → GB18030 回退，处理中文圈常见的无 BOM GBK 字幕

**AI 字幕**

- 用 OpenAI 兼容接口（默认阿里云百炼 Qwen-Omni）把外语片译成字幕
- **批处理生成**与**实时翻译**（边看边生成）两种模式
- 配置方式刻意做成「电视只管用、配置交给手机」：电视起一个局域网 HTTP 服务并显示二维码，
  手机扫码在浏览器里填 API Key —— 用遥控器敲 URL 是折磨

---

## 双字幕是怎么做到的

**两路文本字幕走同一条自绘链路，只有图片字幕交给 libVLC**：

| | 主字幕（文本） | 次字幕 |
|---|---|---|
| 渲染者 | **Compose 自绘** | **Compose 自绘** |
| 画在哪 | Compose 层 | Compose 层，位于主字幕**之上** |
| 能调 | 字号 / 颜色 / 描边 / 底部距离 / 时间偏移 | 同左 |
| 数据来源 | `embeddedCues` 窗口读取 / 外挂文件 | 同左 |

| | 主字幕（图片：PGS / DVD SPU / 蓝光） |
|---|---|
| 渲染者 | **libVLC**（`setSpuTrack`）—— 位图 Compose 画不了 |
| 能调 | 只有时间偏移 |

**为什么主字幕也从 libVLC 改成了自绘**：真机上调不出「指定字幕字体」，中文字幕整片渲染成
方框（tofu）；而自绘走 Compose 的系统字体栈，任何能正常显示中文界面的电视都不会方框。
代价是放弃 libass 的 ASS 特效排版 —— 但这本来就是次字幕的限制，两路统一反而更简单。

具体做法：

1. **两路各自定位** —— 一条 50ms 的 ticker 按 `player.currentPosition` 对两路各做二分查找
   （`SubtitleCueIndex`），叠加各自的时间偏移，通过 `StateFlow<SubtitleCue?>` 推给 `SubtitleOverlay`。
2. **尽量单行** —— 字幕组在片源里按语义断的行，在电视上大多一行放得下（可用宽度约屏宽 − 80dp）。
   `SubtitleOverlay` 用 `TextMeasurer` **先测一次**：合并换行后只占一行就用合并版，
   否则**回到原文**按片源断行显示。合并是等长替换（`\n` → 空格），不会打乱 ASS 的字符级偏移。
3. **两路避让靠位置错开、不靠层级** —— 次字幕天然画在主字幕之上，所以它用 `bottomPaddingDp`
   留一个**固定余量**（默认 112dp，约两行主字幕加一条间隙）来避开主字幕。
   主字幕特别高时，到「设置 → 次字幕样式」里把这个值调大。

---

## 局域网播放是怎么做到的

- **SMB 用两个库，职责分开**：
  - [smbj]（`com.hierynomus:smbj`）负责**浏览与播放**。播放直接把 `smb://host/share/path`
    交给播放器 —— 相比「起本地 HTTP 代理再转发」，少一个组件、没有端口冲突，
    而且天然支持随机读，所以能拖进度条。
  - [jcifs-ng]（`eu.agno3.jcifs:jcifs-ng`）**只负责列举共享**。smbj 只暴露文件级 API，
    没有 SRVSVC 的 `NetShareEnumAll`；而 jcifs-ng 对 `smb://host/` 调 `listFiles()`
    得到的就是共享清单。播放链路完全不碰它。
- **连接自愈分两层**：共享句柄失效（`DiskShare has already been closed`）只重建句柄；
  传输层断开（`transport is disconnected`）则整条连接作废重连 —— 两者串成"先轻后重"，
  详见架构文档 §6。
- **服务器发现**：对当前网段的 445 端口做并发 TCP 探测，不依赖 NetBIOS / mDNS ——
  这两者在家庭网络里常被路由器拦掉。
- **枚举受限时的兜底**：少数服务器组策略禁止枚举，这时退回逐个尝试 23 个常见共享名。
- **DLNA** 不引入 Cling（EOL）/ jUPnP，而是自实现最小客户端：SSDP 发现 → 解析设备描述
  → SOAP `Browse`。

---

## 构建

前置：**JDK 17**、Android SDK（`platforms;android-35`，`build-tools;34.0.0`）。
`local.properties` 里的 `sdk.dir` 指向本机 SDK。

```powershell
# 只做编译校验（最快，不打包）
.\gradlew.bat :app:compileDebugKotlin

# 单元测试（字幕解析、SMB 路径编解码、子网枚举、DLNA 报文解析、时间轴定位）
.\gradlew.bat :app:testDebugUnitTest

# 打包
.\gradlew.bat :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk（约 125 MB）
```

> ⚠️ **本机一个坑**：`gradle.properties` 里的
> `kotlin.compiler.execution.strategy=in-process` 不能删 —— 否则 Kotlin 编译进程起不来，
> Gradle 会返回 `BUILD SUCCESSFUL` 却**什么都没编**。判断"是否真的编了"要看 class 时间戳
> 与 APK 时间戳（详见 `docs/HANDOVER.md` 第六节）。

APK 体积主要来自 libVLC：`abiFilters` 只留 `arm64-v8a`（debug 另加 `x86_64` 给模拟器），
不做过滤会从 125 MB 涨到 210 MB。

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

同理 `android.software.leanback` 声明为 `required="false"`，避免厂商系统把安装过滤掉。

> `category` 在同一个 `intent-filter` 内是「全部满足」语义，所以两个入口必须拆开写；
> 合并成一组会导致两边都匹配不上。

## 首次使用：接你的 NAS

1. 首页点「**本地网络**」—— 进去会**自动开始扫描**，不用先点一次"扫描"
2. 看到你的 NAS 后点它（整张卡可点），进入填账号的表单
3. 只填**用户名 / 密码**（没设密码就留空 = 匿名），主机已按你点的设备预填
4. 点「列出共享」→ 直接点选你的共享文件夹 → 保存

**已经知道 IP 和共享名？** 点右上角「**＋ 手动配置**」直接填。

**要改 IP / 密码 / 换共享？** 在网络首页的「已保存」里点该条目的「编辑」。

如果 NAS 开的是 DLNA 而不是 SMB，点「DLNA」卡扫描，保存后直接进入。

**如果是飞牛 / 群晖 / 威联通 / 绿联这类家用 NAS**：点「**NAS**」→ 选你的品牌，
只填地址 / 账号 / 密码即可 —— 各家默认端口已经预填，探测会自动校正；
表单上还直接写着该家「去哪儿开 WebDAV」和它特有的坑。两个常见前提：

1. WebDAV 服务**默认是关的**，要先在后台打开（表单上有具体路径）；
2. 进去后看不到文件夹时，检查共享文件夹上「允许通过文件共享协议挂载」之类的开关
   （飞牛和群晖都有这一项）。

---

## 播放页操作

| 按键 | 行为 |
|---|---|
| OK | 播放 / 暂停 |
| ← / →（单击） | 快退 / 快进 10 秒 |
| ← / →（长按） | 逐帧预览，松键恢复 |
| ↑ / ↓ | 开关播放信息层 |
| ☰ / Info | 开关侧边菜单 |
| **返回** | **逐层退出**（见下） |

**返回键是逐层的**，按顺序：

```
退出确认框 → AI 配置层 → 整页选择页 → 数值激活态 → 菜单二级 → 菜单一级
          → 提示条 → 控制条 → 退出确认框（默认高亮「取消」）
```

两条规矩：**覆盖层互斥显示**（整页选择页打开时菜单整个收掉，不叠透明层）；
**退出播放要确认**（会丢进度，所以默认落点是「取消」）。

---

## 技术选型

| 项 | 选择 | 说明 |
|---|---|---|
| 播放引擎 | libVLC 3.6.5 | 内置完整 FFmpeg，含 RealMedia 容器解析；**两路文本字幕自绘**，只有图片字幕交给它 |
| 音频输出 | `--aout=opensles_android` | **只出 PCM（即下混）**，详见「已知限制」 |
| SMB 浏览播放 | `com.hierynomus:smbj` 0.13.0 | SMB2/SMB3；需排除其传递的 bcprov，见下 |
| SMB 列举共享 | `eu.agno3.jcifs:jcifs-ng` 2.1.10 | 提供 SRVSVC `NetShareEnumAll`，smbj 没有这层 |
| 摘要算法 | `org.bouncycastle:bcprov-jdk15to18` | NTLM 需要 MD4；选 15to18 变体是因为它不依赖 `java.lang.invoke` |
| DLNA | 自实现（SSDP + SOAP） | 避免 Cling（EOL）与 jUPnP 的依赖负担 |
| 服务器发现 | 自实现（子网 + 445 并发探测） | 不依赖 NetBIOS/mDNS，家庭网络里更可靠 |
| UI | Compose for TV（`androidx.tv:tv-material` 1.0.0） | 卡片自带 D-pad 焦点态；**焦点视觉自管**，见规范 §2.4 |
| 列表 | Compose `LazyVerticalGrid` | `tv-foundation:1.0.0` **不提供** lazy 网格组件 |
| 构建 | AGP 8.7.3 / Kotlin 2.0.21 / compileSdk 35 / targetSdk 34 | targetSdk 保持 34：升 35 会引入强制 edge-to-edge |
| 依赖注入 | 手写 `AppServices` 容器 | 规模不需要 Hilt，省掉注解处理器 |
| 存储 | DataStore Preferences | 字幕来源与样式、网络位置、AI 配置；不存播放进度，无需 Room |
| 最低版本 | `minSdk 23` | 受 `tv-foundation:1.0.0` 限制 |

## 已知限制

- **拿不到 Dolby / DTS 直通（Atmos 等），多声道被下混成立体声**：libVLC 的 Android 音频输出里
  只有 `android_audiotrack` 支持 IEC61937 直通，而它在这台 TCL/MTK 设备上初始化失败
  （`too low audio sample frequency (0)` → `module not functional`，症状是**完全无声**）；
  能出声的 `opensles_android` 只出 PCM。作为对照：**当贝播放器在同一台电视上能正常输出 Atmos**，
  说明设备具备直通能力，差距在 libVLC 这一侧。
  **「升级 libVLC」这条路走不通** —— `libvlc-all:3.7.6` 要求 `compileSdk >= 36`，
  而本项目钉在 35，**3.6.5 已是能用的最高 3.x**。
- **图片字幕（PGS / DVD SPU / 蓝光）只能挂在主字幕位**（由 libVLC 渲染）；
  次字幕是自绘的文本层，选到图片轨会提示不支持。
- **DLNA 片源的内嵌字幕只有主字幕读得到**：字幕提取需要对 `http` 资源实现基于 Range 的
  随机读，本版本没做。任何片源都可以给次字幕挂外挂文件。
- **ASS 特效排版不完整**：文本字幕现在走自绘，所以复杂排版（矢量绘图、复杂动画组、
  精细的 `\pos` / `\move` 组合）表现不如 libass —— 这是"中文字幕不出方框"换来的代价。
- **少数服务器会拒绝枚举共享**（组策略或 NAS 设置里关掉了）。这时会退化为逐个尝试
  23 个常见共享名；两者都没命中也**不代表服务器没有共享**，请到设备后台查看实际名称后手填。
- **播放 Dolby Vision 片源取决于设备解码能力**：走系统 `MediaCodec`，DV 需要厂商提供对应
  profile 的解码器；HDR10 / HLG 一般没问题。遇到 DV 片源黑屏或偏色，可先在电视上关掉 DV 输出。
- **网络位置用明文保存密码**（应用私有目录）；介意的话可以改用只读的访客账号。
- **模拟器验证不了的**：SMB 相关路径（模拟器没有 SMB 源）、中文字形（模拟器有中文字体，
  真机才看得出方框问题）。

## 相关文档

| 文档 | 内容 |
|---|---|
| [`docs/UI_STYLE_REFERENCE.md`](docs/UI_STYLE_REFERENCE.md) | 视觉规范：配色 token、尺寸档位、焦点态规则、覆盖层与返回键层序、自检清单 |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | 架构：模块结构、核心决策、数据流、网络体系、字幕系统、扩展指南 |
| [`docs/HANDOVER.md`](docs/HANDOVER.md) | 交接：本轮改动、踩过的坑、构建环境问题、待办 |
| [`DEV_SETUP.md`](DEV_SETUP.md) | 开发环境准备 |

[smbj]: https://github.com/hierynomus/smbj
[jcifs-ng]: https://github.com/AgNO3/jcifs-ng
