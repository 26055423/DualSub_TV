package com.dualsub.tv.subtitle

/**
 * 一条 ASS 字幕行解析出的特效参数。
 *
 * 所有坐标（posX/Y、moveFrom/To）均已按 PlayResX/Y 归一化到 [0,1]，
 * 可直接乘以渲染区域的实际像素宽高。
 *
 * null 表示"使用全局 SubtitleStyle 的默认值"，与覆盖为具体值区分。
 */
data class AssOverride(
    /** \an1-9，numpad 布局对应 9 个对齐点；null = 沿用全局样式（通常是 2 = BottomCenter）。 */
    val alignment: Int? = null,

    /** \pos(x,y) 归一化横坐标，[0,1]；与 posY 同时非 null 时启用绝对定位。 */
    val posX: Float? = null,
    val posY: Float? = null,

    /** \move(x1,y1,x2,y2[,t1,t2]) 起点，归一化。 */
    val moveFromX: Float? = null,
    val moveFromY: Float? = null,
    /** \move 终点，归一化。 */
    val moveToX: Float? = null,
    val moveToY: Float? = null,
    /** \move 动画开始时刻（相对本条字幕 startMs，毫秒）。 */
    val moveT1Ms: Long = 0L,
    /** \move 动画结束时刻（相对本条字幕 startMs，毫秒）；0 表示与字幕同时结束。 */
    val moveT2Ms: Long = 0L,

    /** \fad(t1,t2) 淡入时长（毫秒）。 */
    val fadeInMs: Int = 0,
    /** \fad(t1,t2) 淡出时长（毫秒）。 */
    val fadeOutMs: Int = 0,
    /** \fade(a1,a2,a3,t1,t2,t3,t4) 复杂透明度分段；非空时优先于 fadeInMs/fadeOutMs。 */
    val fadeParts: List<FadePart> = emptyList(),

    /** \t(...) 变换动画目标列表，按出现顺序排列。 */
    val transforms: List<AssTransform> = emptyList(),

    /**
     * 内联样式 span 列表，startChar/endChar 对应剥离 override 块后纯文本的字符偏移。
     * 每个 span 的样式仅覆盖该区间，区间外恢复全局样式。
     */
    val spans: List<AssSpan> = emptyList(),

    /** \k/\kf/\ko 卡拉OK段，按时间顺序。非空时启用卡拉OK渲染。 */
    val karaokeSegments: List<KaraokeSegment> = emptyList()
)

/** \fade 的一个透明度分段。alpha 范围 [0,1]，startMs/endMs 相对字幕 startMs。 */
data class FadePart(
    val alpha: Float,
    val startMs: Long,
    val endMs: Long
)

/**
 * \t([t1,t2,][accel,]<tags>) 变换动画。
 * [t1,t2] 相对字幕 startMs（毫秒）；accel 为插值加速度（1.0 = 线性）。
 * targetColor/targetFontSize 是动画目标值，null 表示该属性无动画。
 */
data class AssTransform(
    val t1Ms: Long = 0L,
    val t2Ms: Long = 0L,
    val accel: Float = 1f,
    val targetColor: Int? = null,
    val targetFontSize: Float? = null,
    val targetBold: Boolean? = null,
    val targetItalic: Boolean? = null
)

/**
 * 一段内联样式覆盖。
 * 颜色已从 ASS 的 `&HAABBGGRR&` 转换为 Android/Compose 的 ARGB Int（0xAARRGGBB）。
 */
data class AssSpan(
    val startChar: Int,
    val endChar: Int,
    val color: Int? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strikeout: Boolean? = null,
    val fontSize: Float? = null
)

/** 卡拉OK一段的文字、时长和类型。 */
data class KaraokeSegment(
    val text: String,
    /** 本段持续时长（毫秒）。 */
    val durationMs: Int,
    val type: KaraokeType
)

enum class KaraokeType {
    /** \k  到时间点时立即切换高亮色。 */
    K,
    /** \kf 在本段持续时间内从左到右渐变高亮（用两段 SpanStyle 近似）。 */
    KF,
    /** \ko 到时间点切换，但用描边色而非填充色高亮。 */
    KO
}
