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
