#!/usr/bin/env python3
"""Guard: every service declared in the manifest must be torn down by Engine.

The master engine switch promises that turning it off leaves *nothing*
running. That promise is only true while `Engine.stopEverything()` stops
every service the app can start. This check fails the build if a new
service is added to AndroidManifest.xml without being added to the
teardown list.

Run: python3 tools/check_engine_coverage.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
ENGINE = ROOT / "app/src/main/java/com/gamershub/core/Engine.kt"

# Services that are deliberately not part of the runtime teardown:
#   GamersHubShellService is the Shizuku *user service*; it is killed by
#   Shell.disconnect() + pkill, not by stopService().
DELIBERATE = {"GamersHubShellService"}


def declared_services() -> set[str]:
    text = MANIFEST.read_text()
    names = set()
    # <service android:name=".overlay.FloatingPanelService" ... />
    for block in re.findall(r"<service\b(.*?)/>", text, re.S):
        m = re.search(r'android:name="([^"]+)"', block)
        if m:
            names.add(m.group(1).rsplit(".", 1)[-1])
    return names


def torn_down() -> set[str]:
    text = ENGINE.read_text()
    return set(re.findall(r"([A-Za-z0-9_]+Service)::class", text))


def main() -> int:
    services = declared_services()
    handled = torn_down()
    missing = sorted(services - handled - DELIBERATE)

    print(f"manifest services : {len(services)}")
    print(f"stopped by Engine : {len(handled)}")
    for s in sorted(services):
        mark = "OK  " if s in handled or s in DELIBERATE else "MISS"
        note = " (deliberate)" if s in DELIBERATE else ""
        print(f"  {mark} {s}{note}")

    if missing:
        print()
        print("FAIL: these services are never stopped by Engine.stopEverything():")
        for m in missing:
            print(f"  - {m}")
        print()
        print("Add them to the list in core/Engine.kt, or to DELIBERATE here")
        print("with a note explaining what tears them down instead.")
        return 1

    print()
    print("PASS: every declared service is covered by the master engine teardown")
    return 0


if __name__ == "__main__":
    sys.exit(main())
