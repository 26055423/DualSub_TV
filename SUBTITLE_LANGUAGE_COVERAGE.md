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

- ✅ 映射表**已覆盖**：**1116** 条（100.0%）
- ❌ 映射表**未覆盖**：**0** 条（0.0%）

**没有未覆盖的语言码** —— 现有映射表已覆盖全部实测数据。

---

## 三、已覆盖的语言码（命中情况）

| 语言码 | 条数 | 现显示名 |
|---|---|---|
| `chi` | 77 | 中文 |
| `spa` | 65 | Español |
| `fre` | 61 | Français |
| `por` | 60 | Português |
| `cze` | 30 | Čeština |
| `dan` | 30 | Dansk |
| `ger` | 30 | Deutsch |
| `gre` | 30 | Ελληνικά |
| `fin` | 30 | Suomi |
| `heb` | 30 | עברית |
| `hun` | 30 | Magyar |
| `ind` | 30 | Bahasa Indonesia |
| `ita` | 30 | Italiano |
| `dut` | 30 | Nederlands |
| `swe` | 30 | Svenska |
| `tha` | 30 | ไทย |
| `tur` | 30 | Türkçe |
| `may` | 29 | Bahasa Melayu |
| `pol` | 29 | Polski |
| `ara` | 28 | العربية |
| `jpn` | 28 | 日本語 |
| `nob` | 28 | Norsk bokmål |
| `rum` | 28 | Română |
| `kor` | 27 | 한국어 |
| `cat` | 26 | Català |
| `baq` | 26 | Euskara |
| `glg` | 26 | Galego |
| `hin` | 26 | हिन्दी |
| `tam` | 26 | தமிழ் |
| `tel` | 26 | తెలుగు |
| `kan` | 25 | ಕನ್ನಡ |
| `mal` | 25 | മലയാളം |
| `eng` | 12 | English |
| `vie` | 5 | Tiếng Việt |
| `rus` | 4 | Русский |
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

## 四、字幕编码分布

| CodecID | 条数 | 说明 |
|---|---|---|
| `S_TEXT/UTF8` | 1154 | SubRip (.srt) |
| `S_TEXT/ASS` | 19 | ASS/SSA (.ass) |
| `S_TEXT/SSA` | 16 | ASS/SSA (.ass) |
| `TEXT` | 7 |  |

---

## 五、`Name` 字段高频取值（只统计 MKV）

**为什么只统计 MKV**：`Name` 能否变成界面上的轨名，取决于播放框架读不读它——

| 容器 | 轨名来自 | Media3 会放进 `MediaFormat.label` 吗 |
|---|---|---|
| `.mkv` | `Name`(0x536E) | ✅ 会（`MatroskaExtractor` 写入 label，即 App 的 `TrackInfo.title`） |
| `.mp4` | `hdlr.name` | ❌ **不会**（`Mp4Extractor` 只读 `hdlr` 的 handlerType，字节码里没有 `label` 引用） |
| `.avi` | `strn` | ❌ 不适用（AVI 字幕流本就少见，Media3 的 AVI 路径也不设 label） |

所以 MP4 里那些 `SubtitleHandler` / `SoundHandler` 之类（编码器默认名）**不会出现在界面上**，
本表已把它们排除；同时按 App 的 `NOISE_TITLES` 规则过滤掉了 `*Handler` 类噪声值。

| MKV 的 `Name` | 次数 |
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
| `Français (Canada)` | 4 |
| `中英文` | 4 |
| `Español (Latinoamérica)` | 3 |
| `English` | 3 |
| `中英文字幕` | 3 |

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

## 七、结论

### 1. 覆盖率 **100%** —— 映射表已补全

本报告的**初版**（补表之前）测得：命中 487 条（43.6%）、未覆盖 629 条（56.4%），
缺口集中在 35 个语言码。

**该缺口已在 `b909340` 修复**：`normalizeLanguage()` 从 15 组扩到 **41 组**，
除 ISO 639-2 外还一并覆盖了 639-2/B 与 639-1 两种变体
（如 `cze` / `ces` / `cs` 都指向 `Čeština`）。

用当前映射表重跑：**1116 条全部命中，未覆盖 0 条**。

### 2. 「轨名」的一个易错点：容器里有 ≠ 界面会显示

`Name` 能不能变成界面上的轨名，取决于**播放框架读不读它**：

| 容器 | 轨名来源 | 会进界面吗 |
|---|---|---|
| `.mkv` | `Name`(0x536E) | ✅ 会（`MatroskaExtractor` 写进 `MediaFormat.label`，即 App 的 `TrackInfo.title`） |
| `.mp4` | `hdlr.name` | ❌ **不会**（`Mp4Extractor` 只读 handlerType，字节码里没有 `label` 引用） |

本报告**早期版本**曾把 MP4 的 `hdlr.name` 默认值 `SubtitleHandler` 报成
「会被显示的噪声、建议过滤」——**那是错的**：它是本扫描脚本读到的，App 不会显示。
（代码里后来确实加了 `NOISE_TITLES` 与 `endsWith("Handler")` 过滤，属额外保险，无害。）

**教训**：给 App 提「显示建议」前，要验证的是 **Media3 交给 App 的字段**，
而不是容器里恰好存在的字段。

### 3. 其余观察（仍然有效）

- **中文字幕有三套命名**：`中文`/`英文`、`简体`/`繁體`、`中英`/`英中`/`中文字幕`。
  这些值不在语言表里，会被**原样保留** —— 这是对的行为，别把它们塞进语言表。
- **图片字幕 0 条**：全机没有 PGS / VOBSUB / DVBSUB 轨；字幕编码只有
  `S_TEXT/UTF8`、`ASS/SSA` 与 MP4 的 `TEXT`。
- **多轨片源集中在流媒体剧集**：单文件最多 **42 条**字幕轨（War Machine），
  这批片源最能考验名称缩短逻辑。

### 4. 数据来源与可复现性

- 扫描 **14730** 个文件（mp4 13741 / avi 628 / mov 258 / mkv 96 / m4v 7），
  其中 **57 个**含内嵌字幕轨、共 **1196 条**
- 解析器为自写的容器头解析（MKV EBML / MP4 ISO-BMFF / AVI RIFF），
  **不依赖 ffprobe / mkvtoolnix**
- 覆盖率**按条数**统计；语言码比较大小写不敏感；`und`（未定义）不计入缺口
- 各片源逐条的完整字段见同目录 `SUBTITLE_TRACK_NAMES.md`
- 映射表对照版本：`PlayerScreen.normalizeLanguage()`（2026-09-18，41 组）
