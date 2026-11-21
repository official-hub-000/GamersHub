package com.gamershub.core

import com.gamershub.BuildConfig

/**
 * The in-app user manual, as data.
 *
 * ## Why this is a list and not a screen
 *
 * Two features read the same words: the manual (browse by section) and the global
 * search (type anything, find the page). If the text lived inside composables, the
 * search index would drift from what the screen says the first time anybody edited a
 * heading. Here the manual *is* the index, so a topic that can be found is always a
 * topic that can be read, and vice versa.
 *
 * ## What a topic has to carry
 *
 * `title` — what the user sees in the app (the same words the button uses).
 * `aliases` — the other names people use for it. "ping", "lag", "delay", "dns".
 * `sheet` / `tab` — where it lives, so a search hit can jump straight there instead
 * of making the user read and then go hunting.
 * `steps` — the exact taps, because "open the panel" is not a manual.
 * `note` — the one caveat that causes most support messages.
 */
data class HelpTopic(
    val id: String,
    val section: String,
    val title: String,
    val aliases: String,
    val summary: String,
    val steps: List<String> = emptyList(),
    val note: String = "",
    /** Sheet key understood by RootShell.sheetFor(); empty when the topic is read-only. */
    val sheet: String = "",
    /** Tab name (LOBBY / LABS / TOOLS / ROOT / PROFILE); empty when not a tab. */
    val tab: String = ""
)

/**
 * Every feature in GamersHub, written down once.
 *
 * Ordered by section as a new user meets them: first the two doors into the app,
 * then each tab, then the surfaces that are not a tab (overlays, studio, root), then
 * the look-and-feel, then safety, then this manual's own help pages.
 */
object Manual {

    /**
     * Shown on every manual page. Read from the build, never typed by hand — a manual
     * that claims a different version than the app is worse than no version at all.
     */
    val VERSION: String get() = BuildConfig.VERSION_NAME

    /** Section order as shown in the manual — deliberately the user's path, not alphabetical. */
    val SECTIONS = listOf(
        "Start Here",
        "Lobby",
        "Game Labs",
        "Tools",
        "Overlays",
        "Studio",
        "Network & Performance",
        "Root",
        "Look & Feel",
        "Safety",
        "Help & Reporting",
        "Fixes"
    )

    val topics: List<HelpTopic> = listOf(

        /* ---------------------------------------------------------------- start here */

        HelpTopic(
            id = "first-run",
            section = "Start Here",
            title = "First launch",
            aliases = "setup install start begin onboarding intro skip what to do first",
            summary = "The intro clip plays once, then five permission cards walk you through " +
                "everything GamersHub needs. Nothing privileged is touched before you allow it.",
            steps = listOf(
                "Tap the screen to skip the intro clip.",
                "Read each permission card and tap Let it — or Not now to come back later.",
                "Land on the Lobby (the Base screen). The engine starts OFF on purpose.",
                "Flip the engine switch when you want overlays, root and capture to be allowed."
            ),
            note = "Not now is safe: every card can be reopened from Profile ▸ Setup or Tools ▸ Power Up."
        ),
        HelpTopic(
            id = "permissions",
            section = "Start Here",
            title = "Permissions",
            aliases = "allow notification overlay display over other apps modify system settings usage access grant access restricted settings",
            summary = "Five things. Four are Android permissions; the fifth (Shizuku) is optional " +
                "but unlocks the deeper tools.",
            steps = listOf(
                "Notifications — lets running services show their status.",
                "Display over other apps — required by every overlay (bubble, watcher, aim mark, mapping, cursor).",
                "Modify system settings — needed to change brightness and display values and put them back.",
                "Usage access — detects the game you opened and counts play time.",
                "Shizuku helper (optional) — see the Power Up page."
            ),
            note = "On Android 13+ Usage access may be greyed out in Settings. Open App Info ▸ ⋮ ▸ Allow restricted settings, then grant it."
        ),
        HelpTopic(
            id = "engine",
            section = "Start Here",
            title = "Master Engine",
            aliases = "engine switch master on off power start stop allow everything",
            summary = "One switch that owns root, Shizuku, overlays and capture. While it is off, " +
                "GamersHub refuses to start anything — the honest way to say \"do nothing right now\".",
            steps = listOf(
                "Lobby ▸ flip the switch in the header, or Tools ▸ MASTER ENGINE.",
                "Turning it ON arms the channel (root ▸ Shizuku ▸ shell ▸ app only) and restores your last state.",
                "Turning it OFF opens a confirmation sheet listing exactly what will be undone.",
                "Read that list, then confirm — the engine stops services, undoes journaled changes and restores settings."
            ),
            note = "The engine remembers its state across restarts, so a crash can never silently leave privileged surfaces armed."
        ),
        HelpTopic(
            id = "power-up",
            section = "Start Here",
            title = "Power Up Spot",
            aliases = "activation shizuku door pair quick start pair by hand net mode otg usb adb env level deep tier",
            summary = "The activation sheet: everything that raises the app from \"app only\" to " +
                "\"root\". Shows your Phone Load out of 3 and two ways to get a shell.",
            steps = listOf(
                "Profile ▸ Door Helper ▸ Setup, or Tools ▸ Power Up.",
                "Quick Start ▸ Shizuku Door: install Shizuku, start it, then tap Activate.",
                "Quick Start ▸ Net Mode ▸ Cordless Debug: pair over wireless debugging — no second app.",
                "Pair by Hand ▸ enter the six-digit code yourself if auto-detect is unavailable.",
                "Shizuku Log shows the live shell conversation when something refuses to connect."
            ),
            note = "You do not need root to use GamersHub. Root only widens what the shell may touch.",
            sheet = "activation",
        ),
        HelpTopic(
            id = "wireless",
            section = "Start Here",
            title = "Wireless debugging",
            aliases = "cordless debug pair code port mdns adb wifi no shizuku nearby devices",
            summary = "The Shizuku-free door. Pairs directly with Android's own adb daemon over Wi-Fi.",
            steps = listOf(
                "Settings ▸ Developer options ▸ Wireless debugging ▸ Pair device with pairing code.",
                "Leave that dialog open — it shows a six-digit code and a port.",
                "In GamersHub: Power Up ▸ Net Mode ▸ Cordless Debug, type the code, tap Pair.",
                "Tapping Connect reuses the port from your last session first, so a working setup reconnects without discovery.",
                "Auto-detect uses mDNS, then falls back to adbd's fixed port — it no longer reports \"not on\" just because mDNS is quiet.",
                "On Android 13+ allow Nearby devices when asked — without it discovery cannot answer.",
                "If the port changed (wireless debugging toggled, or the phone rebooted), tap Connect again: the app drops the dead port and rediscovers."
            ),
            note = "A failed pair is almost always a closed dialog or a stale code. A failed *connect* is usually a changed port — reopen Wireless debugging to read the current port. Either way the sheet now names which one it was; you should not have to reinstall.",
            sheet = "wireless",
        ),
        HelpTopic(
            id = "app-picker",
            section = "Start Here",
            title = "App Picker",
            aliases = "add games library select apps my picks search apps installed",
            summary = "Chooses which apps appear in the Lobby and in Game Space.",
            steps = listOf(
                "Lobby ▸ + Add games (or tap the empty state).",
                "Type in Search apps to filter, then toggle the apps you want.",
                "Tap close — the count on the sheet and the Lobby update immediately.",
                "Game titles are tagged automatically; every app can still be added by hand."
            )
        ),

        /* ---------------------------------------------------------------- lobby */

        HelpTopic(
            id = "lobby",
            section = "Lobby",
            title = "Base (Lobby)",
            aliases = "lobby home base first tab library hero play room emblem latest session",
            summary = "Your home screen: engine switch, hero card, latest session, and the game " +
                "library with play time per title.",
            steps = listOf(
                "Header shows the engine state and the channel it is using (root / Shizuku / app only).",
                "Hero card shows CPU pace and the last session.",
                "Each library row shows play minutes and opens the launch sheet.",
                "The octagon bar at the bottom switches tabs: Lobby, Game-Labs, Tools, Root, Profile."
            ),
            tab = "LOBBY"
        ),
        HelpTopic(
            id = "game-space",
            section = "Lobby",
            title = "Game Space",
            aliases = "game space tool shelf slide to enter in game hub overlay console launch bar app strip my picks",
            summary = "The in-game hub: a console-styled screen that opens before a game and keeps " +
                "your tools one tap away.",
            steps = listOf(
                "Tap the emblem on the Lobby hero card, or PLAY ROOM, or Tools ▸ Tool Shelf.",
                "Slide the bar to enter (Slide to enter) — the slide is deliberate, it stops pocket taps.",
                "Use the app strip to switch games, and the grid tiles to start/stop draw, use a look now, or put it back.",
                "PICK APPS opens the app picker without leaving Game Space."
            ),
            note = "Every launch from the Lobby goes through Game Space on purpose — launches that skipped it also skipped the panel and the boosters.",
            sheet = "logolaunch",
        ),

        /* ---------------------------------------------------------------- game labs */

        HelpTopic(
            id = "labs",
            section = "Game Labs",
            title = "Lab Bench (Game-Labs)",
            aliases = "labs game labs tab experiment all visual configs tabs",
            summary = "The experiment bench: taps, screen, and feature switches in one scroll, split " +
                "into All / Visual / Configs.",
            steps = listOf(
                "All shows every card.",
                "Visual keeps the tap and display cards.",
                "Configs keeps the feature cards."
            ),
            tab = "LABS"
        ),
        HelpTopic(
            id = "stop-all",
            section = "Game Labs",
            title = "Shut It All Down",
            aliases = "stop all features end everything off panic kill services",
            summary = "The panic button. Appears only while something is actually running, and turns " +
                "off every service and stored switch at once.",
            steps = listOf(
                "Open Game-Labs — the card is at the top while anything is active.",
                "Read the count (services running + features active).",
                "Tap End. Overlays stop, capture stops, switches clear."
            ),
            note = "Settings already changed on the phone are undone by the engine's OFF switch, not by this card."
        ),
        HelpTopic(
            id = "magic-mapper",
            section = "Game Labs",
            title = "Magic Pad Mapper",
            aliases = "magic pad mapper mapping overlay key mapper keys shell identity touch",
            summary = "Draws on-screen key pads that are delivered with the shell identity, so games " +
                "receive them as real input.",
            steps = listOf(
                "Game-Labs ▸ Tap Likes ▸ flip Magic Pad Mapper on (or Tools ▸ Key Mapper).",
                "Open Tools ▸ Key Mapper to add keys: name the key, place it, choose what it sends.",
                "Keys are injected with shell identity — no root needed, a shell channel is enough.",
                "Flip it off, or use Shut It All Down, to remove the overlay."
            ),
            note = "The switch reads the running overlay, so it can never claim ON while the pads are gone.",
            sheet = "mapping",
        ),
        HelpTopic(
            id = "show-taps",
            section = "Game Labs",
            title = "Show Taps / Show Pointers",
            aliases = "show taps pointer location touch indicator coordinates white dot",
            summary = "Android's own developer switches: a dot where you touch and crosshair pointer " +
                "coordinates. Useful when calibrating a mapper.",
            steps = listOf(
                "Game-Labs ▸ Tap Likes ▸ Show Taps (or Show Pointers).",
                "Both write a system setting, so they need Modify system settings.",
                "The buttons read the real setting, so they show what the phone is actually doing."
            ),
            note = "These stay on until you turn them off — that is Android behaviour, not an app bug."
        ),
        HelpTopic(
            id = "tap-speed",
            section = "Game Labs",
            title = "Tap Speed",
            aliases = "tap speed touch rate probe scan panel report hz",
            summary = "Reads the touch panel and reports its sample rate — the number that decides " +
                "how snappy aiming feels.",
            steps = listOf(
                "Game-Labs ▸ Tap Likes ▸ read the Tap Speed line.",
                "It reads once when the tab opens; reopen the tab to re-measure."
            ),
            note = "The value comes from the input device, so a cheap panel reporting 60 Hz is telling the truth, not failing."
        ),
        HelpTopic(
            id = "setup-saver",
            section = "Game Labs",
            title = "Setup Saver",
            aliases = "setup saver export handle presets saved looks screen looks keep load",
            summary = "Keeps a named copy of your screen look (size, density, refresh) so you can put " +
                "it back after a game changes it.",
            steps = listOf(
                "Game-Labs ▸ Screen Likes ▸ Export to save the current look.",
                "Handle opens Screen Setups with the saved looks and Clear All.",
                "Tap Tune next to Screen Tweak to change size, DPI and refresh directly."
            )
        ),
        HelpTopic(
            id = "screen-tweak",
            section = "Game Labs",
            title = "Screen Tweak",
            aliases = "screen tweak display tuning dpi resolution hz refresh crisp navigation bar gestural",
            summary = "Screen size, density, refresh rate and navigation mode in one sheet, with a " +
                "restore path for each.",
            steps = listOf(
                "Game-Labs ▸ Screen Likes ▸ Tune, or Tools ▸ Screen Tweak.",
                "Pick a preset or set DPI / resolution / refresh by hand.",
                "Navigation mode: 3-Button, 2-Button or Gestural.",
                "Reset puts the stock values back."
            ),
            note = "A wrong density can make the launcher unusable. If that happens, reboot — Android falls back to the real display size.",
            sheet = "display",
        ),
        HelpTopic(
            id = "ready-to-play",
            section = "Game Labs",
            title = "Ready to Play",
            aliases = "ready to play phone health steady score clean before game boost prepare",
            summary = "A short pre-game pass: measures phone health, then clears hidden apps so the " +
                "game gets the memory.",
            steps = listOf(
                "Game-Labs ▸ Feature Likes ▸ Ready to Play.",
                "Read the health lines, then tap Done — background processes are cleared.",
                "Open your game from Game Space."
            )
        ),
        HelpTopic(
            id = "dns-tuner",
            section = "Game Labs",
            title = "DNS Tuner (Net Delay Fixer)",
            aliases = "dns tuner net delay fixer private dns auto system dns hostname network resolve",
            summary = "Switches Android's Private DNS between System, Auto and a private hostname, " +
                "which often fixes slow matchmaking.",
            steps = listOf(
                "Game-Labs ▸ Feature Likes ▸ DNS Tuner.",
                "Pick System DNS for stock behaviour, Auto for opportunistic, or type a hostname.",
                "The switch reports the mode currently set on the phone."
            ),
            note = "A wrong hostname breaks name resolution for every app until you set it back — the engine restores it on OFF."
        ),
        HelpTopic(
            id = "session-monitor",
            section = "Game Labs",
            title = "Session Monitor (Run Watcher)",
            aliases = "session monitor run watcher strip floating stats cpu ram temp fps clock chip",
            summary = "The draggable strip with CPU, RAM, battery, temperature, FPS and the clock.",
            steps = listOf(
                "Game-Labs ▸ Feature Likes ▸ Session Monitor — turn it on.",
                "Drag it anywhere; it sits above the tabs.",
                "Turn it off to remove the strip."
            ),
            note = "It needs Display over other apps only if you want it over a game; inside GamersHub it needs nothing."
        ),

        /* ---------------------------------------------------------------- tools */

        HelpTopic(
            id = "tools",
            section = "Tools",
            title = "Tools tab",
            aliases = "tools tab every feature list sections master engine overlays studio network performance capture",
            summary = "The complete feature surface, live: every row either toggles a running service " +
                "or opens its configuration sheet.",
            steps = listOf(
                "Sections: MASTER ENGINE, OVERLAYS, STUDIO, NETWORK, PERFORMANCE, MEMORY, CAPTURE & SPACE.",
                "A row with ON/OFF flips a real service; the state is read back from the service itself.",
                "A row with SET / OPEN / STATUS opens its sheet, runs its action, or shows state."
            ),
            tab = "TOOLS",
            note = "If Display-over-apps is off, a red banner appears at the top with a one-tap fix — every overlay needs it."
        ),
        HelpTopic(
            id = "panel",
            section = "Tools",
            title = "Floating Bubble",
            aliases = "floating bubble floating panel bubble panel quick panel hub overlay hub",
            summary = "A small bubble plus a quick panel that floats over the game with the tools you " +
                "reach for mid-match.",
            steps = listOf(
                "Tools ▸ OVERLAYS ▸ Floating Bubble ▸ ON.",
                "Tap the bubble to open the panel; drag it where it does not cover the HUD.",
                "Tools ▸ Overlay Hub has the same switches plus what the panel shows."
            ),
            note = "Needs Display over other apps. A notification permission denial can also stop it — check the banner in Tools."
        ),

        /* ---------------------------------------------------------------- overlays */

        HelpTopic(
            id = "info-watcher",
            section = "Overlays",
            title = "Info Watcher",
            aliases = "info watcher monitor overlay fps cpu gpu ram temp ping hud stats live",
            summary = "The floating stats HUD: FPS, CPU, GPU, RAM, temperature and ping, each toggleable.",
            steps = listOf(
                "Tools ▸ OVERLAYS ▸ Info Watcher ▸ ON.",
                "Tap SET to choose which values float and how they look.",
                "Turn off from the same row, or with Shut It All Down."
            ),
            sheet = "monitor",
        ),
        HelpTopic(
            id = "aim-mark",
            section = "Overlays",
            title = "Aim Mark",
            aliases = "aim mark crosshair reticle dot seven styles opacity rotation fade",
            summary = "A crosshair overlay with seven styles, size, colour, opacity, fade and rotation.",
            steps = listOf(
                "Tools ▸ OVERLAYS ▸ Aim Mark ▸ ON.",
                "Tap SET to pick a style and tune size, opacity and rotation.",
                "The mark is an overlay window, so it stays above the game."
            ),
            note = "Some games treat any overlay as an anti-cheat input; turn it off if a match refuses to start.",
            sheet = "crosshair",
        ),
        HelpTopic(
            id = "key-mapper",
            section = "Overlays",
            title = "Key Mapper",
            aliases = "key mapper mapping editor keys add key name what it sends remove keyboard",
            summary = "Edits the key pads drawn by Magic Pad Mapper: what each key is called and what " +
                "it sends.",
            steps = listOf(
                "Tools ▸ Key Mapper (or the SET button on the Key Mapper row).",
                "Type a Key name, then choose what it sends.",
                "The list shows how many keys are mapped.",
                "Keys only appear on screen while the mapping overlay is on."
            ),
            sheet = "mapping",
        ),
        HelpTopic(
            id = "mouse-pointer",
            section = "Overlays",
            title = "Mouse Pointer",
            aliases = "mouse pointer cursor plugged in finger pad usb mouse",
            summary = "A visible cursor and a finger pad for phones driving a mouse — plugged-in " +
                "hardware is detected and reported.",
            steps = listOf(
                "Tools ▸ OVERLAYS ▸ Mouse Pointer ▸ ON.",
                "The subtitle says Mouse plugged in when a mouse is present, Finger pad otherwise.",
                "SET adjusts speed and look."
            ),
            sheet = "mouse",
        ),

        /* ---------------------------------------------------------------- studio */

        HelpTopic(
            id = "screen-grab",
            section = "Studio",
            title = "Screen Grab (screenshot)",
            aliases = "screen grab screenshot capture picture png pictures gamershub",
            summary = "One-tap screenshot written to Pictures/GamersHub.",
            steps = listOf(
                "Tools ▸ CAPTURE & SPACE ▸ Screen Grab ▸ CAPTURE.",
                "Grant Shizuku when asked — a shell screenshot needs it.",
                "The sheet lists what was written, and shows where."
            ),
            note = "If it says Grant Shizuku to capture, the shell channel is not up yet. Power Up ▸ Activate fixes that.",
            sheet = "screenshot",
        ),
        HelpTopic(
            id = "record",
            section = "Studio",
            title = "Screen Grab (recording)",
            aliases = "record recorder screen grab mp4 video mic clips movies record gameplay",
            summary = "Records the screen to MP4 in Movies/GamersHub with height, frame rate and " +
                "bitrate you choose. Microphone is off by default.",
            steps = listOf(
                "Tools ▸ STUDIO ▸ Screen Grab ▸ OPEN.",
                "Choose height, FPS and bitrate; flip MIC on only if you want audio.",
                "Tap Start and accept Android's screen-capture prompt.",
                "Stop from the sheet or the notification. The clip appears in CLIPS."
            ),
            note = "Clips live in Movies/GamersHub. Each row has play, share and delete.",
            sheet = "record",
        ),
        HelpTopic(
            id = "go-on-air",
            section = "Studio",
            title = "Go On Air",
            aliases = "go on air live stream rtmp url key h264 broadcast stream",
            summary = "Streams the screen to any RTMP endpoint — a real encoder, not a mirror.",
            steps = listOf(
                "Tools ▸ STUDIO ▸ Go On Air ▸ OPEN.",
                "Paste the RTMP URL from the site you stream to (it includes the stream key).",
                "Pick height, FPS and bitrate, then Start and accept screen capture.",
                "Stop from the sheet or the notification."
            ),
            note = "The status row says On air now while the encoder is running, so you can always tell.",
            sheet = "live",
        ),
        HelpTopic(
            id = "view-boost",
            section = "Studio",
            title = "View Boost",
            aliases = "view boost graphics texture shadow effects antialiasing resolution sharpness frame limit draw scale vulkan surface shade",
            summary = "Draw scale, texture/shadow/effect quality, anti-aliasing, resolution scale, " +
                "sharpness and a frame limit — applied through the phone's game manager.",
            steps = listOf(
                "Tools ▸ STUDIO ▸ View Boost ▸ OPEN.",
                "Pick a quality preset, then tune the individual keys.",
                "Draw scale / Vulkan / surface and shade keys are on the same sheet.",
                "Reset restores the stock values."
            ),
            note = "Only affects phones that expose a game manager or Game Mode. Without one the sheet says so instead of pretending.",
            sheet = "graphics",
        ),
        HelpTopic(
            id = "phone-swap",
            section = "Studio",
            title = "Phone Swap",
            aliases = "phone swap spoof device name model preset display identity fake phone",
            summary = "Changes how the phone introduces itself: display size presets, device name " +
                "and the tags apps read.",
            steps = listOf(
                "Tools ▸ STUDIO ▸ Phone Swap ▸ OPEN.",
                "Pick a preset (the list comes from the device profile table).",
                "Apply, then reset when you are done.",
                "Device name can be edited and restored separately."
            ),
            note = "The app repairs stale names on every launch, because an old build left phones renamed permanently. Applying a preset is temporary and reversible.",
            sheet = "spoof",
        ),
        HelpTopic(
            id = "overlay-hub",
            section = "Studio",
            title = "Overlay Hub",
            aliases = "overlay hub bubble panel watcher inside the panel floating windows",
            summary = "The list of floating surfaces: the bubble, the panel, and the watcher inside " +
                "the panel.",
            steps = listOf(
                "Tools ▸ set on Floating Bubble ▸ SET, or Tools row ▸ Overlay Hub.",
                "Switch the panel and the watcher independently.",
                "HALT ALL WINDOWS on the Tools screen stops every overlay at once."
            ),
            sheet = "panel",
        ),

        /* ---------------------------------------------------------------- network & performance */

        HelpTopic(
            id = "delay-fixer",
            section = "Network & Performance",
            title = "Delay Fixer",
            aliases = "delay fixer ping optimizer lag latency jitter loss test servers dns",
            summary = "Measures real TCP latency, jitter and packet loss to game endpoints, then shows " +
                "the grade the way a match feels.",
            steps = listOf(
                "Tools ▸ NETWORK ▸ Delay Fixer.",
                "Tap RUN AGAIN to re-measure.",
                "Each target shows latency, jitter and loss with a grade and bars.",
                "Use the DNS section to switch Private DNS if resolution is the slow part."
            ),
            note = "It measures with TCP probes, which is what games actually use — not ICMP, which Android often blocks.",
            sheet = "ping",
        ),
        HelpTopic(
            id = "free-memory",
            section = "Network & Performance",
            title = "Free Memory",
            aliases = "free memory clean held apps trim caches mb freed",
            summary = "Closes background apps and trims caches, then reports exactly how much it freed.",
            steps = listOf(
                "Tools ▸ PERFORMANCE ▸ Free Memory.",
                "Tap CLEAN; the result line under MEMORY shows the freed megabytes."
            ),
            note = "Android may refill memory right away — that is normal, the freed figure is what was actually reclaimed."
        ),
        HelpTopic(
            id = "quick-ram",
            section = "Network & Performance",
            title = "Quick Ram",
            aliases = "quick ram boost wipe drop cache quick free",
            summary = "A faster, lighter pass than Free Memory: wipe and drop caches.",
            steps = listOf(
                "Tools ▸ PERFORMANCE ▸ Quick Ram ▸ BOOST.",
                "The freed amount is reported in the toast and the MEMORY header."
            )
        ),
        HelpTopic(
            id = "deep-wipe",
            section = "Network & Performance",
            title = "Deep wipe",
            aliases = "deep wipe kill hidden apps trim drop cache apps stopped",
            summary = "The heaviest clean: kills hidden apps, trims and drops caches. Reports apps " +
                "stopped as well as memory freed.",
            steps = listOf(
                "Tools ▸ PERFORMANCE ▸ Deep wipe ▸ DEEP.",
                "Read the count: MB freed and apps stopped."
            ),
            note = "Messaging apps may need a moment to reconnect after a deep wipe. Expected, not a bug."
        ),
        HelpTopic(
            id = "tilt-tune",
            section = "Network & Performance",
            title = "Tilt Tune",
            aliases = "tilt tune gyro calibration flip y live read sensor aim",
            summary = "Calibrates the gyroscope and shows a live read, so aim drift can be seen and " +
                "corrected instead of guessed at.",
            steps = listOf(
                "Tools ▸ PERFORMANCE ▸ Tilt Tune.",
                "Hold the phone the way you play, then run the calibration.",
                "Flip Y reverses the axis if the game reads it upside down.",
                "The live read shows whether the sensor settles."
            ),
            sheet = "gyro",
        ),

        /* ---------------------------------------------------------------- root */

        HelpTopic(
            id = "root-tab",
            section = "Root",
            title = "Root tab",
            aliases = "root tab sections reboot privilege shell wireless dfroot session superuser module store flash installed modules system depth",
            summary = "Everything privileged in one screen: root state, modules, superuser policy " +
                "and diagnostics.",
            steps = listOf(
                "Open the ROOT tab; the top card says whether root is active.",
                "Use the section list: REBOOT, PRIVILEGE, SHELL, TEMPORARY ROOT, SESSION ROOT, SUPERUSER, MODULE STORE, FLASH, INSTALLED MODULES, SYSTEM DEPTH.",
                "Values refresh every few seconds, so a change made in Magisk appears without a restart."
            ),
            tab = "ROOT"
        ),
        HelpTopic(
            id = "privilege-ladder",
            section = "Root",
            title = "Privilege ladder",
            aliases = "privilege ladder rung climb shizuku provider su shell root attempt steps descend",
            summary = "Climbs from app to shell to root one rung at a time and shows exactly which step " +
                "answered.",
            steps = listOf(
                "Root ▸ PRIVILEGE ▸ climb (or Tools ▸ Root ▸ toggle).",
                "Each step is listed with a tick or a cross and its own note.",
                "Descend drops back down a rung without rebooting."
            ),
            note = "The ladder is the first place to look when root \"does not work\": it names the rung that refused."
        ),
        HelpTopic(
            id = "dfroot",
            section = "Root",
            title = "Temporary Root · DFRoot",
            aliases = "dfroot temporary root temp root kernel module lkm exploit session hold release",
            summary = "A session-only root that does not install anything permanently. Requires an " +
                "arm64 device with a matching kernel module.",
            steps = listOf(
                "Root ▸ TEMPORARY ROOT · DFROOT ▸ scan.",
                "The section lists the installed managers it found and the kernel it expects.",
                "Pick the SU manager the module should attach to — KernelSU, KernelSU-Next, KowSU or Magisk.",
                "Hold the session to use root; release it when you are done.",
                "Rebooting always clears it."
            ),
            note = "The scan now refuses to run when the payload is staged for a different app id, which is the failure that used to report success and then produce no root. \"Disable KSU modules\" is on by default: an incompatible module is the usual reason the module loads and root still does not appear."
        ),
        HelpTopic(
            id = "session-root",
            section = "Root",
            title = "Session Root",
            aliases = "session root hold keep root while app runs release",
            summary = "Keeps the root session alive for this run of the app and gives it back on exit.",
            steps = listOf(
                "Root ▸ SESSION ROOT ▸ hold.",
                "The card shows the su path in use.",
                "Release (or turning the engine off) ends it."
            )
        ),
        HelpTopic(
            id = "superuser",
            section = "Root",
            title = "Superuser",
            aliases = "superuser su manager grants magisk kernelsu allow deny forever forget policy",
            summary = "Reads and edits the root grants your manager (Magisk / KernelSU / APatch) " +
                "already holds, so a stray grant can be revoked here.",
            steps = listOf(
                "Root ▸ SUPERUSER — the header names the manager and backend.",
                "Each row is an app with its current decision.",
                "FORGET removes the stored decision.",
                "Deny-all and restore are available as snapshots where the manager supports it."
            ),
            note = "A manager that does not expose its policy database shows a read-only view instead of inventing rows."
        ),
        HelpTopic(
            id = "module-store",
            section = "Root",
            title = "Module Store",
            aliases = "module store download modules catalog stars gaming search modules install zip",
            summary = "Browses, downloads and installs root modules from the catalogs, filtered for " +
                "gaming where possible.",
            steps = listOf(
                "Root ▸ MODULE STORE ▸ Download modules directly.",
                "Type in search modules to filter, or browse the catalog list.",
                "Tap a module to install; the row shows download progress and stars.",
                "Installed modules appear under INSTALLED MODULES."
            ),
            note = "Installing a module is journaled, so the engine's OFF switch knows it happened."
        ),
        HelpTopic(
            id = "flash",
            section = "Root",
            title = "Flash from Storage",
            aliases = "flash zip module install storage file picker magisk",
            summary = "Installs a module from a zip on the phone.",
            steps = listOf(
                "Root ▸ FLASH FROM STORAGE ▸ pick the .zip.",
                "The file is copied into the app's cache first, so the manager can read it as root.",
                "The result line says installed or names the reason it failed."
            ),
            note = "You need root for this. A zip that expects a different manager will fail cleanly, not half-install."
        ),
        HelpTopic(
            id = "installed-modules",
            section = "Root",
            title = "Installed modules",
            aliases = "installed modules enabled disabled pending remove update zygisk",
            summary = "Lists modules found in /data/adb/modules with their version, author and state.",
            steps = listOf(
                "Root ▸ INSTALLED MODULES (n).",
                "A module card shows enabled / pending remove / pending update.",
                "A pending remove takes effect at the next boot — that is how the manager works."
            )
        ),
        HelpTopic(
            id = "zygisk",
            section = "Root",
            title = "Zygisk",
            aliases = "zygisk magisk denylist modules injection root hide",
            summary = "Zygisk is the module hook inside Magisk / KernelSU. GamersHub only reads " +
                "whether it is on, so a game that refuses rooted phones can be diagnosed.",
            steps = listOf(
                "Root ▸ the top card says Zygisk is off when it is not enabled.",
                "Enable it in your root manager (Magisk ▸ Settings ▸ Zygisk), then reboot.",
                "Reopen the Root tab — the value refreshes on its own."
            ),
            note = "GamersHub does not toggle Zygisk. Changing it needs a reboot and is the manager's job."
        ),

        HelpTopic(
            id = "system-depth",
            section = "Root",
            title = "System depth & diagnostics",
            aliases = "system depth diagnostics zygisk sepolicy selinux props getprop live values",
            summary = "The deep read-out: SELinux state, properties, Zygisk state and the live shell " +
                "diagnostics.",
            steps = listOf(
                "Root ▸ SYSTEM DEPTH ▸ enable root to see live values.",
                "DIAGNOSTICS prints every probe the ladder made, verbatim."
            )
        ),
        HelpTopic(
            id = "reboot",
            section = "Root",
            title = "Reboot modes",
            aliases = "reboot restart normal recovery bootloader soft restart userspace emulated",
            summary = "Every reboot target in one picker. Each one is one-way, so it goes through a " +
                "confirmation first.",
            steps = listOf(
                "Root ▸ REBOOT ▸ choose a mode.",
                "Confirm in the picker — it names what will happen.",
                "Userspace (soft) restart is shown as EMULATED on phones that fake it."
            ),
            note = "There is no undo for a reboot. The confirmation exists because a mis-tap in this list costs you the session."
        ),

        /* ---------------------------------------------------------------- look & feel */

        HelpTopic(
            id = "settings",
            section = "Look & Feel",
            title = "Likes (settings)",
            aliases = "settings likes wording colour look colour color theme icon look health look custom hue any shade",
            summary = "Everything cosmetic: wording, colour, icon and the health card with the log.",
            steps = listOf(
                "Profile ▸ gear icon, or Lobby ▸ header ▸ Settings.",
                "Wording — opens your phone's language settings.",
                "Colour Look — six tones (Light red, Rose red, True red, Signal red, Deep red, Ember) or Any shade via the hue slider.",
                "Icon Look — Auto, Dark, Light or Mono launcher icon.",
                "Health Look — the last caught error, with Grab log and Wipe."
            ),
            sheet = "settings",
        ),
        HelpTopic(
            id = "saved-looks",
            section = "Look & Feel",
            title = "Saved Looks",
            aliases = "saved looks presets screen setups clear all restore look",
            summary = "Your exported screen setups, as a list you can clear.",
            steps = listOf(
                "Tools ▸ CAPTURE & SPACE ▸ Saved Looks.",
                "Each row is a saved display description (size, density, refresh).",
                "Clear All empties the list — it does not change the screen."
            ),
            sheet = "presets",
        ),
        HelpTopic(
            id = "intro",
            section = "Look & Feel",
            title = "Intro clip",
            aliases = "intro animation video skip tap splash launch media sound",
            summary = "The launch clip, decoded into a texture so the picture can never end up behind " +
                "the UI. Tap to skip; it falls back to the pad emblem if the clip cannot be decoded.",
            steps = listOf(
                "Tap anywhere to skip.",
                "It plays once per launch, ending on the Lobby."
            )
        ),

        /* ---------------------------------------------------------------- safety */

        HelpTopic(
            id = "engine-off",
            section = "Safety",
            title = "Turn engine off",
            aliases = "turn engine off restore report undo change log restored skipped could not restore snapshot",
            summary = "The confirmation screen for the one destructive switch. It lists what will be " +
                "undone before anything is undone.",
            steps = listOf(
                "Flip the engine off in the Lobby header, or Tools ▸ MASTER ENGINE ▸ OFF.",
                "Read the list: services, overlays, journaled changes, display overrides.",
                "Confirm — the report then shows RESTORED / SKIPPED / COULD NOT RESTORE per item."
            ),
            note = "SKIPPED means the item had no stock value recorded. COULD NOT RESTORE means the phone refused the write — send that report, it is a real bug.",
            sheet = "engineoff",
        ),
        HelpTopic(
            id = "change-log",
            section = "Safety",
            title = "Change journal",
            aliases = "change log journal undo history what changed record before after",
            summary = "Every privileged change is recorded with its before and after value, which is " +
                "what makes the OFF switch's promise true.",
            steps = listOf(
                "The journal fills itself — there is nothing to switch on.",
                "Engine OFF replays it backwards.",
                "The undo report in the engine-off sheet names each entry it replayed."
            )
        ),
        HelpTopic(
            id = "data-safety",
            section = "Safety",
            title = "Your data",
            aliases = "privacy data cloud account saved on phone offline no account telemetry",
            summary = "Everything stays on the phone. There is no account, no cloud and no upload " +
                "unless you press send on an issue report.",
            steps = listOf(
                "Settings, presets, play time and the change journal live in app storage.",
                "Clearing app data removes all of it.",
                "An issue report is only sent when you tap Send and choose an app."
            )
        ),

        /* ---------------------------------------------------------------- help & reporting */

        HelpTopic(
            id = "search",
            section = "Help & Reporting",
            title = "Search bar",
            aliases = "search bar find anything lookup magnifier global search",
            summary = "Finds any feature, setting or help page in the app by name, by nickname " +
                "(\"lag\", \"fps\", \"pair\"), or by what you are trying to do.",
            steps = listOf(
                "Lobby ▸ the search field under the header, or Tools ▸ Search anything.",
                "Type a few letters; results are ranked, features first.",
                "Tap a result to read the page, or tap OPEN to jump straight to that feature."
            ),
            sheet = "search"
        ),
        HelpTopic(
            id = "manual",
            section = "Help & Reporting",
            title = "User manual",
            aliases = "manual guide help documentation read how to instructions handbook",
            summary = "This document: every screen, every switch, what it does and what it needs.",
            steps = listOf(
                "Tools ▸ Search anything → User manual, or Profile ▸ Manual.",
                "Browse by section or search inside the manual.",
                "Each page ends with Open it for me when the feature has a screen."
            ),
            sheet = "manual"
        ),
        HelpTopic(
            id = "reporter",
            section = "Help & Reporting",
            title = "Report a problem",
            aliases = "report problem issue bug crash send developer log error feedback complain",
            summary = "Builds a complete report — what happened, the app version, your phone, the last " +
                "error log and the steps you took — and lets you send it in one tap.",
            steps = listOf(
                "The reporter opens by itself when something breaks.",
                "Or open it yourself: Tools ▸ Report a problem, Profile ▸ Report a problem, or Settings ▸ Health Look.",
                "Describe what you were doing (optional but it helps most).",
                "Tap Send — pick any app. Copy saves it to the clipboard, Save file attaches it, GitHub opens an issue."
            ),
            sheet = "report",
            note = "Nothing is sent automatically. The report sits on your phone until you choose where it goes."
        ),
        HelpTopic(
            id = "error-log",
            section = "Help & Reporting",
            title = "Reading the error log",
            aliases = "error log crash log stack trace steps breadcrumbs crash.log what happened",
            summary = "The app writes every caught failure to a log with the exact screen and step, " +
                "so a report does not need guesswork.",
            steps = listOf(
                "Open Report a problem ▸ the log is shown at the bottom, in full.",
                "The steps: line is the breadcrumb trail — the last screen you were on.",
                "The first line after it is the exception and the file that threw it.",
                "Copy takes the whole thing to the clipboard."
            )
        ),
        HelpTopic(
            id = "version",
            section = "Help & Reporting",
            title = "Version & build",
            aliases = "version build number update check latest release apk changelog",
            summary = "The version is shown in Profile (next to the header) and inside every issue report.",
            steps = listOf(
                "Profile ▸ the pill next to Account shows the running version.",
                "Reports include versionName and versionCode automatically.",
                "Releases are published on the project's GitHub releases page."
            ),
            tab = "PROFILE"
        ),

        /* ---------------------------------------------------------------- fixes */

        HelpTopic(
            id = "fix-overlay",
            section = "Fixes",
            title = "An overlay will not appear",
            aliases = "overlay not showing nothing happens display over other apps permission blocked",
            summary = "Nine times out of ten this is Display over other apps, or the engine being off.",
            steps = listOf(
                "Tools ▸ if a red banner says Display-over-apps is off, tap it and allow.",
                "Check the engine is ON (Lobby header).",
                "Check the row reads ON — the state is read from the service, not stored.",
                "Still nothing? Open Report a problem and send the log; the service start failure is recorded."
            )
        ),
        HelpTopic(
            id = "fix-shell",
            section = "Fixes",
            title = "Shizuku / shell will not connect",
            aliases = "shizuku not connecting shell failed permission denied binder dead restart service",
            summary = "Shizuku must be running and GamersHub must be allowed inside it.",
            steps = listOf(
                "Open Shizuku and confirm the service is started (it stops after every reboot).",
                "In Shizuku ▸ Authorized apps, allow GamersHub.",
                "Back in GamersHub, Power Up Spot ▸ Shizuku Door ▸ Activate.",
                "If it still refuses, try Net Mode ▸ Cordless Debug instead — it needs no app."
            ),
            note = "Wireless debugging turns itself off after a reboot on most phones. Re-pair, do not reinstall."
        ),
        HelpTopic(
            id = "fix-pair",
            section = "Fixes",
            title = "Wireless pairing fails",
            aliases = "pairing failed wrong code port unknown nearby permission mdns timeout",
            summary = "The pairing dialog is time-limited and the port changes every time it opens.",
            steps = listOf(
                "Close and reopen Pair device with pairing code — a new code and port appear.",
                "Type the new code within the window.",
                "On Android 13+, allow Nearby devices so auto-detect can answer.",
                "If discovery is silent, type the port by hand and tap Pair again."
            )
        ),
        HelpTopic(
            id = "fix-wireless-connect",
            section = "Fixes",
            title = "Wireless debugging says it is off, but it is on",
            aliases = "wireless off not on connect fails port changed mdns silent reboot stale port already paired",
            summary = "Pairing and connecting are two different things. A phone can stay paired and still " +
                "refuse a connect, because the shell port changes and the old one is dead.",
            steps = listOf(
                "Open Wireless debugging in Settings and read the port under IP address & port.",
                "In GamersHub: Cordless Debug ▸ Already paired ▸ type that port ▸ Connect.",
                "If you leave the field empty, the app tries your last working port, then mDNS, then adbd's fixed port.",
                "After a reboot or after toggling wireless debugging, always tap Connect again — the pair itself survives, the port does not."
            ),
            note = "You do not need to re-pair or reinstall for this. The sheet now separates \"not paired\" from \"port changed\" in the error text."
        ),
        HelpTopic(
            id = "fix-capture",
            section = "Fixes",
            title = "Recording or streaming will not start",
            aliases = "record fails capture permission missing screen capture not working stream error",
            summary = "Android needs the screen-capture prompt accepted for every new session, and the " +
                "encoder needs a supported size.",
            steps = listOf(
                "Start again and accept Android's capture prompt.",
                "Keep the notification: stopping it kills the encoder.",
                "Lower height or FPS if the phone refuses the encoder.",
                "Read Last error in the sheet — it names the actual failure."
            )
        ),
        HelpTopic(
            id = "fix-root",
            section = "Fixes",
            title = "Root is not granted",
            aliases = "root not granted su missing magisk kernelsu no root failed ladder",
            summary = "GamersHub does not root the phone; it uses a root that already exists, plus a " +
                "temporary one it can provide.",
            steps = listOf(
                "Root ▸ PRIVILEGE ▸ climb and read which rung failed.",
                "Grant the su request in your manager if it is waiting there.",
                "No root at all? Use Shizuku or Wireless debugging — most features work without root.",
                "DFRoot (Temporary Root) needs a matching arm64 kernel module; the scan reports whether yours matches."
            )
        ),
        HelpTopic(
            id = "fix-crash",
            section = "Fixes",
            title = "The app closed itself",
            aliases = "closed itself crash restarted reopened stopped here force close bug",
            summary = "A broken step restarts the app instead of leaving a black screen, and the error " +
                "is kept so it can be reported.",
            steps = listOf(
                "The reporter opens on the next launch showing It stopped here.",
                "Read the steps: line to see the last screen.",
                "Tap Send to pass it on — the trace names the exact file and line.",
                "If it repeats, note whether it happens on a specific screen; that is in the report."
            ),
            note = "Three failures in a row hand the process back to Android on purpose — a loop is worse than a restart."
        ),
        HelpTopic(
            id = "fix-lag",
            section = "Fixes",
            title = "The game still lags",
            aliases = "lag high ping slow stutter fps drop delay fixer not helping",
            summary = "Work down the stack: measure, then change one thing at a time.",
            steps = listOf(
                "Delay Fixer ▸ RUN AGAIN — is it latency, jitter or loss?",
                "High jitter with low latency is Wi-Fi; move to 5 GHz or Ethernet.",
                "High latency on every target is your route; try Private DNS in DNS Tuner.",
                "Low latency with frame drops is the phone: Free Memory, then lower View Boost settings."
            )
        ),
        HelpTopic(
            id = "fix-space",
            section = "Fixes",
            title = "Game Space looks wrong",
            aliases = "game space blank empty picks no games add app library missing",
            summary = "Game Space fills from the library. With nothing added it shows a PICK APPS door " +
                "instead of an empty console.",
            steps = listOf(
                "Lobby ▸ + Add games ▸ pick at least one app.",
                "Reopen Game Space from the emblem or PLAY ROOM.",
                "If a game launched outside Game Space, launch it from the Lobby row instead."
            )
        )
    )

    fun topic(id: String): HelpTopic? = topics.firstOrNull { it.id == id }

    /** Sections in display order, each with its topics, skipping empty sections. */
    fun bySection(): List<Pair<String, List<HelpTopic>>> =
        SECTIONS.map { section -> section to topics.filter { it.section == section } }
            .filter { it.second.isNotEmpty() }

    /**
     * Ranked search across titles, aliases, summaries, steps and section names.
     *
     * The ranking is deliberately simple and predictable: an exact title hit wins, a
     * nickname hit is next, and every additional matching word adds a little. A query
     * where one word matches nothing returns nothing, so "fps root" does not return
     * every root page.
     */
    fun search(raw: String, limit: Int = 24): List<HelpTopic> {
        val q = raw.trim().lowercase()
        if (q.length < 2) return emptyList()
        val terms = q.split(Regex("[^a-z0-9]+")).filter { it.length > 1 }
        val scored = ArrayList<Pair<HelpTopic, Int>>()
        topics.forEach { t ->
            val title = t.title.lowercase()
            val alias = t.aliases.lowercase()
            val section = t.section.lowercase()
            val body = (t.summary + " " + t.steps.joinToString(" ") + " " + t.note + " " + section + " " + alias).lowercase()
            var score = 0
            if (title == q) score += 90
            if (title.contains(q)) score += 45
            if (alias.contains(q)) score += 22
            if (body.contains(q)) score += 10
            var matchedAll = true
            for (term in terms) {
                when {
                    title.contains(term) -> score += 16
                    alias.contains(term) -> score += 9
                    section.contains(term) -> score += 5
                    body.contains(term) -> score += 3
                    else -> { matchedAll = false }
                }
            }
            if (!matchedAll) return@forEach
            // Short titles that match are usually the thing the user meant.
            score += (24 - t.title.length).coerceAtLeast(0) / 6
            if (score > 0) scored.add(t to score)
        }
        return scored.sortedByDescending { it.second }.take(limit).map { it.first }
    }
}
