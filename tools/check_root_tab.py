#!/usr/bin/env python3
"""Regression gate for the ROOT tab.

Every rule here corresponds to a defect that made the ROOT tab look broken: controls
that were disabled, dead, or silently no-ops while the device was in fact rooted. They
are cheap textual checks, deliberately, so they run in the same CI job as the rest of
the suite and fail the build rather than reaching a release.

Run: python3 tools/check_root_tab.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/gamershub"
SHELL = SRC / "core/root/RootShell.kt"
LADDER = SRC / "core/root/PrivilegeLadder.kt"
REBOOT = SRC / "core/root/Reboot.kt"
SCREEN = SRC / "ui/RootScreen.kt"

failures = []
checks = 0


def strip_comments(text):
    """Drop // comments so prose about a function is never mistaken for a call."""
    out = []
    for line in text.split("\n"):
        # keep the string-literal case simple: the code we scan has no // in literals
        idx = line.find("//")
        out.append(line if idx < 0 else line[:idx])
    return "\n".join(out)


def read(p):
    if not p.exists():
        failures.append(f"missing file: {p.relative_to(ROOT)}")
        return ""
    return p.read_text()


def check(ok, message):
    global checks
    checks += 1
    if not ok:
        failures.append(message)


shell = strip_comments(read(SHELL))
ladder = strip_comments(read(LADDER))
reboot = strip_comments(read(REBOOT))
screen = strip_comments(read(SCREEN))


def fn_body(text, signature_regex, name):
    """Body of the first function matching the regex, by brace matching."""
    m = re.search(signature_regex, text)
    if not m:
        failures.append(f"{name}: signature not found")
        return ""
    i = text.find("{", m.end() - 1)
    if i < 0:
        failures.append(f"{name}: no body")
        return ""
    depth = 0
    for j in range(i, len(text)):
        if text[j] == "{":
            depth += 1
        elif text[j] == "}":
            depth -= 1
            if depth == 0:
                return text[i : j + 1]
    failures.append(f"{name}: unbalanced braces")
    return ""


# ---- 1. ready() must mean "root is held", not "a multiplexed shell is up" ----------
# Requiring session.alive made every root control on the tab read false on devices
# whose su refuses to stay on a pipe, which is most Magisk and KernelSU installs.
# ready() is a one-line expression body, so match the whole line rather than a brace block.
ready = ""
for line in shell.split("\n"):
    if re.search(r"fun ready\(\)\s*:", line):
        ready = line
        break
check(ready != "", "RootShell.ready(): could not read the definition")
check(
    "session" not in ready,
    "RootShell.ready() must not depend on the live session — one-shot root is root, "
    "and run() already falls back to `su -c`. A session check here disables the whole tab.",
)

# ---- 2. run() must keep a fallback for a grant held by another process -------------
run = fn_body(shell, r"fun run\(command: String,[^)]*\)\s*:\s*String\?", "RootShell.run")
check(
    "quick(" in run or "su -c" in run,
    "RootShell.run() lost its one-shot `su -c` fallback",
)
check(
    "com.gamershub.core.shizuku.Shell.exec" in run,
    "RootShell.run() must route a Shizuku-held grant through that shell; otherwise "
    "adoptShizukuRoot() sets READY and every command then fails.",
)
# …and the branch must actually be guarded on the Shizuku case, not merely contain the
# call somewhere a later edit could make unreachable.
check(
    'method.contains("Shizuku"' in run,
    "RootShell.run(): the Shizuku route must be selected by method == Shizuku (or by an "
    "absent su binary), not left in a branch that is never taken",
)

# ---- 3. the engine switch must honour the stored root opt-in ----------------------
# All four call sites passed wantRoot = false, so nothing in the app ever requested
# root as a consequence of the user opting in.
starts = []
for kt in SRC.rglob("*.kt"):
    for m in re.finditer(r"Engine\.start\((?:[^()]|\([^()]*\))*\)", strip_comments(kt.read_text())):
        starts.append((kt.relative_to(ROOT), m.group(0)))
check(len(starts) >= 3, f"expected several Engine.start() call sites, found {len(starts)}")
for path, call in starts:
    check(
        "wantRoot = false" not in call.replace(" ", " "),
        f"{path}: Engine.start() hardcodes wantRoot = false, so the opt-in is ignored",
    )
    check(
        "rootOptIn" in call or "wantRoot = true" in call or "wantRoot = true" in call,
        f"{path}: Engine.start() must pass the stored opt-in (Engine.rootOptIn(context))",
    )

# ---- 4. a climb must leave a usable session behind -------------------------------
climb = fn_body(ladder, r"fun climb\([^)]*\)\s*:\s*Result", "PrivilegeLadder.climb")
check("holdSession" in ladder, "PrivilegeLadder: holdSession parameter disappeared")
# every success path must hold when asked
returns = re.findall(r"return Result\([^)]*\)", climb)
check(
    len(returns) >= 4,
    f"PrivilegeLadder.climb(): expected several Result returns, found {len(returns)}",
)
# The already-ready rung specifically. It used to return "root" without ever asking for
# a session, so a grant that existed but had no multiplexed shell stayed unusable, and
# nothing downstream noticed because the rung said PERSISTENT_ROOT.
rung1 = ""
a, b = climb.find("if (RootShell.ready())"), climb.find("RootShell.acquire(")
if a >= 0 and b > a:
    rung1 = climb[a:b]
check(rung1 != "", "PrivilegeLadder.climb(): could not locate the already-ready rung")
check(
    "hold" in rung1,
    "PrivilegeLadder.climb(): the already-ready rung must honour holdSession — it used "
    "to return success without ever starting a session, leaving the grant unusable",
)

# ---- 5. a reboot must be issued after the teardown re-acquires privilege ----------
rrun = fn_body(reboot, r"(?s)fun run\(.*?\)\s*:\s*String", "Reboot.run")
if rrun:
    prep = rrun.find("prepare()")
    hold = rrun.find("holdRoot()")
    check(
        prep >= 0 and hold >= 0,
        "Reboot.run(): must call prepare() and then holdRoot() — prepare() ends in "
        "PrivilegeLadder.descend(), so without re-acquiring, the reboot is sent through "
        "a channel that was just closed",
    )
    if prep >= 0 and hold >= 0:
        check(
            hold > prep,
            "Reboot.run(): holdRoot() must come AFTER prepare(); before it, the teardown "
            "immediately drops what was just taken",
        )
        # the shell call must also come after the re-acquire
        send = rrun.find("shell(cmd")
        check(
            send > hold,
            "Reboot.run(): the reboot command must be sent after root is re-held",
        )

# ---- 6. the snapshot poll must survive a throw -----------------------------------
poll = fn_body(screen, r"val snap by produceState", "RootScreen snap poll")
check(poll != "", "RootScreen: could not locate the snapshot poll")
check(
    poll.count("runCatching") >= 1,
    "RootScreen: the snapshot poll is unguarded. One throw ends the coroutine and every "
    "control on the tab is frozen at its initial value for the rest of the process.",
)
# The guard has to wrap the Snapshot(...) construction itself; a runCatching somewhere
# else in the loop would let the throwing call through untouched.
_snap_at = poll.find("Snapshot(")
_guard_at = poll.find("runCatching")
check(
    0 <= _guard_at < _snap_at,
    "RootScreen: runCatching must wrap the Snapshot(...) construction, not something "
    "else in the poll loop",
)

# ---- 7. no root control may be silently unpressable -------------------------------
# A disabled row swallows the tap and reports nothing, which is indistinguishable from
# a broken button. Root absence must be reported, not assumed by the framework.
for m in re.finditer(r"clickable\(enabled\s*=\s*snap\.ready\)", screen):
    failures.append(
        "RootScreen: a control is gated with clickable(enabled = snap.ready) — it must "
        "stay tappable and explain the missing root instead of swallowing the tap"
    )
    checks += 1

# ---- 8. diagnose() must be able to explain a failure -----------------------------
diag = fn_body(shell, r"fun diagnose\([^)]*\)\s*:\s*List<String>", "RootShell.diagnose")
check(
    "State.READY" in diag,
    "RootShell.diagnose() must include a no-root section; gated on READY it was empty in "
    "exactly the case a user runs it",
)

# ---- 9. the tab must attempt root on its own ------------------------------------
check(
    "PrivilegeLadder.climb(" in screen,
    "RootScreen: nothing climbs the ladder",
)
check(
    screen.count("autoTried") >= 2,
    "RootScreen: the tab must make exactly one automatic probe on open; without it the "
    "tab sits at 'Root not granted' on a plainly rooted phone",
)
# …and that probe must not modify the device. Walking the whole ladder can install the
# temporary provider; opening a tab is not consent to write to the phone.
check(
    "RootShell.probe(" in screen,
    "RootScreen: the automatic attempt must use RootShell.probe(), not the full ladder — "
    "the ladder's later rungs install the temporary provider",
)
check(
    "provisionTemporary" not in screen,
    "RootScreen: the tab must not install the temporary provider as a side effect of "
    "being opened; that has to stay behind an explicit press",
)

# ---- report ---------------------------------------------------------------------
if failures:
    print(f"FAIL: {len(failures)} root-tab problem(s)")
    for f in dict.fromkeys(failures):
        print("  -", f)
    sys.exit(1)

print(f"PASS: {checks} root-tab checks")
