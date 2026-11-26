/* ------------------------------------------------------------------ */
/* Activation Center — Shizuku only                                  */
/* ------------------------------------------------------------------ */

@Composable
fun ActivationSheet(
    envLevel: Int,
    shizukuActive: Boolean,
    onOpenShizuku: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accent = LocalAccent.current
    var logOpen by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    // Keep the live shell log fresh while it is open.
    LaunchedEffect(logOpen) {
        while (logOpen) {
            kotlinx.coroutines.delay(1200)
            tick++
        }
    }
    SheetFrame(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Power Up Spot", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 24.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(GC.SurfaceHi).clickable(onClick = onDismiss).padding(11.dp)
            ) {
                AIcon(GhIcons.Close, contentDescription = "Shut", tint = GC.Text, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            com.gamershub.ui.components.GaugeRing(pct = envLevel * 100 / 3, big = "$envLevel", small = "/ 3", diameter = 128)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(GC.SurfaceHi).padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text("DEEP TIER", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 13.sp, letterSpacing = 1.2.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RomKit.R12)
                .background(RomKit.Panel2)
                .border(1.dp, RomKit.Stroke, RomKit.R12)
                .padding(15.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(GhIcons.Android, size = 44)
                Spacer(Modifier.width(11.dp))
                Text("Shizuku Door", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 19.sp, modifier = Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (shizukuActive) accent.copy(alpha = 0.2f) else GC.Bg)
                        .clickable(onClick = onOpenShizuku)
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                ) {
                    if (shizukuActive) {
                        AIcon(GhIcons.Check, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        if (shizukuActive) "Activated" else "Activate",
                        style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 13.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Shizuku runs a shell as uid 2000 and GamersHub borrows it. No root, no second transport. Install the Shizuku app, start its server, then tap Activate here and grant GamersHub access.",
                color = GC.TextDim, fontSize = 12.sp, lineHeight = 18.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { logOpen = !logOpen }
                .padding(vertical = 6.dp)
        ) {
            AIcon(GhIcons.FastForward, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text("Shizuku Log", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(if (logOpen) "⌃" else "⌄", style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 14.sp)
        }
        if (logOpen) {
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(GC.Bg)
                    .padding(12.dp)
            ) {
                val lines = remember(tick) {
                    val history = runCatching { com.gamershub.core.shizuku.Shell.recent(8) }.getOrDefault(emptyList())
                    if (history.isEmpty()) {
                        listOf(
                            "binder: " + com.gamershub.core.shizuku.Shell.statusLabel(),
                            "error: " + com.gamershub.core.shizuku.Shell.reason()
                        )
                    } else {
                        history.reversed()
                    }
                }
                lines.forEach {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 11.5.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}