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

# ============================ transport parity ===============================
# "The same features as Shizuku" is a testable property even though the transports are
# different: every call site goes through one surface, and that surface must behave
# identically no matter which transport answers.

channel = strip_comments(read(SRC / "core/Channel.kt"))

# 11. A caller's timeout must reach the transport. Channel.exec used to forward it to
#     root only, so an 8 s request became the transport's own default elsewhere.
check(
    "Shell.exec(command, timeoutMs)" in channel,
    "Channel.exec() must forward its timeoutMs to Shell.exec on the daemon, wireless and "
    "Shizuku branches; discarding it makes the same command behave differently per "
    "transport.",
)

# 12. A missing exit code is a failure, not a success.
check(
    'find(out) ?: return true' not in channel,
    "Channel.ok() must not treat a missing __GH_EXIT__ sentinel as success — that is "
    "exactly what a timed-out command looks like.",
)
check(
    'find(out) ?: return false' in channel,
    "Channel.ok() must require the exit code",
)
ok_body = ""
m = re.search(r"fun ok\(", shell)
if m:
    i = shell.find("{", m.end() - 1)
    depth = 0
    for j in range(i, len(shell)):
        if shell[j] == "{":
            depth += 1
        elif shell[j] == "}":
            depth -= 1
            if depth == 0:
                ok_body = shell[i : j + 1]
                break
check(ok_body != "", "Shell.ok(): could not read the body")
# Must be the exit-code lookup specifically. "?: return false" alone also matches the
# `exec(...) ?: return false` line above it, which made this rule pass on the exact bug
# it exists to catch.
check(
    re.search(r"find\(out\)\s*\?:\s*return false", ok_body) is not None,
    "Shell.ok() must require the exit code: a missing __GH_EXIT__ sentinel is exactly "
    "what a timed-out command looks like, and returning true reported it as success.",
)
check(
    re.search(r"find\(out\)\s*\?:\s*return true", ok_body) is None,
    "Shell.ok() must not default a missing exit code to success",
)

# 13. One default timeout for every transport.
check(
    "DEFAULT_TIMEOUT_MS" in shell,
    "Shell: expected a single DEFAULT_TIMEOUT_MS so no transport has its own hard-coded "
    "cut-off (Shizuku's was 1.5 s while wireless was 12 s)",
)
check(
    "join(1500)" not in shell,
    "Shell: the hard-coded 1.5 s join cut long commands off on the Shizuku path only",
)

# 14. Wireless must recover like Shizuku does.
check(
    "private fun reconnect(" in wireless,
    "WirelessAdb: must reconnect when a stream dies. Shizuku rebinds on its next call; "
    "this transport used to mark itself OFF and answer null for the rest of the process.",
)
exec_body = ""
m = re.search(r"fun exec\(context: Context", wireless)
if m:
    i = wireless.find("{", m.end() - 1)
    depth = 0
    for j in range(i, len(wireless)):
        if wireless[j] == "{":
            depth += 1
        elif wireless[j] == "}":
            depth -= 1
            if depth == 0:
                exec_body = wireless[i : j + 1]
                break
check(
    "if (!connected() && !reconnect(context)) return null" in exec_body,
    "WirelessAdb.exec() must attempt a reconnect in its entry guard — merely calling "
    "reconnect() somewhere later still answers null for every caller once the link drops.",
)
check(
    "RECONNECT_BACKOFF_MS" in wireless,
    "WirelessAdb: a reconnect storm on a dead network is worse than the drop — expected "
    "a backoff constant",
)

# 15. A live connection must leave a service behind that does not need it.
check(
    "ensureDaemon(context)" in wireless,
    "WirelessAdb: a successful connect must CALL ensureDaemon(). Without it, privilege "
    "ends when the connection does — which is the one architectural property Shizuku "
    "has that a bare ADB link does not.",
)
check(
    re.search(r"ensureDaemon\(context\)\s*\n\s*return Triple\(true", wireless) is not None,
    "WirelessAdb: ensureDaemon must be called on the success path of tryPort(), right "
    "before the successful result is returned",
)
check(
    "daemon_enabled" in wireless,
    "WirelessAdb.ensureDaemon must respect the user's daemon_enabled choice",
)

# 16. Status must not claim "Shizuku" on devices that have never had it.
check(
    '"Shizuku not running"' not in shell,
    "Shell.statusLabel() must name the live transport instead of reporting Shizuku on a "
    "device driven by wireless debugging, the daemon or root",
)
check(
    "fun running()" in shell and "RootShell.ready()" in shell,
    "Shell.running() must include root; it is a transport like the others",
)

# 12b. Root's ok() must agree — it is the fourth transport.
rootshell = strip_comments(read(SRC / "core/root/RootShell.kt"))
check(
    re.search(r"find\(out\)\s*\?:\s*return true", rootshell) is None,
    "RootShell.ok() must not default a missing exit code to success — it is the fourth "
    "transport and has to behave like the other three",
)

# 17. Every transport that carries a command must be reachable from the same surface.
for name, needle in (
    ("root", "RootShell.run"),
    ("daemon", "ShellDaemon.exec"),
    ("wireless", "WirelessAdb.exec"),
):
    check(
        needle in shell,
        f"Shell.exec must route to the {name} transport",
    )

# 18. The staging dir must be device-protected.
#     applicationInfo.dataDir is /data/user/0 (credential-encrypted) and disagrees with
#     the path the kernel module execs, so using it made every DFRoot run refuse.
check(
    "createDeviceProtectedStorageContext" in dfpaths,
    "DfPaths.payloadDir must resolve device-protected storage; applicationInfo.dataDir is "
    "credential-encrypted (/data/user/0) and disagrees with the payload's compiled path, "
    "which makes layoutMatches() false and DFRoot refuse on every device",
)
check(
    "fun payloadDir(context: Context): File = dataDir(context)" not in dfpaths,
    "DfPaths.payloadDir must not delegate straight to dataDir — that is the regression",
)

# 19. The TLS context caches the key it was built with.
sslutils = read(ROOT / "app/src/main/java/io/github/muntashirakon/adb/SslUtils.java")
check(
    "public static synchronized void reset()" in sslutils,
    "SslUtils must expose reset(): it caches the SSLContext built from one key, so after "
    "an identity reset every handshake still presents the old certificate",
)
check(
    "SslUtils.reset()" in manager,
    "AdbManager.resetIdentity must clear the cached SSLContext, or pairing presents the "
    "pre-reset certificate while connect presents the new one",
)

# 20. The device must be able to report what its storage layout actually is.
check(
    "object StorageLayoutCheck" in strip_comments(
        read(SRC / "core/root/StorageLayoutCheck.kt")
    ),
    "StorageLayoutCheck is missing: path checks that only run at build time cannot see "
    "whether device-protected storage exists or is writable on the running device",
)
check(
    "gh-layout-probe" in read(SRC / "core/root/StorageLayoutCheck.kt"),
    "StorageLayoutCheck must actually write a file at the staging path; anything weaker "
    "can report a layout as usable when the payload cannot be staged",
)

# 21. Every failure must be photographable.
check(
    "fun diagnostics(" in wireless,
    "WirelessAdb must expose a stage-by-stage diagnostics() — every failure in this "
    "subsystem is identical on screen, and guessing at it has cost four releases",
)
check(
    "WirelessAdb.diagnostics(context)" in read(SRC / "ui/sheets/WirelessAdbSheet.kt"),
    "the wireless sheet must display WirelessAdb.diagnostics(context)",
)

# 22. The marker must carry the path, and the build must not rely on a shell.
#     Three separate attempts shipped a marker with no path in it, and an empty marker makes
#     layoutMatches() false, which makes DFRoot refuse to run on every device. All three had
#     the same root cause: assuming a redirect or a foreign binary could write the file.
check(
    'file(WRITE "${MARKER_OUT}" "${PAYLOAD_DIR}/bootstrap' in cmake,
    "the layout marker must be written whole by file(WRITE): add_custom_command does not "
    "run through a shell, so '>' and '>>' become literal arguments and the marker ends up "
    "empty while the build still succeeds",
)
check(
    "marker-head.bin" not in cmake,
    "the marker must not be assembled from parts — every attempt at appending without a "
    "shell silently produced an empty file",
)
check(
    "pathmarker_tool" not in cmake,
    "the marker must not be written by a cross-compiled target: an arm64 binary cannot run "
    "on the x86 build host",
)
# strip #-comments first: the explanation above this rule quotes the broken commands.
cmake_code = "\n".join(
    ln for ln in cmake.splitlines() if not ln.lstrip().startswith("#")
)
check(
    "-E cat" not in cmake_code,
    "cmake -E cat cannot append; combining the marker from parts is what shipped it empty",
)
check(
    "libdfroot-path.so" in cmake,
    "the marker file must still be installed into the library output directory",
)
# The reader must scan for the absolute path, not read the file as a whole.
check(
    "/data/[A-Za-z0-9_./-]+/bootstrap" in dfpaths,
    "DfPaths.compiledBootstrapPath must scan the marker for an absolute path; comparing "
    "the whole trimmed file cannot match once the marker carries anything else",
)

# 23. The KMI must come from the kernel, not the OS version.
#     A KMI is a kernel ABI brand, not an Android version. Taking the Android number from
#     Build.VERSION.RELEASE computed android16-5.15 for a device whose real KMI is
#     android13-5.15, reported "kernel not supported", and refused to run DFRoot on a
#     device that is in the supported list.
dfroot = strip_comments(read(SRC / "core/root/DfRoot.kt"))
check(
    'Regex("-android' in dfroot,
    "DfRoot.deviceKmi must read the GKI brand from the kernel release string; an OS "
    "version is not a KMI and vendors ship new Android on old GKI kernels",
)
# The brand must be the PRIMARY source. Two independent statements: the brand regex has
# to exist at all, and the OS version may only appear after it as a fallback. Checking that
# the old line is absent is not enough — deleting the brand entirely also removes it.
_body = dfroot.split("fun deviceKmi")[1].split("fun kmiIsBranded")[0]
# Strip comment lines: the doc comment above the function explains the format with real
# examples like "-android13-", which a naive substring check happily matches.
_body_code = "\n".join(
    ln for ln in _body.splitlines() if not ln.lstrip().startswith(("*", "//"))
)
check(
    'Regex("-android' in dfroot,
    "deviceKmi must read the GKI brand from the kernel release string: a KMI is a kernel "
    "ABI brand, not an OS version (this computed android16-5.15 on a real android13-5.15 "
    "device and refused to run DFRoot on a supported kernel)",
)
check(
    "Build.VERSION.RELEASE" not in _body or _body.find("branded ?:") < _body.find("Build.VERSION.RELEASE"),
    "deviceKmi must not use the OS version as its primary source, only as a fallback",
)

# 24. Module sources must point at branches that exist.
modstore = read(SRC / "core/root/ModuleStore.kt")
check(
    "ZG-R/master/json/modules.json" in modstore,
    "the ZG-R catalog must use the repository's default branch (master); main 404s",
)
check(
    "mmrl-repo/master/json/modules.json" in modstore,
    "the mmrl-repo catalog must use the repository's default branch (master); main 404s",
)
check(
    "take(180)" not in read(SRC / "ui/RootScreen.kt"),
    "source errors must not be truncated at a fixed length — it split a URL exactly where "
    "the branch was named",
)

# 25. No duplicated search row, no placeholder leaking into a sentence.
lobby = read(SRC / "ui/LobbyScreen.kt")
check(
    lobby.count("Search anything — feature, setting or help") == 1,
    "the Lobby search row is duplicated; two identical dead-looking fields appear on top "
    "of each other",
)
check(
    'Manager.UNKNOWN -> "root manager (unrecognised)"'
    not in read(SRC / "core/root/ModuleManager.kt"),
    "managerName must not return a parenthesis placeholder: it is interpolated into "
    "user-facing sentences",
)

# 26. A bundled backend must actually be used, and must never be required.
#     Two ways this goes wrong: the binary ships but nothing stages it (so the app still
#     demands a manager), or the code assumes it is present (so builds without it break).
check(
    "object BundledKsud" in read(SRC / "core/root/BundledKsud.kt"),
    "BundledKsud is missing: the bundled KernelSU backend must be extracted to the "
    "staging path, not just packaged",
)
check(
    "BundledKsud.stage(ctx)" in read(ROOT / "app/src/main/java/df/root/ExploitRunner.kt"),
    "ExploitRunner must stage the bundled backend when it is present",
)
cm = strip_comments(read(SRC / "core/root/DfRootRunner.kt"))
check(
    "BundledKsud.present(context)" in cm,
    "DfRootRunner must consult the bundled backend",
)
# The requirement is that selected() *falls back* to the bundled backend. Presence of
# the words anywhere is not that: the call site satisfies a naive search even when the
# fallback has been removed. Inspect the function body.
_sel = cm.split("fun selected(context: Context)")[1].split("fun selfContained")[0]
check(
    "bundledSource(context)" in _sel,
    "DfRootRunner.selected must fall back to the bundled backend, or a device with no "
    "manager installed still refuses to run DFRoot",
)
check(
    _sel.rstrip().rstrip("}").rstrip().endswith("bundledSource(context)"),
    "the bundled backend must be the fallback in selected() — reached only after an "
    "installed manager has been rejected",
)

# 27. A bundled backend must be optional at build time.
#     If the fetch step fails, the app must degrade to asking for an installed manager
#     rather than refusing to build or crashing at run time.
check(
    "getOrDefault(false)" in strip_comments(read(SRC / "core/root/BundledKsud.kt")),
    "BundledKsud.present must fail closed to false: a build without the backend still "
    "has to work",
)

# 28. Licence obligations are a gate, not a comment.
check(
    (ROOT / "tools/check_licenses.py").exists(),
    "tools/check_licenses.py is missing: bundling a GPL binary without its notice, "
    "licence text and source offer is a licence violation that nothing else catches",
)
check(
    "check_licenses.py" in read(ROOT / ".github/workflows/android-ci.yml"),
    "the licence gate must run in CI",
)

# ================================== report ===================================
if failures:
    print(f"FAIL: {len(failures)} transport problem(s)")
    for f in dict.fromkeys(failures):
        print("  -", f)
    sys.exit(1)

print(f"PASS: {checks} transport checks")
