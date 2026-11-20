# Third-party code

## Vendored: ADB client (`app/src/main/java/io/github/muntashirakon/adb`)

**Source:** https://github.com/MuntashirAkon/libadb-android (v3.1.1)
**Dual-licensed:** `GPL-3.0-or-later OR Apache-2.0` — used here under **Apache 2.0**.
**Used for:** the Wireless debugging transport — the ADB protocol, the TLS pairing
tunnel, and the mDNS discovery helpers.

Why it is vendored rather than declared as a dependency: the published artifacts
(`io.github.muntashirakon:libadb` on Maven Central, `com.github.MuntashirAkon:libadb-android`
on JitPack) did not resolve when this was written, and an unresolvable dependency is a
broken release. The source is small and self-contained, so it is compiled here.

## Vendored: SPAKE2 (`io/github/muntashirakon/crypto/{spake2,ed25519,x25519}`)

**Source:** https://github.com/MuntashirAkon/spake2-java (v2.2.1), pure-Java module.
**Licensed:** LGPL-3.0. Used unmodified, as a separate module in the same application,
which is the "work that uses the library" case — the rest of GamersHub is not derived
from it and its source is available at the URL above.
**Used for:** the SPAKE2 PAKE that ADB pairing runs inside its TLS tunnel, keyed by the
six-digit code. There is no JCA primitive for this.

## Runtime dependencies added

| Artifact | Licence | Why |
|---|---|---|
| `org.bouncycastle:bcprov-jdk15to18:1.81` | MIT-style (Bouncy Castle) | AES-GCM, HKDF-SHA256 and SHA-256 for the pairing tunnel. Not reachable through JCA on Android. |

## Not used, and why it matters

The certificate for pairing is **not** taken from `sun-security-android` or apksig.
`sun.security.x509` does not exist on Android, and apksig's API could not be verified
against a real artifact from the build environment. `core/adb/AdbCert.kt` writes the one
self-signed X.509 certificate this app needs with a small DER encoder, and its output was
validated with `openssl x509` and `openssl verify` during development.

## Vendored: DFRoot — temporary root payload (`app/src/main/jni/*.c`, `*.S`, `lkm/`, `app/src/main/java/df/root/`)

**Source:** https://github.com/diabl0w/DFRoot (v4.7, commit as of 2026-10-22)
**Licence:** GPL-2.0 (see `licenses/DFRoot-LICENSE.txt`). Verbatim, except one line in
`ExploitRunner.java` that referenced DFRoot's own `BuildConfig` and now reports the
GamersHub version. The per-KMI kernel modules (`lkm/dfroot.c`) are built in CI from the
vendored source inside the same `ghcr.io/ylarod/ddk-min:<kmi>` containers DFRoot uses,
and compiled into `libdfroot.so`; they are never committed (`app/src/main/jni/ko/` is a
build artifact).

**Used for:** the in-app temporary-root path in Root ▸ Temporary Root. Pressing
**Run DFRoot** executes the exploit on this device for this boot only, exactly as the
DFRoot app does. Nothing about the exploit is altered, weakened or retargeted — no
persistence, no spread, no remote access.

**Consequence of GPL-2.0:** the distributed APK is a combined work that includes
GPL-2.0 code, so the combined work must be distributed under GPL-2.0 terms with source
available. GamersHub's source is public on GitHub, which satisfies this. Repackaging
the APK outside the repository breaks it.
