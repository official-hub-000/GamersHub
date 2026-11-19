# GamersHub v6.22.0 — Game Space now looks like the intro

## 📲 Download

**https://github.com/official-hub-000/GamersHub/releases/download/v6.22.0/GamersHub-v6.22.0.apk**

Release page: https://github.com/official-hub-000/GamersHub/releases/tag/v6.22.0

| | |
|---|---|
| File | `GamersHub-v6.22.0.apk` — 25,725,661 bytes (24.5 MB) |
| Package | `com.gamershub` |
| Version | **6.22.0** (versionCode **91**) |
| Signing | APK Sig v2, release keystore |

---

## The problem

The hub's mark was a rounded pentagon with a white lightning bolt — a generic
gaming glyph with no relationship to the film. Watch the intro, open the hub, and
they didn't look like the same product.

## What the intro actually is

I pulled the source frames out of `tools/intro/parts/` and looked at them. The
emblem is a **deep open V**, not a shallow chevron:

- thin **violet silhouette** as an open V — tips at 14.5% height, apex at 90.5%
- **gunmetal band** just inside it
- **armour wedges** red→orange meeting at a fold in the apex, each half with its
  own gradient so the fold reads
- bright **leading edge** catching light along each wing
- nested **inner chevron** + **gunmetal core notch** at the point
- **circuit traces** to amber nodes, plus **studs** along the wings

## What ships

**`GhostMark`** — the full emblem in the backdrop at 20% opacity, built in the
same order the film builds it. Static, so it costs nothing per frame.

**`MarkCore`** — the chrome mark, same geometry, and it isn't decoration:

| Part | Driven by |
|---|---|
| Two energy rails | actual free memory |
| Core glow | actual CPU load |
| Trace sweep | animates while the engine is on |
| Engine off | draws **inert** — dashed, desaturated |

**Nav chevrons** use the emblem gradient when active and carry the same notch.

## How this was verified

No emulator or phone available, so I ported the geometry to Python and rendered
it before every build. That caught two defects in my own first version:

1. **Shallow V.** First pass used a rounded chevron (tips 20%, apex 94%) — read as
   a completely different logo. Corrected to the render's proportions.
2. **Chevron poking outside the silhouette.** The inner chevron started at
   x=0.255/y=0.560, which is *outside* the wing at that depth, drawing two stray
   violet lines. Moved inside.

The renderer is committed (`tools/emblem_preview.py`) with its output
(`docs/emblem-preview.png`), so the geometry can be re-checked without a device.

## Verified in the shipped APK

```
Verifies · v2 scheme: true
Signer SHA-256: 463c7264…c16e0
package: name='com.gamershub' versionCode='91' versionName='6.22.0'
expected: versionCode=91 versionName=6.22.0
not debuggable · media audit rc=0
```

Both build gates pass (engine teardown, restore coverage).

## Where to look

Open Game Space. Emblem in the backdrop, live one top-left — and it **dims to
dashed when you turn the engine off**.

## Honest note

I verified the emblem by rendering its geometry, not by screenshotting the running
app. The shapes match the intro; small-size legibility at 40dp is the thing I
couldn't check without a device. If the top-left mark looks muddy on your phone,
tall that and I'll simplify it for small sizes specifically.
