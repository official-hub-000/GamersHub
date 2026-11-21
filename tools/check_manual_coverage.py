#!/usr/bin/env python3
"""Guard: the in-app manual must actually cover the app.

The manual is a data table (`core/SelfHelp.kt`), which means it can rot silently: a
feature gets renamed in `ToolsScreen` and the manual keeps describing the old name, so
searching for what the user sees on screen finds nothing.

This gate is deliberately cheap and text-based — it does not try to parse Kotlin. It
checks the two things that break users:

  1. Every **row title** in Tools (and every Game-Labs / Root card title) appears in the
     manual's searchable text. A renamed feature fails here instead of shipping.
  2. Every sheet key the UI can open has at least one manual page pointing at it, so
     "Open it for me" is always available for the things that have a screen.

Run: python3 tools/check_manual_coverage.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/gamershub"
MANUAL = SRC / "core/Manual.kt"
TOOLS = SRC / "ui/ToolsScreen.kt"
LABS = SRC / "ui/GameLabsScreen.kt"
ROOT_SCREEN = SRC / "ui/RootScreen.kt"
SHELL = SRC / "ui/RootShell.kt"

# UI strings that are layout labels, not features.
SKIP = {
    "MP4 · up to 4K · mic off by default",
    "Real air to any site link",
    "Draw scale · Vulkan · surface and shade keys",
    "Phone looks · screen, name, tags",
    "Net wait · jitter · private DNS",
    "Free held apps · trim caches",
    "Wipe and drop cache · quick free memory",
    "Kill hidden apps · trim · drop cache",
    "Tilt grab · feel · live read",
    "Crisp · DPI · HZ rate",
    "Sideways in-game hub",
    "Loaded screen looks",
    "Key taps sent as the shell",
    "FPS · CPU · GPU · RAM · TEMP · PING",
    "7 styles · colour · fade · turn",
    "Bubble and panel",
    "Every feature and every help page, findable by name",
    "Sends the error log, build and phone details",
    # Runtime state banners, not features.
    "Root active",
    "Root not granted",
    "No modules installed.",
}


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def titles(text: str):
    """Feature titles as they are rendered on screen."""
    out = set()
    # ToolRow(title = "…")
    out.update(re.findall(r'title = "([^"]{3,60})"', text))
    # Card headings: Text("…", style = MaterialTheme.typography.titleMedium
    out.update(re.findall(r'Text\("([^"]{3,40})", style = MaterialTheme\.typography\.titleMedium', text))
    # Root card headings: Text("…", color = GC.Text, fontSize = 14..19.sp
    out.update(re.findall(r'Text\("([^"]{3,40})", color = GC\.Text, fontSize = 1[4-9]\.sp', text))
    return {t for t in out if t not in SKIP and not t.startswith("$")}


def main() -> int:
    manual = read(MANUAL)
    haystack = manual.lower()
    # Words that are substituted at runtime can never match a literal check.
    dynamic = re.compile(r"\$\{?")

    missing = []
    checked = 0
    for path in (TOOLS, LABS, ROOT_SCREEN):
        for title in sorted(titles(read(path))):
            if dynamic.search(title):
                continue
            checked += 1
            # Match on the first word-pair, so "Screen Grab" also covers
            # "Screen Grab (recording)" and "Report a problem" covers "report".
            probe = title.split("(")[0].strip().lower()
            if probe not in haystack:
                missing.append(f"{path.name}: {title!r} is not described in the manual")

    # ---- sheet keys the UI can open must have a manual page that points at them
    shell = read(SHELL)
    keys = set(re.findall(r'"([a-z]+)" -> SheetState\.', shell))
    keys.discard("apps")  # the app picker is an action, not a feature
    pages = set(re.findall(r'sheet = "([a-z]+)"', manual))
    for key in sorted(keys - pages):
        missing.append(f"sheet key {key!r} has no manual page (add sheet = \"{key}\" to a topic)")

    if missing:
        print(f"::error::manual coverage failed ({len(missing)} problem(s))")
        for line in missing:
            print(" -", line)
        return 1

    print(f"OK: {checked} feature titles covered, {len(keys)} sheet keys documented, "
          f"{len(re.findall(chr(10) + '            id = ', manual))} manual pages")
    return 0


if __name__ == "__main__":
    sys.exit(main())
