# 内嵌字幕轨名称 · 实测数据

这份文档是**实测记录**，不是设计稿：把 `D:\Movie` 下若干真实片源的内嵌字幕轨字段
原样扒下来，再按 App 里那套「名称缩短」逻辑算一遍最终显示名，用来验证缩短效果、
并找出映射表覆盖不到的语言。

采集方式：直接解析 MKV 的 EBML 头部（`Segment → Tracks → TrackEntry`），
不依赖 `ffprobe` / `mkvtoolnix`。字段取 `CodecID`(0x86) / `Language`(0x22B59C) /
`LanguageIETF`(0x22B59D) / `Name`(0x536E)。

> **两点须注意**
>
> 1. 下表 `Language` 是 **MKV 容器里的原始码**（多为 ISO 639-2，如 `chi`）。
>    App 经 Media3 读到的可能是规范化后的 ISO 639-1（真机实测某片源显示为 `zh`），
>    因此实际显示名以真机为准；此处按原始码计算，映射表两种写法都已覆盖。
> 2. `Name` 是 MKV 的 `Name` 元素，Media3 会把它放进 `MediaFormat.label`，
>    也就是 App 里 `TrackInfo.title` 的来源（`Title`(0x7BA9) 这批片源全为空）。

---

## Fallout.S02E08.The.Strip.2160p.AMZN.WEB-DL.DDP.5.1.Atmos.H.265-BlackTV.mkv

- 总轨数 **39**，其中字幕轨 **37**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | — | en-US | SDH | `SDH（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | chi | zh-Hans | Simplified | `中文 · Simplified（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | chi | zh-Hant | Traditional | `中文 · Traditional（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | ara | ar-001 | world | `العربية · world（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | cat | ca-ES | Spain | `cat · Spain（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | cze | cs-CZ | Czechia | `cze · Czechia（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | dan | da-DK | Denmark | `dan · Denmark（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | ger | de-DE | — | `Deutsch（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | gre | el-GR | Greece | `gre · Greece（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | spa | es-419 | Latin America | `Español · Latin America（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | spa | es-ES | — | `Español（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | baq | eu-ES | Spain | `baq · Spain（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | fin | fi-FI | — | `fin（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | fre | fr-CA | Canada | `Français · Canada（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | fre | fr-FR | — | `Français（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | glg | gl-ES | Spain | `glg · Spain（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | heb | he-IL | Israel | `heb · Israel（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | hin | hi-IN | India | `hin · India（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | hun | hu-HU | — | `hun（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | ind | id-ID | — | `Bahasa Indonesia（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | ita | it-IT | — | `Italiano（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | jpn | ja-JP | Japan | `日本語 · Japan（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | kan | kn-IN | India | `kan · India（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | kor | ko-KR | South Korea | `한국어 · South Korea（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | mal | ml-IN | India | `mal · India（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | may | ms-MY | Malaysia | `may · Malaysia（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | nob | nb-NO | Norway | `nob · Norway（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | dut | nl-NL | — | `dut（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | pol | pl-PL | — | `pol（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | por | pt-BR | Brazil | `Português · Brazil（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | por | pt-PT | — | `Português（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | rum | ro-RO | — | `rum（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | swe | sv-SE | Sweden | `swe · Sweden（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | tam | ta-IN | India | `tam · India（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | tel | te-IN | India | `tel · India（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | tha | th-TH | — | `ภาษาไทย（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | tur | tr-TR | — | `tur（内嵌 #39）` |

## Fallout.S02E05.The.Wrangler.2160p.AMZN.WEB-DL.DDP.5.1.Atmos.H.265-BlackTV.mkv

- 总轨数 **39**，其中字幕轨 **37**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | — | en-US | SDH | `SDH（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | chi | zh-Hans | Simplified | `中文 · Simplified（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | chi | zh-Hant | Traditional | `中文 · Traditional（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | ara | ar-001 | world | `العربية · world（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | cat | ca-ES | Spain | `cat · Spain（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | cze | cs-CZ | Czechia | `cze · Czechia（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | dan | da-DK | Denmark | `dan · Denmark（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | ger | de-DE | — | `Deutsch（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | gre | el-GR | Greece | `gre · Greece（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | spa | es-419 | Latin America | `Español · Latin America（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | spa | es-ES | — | `Español（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | baq | eu-ES | Spain | `baq · Spain（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | fin | fi-FI | — | `fin（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | fre | fr-CA | Canada | `Français · Canada（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | fre | fr-FR | — | `Français（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | glg | gl-ES | Spain | `glg · Spain（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | heb | he-IL | Israel | `heb · Israel（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | hin | hi-IN | India | `hin · India（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | hun | hu-HU | — | `hun（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | ind | id-ID | — | `Bahasa Indonesia（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | ita | it-IT | — | `Italiano（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | jpn | ja-JP | Japan | `日本語 · Japan（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | kan | kn-IN | India | `kan · India（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | kor | ko-KR | South Korea | `한국어 · South Korea（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | mal | ml-IN | India | `mal · India（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | may | ms-MY | Malaysia | `may · Malaysia（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | nob | nb-NO | Norway | `nob · Norway（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | dut | nl-NL | — | `dut（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | pol | pl-PL | — | `pol（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | por | pt-BR | Brazil | `Português · Brazil（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | por | pt-PT | — | `Português（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | rum | ro-RO | — | `rum（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | swe | sv-SE | Sweden | `swe · Sweden（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | tam | ta-IN | India | `tam · India（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | tel | te-IN | India | `tel · India（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | tha | th-TH | — | `ภาษาไทย（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | tur | tr-TR | — | `tur（内嵌 #39）` |

## Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.mkv

- 总轨数 **39**，其中字幕轨 **37**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | — | — | English (United States) [CC] | `English (United States) [CC]（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | chi | — | 中文（简体） | `中文 · 中文（简体）（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | chi | — | 中文 (繁體) | `中文 · 中文 (繁體)（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | ara | — | العربية (العالم) | `العربية · العربية (العالم)（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | cat | — | Català (Espanya) | `cat · Català (Espanya)（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | cze | — | Čeština (Česko) | `cze · Čeština (Česko)（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | dan | — | Dansk (Danmark) | `dan · Dansk (Danmark)（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | ger | — | Deutsch (Deutschland) | `Deutsch · Deutsch (Deutschland)（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | gre | — | Ελληνικά (Ελλάδα) | `gre · Ελληνικά (Ελλάδα)（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | spa | — | Español (Latinoamérica) | `Español · Español (Latinoamérica)（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | spa | — | Español (España) | `Español · Español (España)（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | baq | — | Euskara (Espainia) | `baq · Euskara (Espainia)（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | fin | — | Suomi (Suomi) | `fin · Suomi (Suomi)（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | fre | — | Français (Canada) | `Français · Français (Canada)（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | fre | — | Français (France) | `Français · Français (France)（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | glg | — | Galego (España) | `glg · Galego (España)（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | heb | — | עברית (ישראל) | `heb · עברית (ישראל)（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | hin | — | हिन्दी (भारत) | `hin · हिन्दी (भारत)（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | hun | — | Magyar (Magyarország) | `hun · Magyar (Magyarország)（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | ind | — | Bahasa Indonesia (Indonesia) | `Bahasa Indonesia · Bahasa Indonesia (Indonesia)（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | ita | — | Italiano (Italia) | `Italiano · Italiano (Italia)（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | jpn | — | 日本語 (日本) | `日本語 · 日本語 (日本)（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | kan | — | ಕನ್ನಡ (ಭಾರತ) | `kan · ಕನ್ನಡ (ಭಾರತ)（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | kor | — | 한국어 (대한민국) | `한국어 · 한국어 (대한민국)（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | mal | — | മലയാളം (ഇന്ത്യ) | `mal · മലയാളം (ഇന്ത്യ)（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | may | — | Bahasa Malaysia (Malaysia) | `may · Bahasa Malaysia (Malaysia)（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | nob | — | Norsk Bokmål (Norge) | `nob · Norsk Bokmål (Norge)（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | dut | — | Nederlands | `dut · Nederlands（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | pol | — | Polski (Polska) | `pol · Polski (Polska)（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | por | — | Português (Brasil) | `Português · Português (Brasil)（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | por | — | Português (Portugal) | `Português · Português (Portugal)（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | rum | — | Română (România) | `rum · Română (România)（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | swe | — | Svenska (Sverige) | `swe · Svenska (Sverige)（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | tam | — | தமிழ் (இந்தியா) | `tam · தமிழ் (இந்தியா)（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | tel | — | తెలుగు (భారతదేశం) | `tel · తెలుగు (భారతదేశం)（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | tha | — | ไทย (ไทย) | `ภาษาไทย · ไทย (ไทย)（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | tur | — | Türkçe (Türkiye) | `tur · Türkçe (Türkiye)（内嵌 #39）` |

## Fallout.S02E03.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.H.265-ColorTV.mkv

- 总轨数 **40**，其中字幕轨 **38**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | — | en | English | `English（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | — | en | English [SDH] | `English [SDH]（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | chi | zh-Hans | 中文（简体） | `中文 · 中文（简体）（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | chi | zh-Hant | 中文 (繁體) | `中文 · 中文 (繁體)（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | ara | ar-001 | العربية (العالم) | `العربية · العربية (العالم)（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | cat | ca | Català | `cat · Català（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | cze | cs | Čeština | `cze · Čeština（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | dan | da | Dansk | `dan · Dansk（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | ger | de | Deutsch | `Deutsch（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | gre | el | Ελληνικά | `gre · Ελληνικά（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | spa | es-419 | Español (Latinoamérica) | `Español · Español (Latinoamérica)（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | spa | es-ES | Español (España) | `Español · Español (España)（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | baq | eu-ES | Euskara (Espainia) | `baq · Euskara (Espainia)（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | fin | fi | Suomi | `fin · Suomi（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | fre | fr-CA | Français (Canada) | `Français · Français (Canada)（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | fre | fr-FR | Français (France) | `Français · Français (France)（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | glg | gl | Galego | `glg · Galego（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | heb | he | עברית | `heb · עברית（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | hin | hi | हिन्दी | `hin · हिन्दी（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | hun | hu | Magyar | `hun · Magyar（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | ind | id | Bahasa Indonesia | `Bahasa Indonesia（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | ita | it | Italiano | `Italiano（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | jpn | ja | 日本語 | `日本語（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | kan | kn-IN | ಕನ್ನಡ (ಭಾರತ) | `kan · ಕನ್ನಡ (ಭಾರತ)（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | kor | ko | 한국어 | `한국어（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | mal | ml-IN | മലയാളം (ഇന്ത്യ) | `mal · മലയാളം (ഇന്ത്യ)（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | may | ms | Bahasa Malaysia | `may · Bahasa Malaysia（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | nob | nb | Norsk Bokmål | `nob · Norsk Bokmål（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | dut | nl | Nederlands | `dut · Nederlands（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | pol | pl | Polski | `pol · Polski（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | por | pt-BR | Português (Brasil) | `Português · Português (Brasil)（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | por | pt-PT | Português (Portugal) | `Português · Português (Portugal)（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | rum | ro | Română | `rum · Română（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | swe | sv | Svenska | `swe · Svenska（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | tam | ta | தமிழ் | `tam · தமிழ்（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | tel | te | తెలుగు | `tel · తెలుగు（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | tha | th | ไทย | `ภาษาไทย · ไทย（内嵌 #39）` |
| 40 | S_TEXT/UTF8 | tur | tr | Türkçe | `tur · Türkçe（内嵌 #40）` |

## Fallout.S02E02.The.Golden.Rule.2160p.AMZN.WEB-DL.DDP.5.1.Atmos.H.265-BlackTV.mkv

- 总轨数 **39**，其中字幕轨 **37**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | — | en-US | SDH | `SDH（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | chi | zh-Hans | Simplified | `中文 · Simplified（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | chi | zh-Hant | Traditional | `中文 · Traditional（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | ara | ar-001 | world | `العربية · world（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | cat | ca-ES | Spain | `cat · Spain（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | cze | cs-CZ | Czechia | `cze · Czechia（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | dan | da-DK | Denmark | `dan · Denmark（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | ger | de-DE | — | `Deutsch（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | gre | el-GR | Greece | `gre · Greece（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | spa | es-419 | Latin America | `Español · Latin America（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | spa | es-ES | — | `Español（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | baq | eu-ES | Spain | `baq · Spain（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | fin | fi-FI | — | `fin（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | fre | fr-CA | Canada | `Français · Canada（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | fre | fr-FR | — | `Français（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | glg | gl-ES | Spain | `glg · Spain（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | heb | he-IL | Israel | `heb · Israel（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | hin | hi-IN | India | `hin · India（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | hun | hu-HU | — | `hun（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | ind | id-ID | — | `Bahasa Indonesia（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | ita | it-IT | — | `Italiano（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | jpn | ja-JP | Japan | `日本語 · Japan（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | kan | kn-IN | India | `kan · India（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | kor | ko-KR | South Korea | `한국어 · South Korea（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | mal | ml-IN | India | `mal · India（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | may | ms-MY | Malaysia | `may · Malaysia（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | nob | nb-NO | Norway | `nob · Norway（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | dut | nl-NL | — | `dut（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | pol | pl-PL | — | `pol（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | por | pt-BR | Brazil | `Português · Brazil（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | por | pt-PT | — | `Português（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | rum | ro-RO | — | `rum（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | swe | sv-SE | Sweden | `swe · Sweden（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | tam | ta-IN | India | `tam · India（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | tel | te-IN | India | `tel · India（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | tha | th-TH | — | `ภาษาไทย（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | tur | tr-TR | — | `tur（内嵌 #39）` |

## Fallout.S02E04.The.Demon.in.the.Snow.2160p.AMZN.WEB-DL.DDP.5.1.Atmos.H.265-BlackTV.mkv

- 总轨数 **40**，其中字幕轨 **38**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | — | en-US | Forced | `Forced（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | — | en-US | SDH | `SDH（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | chi | zh-Hans | Simplified | `中文 · Simplified（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | chi | zh-Hant | Traditional | `中文 · Traditional（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | ara | ar-001 | world | `العربية · world（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | cat | ca-ES | Spain | `cat · Spain（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | cze | cs-CZ | Czechia | `cze · Czechia（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | dan | da-DK | Denmark | `dan · Denmark（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | ger | de-DE | — | `Deutsch（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | gre | el-GR | Greece | `gre · Greece（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | spa | es-419 | Latin America | `Español · Latin America（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | spa | es-ES | — | `Español（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | baq | eu-ES | Spain | `baq · Spain（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | fin | fi-FI | — | `fin（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | fre | fr-CA | Canada | `Français · Canada（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | fre | fr-FR | — | `Français（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | glg | gl-ES | Spain | `glg · Spain（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | heb | he-IL | Israel | `heb · Israel（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | hin | hi-IN | India | `hin · India（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | hun | hu-HU | — | `hun（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | ind | id-ID | — | `Bahasa Indonesia（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | ita | it-IT | — | `Italiano（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | jpn | ja-JP | Japan | `日本語 · Japan（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | kan | kn-IN | India | `kan · India（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | kor | ko-KR | South Korea | `한국어 · South Korea（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | mal | ml-IN | India | `mal · India（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | may | ms-MY | Malaysia | `may · Malaysia（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | nob | nb-NO | Norway | `nob · Norway（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | dut | nl-NL | — | `dut（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | pol | pl-PL | — | `pol（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | por | pt-BR | Brazil | `Português · Brazil（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | por | pt-PT | — | `Português（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | rum | ro-RO | — | `rum（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | swe | sv-SE | Sweden | `swe · Sweden（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | tam | ta-IN | India | `tam · India（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | tel | te-IN | India | `tel · India（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | tha | th-TH | — | `ภาษาไทย（内嵌 #39）` |
| 40 | S_TEXT/UTF8 | tur | tr-TR | — | `tur（内嵌 #40）` |

## Fallout.S02E06.The.Other.Player.2160p.AMZN.WEB-DL.DDP.5.1.Atmos.H.265-BlackTV.mkv

- 总轨数 **39**，其中字幕轨 **37**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | — | en-US | SDH | `SDH（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | chi | zh-Hans | Simplified | `中文 · Simplified（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | chi | zh-Hant | Traditional | `中文 · Traditional（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | ara | ar-001 | world | `العربية · world（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | cat | ca-ES | Spain | `cat · Spain（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | cze | cs-CZ | Czechia | `cze · Czechia（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | dan | da-DK | Denmark | `dan · Denmark（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | ger | de-DE | — | `Deutsch（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | gre | el-GR | Greece | `gre · Greece（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | spa | es-419 | Latin America | `Español · Latin America（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | spa | es-ES | — | `Español（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | baq | eu-ES | Spain | `baq · Spain（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | fin | fi-FI | — | `fin（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | fre | fr-CA | Canada | `Français · Canada（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | fre | fr-FR | — | `Français（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | glg | gl-ES | Spain | `glg · Spain（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | heb | he-IL | Israel | `heb · Israel（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | hin | hi-IN | India | `hin · India（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | hun | hu-HU | — | `hun（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | ind | id-ID | — | `Bahasa Indonesia（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | ita | it-IT | — | `Italiano（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | jpn | ja-JP | Japan | `日本語 · Japan（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | kan | kn-IN | India | `kan · India（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | kor | ko-KR | South Korea | `한국어 · South Korea（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | mal | ml-IN | India | `mal · India（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | may | ms-MY | Malaysia | `may · Malaysia（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | nob | nb-NO | Norway | `nob · Norway（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | dut | nl-NL | — | `dut（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | pol | pl-PL | — | `pol（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | por | pt-BR | Brazil | `Português · Brazil（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | por | pt-PT | — | `Português（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | rum | ro-RO | — | `rum（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | swe | sv-SE | Sweden | `swe · Sweden（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | tam | ta-IN | India | `tam · India（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | tel | te-IN | India | `tel · India（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | tha | th-TH | — | `ภาษาไทย（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | tur | tr-TR | — | `tur（内嵌 #39）` |

## Fallout.S02E07.The.Handoff.2160p.AMZN.WEB-DL.DDP.5.1.Atmos.HDR10+.H.265-BlackTV.mkv

- 总轨数 **39**，其中字幕轨 **37**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | — | en-US | SDH | `SDH（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | chi | zh-Hans | Simplified | `中文 · Simplified（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | chi | zh-Hant | Traditional | `中文 · Traditional（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | ara | ar-001 | world | `العربية · world（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | cat | ca-ES | Spain | `cat · Spain（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | cze | cs-CZ | Czechia | `cze · Czechia（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | dan | da-DK | Denmark | `dan · Denmark（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | ger | de-DE | — | `Deutsch（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | gre | el-GR | Greece | `gre · Greece（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | spa | es-419 | Latin America | `Español · Latin America（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | spa | es-ES | — | `Español（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | baq | eu-ES | Spain | `baq · Spain（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | fin | fi-FI | — | `fin（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | fre | fr-CA | Canada | `Français · Canada（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | fre | fr-FR | — | `Français（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | glg | gl-ES | Spain | `glg · Spain（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | heb | he-IL | Israel | `heb · Israel（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | hin | hi-IN | India | `hin · India（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | hun | hu-HU | — | `hun（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | ind | id-ID | — | `Bahasa Indonesia（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | ita | it-IT | — | `Italiano（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | jpn | ja-JP | Japan | `日本語 · Japan（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | kan | kn-IN | India | `kan · India（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | kor | ko-KR | South Korea | `한국어 · South Korea（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | mal | ml-IN | India | `mal · India（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | may | ms-MY | Malaysia | `may · Malaysia（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | nob | nb-NO | Norway | `nob · Norway（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | dut | nl-NL | — | `dut（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | pol | pl-PL | — | `pol（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | por | pt-BR | Brazil | `Português · Brazil（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | por | pt-PT | — | `Português（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | rum | ro-RO | — | `rum（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | swe | sv-SE | Sweden | `swe · Sweden（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | tam | ta-IN | India | `tam · India（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | tel | te-IN | India | `tel · India（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | tha | th-TH | — | `ภาษาไทย（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | tur | tr-TR | — | `tur（内嵌 #39）` |

## War.Machine.2026.1080p.NF.WEB-DL.DDP.5.1.Atmos.H.264-DreamHD.mkv

- 总轨数 **46**，其中字幕轨 **44**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | — | en | SDH | `SDH（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | — | en | — | `SubRip (.srt)（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | chi | zh-Hans | Simplified | `中文 · Simplified（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | chi | zh-Hant | Traditional | `中文 · Traditional（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | ara | ar | — | `العربية（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | cat | ca | — | `cat（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | cze | cs | — | `cze（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | dan | da | — | `dan（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | ger | de | SDH | `Deutsch · SDH（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | ger | de | — | `Deutsch（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | gre | el | — | `gre（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | spa | es-419 | Latin America (SDH) | `Español · Latin America (SDH)（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | spa | es-419 | Latin America | `Español · Latin America（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | spa | es-ES | SDH | `Español · SDH（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | spa | es-ES | — | `Español（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | baq | eu | — | `baq（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | fin | fi | — | `fin（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | fil | fil | — | `fil（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | fre | fr | SDH | `Français · SDH（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | fre | fr | — | `Français（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | fre | fr-CA | Canada | `Français · Canada（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | glg | gl | — | `glg（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | heb | he | — | `heb（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | hrv | hr | — | `hrv（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | hun | hu | — | `hun（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | ind | id | — | `Bahasa Indonesia（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | ita | it | SDH | `Italiano · SDH（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | ita | it | — | `Italiano（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | jpn | ja | — | `日本語（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | kor | ko | — | `한국어（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | may | ms | — | `may（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | nob | nb | — | `nob（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | dut | nl | — | `dut（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | pol | pl | — | `pol（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | por | pt-BR | Brazil (SDH) | `Português · Brazil (SDH)（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | por | pt-BR | Brazil | `Português · Brazil（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | por | pt-PT | — | `Português（内嵌 #39）` |
| 40 | S_TEXT/UTF8 | rum | ro | — | `rum（内嵌 #40）` |
| 41 | S_TEXT/UTF8 | rus | ru | — | `Русский（内嵌 #41）` |
| 42 | S_TEXT/UTF8 | swe | sv | — | `swe（内嵌 #42）` |
| 43 | S_TEXT/UTF8 | tha | th | — | `ภาษาไทย（内嵌 #43）` |
| 44 | S_TEXT/UTF8 | tur | tr | — | `tur（内嵌 #44）` |
| 45 | S_TEXT/UTF8 | ukr | uk | — | `ukr（内嵌 #45）` |
| 46 | S_TEXT/UTF8 | vie | vi | — | `Tiếng Việt（内嵌 #46）` |

## 侠探杰克.Reacher.S03E01.2025.2160p.AMZN.WEB-DL.DDP5.1.Atmos.H265.HDR-ZeroTV.mkv

- 总轨数 **39**，其中字幕轨 **37**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | chi | — | 简体 | `中文 · 简体（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | chi | — | 繁體 | `中文 · 繁體（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | ara | — | — | `العربية（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | cat | — | European | `cat · European（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | cze | — | — | `cze（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | dan | — | Danish | `dan · Danish（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | ger | — | — | `Deutsch（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | gre | — | — | `gre（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | — | — | SDH | `SDH（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | spa | — | Latin American | `Español · Latin American（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | spa | — | European | `Español · European（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | baq | — | European | `baq · European（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | fin | — | Finnish | `fin · Finnish（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | fre | — | Canadian | `Français · Canadian（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | fre | — | European | `Français · European（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | glg | — | European | `glg · European（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | heb | — | — | `heb（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | hin | — | — | `hin（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | hun | — | — | `hun（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | ind | — | — | `Bahasa Indonesia（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | ita | — | — | `Italiano（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | jpn | — | Japanese | `日本語 · Japanese（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | kan | — | — | `kan（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | kor | — | — | `한국어（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | mal | — | — | `mal（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | may | — | — | `may（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | nob | — | Norwegian | `nob · Norwegian（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | dut | — | — | `dut（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | pol | — | — | `pol（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | por | — | Brazilian | `Português · Brazilian（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | por | — | European | `Português · European（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | rum | — | — | `rum（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | swe | — | Swedish | `swe · Swedish（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | tam | — | — | `tam（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | tel | — | — | `tel（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | tha | — | — | `ภาษาไทย（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | tur | — | — | `tur（内嵌 #39）` |

## 侠探杰克.Reacher.S03E02.2025.2160p.AMZN.WEB-DL.DDP5.1.Atmos.H265.HDR-ZeroTV.mkv

- 总轨数 **39**，其中字幕轨 **37**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | chi | — | 简体 | `中文 · 简体（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | chi | — | 繁體 | `中文 · 繁體（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | ara | — | — | `العربية（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | cat | — | European | `cat · European（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | cze | — | — | `cze（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | dan | — | Danish | `dan · Danish（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | ger | — | — | `Deutsch（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | gre | — | — | `gre（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | — | — | SDH | `SDH（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | spa | — | Latin American | `Español · Latin American（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | spa | — | European | `Español · European（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | baq | — | European | `baq · European（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | fin | — | Finnish | `fin · Finnish（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | fre | — | Canadian | `Français · Canadian（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | fre | — | European | `Français · European（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | glg | — | European | `glg · European（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | heb | — | — | `heb（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | hin | — | — | `hin（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | hun | — | — | `hun（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | ind | — | — | `Bahasa Indonesia（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | ita | — | — | `Italiano（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | jpn | — | Japanese | `日本語 · Japanese（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | kan | — | — | `kan（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | kor | — | — | `한국어（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | mal | — | — | `mal（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | may | — | — | `may（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | nob | — | Norwegian | `nob · Norwegian（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | dut | — | — | `dut（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | pol | — | — | `pol（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | por | — | Brazilian | `Português · Brazilian（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | por | — | European | `Português · European（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | rum | — | — | `rum（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | swe | — | Swedish | `swe · Swedish（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | tam | — | — | `tam（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | tel | — | — | `tel（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | tha | — | — | `ภาษาไทย（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | tur | — | — | `tur（内嵌 #39）` |

## 侠探杰克.Reacher.S03E03.2025.2160p.AMZN.WEB-DL.DDP5.1.Atmos.H265.HDR-ZeroTV.mkv

- 总轨数 **39**，其中字幕轨 **37**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | chi | — | 简体 | `中文 · 简体（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | chi | — | 繁體 | `中文 · 繁體（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | ara | — | — | `العربية（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | cat | — | European | `cat · European（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | cze | — | — | `cze（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | dan | — | Danish | `dan · Danish（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | ger | — | — | `Deutsch（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | gre | — | — | `gre（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | — | — | SDH | `SDH（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | spa | — | Latin American | `Español · Latin American（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | spa | — | European | `Español · European（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | baq | — | European | `baq · European（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | fin | — | Finnish | `fin · Finnish（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | fre | — | Canadian | `Français · Canadian（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | fre | — | European | `Français · European（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | glg | — | European | `glg · European（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | heb | — | — | `heb（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | hin | — | — | `hin（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | hun | — | — | `hun（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | ind | — | — | `Bahasa Indonesia（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | ita | — | — | `Italiano（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | jpn | — | Japanese | `日本語 · Japanese（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | kan | — | — | `kan（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | kor | — | — | `한국어（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | mal | — | — | `mal（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | may | — | — | `may（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | nob | — | Norwegian | `nob · Norwegian（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | dut | — | — | `dut（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | pol | — | — | `pol（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | por | — | Brazilian | `Português · Brazilian（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | por | — | European | `Português · European（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | rum | — | — | `rum（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | swe | — | Swedish | `swe · Swedish（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | tam | — | — | `tam（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | tel | — | — | `tel（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | tha | — | — | `ภาษาไทย（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | tur | — | — | `tur（内嵌 #39）` |

## 侠探杰克.Reacher.S03E04.2025.2160p.AMZN.WEB-DL.DDP5.1.Atmos.H265-ZeroTV.mkv

- 总轨数 **39**，其中字幕轨 **37**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | chi | — | 简体 | `中文 · 简体（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | chi | — | 繁體 | `中文 · 繁體（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | ara | — | — | `العربية（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | cat | — | European | `cat · European（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | cze | — | — | `cze（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | dan | — | Danish | `dan · Danish（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | ger | — | — | `Deutsch（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | gre | — | — | `gre（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | — | — | SDH | `SDH（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | spa | — | Latin American | `Español · Latin American（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | spa | — | European | `Español · European（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | baq | — | European | `baq · European（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | fin | — | Finnish | `fin · Finnish（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | fre | — | Canadian | `Français · Canadian（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | fre | — | European | `Français · European（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | glg | — | European | `glg · European（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | heb | — | — | `heb（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | hin | — | — | `hin（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | hun | — | — | `hun（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | ind | — | — | `Bahasa Indonesia（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | ita | — | — | `Italiano（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | jpn | — | Japanese | `日本語 · Japanese（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | kan | — | — | `kan（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | kor | — | — | `한국어（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | mal | — | — | `mal（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | may | — | — | `may（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | nob | — | Norwegian | `nob · Norwegian（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | dut | — | — | `dut（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | pol | — | — | `pol（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | por | — | Brazilian | `Português · Brazilian（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | por | — | European | `Português · European（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | rum | — | — | `rum（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | swe | — | Swedish | `swe · Swedish（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | tam | — | — | `tam（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | tel | — | — | `tel（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | tha | — | — | `ภาษาไทย（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | tur | — | — | `tur（内嵌 #39）` |

## 侠探杰克.Reacher.S03E05.2025.2160p.AMZN.WEB-DL.DDP5.1.Atmos.H265-ZeroTV.mkv

- 总轨数 **39**，其中字幕轨 **37**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | chi | — | 简体 | `中文 · 简体（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | chi | — | 繁體 | `中文 · 繁體（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | ara | — | — | `العربية（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | cat | — | European | `cat · European（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | cze | — | — | `cze（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | dan | — | Danish | `dan · Danish（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | ger | — | — | `Deutsch（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | gre | — | — | `gre（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | — | — | SDH | `SDH（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | spa | — | Latin American | `Español · Latin American（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | spa | — | European | `Español · European（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | baq | — | European | `baq · European（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | fin | — | Finnish | `fin · Finnish（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | fre | — | Canadian | `Français · Canadian（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | fre | — | European | `Français · European（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | glg | — | European | `glg · European（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | heb | — | — | `heb（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | hin | — | — | `hin（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | hun | — | — | `hun（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | ind | — | — | `Bahasa Indonesia（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | ita | — | — | `Italiano（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | jpn | — | Japanese | `日本語 · Japanese（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | kan | — | — | `kan（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | kor | — | — | `한국어（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | mal | — | — | `mal（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | may | — | — | `may（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | nob | — | Norwegian | `nob · Norwegian（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | dut | — | — | `dut（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | pol | — | — | `pol（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | por | — | Brazilian | `Português · Brazilian（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | por | — | European | `Português · European（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | rum | — | — | `rum（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | swe | — | Swedish | `swe · Swedish（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | tam | — | — | `tam（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | tel | — | — | `tel（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | tha | — | — | `ภาษาไทย（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | tur | — | — | `tur（内嵌 #39）` |

## 侠探杰克.Reacher.S03E06.2025.2160p.AMZN.WEB-DL.DDP5.1.Atmos.H265.HDR-ZeroTV.mkv

- 总轨数 **39**，其中字幕轨 **37**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | chi | — | 简体 | `中文 · 简体（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | chi | — | 繁體 | `中文 · 繁體（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | ara | — | — | `العربية（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | cat | — | European | `cat · European（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | cze | — | — | `cze（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | dan | — | Danish | `dan · Danish（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | ger | — | — | `Deutsch（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | gre | — | — | `gre（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | — | — | SDH | `SDH（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | spa | — | Latin American | `Español · Latin American（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | spa | — | European | `Español · European（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | baq | — | European | `baq · European（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | fin | — | Finnish | `fin · Finnish（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | fre | — | Canadian | `Français · Canadian（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | fre | — | European | `Français · European（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | glg | — | European | `glg · European（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | heb | — | — | `heb（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | hin | — | — | `hin（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | hun | — | — | `hun（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | ind | — | — | `Bahasa Indonesia（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | ita | — | — | `Italiano（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | jpn | — | Japanese | `日本語 · Japanese（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | kan | — | — | `kan（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | kor | — | — | `한국어（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | mal | — | — | `mal（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | may | — | — | `may（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | nob | — | Norwegian | `nob · Norwegian（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | dut | — | — | `dut（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | pol | — | — | `pol（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | por | — | Brazilian | `Português · Brazilian（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | por | — | European | `Português · European（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | rum | — | — | `rum（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | swe | — | Swedish | `swe · Swedish（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | tam | — | — | `tam（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | tel | — | — | `tel（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | tha | — | — | `ภาษาไทย（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | tur | — | — | `tur（内嵌 #39）` |

## 侠探杰克.Reacher.S03E07.2025.2160p.AMZN.WEB-DL.DDP5.1.Atmos.H265.HDR-ZeroTV.mkv

- 总轨数 **39**，其中字幕轨 **37**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | chi | — | 简体 | `中文 · 简体（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | chi | — | 繁體 | `中文 · 繁體（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | ara | — | — | `العربية（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | cat | — | European | `cat · European（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | cze | — | — | `cze（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | dan | — | Danish | `dan · Danish（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | ger | — | — | `Deutsch（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | gre | — | — | `gre（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | — | — | SDH | `SDH（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | spa | — | Latin American | `Español · Latin American（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | spa | — | European | `Español · European（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | baq | — | European | `baq · European（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | fin | — | Finnish | `fin · Finnish（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | fre | — | Canadian | `Français · Canadian（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | fre | — | European | `Français · European（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | glg | — | European | `glg · European（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | heb | — | — | `heb（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | hin | — | — | `hin（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | hun | — | — | `hun（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | ind | — | — | `Bahasa Indonesia（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | ita | — | — | `Italiano（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | jpn | — | Japanese | `日本語 · Japanese（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | kan | — | — | `kan（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | kor | — | — | `한국어（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | mal | — | — | `mal（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | may | — | — | `may（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | nob | — | Norwegian | `nob · Norwegian（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | dut | — | — | `dut（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | pol | — | — | `pol（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | por | — | Brazilian | `Português · Brazilian（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | por | — | European | `Português · European（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | rum | — | — | `rum（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | swe | — | Swedish | `swe · Swedish（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | tam | — | — | `tam（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | tel | — | — | `tel（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | tha | — | — | `ภาษาไทย（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | tur | — | — | `tur（内嵌 #39）` |

## 侠探杰克.Reacher.S03E08.2025.2160p.AMZN.WEB-DL.DDP5.1.Atmos.H265.HDR-ZeroTV.mkv

- 总轨数 **39**，其中字幕轨 **37**

省略规则生效情况：格式相同→省略 / 语言不同→保留 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 3 | S_TEXT/UTF8 | chi | — | 简体 | `中文 · 简体（内嵌 #3）` |
| 4 | S_TEXT/UTF8 | chi | — | 繁體 | `中文 · 繁體（内嵌 #4）` |
| 5 | S_TEXT/UTF8 | ara | — | — | `العربية（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | cat | — | European | `cat · European（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | cze | — | — | `cze（内嵌 #7）` |
| 8 | S_TEXT/UTF8 | dan | — | Danish | `dan · Danish（内嵌 #8）` |
| 9 | S_TEXT/UTF8 | ger | — | — | `Deutsch（内嵌 #9）` |
| 10 | S_TEXT/UTF8 | gre | — | — | `gre（内嵌 #10）` |
| 11 | S_TEXT/UTF8 | — | — | SDH | `SDH（内嵌 #11）` |
| 12 | S_TEXT/UTF8 | spa | — | Latin American | `Español · Latin American（内嵌 #12）` |
| 13 | S_TEXT/UTF8 | spa | — | European | `Español · European（内嵌 #13）` |
| 14 | S_TEXT/UTF8 | baq | — | European | `baq · European（内嵌 #14）` |
| 15 | S_TEXT/UTF8 | fin | — | Finnish | `fin · Finnish（内嵌 #15）` |
| 16 | S_TEXT/UTF8 | fre | — | Canadian | `Français · Canadian（内嵌 #16）` |
| 17 | S_TEXT/UTF8 | fre | — | European | `Français · European（内嵌 #17）` |
| 18 | S_TEXT/UTF8 | glg | — | European | `glg · European（内嵌 #18）` |
| 19 | S_TEXT/UTF8 | heb | — | — | `heb（内嵌 #19）` |
| 20 | S_TEXT/UTF8 | hin | — | — | `hin（内嵌 #20）` |
| 21 | S_TEXT/UTF8 | hun | — | — | `hun（内嵌 #21）` |
| 22 | S_TEXT/UTF8 | ind | — | — | `Bahasa Indonesia（内嵌 #22）` |
| 23 | S_TEXT/UTF8 | ita | — | — | `Italiano（内嵌 #23）` |
| 24 | S_TEXT/UTF8 | jpn | — | Japanese | `日本語 · Japanese（内嵌 #24）` |
| 25 | S_TEXT/UTF8 | kan | — | — | `kan（内嵌 #25）` |
| 26 | S_TEXT/UTF8 | kor | — | — | `한국어（内嵌 #26）` |
| 27 | S_TEXT/UTF8 | mal | — | — | `mal（内嵌 #27）` |
| 28 | S_TEXT/UTF8 | may | — | — | `may（内嵌 #28）` |
| 29 | S_TEXT/UTF8 | nob | — | Norwegian | `nob · Norwegian（内嵌 #29）` |
| 30 | S_TEXT/UTF8 | dut | — | — | `dut（内嵌 #30）` |
| 31 | S_TEXT/UTF8 | pol | — | — | `pol（内嵌 #31）` |
| 32 | S_TEXT/UTF8 | por | — | Brazilian | `Português · Brazilian（内嵌 #32）` |
| 33 | S_TEXT/UTF8 | por | — | European | `Português · European（内嵌 #33）` |
| 34 | S_TEXT/UTF8 | rum | — | — | `rum（内嵌 #34）` |
| 35 | S_TEXT/UTF8 | swe | — | Swedish | `swe · Swedish（内嵌 #35）` |
| 36 | S_TEXT/UTF8 | tam | — | — | `tam（内嵌 #36）` |
| 37 | S_TEXT/UTF8 | tel | — | — | `tel（内嵌 #37）` |
| 38 | S_TEXT/UTF8 | tha | — | — | `ภาษาไทย（内嵌 #38）` |
| 39 | S_TEXT/UTF8 | tur | — | — | `tur（内嵌 #39）` |

## 星际迷航10..mkv

- 总轨数 **2**，其中字幕轨 **0**

_（该文件没有内嵌字幕轨）_

## 星际迷航11.mkv

- 总轨数 **2**，其中字幕轨 **0**

_（该文件没有内嵌字幕轨）_

## 星际迷航1：星际旅行.mkv

- 总轨数 **7**，其中字幕轨 **4**

省略规则生效情况：格式不同→保留 / 语言相同→省略 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 4 | S_TEXT/SSA | und | — | 中英 | `ASS/SSA (.ass) · 中英（内嵌 #4）` |
| 5 | S_TEXT/SSA | und | — | 英中 | `ASS/SSA (.ass) · 英中（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | und | — | 中文 | `SubRip (.srt) · 中文（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | und | — | 英文 | `SubRip (.srt) · 英文（内嵌 #7）` |

## 星际迷航2：可汗之怒.mkv

- 总轨数 **7**，其中字幕轨 **4**

省略规则生效情况：格式不同→保留 / 语言相同→省略 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 4 | S_TEXT/SSA | und | — | 中英 | `ASS/SSA (.ass) · 中英（内嵌 #4）` |
| 5 | S_TEXT/SSA | und | — | 英中 | `ASS/SSA (.ass) · 英中（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | und | — | 中文 | `SubRip (.srt) · 中文（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | und | — | 英文 | `SubRip (.srt) · 英文（内嵌 #7）` |

## 星际迷航3：石破天惊.mkv

- 总轨数 **7**，其中字幕轨 **4**

省略规则生效情况：格式不同→保留 / 语言相同→省略 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 4 | S_TEXT/SSA | und | — | 中英 | `ASS/SSA (.ass) · 中英（内嵌 #4）` |
| 5 | S_TEXT/SSA | und | — | 英中 | `ASS/SSA (.ass) · 英中（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | und | — | 中文 | `SubRip (.srt) · 中文（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | und | — | 英文 | `SubRip (.srt) · 英文（内嵌 #7）` |

## 星际迷航4：抢救未来.mkv

- 总轨数 **7**，其中字幕轨 **4**

省略规则生效情况：格式不同→保留 / 语言相同→省略 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 4 | S_TEXT/SSA | und | — | 中英 | `ASS/SSA (.ass) · 中英（内嵌 #4）` |
| 5 | S_TEXT/SSA | und | — | 英中 | `ASS/SSA (.ass) · 英中（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | und | — | 中文 | `SubRip (.srt) · 中文（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | und | — | 英文 | `SubRip (.srt) · 英文（内嵌 #7）` |

## 星际迷航5：终极先锋.mkv

- 总轨数 **7**，其中字幕轨 **4**

省略规则生效情况：格式不同→保留 / 语言相同→省略 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 4 | S_TEXT/SSA | und | — | 中英 | `ASS/SSA (.ass) · 中英（内嵌 #4）` |
| 5 | S_TEXT/SSA | und | — | 英中 | `ASS/SSA (.ass) · 英中（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | und | — | 中文 | `SubRip (.srt) · 中文（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | und | — | 英文 | `SubRip (.srt) · 英文（内嵌 #7）` |

## 星际迷航6：未来之城.mkv

- 总轨数 **7**，其中字幕轨 **4**

省略规则生效情况：格式不同→保留 / 语言相同→省略 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 4 | S_TEXT/SSA | und | — | 中英 | `ASS/SSA (.ass) · 中英（内嵌 #4）` |
| 5 | S_TEXT/SSA | und | — | 英中 | `ASS/SSA (.ass) · 英中（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | und | — | 中文 | `SubRip (.srt) · 中文（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | und | — | 英文 | `SubRip (.srt) · 英文（内嵌 #7）` |

## 星际迷航7：斗转星移.mkv

- 总轨数 **7**，其中字幕轨 **4**

省略规则生效情况：格式不同→保留 / 语言相同→省略 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 4 | S_TEXT/SSA | und | — | 中英 | `ASS/SSA (.ass) · 中英（内嵌 #4）` |
| 5 | S_TEXT/SSA | und | — | 英中 | `ASS/SSA (.ass) · 英中（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | und | — | 中文 | `SubRip (.srt) · 中文（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | und | — | 英文 | `SubRip (.srt) · 英文（内嵌 #7）` |

## 星际迷航8：第一类接触.mkv

- 总轨数 **7**，其中字幕轨 **4**

省略规则生效情况：格式不同→保留 / 语言相同→省略 / 标题不同→保留（“相同”即被省略）

| # | CodecID | Language | LanguageIETF | Name | 缩短后显示名 |
|---|---|---|---|---|---|
| 4 | S_TEXT/SSA | und | — | 中英 | `ASS/SSA (.ass) · 中英（内嵌 #4）` |
| 5 | S_TEXT/SSA | und | — | 英中 | `ASS/SSA (.ass) · 英中（内嵌 #5）` |
| 6 | S_TEXT/UTF8 | und | — | 中文 | `SubRip (.srt) · 中文（内嵌 #6）` |
| 7 | S_TEXT/UTF8 | und | — | 英文 | `SubRip (.srt) · 英文（内嵌 #7）` |

## 星际迷航9..mkv

- 总轨数 **2**，其中字幕轨 **0**

_（该文件没有内嵌字幕轨）_

---

## 映射表未覆盖的语言码

以下语言码**没有出现在 `normalizeLanguage()` 的表里**，界面上会原样显示 ISO 码：

| 语言码 | 命中次数 | 出现在 |
|---|---|---|
| `cat` | 17 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `cze` | 17 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `dan` | 17 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `gre` | 17 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `baq` | 17 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `fin` | 17 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `glg` | 17 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `heb` | 17 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `hun` | 17 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `may` | 17 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `nob` | 17 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `dut` | 17 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `pol` | 17 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `rum` | 17 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `swe` | 17 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `tur` | 17 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `hin` | 16 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `kan` | 16 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `mal` | 16 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `tam` | 16 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `tel` | 16 | Fallout.S02E01.2024.2160p.AMZN.WEB-DL.DDP5.1.Atmos.HDR.H.265-ColorTV.m |
| `fil` | 1 | War.Machine.2026.1080p.NF.WEB-DL.DDP.5.1.Atmos.H.264-DreamHD.mkv |
| `hrv` | 1 | War.Machine.2026.1080p.NF.WEB-DL.DDP.5.1.Atmos.H.264-DreamHD.mkv |
| `ukr` | 1 | War.Machine.2026.1080p.NF.WEB-DL.DDP.5.1.Atmos.H.264-DreamHD.mkv |

---

统计：共 **28** 个片源、**670** 条内嵌字幕轨。
