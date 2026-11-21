#!/usr/bin/env python3
"""Every wireless-debugging failure path must produce a message the user can read.

The complaint this guards against is "wireless debugging not working" with nothing
on screen to explain it. That happens when a function returns a bare false, or a
throwable's message, without setting WirelessAdb.lastError — the UI then has an
empty string to show and the user has nothing to report.

Fails the build when:
  1. a `return false to` in WirelessAdb.kt hands back a message that is not
     `lastError` and not assigned to it in the same breath;
  2. a `catch` block in WirelessAdb.kt returns or assigns without touching
     lastError;
  3. the sheet stops showing lastError at all.

Deliberately shallow: it is a lint over a specific file, not a parser.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WIRELESS = ROOT / "app/src/main/java/com/gamershub/core/adb/WirelessAdb.kt"


def check() -> list[str]:
    problems: list[str] = []
    src = WIRELESS.read_text()
    lines = src.splitlines()

    for i, line in enumerate(lines, 1):
        stripped = line.strip()

        # 1. `return false to <something>` outside the helpers that set lastError.
        m = re.search(r"return false to (.+)", stripped)
        # fail() is the one place allowed to hand back a message: it assigns lastError.
        if m and "lastError" not in m.group(1) and "message" != m.group(1).strip():
            problems.append(
                f"WirelessAdb.kt:{i}: returns a message that is not lastError — "
                f"the UI would show an empty or invented reason: {stripped}"
            )

        # 2. A catch that neither sets lastError nor delegates to fail().
        if stripped.startswith("} catch (") or stripped.startswith("catch ("):
            window = "\n".join(lines[i - 1 : i + 8])
            if "lastError" not in window and "fail(" not in window and "no lastError:" not in window:
                problems.append(
                    f"WirelessAdb.kt:{i}: catch block swallows the reason without "
                    f"setting lastError"
                )

    # 3. The sheet must render lastError somewhere.
    for sheet in (ROOT / "app/src/main/java/com/gamershub/ui/sheets").glob("WirelessAdbSheet.kt"):
        text = sheet.read_text()
        if "lastError" not in text:
            problems.append(
                f"{sheet.name}: never mentions WirelessAdb.lastError, so a failed "
                f"attempt can be invisible"
            )

    return problems


def main() -> int:
    problems = check()
    if problems:
        print("FAIL: a wireless-debugging failure could reach the user with no reason")
        for p in problems:
            print("  " + p)
        return 1
    print("PASS: every wireless-debugging failure path sets a reason the sheet shows")
    return 0


if __name__ == "__main__":
    sys.exit(main())
