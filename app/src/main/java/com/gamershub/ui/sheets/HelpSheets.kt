package com.gamershub.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.BuildConfig
import com.gamershub.core.HelpTopic
import com.gamershub.core.IssueReporter
import com.gamershub.core.Manual
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import com.gamershub.theme.RomKit
import com.gamershub.ui.components.AIcon
import com.gamershub.ui.components.BigButton
import com.gamershub.ui.components.CircleIconButton
import com.gamershub.ui.components.IconChip
import com.gamershub.ui.icons.GhIcons
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.rememberCoroutineScope
import com.gamershub.core.StartupWatch
import com.gamershub.core.SupportDiagnostics
import com.gamershub.core.SupportMail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The manual and the search bar are the same screen.
 *
 * Empty query → browse by section. Typed query → ranked results. Tapped result →
 * the topic page. Keeping them one composable means a user who started by searching
 * can keep browsing afterwards without being dropped back on a menu, and it guarantees
 * that everything findable is also readable.
 *
 * @param openTopicId jump straight to a page (used by "Report a problem" cross-links).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualSheet(
    onDismiss: () -> Unit,
    onOpenFeature: (HelpTopic) -> Unit,
    openTopicId: String? = null
) {
    val accent = LocalAccent.current
    var query by remember { mutableStateOf("") }
    var section by remember { mutableStateOf(Manual.SECTIONS.first()) }
    var open by remember { mutableStateOf(openTopicId?.let { Manual.topic(it) }) }

    SheetFrame(onDismiss = onDismiss) {
        // ---------------------------------------------------------------- header
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (open != null) {
                CircleIconButton(GhIcons.ArrowBack, onClick = { open = null })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Manual", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 11.sp, letterSpacing = 1.6.sp)
                    Text(
                        open!!.section,
                        style = MaterialTheme.typography.titleMedium,
                        color = GC.Text,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text("User manual", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 24.sp)
                    Text(
                        "${Manual.topics.size} pages · every feature · v${Manual.VERSION}",
                        style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp
                    )
                }
                CircleIconButton(GhIcons.Close, onClick = onDismiss)
            }
        }
        Spacer(Modifier.height(12.dp))

        val detail = open
        if (detail != null) {
            TopicPage(detail, accent, onOpenFeature)
        } else {
            // ------------------------------------------------------------ search field
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search the manual — \"lag\", \"pair\", \"fps\"", color = GC.TextDim, fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = { AIcon(GhIcons.Search, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = RomKit.Panel2,
                    unfocusedContainerColor = RomKit.Panel2,
                    focusedTextColor = GC.Text,
                    unfocusedTextColor = GC.Text,
                    cursorColor = accent,
                    focusedIndicatorColor = accent,
                    unfocusedIndicatorColor = RomKit.Stroke
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            if (query.isBlank()) {
                // Section chips — one section at a time keeps the list readable and
                // keeps this sheet a plain Column (a lazy list cannot live inside the
                // sheet's own scroll container).
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Manual.SECTIONS.forEach { name ->
                        val selected = name == section
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) accent else RomKit.Panel2)
                                .border(1.dp, if (selected) Color.Transparent else RomKit.Stroke, RoundedCornerShape(50))
                                .clickable { section = name }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            Text(
                                name,
                                color = if (selected) GC.OnAccent else GC.TextSoft,
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Manual.topics.filter { it.section == section }.forEach { topic ->
                    TopicRow(topic, accent) { open = topic }
                }
            } else {
                val hits = Manual.search(query)
                if (hits.isEmpty()) {
                    EmptySearch(query)
                } else {
                    Text(
                        "${hits.size} result${if (hits.size == 1) "" else "s"}",
                        color = GC.TextDim,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    hits.forEach { topic ->
                        TopicRow(topic, accent) { open = topic }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Section browse list row. */
@Composable
private fun TopicRow(topic: HelpTopic, accent: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 7.dp)
            .clip(RomKit.R12)
            .background(RomKit.Panel2)
            .border(1.dp, RomKit.Stroke, RomKit.R12)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(topic.title, color = GC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                topic.summary,
                color = GC.TextDim,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        AIcon(GhIcons.ChevronRight, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun EmptySearch(query: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RomKit.R12)
            .background(RomKit.Panel2)
            .border(1.dp, RomKit.Stroke, RomKit.R12)
            .padding(16.dp)
    ) {
        Text("Nothing matched \"$query\"", color = GC.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(
            "Try the feature's name as it appears in the app (Delay Fixer, Aim Mark, " +
                "Magic Pad Mapper), or a plain word for the problem (lag, crash, overlay).",
            color = GC.TextDim, fontSize = 12.sp, lineHeight = 18.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Still stuck? Open Tools ▸ Report a problem and send the log — that reaches the developer.",
            color = LocalAccent.current, fontSize = 12.sp, lineHeight = 18.sp
        )
    }
}

/** One manual page: what it is, the exact taps, the caveat, and a jump button. */
@Composable
private fun TopicPage(topic: HelpTopic, accent: Color, onOpenFeature: (HelpTopic) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(topic.title, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 23.sp)
        Spacer(Modifier.height(6.dp))
        Text(topic.summary, color = GC.TextSoft, fontSize = 14.sp, lineHeight = 21.sp)

        if (topic.steps.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("HOW TO", color = accent, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            topic.steps.forEachIndexed { index, line ->
                Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .size(21.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(line, color = GC.TextSoft, fontSize = 13.5.sp, lineHeight = 20.sp)
                }
            }
        }

        if (topic.note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RomKit.R12)
                    .background(RomKit.Panel2)
                    .border(1.dp, RomKit.Stroke, RomKit.R12)
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                AIcon(GhIcons.Info, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(9.dp))
                Text(topic.note, color = GC.TextSoft, fontSize = 12.5.sp, lineHeight = 18.sp)
            }
        }

        if (topic.sheet.isNotBlank() || topic.tab.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            BigButton(
                text = "Open it for me",
                onClick = { onOpenFeature(topic) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = GhIcons.OpenInFull
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AIcon(GhIcons.Info, contentDescription = null, tint = GC.TextDim, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "GamersHub v" + BuildConfig.VERSION_NAME + " · " + Manual.topics.size + " pages",
                color = GC.TextDim, fontSize = 11.sp
            )
        }
    }
}

/**
 * The issue reporter.
 *
 * Opens by itself when the app caught a failure (see RootShell's poll), and can be
 * opened on demand for "a feature did not work" — which is the case that has no
 * exception to show. Everything it would send is displayed first: the user always sees
 * the log before anybody else does, and nothing leaves the phone until they pick a
 * destination.
 */
/**
 * Help & Support — the one screen that can reach the developer.
 *
 * ## Two doors, one destination
 *
 * **Send feedback** and **Report a problem** differ only in what is attached: feedback
 * carries the build, the phone and (if the user allows it) an approximate region;
 * a problem report carries those *plus* the latest sanitized error. Both open a mail
 * draft addressed to [SupportDiagnostics.SUPPORT_EMAIL].
 *
 * ## Everything is disclosed before it leaves
 *
 * The exact text of the attachment is rendered in this sheet, in full, before the
 * send button. It can be removed with one tap. The network row says which service is
 * contacted and that leaving it on is what produces the IP and region. Nothing is
 * transmitted until the user presses send inside their own email app — this sheet only
 * ever hands a draft over.
 *
 * ## The fallback is not an afterthought
 *
 * If no app on the device handles `mailto:`, the sheet says the report was NOT sent,
 * shows the address, and offers **Copy email address** so the user can still reach the
 * developer from a browser or another device. A button that silently does nothing is
 * exactly the failure mode this whole feature exists to remove.
 */
@Composable
fun ReportSheet(
    onDismiss: () -> Unit,
    initialNote: String = "",
    initialKind: SupportDiagnostics.Kind = SupportDiagnostics.Kind.PROBLEM,
    /**
     * Describes a crash that killed the previous launch, when there was one.
     *
     * Shown as a banner rather than a dialog: it is the reason the sheet opened, and the
     * user should be able to read the failure before deciding to send it.
     */
    startupCrashLine: String = "",
) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()

    var kind by remember { mutableStateOf(initialKind) }
    var note by remember { mutableStateOf(initialNote) }
    var busy by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<SupportMail.Result.NoMailApp?>(null) }
    var showAttachment by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }

    // Consent, per report. Defaults are the useful ones, and both are visible.
    var attachLog by remember { mutableStateOf(true) }
    var attachNetwork by remember { mutableStateOf(true) }
    var autoPopup by remember { mutableStateOf(IssueReporter.autoPopupEnabled(context)) }

    // What is already known locally, cheaply, so the disclosure is true before sending.
    val homeRegion = remember { java.util.Locale.getDefault().displayCountry }
    val mailApps = remember { SupportMail.mailAppLabels(context) }
    val hasFailure = remember(tick) { IssueReporter.build(context, "").hasFailure }

    // The attachment is built once per (kind, note, consent) change, so what the user
    // reads in the preview is byte-identical to what gets attached.
    val diag = remember(kind, note, attachLog, attachNetwork, tick) {
        SupportDiagnostics.collect(
            context = context,
            kind = kind,
            note = note,
            includeLog = attachLog,
            includeNetwork = attachNetwork,
        )
    }

    fun subject(): String =
        if (kind == SupportDiagnostics.Kind.PROBLEM) SupportMail.SUBJECT_PROBLEM else SupportMail.SUBJECT_FEEDBACK

    /** A short body: the detail travels in the attached file. */
    fun body(): String = buildString {
        appendLine(if (kind == SupportDiagnostics.Kind.PROBLEM) "Hi — something went wrong." else "Hi — some feedback.")
        appendLine()
        if (note.isNotBlank()) {
            appendLine(note.trim())
            appendLine()
        }
        appendLine("A diagnostics file is attached (${diag.fileName}).")
        appendLine("It contains the app version, phone model, Android version, language, the time of")
        appendLine("this report, and — if you left the network row on — an approximate region from your")
        appendLine("public IP. It contains no passwords, tokens, messages or personal files.")
        appendLine()
        appendLine("Sent from GamersHub.")
    }

    fun send() {
        if (busy.isNotBlank()) return
        scope.launch {
            busy = "Preparing the attachment…"
            val file = withContext(Dispatchers.IO) {
                SupportDiagnostics.writeTo(context, diag)
            }
            busy = ""
            val result = withContext(Dispatchers.Main) {
                SupportMail.openDraft(context, subject(), body(), file)
            }
            failure = null
            when (result) {
                is SupportMail.Result.Opened -> {
                    status = "Draft opened in ${result.appLabel}. Review it and tap send — nothing is " +
                        "sent until you do."
                    // The offer has been acted on; do not keep nagging at launch.
                    StartupWatch.resolve(context)
                }
                is SupportMail.Result.OpenedWithoutAttachment -> {
                    status = "Draft opened in ${result.appLabel} without the file: ${result.reason}."
                    StartupWatch.resolve(context)
                }
                is SupportMail.Result.NoMailApp -> {
                    // The honest outcome: say it was not sent, and give the address.
                    failure = result
                    status = ""
                }
            }
        }
    }

    SheetFrame(onDismiss = onDismiss) {
        // ---------------------------------------------------------------- header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(GhIcons.Campaign, size = 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Help & Support",
                    style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 23.sp
                )
                Text(
                    "Reaches the developer by email · you send it yourself",
                    style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            CircleIconButton(GhIcons.Close, onClick = onDismiss)
        }
        if (startupCrashLine.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RomKit.R12)
                    .background(RomKit.Panel2)
                    .border(1.dp, LocalAccent.current.copy(alpha = 0.45f), RomKit.R12)
                    .padding(12.dp)
            ) {
                Text(
                    "GamersHub stopped last time",
                    color = LocalAccent.current, fontSize = 12.5.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(startupCrashLine, color = GC.TextDim, fontSize = 11.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    "The log from that run is attached below. Not every failure can be caught — " +
                        "a crash before the app starts, or an out-of-memory kill, leaves nothing.",
                    color = GC.TextDim.copy(alpha = 0.85f), fontSize = 10.sp, lineHeight = 14.sp
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // ------------------------------------------------------- which door
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            KindButton(
                text = "SEND FEEDBACK",
                selected = kind == SupportDiagnostics.Kind.FEEDBACK,
                accent = accent,
                modifier = Modifier.weight(1f)
            ) { kind = SupportDiagnostics.Kind.FEEDBACK }
            KindButton(
                text = "REPORT A PROBLEM",
                selected = kind == SupportDiagnostics.Kind.PROBLEM,
                accent = accent,
                modifier = Modifier.weight(1f)
            ) { kind = SupportDiagnostics.Kind.PROBLEM }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (kind == SupportDiagnostics.Kind.PROBLEM)
                "Sends the build, your phone, and the latest error the app caught."
            else "Sends the build and your phone. No error log, because this is not a fault.",
            color = GC.TextDim, fontSize = 11.5.sp, lineHeight = 16.sp
        )

        // ------------------------------------------------------- what they wrote
        Spacer(Modifier.height(14.dp))
        Text("WHAT HAPPENED", color = accent, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = note,
            onValueChange = { note = it.take(1200) },
            placeholder = {
                Text(
                    if (kind == SupportDiagnostics.Kind.PROBLEM)
                        "Optional — e.g. \"tapping Aim Mark turns it on but no dot appears\""
                    else "Optional — what would make GamersHub better?",
                    color = GC.TextDim, fontSize = 12.5.sp
                )
            },
            minLines = 2,
            maxLines = 5,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = RomKit.Panel2,
                unfocusedContainerColor = RomKit.Panel2,
                focusedTextColor = GC.Text,
                unfocusedTextColor = GC.Text,
                cursorColor = accent,
                focusedIndicatorColor = accent,
                unfocusedIndicatorColor = RomKit.Stroke
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // ------------------------------------------------------- what travels
        Spacer(Modifier.height(14.dp))
        Text("WHAT WILL BE SHARED", color = accent, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))

        Frame {
            FactRow("App", "GamersHub ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            FactRow(
                "Device",
                "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android " +
                    "${android.os.Build.VERSION.RELEASE}"
            )
            FactRow("Language", java.util.Locale.getDefault().toString())
            FactRow("Time", "now, when the report is built")
            Divider()
            // Consent for the error log
            ConsentRow(
                title = "Latest error log",
                detail = when {
                    kind == SupportDiagnostics.Kind.FEEDBACK -> "Not included for feedback."
                    !hasFailure -> "No error was caught — nothing to attach."
                    attachLog -> "Sanitized: tokens, keys and emails are removed."
                    else -> "Left out at your request."
                },
                on = attachLog && kind == SupportDiagnostics.Kind.PROBLEM && hasFailure,
                enabled = kind == SupportDiagnostics.Kind.PROBLEM && hasFailure,
                accent = accent
            ) { attachLog = it }
            Divider()
            // Consent for the network lookup
            ConsentRow(
                title = "Public IP and approximate region",
                detail = if (attachNetwork)
                    "Looked up from ${SupportDiagnostics.IP_ENDPOINT} — this is the only service " +
                        "contacted, and only now. $homeRegion (from your language) is not used for this."
                else "Left out. The report will say \"unavailable\" instead of guessing.",
                on = attachNetwork,
                enabled = true,
                accent = accent
            ) { attachNetwork = it }
        }

        // ------------------------------------------------------- preview
        Spacer(Modifier.height(9.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .clickable { showAttachment = !showAttachment }
                .padding(vertical = 6.dp)
        ) {
            AIcon(GhIcons.Storage, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                (if (showAttachment) "HIDE" else "READ") + " the exact file (" +
                    diag.fileName + ")",
                color = accent, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold
            )
        }
        if (showAttachment) {
            Text(
                diag.text,
                color = GC.TextSoft,
                fontSize = 9.5.sp,
                lineHeight = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 210.dp)
                    .clip(RomKit.R12)
                    .background(RomKit.Panel2)
                    .border(1.dp, RomKit.Stroke, RomKit.R12)
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
            )
        }

        // ------------------------------------------------------- address + send
        Spacer(Modifier.height(14.dp))
        Text(
            "TO  " + SupportDiagnostics.SUPPORT_EMAIL,
            color = GC.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (mailApps.isEmpty()) "No email app found on this device."
            else "Available: " + mailApps.joinToString(", "),
            color = GC.TextDim, fontSize = 10.5.sp, lineHeight = 14.sp
        )

        Spacer(Modifier.height(12.dp))
        BigButton(
            text = if (busy.isNotBlank()) busy else "OPEN EMAIL DRAFT",
            onClick = { send() },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = GhIcons.Campaign
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Your email app opens with this pre-filled. You review it and press send there — " +
                "GamersHub cannot send mail by itself.",
            color = GC.TextDim, fontSize = 10.5.sp, lineHeight = 15.sp
        )

        // ------------------------------------------------------- no-mail-app fallback
        val f = failure
        if (f != null) {
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RomKit.R12)
                    .background(RomKit.Panel2)
                    .border(1.dp, Color(0xFFD9534F).copy(alpha = 0.5f), RomKit.R12)
                    .padding(13.dp)
            ) {
                Text(
                    "The report was NOT sent",
                    color = Color(0xFFD9534F), fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(f.reason, color = GC.TextDim, fontSize = 11.5.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Email it yourself from any mail app or website:",
                    color = GC.TextDim, fontSize = 11.5.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    f.address,
                    color = GC.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                BigButton(
                    text = "COPY EMAIL ADDRESS",
                    onClick = {
                        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(
                            android.content.ClipData.newPlainText("GamersHub support", f.address)
                        )
                        status = "Address copied. Attach the file from ${diag.fileName} if you saved it."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    primary = false
                )
                Spacer(Modifier.height(8.dp))
                BigButton(
                    text = "SAVE THE DIAGNOSTIC FILE",
                    onClick = {
                        val saved = SupportDiagnostics.writeTo(context, diag)
                        status = if (saved != null)
                            "Saved to ${saved.absolutePath} — attach it to the email yourself."
                        else "Could not write the file."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    primary = false,
                    leadingIcon = GhIcons.Storage
                )
            }
        }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(status, color = accent, fontSize = 11.5.sp, lineHeight = 16.sp)
        }

        // ------------------------------------------------------- honesty + clear
        Spacer(Modifier.height(14.dp))
        Text(
            "Kept on this phone until you share it: the report file sits in GamersHub's private " +
                "cache, at most three are kept, and each is deleted after a week. Nothing is " +
                "uploaded at any point.",
            color = GC.TextDim, fontSize = 10.5.sp, lineHeight = 15.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Saved reports: ${SupportDiagnostics.savedCount(context)}",
                color = GC.TextDim, fontSize = 11.sp, modifier = Modifier.weight(1f)
            )
            Text(
                "DELETE THEM",
                color = accent, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(RomKit.Panel2)
                    .border(1.dp, RomKit.Stroke, RoundedCornerShape(50))
                    .clickable {
                        SupportDiagnostics.clear(context)
                        status = "Saved reports deleted."
                        tick++
                    }
                    .padding(horizontal = 13.dp, vertical = 7.dp)
            )
        }

        // ------------------------------------------------------- auto popup
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RomKit.R12)
                .background(RomKit.Panel2)
                .border(1.dp, RomKit.Stroke, RomKit.R12)
                .padding(13.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Offer this after a crash", color = GC.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "When GamersHub catches a crash it opens this next time, once. Crashes before " +
                        "the app can start are offered on the following launch — not every kind of " +
                        "failure can be detected.",
                    color = GC.TextDim, fontSize = 11.5.sp, lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(10.dp))
            com.gamershub.ui.components.Toggle(on = autoPopup) {
                autoPopup = it
                IssueReporter.setAutoPopup(context, it)
            }
        }
    }

    LaunchedEffect(Unit) { IssueReporter.markSeen(context) }
}

/** Small pill used for the two support options. */
@Composable
private fun KindButton(
    text: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent.copy(alpha = 0.18f) else RomKit.Panel2)
            .border(1.dp, if (selected) accent else RomKit.Stroke, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) accent else GC.TextDim,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}

/** Plain bordered container used by the disclosure block. */
@Composable
private fun Frame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RomKit.R12)
            .background(RomKit.Panel2)
            .border(1.dp, RomKit.Stroke, RomKit.R12)
            .padding(13.dp),
        content = content
    )
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = GC.TextDim, fontSize = 11.5.sp, modifier = Modifier.width(78.dp))
        Text(value, color = GC.Text, fontSize = 11.5.sp, modifier = Modifier.weight(1f), lineHeight = 15.sp)
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .height(1.dp)
            .background(GC.Stroke)
    )
}

/**
 * A row with a switch, for one category of data.
 *
 * [enabled] false renders the row as a statement rather than a control, so "no error was
 * caught" cannot look like a setting the user failed to turn on.
 */
@Composable
private fun ConsentRow(
    title: String,
    detail: String,
    on: Boolean,
    enabled: Boolean,
    accent: Color,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) GC.Text else GC.TextDim,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(detail, color = GC.TextDim, fontSize = 10.5.sp, lineHeight = 14.sp)
        }
        if (enabled) {
            Spacer(Modifier.width(10.dp))
            com.gamershub.ui.components.Toggle(on = on) { onChange(it) }
        } else {
            Spacer(Modifier.width(10.dp))
            Text("—", color = GC.TextDim, fontSize = 13.sp)
        }
    }
}

