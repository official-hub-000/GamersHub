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

/**
 * Geometry of the emblem, in normalised 0..1 space.
 *
 * These are the actual points from the intro render: a deep open V, a thin violet
 * silhouette, a gunmetal band, and two armour wedges that meet at a fold in the
 * apex. Getting these ratios right is most of looking like the film — an earlier
 * version used a shallow rounded chevron and read as a different logo.
 */
object Geo {
    /** Silhouette: left tip, apex, right tip. */
    val L = 0.065f to 0.145f
    val A = 0.500f to 0.905f
    val R = 0.935f to 0.145f

    /** Inner edge of the violet/gunmetal band. */
    val Li = 0.215f to 0.225f
    val Ai = 0.500f to 0.700f
    val Ri = 0.785f to 0.225f

    /** Armour wings sit on that band, pulled in slightly. */
    val Lw = 0.085f to 0.185f
    val Aw = 0.500f to 0.840f
    val Rw = 0.915f to 0.185f
    val Liw = 0.205f to 0.255f
    val Aiw = 0.500f to 0.665f
    val Riw = 0.795f to 0.255f

    /**
     * Nested inner chevron. It sits inside the wings, not outside them — an
     * earlier value started at x=0.255 on y=0.560, which is left of the wing, so
     * it read as two stray lines poking out of the emblem.
     */
    val IL = 0.365f to 0.555f
    val IA = 0.500f to 0.815f
    val IR = 0.635f to 0.555f

    /** Gunmetal core notch at the apex. */
    val core = listOf(
        0.395f to 0.700f, 0.500f to 0.820f, 0.605f to 0.700f,
        0.560f to 0.690f, 0.500f to 0.760f, 0.440f to 0.690f,
    )

    /** Circuit runs: polyline, then the node it terminates in. */
    val traces = listOf(
        listOf(0.250f to 0.430f, 0.310f to 0.430f, 0.360f to 0.520f) to (0.360f to 0.520f),
        listOf(0.750f to 0.430f, 0.690f to 0.430f, 0.640f to 0.520f) to (0.640f to 0.520f),
        listOf(0.180f to 0.330f, 0.180f to 0.395f) to (0.180f to 0.395f),
        listOf(0.820f to 0.330f, 0.820f to 0.395f) to (0.820f to 0.395f),
    )

    val studs = listOf(
        0.170f to 0.325f, 0.240f to 0.470f, 0.760f to 0.470f, 0.830f to 0.325f,
    )
}

/** Build a closed path from normalised points. */
private fun pathOf(w: Float, h: Float, points: List<Pair<Float, Float>>): Path =
    Path().apply {
        points.forEachIndexed { i, (x, y) ->
            if (i == 0) moveTo(w * x, h * y) else lineTo(w * x, h * y)
        }
        close()
    }

/** Build an open polyline. */
private fun lineOf(w: Float, h: Float, points: List<Pair<Float, Float>>): Path =
    Path().apply {
        points.forEachIndexed { i, (x, y) ->
            if (i == 0) moveTo(w * x, h * y) else lineTo(w * x, h * y)
        }
    }

/**
 * The full emblem, drawn from the same geometry as the intro render.
 *
 * Painted back-to-front the way the film builds it: violet skeleton, gunmetal
 * band, armour wedges that fold at the apex, inner chevron, core notch, then the
 * circuit traces. [progress] animates the build, so the mark can assemble itself
 * on screen exactly as it does in the opening shot.
 *
 * Static in steady state — it is drawn once per size change, not per frame, so it
 * is cheap enough to sit in a backdrop.
 */
@Composable
fun GhostMark(
    modifier: Modifier = Modifier,
    accent: Color = GsEmblem.Red,
    accentPair: Color = GsEmblem.Orange,
    /** 0 = violet skeleton only, 1 = complete emblem. */
    progress: Float = 1f,
    showStuds: Boolean = true,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = progress.coerceIn(0f, 1f)

        with(Geo) {
            // 1. violet silhouette
            drawPath(
                lineOf(w, h, listOf(L, A, R)),
                GsEmblem.Violet.copy(alpha = 0.90f * p),
                style = Stroke(width = h * 0.011f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            if (p > 0.25f) {
                // 2. gunmetal band between silhouette and wings
                val band = pathOf(w, h, listOf(L, A, R, Ri, Ai, Li))
                drawPath(band, GsEmblem.Gun)
                drawPath(
                    lineOf(w, h, listOf(Li, Ai, Ri)),
                    GsEmblem.GunHi.copy(alpha = 0.85f),
                    style = Stroke(width = h * 0.006f, join = StrokeJoin.Round)
                )
            }

            if (p > 0.5f) {
                val wp = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)

                // 3. armour wedges. Each half gets its own gradient, so the apex
                //    reads as a fold rather than one flat sweep.
                val leftWing = pathOf(w, h, listOf(Lw, Aw, Ai, Liw))
                val rightWing = pathOf(w, h, listOf(Aw, Rw, Riw, Ai))

                drawPath(
                    leftWing,
                    Brush.linearGradient(
                        listOf(GsEmblem.RedDeep, GsEmblem.Red, accent),
                        start = Offset(w * 0.10f, h * 0.19f),
                        end = Offset(w * 0.50f, h * 0.84f)
                    ),
                    alpha = wp
                )
                drawPath(
                    rightWing,
                    Brush.linearGradient(
                        listOf(accentPair, GsEmblem.Orange, GsEmblem.Amber),
                        start = Offset(w * 0.50f, h * 0.84f),
                        end = Offset(w * 0.92f, h * 0.19f)
                    ),
                    alpha = wp
                )

                // catch-light along the leading edge
                drawPath(
                    lineOf(w, h, listOf(Lw, Aw, Rw)),
                    Color.White.copy(alpha = 0.40f * wp),
                    style = Stroke(width = h * 0.005f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            if (p > 0.7f) {
                // 4. nested inner chevron
                drawPath(
                    lineOf(w, h, listOf(IL, IA, IR)),
                    GsEmblem.VioletSoft.copy(alpha = 0.85f),
                    style = Stroke(width = h * 0.008f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // 5. core notch
                val corePath = pathOf(w, h, core)
                drawPath(corePath, GsEmblem.GunLo)
                drawPath(
                    corePath,
                    GsEmblem.GunHi.copy(alpha = 0.70f),
                    style = Stroke(width = h * 0.005f, join = StrokeJoin.Round)
                )
            }

            if (p > 0.88f) {
                // 6. circuit traces with terminal nodes
                val tp = ((p - 0.88f) / 0.12f).coerceIn(0f, 1f)
                traces.forEach { (run, node) ->
                    drawPath(
                        lineOf(w, h, run),
                        GsEmblem.Trace.copy(alpha = 0.75f * tp),
                        style = Stroke(width = h * 0.005f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    val (nx, ny) = node
                    drawCircle(
                        GsEmblem.Amber.copy(alpha = 0.30f * tp),
                        radius = h * 0.020f,
                        center = Offset(w * nx, h * ny)
                    )
                    drawCircle(GsEmblem.Amber, radius = h * 0.011f, center = Offset(w * nx, h * ny))
                }
            }

            // 7. studs
            if (showStuds && p > 0.6f) {
                val sp = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
                studs.forEach { (x, y) ->
                    drawCircle(
                        GsEmblem.Amber.copy(alpha = 0.90f * sp),
                        radius = h * 0.0075f,
                        center = Offset(w * x, h * y)
                    )
                }
            }
        }
    }
}

/**
 * Compact, live version of the emblem for chrome.
 *
 * The same silhouette as [GhostMark], sized for a 40-60dp slot. Unlike a logo,
 * its parts are driven by real numbers:
 *
 *  - [energy] fills the two rails under the wings (free memory fraction)
 *  - [load] drives the core glow and the trace brightness
 *  - [active] false draws the mark inert — dashed and desaturated — so "engine
 *    off" looks off rather than merely idle
 *  - [sweep] runs a light along the traces, as in the film's opening shot
 */
@Composable
fun MarkCore(
    modifier: Modifier = Modifier,
    energy: Float = 0.5f,
    load: Float = 0.3f,
    active: Boolean = true,
    accent: Color = GsEmblem.Red,
    accentPair: Color = GsEmblem.Orange,
    showRails: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "mark")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (active) 2400 else 6000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "sweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (active) 900 else 2200, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val a = if (active) 1f else 0.30f
        val e = energy.coerceIn(0f, 1f)
        val l = load.coerceIn(0f, 1f)

        with(Geo) {
            // silhouette
            drawPath(
                lineOf(w, h, listOf(L, A, R)),
                GsEmblem.Violet.copy(alpha = 0.90f * a),
                style = Stroke(
                    width = h * 0.011f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = if (active) null
                    else PathEffect.dashPathEffect(floatArrayOf(h * 0.06f, h * 0.05f))
                )
            )

            // band
            drawPath(pathOf(w, h, listOf(L, A, R, Ri, Ai, Li)), GsEmblem.Gun, alpha = a)

            // wings, each half with its own gradient so the fold survives at small size
            drawPath(
                pathOf(w, h, listOf(Lw, Aw, Ai, Liw)),
                Brush.linearGradient(
                    listOf(GsEmblem.RedDeep, accent),
                    start = Offset(w * 0.10f, h * 0.19f),
                    end = Offset(w * 0.50f, h * 0.84f)
                ),
                alpha = a
            )
            drawPath(
                pathOf(w, h, listOf(Aw, Rw, Riw, Ai)),
                Brush.linearGradient(
                    listOf(accentPair, GsEmblem.Amber),
                    start = Offset(w * 0.50f, h * 0.84f),
                    end = Offset(w * 0.92f, h * 0.19f)
                ),
                alpha = a
            )

            // leading edge
            drawPath(
                lineOf(w, h, listOf(Lw, Aw, Rw)),
                Color.White.copy(alpha = 0.38f * a),
                style = Stroke(width = h * 0.006f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // inner chevron
            drawPath(
                lineOf(w, h, listOf(IL, IA, IR)),
                GsEmblem.VioletSoft.copy(alpha = 0.80f * a),
                style = Stroke(width = h * 0.008f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // traces: lit while active, dashed when not
            val glow = 0.35f + 0.65f * sweep
            traces.take(2).forEach { (run, node) ->
                drawPath(
                    lineOf(w, h, run),
                    GsEmblem.Trace.copy(alpha = (if (active) glow else 0.30f * a)),
                    style = Stroke(width = h * 0.007f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                val (nx, ny) = node
                drawCircle(
                    GsEmblem.Amber.copy(alpha = if (active) glow else 0.30f * a),
                    radius = h * 0.016f,
                    center = Offset(w * nx, h * ny)
                )
            }

            // core: brightness tracks real load
            val coreA = (0.30f + 0.70f * l).coerceIn(0f, 1f) * a
            val cx = 0.500f
            val cy = 0.560f
            drawCircle(
                GsEmblem.Amber.copy(alpha = 0.26f * coreA * pulse),
                radius = h * 0.14f,
                center = Offset(w * cx, h * cy)
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White, accent, accentPair),
                    center = Offset(w * cx, h * cy),
                    radius = h * 0.10f
                ),
                radius = h * 0.070f,
                center = Offset(w * cx, h * cy),
                alpha = coreA
            )
        }

        // energy rails — two bars whose fill is the real number
        if (showRails) {
            val railW = w * 0.26f
            val railH = (h * 0.035f).coerceAtLeast(1.6f)
            val railY = h * 1.02f
            listOf(w * 0.22f, w * 0.52f).forEach { x ->
                drawRect(Color(0x22FFFFFF), topLeft = Offset(x, railY), size = Size(railW, railH), alpha = 0.22f * a)
                drawRect(
                    Brush.linearGradient(listOf(accent, accentPair)),
                    topLeft = Offset(x, railY),
                    size = Size(railW * e, railH),
                    alpha = a
                )
            }
        }
    }
}
