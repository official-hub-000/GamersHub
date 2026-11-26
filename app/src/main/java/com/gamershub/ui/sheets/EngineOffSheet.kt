package com.gamershub.ui.sheets

import com.gamershub.ui.icons.GhIcons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.core.Channel
import com.gamershub.core.Engine
import com.gamershub.core.Restore
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import com.gamershub.theme.RomKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Confirmation sheet for turning the engine off.
 *
 * The engine switch is the one destructive control in the app, so it does not
 * fire immediately. This sheet lists exactly what is about to be undone, taken
 * from live state — not a static paragraph. If nothing is running it says so and
 * the button is a no-op instead of pretending to do work.
 *
 * It also shows the *result* afterwards, including what could not be restored and
 * why, because a restore that silently leaves the display scaled is the failure
 * mode this whole feature exists to prevent.
 */
@Composable
fun EngineOffSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()

    var stopping by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<Restore.Report?>(null) }
    var showDetail by remember { mutableStateOf(false) }

    val pending = remember {
        runCatching { Engine.pendingChanges(context) }.getOrDefault(emptyList())
    }
    val channel = remember { Channel.kind() }
    val snapshotAge = remember { Restore.snapshotAge(context) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                GhIcons.PowerSettingsNew,
                null,
                tint = if (report == null) Color(0xFFE2A03A) else Color(0xFF4ADE80),
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (report == null) "Turn engine off" else "Engine off",
                    color = GC.Text,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (report == null) "restores the phone to how it was" else "phone restored",
                    color = GC.TextDim,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (report == null) {
            // ---- what will be undone
            Text(
                "THIS WILL UNDO",
                color = accent,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            if (pending.isEmpty()) {
                Text(
                    "Nothing is running and no changes were recorded. The switch will still " +
                        "reset display and settings to platform defaults.",
                    color = GC.TextDim,
                    fontSize = 12.sp
                )
            } else {
                pending.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp)
                    ) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(GC.TextDim)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(item, color = GC.Text, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---- honesty about what cannot be reverted
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RomKit.R12)
                    .background(Color(0x1AE2A03A))
                    .border(1.dp, Color(0x33E2A03A), RomKit.R12)
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(GhIcons.Warning, null, tint = Color(0xFFE2A03A), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("What it cannot undo", color = GC.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "• An app permission Android granted (e.g. overlay) is yours, not ours\n" +
                            "• Settings Android doesn't let the shell user change are left as-is\n" +
                            "• Files you deleted cannot be brought back",
                        color = GC.TextDim,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Channel", color = GC.TextDim, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    when (channel) {
                        Channel.Kind.DAEMON -> "daemon — shell-level restore"
                        Channel.Kind.WIRELESS -> "wireless debugging — shell-level restore"
                        Channel.Kind.SHIZUKU -> "Shizuku — shell-level restore"
                        Channel.Kind.NONE -> "none — app-level restore only"
                    },
                    color = if (channel == Channel.Kind.NONE) Color(0xFFE2A03A) else GC.Text,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Snapshot", color = GC.TextDim, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(snapshotAge, color = GC.Text, fontSize = 11.sp)
            }

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetButton(
                    "KEEP RUNNING",
                    filled = false,
                    modifier = Modifier.weight(1f)
                ) { onDismiss() }

                SheetButton(
                    if (stopping) "RESTORING…" else "TURN OFF",
                    filled = true,
                    enabled = !stopping,
                    modifier = Modifier.weight(1f)
                ) {
                    scope.launch {
                        stopping = true
                        val result = withContext(Dispatchers.IO) {
                            Engine.stopEverything(context)
                            Engine.lastRestore ?: Restore.Report()
                        }
                        report = result
                        stopping = false
                    }
                }
            }
        } else {
            // ---- result
            val r = report!!
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(GhIcons.CheckCircle, null, tint = Color(0xFF4ADE80), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(r.summary(), color = GC.Text, fontSize = 14.sp)
            }
            Spacer(Modifier.height(12.dp))

            if (r.restored.isNotEmpty()) {
                Text("RESTORED", color = Color(0xFF4ADE80), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                r.restored.take(12).forEach {
                    Text("✓ $it", color = GC.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                if (r.restored.size > 12) {
                    Text("+${r.restored.size - 12} more", color = GC.TextDim, fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
            }

            AnimatedVisibility(visible = r.skipped.isNotEmpty()) {
                Column {
                    Text("SKIPPED", color = Color(0xFFE2A03A), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    r.skipped.forEach {
                        Text("· $it", color = GC.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            AnimatedVisibility(visible = r.failed.isNotEmpty()) {
                Column {
                    Text("COULD NOT RESTORE", color = Color(0xFFE23A3A), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    r.failed.forEach {
                        Text("✗ $it", color = GC.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            Text(
                "Overlays are closed and the Shizuku binder is disconnected. " +
                    "The app is now an ordinary app.",
                color = GC.TextDim,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(18.dp))
            SheetButton("DONE", filled = true, modifier = Modifier.fillMaxWidth()) { onDismiss() }
        }

        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun SheetButton(
    label: String,
    filled: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    Box(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (filled && enabled) accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                1.dp,
                if (filled && enabled) accent.copy(alpha = 0.55f) else RomKit.Stroke,
                RoundedCornerShape(11.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        if (label.endsWith("…")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = accent)
                Spacer(Modifier.width(8.dp))
                Text(label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }
        } else {
            Text(
                label,
                color = if (enabled) (if (filled) accent else GC.Text) else GC.TextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
    }
}
