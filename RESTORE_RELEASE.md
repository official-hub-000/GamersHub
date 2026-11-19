# GamersHub v6.21.0 — optional root + restore-to-default engine

## 📲 Download

**https://github.com/official-hub-000/GamersHub/releases/download/v6.21.0/GamersHub-v6.21.0.apk**

Release page: https://github.com/official-hub-000/GamersHub/releases/tag/v6.21.0

| | |
|---|---|
| File | `GamersHub-v6.21.0.apk` — 25,709,281 bytes (24.5 MB) |
| Package | `com.gamershub` |
| Version | **6.21.0** (versionCode **90**) |
| Signing | APK Sig v2, release keystore |

---

## 1. Root is an option, not a requirement

Three modes, and the app tells you which one you're in:

| Mode | What you get |
|---|---|
| **No privilege** | overlays, macros, panel, recording |
| **Shizuku** | shell commands, settings writes, package control |
| **Root** | everything Shizuku can do, **plus** props, `/data/adb`, module flashing, kernel tunables, app private data |

This lives in **one table** (`Channel.CAPABILITIES`), so the asymmetry can't drift
over time. Root is a strict superset of Shizuku by construction.

Turning the engine on **no longer demands root**. No root → not nagged. Granting
it is a separate, remembered choice, and **releasing root doesn't disturb anything
else** — the app falls back to the Shizuku subset and keeps working.

## 2. Engine OFF is now a real restore

Previously OFF stopped the services. Now it **undoes what was done.**

**ON** → snapshots all 17 settings keys + display override.
**OFF** → puts those exact values back.

That distinction is the whole point: hardcoding refresh rate to 60 Hz would be
wrong on a 120 Hz panel, and writing an assumed brightness is worse than leaving
it alone. The snapshot is the source of truth.

OFF does all of this:

1. Closes every overlay, recording, stream, upscale pipeline
2. Kills GamersHub root processes
3. **Restores** all 17 settings keys + display size/density + device identity + graphics props + private DNS
4. Releases the root session, deletes the temporary root provider
5. Disconnects the Shizuku binder, kills its user service
6. Clears local "running" state

Restore is idempotent, runs every stage even if one fails, and reads values back
to compare.

## 3. The OFF button tells the truth

Tapping OFF opens a sheet listing **from live state** what will be undone — and
what **can't** be:

> • Modules you installed stay installed — uninstall them in the store
> • An app permission Android granted (e.g. overlay) is yours, not ours
> • Module changes already flashed need a reboot to unwind
> • Files you deleted from the system cannot be brought back

Afterwards it shows **restored / skipped / could not restore** separately, so a
display left scaled can't pass silently.

## 4. New build gate

`tools/check_restore_coverage.py` fails the release if any `settings put` key is
missing from the restore list:

```
settings keys written  : 17
settings keys restorable: 17
PASS
```

A future feature can't write a setting that survives "restore to default". Runs
alongside the service-teardown gate, both before the build.

---

## Verified in the shipped APK

```
Verifies · v2 scheme: true
Signer SHA-256: 463c7264…c16e0
package: name='com.gamershub' versionCode='90' versionName='6.21.0'
expected: versionCode=90 versionName=6.21.0
not debuggable · media audit rc=0
```

## ⚠️ Note on the build

This took four CI attempts. The first three failed to compile — an extension
property declared as `val Channel.Capability.label` isn't importable that way. Two
of those failures were my own repeated mistake, and one push was silently dropped
as non-fast-forward earlier in the session. All fixed and verified against the
correct commit (`c8d5ecb`) before tagging.

## How to use it

- **No root is fine.** Turn the engine on and use what your device allows.
- **Restore:** Tools → ENGINE → OFF. Read the list, confirm, check the result.
