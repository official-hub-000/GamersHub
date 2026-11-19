package com.gamershub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gamershub.theme.GC

/**
 * The Game Space surface language.
 *
 * The hub used to be flat boxes: a solid fill and a 1px border, on every card, at
 * every size. Flat is why it read as "basic" next to the lobby, which has gradients,
 * rims and a lit emblem behind everything.
 *
 * These helpers give the hub the same depth without adding a blur pass (the whole
 * screen updates every 2s with live stats, so anything expensive here is paid
 * constantly):
 *
 *  - `glass()`      a two-stop vertical fill plus a top rim highlight, which is what
 *                   makes a panel read as a lit sheet rather than a rectangle
 *  - `accentBloom()`  a radial wash in the accent behind a card, the same glow the
 *                   launcher icon has
 *  - `edgeLight()`  a 1.5px inner line on the top edge only, so the light appears
 *                   to come from above, consistently
 *
 * Everything is a draw pass, not a composable layer, so it costs one draw call.
 */

/** Two-stop panel fill + top rim. The base surface for every hub card. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(12.dp),
    /** 0 = flat, 1 = lifted. The featured card uses 1, small stat boxes 0.35. */
    lift: Float = 0.5f,
    accent: Color = GC.Signal,
    tint: Color = Color(0xFF0D1017)
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                tint.copy(alpha = (0.86f + 0.10f * lift)),
                tint.copy(alpha = (0.72f + 0.06f * lift))
            )
        )
    )
    .border(1.dp, Color.White.copy(alpha = 0.05f + 0.06f * lift), shape)
    .drawBehind {
        // top rim: a short bright line inset from the corners, so the panel looks
        // like it is catching light rather than being outlined
        val inset = size.width * 0.10f
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.18f + 0.14f * lift),
                    Color.Transparent
                )
            ),
            start = Offset(inset, 1f),
            end = Offset(size.width - inset, 1f),
            strokeWidth = 1.4f
        )
        // a whisper of the accent along the bottom edge ties the card to the theme
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, accent.copy(alpha = 0.10f * lift + 0.04f), Color.Transparent)
            ),
            start = Offset(inset * 1.6f, size.height - 1f),
            end = Offset(size.width - inset * 1.6f, size.height - 1f),
            strokeWidth = 1.2f
        )
    }

/** A radial accent wash behind content. Used on the featured card and the launch bar. */
fun Modifier.accentBloom(
    accent: Color = GC.Signal,
    strength: Float = 0.18f,
    center: Offset = Offset(0.5f, 0.12f)
): Modifier = this.drawBehind {
    val r = size.maxDimension * 0.72f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = strength), Color.Transparent),
            center = Offset(size.width * center.x, size.height * center.y),
            radius = r
        )
    )
}

/** A hairline that is brighter where the mouse/eye enters: left edge only. */
fun Modifier.edgeLight(accent: Color = GC.Signal, strength: Float = 0.35f): Modifier =
    this.drawBehind {
        drawLine(
            brush = Brush.verticalGradient(
                listOf(accent.copy(alpha = strength), Color.Transparent)
            ),
            start = Offset(0.8f, size.height * 0.12f),
            end = Offset(0.8f, size.height * 0.88f),
            strokeWidth = 1.6f
        )
    }

/** A soft vignette so the layout edges fall off instead of ending abruptly. */
@Composable
fun GsVignette(strength: Float = 0.55f) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color.Transparent, GC.Void.copy(alpha = strength)),
                    center = Offset(0.5f, 0.5f),
                    radius = 1600f
                )
            )
    )
}
