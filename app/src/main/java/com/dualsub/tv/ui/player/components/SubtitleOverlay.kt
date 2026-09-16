package com.dualsub.tv.ui.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.dualsub.tv.player.SubtitleStyle
import com.dualsub.tv.subtitle.AssOverride
import com.dualsub.tv.subtitle.AssSpan
import com.dualsub.tv.subtitle.FadePart
import com.dualsub.tv.subtitle.KaraokeType
import com.dualsub.tv.subtitle.SubtitleCue

/**
 * 一路字幕的显示层。
 *
 * 当 [cue] 不含 ASS override 时，渲染逻辑与纯文本路径完全一致。
 * 当含有 override 时，依次处理：
 *   - \pos / \move → BoxWithConstraints 绝对定位 + Animatable 位移动画
 *   - \an          → 9 宫格对齐
 *   - \fad / \fade → graphicsLayer alpha 手算
 *   - \t           → Animatable 颜色/字号渐变
 *   - \b\i\u\s\c\fs → AnnotatedString SpanStyle
 *   - \k/\kf/\ko   → AnnotatedString 卡拉OK动态高亮
 *
 * [positionMs] 仅在有 \fad、\move、\t、卡拉OK 时用到；无动画 cue 传 0 即可。
 * [onHeightPx] 仅主字幕传入，用于次字幕动态避位。
 */
@Composable
fun SubtitleOverlay(
    cue: SubtitleCue?,
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
    positionMs: Long = 0L,
    onHeightPx: ((Int) -> Unit)? = null
) {
    if (cue == null || cue.text.isBlank()) {
        onHeightPx?.invoke(0)
        return
    }

    val override = cue.assOverride
    if (override == null) {
        SimpleSubtitleOverlay(
            text = cue.text,
            style = style,
            modifier = modifier,
            onHeightPx = onHeightPx
        )
        return
    }

    AssSubtitleOverlay(
        cue = cue,
        override = override,
        style = style,
        positionMs = positionMs,
        modifier = modifier,
        onHeightPx = onHeightPx
    )
}

// ─────────────────────────────────── 简单路径（无 override）

@Composable
private fun SimpleSubtitleOverlay(
    text: String,
    style: SubtitleStyle,
    modifier: Modifier,
    onHeightPx: ((Int) -> Unit)?
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = style.bottomPaddingDp.dp, start = 40.dp, end = 40.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = TextStyle(
                color = style.textColorCompose,
                fontSize = style.fontSize,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                shadow = Shadow(
                    color = style.outlineColorCompose,
                    offset = Offset.Zero,
                    blurRadius = style.outlineWidth * 2f
                )
            ),
            modifier = if (onHeightPx != null) {
                Modifier.onSizeChanged { onHeightPx(it.height) }
            } else {
                Modifier
            }
        )
    }
}

// ─────────────────────────────────── 特效路径（有 override）

@Composable
private fun AssSubtitleOverlay(
    cue: SubtitleCue,
    override: AssOverride,
    style: SubtitleStyle,
    positionMs: Long,
    modifier: Modifier,
    onHeightPx: ((Int) -> Unit)?
) {
    val elapsedMs = positionMs - cue.startMs
    val durationMs = (cue.endMs - cue.startMs).coerceAtLeast(1L)

    // ── alpha（\fad / \fade）
    val alpha = remember(override.fadeInMs, override.fadeOutMs, override.fadeParts) {
        { elapsed: Long, dur: Long ->
            computeAlpha(elapsed, dur, override.fadeInMs, override.fadeOutMs, override.fadeParts)
        }
    }

    // ── \move：Animatable 位移
    val hasMove = override.moveFromX != null && override.moveToX != null
    val animX = remember(cue.startMs) { Animatable(override.moveFromX ?: 0f) }
    val animY = remember(cue.startMs) { Animatable(override.moveFromY ?: 0f) }
    LaunchedEffect(cue.startMs, hasMove) {
        if (!hasMove) return@LaunchedEffect
        val t1 = override.moveT1Ms
        val t2 = if (override.moveT2Ms > 0L) override.moveT2Ms else durationMs
        val durMove = (t2 - t1).coerceAtLeast(1L).toInt()
        animX.snapTo(override.moveFromX!!)
        animY.snapTo(override.moveFromY!!)
        animX.animateTo(override.moveToX!!, tween(durMove, t1.toInt(), LinearEasing))
        animY.animateTo(override.moveToY!!, tween(durMove, t1.toInt(), LinearEasing))
    }

    // ── \t 颜色渐变（取第一个 targetColor）
    val transform = override.transforms.firstOrNull { it.targetColor != null }
    var animColor by remember(cue.startMs) { mutableFloatStateOf(0f) }
    LaunchedEffect(cue.startMs, transform) {
        if (transform?.targetColor == null) return@LaunchedEffect
        val t1 = transform.t1Ms.toInt()
        val dur = (transform.t2Ms - transform.t1Ms).coerceAtLeast(1L).toInt()
        val anim = Animatable(0f)
        anim.animateTo(1f, tween(dur, t1, LinearEasing))
        animColor = anim.value
    }

    // ── \t 字号渐变
    val transformSize = override.transforms.firstOrNull { it.targetFontSize != null }
    var animSize by remember(cue.startMs) { mutableFloatStateOf(-1f) }
    LaunchedEffect(cue.startMs, transformSize) {
        if (transformSize?.targetFontSize == null) return@LaunchedEffect
        val baseSize = style.fontSizeSp.toFloat()
        val targetSize = transformSize.targetFontSize
        val t1 = transformSize.t1Ms.toInt()
        val dur = (transformSize.t2Ms - transformSize.t1Ms).coerceAtLeast(1L).toInt()
        val anim = Animatable(baseSize)
        anim.animateTo(targetSize, tween(dur, t1, LinearEasing))
        animSize = anim.value
    }

    // ── AnnotatedString（spans + 卡拉OK）
    val annotated: AnnotatedString = buildAnnotatedString {
        append(cue.text)

        // 内联样式 span
        for (span in override.spans) {
            addStyle(span.toSpanStyle(style), span.startChar, span.endChar.coerceAtMost(cue.text.length))
        }

        // 卡拉OK：已过的段用高亮色，未过的段用暗色
        if (override.karaokeSegments.isNotEmpty()) {
            var segStart = 0L
            var charPos = 0
            for (seg in override.karaokeSegments) {
                val segEnd = segStart + seg.durationMs
                val active = elapsedMs in segStart until segEnd
                val passed = elapsedMs >= segEnd
                val endChar = (charPos + seg.text.length).coerceAtMost(cue.text.length)
                val highlightColor = if (seg.type == KaraokeType.KO) {
                    style.outlineColorCompose
                } else {
                    Color(0xFFFFD54F) // 金黄色高亮
                }
                when {
                    passed -> addStyle(SpanStyle(color = highlightColor), charPos, endChar)
                    active && seg.type == KaraokeType.KF -> {
                        // KF：按比例切分，左半高亮
                        val progress = ((elapsedMs - segStart).toFloat() / seg.durationMs).coerceIn(0f, 1f)
                        val midChar = charPos + (seg.text.length * progress).toInt()
                        if (midChar > charPos) addStyle(SpanStyle(color = highlightColor), charPos, midChar)
                    }
                    active -> addStyle(SpanStyle(color = highlightColor), charPos, endChar)
                }
                charPos = endChar
                segStart = segEnd
            }
        }
    }

    // ── 对齐：\an 映射到 Compose Alignment
    val alignment = anToAlignment(override.alignment ?: 2)

    // ── 基础 TextStyle（\t 颜色渐变叠加）
    val baseColor = style.textColorCompose
    val resolvedColor = if (transform?.targetColor != null) {
        val target = Color(transform.targetColor)
        lerp(baseColor, target, animColor)
    } else {
        baseColor
    }
    val resolvedFontSize = if (animSize > 0f) animSize.sp else style.fontSize
    val baseTextStyle = TextStyle(
        color = resolvedColor,
        fontSize = resolvedFontSize,
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        shadow = Shadow(
            color = style.outlineColorCompose,
            offset = Offset.Zero,
            blurRadius = style.outlineWidth * 2f
        )
    )

    val density = LocalDensity.current

    if (override.posX != null || hasMove) {
        // ── 绝对定位路径
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha(elapsedMs, durationMs) }
        ) {
            val maxWidthPx  = with(density) { maxWidth.toPx() }
            val maxHeightPx = with(density) { maxHeight.toPx() }

            val nx = if (hasMove) animX.value else (override.posX ?: 0.5f)
            val ny = if (hasMove) animY.value else (override.posY ?: 0.9f)

            val xPx = nx * maxWidthPx
            val yPx = ny * maxHeightPx

            // 对齐锚点：根据 \an 决定文字框的哪个角对齐到 (xPx, yPx)
            val xOffset = with(density) {
                (xPx - anchorXFraction(override.alignment ?: 2) * maxWidthPx * 0.3f).toDp()
            }
            val yOffset = with(density) {
                (yPx - anchorYFraction(override.alignment ?: 2) * maxHeightPx * 0.1f).toDp()
            }

            Box(
                modifier = Modifier
                    .offset(x = xOffset, y = yOffset)
                    .then(if (onHeightPx != null) Modifier.onSizeChanged { onHeightPx(it.height) } else Modifier)
            ) {
                Text(
                    text = annotated,
                    textAlign = TextAlign.Center,
                    style = baseTextStyle
                )
            }
        }
    } else {
        // ── 相对定位路径（有 \an 但无 \pos）
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = style.bottomPaddingDp.dp, start = 40.dp, end = 40.dp)
                .graphicsLayer { this.alpha = alpha(elapsedMs, durationMs) },
            contentAlignment = alignment
        ) {
            Text(
                text = annotated,
                textAlign = TextAlign.Center,
                style = baseTextStyle,
                modifier = if (onHeightPx != null) {
                    Modifier.onSizeChanged { onHeightPx(it.height) }
                } else {
                    Modifier
                }
            )
        }
    }
}

// ─────────────────────────────────── 工具函数

/** \an numpad → Compose Alignment（2 = BottomCenter 为默认）。 */
private fun anToAlignment(an: Int): Alignment = when (an) {
    1 -> Alignment.BottomStart
    2 -> Alignment.BottomCenter
    3 -> Alignment.BottomEnd
    4 -> Alignment.CenterStart
    5 -> Alignment.Center
    6 -> Alignment.CenterEnd
    7 -> Alignment.TopStart
    8 -> Alignment.TopCenter
    9 -> Alignment.TopEnd
    else -> Alignment.BottomCenter
}

/** \pos 绝对定位时，\an 决定文字框锚点的横向相对位置（0=左，0.5=中，1=右）。 */
private fun anchorXFraction(an: Int): Float = when (an % 3) {
    1 -> 0f   // 左列
    2 -> 0.5f // 中列
    0 -> 1f   // 右列
    else -> 0.5f
}

/** \an 决定文字框锚点的纵向相对位置（0=顶，0.5=中，1=底）。 */
private fun anchorYFraction(an: Int): Float = when {
    an in 7..9 -> 0f   // 顶行
    an in 4..6 -> 0.5f // 中行
    else       -> 1f   // 底行
}

/** 线性插值 Compose Color。 */
private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t
)

/** AssSpan → Compose SpanStyle。 */
private fun AssSpan.toSpanStyle(globalStyle: SubtitleStyle): SpanStyle {
    val dec = buildList {
        if (underline == true) add(TextDecoration.Underline)
        if (strikeout == true) add(TextDecoration.LineThrough)
    }.let { if (it.isEmpty()) null else TextDecoration.combine(it) }

    return SpanStyle(
        color = color?.let { Color(it) } ?: Color.Unspecified,
        fontSize = fontSize?.sp ?: globalStyle.fontSize,
        fontWeight = when (bold) {
            true  -> FontWeight.Bold
            false -> FontWeight.Normal
            null  -> null
        },
        fontStyle = when (italic) {
            true  -> FontStyle.Italic
            false -> FontStyle.Normal
            null  -> null
        },
        textDecoration = dec
    )
}

/**
 * 计算当前帧的 alpha 值（\fad / \fade）。
 *
 * [elapsedMs] 相对字幕 startMs；[durationMs] 字幕总时长。
 * [fadeParts] 非空时优先使用（\fade）；否则用 [fadeInMs]/[fadeOutMs]（\fad）。
 */
private fun computeAlpha(
    elapsedMs: Long,
    durationMs: Long,
    fadeInMs: Int,
    fadeOutMs: Int,
    fadeParts: List<FadePart>
): Float {
    if (fadeParts.isNotEmpty()) {
        for (part in fadeParts) {
            if (elapsedMs in part.startMs..part.endMs) {
                val segDur = (part.endMs - part.startMs).coerceAtLeast(1L)
                val t = (elapsedMs - part.startMs).toFloat() / segDur
                val prev = fadeParts.getOrNull(fadeParts.indexOf(part) - 1)?.alpha ?: part.alpha
                return lerp(prev, part.alpha, t)
            }
        }
        return fadeParts.lastOrNull()?.alpha ?: 1f
    }
    return when {
        fadeInMs > 0 && elapsedMs < fadeInMs ->
            elapsedMs.toFloat() / fadeInMs
        fadeOutMs > 0 && elapsedMs > durationMs - fadeOutMs ->
            ((durationMs - elapsedMs).toFloat() / fadeOutMs).coerceIn(0f, 1f)
        else -> 1f
    }
}

/** Float alpha 的线性插值（用于 fadeParts 相邻段之间）。 */
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
