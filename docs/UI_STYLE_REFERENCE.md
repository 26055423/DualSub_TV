# UI 风格参考（**TV 版**）

> 来源：用户提供的 stylekit 风格规范（`styles/glassmorphism`，Nocturne Glassmorphism / 夜航玻璃拟态）。
> **本文件是它的裁剪版**：按用户要求「根据 TV 环境改造 —— 只保留配色风格，不适用特效」。
> 项目文档习惯：英文文件名 + 中文内容。
>
> ## ⚠️ 状态：本规范已全量落地
>
> 下面每一节都标出了**实际落地的 Kotlin token**。颜色的唯一来源是
> `app/src/main/java/com/dualsub/tv/ui/theme/BeiGlass.kt`，里面三个 object：
> **`BeiGlass`**（配色）· **`BeiDims`**（TV 尺寸档位）· **`BeiMotion`**（缓动 / 时长 / 缩放）。
>
> - 覆盖层与返回键的层序见 **[§2.5](#25-覆盖层与返回键层序)**；
> - 落点索引（哪块 UI 用在哪）见 **[§七](#七落点索引实际部署位置)**；
> - 刻意偏离规范的少数几处见 **[§八](#八已知偏离与未做项)**；
> - 改 UI 前先读本文；**实现与本文不一致时，先改文档再改代码**（或同步改）。

---

## 〇、一句话结论：原规范里哪些留、哪些丢

| 原规范的做法 | TV 版怎么处理 | 理由 |
|---|---|---|
| 深墨夜景底色 + 柔和光源光斑 | ✅ **保留**（`BeiGlass.Ink/Night/GlowDeep`） | 配色基础；播放页甚至可以直接拿视频画面当"光源" |
| 无色玻璃表面（白 5%–12%） | ✅ **保留**（`Glass` 白 6%） | 面板底色的正解 —— 颜色属于背景，面板不上色 |
| 唯一强调色（原为香槟金） | ✅ **保留**（`Accent` `#E4B863`） | 最重要的一条：强调色超过一种就是风格漂移 |
| 大圆角（16–24px） | ✅ 保留，**TV 上取 16–18dp**（`BeiDims.PanelRadius` / `CardRadius`） | 遥控器能看清，也不必像手机那样夸张 |
| spring easing 过渡 | ✅ 保留，但**只动 alpha / scale / 颜色**（`BeiMotion.Easing`） | 动 blur / shadow 会触发整块重绘 |
| `backdrop-filter: blur(40–60px)` | ❌ **删掉** | ① Compose 无跨版本 backdrop blur（minSdk 23）；② MTK 电视 SoC 上**逐帧模糊直接掉帧** |
| `backdrop-saturate(180%)` | ❌ **删掉** | 无对应 API，收益也小 |
| 颗粒噪点 `feTurbulence` | ❌ **删掉**（仅静态页面可选） | 要额外贴图 + 每帧混合，电视上性价比极低 |
| 方向性 inset 阴影（顶高光/底暗缘） | ⚠️ **降级为静态描边**（`BeiDims.Border` / `BorderFocus`） | Compose 不支持 inset 阴影；**不做动画** |
| `hover:` 上浮 / 扫光高光 | 🔁 **替换成焦点态**（见 §2.4） | 电视**没有 hover**，交互态只有「遥控器焦点」与「按下」 |
| 渐变文字 / 单侧粗边框 / 嵌套卡片 | ❌ 仍然禁止 | 这些与设备无关，本来就是坏做法 |

> 一句话：**在电视上，这份风格退化成"深色夜景 + 半透明无色面板 + 一个强调色 + 克制的焦点反馈"**。
> 玻璃的"光学感"靠**配色与层级**表达，不靠模糊。

---

## 一、配色（TV 版 token）

> **落地位置**：全部在 `ui/theme/BeiGlass.kt` 的 `object BeiGlass` 里；UI 代码**只允许**引用这些 token，
> **不许出现裸色**（见 §三「零裸色」）。

### 1.1 夜景底色

| 落地 token | 值 | 用途 |
|---|---|---|
| `BeiGlass.Ink` | `#060A13` | 近黑：最深的底、页面边缘（也是播放器底色与运行日志层底色） |
| `BeiGlass.Night` | `#0B1322` | 主背景 —— 外壳与播放页共用的夜景底 |
| `BeiGlass.Elevated` | **＝ `Night`** | 分区 / 抬升区域。简约化后不再靠"另一种底色"分层，名字保留为语义槽位 |
| `BeiGlass.GlowDeep` | `#33517A` | 光源光斑（**唯一色**） |
| `BeiGlass.Glow` | **＝ `GlowDeep`** | 同上 —— 两处柔光同色，界面才不会"发花" |

**TV 上光源光斑怎么画**：不要做实时模糊，直接 `Brush.radialGradient` 铺一层柔光即可。
实际实现见 `ui/shell/AppShell.kt` 的 `BeiGlowBackground()` —— 三处大半径、低透明度的
`drawRect(brush = Brush.radialGradient(...))`（右上主光源 55%、左上补光 32%、左下 12%），
用 `Canvas` 一次画完、不响应任何状态，因此不会引起重组。

播放页更省事：**视频画面本身就是光源**，面板只需要半透明（但要换成黑底，见 §四）。

### 1.2 无色玻璃表面（外壳面板底）

**只有一档玻璃**。卡片 / 静态卡这类"面板"的焦点**不换底色**，只加描边与放大；
少一档底色，整屏就少一层灰阶 —— 这是第二轮简约化最主要的一刀。

| 落地 token | 值 | 用途 |
|---|---|---|
| `BeiGlass.Glass` | 白 6%（`0x0FFFFFFF`） | 面板底（卡片、静态卡、输入框、来源图标底） |
| `BeiGlass.GlassFocused` | **＝ `Glass`** | 焦点态面板底（卡片类不换底色） |
| `BeiGlass.GlassBright` | **＝ `Glass`** | 按下态面板底（反馈由 `BeiMotion.PRESS_SCALE` 承担） |
| `BeiGlass.Border` | 白 15%（`0x26FFFFFF`） | **常态**描边，1dp |
| `BeiGlass.BorderBright` | **＝ `Accent`（香槟）** | **焦点**描边色，2dp |
| `BeiGlass.TextPrimary` | 白 100% | 标题 |
| `BeiGlass.TextSecondary` | 白 60%（`0x99FFFFFF`） | 正文 / 小节标题 |
| `BeiGlass.TextMuted` | 白 40%（`0x66FFFFFF`） | 说明 / 字段名 |

> **面板透明度上限 15%**：再多就变成实心色块，"借光"的感觉没了。
> 这套白玻璃只用于**外壳**（背后是我们自己铺的夜景 + 光斑）。播放页压在视频上的面板
> 必须用黑底（§四），两者**别混用**。
>
> **导航项是个例外**：左侧导航的焦点项会给一整块香槟 15% 底（见 §2.4），因为它是屏上
> 唯一的"位置指示"，分量要压过普通卡片。

### 1.3 唯一强调色：香槟金

**只有三档**：实色本体、亮香槟（文字用）、15% 填充（所有"强调底"共用）。

| 落地 token | 值 | 用途 |
|---|---|---|
| `BeiGlass.Accent` | `#E4B863` | 强调色本体：主操作、当前选中、来源图标符号、进度条已播段、**以及所有焦点描边** |
| `BeiGlass.AccentBright` | `#F3DCA8` | 香槟底上的文字、高亮词 |
| `BeiGlass.AccentFill` | 香槟 15%（`0x26E4B863`） | 强调底填充 —— 按钮常态 / 焦点 / 选中 / 导航项焦点**共用这一个值** |
| `BeiGlass.AccentFillStrong` | **＝ `AccentFill`** | 更亮一档的强调底（焦点不再靠"更亮的底"表达） |
| `BeiGlass.AccentBorder` | **＝ 实色 `Accent`** | 强调描边（常态） |
| `BeiGlass.AccentBorderStrong` | **＝ 实色 `Accent`** | 强调描边（焦点）—— 与常态同色，靠 **2dp 宽度**区分 |
| `BeiGlass.Link` | **＝ `AccentBright`** | 可点击动作（如「选择外挂字幕文件…」） |
| `BeiGlass.AccentDim` | **＝ `AccentFill`** | 预留的"次级强调底"槽位，当前无独立视觉 |

**已统一到香槟金**（决策记录见 §六）。原先项目用的是靛蓝 `#4F6BED` / `#5B6CF5`，
**那两个色值在代码里已经彻底不存在** —— 下次看到谁想加回靛蓝，就是在破坏"强调色只有一个"。

> 另有一个 `OnAccent`（压在**实心**香槟底上的前景色）曾存在过，因实现改为"半透明填充 +
> 亮香槟字"而**已删除**；若将来真要铺实心香槟底，记得连同前景色一起补回来。

### 1.4 状态语义色与功能性固定色

这不是"第二个强调色"：只在状态发生时短暂出现，不参与常态视觉、不用于装饰。

| 落地 token | 值 | 用途 |
|---|---|---|
| `BeiGlass.Success` | `#6FD08C` | 成功（「配置已更新」、扫码成功、实时 AI 胶囊） |
| `BeiGlass.Warning` | ＝ `Accent` | 警告（「已扫码，请确认授权」、播放失败页标题） |
| `BeiGlass.Danger` | `#FF8A80` | 错误（「⚠ 打开文件失败」、日志层 E/ 行） |
| `BeiGlass.QrSurface` | 纯白 | **二维码白底** —— 扫码识别要求白底黑码、不能带透明度，所以用不了 `Glass` 那套半透明白 |

---

## 二、TV 尺寸与排版规范（**本节是原规范没有的，电视专属**）

电视是"三米外看"的设备：分辨率高、观看距离远、操作只有遥控器。
**落地位置**：`ui/theme/BeiGlass.kt` 的 `object BeiDims`。

### 2.1 字号档位（10-foot UI）

| 落地常量 | 值 | 用途 |
|---|---|---|
| `BeiDims.TitleSize` | **28sp** | 页面大标题（各页标题、扫码登录页标题、表单标题） |
| `BeiDims.CardTitleSize` | **16sp** | 面板 / 卡片标题、播放信息层标题 |
| `BeiDims.BodySize` | **13sp** | 正文（遥控器界面里的舒适下限）、按钮文字 |
| `BeiDims.CaptionSize` | **12sp** | 次级 / 状态文字（一句说明、状态、单位） |
| `BeiDims.TinySize` | **11sp** | 极弱提示（尽量少用 —— 三米外基本读不清） |

> 经验值（本项目实测）：播放菜单从 14sp 降到 13sp、行高减 2dp 之后，同一屏能多显示三四行 ——
> **电视上的"信息密度"是靠字号省的，不是靠缩小间距**。

### 2.2 图标尺寸

| 落地常量 | 值 | 用途 |
|---|---|---|
| `BeiDims.IconSourceCard` | **56dp**（圆角 ≈ 28% 边长） | 来源 / 功能大图标卡（`SourceIcon`） |
| `BeiDims.IconNav` | **22dp** | 导航栏图标（`AppShell.TabGlyph`） |
| `BeiDims.IconStatus` | **18dp** | 状态 / 提示图标（`RemoteBrowseScreen.EntryGlyph` 的目录/文件标记） |
| `BeiDims.MinTouchTarget` | **48dp** | 最小可点区域（如设置页色点：视觉 30dp + 48dp 焦点框） |

> **不要用 emoji 当图标**：不同电视字形覆盖不同，缺字就是方框（本项目在字幕上踩过）。
> 本项目**全部自绘**（`Canvas`）：`TabGlyph` / `SourceIcon` / `EntryGlyph`，
> 并且依赖 `androidx.tv` 里**没有** `material-icons` 这个前提。
>
> 同一排卡片的图标**外接尺寸要统一**（都收敛到画布的 0.10–0.90）：`SourceIcon` 里曾经
> 有的偏大（实心圆占满）有的偏小（三圆组成的云），排在一起很显眼。

### 2.3 圆角 / 描边 / 间距 / 安全区

| 落地常量 | 值 |
|---|---|
| `BeiDims.CardRadius` | 卡片圆角 **18dp** |
| `BeiDims.PanelRadius` | 面板圆角 **16dp**（贴屏幕边缘那一侧可只圆外侧，如侧边菜单） |
| `BeiDims.Border` | 描边 **1dp** |
| `BeiDims.BorderFocus` | 焦点态描边 **2dp** |
| `BeiDims.ScreenStart` / `ScreenEnd` | 左右安全边距 **40dp**（`AppShell` 统一给内容区） |
| `BeiDims.ScreenVertical` | 上下安全边距 **28dp** |
| `BeiDims.CardGap` | 卡片间距 **18dp** |
| `BeiDims.CardPaddingH` / `CardPaddingV` | 卡片内边距 **20dp** / **16dp** |

### 2.4 焦点态（**替代 hover**）

电视只有四种状态：**常态 / 焦点 / 按下 / 不可用**。
实现要点见 `ui/shell/BeiUi.kt`（`BeiCard` / `BeiPillButton` / `ChoiceChip`）与 `AppShell.NavItem`。

**规则一：焦点是唯一的位置指示。** 列表 / 网格里高亮项必须**明显**，因为用户没有鼠标指针可看。

| 态 | 实际落地 | 实现要点 |
|---|---|---|
| 常态 | `Glass` 底（白 6%）+ `Border` 1dp（白 15%） | — |
| **焦点** | 卡片 / 按钮：**底色不变**，描边换成 2dp 香槟 + 微放大 `FOCUS_SCALE` = 1.03<br>导航项：**再加一整块** `AccentFill`（香槟 15%）底 | 只用"描边 + 放大"两样即可（规范要求三选二） |
| 按下 | 底色不变，靠 `PRESS_SCALE` = 0.97 缩回，`PRESS_DURATION_MS` = 160ms | 200ms 内完成，给"按到了"的即时反馈 |
| 不可用 | 文字降到 `TextMuted`（白 40%），不上色 | 不要靠颜色单独表意 |

**规则二：一处 UI 里只允许存在一块高亮，且它跟焦点走。**

这条是**踩过两次坑**换来的，写下来给将来的人：

| 坑 | 表现 | 修法 |
|---|---|---|
| 底色交给 `Surface` 的 `focusedContainerColor` | 焦点走了旧项**不撤** | 底色改由自绘层控制，与描边**同源**（同一个 `focused` state） |
| 自绘时写成 `if (focused \|\| selected)` | "选中"那块底**赖着不走**，看起来焦点上下移动时"颜色没跟着变" | 底色、竖线、描边**全部只看 `focused`** |

所以在**导航栏**里：

| 状态 | 视觉 |
|---|---|
| **焦点** | 香槟 15% 底 + 2dp 香槟描边 + **左侧竖线** + 微放大 |
| **选中**（当前页，焦点不在它上面时） | 只有**亮香槟文字 + 加粗**，**没有底、没有竖线、没有描边** |
| 其它 | 白 60% 文字 |

「选中」与「焦点」是两个概念（焦点答"遥控器在哪"，选中答"当前看的是哪个"），
但**只能用不冲突的视觉语言表达** —— 竖线与描边归焦点，文字色与字重归选中。

**动画**（`object BeiMotion`）：

| 常量 | 值 |
|---|---|
| `BeiMotion.Easing` | `CubicBezierEasing(0.16f, 1f, 0.3f, 1f)` |
| `BeiMotion.DURATION_MS` | 350 |
| `BeiMotion.PRESS_DURATION_MS` | 160 |
| `BeiMotion.FOCUS_SCALE` | 1.03f |
| `BeiMotion.PRESS_SCALE` | 0.97f |

**实现细节**：描边叠在 `Surface` 的 content 里（`Modifier.matchParentSize().border(...)`），
这样它会跟着 `ClickableSurfaceDefaults.scale` 的放大一起缩放，焦点态不会出现描边错位。

### 2.5 覆盖层与返回键层序

电视上**没有鼠标**，返回键是用户唯一的"撤销"手段，所以每一层的退出顺序必须可预期。

| 层（由内到外） | 返回键行为 |
|---|---|
| 退出确认框 | 取消（继续看） |
| AI 字幕配置层 | 关掉，回到菜单 |
| 整页选择页（字幕轨过多时） | 关掉，**菜单随之重新出现** |
| 数值项激活态 | 退出激活 |
| 菜单二级 | 回一级 |
| 菜单一级 | 关掉菜单 |
| 提示条（notice） | **立即收掉**，不必等它自己消失 |
| 控制条（连同顶栏） | 收掉（等于提前执行那个 6 秒自动隐藏） |
| —— 没有别的层了 —— | **弹退出确认框**（默认高亮「取消」），不是直接退片 |

两条硬规则：

1. **覆盖层互斥显示，不叠透明层。** 整页选择页打开时**不渲染**菜单 —— 否则两层半透明
   遮罩会互相透出来（真机反馈过"几个透明页面叠加在一起"）。规范 §四「覆盖层分层要少」
   本来就是这个意思：层数少了合成更便宜，观感也不会糊成一团。
   （不透明的层 —— AI 配置层、运行日志层 —— 可以叠，它们已经把下面盖住了。）
2. **破坏性操作要确认。** "退出播放"会丢掉进度，所以必须经过确认框；确认框的默认落点是
   **「取消」**，不能是"确认退出"。

---

## 三、仍然保留的排版/视觉纪律（与设备无关）

- **禁止**紫粉 AI 渐变（`#667eea` / `#764ba2` / `#f093fb` 一类）
- **禁止**给面板本身上色（玻璃无色，颜色属于背景）；**禁止**不透明面板（`Color.White` / `Black` 直接铺）
- **禁止**多种强调色
- **禁止**嵌套卡片（卡片里套卡片）
- **禁止**渐变文字、单侧粗边框装饰（`border-left` 式 accent stripe）
- **禁止**在彩色背景上放灰字
- **零裸色**：UI 代码里**不允许出现 `Color(0x…)` / `Color.White` / `Color.Black`**，一律走 `BeiGlass`。
  当前状态：`grep -rn "Color(0x\|Color\.White\|Color\.Black" app/src/main/java` 的结果**只剩 `BeiGlass.kt` 里的定义行**。
  允许保留的只有三类，且都不算"颜色值"：
  1. `Color.Transparent`（＝无色）、`Color.Unspecified`；
  2. 字幕样式里的动态色 `Color(style.textColor)`；
  3. `android.graphics.Color.BLACK/WHITE` —— **平台 Bitmap API 的 Int 常量**，生成二维码位图用，与 Compose 主题无关。
- 正文对比度 **WCAG AA（≥ 4.5:1）**；正文行宽不必限制（电视是整屏流式布局）
- 缓动统一用 `BeiMotion.Easing`，时长 300–500ms；**不要 bounce / elastic**
- ~~一定要有 reduced-motion 的等价方案~~ → **本项目未实现**（见 §八）

---

## 四、性能红线（**MTK 电视 SoC，本项目实测**）

| 红线 | 原因 | 本项目实际做法 |
|---|---|---|
| **不做逐帧模糊**（`RenderEffect` / blur） | 每帧重算模糊直接把帧率吃光 | 光斑用 `Brush.radialGradient` 静态铺色；**全项目零 blur** |
| **不做全屏大面积半透明叠白** | 大块 alpha 混合同样贵，且画面发灰 | 侧边菜单宽度 = 屏宽 × 0.85 × (0.58 + 0.42)，两块各自 ≤ 55% 屏宽 |
| **动画只动 alpha / scale / 颜色** | 动 shadow / blur / 裁剪会触发整块重绘 | `ClickableSurfaceDefaults.scale(...)`；**已删除 `Modifier.shadow`** |
| 覆盖层分层要少 | 视频 + 字幕 + 菜单 + 提示，层数多了合成也贵 | **已实现互斥**（选择页打开时不画菜单，见 §2.5） |
| 不在组合期做重活（JNI / IO） | 重组每秒几十次会把 UI 线程压死（本项目踩过） | 快照走 `StateFlow`，界面只读 |
| **视频画面上叠色上限：白 ≤ 12%、黑 ≤ 60%** | 再高就看不清画面 | 播放页面板用 `BeiGlass.Panel`（黑 55%）/ `PanelDeep` / `Scrim`；控制条底部渐变收到黑 **60%** |

> 这一条决定了 `BeiGlass` 里**为什么有两组面板 token**：`Glass` 系（白）给外壳，
> `Panel` 系（黑）给播放页。**用错方向就等于违反红线**。

---

## 五、自检清单（TV 版）

> 状态：全量改造后各项均已满足（偏离项见 §八）。

**配色**

- [x] 面板是**无色**半透明（`Glass` 白 6%），不是彩色
- [x] 强调色**只有一个**（`Accent` 香槟金），且只出现在主操作 / 当前选中 / 焦点 / 关键数字上
- [x] 深色场景有深墨底色（`Night` / `Ink`），没有紫粉渐变
- [x] 文字层级用白 100% / 60% / 40% 三档表达（`TextPrimary` / `TextSecondary` / `TextMuted`）
- [x] 全项目零裸色（见 §三）

**尺寸（电视专属）**

- [x] 正文 ≥ 13sp（`BodySize`）、次级 ≥ 11sp（`TinySize`）
- [x] 图标：大卡片 56dp、导航 22dp、状态 18dp；**同一排的外接尺寸统一**
- [x] 可点区域 ≥ 48dp（`MinTouchTarget`）
- [x] 四周留了 40dp / 28dp 安全边距
- [x] 卡片圆角 18dp（`CardRadius`）、描边 1dp / 焦点 2dp（`BorderFocus`）

**交互**

- [x] 有明确的**焦点态**，且与"当前选中"不撞视觉
- [x] 一处 UI 里**只有一块高亮**，且跟焦点走（见 §2.4 规则二）
- [x] 有按下反馈（`PRESS_SCALE`）
- [x] 过渡时长 350ms、curve `(0.16,1,0.3,1)`
- [x] 没有 hover 依赖（遥控器没有鼠标）
- [x] 覆盖层不叠透明层；返回键层序可预期（见 §2.5）

**性能**

- [x] 没有任何逐帧 blur
- [x] 半透明大面积铺色不超过屏宽 60%
- [x] 动画只动 alpha / scale / 颜色
- [x] 组合期没有 JNI / IO

---

## 六、决策记录

> 本节原先是「待决策」，列出两套并存的色板。**现在已落地，本节是结论**。

| 争议点 | 结论 |
|---|---|
| 强调色：靛蓝 `#4F6BED` vs 香槟金 `#E4B863` | **统一到香槟金**（严格按本规范 §1.3） |
| 外壳（媒体库 / 网络 / 设置 / AI 字幕）的基调 | **统一改深墨夜景**，与播放页共用同一套视觉语言 |

| 原文件 | 现状 |
|---|---|
| `ui/theme/BeiLightPalette.kt`（浅蓝底 + 白卡片 + 靛蓝） | **已删除**，内容并入 `ui/theme/BeiGlass.kt` |
| `ui/theme/BeiPalette.kt`（深蓝半透明 + 靛蓝） | **已删除**，内容并入 `ui/theme/BeiGlass.kt` |
| `ui/theme/BeiGlass.kt` | **唯一的调色板**（含 `BeiDims` / `BeiMotion`）。`ui/theme/` 下现在只有它和 `Theme.kt` |

关于"外壳不适合玻璃"：**已不再是问题**。当时的顾虑是"纯色浅底无物可借光"；
外壳改成深墨夜景之后，它自己就成了光源（`radialGradient` 光斑），玻璃面板照样能"借光"。

**唯一的例外**：播放页压在视频画面上的面板必须用**黑底半透明**（`Panel` 系），
不能用外壳那套白玻璃（`Glass` 系）—— §四 的硬约束是"视频画面上叠白 ≤ 12%"，
白底半透明面板会把画面洗灰。所以 `BeiGlass` 里同时保留这两组面板 token，**用途不同，别混用**。

**第二轮简约化**（色阶收敛）也记在这里：第一版落地后色阶偏多（玻璃 3 档、描边 2 档、
香槟填充 2 档 + 描边 2 档……），一屏里灰阶层次太多、看着复杂。现在只保留必要的语义，
其余档位合并 —— **token 名全部保留**（它们是语义槽位），只把值收敛。所以代码里会出现
"名字不同、值相同"的 token，那是刻意的，不是漏改。

---

## 七、落点索引（实际部署位置）

| 落地物 | 位置 |
|---|---|
| 配色 / 尺寸 / 动效的**唯一来源** | `ui/theme/BeiGlass.kt`（`BeiGlass` / `BeiDims` / `BeiMotion`） |
| 外壳骨架：夜景观底 + 光斑 + 玻璃导航 + 品牌块 | `ui/shell/AppShell.kt` |
| 基础件：页面标题 / 玻璃卡 / 大图标卡 / 静态卡 / 胶囊按钮 / 选项胶囊 / 来源图标 | `ui/shell/BeiUi.kt` |
| 媒体库（16:9 封面用 `Night → Ink` 渐变 + 香槟播放符号） | `ui/library/LibraryScreen.kt` |
| 网络位置（**6 张**大图标卡，3 列 × 2 行） | `ui/network/NetworkScreen.kt` |
| 「本地网络」子页（进入即扫描 + 「＋ 手动配置」+ 已保存入口） | `ui/network/LocalNetworkScreen.kt` |
| 目录浏览 + SMB 表单（自绘 `EntryGlyph` 替代 emoji） | `ui/network/RemoteBrowseScreen.kt` |
| WebDAV 表单（玻璃输入框 + 香槟光标） | `ui/network/WebDavServerForm.kt` |
| 夸克 / 百度 / 阿里扫码登录（夜景底 + 玻璃面板 + `QrSurface` 二维码白底） | `ui/network/{Quark,Baidu,Ali}LoginScreen.kt` |
| 设置页（选项胶囊 + 48dp 可点区色点） | `ui/settings/SettingsScreen.kt` |
| AI 字幕配置（含二维码白底） | `ui/settings/AiSettingsScreen.kt` |
| 播放页：控制条 / 顶栏 / 信息层 / 侧边菜单 / 整页选择页 / AI 进度条 / 退出确认框 / 即时层 | `ui/player/PlayerScreen.kt` + `ui/player/components/*` |
| 字幕样式（含卡拉OK 未唱段扫亮色 `KARAOKE_HIGHLIGHT_ARGB`） | `player/SubtitleStyle.kt`、`ui/player/components/SubtitleOverlay.kt` |
| 主题外壳（`tv-material3` 的 `darkColorScheme()`） | `ui/theme/Theme.kt` |

---

## 八、已知偏离与未做项

诚实记录，别当成已满足：

| 项 | 规范怎么说 | 实际 |
|---|---|---|
| reduced-motion 等价方案（§三 末条） | 关掉放大动画、只留颜色变化 | **未实现**。焦点放大固定 1.03，没有跟随系统"减少动画"设置 |
| inset 阴影的降级做法（§〇） | 1dp 顶边亮描边 + 1dp 底边暗描边 | **未做**，统一用整体 1dp 描边 —— 电视上那两条 1dp 几乎看不见，收益低于复杂度 |
| 卡片内小图标 24–32dp（§2.2） | 24–32dp | 目录/文件标记实际用 **18dp**（`IconStatus`），行内元素与 16sp 标题等高更协调 |
| 列表行高 28–36dp（§2.3） | 28–36dp | 未硬编码行高，内容自适应；实测约 44–52dp，电视上更好按 |
| `BeiGlass.Scrim` 与 `Panel` | 两个不同语义 | 当前**值相同**（都是黑 55%），语义上分开只为将来独立调整 |
| `BeiGlass.AccentDim` | "次级强调底" | **当前无独立视觉**（＝ `AccentFill`），槽位留待将来用 |

未验证的部分：以上都是**编译期**结论（`:app:compileDebugKotlin` 通过）。
**观感**（玻璃层级是否够清晰、香槟金焦点在三米外是否够醒目、黑面板会不会挡画面）
需要模拟器截图或真机确认。
