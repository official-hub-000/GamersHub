# GamersHub v6.27.0 — engine in the lobby, su manager, deeper Game Space

## 📲 Download

**https://github.com/official-hub-000/GamersHub/releases/download/v6.27.0/GamersHub-v6.27.0.apk**

| | |
|---|---|
| File | `GamersHub-v6.27.0.apk` — 24,567,386 bytes (23.4 MB) |
| Package | `com.gamershub` |
| Version | **6.27.0** (versionCode **99**) |
| Signing | APK Sig v2, release keystore |

---

## 1. Master engine — now in the lobby

The switch that decides whether GamersHub touches your phone **at all** is in the
lobby header, above the fold. It was only in Tools, which is the wrong place for the
one control that matters most.

Off means off: root, Shizuku, overlays, capture, upscale — all stopped, phone back
to how it was. A rooted device with the engine off does nothing. Not "does less" —
nothing at all.

The same switch is in the Game Space Control Center, so you can stop everything
without leaving the game.

## 2. The engine gates Game Space itself

Game Space used to open and play its intro regardless of the switch. Now:

- engine **off** → read-only preview, no intro film, no launch, amber banner saying so
- engine **on** → everything live

Launch is gated for the same reason. A stray tap must not start privileged work with
the master switch off.

## 3. Built-in su manager — Root tab

Every app with a root decision, and a switch to flip it. It drives the policy store
of whichever manager is installed rather than keeping its own list:

| Manager | Policy source |
|---|---|
| Magisk | `/data/adb/magisk.db` → sqlite `policies` |
| KernelSU | `ksud su` |
| APatch | `apd su` |

If it cannot read the store, it says **"cannot read the policy store"** — not an
empty list, which would look identical to "nobody has root". Those are different
answers and the UI keeps them apart.

Each row also has **FORGET**, which deletes the decision so the app asks again.

## 4. Temporary root — Root tab

Reports what the device actually has:

```
persistent / session / per-boot · kernel · SELinux mode · uptime · su path
```

**Release root now** drops the session immediately, and the master engine calls the
same path on every stop — so a temporary root really is temporary and cannot outlive
the switch.

### On DFRoot — I read it, and I did not ship it

DFRoot is a **kernel page-cache exploit** (CVE-2026-43284, "DirtyFrag"). It decrypts
crafted AES-CBC ESP packets directly into the page cache of a `splice()`d file,
patching shellcode into `libc++.so` with no write permission and no copy-on-write.
That shellcode fires inside init (uid 0, tid 1) and `insmod`s a kernel module that
writes `false` to `selinux_state`.

Three concrete reasons it cannot go into this build:

1. **It is 59% C, 13% assembly**, and ships a prebuilt `.ko` *per kernel family*.
   This repo has no NDK build. A wrong offset does not fail cleanly — it bricks the
   phone.
2. **It is dead on patched kernels.** Any vendor that backported the fix, or any
   kernel past the vulnerable window, gets a button that fails. That is exactly the
   "is it real?" problem the rest of this app works hard to avoid.
3. **It leaves the device permanently weaker.** A page-cache write primitive plus a
   permissive-SELinux module is a hole for every other app on the phone, traded for
   one root shell.

What *is* useful about it is already here: if your phone has root from DFRoot or any
other source, the whole Root tab works against it. GamersHub needs a working `su`,
not a specific manager.

## 5. Game Space, rebuilt

Every surface was a flat fill with a 1px border. That flatness is exactly why it read
as basic next to the lobby. New `glass()` surface:

- two-stop vertical fill, with a **top rim light** that falls off before the corners
- a faint accent line along the foot of each card
- an **accent bloom** on the featured card, like the launcher icon
- a two-shaft backdrop and a vignette, so the layout has a room around it
- the LAUNCH key gets a lit top edge and a darker foot, like a physical key

All of it is **draw passes, not composables**. The screen re-renders every 2 seconds
with live stats, so a blur or extra layer here would be paid constantly — this costs
one draw call.

---

## Verified in the shipped APK

```
Signer SHA-256: 463c7264…c16e0
package: name='com.gamershub' versionCode='99' versionName='6.27.0'
expected: versionCode=99 versionName=6.27.0
not debuggable · media audit rc=0 · RESULT: PASS
```

## Note

Two build attempts went red on a Gradle infrastructure outage
(`Could not find kotlin-gradle-plugin:2.0.20` — the artifact host was returning 5xx).
Reran clean; nothing in the app changed for it.
