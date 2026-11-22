#!/usr/bin/env python3
"""Regression gate for the two privilege transports: wireless debugging and DFRoot.

Both shipped broken, and both broke in ways that look identical on screen ("it says
not paired" / "it says success and nothing happens"), so the checks here are specific:
a path computed in two places, a file written in one encoding and read in another, a
teardown that runs before the thing it protects.

Run: python3 tools/check_transports.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/gamershub"
JNI = ROOT / "app/src/main/jni"
LKM = ROOT / "lkm"

failures = []
checks = 0


def read(p):
    if not p.exists():
        failures.append(f"missing file: {p.relative_to(ROOT)}")
        return ""
    return p.read_text()


def strip_comments(text):
    """Code only: prose that *describes* a bug must never trip the check for it.

    Both comment forms: several of the checks look for a string that the comment
    explaining the fix necessarily contains.
    """
    # Block comments first, so a `//` inside one cannot split it.
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    out = []
    for line in text.split("\n"):
        idx = line.find("//")
        out.append(line if idx < 0 else line[:idx])
    return "\n".join(out)


def check(ok, message):
    global checks
    checks += 1
    if not ok:
        failures.append(message)


wireless = strip_comments(read(SRC / "core/adb/WirelessAdb.kt"))
manager = strip_comments(read(SRC / "core/adb/AdbManager.kt"))
shell = strip_comments(read(SRC / "core/shizuku/Shell.kt"))
dfrunner = strip_comments(read(SRC / "core/root/DfRootRunner.kt"))
dfpaths = strip_comments(read(SRC / "core/root/DfPaths.kt"))
exploit = strip_comments(read(ROOT / "app/src/main/java/df/root/ExploitRunner.kt"))
bootstrap = read(JNI / "bootstrap.c")
kernel = read(LKM / "dfroot.c")
cmake = read(JNI / "CMakeLists.txt")

# ============================ wireless debugging ==============================

# 1. Pairing must drop any previous connection before it runs. The vendored manager's
#    connect() returns false without dialing when it believes it is already connected,
#    and pairing leaves it believing exactly that — so a successful pair was followed by
#    a connect that reported "not paired".
pair_body = ""
m = re.search(r"fun pair\(", wireless)
if m:
    i = wireless.find("{", m.end() - 1)
    depth = 0
    for j in range(i, len(wireless)):
        if wireless[j] == "{":
            depth += 1
        elif wireless[j] == "}":
            depth -= 1
            if depth == 0:
                pair_body = wireless[i : j + 1]
                break
check(pair_body != "", "WirelessAdb.pair(): could not read the body")
check(
    "teardown" in pair_body,
    "WirelessAdb.pair() must tear the old connection down first: the manager skips "
    "connect() while it thinks it is connected, so pairing would succeed and the very "
    "next connect would fail without dialing.",
)

# 2. The identity adbd just authorised has to be the one connect() presents.
check(
    "AdbIdentity.certificate" in pair_body,
    "WirelessAdb.pair() must pin the certificate it paired with; a silent re-mint is "
    "indistinguishable from 'pairing does not work'.",
)

# 3. The certificate must be written as the DER it is.
check(
    "Base64" not in manager,
    "AdbManager: the certificate file must be written as DER. It used to be base64 "
    "text, which CertificateFactory rejects, so the repair path could never read the "
    "certificate adbd had already authorised.",
)
check(
    "File(context.filesDir, CERT_FILE).writeBytes(cert.encoded)" in manager,
    "AdbManager: expected the certificate to be stored as cert.encoded (DER bytes)",
)
check(
    "cert.encoded.inputStream" in manager or "inputStream()" in manager,
    "AdbManager: readCertificate must parse the stored DER via CertificateFactory",
)

# 4. The wireless transport must never be chosen without a context available.
check(
    "fun ctx()" in shell or "fun ctx(): Context?" in shell,
    "Shell: exec() needs a context for the wireless and daemon transports. It used to "
    "read appContext, which only ensure() ever set, so a live wireless connection "
    "answered null.",
)
check(
    "appContext ?: return null" not in shell,
    "Shell: returning null when appContext is unset silently disables a working "
    "wireless connection — resolve it from the application object instead.",
)

# 5. The nearby-devices permission has to be requested, not just declared.
manifest = read(ROOT / "app/src/main/AndroidManifest.xml")
check(
    "NEARBY_WIFI_DEVICES" in manifest,
    "manifest: NEARBY_WIFI_DEVICES must be declared for mDNS discovery on Android 13+",
)
check(
    "NEARBY_WIFI_DEVICES" in read(SRC / "ui/sheets/WirelessAdbSheet.kt"),
    "WirelessAdbSheet: the permission must actually be requested at runtime",
)

# ================================== DFRoot ===================================

# 6. The staging path must have exactly one owner.
check(
    "filesDir.parentFile" not in exploit,
    "ExploitRunner: the staging path must come from DfPaths, not filesDir.parentFile — "
    "the latter is /data/user/0/<pkg> on most installs, while the module execs a "
    "device-protected path, and the two silently disagree.",
)
check(
    "ExploitRunner.run(deContext" not in dfrunner,
    "DfRootRunner: ExploitRunner must not be handed the device-protected context — it "
    "stages into DfPaths' dir and reads nativeLibraryDir, both real-install properties",
)
check(
    "filesDir.parentFile" not in dfrunner,
    "DfRootRunner: bootstrapPath must come from DfPaths, not filesDir.parentFile",
)
check(
    "fun payloadDir" in dfpaths,
    "DfPaths: expected a single payloadDir() owning the staged-executable location",
)

# 7. The payload must declare the path it was compiled for, and the app must compare.
check(
    "libdfroot-path.so" in cmake,
    "CMake: the payload must emit a layout marker (libdfroot-path.so) carrying the "
    "compiled staging path; without it the app cannot tell a good payload from a "
    "mismatched one except by guessing.",
)
check(
    "layoutMatches" in dfpaths,
    "DfPaths: expected layoutMatches() to compare the app's path with the payload's",
)
check(
    "layoutMatches" in dfrunner,
    "DfRootRunner.payloadPresent() must compare against the payload's declared path "
    "instead of re-deriving the expected one and refusing valid payloads.",
)
check(
    'expectedParent = "/data/user_de/0/$PAYLOAD_PACKAGE"' not in dfrunner,
    "DfRootRunner: the hard-coded expectedParent comparison is the bug — it compared "
    "the compiled path against createDeviceProtectedStorageContext(), which frequently "
    "returns the credential-encrypted dir, so a valid payload was refused.",
)

# 8. Four parties, one path: the native side must accept it as an argument.
check(
    "--payload-dir" in bootstrap and "--prefs" in bootstrap,
    "bootstrap.c: must accept --payload-dir and --prefs rather than relying only on "
    "compiled-in copies of paths that other code also computes",
)
check(
    "--payload-dir" in kernel,
    "lkm/dfroot.c: must pass the payload dir to bootstrap; it is the party that knows "
    "the exec path and is where the drift originated",
)
check(
    "dfroot-native.xml" in kernel and "dfroot-native.xml" in dfpaths,
    "the prefs file bootstrap parses must be the one DfPaths writes "
    "(dfroot-native.xml on both sides)",
)
check(
    "--payload-dir" in kernel and "%s/dfroot-native.xml" in kernel,
    "lkm/dfroot.c: expected the prefs path to be derived from the same payload dir",
)

# 9. A successful late-load is not a running su daemon.
check(
    "awaitSu" in dfrunner,
    "DfRootRunner.run(): must wait for su to answer after a successful late-load. "
    "Returning immediately made ADOPT ROOT fail with 'no rung answered', which reads "
    "as 'DFRoot did not work'.",
)
run_body = ""
m = re.search(r"fun run\(", dfrunner)
if m:
    i = dfrunner.find("{", m.end() - 1)
    depth = 0
    for j in range(i, len(dfrunner)):
        if dfrunner[j] == "{":
            depth += 1
        elif dfrunner[j] == "}":
            depth -= 1
            if depth == 0:
                run_body = dfrunner[i : j + 1]
                break
check(
    "awaitSu(context" in run_body,
    "DfRootRunner.run(): awaitSu must be called on the success path",
)

# 10. The status panel must describe the embedded payload, not ask for an install.
dfroot = strip_comments(read(SRC / "core/root/DfRoot.kt"))
check(
    "DFRoot is not installed" not in dfroot,
    "DfRoot.status(): the payload is embedded, so the panel must not tell users to "
    "install the standalone DFRoot app",
)
check(
    "payloadPresent(context)" in dfroot,
    "DfRoot.status(): must report whether the embedded payload can actually run",
)

# ================================== report ===================================
if failures:
    print(f"FAIL: {len(failures)} transport problem(s)")
    for f in dict.fromkeys(failures):
        print("  -", f)
    sys.exit(1)

print(f"PASS: {checks} transport checks")
