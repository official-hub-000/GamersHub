package com.gamershub.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gamershub.theme.GC
import com.gamershub.theme.RomKit

/**
 * A confirmation that says what will actually happen.
 *
 * ## Why this exists
 *
 * The user asked to be able to press anything confidently. That is not the same as
 * asking for fewer buttons — it means every button should either be safe to press or
 * say, before it acts, what it is about to do and how to get back. A confirmation that
 * only says "Are you sure?" does not help anyone decide; one that names the consequence
 * does.
 *
 * [reversible] is the part that matters. Most of GamersHub's privileged changes are
 * reversible by the journal in [com.gamershub.core.ChangeLog], and telling the user that
 * is what makes the button pressable. The ones that are not — an uninstall whose files
 * are already live — are marked, so the difference is visible rather than learned by
 * accident.
 */
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String = "DO IT",
    /** What undoes this, shown in green. Empty means "nothing does". */
    reversible: String = "",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RomKit.R18)
                .background(RomKit.Panel)
                .padding(18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        color = GC.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(GC.SurfaceHi)
                            .clickable(onClick = onDismiss)
                            .padding(9.dp)
                    ) {
                        Icon(
                            com.gamershub.ui.icons.GhIcons.Close, null,
                            tint = GC.Text, modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(body, color = GC.TextDim, fontSize = 12.sp)

                if (reversible.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            com.gamershub.ui.icons.GhIcons.CheckCircle, null,
                            tint = Color(0xFF39D98A), modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "Reversible: $reversible",
                            color = Color(0xFF39D98A), fontSize = 11.sp
                        )
                    }
                }
                if (destructive) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This one is not fully reversible. Read the line above before continuing.",
                        color = Color(0xFFE0A33A), fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton(
                        text = "CANCEL",
                        onClick = onDismiss,
                        primary = false,
                        modifier = Modifier.weight(1f)
                    )
                    BigButton(
                        text = confirmLabel,
                        onClick = { onDismiss(); onConfirm() },
                        primary = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Remembers a pending confirmation and renders it.
 *
 * Usage: `val gate = rememberConfirmGate()` then `gate.ask(...)` where the action used to
 * run directly. Keeps each call site to one line instead of a dialog of its own.
 */
class ConfirmGate internal constructor(
    private val pending: androidx.compose.runtime.MutableState<PendingConfirm>
) {
    fun ask(
        title: String,
        body: String,
        confirmLabel: String = "DO IT",
        reversible: String = "",
        destructive: Boolean = false,
        onConfirm: () -> Unit
    ) {
        pending.value = PendingConfirm(title, body, confirmLabel, reversible, destructive, onConfirm)
    }

    internal fun dismiss() = pending.value.let { confirm ->
        pending.value = confirm.copy(title = null, action = null)
    }
}

internal data class PendingConfirm(
    val title: String?,
    val body: String,
    val confirmLabel: String,
    val reversible: String,
    val destructive: Boolean,
    val action: (() -> Unit)?
)

@Composable
fun rememberConfirmGate(): ConfirmGate {
    // A MutableState, not a var: the gate outlives recomposition, and a captured `var`
    // would write into a stale slot and silently never show a dialog.
    val pending = remember { mutableStateOf(PendingConfirm(null, "", "", "", false, null)) }
    val gate = remember { ConfirmGate(pending) }
    pending.value.title?.let { title ->
        ConfirmSheet(
            title = title,
            body = pending.value.body,
            confirmLabel = pending.value.confirmLabel,
            reversible = pending.value.reversible,
            destructive = pending.value.destructive,
            onConfirm = { pending.value.action?.invoke() },
            onDismiss = { gate.dismiss() }
        )
    }
    return gate
}
