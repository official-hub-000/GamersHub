# GamersHub v6.19.0 — gaming module support

## 📲 Download

**https://github.com/official-hub-000/GamersHub/releases/download/v6.19.0/GamersHub-v6.19.0.apk**

Release page: https://github.com/official-hub-000/GamersHub/releases/tag/v6.19.0

| | |
|---|---|
| File | `GamersHub-v6.19.0.apk` — 25,676,513 bytes (24.5 MB) |
| Package | `com.gamershub` |
| Version | **6.19.0** (versionCode **88**) |
| Signing | APK Sig v2, release keystore |

There is a new **ROOT** tab — the 4th tab in the bottom nav.

---

## The bug that was blocking you

The last build only looked for `su` on `PATH`. On a real Magisk install the binary
lives at `/debug_ramdisk/su`, which is **not** on an app's `PATH`. So root was
reported as missing even on a properly rooted phone.

Now all real locations are probed:

```
/system/bin/su   /system/xbin/su   /sbin/su   /su/bin/su
/debug_ramdisk/su          ← Magisk
/data/adb/magisk/su
/data/adb/ksu/bin/su       ← KernelSU
/data/adb/ap/bin/su        ← APatch
/vendor/bin/su
```

`DENIED` is also now separate from `NOT_ROOT`, so if you get the Superuser prompt
and deny it you're told to go approve it — not told you have no root.

## Manager detection

| Manager | Detected by | Flash command |
|---|---|---|
| Magisk | `magisk` binary, `/data/adb/magisk` | `magisk --install-module` |
| KernelSU | `ksud`, `/data/adb/ksu`, `/data/adb/ksu/bin/su` | `ksud module install` |
| APatch | `apd`, `/data/adb/ap` | `apd module install` |

Version is shown on the Root screen.

## Installing a gaming module

Tap **CHOOSE .ZIP**, pick the module you downloaded.

1. **Validated first** — the zip must contain `module.prop` or `META-INF`. A random
   zip is rejected instead of being pushed into `/data/adb`, which is one of the
   ways phones bootloop.
2. **Staged** in `/data/local/tmp` — the manager runs as root in a different mount
   namespace and cannot reliably read your app sandbox or the picker's content URI.
3. **Flashed** by the manager's own installer. Its real output is shown, including
   `!` error lines. No success is reported that wasn't observed.
4. Staged copy deleted.

## Managing installed modules

Read straight from `module.prop`, so the list matches the manager app exactly —
disabled ones, removal-pending ones, and reboot-pending updates included.

- **ENABLE / DISABLE** — adds or removes the `disable` marker
- **REMOVE** — sets the `remove` marker; the manager deletes it during boot
  (deleting a live module whose files are mapped into running processes is how
  systems get wedged)

Changes take effect after reboot. That's the managers' design, not a GamersHub
limit — there's a REBOOT button on the screen.

## Zygisk warning

Most gaming modules inject into game processes and need Zygisk. If it's off, the
screen says so at the top, instead of letting you wonder why a module installed
cleanly and did nothing.

## Diagnostics

**DIAGNOSE** prints every probe, so a root problem is visible instead of opaque:

```
state        READY
su on PATH   /debug_ramdisk/su
su binary    /debug_ramdisk/su
method       su (/debug_ramdisk/su)
session      alive
uid now      uid=0(root) gid=0(root)
magisk       27.0:MAGISK
zygisk       enabled
modules      3 installed
manager      Magisk
```

---

## Verified in the shipped APK

```
Verifies · v2 scheme: true
Signer SHA-256: 463c7264…c16e0
package: name='com.gamershub' versionCode='88' versionName='6.19.0'
expected: versionCode=88 versionName=6.19.0
not debuggable · media audit rc=0
```

Engine teardown gate still passes — all 9 services die when the master switch
goes off.

## How to use it

1. Install the APK
2. Open the **Root** tab → **REQUEST ROOT** → approve the Superuser prompt
3. **CHOOSE .ZIP** → pick your gaming module
4. Read the result on screen, then **REBOOT**

If a module needs Zygisk, enable it in your manager first — the screen will tell
you if it's off.
