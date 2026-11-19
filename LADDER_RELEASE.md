# GamersHub v6.28.0 — the two-layer root model, wired

## 📲 Download

**https://github.com/official-hub-000/GamersHub/releases/download/v6.28.0/GamersHub-v6.28.0.apk**

| | |
|---|---|
| File | `GamersHub-v6.28.0.apk` — 24,567,386 bytes (23.4 MB) |
| Package | `com.gamershub` |
| Version | **6.28.0** (versionCode **100**) |
| Signing | APK Sig v2, release keystore |

---

## You described it right — so the app now works that way

Two layers, and they are genuinely different things:

| | Temporary root | Manager |
|---|---|---|
| **What** | uid 0 for this session | policy for every app + module zips |
| **Lifetime** | dies with the engine | survives reboot |
| **Who** | acquired by the ladder | KernelSU / Magisk / APatch |

Before this build those pieces existed in three different places with no single
owner. Now `PrivilegeLadder` owns the sequence.

## The ladder

It climbs in order and reports the exact rung that answered:

| # | Rung | What it means |
|---|---|---|
| 1 | Shizuku binder | shell (uid 2000), no system writes |
| 2 | live root session | already held — no prompt |
| 3 | device su | the manager's grant |
| 4 | su via Shizuku | grant lives in the shell process — adopted, not faked |
| 5 | temporary provider | session-only shim over the device's su |

**Every step is verified with `id`.** Nothing reports success it did not see.

`descend()` gives all of it back in one call — root session, temp provider, Shizuku
binder — and the master engine calls it on **every** stop. That is what makes
"temporary" true instead of a label on a card.

## Root tab

New **PRIVILEGE** card, both layers side by side:

```
TEMPORARY                        MANAGER
uid 0 for this session           KernelSU
live — drops with the engine     superuser + module zips
```

The live one is lit; the other stays readable so you can see what *could* be done.
The last climb prints step by step:

```
✓ Shizuku binder · ✗ device su · ✓ temporary provider
```

**CLIMB TO ROOT** / **DROP ROOT** sit right on the card. And every "request root"
button in the app — Root tab, Tools, floating panel — now climbs the ladder instead
of attempting one route and giving up.

## Manager depth

Already working, unchanged: superuser policy per app, module store (KernelSU repo +
MMRL), zip flashing through the manager's own installer, module removal, enable and
disable.

New in this build:

- `isDisabled()` reads the on-disk `disable` flag, not the manager's cache — the two
  disagree after a failed boot
- `softRebootUserspace()` — the restart-zygote path KernelSU late-load uses, rather
  than a full reboot

---

## On DFRoot, briefly

DFRoot produces **layer 1** by exploiting the kernel. GamersHub produces layer 1 from
whatever `su` your device already has, and gives it back when you switch off.

I did not ship the exploit: it needs a prebuilt `.ko` per kernel family, this repo has
no NDK toolchain, and it leaves a page-cache write primitive plus permissive SELinux
on your phone permanently. If DFRoot already gave you root, **rung 2 or 3 will find
it** and every feature above works against it.

---

## Verified in the shipped APK

```
Signer SHA-256: 463c7264…c16e0
package: name='com.gamershub' versionCode='100' versionName='6.28.0'
expected: versionCode=100 versionName=6.28.0
not debuggable · media audit rc=0 · RESULT: PASS
```

Both CI gates pass: master-engine teardown coverage, restore coverage.

## Note

Two build attempts went red on a Gradle artifact-host outage
(`Could not find kotlin-gradle-plugin:2.0.20`). Then two genuine compile errors of
mine — `hold()` needed the context, and the Tools toggle wanted a `Boolean` where the
ladder returns a `Result`. Both fixed; the third run was clean.
