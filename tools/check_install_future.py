#!/usr/bin/env python3
"""
Install-future gate: keeps the APK installable and sane on Android versions
that do not exist yet.

Android's guarantee is asymmetric: future versions are backward compatible at
the install level, but the platform actively *blocks* sideloading once
targetSdkVersion falls too far behind, and raises the floor one API level per
release (Android 14 blocks < 23, Android 15 blocks < 24). An APK built today
can only stay installable if nothing in its manifest is hostile to new
platforms.

This gate fails the build when a change would make future installs fail:

  1. maxSdkVersion anywhere (manifest or <uses-permission>)  -> hard ceiling,
     the one thing that blocks new Android outright.
  2. <uses-feature> without android:required="false"          -> lets the
     installer reject a future device lacks a declared feature.
  3. Build.VERSION.SDK_INT == <number>                        -> exact-version
     check silently mis-handles every future API; must be >=.
  4. References to hidden platform APIs (android.internal.*, com.android.internal.*,
     android.app.ActivityThread)                              -> the classic
     "crashes on the next Android release" pattern.
  5. Regression guard: targetSdkVersion must not drop below 34, minSdkVersion
     must not rise above 26 — dropping below those widens the active-block risk
     and shrinks the installable device list.
"""
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
APP = ROOT / "app" / "src" / "main"

errors = []

def fail(msg):
    errors.append(msg)

# --- 1 & 2: manifest scans -------------------------------------------------
manifest = APP / "AndroidManifest.xml"
if not manifest.exists():
    fail("AndroidManifest.xml not found at expected path")
else:
    xml = manifest.read_text(encoding="utf-8")

    if re.search(r"maxSdkVersion", xml):
        fail("1. maxSdkVersion found in the manifest — it hard-caps which Android versions may install the APK. Remove it.")

    for m in re.finditer(r"<uses-feature\b[^>]*>", xml):
        tag = m.group(0)
        if 'android:required="false"' not in tag and "android:required='false'" not in tag:
            fail(f"2. <uses-feature> without required=\"false\": {tag.strip()} — declare it optional or it blocks install on devices lacking that feature.")

# --- 3: exact SDK equality in Kotlin/Java ----------------------------------
SDK_EQ = re.compile(r"SDK_INT\s*==")
for path in list(APP.glob("**/*.kt")) + list(APP.glob("**/*.java")):
    text = path.read_text(encoding="utf-8")
    for i, line in enumerate(text.splitlines(), 1):
        if SDK_EQ.search(line):
            fail(f"3. Exact SDK equality at {path.relative_to(ROOT)}:{i}: {line.strip()} — use >= so future API levels take the " +
                 "same branch as the newest known one.")

# --- 4: hidden platform API references --------------------------------------
HIDDEN = re.compile(r"(android\.internal\.|com\.android\.internal\.|android\.app\.ActivityThread\b)")
for path in list(APP.glob("**/*.kt")) + list(APP.glob("**/*.java")):
    text = path.read_text(encoding="utf-8")
    for i, line in enumerate(text.splitlines(), 1):
        if HIDDEN.search(line):
            fail(f"4. Hidden platform API at {path.relative_to(ROOT)}:{i}: {line.strip()}")

# --- 5: regression guards on the SDK floor/ceiling -------------------------
build = ROOT / "app" / "build.gradle.kts"
if build.exists():
    text = build.read_text(encoding="utf-8")
    m = re.search(r"targetSdk\s*=\s*(\d+)", text)
    if m and int(m.group(1)) < 34:
        fail(f"5. targetSdk regressed to {m.group(1)} (floor is 34). The platform actively blocks install when targetSdk falls behind; do not drop it.")
    m = re.search(r"minSdk\s*=\s*(\d+)", text)
    if m and int(m.group(1)) > 26:
        fail(f"5. minSdk rose to {m.group(1)} (ceiling is 26). That shrinks the device list the APK can install on.")
    m = re.search(r"compileSdk\s*=\s*(\d+)", text)
    if m and m.group(0) and int(m.group(1)) < 34:
        fail(f"5. compileSdk regressed to {m.group(1)}; keep it at the newest SDK the toolchain supports.")
else:
    fail("app/build.gradle.kts not found")

if errors:
    print("check_install_future: FAIL")
    for e in errors:
        print(" -", e)
    sys.exit(1)

print("check_install_future: PASS (installable on future Android: no maxSdkVersion, no required features, no exact SDK equality, no hidden API, SDK floors intact)")
