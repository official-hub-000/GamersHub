# GamersHub — Release Report

**First version:** `v6.17.1`

| | |
|---|---|
| Release page | https://github.com/official-hub-000/GamersHub/releases/tag/v6.17.1 |
| APK | `GamersHub-v6.17.1.apk` (26,382,269 bytes) |
| Package | `com.gamershub` |
| Version | 6.17.1 (versionCode 85) |
| Min / target SDK | 26 / 34 |
| Type | **Release build** — signed, not debuggable |

## How it was built

Built on GitHub Actions (`.github/workflows/release.yml`) from tag `v6.17.1`,
not locally — the sandbox has no JDK or Android SDK, and outbound network is
limited to a small allowlist, so the compile had to run on the runner.

```
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=$PWD/gamershub.jks \
  -Pandroid.injected.signing.password=*** \
  -Pandroid.injected.signing.key.alias=gamershub \
  -Pandroid.injected.signing.key.password=***
```

The committed `keystore/gamershub.p12` is normalised to JKS by the workflow
first, so every future release is signed by the **same** certificate and
in-place updates keep app data and the Shizuku grant.

## Verification (automated gate, passed)

| Check | Result |
|---|---|
| `apksigner verify` | **Verifies** |
| Signature scheme | v2 (correct for `minSdk 26`; v1 only needed below 24) |
| Signers | 1 |
| Signer DN | `CN="GamersHub, OU=App, O=GamersHub, C=US"` |
| Signer SHA-256 | `463c72645dbdaaab2e096dc53a6e82bb99ff591eaab221ba30f3b611945c16e0` |
| Key | RSA 2048 |
| `aapt2 badging` | `com.gamershub` versionCode 85, versionName 6.17.1 |
| Debuggable flag | **absent** |
| Dex | `classes.dex`, `classes2.dex`, `classes3.dex` |

The gate is now a permanent part of the workflow: a build that is unsigned,
signed with the wrong key, or debuggable **fails the run** instead of being
published.

## Fixes made to the release pipeline

1. `apksigner` was not found — the SDK `build-tools` directory is not on `PATH`
   on the runner; resolve the newest copy explicitly.
2. The gate demanded a v1 (JAR) signature, which is wrong for `minSdk 26`.
3. The certificate digest was parsed with `Signer #1 …`, but apksigner emits
   `V2 Signer: certificate SHA-256 digest: …`, so the comparison always failed.
4. The report step switched the build workspace to an orphan branch and deleted
   the working tree, breaking the later "Rename APK" step. It now works in a
   throwaway clone.

## Repository state

- `main` → `7570c5f`
- `arena/b678a1fe-gamershub` → `7570c5f` (identical)
- Tag `v6.17.1` → `7570c5f`
- Stale auto-generated `nightly` prerelease removed

## Still outstanding

The repository you originally linked, `spideycandy973-web/GamersHub`, is
**read-only** for the connected `official-hub-000` account, so the build and
release were published to `official-hub-000/GamersHub`. To ship to
`spideycandy973-web/GamersHub`, connect that account or grant
`official-hub-000` write access.
