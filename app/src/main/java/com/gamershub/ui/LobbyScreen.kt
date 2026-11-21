package com.gamershub.ui

import com.gamershub.ui.icons.GhIcons

import com.gamershub.ui.components.AIcon

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.data.InstalledApp
import com.gamershub.theme.RomKit
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import com.gamershub.ui.components.EngineSwitch
import com.gamershub.ui.components.CircleIconButton
import com.gamershub.ui.components.HeroMark
import com.gamershub.ui.components.IconChip

/** Lobby — hero card, game library, rows / empty state. Fit-safe on all screens. */
@Composable
fun LobbyScreen(
    cpuPct: Int,
    library: List<InstalledApp>,
    version: String,
    playMinutes: (String) -> Long,
    libraryCount: Int,
    onSelectApps: () -> Unit,
    onAddGames: () -> Unit,
    onGameTap: (InstalledApp) -> Unit,
    /** Tapping the emblem (or PLAY ROOM) opens the slide-to-enter sheet. */
    onGameSpace: () -> Unit,
    /** Opens the global search / user manual. */
    onSearch: () -> Unit = {},
    /** Master engine: whether anything is allowed to run. */
    engineOn: Boolean,
    engineChannel: String,
    onEngineToggle: () -> Unit
) {
    val accent = LocalAccent.current
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 120.dp)
    ) {
        // ---- Header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Base", style = MaterialTheme.typography.displaySmall, color = GC.Text, fontSize = 33.sp)
                Text(
                    com.gamershub.ui.components.Brand.BYLINE,
                    style = MaterialTheme.typography.labelMedium,
                    color = GC.TextDim,
                    fontSize = 13.sp,
                    letterSpacing = 0.6.sp
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RomKit.R12)
                    .background(RomKit.Panel2)
                    .border(1.dp, RomKit.Stroke, RomKit.R12)
                    .padding(horizontal = 13.dp, vertical = 8.dp)
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text("Here", style = MaterialTheme.typography.labelLarge, color = GC.TextSoft, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Global search -----------------------------------------------------
        // One field that reaches every feature, setting and help page. It reads as a
        // search box but it is the manual's front door on purpose: a user who types
        // "lag" should be able to *read* what to do, not just be teleported to a sheet.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RomKit.R12)
                .background(RomKit.Panel)
                .border(1.dp, RomKit.Stroke, RomKit.R12)
                .clickable(onClick = onSearch)
                .padding(horizontal = 13.dp, vertical = 12.dp)
        ) {
            AIcon(GhIcons.Search, contentDescription = "Search", tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Search anything — feature, setting or help",
                style = MaterialTheme.typography.labelMedium,
                color = GC.TextDim,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .clip(RomKit.R10)
                    .background(RomKit.Panel2)
                    .border(1.dp, RomKit.Stroke, RomKit.R10)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text("MANUAL", style = RomKit.LabelSm, color = RomKit.TextLo)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Global search -----------------------------------------------------
        // One door to every feature, every setting and every help page. It reads as a
        // search field but it opens the manual, so a user who types "lag" can *read*
        // what to do instead of being dropped somewhere they cannot explain later.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RomKit.R12)
                .background(RomKit.Panel)
                .border(1.dp, RomKit.Stroke, RomKit.R12)
                .clickable(onClick = onSearch)
                .padding(horizontal = 13.dp, vertical = 12.dp)
        ) {
            AIcon(GhIcons.Search, contentDescription = "Search", tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Search anything — feature, setting or help",
                style = MaterialTheme.typography.labelMedium,
                color = GC.TextDim,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .clip(RomKit.R10)
                    .background(RomKit.Panel2)
                    .border(1.dp, RomKit.Stroke, RomKit.R10)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text("MANUAL", style = RomKit.LabelSm, color = RomKit.TextLo)
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- Master engine ----------------------------------------------------
        // The one switch that decides whether GamersHub touches the phone. It is in
        // the lobby header, above the fold, because a user who turned it off has to
        // be able to see that it is off without opening anything.
        EngineSwitch(
            on = engineOn,
            channel = engineChannel,
            onToggle = onEngineToggle
        )

        Spacer(Modifier.height(14.dp))

        // ---- Hero card ----
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RomKit.R18)
                .background(GC.HeroBrush)
                .border(1.dp, RomKit.Stroke, RomKit.R18)
        ) {
            // The emblem is the door. Tapping it opens the slide-to-enter sheet,
            // so there is exactly one way into Game Space and it is deliberate.
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.gamershub.R.drawable.ic_float_emblem),
                contentDescription = "Enter Game Space",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 14.dp)
                    .size(112.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onGameSpace)
            )
            Column {
                Column(Modifier.padding(start = 18.dp, top = 16.dp, bottom = 12.dp)) {
                    Text("GAME", style = RomKit.Label, color = RomKit.Ember)
                    Text(
                        "CORNER",
                        style = RomKit.Display,
                        color = RomKit.TextHi
                    )
                    Text("V2.5.3-25300", style = RomKit.LabelSm, color = RomKit.TextLo)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, RomKit.Divider, RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) {
                    Box(Modifier.size(42.dp).clip(RomKit.R12).background(RomKit.Panel2), contentAlignment = Alignment.Center) {
                        AIcon(GhIcons.Apps, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("MY PICKS", style = RomKit.LabelSm, color = RomKit.TextLo)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$libraryCount", style = RomKit.MonoValue.copy(fontSize = 15.sp), color = RomKit.TextHi)
                            Text(" added", style = RomKit.Caption, color = RomKit.TextLo)
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .clip(RomKit.R10)
                                    .background(RomKit.emberBrush())
                                    .clickable(onClick = onSelectApps)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "SELECT",
                                    style = RomKit.Label.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                                    color = RomKit.OnEmber
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RomKit.R12)
                            .background(RomKit.Panel2)
                            .border(1.dp, RomKit.Stroke, RomKit.R12)
                            .clickable(onClick = onGameSpace)
                            .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp)
                    ) {
                        Column {
                            Text("PLAY", style = RomKit.LabelSm, color = RomKit.TextLo)
                            Text("ROOM", style = RomKit.MonoValue, color = RomKit.TextHi)
                        }
                        Spacer(Modifier.width(8.dp))
                        AIcon(GhIcons.Rocket, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- Library header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Game Shelf", style = RomKit.Headline.copy(fontSize = 16.sp), color = RomKit.TextHi)
            Spacer(Modifier.width(8.dp))
            Text("${library.size} / 10", style = RomKit.LabelSm, color = RomKit.TextLo)
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RomKit.RPill)
                    .background(RomKit.Panel2)
                    .border(1.dp, RomKit.Stroke, RomKit.RPill)
                    .clickable(onClick = onAddGames)
                    .padding(start = 15.dp, end = 7.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Text("Pick games", style = RomKit.Caption.copy(fontSize = 12.sp), color = RomKit.TextHi)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(25.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    AIcon(GhIcons.Add, contentDescription = null, tint = RomKit.OnEmber, modifier = Modifier.size(15.dp))
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (library.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RomKit.R18)
                    .background(RomKit.Panel)
                    .border(1.dp, RomKit.Stroke, RomKit.R18)
                    .padding(vertical = 26.dp, horizontal = 18.dp)
            ) {
                IconChip(GhIcons.Gamepad, size = 64)
                Spacer(Modifier.height(13.dp))
                Text("Nothing found on this phone", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 20.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Add prioritized applications to start Axeron Space.",
                    style = MaterialTheme.typography.labelMedium,
                    color = GC.TextDim,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.85f))
                        .clickable(onClick = onAddGames)
                        .padding(horizontal = 22.dp, vertical = 11.dp)
                ) {
                    AIcon(GhIcons.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Pick games", style = MaterialTheme.typography.labelLarge, color = Color.White, fontSize = 15.sp)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // An explicit "add" tile sits at the end of the shelf, so adding an
                // app is possible without finding the header pill or scrolling back
                // up. Apps and games are the same list: the picker shows everything
                // installed, filtered to nothing.
                library.forEach { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RomKit.R18)
                            .background(RomKit.Panel)
                            .border(1.dp, RomKit.Stroke, RomKit.R18)
                            .clickable { onGameTap(app) }
                            .padding(12.dp)
                    ) {
                        Image(
                            painter = androidx.compose.ui.graphics.painter.BitmapPainter(app.icon),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(15.dp))
                        )
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.name, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(13.dp).clip(CircleShape).border(2.dp, GC.TextDim, CircleShape))
                                Spacer(Modifier.width(5.dp))
                                Text("${playMinutes(app.pkg)}m", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 13.sp)
                            }
                        }
                        CircleIconButton(GhIcons.ChevronRight, onClick = { onGameTap(app) })
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RomKit.R18)
                        .background(RomKit.Panel)
                        .border(1.dp, RomKit.Stroke, RomKit.R18)
                        .clickable(onClick = onAddGames)
                        .padding(horizontal = 14.dp, vertical = 16.dp)
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RomKit.R12).background(RomKit.Panel2),
                        contentAlignment = Alignment.Center
                    ) {
                        AIcon(GhIcons.Add, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Add app or game",
                            style = MaterialTheme.typography.titleMedium,
                            color = GC.Text,
                            fontSize = 16.sp
                        )
                        Text(
                            "Anything installed on this phone",
                            style = MaterialTheme.typography.labelMedium,
                            color = GC.TextDim,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
