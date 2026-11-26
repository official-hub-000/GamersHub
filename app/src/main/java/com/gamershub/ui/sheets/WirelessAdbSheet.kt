package com.gamershub.ui.sheets

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.core.adb.ShellDaemon
import com.gamershub.core.adb.WirelessAdb
import com.gamershub.core.adb.WirelessNotification
import com.gamershub.ui.components.AIcon
import com.gamershub.ui.components.BigButton
import com.gamershub.ui.icons.GhIcons
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import com.gamershub.theme.RomKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Wireless debugging setup — the screen that replaces "install Shizuku".
 *
 * ## What this has to get right
 *
 * Pairing is the one step a user cannot guess. The six-digit code lives behind
 * Settings › Developer options › Wireless debugging › *Pair device with pairing code*,
 * and that dialog is only open for a short window — so this sheet shows the code
 * entry first and discovers the port over mDNS automatically while offering an
 * optional manual port fallback if mDNS is blocked (VPN, emulator, restricted Wi-Fi).
 *
 * ## Why the steps are spelled out
 *
 * A failed pairing is almost never a bug in the app: it is a closed dialog, a stale
 * code, or wireless debugging turned off. Each of those has a distinct message, and
 * the sheet says which one happened instead of a generic "failed" — otherwise the only
 * recovery is toggling things at random.
 */
@Composable
fun WirelessAdbSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var showManualPorts by remember { mutableStateOf(false) }
    var manualPairPort by remember { mutableStateOf("") }
    var manualConnectPort by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(WirelessAdb.status) }
    var daemonAlive by remember { mutableStateOf(ShellDaemon.alive) }
    var daemonMsg by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(WirelessAdb.shellPort) }

    val connected = status == WirelessAdb.Status.CONNECTED

    fun refresh() {
        status = WirelessAdb.status
        port = WirelessAdb.shellPort
        daemonAlive = ShellDaemon.alive
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            WirelessAdb.attached(context)
            WirelessAdb.connected()
            WirelessAdb.discover(context, timeoutMs = 2_500)
        }
        refresh()
    }

    LaunchedEffect(connected) {
        if (connected) {
            runCatching { WirelessNotification.dismiss(context) }
        }
    }

    SheetFrame(onDismiss = {
        runCatching { WirelessNotification.dismiss(context) }
        onDismiss()
    }) {
        // ---------------------------------------------------------------- header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Wireless debugging",
                color = GC.Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .testTag("wireless_close_button")
                    .clip(RoundedCornerShape(50))
                    .background(GC.SurfaceHi)
                    .clickable(onClick = onDismiss)
                    .padding(11.dp)
            ) {
                AIcon(GhIcons.Close, contentDescription = "Close", tint = GC.Text, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "A shell as uid 2000 — the same user Shizuku gives you. Everything GamersHub " +
                "can do with Shizuku works here, with no other app installed.",
            color = GC.TextDim,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(14.dp))

        // ---------------------------------------------------------------- state
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RomKit.R18)
                .background(RomKit.Panel)
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                when (status) {
                                    WirelessAdb.Status.CONNECTED -> Color(0xFF39D98A)
                                    WirelessAdb.Status.NOT_PAIRED,
                                    WirelessAdb.Status.UNAUTHORISED -> Color(0xFFE0A33A)
                                    WirelessAdb.Status.OFF -> Color(0xFF6B7280)
                                }
                            )
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        if (connected) "Connected" else WirelessAdb.statusLabel(),
                        color = GC.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    if (port > 0) {
                        Text("port $port", color = GC.TextDim, fontSize = 11.sp)
                    }
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = GC.TextDim, fontSize = 12.sp)
                }
                if (busy.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(busy, color = accent, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------------------------------------------------------- daemon
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RomKit.R18)
                .background(RomKit.Panel)
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (daemonAlive) Color(0xFF39D98A) else Color(0xFF6B7280))
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        if (daemonAlive) "Daemon running" else "Daemon not started",
                        color = GC.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text("uid 2000", color = GC.TextDim, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (daemonAlive)
                        "A process of its own, started once. It keeps answering after you " +
                            "turn wireless debugging off — that is the Shizuku behaviour."
                    else
                        "Starts a process as uid 2000 that outlives this connection. " +
                            "Without it, privileges end the moment wireless debugging does.",
                    color = GC.TextDim, fontSize = 11.sp
                )
                if (daemonMsg.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(daemonMsg, color = GC.TextDim, fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                BigButton(
                    text = if (daemonAlive) "STOP DAEMON" else "START DAEMON",
                    onClick = {
                        if (busy.isNotBlank()) return@BigButton
                        scope.launch {
                            busy = "daemon…"
                            val result = withContext(Dispatchers.IO) {
                                if (ShellDaemon.alive || daemonAlive) {
                                    ShellDaemon.stop(context)
                                    false to "Daemon stopped"
                                } else {
                                    ShellDaemon.start(context)
                                }
                            }
                            busy = ""
                            daemonMsg = result.second
                            refresh()
                        }
                    },
                    primary = !daemonAlive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wireless_daemon_button")
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------------------------------------------------------- steps
        if (!connected) {
            Text("1 · Turn on Wireless debugging", color = GC.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Developer options › Wireless debugging. It must be on — the port only " +
                    "exists while it is.",
                color = GC.TextDim, fontSize = 11.sp
            )
            Spacer(Modifier.height(10.dp))

            Text("2 · Tap “Pair device with pairing code”", color = GC.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "That opens a six-digit code. Enter it below while the dialog is still open.",
                color = GC.TextDim, fontSize = 11.sp
            )
            Spacer(Modifier.height(10.dp))

            val fieldColors = TextFieldDefaults.colors(
                focusedContainerColor = RomKit.Panel,
                unfocusedContainerColor = RomKit.Panel,
                focusedTextColor = GC.Text,
                unfocusedTextColor = GC.Text,
                cursorColor = accent,
                focusedIndicatorColor = accent,
                unfocusedIndicatorColor = RomKit.Stroke
            )

            TextField(
                value = code,
                onValueChange = { input ->
                    // Six digits only: adbd rejects anything else, and a stray letter
                    // produces a pairing failure that looks like a wrong code.
                    code = input.filter { it.isDigit() }.take(6)
                },
                placeholder = { Text("six-digit pairing code", color = GC.TextDim, fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wireless_pairing_code_input")
            )
            Spacer(Modifier.height(8.dp))

            Text(
                if (showManualPorts) "Hide manual port options" else "Enter ports manually (if mDNS fails)",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .testTag("wireless_manual_ports_toggle")
                    .clickable { showManualPorts = !showManualPorts }
                    .padding(vertical = 4.dp)
            )

            AnimatedVisibility(visible = showManualPorts) {
                Column {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = manualPairPort,
                            onValueChange = { manualPairPort = it.filter { c -> c.isDigit() }.take(5) },
                            placeholder = { Text("pairing port (auto)", color = GC.TextDim, fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wireless_pair_port_input")
                        )
                        TextField(
                            value = manualConnectPort,
                            onValueChange = { manualConnectPort = it.filter { c -> c.isDigit() }.take(5) },
                            placeholder = { Text("connect port (auto)", color = GC.TextDim, fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wireless_connect_port_input")
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            BigButton(
                text = if (busy.isNotBlank()) "WORKING…" else "PAIR & CONNECT",
                onClick = {
                    if (code.length != 6) {
                        message = "Enter all six digits from the pairing dialog"
                        return@BigButton
                    }
                    if (busy.isNotBlank()) return@BigButton
                    val pPort = manualPairPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: 0
                    val cPort = manualConnectPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: 0
                    scope.launch {
                        busy = if (pPort > 0) "Pairing on port $pPort…" else "Looking for adbd…"
                        val paired = withContext(Dispatchers.IO) {
                            WirelessAdb.pair(context, code, pPort)
                        }
                        if (!paired.first) {
                            busy = ""
                            message = paired.second
                            refresh()
                            return@launch
                        }
                        busy = "Paired. Opening the shell…"
                        val conn = withContext(Dispatchers.IO) {
                            WirelessAdb.connect(context, portOverride = cPort)
                        }
                        busy = ""
                        message = if (conn.first) {
                            "Connected — all Shizuku features are live. ${conn.second}"
                        } else {
                            conn.second
                        }
                        refresh()
                    }
                },
                primary = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wireless_pair_connect_button")
            )
        } else {
            // Already connected: the useful action is the opposite one.
            Text(
                "The shell is live. Every privileged feature in GamersHub is routed " +
                    "through it while this stays connected.",
                color = GC.TextDim, fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton(
                    text = "DISCONNECT",
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { WirelessAdb.disconnect() }
                            message = "Disconnected. The phone still trusts GamersHub — reconnecting needs no code."
                            refresh()
                        }
                    },
                    primary = false,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("wireless_disconnect_button")
                )
                BigButton(
                    text = "FORGET PAIRING",
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { WirelessAdb.forgetIdentity() }
                            message = "Identity cleared — pair again with a new 6-digit code."
                            refresh()
                        }
                    },
                    primary = false,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("wireless_forget_button")
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------------------------------------------------------- fallback
        // Reconnect without a code, for the common case: the port changed because
        // wireless debugging was toggled, but the identity is still authorised.
        if (!connected) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton(
                    text = "ALREADY PAIRED · CONNECT",
                    onClick = {
                        if (busy.isNotBlank()) return@BigButton
                        val cPort = manualConnectPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: 0
                        scope.launch {
                            busy = if (cPort > 0) "Connecting to port $cPort…" else "Finding the current port…"
                            val res = withContext(Dispatchers.IO) {
                                if (cPort == 0) WirelessAdb.discover(context)
                                WirelessAdb.connect(context, portOverride = cPort)
                            }
                            busy = ""
                            message = if (res.first) "Connected — ${res.second}" else res.second
                            refresh()
                        }
                    },
                    primary = false,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("wireless_reconnect_button")
                )
                if (status == WirelessAdb.Status.UNAUTHORISED || status == WirelessAdb.Status.NOT_PAIRED) {
                    BigButton(
                        text = "RESET KEY",
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { WirelessAdb.forgetIdentity() }
                                message = "ADB key reset — pair again with the 6-digit code."
                                refresh()
                            }
                        },
                        primary = false,
                        modifier = Modifier.testTag("wireless_reset_key_button")
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Wireless debugging resets whenever you reboot the phone or toggle it off — " +
                "the port changes. GamersHub keeps its identity, so reconnecting is one tap " +
                "and no code. GamersHub never enables wireless debugging for you, and it " +
                "stays off when you turn it off.",
            color = GC.TextDim.copy(alpha = 0.8f),
            fontSize = 11.sp,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Shizuku on this device also works. This is a root-free shell transport.",
            color = GC.TextDim.copy(alpha = 0.8f),
            fontSize = 11.sp
        )
    }
}
