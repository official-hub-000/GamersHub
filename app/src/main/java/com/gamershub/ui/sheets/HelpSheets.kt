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
@Composable
fun ReportSheet(onDismiss: () -> Unit, initialNote: String = "") {
    val context = LocalContext.current
    val accent = LocalAccent.current
    var note by remember { mutableStateOf(initialNote) }
    var showLog by remember { mutableStateOf(false) }
    var sentTick by remember { mutableStateOf(0) }
    var autoPopup by remember { mutableStateOf(IssueReporter.autoPopupEnabled(context)) }
    var status by remember { mutableStateOf("") }

    // Rebuilt when the note changes or a report is sent: the report is a snapshot, and
    // the user must see exactly what they are about to send.
    val report = remember(note, sentTick) { IssueReporter.build(context, note) }
    val log = remember(sentTick) { IssueReporter.readLog(context) }

    SheetFrame(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(GhIcons.Warning, size = 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (report.hasFailure) "Something broke" else "Report a problem",
                    style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 23.sp
                )
                Text(
                    if (report.hasFailure) "The error is below — send it and it gets fixed"
                    else "A feature misbehaved? Say what happened, the log goes with it",
                    style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            CircleIconButton(GhIcons.Close, onClick = onDismiss)
        }
        Spacer(Modifier.height(12.dp))

        // ---------------------------------------------------------------- what happened
        Text("WHAT HAPPENED", color = accent, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        TextField(
            value = note,
            onValueChange = { note = it.take(600) },
            placeholder = {
                Text(
                    "Optional — e.g. \"tap on Aim Mark turned it on but no dot appeared\"",
                    color = GC.TextDim, fontSize = 12.5.sp
                )
            },
            minLines = 2,
            maxLines = 4,
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

        // ---------------------------------------------------------------- the failure
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RomKit.R12)
                .background(RomKit.Panel2)
                .border(1.dp, RomKit.Stroke, RomKit.R12)
                .padding(13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AIcon(
                    if (report.hasFailure) GhIcons.Warning else GhIcons.CheckCircle,
                    contentDescription = null,
                    tint = if (report.hasFailure) accent else RomKit.Good,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (report.hasFailure) "Error log captured" else "No error was caught",
                    color = GC.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (log.isNotBlank()) {
                    Text(
                        if (showLog) "HIDE" else "SHOW",
                        color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { showLog = !showLog }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
            if (log.isBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "The app is running clean. The build, phone and permission details are still " +
                        "attached, so a \"this button does nothing\" report is still useful.",
                    color = GC.TextDim, fontSize = 12.sp, lineHeight = 17.sp
                )
            }
            if (showLog && log.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    log.takeLast(3000),
                    color = GC.TextSoft,
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 210.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }

        // ---------------------------------------------------------------- build + phone
        Spacer(Modifier.height(8.dp))
        Text(
            "GamersHub ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
                "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android " +
                "${android.os.Build.VERSION.RELEASE} · attached automatically",
            color = GC.TextDim, fontSize = 10.5.sp, lineHeight = 15.sp
        )

        // ---------------------------------------------------------------- send
        Spacer(Modifier.height(14.dp))
        BigButton(
            text = "Send to the developer",
            onClick = {
                IssueReporter.share(context, IssueReporter.build(context, note))
                status = "Pick any app — mail, chat, whatever you have. The report is attached."
                sentTick++
            },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = GhIcons.Share
        )
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            BigButton(
                text = "Copy",
                onClick = {
                    IssueReporter.copy(context, IssueReporter.build(context, note))
                    status = "Copied — paste it anywhere."
                },
                modifier = Modifier.weight(1f),
                primary = false
            )
            BigButton(
                text = "Save file",
                onClick = {
                    val saved = IssueReporter.writeFile(context, IssueReporter.build(context, note))
                    status = if (saved != null) "Saved: ${saved.name}" else "Could not write the file"
                },
                modifier = Modifier.weight(1f),
                primary = false,
                leadingIcon = GhIcons.Storage
            )
        }
        Spacer(Modifier.height(9.dp))
        BigButton(
            text = "Open a GitHub issue",
            onClick = {
                IssueReporter.openGitHubIssue(context, IssueReporter.build(context, note))
                status = "Fill in the title and tap Submit — the body is already written."
            },
            modifier = Modifier.fillMaxWidth(),
            primary = false,
            leadingIcon = GhIcons.Campaign
        )

        if (status.isNotBlank()) {
            Spacer(Modifier.height(9.dp))
            Text(status, color = accent, fontSize = 11.5.sp, lineHeight = 16.sp)
        }

        // ---------------------------------------------------------------- after sending
        if (report.hasFailure) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Clear the log once it is sent, so the next popup is a new problem",
                    color = GC.TextDim, fontSize = 11.5.sp, lineHeight = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "CLEAR",
                    color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(RomKit.Panel2)
                        .border(1.dp, RomKit.Stroke, RoundedCornerShape(50))
                        .clickable {
                            IssueReporter.clearLog(context)
                            status = "Log cleared."
                            sentTick++
                        }
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                )
            }
        }

        // ---------------------------------------------------------------- auto popup
        Spacer(Modifier.height(14.dp))
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
                Text("Open this by itself", color = GC.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "When GamersHub catches a failure it opens this sheet with the log already in it.",
                    color = GC.TextDim, fontSize = 11.5.sp, lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(10.dp))
            com.gamershub.ui.components.Toggle(on = autoPopup) {
                autoPopup = it
                IssueReporter.setAutoPopup(context, it)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Nothing is uploaded on its own. This text stays on your phone until you send it, " +
                "and it contains no account, no location and no personal files — only the app " +
                "version, the phone model, the permissions and the error log.",
            color = GC.TextDim, fontSize = 10.5.sp, lineHeight = 15.sp
        )
    }

    // A failure that arrived while the sheet was open should not be lost.
    LaunchedEffect(Unit) { IssueReporter.markSeen(context) }
}
