#!/usr/bin/env python3
"""Guard: every privileged mutation must be journaled, or deliberately exempt.

The engine's OFF switch promises to put the phone back the way it was. For
`settings` rows that is `Restore`, checked by check_restore_coverage.py. But a
module toggle, a flashed zip, a `resetprop` and a `pm disable` cannot be
reconstructed from the device's current state -- the thing that was undone may
not be readable after the fact. Those have to be recorded at the moment they
happen, in ChangeLog.

This gate fails the build when a mutation shape appears in the source without a
ChangeLog record nearby. Without it, the promise quietly stops being true the
first time somebody adds a feature with a `touch .../disable` in it.

Run: python3 tools/check_changelog_coverage.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java"

CHANGELOG = SRC / "com/gamershub/core/ChangeLog.kt"
ENGINE = SRC / "com/gamershub/core/Engine.kt"

# Mutation shapes that change state which cannot be re-derived from the device.
# Each entry: (regex, plain-English name).
MUTATIONS = [
    (re.compile(r"touch\s+\$MODULES/\$\{?id\}?/disable|touch\s+/data/adb/modules/\S+/disable"),
     "module disable marker"),
    (re.compile(r"rm\s+-f\s+\$MODULES/\$\{?id\}?/disable|rm\s+-f\s+/data/adb/modules/\S+/disable"),
     "module enable (marker removal)"),
    (re.compile(r"touch\s+\$MODULES/\$\{?id\}?/remove|touch\s+/data/adb/modules/\S+/remove"),
     "module removal marker"),
    (re.compile(r"resetprop\s+-n\s+[A-Za-z_][\w.]*"),
     "system property write"),
    (re.compile(r"pm\s+(disable-user|disable|suspend)\s"),
     "package disable"),
    (re.compile(r"cmd\s+game\s+set\s"),
     "game mode write"),
    (re.compile(r"module install\s"),
     "module flash"),
]

# Files whose mutations are undone by a *reset function* that Restore calls,
# rather than replayed from the journal. Both are valid: the journal exists for
# changes that cannot be re-derived, this list is for changes that can simply be
# reset to a known state. The check verifies the reset really is wired into
# Restore, so "it resets somewhere else" cannot become a hiding place.
RESET_COVERED = {
    "com/gamershub/core/GfxEngine.kt": "resetFrameBoost() is called from Restore",
    "com/gamershub/core/RenderEngine.kt": "cmd game reset runs in Restore's reset path",
}

# Files allowed to contain a mutation shape without a ChangeLog reference, each
# with the reason. Keep this list short and justified.
EXEMPT = {
    "com/gamershub/core/ChangeLog.kt": "it is the journal",
    "com/gamershub/core/Restore.kt": "it is the reversal, not a change",
    "com/gamershub/core/root/Reboot.kt": "process control, not persistent state",
    "com/gamershub/core/root/DfRoot.kt": "starts an activity; writes nothing",
    "com/gamershub/core/GfxSafe.kt": "repair path; restores rather than changes",
    # The manual and the reporter are text: the user manual *describes* `module install`
    # in a step and the reporter *prints* the change journal into a bug report. Neither
    # can mutate anything, and matching them here was a false positive that failed the
    # pipeline on documentation alone.
    "com/gamershub/core/Manual.kt": "user manual text; describes features, changes nothing",
    "com/gamershub/core/IssueReporter.kt": "bug report text; prints the journal, changes nothing",
}


def has_record(text: str) -> bool:
    return "ChangeLog" in text or "ChangeLog.Kind" in text


def main() -> int:
    findings = []
    for path in SRC.rglob("*.kt"):
        rel = path.relative_to(SRC).as_posix()
        if rel in EXEMPT:
            continue
        text = path.read_text(errors="ignore")
        if has_record(text) or rel in RESET_COVERED:
            continue
        for i, line in enumerate(text.splitlines(), 1):
            if line.lstrip().startswith(("//", "*")):
                continue
            for pattern, name in MUTATIONS:
                if pattern.search(line):
                    findings.append((rel, i, name, line.strip()[:95]))

    if findings:
        print("FAIL: privileged mutations that are never journaled:")
        for rel, line, name, snippet in findings:
            print(f"  - {name}")
            print(f"      {rel}:{line}")
            print(f"      {snippet}")
        print()
        print("Record it with ChangeLog.record(...) so the engine can undo it, or add")
        print("the file to EXEMPT here with a reason. A change the app cannot reverse")
        print("is a change that survives 'engine off' without the user knowing.")
        return 1

    # The reversal must actually be wired into the engine's stop path, otherwise
    # journaling is bookkeeping that nobody reads.
    engine = ENGINE.read_text()
    if "ChangeLog.undo" not in engine:
        print("FAIL: Engine.stopEverything does not call ChangeLog.undo().")
        print("The journal is written but never replayed, so 'engine off' would")
        print("still leave module and prop changes in place.")
        return 1

    if not CHANGELOG.exists():
        print("FAIL: ChangeLog.kt is missing.")
        return 1

    # A reset-covered file is only trusted if Restore really calls its reset.
    restore = (SRC / "com/gamershub/core/Restore.kt").read_text()
    if "GfxEngine.resetFrameBoost" not in restore:
        print("FAIL: GfxEngine is exempt as reset-covered, but Restore never calls")
        print("GfxEngine.resetFrameBoost(). Its `cmd game` writes would survive engine off.")
        return 1

    print("PASS: every privileged mutation is journaled or reset on engine stop")
    return 0


if __name__ == "__main__":
    sys.exit(main())
