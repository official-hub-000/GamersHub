# GamersHub v6.18.0 — root support + master engine

## 📲 Download

**https://github.com/official-hub-000/GamersHub/releases/download/v6.18.0/GamersHub-v6.18.0.apk**

Release page: https://github.com/official-hub-000/GamersHub/releases/tag/v6.18.0

| | |
|---|---|
| File | `GamersHub-v6.18.0.apk` — 25,643,745 bytes (24.4 MB) |
| Package | `com.gamershub` |
| Version | **6.18.0** (versionCode **87**) |
| Type | Release build, signed v2, not debuggable |

---

## What was added

### 1. Root support

Root is tried **before** Shizuku, in this order:

1. `su` directly — Magisk / KernelSU / APatch / SuperSU
2. `su` through the Shizuku shell — for setups that only trust the shell uid
3. Bundled temporary provider — trampoline in app-private storage re-execing real `su`

Because `Shell.kt` routes every privileged call through root first, **all 170
existing call sites gained root with no per-feature changes**.

Root gives a **persistent shell session** — one `su` process handling many commands,
so privileged work no longer pays a process start per action.

#### Deep controls unlocked by root

| Area | Capability |
|---|---|
| Modules | Flash Magisk/KernelSU zip (`magisk --install-module`), or stage manually at `/data/adb/modules/<id>`; list & remove |
| Packages | Freeze / unfreeze, suspend / unsuspend, true per-user uninstall |
| Properties | `resetprop` (can override `ro.*`), else `setprop` |
| CPU / GPU | Per-core max frequency pin/release, GPU governor |
| Display | Refresh-rate override, reset size/density |
| Processes | Exact `am force-stop`, list open files |
| Storage | Per-app cache purge, `drop_caches`, `fstrim` |

Every one returns real output or the reason it failed — no fake successes.

### 2. Master engine switch

Your requirement: **turn the engine off and everything stops, root and Shizuku.**

`Engine.stopEverything()` does all of this:

1. Stops **all 9** declared services
2. Stops the draw/upscale pipeline
3. Restores display size/density, GameMode, spoofer props (`GfxSafe.repair()`)
4. `pkill`s GamersHub processes, **releases the root session**, deletes the temp provider
5. Disconnects the **Shizuku** binder and kills the user service

Exposed on the **Tools** screen and at the top of the **AX panel**, plus a
dedicated **STOP EVERYTHING** row. State persists across restarts.

### 3. The guarantee is enforced by CI

`tools/check_engine_coverage.py` fails the release build if any manifest service
is not torn down:

```
manifest services : 9
stopped by Engine : 8
  OK   CrosshairOverlayService
  OK   FloatingPanelService
  OK   GamersHubShellService (deliberate)
  OK   MappingOverlayService
  OK   MonitorOverlayService
  OK   MouseCursorService
  OK   RecorderService
  OK   StreamService
  OK   UpscaleMirrorService
PASS
```

A future service cannot be added without being torn down.

---

## Verified in the shipped APK

```
Verifies · v2 scheme: true
Signer SHA-256: 463c7264…c16e0
package: name='com.gamershub' versionCode='87' versionName='6.18.0'
not debuggable · media audit rc=0
```

Both intros still ship with audio (1080×1920 `aac,2` and 1920×1080 `aac,2`).

## Repository state

- `main` → `abed227`
- `arena/b678a1fe-gamershub` → `abed227` (identical)
- Tag `v6.18.0` → released

---

## One thing to be clear about

The **temporary root system** does **not** bundle a `su` binary. Shipping one would
be a security hole, is licence-problematic, and cannot work unless the kernel already
trusts it. What ships is a trampoline that re-execs the device's real `su`, exists
only while the engine is on, and is deleted when it goes off.

So on a **non-rooted** device, root features report `NOT_ROOT` instead of pretending
to work. Shizuku remains the fallback, exactly as before.
