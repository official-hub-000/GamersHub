# GamersHub v6.24.0 — one way into Game Space

## 📲 Download

**https://github.com/official-hub-000/GamersHub/releases/download/v6.24.0/GamersHub-v6.24.0.apk**

| | |
|---|---|
| File | `GamersHub-v6.24.0.apk` — 25,488,717 bytes (24.3 MB) |
| Package | `com.gamershub` |
| Version | **6.24.0** (versionCode **93**) |
| Signing | APK Sig v2, release keystore |

---

## 1. The mode popup is gone

Tapping a game opened a sheet asking **"Tool Shelf" or "Straight In"** — a choice
you re-made every launch, and the wrong answer silently skipped the panel and
boosters.

Removed. Every launch goes through Game Space now. The sheet keeps the game banner
and a **slider**. Same intent required, none of the thinking.

## 2. The logo is the door

Tapping the emblem in the lobby hero card opens a sheet that:

1. shows your games as a horizontal picker, **most-played already selected**
2. shows play time for the selected one
3. requires a **slide** to enter

Game Space arms overlays and starts privileged work — a single stray tap on the
logo shouldn't do that. The slide is the right friction, and it's the same gesture
as the game launch, so there's only one way in.

**PLAY ROOM** uses the same sheet, so the two entry points can't drift apart.

## 3. Adding apps and games

The lobby shelf now ends with an explicit **"Add app or game"** tile. Previously
the only routes were a small header pill or the empty-state button — both easy to
lose once the list has a few rows.

Apps and games are one list; the picker shows everything installed.

---

## Flow now

```
Lobby
  ├─ tap a game row   → banner + slide to launch → Game Space
  ├─ tap the emblem   → picker (most played first) + slide → Game Space
  ├─ PLAY ROOM        → same sheet
  └─ "Add app or game" → picker of everything installed
```

## Verified in the shipped APK

```
Verifies · v2 scheme: true
Signer SHA-256: 463c7264…c16e0
package: name='com.gamershub' versionCode='93' versionName='6.24.0'
expected: versionCode=93 versionName=6.24.0
not debuggable · media audit rc=0
```

Both build gates pass.

## Note

The game rows in the shelf still open their own launch sheet (banner + slide)
rather than going straight in — that's deliberate, since tapping a row is easy to
do by accident while scrolling. If you'd rather a row tap go directly to the
slider-less launch, say so and I'll make it one tap.
