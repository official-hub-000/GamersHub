#!/usr/bin/env python3
"""Compose constraints that crash at run time but compile perfectly.

`SheetFrame` is a Column with `verticalScroll`. Children of a scrolling column are
measured with an infinite maximum height. A LazyColumn (or LazyRow in a horizontal
scroller) refuses that measurement and throws:

  IllegalStateException: Vertically scrollable component was measured with an
  infinity maximum height constraints, which is disallowed.

It compiles. It types-checks. `heightIn(max = ...)` does not prevent it, because that
bounds the list's own size, not the constraint it is measured with. It only shows up
when the sheet is opened on a device, and it reaches the user as "A step broke —
GamersHub reopened".

This checks the structure directly rather than trusting review.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
UI = ROOT / "app/src/main/java/com/gamershub/ui"
problems = []

# ---- 1. No lazily-scrolled list inside a vertically scrolling sheet frame ----------
frames = {}
for path in UI.rglob("*.kt"):
    text = path.read_text(encoding="utf-8")
    if "fun SheetFrame(" in text:
        frames[path] = text

for path, text in frames.items():
    lines = text.split("\n")
    # Find the body of SheetFrame, up to the next top-level fun.
    try:
        start = next(i for i, ln in enumerate(lines) if ln.startswith("fun SheetFrame("))
    except StopIteration:
        continue
    end = next(
        (i for i in range(start + 1, len(lines)) if lines[i].startswith("@Composable")),
        len(lines),
    )
    body = "\n".join(lines[start:end])
    if "verticalScroll" not in body:
        continue  # only a vertical scroller creates the infinite constraint

    # Now find every SheetFrame( ... ) call site in every UI file and inspect the block.
    for path2 in UI.rglob("*.kt"):
        t2 = path2.read_text(encoding="utf-8")
        if "SheetFrame(" not in t2:
            continue
        l2 = t2.split("\n")
        for i, ln in enumerate(l2):
            if "SheetFrame(" not in ln:
                continue
            # Walk the call until braces balance; guard with a window.
            depth = 0
            started = False
            for j in range(i, min(i + 400, len(l2))):
                depth += l2[j].count("{") - l2[j].count("}")
                if "{" in l2[j]:
                    started = True
                if started and depth <= 0:
                    break
            block = "\n".join(l2[i:j + 1])
            for m in re.finditer(r"\bLazyColumn\s*[({]", block):
                problems.append(
                    f"{path2.name}:{i + 1}: LazyColumn inside SheetFrame (a verticalScroll "
                    f"column) — this throws 'measured with an infinity maximum height' at "
                    f"run time and shows up as a crash restart"
                )

# ---- 2. A port found by pairing must be handed to connect ------------------------
wireless = (UI.parent / "core/adb/WirelessAdb.kt").read_text(encoding="utf-8")
sheet_files = list(UI.rglob("*.kt"))
pair_connect = None
for path in sheet_files:
    text = path.read_text(encoding="utf-8")
    if "WirelessAdb.pair(" in text and "WirelessAdb.connect(" in text:
        pair_connect = (path, text)

if pair_connect:
    path, text = pair_connect
    # Between the pair( call and the connect( call, a port must be established.
    pi = text.find("WirelessAdb.pair(")
    ci = text.find("WirelessAdb.connect(")
    between = text[pi:ci]
    if "rediscoverShellPort" not in between and "portOverride" not in between:
        problems.append(
            f"{path.name}: after WirelessAdb.pair the code calls connect() with no port — "
            f"the connect port is not the pairing port and is only advertised after "
            f"pairing, so this pairs successfully and then fails to connect"
        )

# ---- 3. A scrollable must be bounded BEFORE it is made scrollable ---------------
# Modifiers apply outside-in. `.verticalScroll(...).heightIn(max = X)` measures the
# scrollable first, with an unbounded height, and applies the bound inside it — which
# throws "Vertically scrollable component was measured with an infinity maximum height
# constraints". The bound has to come first.
import re as _re

for path in (UI.rglob("*.kt")):
    lines = path.read_text(encoding="utf-8").split("\n")
    for i, ln in enumerate(lines):
        if "verticalScroll" not in ln:
            continue
        # A line that only MENTIONS verticalScroll (a comment explaining this very rule)
        # is not a call site. Requiring the opening paren keeps the doc comments out.
        if ln.lstrip().startswith(("*", "//")) or "verticalScroll(" not in ln:
            continue
        # Walk the rest of this modifier chain, skipping comment lines. The chain ends at
        # a closing paren or the composable's opening brace.
        for j in range(i + 1, min(i + 14, len(lines))):
            nxt = lines[j].strip()
            if not nxt or nxt.startswith(("*", "//")):
                continue
            if _re.search(r"\.(heightIn|height|size)\(", nxt):
                problems.append(
                    f"{path.name}:{j + 1}: height bound applied AFTER verticalScroll — the "
                    f"scrollable is measured with an infinite height and throws; put the "
                    f"bound first"
                )
                break
            if nxt.startswith(")") or nxt.startswith("{"):
                break

if problems:
    print(f"FAIL: {len(problems)} unsafe Compose/transport construction(s)")
    for p in problems:
        print(f"  - {p}")
    sys.exit(1)

print("PASS: no lazily-scrolled list inside a scrolling sheet; pairing hands its port to connect")
