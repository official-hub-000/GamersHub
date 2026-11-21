# GamersHub

**A complete AX-MODE game bar for Android: floating panel, live monitor, crosshair, ping optimizer, gyro calibration, mapping keys, mouse pointer, screenshot and an in-game Game Space — all driven by real system calls through [Shizuku](https://shizuku.rikka.app/). No root required.**

GamersHub is a from-scratch, clean-room Android application (package `com.gamershub`) that reproduces the
full feature surface of the *Red Corner / AXERON* game bar — rebuilt in Kotlin + Jetpack Compose, with every
control wired to a real device API instead of a mock-up.

Every overlay, every reading and every toggle is real: FPS comes from the game's own `gfxinfo` frame counter,
the ping numbers are TCP handshakes to live game endpoints, the mapping keys inject real touches, and the
screenshot is a real `screencap` frame.

---

## 📲 Get the APK

The **[GitHub Releases](../../releases)** page hosts the CI-built, signed `GamersHub-*.apk`.
Download it, install it (allow "install unknown apps"), open it, and let the intro play.

---

## ✨ What is inside

| Feature | What it actually does | Needs |
|---|---|---|
| **Gamepad intro** | Full-screen intro video on every cold start, skippable with one tap | — |
| **Lobby** | Your installed games and apps with real icons, search, "added" library, per-game play time, launch modes | — |
| **Game-Labs** | Display/network/thermal tuning switches, preset export, session monitor | partly Shizuku |
| **Profile** | Live CPU, RAM, storage, clock, ping, environment score, device identity | — |
| **AX-MODE floating panel** | The Red-Corner style pop-up: tab pills, live stats strip, AX-MODE band, 3×3 icon grid around the AXERON wordmark, CPU/RAM chevron bars, draggable bubble | Overlay |
| **Information Monitor** | Floating, draggable HUD: real FPS, CPU, GPU load, RAM, temperature, battery, ping, refresh rate and clock — each field toggleable, scalable | Overlay + Shizuku |
| **Crosshair** | 7 real crosshair styles (the original vectors), size, colour, opacity, rotation, draggable | Overlay |
| **Mapping Editor** | Unlimited macro keys as separate overlay windows; drag them anywhere in edit mode, tap them in play mode and a real `input tap/swipe/keyevent/multi-tap` command is executed in the game | Overlay + Shizuku |
| **Mouse Cursor** | Virtual pointer with a trackpad pad: drag to aim, tap to send a real touch at the pointer, adjustable sensitivity | Overlay + Shizuku |
| **Ping Optimizer** | TCP-handshake latency to 8 real game endpoints, jitter, packet loss, grading; one-tap private-DNS switch (Google/Cloudflare/Quad9) + radio-scanning tweaks with a before/after report | Shizuku |
| **Gyro Calibration** | Live gyroscope readout, 90-sample bias capture, drift quality score, sensitivity, invert-Y, saved to prefs | gyroscope |
| **Screenshot** | Real `screencap` through shell identity into `Pictures/GamersHub`, verified on disk | Shizuku |
| **Quick Boost** | Kills background processes, drops caches, frees RAM, reports the freed megabytes | partly Shizuku |
| **Game Space** | Landscape in-game hub with the game card, live stats, Control Center and per-game texture filtering | Overlay |
| **Shizuku activation** | Real permission flow, real user-service shell (`sh -c` as shell/uid 2000), graceful fallback when absent | Shizuku |
| **Themes** | Six accent tones plus a custom slider colour, animated icons everywhere, spring-driven toggles and gauges | — |

Everything that needs system rights degrades honestly: the UI says *"Needs Shizuku"* instead of pretending.

---

## 🏗 Building from source

1. Android Studio (Ladybug or newer), JDK 17.
2. `File → Open` this folder, let Gradle sync.
3. `./gradlew assembleDebug` → `app/build/outputs/apk/debug/`.

AGP 8.7.3 · Kotlin 2.0.20 · Compose BOM 2024.09.02 · Gradle 8.9 · minSdk 26 · target/compileSdk 35 · Shizuku 13.1.5 · NDK 27.2.12479018.
**Signed release APKs are arm64-v8a only** — the embedded DFRoot payload (`lkm/`, GPL-2.0) supports ARM64 GKI kernels; 32-bit-only devices are no longer supported (all ARMv8 phones from the last decade are fine).
Release builds are produced by CI on every `v*` tag (`release.yml`).

---

## ▶️ First run

Three privilege paths, in order of how much they can do:

1. **Root** — anything with `su` (Magisk / KernelSU / APatch / DFRoot…).
2. **Wireless debugging** *(Root tab → Wireless debugging)* — a shell as uid 2000 over
   the phone's own `adbd`, no other app required. If auto-detect fails on your network,
   type the port shown on the Wireless debugging settings screen and it connects anyway.
   Optionally start the daemon so privileges outlive the ADB session (Shizuku behaviour).
3. **Shizuku** — recommended if you already run it.
4. **DFRoot** *(Root tab → Temporary root)* — embedded in the APK (GPL-2.0, diabl0w/DFRoot).
   Pick your installed SU manager (KernelSU / KernelSU-Next / KowSU), press **Run DFRoot**,
   then **Adopt root**. One boot only; reboot and SELinux enforces again.

Then open **Tools** and switch on what you need, and in-game tap the bubble.

---

## 🧠 Architecture

```
app/src/main/java/com/gamershub/
├── MainActivity.kt                 # Compose host, deep-links a tool sheet from the panel
├── GameSpaceActivity.kt            # landscape in-game hub + system-setting helpers
├── core/
│   ├── shizuku/Shell.kt            # Shizuku user-service shell: exec / ok / appExec
│   ├── shizuku/GamersHubShellService.kt   # runs as shell/root, returns exit code
│   ├── PingEngine.kt               # TCP latency, jitter, loss, private DNS, radio tweaks
│   ├── FpsMeter.kt                 # gfxinfo frame counter, foreground pkg, refresh rate, GPU load
│   ├── GyroEngine.kt               # sensor access, bias calibration, correction
│   ├── ScreenshotEngine.kt         # verified screencap to Pictures/GamersHub
│   ├── MappingStore.kt             # macro-key model, JSON persistence, command compiler
│   └── MonitorPrefs.kt             # monitor fields, scale, colours
├── data/
│   ├── AppRepo.kt                  # installed apps, library, play time, per-game gfx
│   └── DeviceStats.kt              # CPU / RAM / storage / battery / ICMP ping
├── overlay/
│   ├── FloatingPanelService.kt     # AX-MODE bubble + panel
│   ├── MonitorOverlayService.kt    # floating information monitor
│   ├── CrosshairOverlayService.kt  # 7-style crosshair
│   ├── MappingOverlayService.kt    # one overlay window per macro key
│   └── MouseCursorService.kt       # virtual pointer + trackpad
└── ui/
    ├── RootShell.kt                # tabs, sheets, live state, intro
    ├── LobbyScreen.kt / GameLabsScreen.kt / ProfileScreen.kt / ToolsScreen.kt
    ├── components/Parts.kt         # animated icons, toggles, gauges, bars, emblem
    ├── sheets/Sheets.kt            # launch modes, app picker, permissions, settings, activation
    └── sheets/FeatureSheets.kt     # ping, gyro, mapping, monitor, mouse, crosshair, screenshot
```

All UI artwork that ships here was extracted from the original application package
(`frb.axeron.redcorner` 2.5.3) with `apktool`/`jadx`/REA + Ghidra analysis: the AXGC font family,
the AXERON/GAME CORNER marks, the seven crosshair vectors, the info icons and the intro video.

---

## 🛡 Long-term install compatibility — will this still work on Android 100?

Honest answer, in three parts, because one of them is not something any APK can promise.

**What is guaranteed: installation.** GamersHub ships as one signed APK (arm64-v8a only) with
`minSdk 26` (Android 8, 2017) and **no `maxSdkVersion`, no mandatory `<uses-feature>`, and no
exact-version `SDK_INT ==` checks anywhere**. That is the complete list of things that make a
side-loaded APK refuse to install on a *newer* system, and there are none. The installer checks
the floor, never the ceiling. Your signed APK will install on Android 15, 16, 20… and eventually
100 more or less untouched — the app id never changes, and every release is signed with the
same key, so updates keep your data and the Shizuku grant.

**What the platform will do to old builds.** Somewhere along the way, Android will start showing
the *"This app was built for an older version of Android"* dialog. It is a warning, not an error
— tap through and the app still runs. Hard blocks exist, but only at the bottom: Android 14
refuses apps targeting below 23, Android 15 below 24, roughly +1 API level per release. GamersHub
targets **35**, which is decades of headroom above that floor. The one thing that genuinely
cannot be done today is *pre-authorising* compatibility with Android 100's behaviour changes —
when Android ships one that affects this app, it will need a new release with a bumped
`targetSdk`, same as every app you have ever updated.

**What we do about it.** `tools/check_install_future.py` runs in CI *before every build* and
fails it if anyone ever adds a `maxSdkVersion`, a mandatory `<uses-feature>`, an exact
`SDK_INT ==` check, a reference to a hidden platform API (`android.internal.*`, `ActivityThread` —
the classic "works today, crashes on the next Android" pattern), or regresses
`targetSdk`/`minSdk`/`compileSdk` below the floors in the file. Compatibility regressions become
build failures, not bug reports. Current toolchain: AGP 8.7.3 · compileSdk/targetSdk 35 ·
Gradle 8.9 · JDK 17.

**Practice that keeps this true:** one APK, one ABI · release-signed with the
same key every time (updates keep your data and the Shizuku grant) · everything private-API is
behind `runCatching` with a sane default.

---

## ⚖️ Notes

* No ads, no monetisation, no telemetry, no cloud sync — everything stays on the device.
* Settings are never applied before Shizuku consent is granted.
* `com.gamershub` is an independent reimplementation; it is not affiliated with Axeron.
