package com.gamershub

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.min

/**
 * The Game Space emblem, taken from the intro film and made the hub's identity.
 *
 * The intro is built from one shape language: a downward chevron built out of
 * nested plates, red→orange gradient armour wings, gunmetal inserts, violet
 * outline, and circuit traces that terminate in nodes. That language is what
 * makes the brand recognisable, so it belongs in the running product rather than
 * only in the video.
 *
 * This file supplies two things:
 *
 *  - [GhostMark]      the full emblem, drawn from the same geometry as the intro.
 *  - [MarkCore]       the compact version used in chrome, with live state.
 *
 * The important part is that the mark is not decoration. [MarkCore] reacts to real
 * device state: the energy rails fill with actual free memory, the core pulses
 * with actual CPU load, and the traces light up only while the engine is on. A
 * logo that reports the truth is worth more than one that looks good.
 */
object GsEmblem {

    /* ---- palette sampled from the intro frames ---- */
    val Red = Color(0xFFE01B39)
    val RedDeep = Color(0xFFB3122C)
    val Orange = Color(0xFFF0742A)
    val Amber = Color(0xFFFFA23A)
    val Violet = Color(0xFF7B5CE6)
    val VioletSoft = Color(0xFF9A7CF0)
    val Gun = Color(0xFF3A3F4A)
    val GunHi = Color(0xFF5A6170)
    val GunLo = Color(0xFF232830)
    val Trace = Color(0xFFE8663C)
}

/** Downward chevron with an optional notch, in normalised 0..1 space. */
private fun chevron(w: Float, h: Float, topY: Float, botY: Float, inset: Float, notch: Float): Path =
    Path().apply {
        moveTo(w * inset, h * topY)
        lineTo(w * 0.5f, h * (topY + 0.5f * (botY - topY) - notch))
        lineTo(w * (1f - inset), h * topY)
        lineTo(w * (1f - inset), h * (topY + (botY - topY) * 0.28f))
        lineTo(w * 0.5f, h * (botY - notch))
        lineTo(w * inset, h * (topY + (botY - topY) * 0.28f))
        close()
    }

/** The armour wing: a thick chevron band, filled with the brand gradient. */
private fun wing(w: Float, h: Float, topY: Float, thickness: Float, inset: Float): Path =
    Path().apply {
        val left = w * inset
        val right = w * (1f - inset)
        val apexY = h * (topY + thickness * 0.5f)
        moveTo(left, h * topY)
        lineTo(w * 0.5f, apexY)
        lineTo(right, h * topY)
        lineTo(right, h * (topY + thickness))
        lineTo(w * 0.5f, h * (topY + thickness * 1.5f))
        lineTo(left, h * (topY + thickness))
        close()
    }

/**
 * The full emblem from the intro. Static, so it is cheap enough for a backdrop:
 * it is drawn once per size change, not per frame.
 */
@Composable
fun GhostMark(
    modifier: Modifier = Modifier,
    accent: Color = GsEmblem.Red,
    /** 0 = only the violet skeleton, 1 = complete emblem. */
    progress: Float = 1f,
    /** Draws the diamond studs along the wings. */
    studs: Boolean = true,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = progress.coerceIn(0f, 1f)

        // 1. violet outer skeleton — the thing you see first in the film
        val skeleton = chevron(w, h, 0.20f, 0.94f, 0.06f, 0.10f)
        drawPath(skeleton, GsEmblem.Violet.copy(alpha = 0.20f * p))
        drawPath(
            skeleton,
            GsEmblem.Violet.copy(alpha = 0.80f * p),
            style = Stroke(width = h * 0.010f, join = StrokeJoin.Round)
        )

        // 2. gunmetal backing plate revealed behind the wings
        val back = chevron(w, h, 0.26f, 0.86f, 0.12f, 0.08f)
        drawPath(
            back,
            Brush.verticalGradient(
                listOf(GsEmblem.GunHi.copy(alpha = 0.55f), GsEmblem.GunLo.copy(alpha = 0.85f)),
                0.26f * h,
                0.86f * h
            )
        )

        if (p > 0.35f) {
            val wingP = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)

            // 3. the red→orange armour wings, drawn left and right separately so the
            //    gradient runs along each wing like the render does
            val grad = Brush.linearGradient(
                colors = listOf(GsEmblem.Red, GsEmblem.Orange, GsEmblem.Amber),
                start = Offset(0f, h * 0.20f),
                end = Offset(w, h * 0.62f)
            )

            val leftWing = wing(w, h, 0.22f, 0.16f, 0.06f)
            val rightWing = wing(w, h, 0.22f, 0.16f, 0.06f)

            // clip each wing to its own half so the apex seam reads as a fold
            clipRect(0f, 0f, w * 0.5f, h) {
                drawPath(leftWing, grad, alpha = wingP)
            }
            clipRect(w * 0.5f, 0f, w, h) {
                drawPath(rightWing, grad, alpha = wingP)
            }

            // the bright leading edge, the line that catches light in the render
            drawPath(
                wing(w, h, 0.22f, 0.012f, 0.06f),
                color = Color.White.copy(alpha = 0.35f * wingP)
            )

            // 4. inner violet chevron behind the core
            if (p > 0.6f) {
                val inner = chevron(w, h, 0.50f, 0.92f, 0.20f, 0.05f)
                drawPath(inner, GsEmblem.Violet.copy(alpha = 0.28f))
                drawPath(
                    inner,
                    GsEmblem.VioletSoft.copy(alpha = 0.85f),
                    style = Stroke(width = h * 0.008f, join = StrokeJoin.Round)
                )
            }

            // 5. gunmetal core plates at the base
            if (p > 0.75f) {
                val core = Path().apply {
                    moveTo(w * 0.34f, h * 0.62f)
                    lineTo(w * 0.5f, h * 0.80f)
                    lineTo(w * 0.66f, h * 0.62f)
                    lineTo(w * 0.60f, h * 0.60f)
                    lineTo(w * 0.5f, h * 0.72f)
                    lineTo(w * 0.40f, h * 0.60f)
                    close()
                }
                drawPath(core, Color(0xFF2B3038))
                drawPath(
                    core,
                    GsEmblem.GunHi.copy(alpha = 0.7f),
                    style = Stroke(width = h * 0.006f, join = StrokeJoin.Round)
                )
            }

            // 6. circuit traces with terminal nodes
            if (p > 0.85f) {
                trace(w, h, 0.17f, 0.44f, 0.30f, 0.60f, studs)
                trace(w, h, 0.83f, 0.44f, 0.70f, 0.60f, studs, flip = true)
            }
        }

        // 7. studs along the wings
        if (studs && p > 0.5f) {
            val studAlpha = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
            listOf(0.20f to 0.30f, 0.28f to 0.38f, 0.72f to 0.38f, 0.80f to 0.30f).forEach { (x, y) ->
                drawCircle(
                    GsEmblem.Amber.copy(alpha = 0.85f * studAlpha),
                    radius = h * 0.008f,
                    center = Offset(w * x, h * y)
                )
            }
        }
    }
}

/** One circuit trace: a right-angled run ending in a node, as in the intro. */
private fun DrawScope.trace(
    w: Float,
    h: Float,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    node: Boolean,
    flip: Boolean = false,
) {
    val dir = if (flip) -1f else 1f
    val path = Path().apply {
        moveTo(w * x0, h * y0)
        lineTo(w * (x0 + 0.03f * dir), h * y0)
        lineTo(w * (x1 - 0.02f * dir), h * y1)
        lineTo(w * x1, h * y1)
    }
    drawPath(
        path,
        GsEmblem.Trace.copy(alpha = 0.75f),
        style = Stroke(width = h * 0.006f, cap = StrokeCap.Round, join = StrokeJoin.Miter)
    )
    if (node) {
        drawCircle(GsEmblem.Amber, radius = h * 0.012f, center = Offset(w * x1, h * y1))
        drawCircle(
            GsEmblem.Amber.copy(alpha = 0.35f),
            radius = h * 0.022f,
            center = Offset(w * x1, h * y1)
        )
    }
}

/**
 * Compact, live version of the emblem for chrome.
 *
 * Unlike a logo, the parts are driven by real numbers:
 *  - [energy] fills the two inner rails (free memory fraction)
 *  - [load] drives the core pulse and the trace brightness
 *  - [active] false draws the mark inert (engine off) instead of pretending
 *  - [sweep] animates a light running along the traces, like the film's intro
 */
@Composable
fun MarkCore(
    modifier: Modifier = Modifier,
    energy: Float = 0.5f,
    load: Float = 0.3f,
    active: Boolean = true,
    accent: Color = GsEmblem.Red,
    accentPair: Color = GsEmblem.Orange,
    animate: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "mark")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (active) 2600 else 5200, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "sweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val side = min(w, h)
        val cx = w / 2f
        val e = energy.coerceIn(0f, 1f)
        val l = load.coerceIn(0f, 1f)
        val a = if (active) 1f else 0.34f

        val grad = Brush.linearGradient(
            listOf(accent, accentPair),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )

        // --- wing plate
        val wingPath = Path().apply {
            val topY = h * 0.16f
            moveTo(cx - side * 0.46f, topY)
            lineTo(cx, h * 0.62f)
            lineTo(cx + side * 0.46f, topY)
            lineTo(cx + side * 0.46f, topY + side * 0.20f)
            lineTo(cx, h * 0.62f + side * 0.20f)
            lineTo(cx - side * 0.46f, topY + side * 0.20f)
            close()
        }
        drawPath(wingPath, grad, alpha = 0.92f * a)

        // --- leading edge highlight
        drawPath(
            Path().apply {
                moveTo(cx - side * 0.46f, h * 0.16f)
                lineTo(cx, h * 0.62f)
                lineTo(cx + side * 0.46f, h * 0.16f)
            },
            Color.White.copy(alpha = 0.42f * a),
            style = Stroke(width = side * 0.014f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // --- violet skeleton
        val skeleton = Path().apply {
            moveTo(cx - side * 0.5f, h * 0.10f)
            lineTo(cx, h * 0.72f)
            lineTo(cx + side * 0.5f, h * 0.10f)
        }
        drawPath(
            skeleton,
            GsEmblem.Violet.copy(alpha = 0.55f * a),
            style = Stroke(width = side * 0.010f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // --- energy rails: two bars whose fill is real free memory
        val railY = h * 0.78f
        val railW = side * 0.30f
        val railH = side * 0.045f
        listOf(cx - railW - side * 0.03f, cx + side * 0.03f).forEach { x ->
            drawRect(
                Color(0x22FFFFFF).copy(alpha = 0.13f * a),
                topLeft = Offset(x, railY),
                size = Size(railW, railH)
            )
            drawRect(
                brush = grad,
                topLeft = Offset(x, railY),
                size = Size(railW * e, railH),
                alpha = 0.95f * a
            )
        }

        // --- core: brightness tracks real CPU load
        val coreA = (0.35f + 0.65f * l).coerceIn(0f, 1f) * a
        drawCircle(
            GsEmblem.Amber.copy(alpha = 0.22f * coreA * pulse),
            radius = side * 0.15f,
            center = Offset(cx, h * 0.40f)
        )
        drawCircle(
            brush = grad,
            radius = side * 0.070f,
            center = Offset(cx, h * 0.40f),
            alpha = coreA
        )
        drawCircle(
            Color.White.copy(alpha = 0.55f * coreA),
            radius = side * 0.028f,
            center = Offset(cx, h * 0.40f)
        )

        // --- traces that light up with the sweep, like the film's opening
        if (active) {
            val sway = side * 0.02f * (sweep - 0.5f) * 2f
            listOf(-1f, 1f).forEach { dir ->
                val tracePath = Path().apply {
                    moveTo(cx + dir * side * 0.18f, h * 0.56f)
                    lineTo(cx + dir * side * 0.34f, h * 0.36f)
                    lineTo(cx + dir * side * 0.44f, h * 0.36f)
                }
                drawPath(
                    tracePath,
                    GsEmblem.Trace.copy(alpha = 0.45f + 0.45f * sweep),
                    style = Stroke(width = side * 0.010f, cap = StrokeCap.Round)
                )
                drawCircle(
                    GsEmblem.Amber.copy(alpha = 0.55f + 0.45f * sweep),
                    radius = side * 0.016f,
                    center = Offset(cx + dir * side * 0.44f + sway, h * 0.36f)
                )
            }
        } else {
            // Inert: dashed, desaturated. Reads as "off", not as "idle".
            listOf(-1f, 1f).forEach { dir ->
                drawPath(
                    Path().apply {
                        moveTo(cx + dir * side * 0.18f, h * 0.56f)
                        lineTo(cx + dir * side * 0.44f, h * 0.36f)
                    },
                    GsEmblem.Violet.copy(alpha = 0.35f),
                    style = Stroke(
                        width = side * 0.008f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(side * 0.03f, side * 0.03f))
                    )
                )
            }
        }
    }
}
