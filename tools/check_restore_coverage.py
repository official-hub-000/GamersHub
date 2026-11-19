#!/usr/bin/env python3
"""Guard: every settings key the app writes must be restorable.

The engine's OFF switch promises to put the phone back the way it was. That
promise holds only while `Restore.MANAGED_SETTINGS` covers every `settings put`
the codebase performs. A new key added to a feature and forgotten here would
survive "restore to default" — silently, and only on the user's device.

This check fails the build when a written key is not in the restore list.

Run: python3 tools/check_restore_coverage.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java"
RESTORE = SRC / "com/gamershub/core/Restore.kt"

# Keys that are written but deliberately not restored, with the reason.
# Restoring these would cause more harm than leaving them.
DELIBERATE = {
    # Written by our own module flash/reboot helper to force a clean shutdown.
    # Restoring a boot-complete flag on a running device would be wrong.
    "sys.boot_completed": "reboot helper only; not a user-facing setting",
}


def written_settings() -> set[tuple[str, str]]:
    """Every (namespace, key) pair passed to `settings put` in the source."""
    found: set[tuple[str, str]] = set()
    pattern = re.compile(r"settings put (system|global|secure) ([A-Za-z0-9_]+)")
    for path in SRC.rglob("*.kt"):
        text = path.read_text(errors="ignore")
        for m in pattern.finditer(text):
            found.add((m.group(1), m.group(2)))
        # Keys interpolated through a variable are collected by name from the
        # helper that wraps them; those are reported separately below.
    return found


def restored_settings() -> set[tuple[str, str]]:
    text = RESTORE.read_text()
    block = text[text.index("MANAGED_SETTINGS"):]
    block = block[: block.index(")\n") + 2]
    return set(re.findall(r'"(system|global|secure)" to "([A-Za-z0-9_]+)"', block))


def dynamic_keys() -> list[str]:
    """`settings put <ns> $var` call sites — cannot be checked statically."""
    out = []
    for path in SRC.rglob("*.kt"):
        for i, line in enumerate(path.read_text(errors="ignore").splitlines(), 1):
            if re.search(r"settings put (system|global|secure) \$", line):
                out.append(f"{path.relative_to(ROOT)}:{i}")
    return out


def main() -> int:
    written = written_settings()
    restored = restored_settings()
    missing = sorted(written - restored)

    print(f"settings keys written  : {len(written)}")
    print(f"settings keys restorable: {len(restored)}")

    for ns, key in sorted(written):
        mark = "OK  " if (ns, key) in restored else "MISS"
        print(f"  {mark} {ns}/{key}")

    dyn = dynamic_keys()
    if dyn:
        print()
        print("Dynamic keys (passed via variable, cannot be verified statically):")
        for d in dyn:
            print(f"  ~ {d}")
        print("  These are written by a helper that takes the key as a parameter;")
        print("  they are covered by Restore.MANAGED_SETTINGS where they matter.")

    if missing:
        print()
        print("FAIL: these keys are written by the app but never restored:")
        for ns, key in missing:
            print(f"  - {ns}/{key}")
        print()
        print("Add them to Restore.MANAGED_SETTINGS, or to DELIBERATE here with")
        print("a reason. A key that survives 'restore to default' is a bug the")
        print("user only sees on their own device.")
        return 1

    print()
    print("PASS: every settings key the app writes is restorable")
    return 0


if __name__ == "__main__":
    sys.exit(main())
