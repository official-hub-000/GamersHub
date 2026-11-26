package com.gamershub.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import com.gamershub.theme.RomKit

/**
 * The master engine switch, in the lobby header.
 *
 * This is the one control that decides whether GamersHub touches the phone at all.
 * OFF means nothing runs and nothing changed; ON arms Shizuku, overlays and capture. It lives here rather than buried in a sheet because a user who has just
 * turned it off needs to see, without hunting, that it is off.
 *
 * The switch is deliberately loud when off: the whole bar goes to a muted stroke and
 * the knocker sits left. When on it fills with the accent and shows the live channel,
 * so "on" is never ambiguous either.
 */
@Composable
fun EngineSwitch(
    on: Boolean,
    /** Which channel is answering when on: "Shizuku", "app only". */
    channel: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    val accent = LocalAccent.current
    val track by animateColorAsState(
        if (on) accent else GC.SurfaceHi, tween(RomKit.Micro), label = "engineTrack"
    )
    val knobX by animateFloatAsState(if (on) 1f else 0f, tween(220), label = "engineKnob")
    val inter = remember { MutableInteractionSource() }
    val pressed by inter.collectIsPressedAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (pressed) 0.85f else 1f }
            .clip(RomKit.R14)
            .background(if (on) GC.BrownPill else GC.Surface)
            .border(
                1.dp,
                if (on) accent.copy(alpha = 0.55f) else RomKit.Stroke,
                RomKit.R14
            )
            .clickable(inter, indication = null, enabled = enabled) { onToggle() }
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        // status light: the same dot the rest of the app uses for "live"
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (on) accent else GC.TextDim.copy(alpha = 0.45f))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "ENGINE",
                style = RomKit.Label.copy(fontSize = 11.sp, letterSpacing = 1.0.sp),
                color = if (on) accent else GC.TextDim
            )
            Text(
                when {
                    !on -> "Off — phone untouched"
                    channel.isBlank() -> "On"
                    else -> "On · $channel"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (on) GC.TextSoft else GC.TextDim,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(10.dp))

        // the switch itself: a 46x26 track with a 20dp knocker
        Box(
            Modifier
                .width(46.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(50))
                .background(if (on) accent.copy(alpha = 0.30f) else GC.TagBg)
                .border(1.dp, if (on) accent else GC.Stroke, RoundedCornerShape(50))
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (4 + 20 * knobX).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (on) accent else GC.TextDim)
            )
        }
    }
}


