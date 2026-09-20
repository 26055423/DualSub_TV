# DualSub_TV 交接文档

> 更新时间：2026-09-20
> 用途：**新会话接手用**。读完本文即可继续开发，不必翻历史。

---

## 〇、先看文档分工（**别在四处写重**）

| 文档 | 管什么 | 什么时候读 |
|---|---|---|
| `README.md` | **用户向**：功能、怎么用、怎么构建装机、已知限制 | 想知道"这个 app 能干什么" |
| `docs/ARCHITECTURE.md` | **结构**：模块图、核心决策、数据流、网络体系、字幕系统、扩展指南 | 要动某块代码、想知道它为什么长这样 |
| `docs/UI_STYLE_REFERENCE.md` | **视觉规范**：配色 token、尺寸档位、焦点态规则、覆盖层与返回键层序、自检清单 | **任何 UI 改动之前** |
| `docs/HANDOVER.md`（本文） | **交接**：当前状态、别再重踩的坑、构建环境、待办、协作注意 | 刚接手时 |

本文不重复前三份的内容，只保留"踩过才知道"的东西。

---

## 一、这是什么

**DualSub_TV** —— 安卓 TV 播放器，核心卖点是**同时显示主字幕与次字幕**（双字幕）。

- 路径：`D:\Projects\DualSub_TV`
- 包名：`com.dualsub.tv`
- 远程：`git@github.com:26055423/DualSub_TV.git`，分支 `main`
- **最后一个已提交的 commit：`5bd2837`** —— 之后所有改动都在工作区，**未提交**
- **未跟踪且故意不提交**：`DualSub_TV - 制作一个安卓TV的播放器APP，要求….md`（2.4 MB 会话记录）

### 目标形态

「支持双字幕的**当贝增强版**」—— 能力上要当贝没有的（双字幕、AI 字幕），
但**观感上不再是"复刻当贝"**：UI 已统一为**深墨夜景 + 无色玻璃 + 香槟金**。
凡是在旧描述里看到"照当贝那套 / 白卡片 / 浅蓝底 / 靛蓝"，一律以 `UI_STYLE_REFERENCE.md` 为准。

---

## 二、当前状态

### 已完成（代码在工作区，编译通过，已打进 APK）

| 主题 | 落点 |
|---|---|
| **UI 风格统一**：深墨夜景 + 无色玻璃 + 香槟金 | `ui/theme/BeiGlass.kt`（唯一色板 + `BeiDims` + `BeiMotion`）；两套旧色板已删除 |
| **UI 简约化**：色阶收敛（玻璃一档、描边两种色、香槟实色 + 15% 填充） | 同上，见 `UI_STYLE_REFERENCE.md` §六 |
| **外壳**：左导航（3 项）+ 夜景观底 + 光斑 + 玻璃导航 | `ui/shell/AppShell.kt`、`ui/shell/BeiUi.kt` |
| **主字幕改 Compose 自绘**（原交 libVLC） | `PlayerViewModel` + `SubtitleOverlay`；图片字幕仍交 libVLC |
| **网络页合并**：SMB + 扫描局域网 →「本地网络」子页（进入即扫描 + 「＋ 手动配置」），卡片 7 → 6 张 | `ui/network/LocalNetworkScreen.kt`、`NetworkScreen.kt` |
| **退出播放确认框** + **返回键逐层退出** | `ui/player/components/PlayerExitConfirmOverlay.kt`、`PlayerScreen` 的 `BackHandler` |
| **覆盖层互斥**：整页选择页打开时不画菜单 | `PlayerScreen` 渲染树 |
| **字幕尽量单行**：合并换行后测一次，放不下就按原断行 | `SubtitleOverlay.rememberSingleLineCue` |
| **SMB 连接级自愈**（在句柄级之上再加一层重连） | `SmbSession.withShareRecovery` |
| **报错文案说人话**：异常类全名不再甩给用户，只进 logcat | `PlayerViewModel` / `RemoteBrowseScreen` |

### 未验证（**接手后优先确认这些**）

| 项 | 为什么没验证 |
|---|---|
| **主字幕方框是否消失** | 模拟器上有中文字体，**只能真机看** |
| 图片字幕（PGS 片源）是否仍正常 | 手头没有 PGS 片源 |
| SMB 连接断开后能否自愈 | 模拟器没有 SMB 源，要真机造"NAS 踢会话"的场景 |
| 「本地网络」自动扫描是否好用 | 同上，需要真实局域网 |
| UI 观感（玻璃层级是否够清晰、香槟金焦点在三米外是否醒目） | 需要真机 / 装机截图 |

---

## 三、关键技术结论（**别再重踩**）

### 1. `--freetype-font` 在 Android 上无效 ⚠️

vlc-android 的 freetype 走**专用 Android 后端**，**只读 `/system/etc/fonts.xml` 按"族名"查**，
**完全不解析文件路径**；且 vlc-android **默认禁用 fontconfig**。

**推论**：`CJK_FONT_CANDIDATES` / `detectCjkFont()` / `buildLaunchOptions(fontPath)` **全是死代码**；
`BundledFont.kt` + `assets/fonts/DroidSansFallback.ttf`（**2.88 MB**）是纯负担，**建议删除**
（主字幕已改自绘，VLC 不再画文本字幕，而图片字幕是位图不需要字体）。

### 2. 当贝不是基于 mpv

逆向结论：**ijkplayer/ffplay 血统的自研内核**（符号重命名为 `lerad`）+ 自编 ffmpeg（21.79 MB）+
**独立 `libass.so`** + **独立 `libplacebo.so`**。

它字体不方框的真正原因：**自己持有 libass 句柄**，能调 `ass_set_fonts_dir` /
`ass_set_extract_fonts`，而且**启用了 fontconfig**。隔着 libVLC 这一层我们够不到。

### 3. 许可证

| 库 | 许可 | 闭源商用 |
|---|---|---|
| **libVLC** | LGPL 2.1+ | ✅ 可以 |
| **mpv** | **GPLv2+** | ❌ 不行（除非整个 APP 开源） |

（GPL 的扳机是**分发**，不是收费；免费发布一样触发。）

### 4. Media3 不识别 `0xF7`（CueTrack）

反编译 `MatroskaExtractor.getElementType` 确认：`lookupswitch` 93 个分支里有
`179`(0xB3 CueTime) / `241`(0xF1 CueClusterPosition)，**没有** `247`(0xF7) / `240`(0xF0) / `178`(0xB2)。

**所以代码里的不对称是刻意的、正确的**：`0xB3`/`0xF1` 分支**不带 `return`**（要转发给 super），
`0xF7`/`0xF0`/`0xB2` **带 `return`**（Media3 对它们无逻辑可转）。

### 5. 其他踩过的坑（每条都真代价）

- **`LibVLC(Context, List)` 构造器会往传入的 list 里 `add()`** → 必须传 `ArrayList`，
  传 `listOf(...)` 会在打开视频的瞬间崩掉整个 APP。
- **`setSpuTrack()` 的 id 必须取自 `MediaPlayer.spuTracks`**；`IMedia.SubtitleTrack.id` 是另一套编号。
  音轨那边 `IMedia.Track.id` 与 `TrackDescription.id` 不同源，是同一个坑。
- **Android 的 ICU 正则与 JVM 的 `java.util.regex` 不兼容**：正则里孤立的 `}` 必须转义，
  否则 Android 抛 `PatternSyntaxException` → 那个 `object` **类初始化失败** → 整条解析链报废。
  **纯 JVM 单测覆盖不到这个差异** —— 涉及 `Regex(...)` 的改动要在真机/模拟器上跑一次真实数据。
- **`--aout=opensles_android` 是这台 TCL 唯一能出声的输出模块**（`android_audiotrack` 起不来）。
  代价：拿不到 Dolby/DTS 直通，仅下混，用户已接受。
- **`androidx.tv.material3` 的 `Surface` / `Border` 不是 Material3 那一套**（**踩过一次，别再改回去**）：
  - 非点击版 `Surface` 的参数是 `colors = SurfaceDefaults.colors(...)`，**没有** `color` / `contentColor`；
    写成 `Surface(modifier = …, color = …)` 会报 `No parameter with name 'color' found`
  - 点击版参数是 `colors = ClickableSurfaceDefaults.colors(...)`、`shape = ClickableSurfaceDefaults.shape(...)`
  - 两者的 `border` 都要它自家的 `androidx.tv.material3.Border`（**不是** foundation 的 `BorderStroke`）：
    传错会报 `actual type is …BorderStroke, but …Border was expected`，并且**紧跟其后的一行**
    会连带报 `@Composable invocations can only happen from the context of a @Composable function`
    —— **报错位置有误导性，别顺着第二行去查**
  - **现在的做法**：可点击件用 `Surface(onClick = …)` 只给 `shape` / `colors` / `scale`，
    **描边不进 tv 的 `border` 参数**，而是用 foundation 的 `Modifier.border` 叠在 `Surface` 的
    **content 里**（`Modifier.matchParentSize()`）。好处：① 绕开上面那套 `Border` 类型坑；
    ② 描边会跟着 `scale` 的焦点放大一起缩放，不会错位
- **焦点态只有"一个来源"才靠得住**（**连踩两次**）：
  ① 底色交给 `Surface` 的 `focusedContainerColor` 时，它与真实焦点不同步，焦点走了旧项**不撤**；
  ② 改成自绘却又写成 `if (focused || selected)`，于是"选中"那块底**赖着不走**。
  结论：**一处 UI 里只允许一块高亮，且它完全由 `focused` 驱动** —— 详见
  `UI_STYLE_REFERENCE.md` §2.4「规则二」。
- **覆盖层不要叠透明层**：整页选择页的遮罩是半透明的，菜单若同时画着，两层会互相透出来。
  **互斥显示**（选择页打开时不渲染菜单）—— 见 `UI_STYLE_REFERENCE.md` §2.5。

---

## 四、构建环境的坑（**曾经卡了很久**）

### 症状

`.\gradlew.bat :app:assembleDebug` 返回 **`BUILD SUCCESSFUL in 7s`**，
`37 actionable tasks: 6 executed, 31 up-to-date` —— **但这几次编译什么都没编**。

**判断依据**（`BUILD SUCCESSFUL` ≠ 代码进产物）：

```powershell
# 看 class 时间戳
Get-Item app\build\tmp\kotlin-classes\debug\com\dualsub\tv\ui\player\PlayerViewModel.class
# 看 APK 时间戳
Get-Item app\build\outputs\apk\debug\app-debug.apk
```

### 根因

Kotlin 编译**进程起不来**。终端日志与 `~/.gradle/daemon/8.12/daemon-*.out.log` **都停在**：

```
Using Kotlin/JVM incremental compilation
[KOTLIN] Kotlin compilation 'jdkHome' argument: null
```

之后再无输出，**CPU 长期 3%**（在等，不是在算），且 `~/.kotlin/daemon` 目录**从未被创建**。

### 解药

```properties
# gradle.properties
kotlin.compiler.execution.strategy=in-process
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m -Dfile.encoding=UTF-8
```

> ⚠️ **本机可用内存只有约 4.2 GB**（总 31.8 GB，被别的程序占掉大部分），
> 所以**不要**把 `-Xmx` 开到 6 GB 以上。
>
> `kotlin.compiler.execution.strategy=in-process` 这行**不能删** —— 删了就会回到
> "`BUILD SUCCESSFUL` 却什么都没编"的状态。

### 另一个执行层的小毛病

在自动化环境里跑 Gradle 时，命令收尾常报 `exec: WaitDelay expired before I/O complete`
并因此被判定为失败 —— 但输出里其实已经打印了 `exit=0` 与 `BUILD SUCCESSFUL`。
**看 exit 码与错误行数，不要只看命令的"成功/失败"标记。** 一个可靠的写法：

```powershell
$log = Join-Path $env:TEMP 'c.log'
.\gradlew.bat :app:compileDebugKotlin --console=plain > $log 2>&1
"exit=$LASTEXITCODE"
"errors=" + (Select-String -Path $log -Pattern '^e: ' | Measure-Object).Count
Select-String -Path $log -Pattern 'BUILD ' | Select-Object -Last 1
```

---

## 五、环境信息

| 项 | 值 |
|---|---|
| SDK | `C:\Android\sdk`（platforms 33/34/35；**无 NDK**） |
| JDK 17 | `C:\Program Files\Eclipse Adoptium\jdk-17.0.15.6-hotspot` |
| 模拟器 | `emulator-5554`（Android TV x86_64），测试片源 MediaStore `_id=18` |
| 用户 NAS | SMB `192.168.1.252`，用户 `player`，共享 `usb_SSD`（**密码不入库，别写进文档**） |
| 测试片源 | `Twisters.2024.2160p...HDR10+.H.265-DreamHD.mkv`（中文名「龙卷风」，4K，SMB 上） |
| APK | `app\build\outputs\apk\debug\app-debug.apk`（约 125 MB，含 arm64 + x86_64） |

### 常用命令

```powershell
cd D:\Projects\DualSub_TV

# 只做编译校验（比 assembleDebug 快得多，不打包不装机）
.\gradlew.bat :app:compileDebugKotlin

# 交付可安装包时才跑
.\gradlew.bat :app:assembleDebug

# 单元测试（字幕解析、SMB 路径、子网枚举、DLNA 报文、时间轴定位）
.\gradlew.bat :app:testDebugUnitTest

# 装机 + 冷启动直接打开测试片源（绕开遥控器导航）
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.dualsub.tv
adb logcat -c
adb shell am start -a android.intent.action.VIEW -d "content://media/external/video/media/18" -t "video/*"
adb logcat -d -s DualSubTV
```

> 装机后想确认"装的是不是最新版"：界面里 `BuildConfig.BUILD_TIME` 会显示构建时刻
> （在「运行日志」覆盖层与播放失败页上能看到）。

---

## 六、待办

### A. 清理（**已基本做完**）

- [x] **字体死代码已删除**：`player/BundledFont.kt` 与 `app/src/main/assets/fonts/` **都已不存在**，
      代码里也搜不到 `CJK_FONT_CANDIDATES` / `detectCjkFont` / `fontPath`
      （只剩 `PlayerViewModel` 里一句解释 `--freetype-font` 为何无效的注释，保留合理）。
      APK 因此已经减掉那 2.88 MB —— 理由见 §三.1
- [x] 工作区里没有临时产物（`build-log.txt` 已确认不存在）
- [ ] `VlcPlayerController` 的 `--verbose=1` 注释里补一句「排查音频问题时临时调回 2」
      —— 原先调 2 就是为了在 logcat 看 `audio output: using module …`

### B. 待真机验证（按优先级）

- [ ] **主字幕方框是否消失**（模拟器验证不了，只能真机）
- [ ] **SMB 连接断开后能否自愈**：真机上播 SMB 片源 → 到 NAS 上踢掉当前会话（或等空闲超时）
      → 打开「字幕设置」看是否还能读出轨道，而不是报错
- [ ] **「本地网络」自动扫描**：进去是否立刻开始扫描、扫到的设备点一下是否回填主机名
- [ ] 图片字幕（有 PGS 的片源）是否仍正常
- [ ] 网速读数：目前**只对网络片源显示**（本地文件按设计不显示读盘速率）；
      SMB 片源上的真实读数未验证
- [ ] 长按快进的节流效果（`PREVIEW_THROTTLE_MS = 150`）
- [ ] 字幕"尽量单行"：短句是否并成一行、长句是否保持片源原本的断行

### C. 已知设计债

- **`SubtitleSource.EmbeddedTrack.trackIndex` 承载两种语义**：文本轨存 Media3 索引，
  图片主字幕存 VLC spu id（`PlayerViewModel` 里有多处条件判断 + 注释保证自洽，但有风险）。
  将来若拆字段，建议 `media3Index` / `vlcSpuId` 分开。
- **`PlayerScreen.kt` 是一个 1400+ 行的大文件**：按键处理、层叠组合、菜单构建都在里面。
  它有详细的 KDoc（哪些约束不能破），但再往上加功能前建议先考虑拆出子 composable。
- **两路字幕的位置靠手调**：主字幕高度拿不到，次字幕只能靠「底部距离」这个固定余量错开。
  彻底解决要给渲染层回报实测高度（`SubtitleOverlay` 已有 `onHeightPx` 回调，但只用于主字幕）。

### D. 可选改进（用户提过、尚未做）

- **Dolby / DTS 直通**：`android_audiotrack` 在这台 TCL 上起不来，只能下混。
  **升级 libVLC 走不通**（`libvlc-all:3.7.6` 要求 `compileSdk >= 36`，本项目钉 35）。
  真要做得换播放引擎（libmpv 的原生 AudioTrack 支持 `--audio-spdif`），但那会把许可证
  拖到 GPL —— 见 §三.3。
- **reduced-motion 支持**：现在焦点放大固定 1.03，没有跟随系统"减少动画"设置。
- **双语字幕的排版**：现在"字幕尽量单行"会把"上行原文 / 下行译文"并成一行。
  若用户常看这类片源，可以加个开关。

---

## 七、协作注意

- **用户偏好中文交流**，思考过程也用中文。
- **提交 / 推送需用户明确指令**，不要自作主张 push。
- **不要默认编译 / 打包**：除非用户明确要求（或要交付可安装的 APK），改完代码**不要顺手**跑
  `assembleDebug`。需要验证时优先只跑 `:app:compileDebugKotlin`。
  用户 2026-09-19 明确说过「不要每次稍微改点啥就要重新编译 apk，除非我要求」。
- **装机 / 截图验证也要先问** —— 用户的工作流是「改 → 我打包 → 他自己装到电视上看」。
- 用户曾明确说过「**不要擅自改 `PlayerScreen.kt`**」—— 该限制后因方案②获得授权而解除，
  但**改它之前最好确认一次**。
- 该项目文档习惯：**英文文件名 + 中文内容**。
- 用户会**直接发电视上的截图**反馈问题。看图时留意：他常圈出问题区域，
  但照片有反光/摩尔纹，OCR 可能误读 —— 结合代码判断比死抠图更可靠。
- 改 UI 之前**先读 `docs/UI_STYLE_REFERENCE.md`**；实现与文档冲突时，
  **同步改文档**（不要留一处说法、一处实现）。
