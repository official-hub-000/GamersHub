package com.gamershub.ui

import com.gamershub.ui.icons.GhIcons

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.core.root.ModuleManager
import com.gamershub.core.root.ModuleStore
import com.gamershub.core.root.RootShell
import com.gamershub.core.root.RootTools
import com.gamershub.ui.components.BigButton
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import com.gamershub.theme.RomKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Root screen.
 *
 * The point of this screen is "use a gaming module": see what is installed, turn
 * modules on and off, flash a downloaded zip, and get a straight answer when root
 * is not available. Every value shown is read live from the device — nothing is
 * cached into looking healthy.
 */
@Composable
fun RootScreen(@Suppress("UNUSED_PARAMETER") accentIdx: Int = 0) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var showDiag by remember { mutableStateOf(false) }
    // Which rungs of the privilege ladder answered on the last attempt, verbatim.
    var ladderStep by remember { mutableStateOf("") }

    // Live snapshot: root state and module list. Refreshed on a timer so a change
    // made in the Magisk app shows up here without a restart.
    val snap by produceState(initialValue = Snapshot()) {
        while (true) {
            value = withContext(Dispatchers.IO) {
                Snapshot(
                    ready = RootShell.ready(),
                    state = RootShell.statusLabel(),
                    method = RootShell.method,
                    manager = ModuleManager.managerName(),
                    managerVersion = ModuleManager.managerVersion(),
                    suBinary = RootShell.suBinary(),
                    modules = if (RootShell.ready()) ModuleManager.list() else emptyList(),
                    zygisk = ModuleManager.zygiskEnabled(),
                    diag = if (showDiag && RootShell.ready()) RootShell.diagnose(context) else emptyList(),
                    grants = if (RootShell.ready()) {
                        // uid -> (package, label). Cached, because this is called once
                        // per policy row and each miss is a PackageManager walk.
                        val pm = context.packageManager
                        val cache = HashMap<Int, Pair<String, String>?>()
                        com.gamershub.core.root.SuManager.grants { uid ->
                            cache.getOrPut(uid) {
                                runCatching {
                                    val pkgs = pm.getPackagesForUid(uid) ?: return@runCatching null
                                    val pkg = pkgs.first()
                                    val label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                                    pkg to label
                                }.getOrNull()
                            }
                        }
                    } else emptyList(),
                    suBackend = com.gamershub.core.root.SuManager.backendName(),
                    canManageSu = com.gamershub.core.root.SuManager.canManage(),
                    temp = runCatching { com.gamershub.core.root.TempRoot.inspect(context) }.getOrNull(),
                    rung = runCatching { com.gamershub.core.root.PrivilegeLadder.standing() }
                        .getOrDefault(com.gamershub.core.root.PrivilegeLadder.Rung.NONE),
                    df = runCatching { com.gamershub.core.root.DfRoot.status(context) }.getOrNull(),
                )
            }
            delay(2500)
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = "flashing"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Copy into our cache: the manager reads as root from a different
                    // namespace and cannot rely on the picker's content URI.
                    val name = context.contentResolver
                        .query(uri, null, null, null, null)?.use { c ->
                            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (c.moveToFirst() && i >= 0) c.getString(i) else null
                        } ?: "module.zip"
                    val out = java.io.File(context.cacheDir, "flash_${System.currentTimeMillis()}_$name")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    } ?: return@runCatching RootTools.Result(false, "could not read the file")
                    val r = ModuleManager.install(out.absolutePath)
                    out.delete()
                    r
                }.getOrElse { RootTools.Result(false, it.message ?: "flash failed") }
            }
            busy = ""
            message = if (result.ok) "✓ ${result.detail}"
            else "✗ ${result.detail}"
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(GC.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(54.dp))

        Text(
            "ROOT",
            style = RomKit.Title,
            color = GC.Text
        )
        Text(
            "modules · system depth · diagnostics",
            color = GC.TextDim,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))

        // ---------------------------------------------------------- root state
        if (!snap.ready) {
            Card(borderColor = Color(0x55E2A03A)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(GhIcons.Security, null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Root not granted", color = GC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            snap.state,
                            color = GC.TextDim,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Flashing and module control need root. Overlays, macros and the rest of " +
                        "GamersHub keep working through Shizuku without it.",
                    color = GC.TextDim,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("REQUEST ROOT", filled = true) {
                        scope.launch {
                            busy = "requesting"
                            // Climb the whole ladder, and keep the step-by-step
                            // result: "failed" with no reason is what makes a root
                            // panel useless.
                            val ladder = withContext(Dispatchers.IO) {
                                com.gamershub.core.root.PrivilegeLadder.climb(context, wantRoot = true)
                            }
                            ladderStep = ladder.steps.joinToString(" · ") { s ->
                                "${if (s.ok) "✓" else "✗"} ${s.name}"
                            }
                            val ok = ladder.reachedRoot
                            busy = ""
                            message = if (ok) "Root granted (${RootShell.method})" else RootShell.lastError
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }
                    Pill("DIAGNOSE") { showDiag = !showDiag }
                }
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(GhIcons.CheckCircle, null, tint = Color(0xFF4ADE80), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Root active", color = GC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            buildString {
                                append(snap.manager)
                                if (snap.managerVersion.isNotBlank()) append(" · ${snap.managerVersion}")
                                append(" · ${snap.method}")
                            },
                            color = GC.TextDim,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("REFRESH") {
                        scope.launch { withContext(Dispatchers.IO) { ModuleManager.list() } }
                    }
                    Pill("DIAGNOSE") { showDiag = !showDiag }
                    if (snap.modules.isNotEmpty()) {
                        Pill("REBOOT") {
                            scope.launch {
                                withContext(Dispatchers.IO) { ModuleManager.reboot() }
                                Toast.makeText(context, "Rebooting…", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---------------------------------------------------------- diagnostics
        AnimatedVisibility(visible = showDiag) {
            Card {
                Text("DIAGNOSTICS", color = accent, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (snap.diag.isEmpty()) {
                    Text("enable root to see live values", color = GC.TextDim, fontSize = 12.sp)
                } else {
                    snap.diag.forEach { line ->
                        Text(
                            line,
                            color = GC.TextDim,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---------------------------------------------------------- zygisk hint
        if (snap.ready && snap.zygisk == false) {
            Card(borderColor = Color(0x55E2A03A)) {
                Text("Zygisk is off", color = GC.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Most gaming modules need Zygisk to inject into game processes. " +
                        "Enable it in ${snap.manager} → Settings, then reboot.",
                    color = GC.TextDim,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---------------------------------------------------------- flash
        // ---------------------------------------------------------- store
        // ---------------------------------------------------------- two layers
        SectionHead("PRIVILEGE")
        LadderCard(
            rung = snap.rung,
            rootReady = snap.ready,
            manager = snap.manager,
            managerVersion = snap.managerVersion,
            suBackend = snap.suBackend,
            canManageSu = snap.canManageSu,
            accent = accent,
            ladderStep = ladderStep,
            busy = busy,
            onClimb = {
                scope.launch {
                    busy = "climbing"
                    val res = withContext(Dispatchers.IO) {
                        com.gamershub.core.root.PrivilegeLadder.climb(context, wantRoot = true)
                    }
                    ladderStep = res.steps.joinToString(" · ") { s2 -> "${if (s2.ok) "✓" else "✗"} ${s2.name}" }
                    message = if (res.reachedRoot) "Root held · ${RootShell.method}" else "no rung answered"
                    busy = ""
                }
            },
            onDescend = {
                scope.launch {
                    busy = "dropping"
                    message = withContext(Dispatchers.IO) {
                        com.gamershub.core.root.PrivilegeLadder.descend(context)
                    }
                    ladderStep = ""
                    busy = ""
                }
            }
        )
        Spacer(Modifier.height(16.dp))

        // ---------------------------------------------------------- DFRoot
        SectionHead("TEMPORARY ROOT · DFROOT")
        DfRootSection(
            df = snap.df,
            accent = accent,
            rootReady = snap.ready,
            onLaunch = {
                val ok = com.gamershub.core.root.DfRoot.launch(context)
                message = if (ok) "Opened DFRoot — run it, then come back" else "Could not open DFRoot"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
            onAdopt = {
                scope.launch {
                    busy = "adopting"
                    // DFRoot has just started the manager's su daemon; walking the
                    // ladder now is what turns "it ran" into "this app holds root".
                    val ladder = withContext(Dispatchers.IO) {
                        com.gamershub.core.root.PrivilegeLadder.climb(context, wantRoot = true)
                    }
                    ladderStep = ladder.steps.joinToString(" · ") { s2 ->
                        "${if (s2.ok) "✓" else "✗"} ${s2.name}"
                    }
                    busy = ""
                    message = if (ladder.reachedRoot) "Root adopted via DFRoot"
                              else "DFRoot has not granted su yet — finish the run first"
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        )
        Spacer(Modifier.height(16.dp))

        // ---------------------------------------------------------- session root
        SectionHead("SESSION ROOT")
        TempRootSection(
            facts = snap.temp,
            accent = accent,
            busy = busy,
            onRelease = {
                scope.launch {
                    busy = "releasing"
                    message = withContext(Dispatchers.IO) { com.gamershub.core.root.TempRoot.release(context) }
                    busy = ""
                }
            }
        )
        Spacer(Modifier.height(16.dp))

        // ---------------------------------------------------------- superuser
        SectionHead("SUPERUSER")
        SuSection(
            grants = snap.grants,
            backend = snap.suBackend,
            canManage = snap.canManageSu,
            rootReady = snap.ready,
            accent = accent,
            busy = busy,
            onAct = { label: String, block: suspend () -> String ->
                scope.launch {
                    busy = label
                    message = withContext(Dispatchers.IO) {
                        runCatching { block() }.getOrElse { it.message ?: "failed" }
                    }
                    busy = ""
                }
            }
        )
        Spacer(Modifier.height(16.dp))

        SectionHead("MODULE STORE")
        StoreSection(
            accent = accent,
            rootReady = snap.ready,
            context = context,
            onBusy = { busy = it },
            onMessage = { message = it },
        )
        Spacer(Modifier.height(16.dp))

        SectionHead("FLASH FROM STORAGE")
        Card {
            Text(
                "Pick a module .zip you already downloaded. It is flashed with " +
                    "${snap.manager}'s own installer, so the result is identical to " +
                    "flashing it from the manager.",
                color = GC.TextDim,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    if (snap.ready) "CHOOSE .ZIP" else "ROOT REQUIRED",
                    filled = true,
                    enabled = snap.ready && busy.isEmpty()
                ) {
                    picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }
                if (busy == "flashing") {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = accent
                    )
                    Text("flashing…", color = GC.TextDim, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ---------------------------------------------------------- modules
        SectionHead("INSTALLED MODULES (${snap.modules.size})")
        if (!snap.ready) {
            Text("Modules appear once root is granted.", color = GC.TextDim, fontSize = 12.sp)
        } else if (snap.modules.isEmpty()) {
            Card {
                Text("No modules installed.", color = GC.Text, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "If you have modules in ${snap.manager} and see none here, the module " +
                        "directory is not readable — run DIAGNOSE.",
                    color = GC.TextDim,
                    fontSize = 12.sp
                )
            }
        } else {
            snap.modules.forEach { m ->
                ModuleCard(
                    module = m,
                    accent = accent,
                    busy = busy.isNotEmpty(),
                    onToggle = { want ->
                        scope.launch {
                            busy = m.id
                            val r = withContext(Dispatchers.IO) {
                                if (want) ModuleManager.enable(m.id) else ModuleManager.disable(m.id)
                            }
                            busy = ""
                            message = r.detail
                            Toast.makeText(context, r.detail, Toast.LENGTH_LONG).show()
                        }
                    },
                    onRemove = {
                        scope.launch {
                            busy = m.id
                            val r = withContext(Dispatchers.IO) { ModuleManager.remove(m.id) }
                            busy = ""
                            message = r.detail
                            Toast.makeText(context, r.detail, Toast.LENGTH_LONG).show()
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // ---------------------------------------------------------- deep control
        SectionHead("SYSTEM DEPTH")
        Card {
            Text(
                "Privileged actions that go below the app sandbox. Every one reports " +
                    "the real result rather than assuming success.",
                color = GC.TextDim,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            val depth = listOf(
                Triple(GhIcons.Tune, "Drop caches + fstrim", "free RAM and trim storage"),
                Triple(GhIcons.Refresh, "Reset display", "size and density back to native"),
                Triple(GhIcons.Block, "Unfreeze all", "re-enable packages frozen earlier"),
            )
            depth.forEach { (icon, title, sub) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RomKit.R10)
                        .clickable(enabled = snap.ready) {
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    when (title) {
                                        "Drop caches + fstrim" -> {
                                            RootTools.trimCaches()
                                            RootTools.fstrim()
                                        }
                                        "Reset display" -> RootTools.resetDisplay()
                                        else -> RootTools.Result(true, "nothing frozen by GamersHub")
                                    }
                                }
                                Toast.makeText(context, r.detail.take(120), Toast.LENGTH_LONG).show()
                            }
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                ) {
                    Icon(icon, null, tint = if (snap.ready) accent else GC.TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, color = if (snap.ready) GC.Text else GC.TextDim, fontSize = 14.sp)
                        Text(sub, color = GC.TextDim, fontSize = 11.sp)
                    }
                }
            }
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(message, color = GC.TextDim, fontSize = 12.sp)
        }

        Spacer(Modifier.height(110.dp))
    }
}

private data class Snapshot(
    val ready: Boolean = false,
    val state: String = "Not checked",
    val method: String = "",
    val manager: String = "—",
    val managerVersion: String = "",
    val suBinary: String? = null,
    val modules: List<ModuleManager.Module> = emptyList(),
    val zygisk: Boolean? = null,
    val diag: List<String> = emptyList(),
    val grants: List<com.gamershub.core.root.SuManager.Grant> = emptyList(),
    val suBackend: String = "",
    val canManageSu: Boolean = false,
    val temp: com.gamershub.core.root.TempRoot.Facts? = null,
    val rung: com.gamershub.core.root.PrivilegeLadder.Rung =
        com.gamershub.core.root.PrivilegeLadder.Rung.NONE,
    val df: com.gamershub.core.root.DfRoot.Status? = null,
)

@Composable
private fun ModuleCard(
    module: ModuleManager.Module,
    accent: Color,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember(module.id) { mutableStateOf(false) }
    Card(borderColor = if (module.enabled) Color(0x334ADE80) else Color(0x228A94A8)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                GhIcons.Extension,
                null,
                tint = when {
                    module.pendingRemove -> Color(0xFFE23A3A)
                    module.enabled -> accent
                    else -> GC.TextDim
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    module.name,
                    color = if (module.enabled) GC.Text else GC.TextDim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${module.id} · v${module.version} · ${module.state}",
                    color = GC.TextDim,
                    fontSize = 11.sp
                )
            }
            Icon(
                if (expanded) GhIcons.KeyboardArrowUp else GhIcons.KeyboardArrowDown,
                "details",
                tint = GC.TextDim,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { expanded = !expanded }
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                if (module.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(module.description, color = GC.TextDim, fontSize = 11.sp)
                }
                if (module.author.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("by ${module.author}", color = GC.TextDim, fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(
                        if (module.enabled) "DISABLE" else "ENABLE",
                        filled = module.enabled,
                        enabled = !busy && !module.pendingRemove
                    ) { onToggle(!module.enabled) }
                    if (!module.pendingRemove) {
                        Pill("REMOVE", enabled = !busy) { onRemove() }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Changes take effect after a reboot — that is how the manager works, " +
                        "not a GamersHub limitation.",
                    color = GC.TextDim,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun Card(
    borderColor: Color = RomKit.Stroke,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RomKit.R18)
            .background(RomKit.Panel)
            .border(1.dp, borderColor, RomKit.R18)
            .padding(14.dp)
    ) { content() }
}

/**
 * Superuser: every app that has a root decision, and the switch to change it.
 *
 * Reads the policy store of whichever root solution is installed (Magisk's sqlite
 * table, or KernelSU/APatch through their CLI) rather than keeping its own list.
 * If it cannot read, it says so — an empty list that means "no access" would look
 * identical to "no apps have root", and those are very different answers.
 */
/**
 * Temporary root: what this device actually has, and what it costs.
 *
 * The card is deliberately plain about the two facts a user needs — whether root
 * survives a reboot, and whether SELinux is enforcing. Everything else is detail.
 * The exploit path (DFRoot / DirtyFrag) is documented in TempRoot.kt rather than
 * shipped: it needs a per-kernel `.ko` and a native build this app does not have, and
 * a wrong offset costs a phone rather than a build.
 */
/**
 * The two layers, side by side, because they are different things and the whole
 * confusion about "temporary root" is people treating them as one.
 *
 *   LEFT  — temporary root: uid 0 for this session, given back when the engine stops.
 *   RIGHT — the manager: policy for every other app, and module zips.
 *
 * Whichever is actually live is lit; the other is dimmed but still readable, so the
 * screen says what *could* be done as well as what is.
 */
@Composable
private fun LadderCard(
    rung: com.gamershub.core.root.PrivilegeLadder.Rung,
    rootReady: Boolean,
    manager: String,
    managerVersion: String,
    suBackend: String,
    canManageSu: Boolean,
    accent: Color,
    ladderStep: String,
    busy: String,
    onClimb: () -> Unit,
    onDescend: () -> Unit
) {
    val tempLive = rung == com.gamershub.core.root.PrivilegeLadder.Rung.TEMP_ROOT
    val persistent = rung == com.gamershub.core.root.PrivilegeLadder.Rung.PERSISTENT_ROOT
    val shellOnly = rung == com.gamershub.core.root.PrivilegeLadder.Rung.SHIZUKU
    val viaDf = rung == com.gamershub.core.root.PrivilegeLadder.Rung.DFROOT_ROOT

    Card(borderColor = if (rootReady) accent.copy(alpha = 0.35f) else RomKit.Stroke) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        persistent -> "Root held"
                        tempLive -> "Temporary root held"
                        viaDf -> "Root held · DFRoot"
                        shellOnly -> "Shizuku only"
                        else -> "No root"
                    },
                    color = GC.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    rung.detail,
                    color = GC.TextDim, fontSize = 12.sp
                )
            }
            Box(
                Modifier
                    .size(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        when {
                            persistent -> accent
                            tempLive -> Color(0xFFE0A33A)
                            shellOnly -> Color(0xFF59A0E0)
                            else -> GC.TextDim.copy(alpha = 0.5f)
                        }
                    )
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            LayerTile(
                title = "TEMPORARY",
                body = "uid 0 for this session",
                detail = if (tempLive) "live — drops with the engine"
                         else "gained on start, released on stop",
                live = tempLive,
                accent = Color(0xFFE0A33A),
                modifier = Modifier.weight(1f)
            )
            LayerTile(
                title = "MANAGER",
                body = if (manager.isBlank() || manager == "—") "no manager detected" else manager,
                detail = when {
                    !canManageSu -> "policy store not readable"
                    else -> "superuser + module zips"
                },
                live = persistent,
                accent = accent,
                modifier = Modifier.weight(1f)
            )
        }

        if (managerVersion.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text("$manager $managerVersion · $suBackend", color = GC.TextDim, fontSize = 11.sp, maxLines = 1)
        }
        if (ladderStep.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text("last attempt", color = GC.TextDim, fontSize = 10.sp, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(3.dp))
            Text(ladderStep, color = GC.TextSoft, fontSize = 12.sp)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (!rootReady) {
                BigButton(
                    text = if (busy.isNotBlank()) busy.uppercase() else "CLIMB TO ROOT",
                    onClick = onClimb,
                    primary = true,
                    modifier = Modifier.weight(1f)
                )
            } else {
                BigButton(
                    text = "DROP ROOT",
                    onClick = onDescend,
                    primary = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LayerTile(
    title: String,
    body: String,
    detail: String,
    live: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (live) accent.copy(alpha = 0.10f) else GC.SurfaceHi.copy(alpha = 0.6f))
            .border(1.dp, if (live) accent.copy(alpha = 0.55f) else RomKit.Stroke, RoundedCornerShape(12.dp))
            .padding(11.dp)
    ) {
        Text(title, color = if (live) accent else GC.TextDim, fontSize = 9.sp, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(4.dp))
        Text(body, color = GC.Text, fontSize = 13.sp, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(detail, color = GC.TextDim, fontSize = 10.sp, maxLines = 2)
    }
}

/**
 * DFRoot: temporary root by kernel exploit, integrated rather than reimplemented.
 *
 * This card is deliberately specific. It names the KMI it wants, says whether the
 * device has it, and — when it does not — says so before the user spends a reboot
 * finding out. That is the whole value of integrating instead of vendoring: the
 * exploit needs DFRoot's own signed native payload and a per-kernel module, so the
 * app runs *its* UI and then adopts the root that comes back.
 *
 * After DFRoot bootstraps KernelSU, the ladder's `su` rung picks the grant up and the
 * MANAGER layer above comes alive — superuser policy, module zips, enable/disable.
 */
@Composable
private fun DfRootSection(
    df: com.gamershub.core.root.DfRoot.Status?,
    accent: Color,
    rootReady: Boolean,
    onLaunch: () -> Unit,
    onAdopt: () -> Unit
) {
    val context = LocalContext.current
    val kmiOk = df?.kmiSupported == true
    val installed = df?.installed == true
    val border = when {
        rootReady -> accent.copy(alpha = 0.35f)
        kmiOk -> Color(0x6659A0E0)
        else -> RomKit.Stroke
    }
    Card(borderColor = border) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        rootReady -> "Root already held"
                        !installed -> "DFRoot not installed"
                        !kmiOk -> "Kernel not supported"
                        else -> "Ready to run"
                    },
                    color = GC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (df == null) "checking…"
                    else "kernel ${df.deviceKmi.major}.${df.deviceKmi.minor} · android ${df.deviceKmi.android}",
                    color = GC.TextDim, fontSize = 12.sp
                )
            }
            if (df != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (kmiOk) Color(0x2239D98A) else Color(0x22E0A33A)
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (kmiOk) "SUPPORTED" else "NO MODULE",
                        color = if (kmiOk) Color(0xFF39D98A) else Color(0xFFE0A33A),
                        fontSize = 9.sp, letterSpacing = 0.8.sp
                    )
                }
            }
        }

        df?.let { st ->
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniFact("VERSION", st.version.ifBlank { "—" })
                MiniFact("HOOKED", if (st.hooked) "/dev/df" else "no")
                MiniFact("BOOTSTRAP", if (st.canBootstrap) "ready" else "missing")
            }
            st.notes.forEach { line ->
                Spacer(Modifier.height(6.dp))
                Text("· $line", color = GC.TextDim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(11.dp))
            BigButton(
                text = if (installed) "OPEN DFROOT" else "GET DFROOT",
                onClick = {
                    if (installed) {
                        onLaunch()
                    } else {
                        // No dead button: send them to the real project page.
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(
                                        "https://github.com/diabl0w/DFRoot/releases/latest"
                                    )
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                },
                primary = kmiOk && !rootReady
            )
            // After the run, this is the button that actually matters: DFRoot has
            // started su; the ladder is what makes the grant belong to this app.
            if (installed && st.hooked && !rootReady) {
                Spacer(Modifier.height(8.dp))
                BigButton(
                    text = "ADOPT ROOT",
                    onClick = onAdopt,
                    primary = true
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "DFRoot gives uid 0 for one boot by patching a kernel page-cache bug, then starts " +
                "KernelSU's su daemon. GamersHub opens it and adopts the result — the superuser " +
                "layer above is what it bootstraps. The exploit itself is DFRoot's signed native " +
                "payload plus one kernel module per KMI, so it runs from there, not from here.",
            color = GC.TextDim.copy(alpha = 0.85f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun TempRootSection(
    facts: com.gamershub.core.root.TempRoot.Facts?,
    accent: Color,
    busy: String,
    onRelease: () -> Unit
) {
    val kind = facts?.kind ?: com.gamershub.core.root.TempRoot.Kind.NONE
    Card(
        borderColor = when (kind) {
            com.gamershub.core.root.TempRoot.Kind.PERSISTENT -> accent.copy(alpha = 0.35f)
            com.gamershub.core.root.TempRoot.Kind.TEMPORARY -> Color(0x66E0A33A)
            com.gamershub.core.root.TempRoot.Kind.SESSION -> Color(0x6659A0E0)
            com.gamershub.core.root.TempRoot.Kind.NONE -> RomKit.Stroke
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (kind) {
                        com.gamershub.core.root.TempRoot.Kind.PERSISTENT -> "Persistent root"
                        com.gamershub.core.root.TempRoot.Kind.TEMPORARY -> "Temporary root"
                        com.gamershub.core.root.TempRoot.Kind.SESSION -> "Session root"
                        com.gamershub.core.root.TempRoot.Kind.NONE -> "No root session"
                    },
                    color = GC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    when (kind) {
                        com.gamershub.core.root.TempRoot.Kind.PERSISTENT ->
                            "survives a reboot · ${facts?.manager ?: "manager"}"
                        com.gamershub.core.root.TempRoot.Kind.TEMPORARY ->
                            "lives until reboot · no manager owns su"
                        com.gamershub.core.root.TempRoot.Kind.SESSION ->
                            "held by this app only · released with the engine"
                        com.gamershub.core.root.TempRoot.Kind.NONE ->
                            "the engine runs on Shizuku or app-level features"
                    },
                    color = GC.TextDim, fontSize = 12.sp
                )
            }
            if (busy.isNotBlank()) Text(busy.uppercase(), color = accent, fontSize = 11.sp)
        }

        if (facts != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniFact("KERNEL", facts.kernel.take(22))
                MiniFact("SELINUX", facts.selinux)
                MiniFact("UPTIME", facts.uptime)
            }
            if (facts.suPath != null) {
                Spacer(Modifier.height(8.dp))
                Text("su · ${facts.suPath}", color = GC.TextDim, fontSize = 11.sp, maxLines = 1)
            }
            facts.notes.forEach { line ->
                Spacer(Modifier.height(6.dp))
                Text("· $line", color = GC.TextDim, fontSize = 12.sp)
            }
        }

        if (facts?.rootReady == true) {
            Spacer(Modifier.height(10.dp))
            BigButton(
                text = "Release root now",
                onClick = onRelease,
                primary = false
            )
        }

        Spacer(Modifier.height(10.dp))
        // Say the limitation out loud instead of shipping a dead button.
        Text(
            "This is the root GamersHub holds itself: a live provider granted for the " +
                "engine session and dropped when it stops. Use DFRoot above when the device " +
                "has no per-boot root yet — whatever it bootstraps is adopted here.",
            color = GC.TextDim.copy(alpha = 0.85f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun MiniFact(label: String, value: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GC.SurfaceHi)
            .padding(horizontal = 9.dp, vertical = 6.dp)
    ) {
        Text(label, color = GC.TextDim, fontSize = 8.sp, letterSpacing = 0.8.sp)
        Text(value, color = GC.Text, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun SuSection(
    grants: List<com.gamershub.core.root.SuManager.Grant>,
    backend: String,
    canManage: Boolean,
    rootReady: Boolean,
    accent: Color,
    busy: String,
    onAct: (String, suspend () -> String) -> Unit
) {
    Card(borderColor = if (rootReady) accent.copy(alpha = 0.35f) else RomKit.Stroke) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (rootReady) "Root manager" else "No root",
                    color = GC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    when {
                        !rootReady -> "grant root below and this fills with the real policy list"
                        !canManage -> "$backend · cannot read the policy store"
                        else -> "$backend · ${grants.size} app(s) with a decision"
                    },
                    color = GC.TextDim, fontSize = 12.sp
                )
            }
            if (busy.isNotBlank()) Text(busy.uppercase(), color = accent, fontSize = 11.sp)
        }

        if (grants.isEmpty() && rootReady) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (canManage) "No app has asked for root yet. The first request shows up here."
                else "This root solution keeps its policy somewhere GamersHub cannot read.",
                color = GC.TextDim, fontSize = 12.sp
            )
        }

        grants.take(24).forEach { g ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        g.label.ifBlank { g.pkg.ifBlank { "uid ${g.uid}" } },
                        color = GC.Text, fontSize = 14.sp, maxLines = 1
                    )
                    Text(
                        listOfNotNull(
                            "uid ${g.uid}",
                            g.pkg.takeIf { it.isNotBlank() },
                            when (g.allowed) {
                                true -> "allowed"
                                false -> "denied"
                                null -> "ask"
                            }
                        ).joinToString(" · "),
                        color = GC.TextDim, fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                if (canManage) {
                    // A real switch, not a button pair: the state is the label.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (g.allowed == true) accent.copy(alpha = 0.20f) else GC.SurfaceHi)
                            .border(
                                1.dp,
                                if (g.allowed == true) accent else RomKit.Stroke,
                                RoundedCornerShape(50)
                            )
                            .clickable {
                                onAct(if (g.allowed == true) "revoking" else "granting") {
                                    com.gamershub.core.root.SuManager.setGrant(
                                        g.uid, allow = g.allowed != true
                                    )
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (g.allowed == true) "ALLOWED" else "ASKING",
                            color = if (g.allowed == true) accent else RomKit.TextLo,
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, RomKit.Stroke, RoundedCornerShape(50))
                            .clickable {
                                onAct("forgetting") {
                                    com.gamershub.core.root.SuManager.forget(g.uid)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("FORGET", color = GC.TextDim, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHead(text: String) {
    Text(
        text,
        color = GC.TextDim,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun Pill(
    label: String,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = if (filled) RomKit.Panel2 else Color.Transparent
    val border = if (filled) RomKit.Stroke else RomKit.Stroke.copy(alpha = 0.6f)
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (enabled) RomKit.TextHi else RomKit.TextLo.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}

// ---------------------------------------------------------------------------
// Module store
// ---------------------------------------------------------------------------

/**
 * Store section: browse, filter, download and flash.
 *
 * The important behaviour is not the list — it is that installing from here runs
 * the exact same validated path as a local zip. A download that is corrupt,
 * truncated, or not actually a module is rejected before the installer sees it.
 */
@Composable
private fun StoreSection(
    accent: Color,
    rootReady: Boolean,
    context: android.content.Context,
    onBusy: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<ModuleStore.Entry>>(emptyList()) }
    var problems by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var queried by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var gamingOnly by remember { mutableStateOf(true) }
    var showAll by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<ModuleStore.Progress?>(null) }

    fun load() {
        if (loading) return
        loading = true
        scope.launch {
            val (list, fails) = ModuleStore.loadAll()
            entries = list
            problems = fails
            loading = false
            queried = true
        }
    }

    // Load once when the section first appears, so the store is not an empty box.
    androidx.compose.runtime.LaunchedEffect(Unit) { load() }

    val filtered = remember(entries, query, gamingOnly) {
        entries.filter { e ->
            val q = query.trim().lowercase()
            val matchQ = q.isEmpty() ||
                e.name.lowercase().contains(q) ||
                e.id.lowercase().contains(q) ||
                e.description.lowercase().contains(q)
            val matchCat = !gamingOnly || e.gaming
            matchQ && matchCat
        }
    }

    fun install(entry: ModuleStore.Entry) {
        if (!rootReady) {
            onMessage("Root is required to flash a module")
            return
        }
        installing = entry.id
        progress = null
        scope.launch {
            onBusy("store")
            val result = runCatching {
                val file = ModuleStore.download(entry, context.cacheDir) { p -> progress = p }
                    .getOrThrow()
                try {
                    ModuleManager.install(file.absolutePath)
                } finally {
                    file.delete()
                }
            }.getOrElse {
                com.gamershub.core.root.RootTools.Result(false, it.message ?: "download failed")
            }
            installing = ""
            progress = null
            onBusy("")
            val text = if (result.ok) "✓ ${entry.name}: ${result.detail}"
            else "✗ ${entry.name}: ${result.detail}"
            onMessage(text)
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(GhIcons.CloudDownload, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Download modules directly", color = GC.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        loading -> "loading catalogs…"
                        entries.isNotEmpty() -> "${entries.size} modules from ${problems.size.let { if (it == 0) "all sources" else "${3 - it.coerceAtMost(3)} sources" }}"
                        queried -> "no modules loaded"
                        else -> "not loaded yet"
                    },
                    color = GC.TextDim,
                    fontSize = 11.sp
                )
            }
            Pill(if (loading) "…" else "RELOAD", enabled = !loading) { load() }
        }

        if (!rootReady) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Grant root to install. You can still browse the catalog now.",
                color = GC.TextDim,
                fontSize = 11.sp
            )
        }

        AnimatedVisibility(visible = problems.isNotEmpty() && queried) {
            Column {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Some sources did not answer: " + problems.joinToString("; ").take(180),
                    color = Color(0xFFE2A03A),
                    fontSize = 10.sp
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))

    // Search + filter
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(RomKit.Panel)
                .border(1.dp, RomKit.Stroke, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = GC.Text, fontSize = 13.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("search modules…", color = GC.TextDim, fontSize = 13.sp)
                    } else inner()
                }
            )
        }
        Pill(if (gamingOnly) "GAMING" else "ALL", filled = gamingOnly) { gamingOnly = !gamingOnly }
    }

    if (loading) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
            Spacer(Modifier.width(10.dp))
            Text("fetching catalogs…", color = GC.TextDim, fontSize = 12.sp)
        }
    }

    Spacer(Modifier.height(10.dp))

    val visible = if (showAll) filtered else filtered.take(8)
    visible.forEach { entry ->
        StoreRow(
            entry = entry,
            accent = accent,
            rootReady = rootReady,
            installing = installing == entry.id,
            progress = if (installing == entry.id) progress else null,
            onInstall = { install(entry) }
        )
        Spacer(Modifier.height(8.dp))
    }

    if (filtered.size > 8) {
        Spacer(Modifier.height(2.dp))
        Pill(if (showAll) "SHOW FEWER" else "SHOW ALL ${filtered.size}") { showAll = !showAll }
    }

    if (queried && !loading && filtered.isEmpty()) {
        Text(
            if (gamingOnly) "Nothing matched. Tap ALL to search every category."
            else "Nothing matched that search.",
            color = GC.TextDim,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun StoreRow(
    entry: ModuleStore.Entry,
    accent: Color,
    rootReady: Boolean,
    installing: Boolean,
    progress: ModuleStore.Progress?,
    onInstall: () -> Unit,
) {
    var expanded by remember(entry.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RomKit.R12)
            .background(RomKit.Panel)
            .border(1.dp, RomKit.Stroke, RomKit.R12)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, color = GC.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(entry.version)
                        if (entry.author.isNotBlank()) append(" · ${entry.author}")
                        if (entry.sizeLabel.isNotEmpty()) append(" · ${entry.sizeLabel}")
                        if (entry.source.isNotBlank()) append(" · ${entry.source}")
                    },
                    color = GC.TextDim,
                    fontSize = 10.sp
                )
            }
            if (installing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
            } else {
                Pill("GET", filled = true, enabled = rootReady) { onInstall() }
            }
        }

        if (entry.description.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                entry.description.take(if (expanded) 400 else 90) +
                    if (!expanded && entry.description.length > 90) "…" else "",
                color = GC.TextDim,
                fontSize = 11.sp
            )
            if (entry.description.length > 90) {
                Text(
                    if (expanded) "less" else "more",
                    color = accent,
                    fontSize = 10.sp,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }
        }

        if (installing && progress != null) {
            Spacer(Modifier.height(8.dp))
            val p = progress
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RomKit.Panel2)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(p.fraction.coerceIn(0.02f, 1f))
                        .height(3.dp)
                        .background(accent)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                if (p.total > 0) "${(p.read / 1024)} / ${(p.total / 1024)} KB"
                else "${p.read / 1024} KB",
                color = GC.TextDim,
                fontSize = 9.sp
            )
        }
    }
}
