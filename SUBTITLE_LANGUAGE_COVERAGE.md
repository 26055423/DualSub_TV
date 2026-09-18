# 内嵌字幕轨 · 语言码覆盖情况（全机实测）

对整机 mp4 / mkv / avi 做一遍扫描，直接解析容器头部（不依赖 ffprobe / mkvtoolnix），
统计内嵌字幕轨的语言码，看 App 的 `normalizeLanguage()` 映射表覆盖到什么程度。

---

## 一、扫描范围

| 项 | 数量 |
|---|---|
| 扫到的视频文件 | **14730** |
| └ .mp4 | 13741 |
| └ .avi | 628 |
| └ .mov | 258 |
| └ .mkv | 96 |
| └ .m4v | 7 |
| 其中**带内嵌字幕轨**的 | **57** |
| 字幕轨总数 | **1196** |

成功解析的容器类型：mp4 14006、avi 628、mkv 96

---

## 二、语言码覆盖情况

在能读出语言码的 **1116** 条字幕轨里：

- ✅ 映射表**已覆盖**：**487** 条（43.6%）
- ❌ 映射表**未覆盖**：**629** 条（56.4%）

### 未覆盖的语言码（界面上会原样显示 ISO 码）

| 语言码 | 字幕轨条数 | 建议显示名 |
|---|---|---|
| `cze` | 30 | Čeština |
| `dan` | 30 | Dansk |
| `gre` | 30 | Ελληνικά |
| `fin` | 30 | Suomi |
| `heb` | 30 | עברית |
| `hun` | 30 | Magyar |
| `dut` | 30 | Nederlands |
| `swe` | 30 | Svenska |
| `tur` | 30 | Türkçe |
| `may` | 29 | Bahasa Melayu |
| `pol` | 29 | Polski |
| `nob` | 28 | Norsk Bokmål |
| `rum` | 28 | Română |
| `cat` | 26 | Català |
| `baq` | 26 | Euskara |
| `glg` | 26 | Galego |
| `hin` | 26 | हिन्दी |
| `tam` | 26 | தமிழ் |
| `tel` | 26 | తెలుగు |
| `kan` | 25 | ಕನ್ನಡ |
| `mal` | 25 | മലയാളം |
| `bul` | 4 | Български |
| `est` | 4 | Eesti |
| `lit` | 4 | Lietuvių |
| `lav` | 4 | Latviešu |
| `slv` | 4 | Slovenščina |
| `hrv` | 3 | Hrvatski |
| `ukr` | 3 | Українська |
| `fil` | 2 | Filipino |
| `ice` | 2 | Íslenska |
| `mac` | 2 | Македонски |
| `srp` | 2 | Српски |
| `slo` | 2 | Slovenčina |
| `nor` | 2 | Norsk |
| `mon` | 1 | Монгол хэл |

---

## 三、已覆盖的语言码（命中情况）

| 语言码 | 条数 | 现显示名 |
|---|---|---|
| `chi` | 77 | 中文 |
| `spa` | 65 | Español |
| `fre` | 61 | Français |
| `por` | 60 | Português |
| `ger` | 30 | Deutsch |
| `ind` | 30 | Bahasa Indonesia |
| `ita` | 30 | Italiano |
| `tha` | 30 | ภาษาไทย |
| `ara` | 28 | العربية |
| `jpn` | 28 | 日本語 |
| `kor` | 27 | 한국어 |
| `eng` | 12 | English |
| `vie` | 5 | Tiếng Việt |
| `rus` | 4 | Русский |

---

## 四、字幕编码分布

| CodecID | 条数 | 说明 |
|---|---|---|
| `S_TEXT/UTF8` | 1154 | SubRip (.srt) |
| `S_TEXT/ASS` | 19 | ASS/SSA (.ass) |
| `S_TEXT/SSA` | 16 | ASS/SSA (.ass) |
| `TEXT` | 7 |  |

---

## 五、`Name` 字段高频取值

这些是媒体容器里直接带的轨名（Media3 会把它放进 `MediaFormat.label`，
对应 App 里的 `TrackInfo.title`）。可以看到主流流媒体用的是「Slanguage / 地区名」两套命名。

| Name | 次数 |
|---|---|
| `India` | 75 |
| `European` | 48 |
| `Spain` | 45 |
| `SDH` | 30 |
| `Traditional` | 19 |
| `Latin America` | 19 |
| `Simplified` | 18 |
| `Brazil` | 18 |
| `Malaysia` | 17 |
| `Canada` | 16 |
| `world` | 15 |
| `Czechia` | 15 |
| `Denmark` | 15 |
| `Greece` | 15 |
| `Israel` | 15 |
| `Japan` | 15 |
| `South Korea` | 15 |
| `Norway` | 15 |
| `Sweden` | 15 |
| `中文` | 12 |
| `Danish` | 9 |
| `Finnish` | 9 |
| `Japanese` | 9 |
| `Norwegian` | 9 |
| `Swedish` | 9 |
| `英文` | 9 |
| `简体` | 8 |
| `繁體` | 8 |
| `Latin American` | 8 |
| `Canadian` | 8 |
| `Brazilian` | 8 |
| `中英` | 8 |
| `英中` | 8 |
| `Forced` | 6 |
| `中文字幕` | 5 |
| `SubtitleHandler` | 4 |
| `Français (Canada)` | 4 |
| `中英文` | 4 |
| `Español (Latinoamérica)` | 3 |
| `English` | 3 |

---

## 六、多字幕轨文件 Top

字幕轨最多的文件（这类片源才真正考验名称缩短逻辑）：

| 字幕轨数 | 文件 |
|---|---|
| 42 | `War.Machine.2026.1080p.NF.WEB-DL.DDP.5.1.Atmos.H.264-DreamHD.mkv` |
| 41 | `Wolfs.2024.2160p.ATVP.WEB-DL.H265.HDR.DDP5.1.Atmos-SONYHD.mkv` |
| 40 | `Reacher.S02E08.Fly.Boy.2160p.AMZN.WEB-DL.DDP.5.1.Atmos.HDR10+.H.265-Blac` |
| 38 | `Stuart.Fails.to.Save.the.Universe.S01E01.Spoiler.Gary.Dies.2160p.HBOMax.` |
| 36 | `Fallout.S02E08.The.Strip.2160p.AMZN.WEB-DL.DDP.5.1.Atmos.H.265-BlackTV.m` |
| 36 | `Fallout.S02E05.The.Wrangler.2160p.AMZN.WEB-DL.DDP.5.1.Atmos.H.265-BlackT` |
| 36 | `Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.mkv` |
| 36 | `Fallout.S02E03.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.H.265-ColorTV.mkv` |
| 36 | `Fallout.S02E02.The.Golden.Rule.2160p.AMZN.WEB-DL.DDP.5.1.Atmos.H.265-Bla` |
| 36 | `Fallout.S02E04.The.Demon.in.the.Snow.2160p.AMZN.WEB-DL.DDP.5.1.Atmos.H.2` |
| 36 | `Fallout.S02E06.The.Other.Player.2160p.AMZN.WEB-DL.DDP.5.1.Atmos.H.265-Bl` |
| 36 | `Fallout.S02E07.The.Handoff.2160p.AMZN.WEB-DL.DDP.5.1.Atmos.HDR10+.H.265-` |
| 36 | `侠探杰克.Reacher.S03E01.2025.2160p.AMZN.WEB-DL.DDP5.1.Atmos.H265.HDR-ZeroTV.` |
| 36 | `侠探杰克.Reacher.S03E02.2025.2160p.AMZN.WEB-DL.DDP5.1.Atmos.H265.HDR-ZeroTV.` |
| 36 | `侠探杰克.Reacher.S03E03.2025.2160p.AMZN.WEB-DL.DDP5.1.Atmos.H265.HDR-ZeroTV.` |

---

## 七、结论与建议

### 1. 主要问题是**映射表缺口**：56.4% 的字幕轨会显示成 ISO 码

`normalizeLanguage()` 现覆盖 15 种语言，实测命中 **43.6%**（487/1116），
其余 **629 条（56.4%）** 落在下面 35 个码上。

**按收益排序，先补这 21 个**（各 25~30 条，合计 560+ 条）：

```
cze  dan  gre  fin  heb  hun  dut  swe  tur  may  pol  nob  rum
cat  baq  glg  hin  tam  tel  kan  mal
```

**次一批**（各 1~4 条）：`bul est lit lav slv hrv ukr fil ice mac srp slo nor mon`

上面第二节的表里已经给出每个码的**原语言写法**，可直接搬进 `normalizeLanguage()`。

### 2. 有一个该过滤掉的噪声值：MP4 的 `hdlr.name` 默认是 `SubtitleHandler`

实测 **4 条**轨的 `Name` 就是 `SubtitleHandler` —— 这是 MP4 里 handler 的默认名，
**不含任何信息**，却会被 Media3 放进 `MediaFormat.label`，最终以
`SubtitleHandler（内嵌 #N）` 的样子出现在界面上。

建议在取名时挡掉这类无意义值：`SubtitleHandler` / `Subtitle` / `TextHandler`，
以及任何以 `Handler` 结尾的纯 ASCII 名字，一律当作空。

### 3. 中文字幕有三套命名习惯，且**都已能正确显示**

| `Name` 取值 | 次数 | 说明 |
|---|---|---|
| `中文` / `英文` | 12 / 9 | 内嵌 MKV 常见；此时 `language` 常为 `und`，走「language 缺失 → 用 title」分支 |
| `简体` / `繁體` | 8 / 8 | 同上 |
| `中英` / `英中` / `中英文` | 8 / 8 / 4 | 双语字幕 |
| `中文字幕` | 5 | |

这些值都**不在** `normalizeLanguage()` 的表里，于是被**原样保留**（界面显示 `简体`、`中英`…）——
**这是对的行为**，不要把它们塞进语言表，否则会被误当成语言码去翻译。

### 4. 图片字幕在这台机器上暂时碰不到

实测字幕编码分布：`S_TEXT/UTF8` 1154、`ASS/SSA` 35、MP4 `TEXT` 7，**PGS / VOBSUB / DVBSUB 为 0 条**。
所以「图片字幕只能挂主字幕位」这条限制，在现有片源里不会触发。

### 5. 数据来源与可复现性

- 扫描 **14730** 个文件：mp4 13741 / avi 628 / mov 258 / mkv 96 / m4v 7；其中 **57 个**带内嵌字幕轨
- 解析器为自写的容器头解析（MKV EBML / MP4 ISO-BMFF / AVI RIFF），**不依赖 ffprobe / mkvtoolnix**
- 未覆盖统计**按条数**计；语言码比较大小写不敏感；`und`（未定义）不计入缺口
- 各片源逐条的完整字段见同目录的 `SUBTITLE_TRACK_NAMES.md`
