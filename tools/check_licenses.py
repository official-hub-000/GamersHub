#!/usr/bin/env python3
"""Licence obligations for code this app does not own.

Bundling a GPL binary is not a file copy. If the notice, the licence text or the
source offer goes missing while the binary stays in the APK, the distributed work is
out of compliance — and nothing about the app stops working, so nobody notices. This
exists so that cannot happen quietly.
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
problems = []
notes = []


def read(p):
    return (ROOT / p).read_text(encoding="utf-8", errors="replace")


def check(ok, message):
    if not ok:
        problems.append(message)


# ---- The binary, if present, brings obligations with it --------------------------
binary = ROOT / "app/src/main/jniLibs/arm64-v8a/libksud.so"
bundled = binary.exists() and binary.stat().st_size > 0

if bundled:
    notes.append(f"ksud bundled ({binary.stat().st_size:,} B) — GPL-3.0 obligations apply")
    check((ROOT / "licenses/KernelSU-GPL-3.0.txt").exists(),
          "licenses/KernelSU-GPL-3.0.txt is missing but libksud.so ships in the APK")
    check((ROOT / "licenses/KernelSU-SOURCE.txt").exists(),
          "licenses/KernelSU-SOURCE.txt is missing but libksud.so ships: GPL-3.0 s6 "
          "requires the corresponding source to be offered to recipients")
    offer = read("licenses/KernelSU-SOURCE.txt") if (ROOT / "licenses/KernelSU-SOURCE.txt").exists() else ""
    check("github.com/tiann/KernelSU" in offer,
          "the source offer must name where the source is")
    check("v3.3.1" in offer or "tag" in offer.lower(),
          "the source offer must name an exact version, not just a repository")
    third = read("THIRD-PARTY.md")
    check("KernelSU" in third,
          "THIRD-PARTY.md must document the bundled KernelSU backend")
    check("libksud.so" in third,
          "THIRD-PARTY.md must record that the binary ships as libksud.so")
    # The app must tell the user, not only the repository.
    check("KernelSU" in read("app/src/main/java/com/gamershub/core/root/BundledKsud.kt"),
          "BundledKsud must reference KernelSU so the in-app attribution exists")
else:
    notes.append("ksud not bundled in this checkout — the build fetches it; obligations "
                 "still checked against the workflow")

# ---- The workflow must fetch a pinned version, not a floating one ----------------
wf = read(".github/workflows/android-ci.yml")
check("tiann/KernelSU" in wf,
      "the CI workflow must fetch ksud from its upstream project")
check('KSU_VERSION="v' in wf,
      "the ksud version must be pinned to an exact tag; a floating ref makes the shipped "
      "binary different on every build")
check("177ELF" in wf,
      "the fetch step must verify the downloaded ksud is a real ELF binary: a truncated "
      "or error-page download would otherwise only fail on the device")
check("1000000" in wf,
      "the fetch step must reject an implausibly small ksud")
check("sha256sum" in wf,
      "the fetched ksud must be hashed and recorded, so the shipped binary can be named")

# ---- DFRoot, the other GPL component --------------------------------------------
check((ROOT / "licenses/DFRoot-LICENSE.txt").exists(),
      "licenses/DFRoot-LICENSE.txt is missing")
check("DFRoot" in read("THIRD-PARTY.md"),
      "THIRD-PARTY.md must document DFRoot")

if problems:
    print(f"FAIL: {len(problems)} licence problem(s)")
    for p in problems:
        print(f"  - {p}")
    sys.exit(1)

for n in notes:
    print(f"  {n}")
print("PASS: licence obligations are satisfied for every bundled component")
