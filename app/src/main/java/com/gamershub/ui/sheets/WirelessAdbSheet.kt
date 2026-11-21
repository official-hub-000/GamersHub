package com.gamershub.ui.sheets

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.gamershub.core.adb.ShellDaemon
import com.gamershub.core.adb.WirelessAdb
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
 * entry first and never makes the user hunt for a port number. The port is discovered
 * over mDNS while the dialog is open, exactly as `adb` does it.
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
    var busy by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(WirelessAdb.status) }
    var daemonAlive by remember { mutableStateOf(ShellDaemon.alive) }
    var daemonMsg by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(WirelessAdb.shellPort) }
    var manualPort by remember { mutableStateOf("") }

    // A13+ requires the Nearby devices permission (neverForLocation) before NSD will
    // answer at all — without it the auto-detect path can never succeed. The request
    // is asked only when a discovery-dependent action is taken, and the manual-port
    // path stays usable without it.
    val needsNearby = remember { Build.VERSION.SDK_INT >= 33 }
    var nearbyGranted by remember {
        mutableStateOf(
            !needsNearby ||
                context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingDiscovery by remember { mutableStateOf<(() -> Unit)?>(null) }
    val nearbyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        nearbyGranted = granted
        if (granted) {
            pendingDiscovery?.invoke()
        } else {
            message = "Auto-detect needs Nearby devices permission — or type the pairing port and tap again."
        }
        pendingDiscovery = null
    }

    fun ensureDiscovery(block: () -> Unit) {
        if (nearbyGranted) {
            block()
        } else {
            pendingDiscovery = block
            nearbyLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }

    val connected = status == WirelessAdb.Status.CONNECTED

    fun refresh() {
        status = WirelessAdb.status
        port = WirelessAdb.shellPort
        daemonAlive = ShellDaemon.alive
    }

    // Kick a discovery when the sheet opens, off the main thread. Doing it inline made
    // opening this screen jank the UI for six seconds while mDNS waited, which read as
    // "wireless debugging is broken" before a single tap was made.
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { WirelessAdb.discover(context, 4_000) } }
        refresh()
    }

    SheetFrame(onDismiss = onDismiss) {
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
        // This is the difference between "a shell while ADB is on" and Shizuku's actual
        // behaviour. It is offered as soon as any shell exists, including the one the
        // pairing above just created.
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
                    modifier = Modifier.fillMaxWidth()
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

            if (needsNearby && !nearbyGranted) {
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RomKit.R18)
                        .background(RomKit.Panel)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Auto-detect permission",
                        color = GC.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Nearby devices",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.16f))
                            .clickable { nearbyLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            TextField(
                value = code,
                onValueChange = { input ->
                    // Six digits only: adbd rejects anything else, and a stray letter
                    // produces a pairing failure that looks like a wrong code.
                    code = input.filter { it.isDigit() }.take(6)
                },
                placeholder = { Text("six-digit code", color = GC.TextDim, fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = RomKit.Panel,
                    unfocusedContainerColor = RomKit.Panel,
                    focusedTextColor = GC.Text,
                    unfocusedTextColor = GC.Text,
                    cursorColor = accent,
                    focusedIndicatorColor = accent,
                    unfocusedIndicatorColor = RomKit.Stroke
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            TextField(
                value = manualPort,
                onValueChange = { input ->
                    // Optional fallback: mDNS discovery can fail on networks and
                    // chipsets that drop multicast. The port is printed on the same
                    // Settings screen that shows the code, so it costs nothing to use.
                    manualPort = input.filter { it.isDigit() }.take(5)
                },
                placeholder = { Text("port — optional", color = GC.TextDim, fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = RomKit.Panel,
                    unfocusedContainerColor = RomKit.Panel,
                    focusedTextColor = GC.Text,
                    unfocusedTextColor = GC.Text,
                    cursorColor = accent,
                    focusedIndicatorColor = accent,
                    unfocusedIndicatorColor = RomKit.Stroke
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "If auto-detect fails, type the port shown on the Wireless debugging " +
                    "screen (the pairing dialog prints its own).",
                color = GC.TextDim.copy(alpha = 0.85f),
                fontSize = 10.sp
            )
            Spacer(Modifier.height(10.dp))

            BigButton(
                text = if (busy.isNotBlank()) "WORKING…" else "PAIR & CONNECT",
                onClick = {
                    if (code.length != 6) {
                        message = "Enter all six digits from the pairing dialog"
                        return@BigButton
                    }
                    if (busy.isNotBlank()) return@BigButton
                    ensureDiscovery {
                        scope.launch {
                            busy = "Looking for adbd…"
                            val manual = manualPort.toIntOrNull() ?: 0
                            val paired = withContext(Dispatchers.IO) {
                                WirelessAdb.pair(context, code, manual)
                            }
                            if (!paired.first) {
                                busy = ""
                                message = paired.second
                                refresh()
                                return@launch
                            }
                            busy = "Paired. Opening the shell…"
                            val conn = withContext(Dispatchers.IO) {
                                WirelessAdb.connect(context)
                            }
                            busy = ""
                            message = if (conn.first) {
                                "Connected — all Shizuku features are live. ${conn.second}"
                            } else {
                                conn.second
                            }
                            refresh()
                        }
                    }
                },
                primary = true,
                modifier = Modifier.fillMaxWidth()
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
                    modifier = Modifier.weight(1f)
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
                        val manual = manualPort.toIntOrNull() ?: 0
                        fun connectNow() {
                            scope.launch {
                                busy = "Finding the current port…"
                                val res = withContext(Dispatchers.IO) {
                                    // connect() discovers on its own when it has no port,
                                    // and falls back to adbd's fixed port when mDNS is
                                    // silent. Doing the discovery here as well doubled the
                                    // wait for no gain.
                                    if (manual > 0) {
                                        WirelessAdb.connect(context, 10_000, manual)
                                    } else {
                                        WirelessAdb.connect(context)
                                    }
                                }
                                busy = ""
                                message = if (res.first) "Connected — ${res.second}" else res.second
                                refresh()
                            }
                        }
                        if (manual > 0) {
                            connectNow()
                        } else {
                            ensureDiscovery(::connectNow)
                        }
                    },
                    primary = false,
                    modifier = Modifier.weight(1f)
                )
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
            "Rooted already? You do not need this. Root is a strict superset and is used first.",
            color = GC.TextDim.copy(alpha = 0.8f),
            fontSize = 11.sp
        )
    }
}
