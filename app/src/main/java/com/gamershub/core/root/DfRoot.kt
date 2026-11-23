package com.gamershub.core.root

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * DFRoot integration — temporary root, honestly.
 *
 * ## What DFRoot actually is (read from its source, not its README)
 *
 * DFRoot is an exploit chain, not a root manager. Concretely, from `app/src/main/jni`:
 *
 *  1. `exp.c` builds an AES-CBC/HMAC-SHA256 ESP transform over a `UdpEncapsulationSocket`.
 *  2. `splicehelper.c` is spliced into `crash_dump64` to reach `vendor_file`-labelled
 *     files that an untrusted app cannot open.
 *  3. `libcxx.S` patches shellcode into `libc++.so` at `std::ostream::sentry::sentry`.
 *     An orphaned grandchild gets reaped by init, so the hook fires as uid 0 tid 1.
 *  4. That shellcode `insmod`s a kernel module — **one prebuilt `.ko` per KMI** —
 *     which writes `false` to `selinux_state` and calls `call_usermodehelper`.
 *  5. `bootstrap.c` runs as uid 0 and then does two things: it execs `ksud late-load`
 *     (KernelSU's userspace backend, so its module stages run and its manager UI can be
 *     opened), and it **publishes a uid-0 command socket** which this app talks to
 *     through [RootBridge].
 *
 * The consequence is the thing that matters for this app, and it is the opposite of
 * what "built-in root" sounds like:
 *
 * **DFRoot does not provide `su`, and on a KernelSU kernel nothing can.** KernelSU has no
 * su file at all: `kernel/feature/sucompat.c` intercepts execve for the literal path
 * `/system/bin/su` and substitutes `/data/adb/ksud`, but only for uids the kernel already
 * allows, and the one uid exempt from the allowlist is the manager package compiled into
 * the `.ko` (`me.weishu.kernelsu`, from `kernel/Kbuild`). GamersHub is neither, so
 * `su -c id` returns `su: inaccessible or not found` no matter what `ksud` started. The
 * socket is the root, which is why the UI says "per boot" and the ladder reports
 * DFROOT_ROOT rather than pretending a su install exists — which is exactly the division of labour
 * the two-layer model assumes.
 *
 * ## Why the engine is not compiled into this APK
 *
 * The `.ko` files are **not in DFRoot's repository** — `.gitignore` lists
 * `app/src/main/jni/ko/` (one .ko per KMI) — and they are not in its release APK either (v4.7 is
 * 84 KB). They are built per-KMI by `podman run ghcr.io/ylarod/ddk-min:<kmi>` in
 * DFRoot's `Makefile`, then `.incbin`'d into `libdfroot.so`.
 *
 * So embedding it would mean shipping eight prebuilt kernel modules built against
 * somebody else's kernel trees, from a third-party container image, updated whenever
 * a kernel drops. That is not something that can be verified in this repo's build,
 * and a wrong `.ko` is a bootloop rather than a failed feature.
 *
 * ## What this class does instead — and it is a real integration
 *
 *  - **Detects** DFRoot, its version, and whether `/dev/df` already exists (hooked).
 *  - **Checks the KMI** against DFRoot's supported list *before* the user tries, so
 *    "your kernel is not supported" is answered up front instead of by a failed run.
 *  - **Launches** DFRoot for the user and comes back.
 *  - **Adopts the result**: after DFRoot bootstraps the manager, the privilege
 *    ladder's `su` rung finds it, and the whole manager layer — superuser policy,
 *    module zips, enable/disable — comes up with it.
 *  - **Releases it**: a root obtained this way is session-scoped in this app, so
 *    [PrivilegeLadder.descend] drops it with the engine like every other rung.
 */
object DfRoot {

    const val PKG = "df.root"

    /** One of DFRoot's per-KMI kernel modules. */
    data class Kmi(val android: Int, val major: Int, val minor: Int) {
        val label: String get() = "android$android-$major.$minor"
    }

    /** Exactly the KMIs DFRoot's Makefile builds for. */
    private val SUPPORTED = listOf(
        Kmi(12, 5, 10), Kmi(13, 5, 10), Kmi(13, 5, 15), Kmi(14, 5, 15),
        Kmi(14, 6, 1), Kmi(15, 6, 6), Kmi(16, 6, 12), Kmi(17, 6, 18)
    )

    data class Status(
        val installed: Boolean,
        val version: String,
        val hooked: Boolean,
        val deviceKmi: Kmi,
        val kmiSupported: Boolean,
        val supportedList: List<String>,
        val rootReady: Boolean,
        val manager: String,
        val canBootstrap: Boolean,
        val notes: List<String>
    )

    /**
     * The device's KMI.
     *
     * A KMI is a *kernel* interface contract, not an OS version. GKI kernels are branded
     * with it in their release string — `5.15.123-android13-8-00002-g1234abcd` is
     * android13-5.15 — and that brand is what DFRoot's module must match, because the
     * module is built against that ABI and that ABI alone.
     *
     * This used to take the Android number from `Build.VERSION.RELEASE`, which is the OS
     * the user is running, not the ABI their kernel was compiled for. Those are routinely
     * different: vendors ship Android 16 on a 5.15 kernel, which is a GKI kernel branded
     * android13. The old code computed `android16-5.15` for such a device, reported
     * "kernel not supported", and refused to run — while the device's real KMI,
     * android13-5.15, is in the supported list. The refusal was the bug.
     */
    fun deviceKmi(): Kmi {
        val (major, minor, branded) = kernelInfo()
        // The kernel's own brand wins. Only if the release string carries none — a vendor
        // kernel without GKI branding — fall back to the OS version, and say so in the
        // status notes rather than presenting it as fact.
        val android = branded ?: (Build.VERSION.RELEASE ?: "0")
            .substringBefore('.').toIntOrNull()
            ?: (Build.VERSION.SDK_INT - 23)
        return Kmi(android, major, minor)
    }

    /** True when the kernel release string named its own KMI, rather than us guessing. */
    fun kmiIsBranded(): Boolean = kernelInfo().third != null

    /**
     * `uname -r` → (major, minor, android brand).
     *
     * `5.15.123-android13-8-00002-g1234abcd` → (5, 15, 13). The brand is the digits after
     * `-android`, which is how GKI names the ABI. Null for it when absent.
     */
    private fun kernelInfo(): Triple<Int, Int, Int?> {
        val raw = runCatching {
            val p = ProcessBuilder("uname", "-r").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            out
        }.getOrDefault("")
        val parts = raw.split('-').firstOrNull()?.split('.')
        val major = parts?.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts?.getOrNull(1)?.toIntOrNull() ?: 0
        val brand = Regex("-android(\\d+)").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return Triple(major, minor, brand)
    }

    fun installed(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PKG, 0)
        true
    }.getOrDefault(false)

    fun version(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(PKG, 0).versionName ?: "?"
    }.getOrDefault("")

    /**
     * DFRoot's own "already hooked" sentinel.
     *
     * `/dev/df` is created by the shellcode as a one-shot mutex, so its presence means
     * this boot has already been patched — running the exploit again would just be
     * refused.
     */
    fun hooked(): Boolean = runCatching { java.io.File("/dev/df").exists() }.getOrDefault(false)

    fun status(context: Context): Status {
        val kmi = deviceKmi()
        val supported = SUPPORTED.any { it == kmi }
        val installedHere = installed(context)
        val root = RootShell.ready()
        val manager = runCatching { ModuleManager.managerName() }.getOrDefault("none")
        // DFRoot's bootstrap stages libksud.so out of the manager's nativeLibraryDir,
        // so a manager without it cannot be started by the exploit. This asks the same
        // question the picker asks, for every installed manager — probing a single
        // hard-coded package reported "not bootstrap-capable" on KernelSU-Next, KowSU
        // and Samsung forks, which are all listed as supported upstream.
        // The bundled backend counts as bootstrap-capable on its own: with `ksud` inside
        // this APK the exploit has what it execs, and no manager app is required.
        val canBootstrap = runCatching {
            DfRootRunner.candidates(context).isNotEmpty() ||
                DfRootRunner.selfContained(context)
        }.getOrDefault(false)
        val selfContained = runCatching { DfRootRunner.selfContained(context) }
            .getOrDefault(false)

        val notes = buildList {
            // The payload is embedded — it has been since the LKM build was wired into CI —
            // so the old "DFRoot is not installed, install it" line was telling users to
            // install an app they do not need while the exploit sat unused in their APK.
            if (DfRootRunner.payloadPresent(context)) {
                add("DFRoot 4.7 payload embedded in this build (arm64)")
            } else {
                add("The embedded DFRoot payload cannot run on this install: " +
                    DfRootRunner.payloadProblem(context))
            }
            if (installedHere) add("The standalone DFRoot ${version(context)} app is also installed")
            if (hooked()) add("/dev/df present — this boot is already patched")
            if (!supported) {
                add(
                    "Your kernel is ${kmi.major}.${kmi.minor} on Android ${kmi.android}. " +
                        "DFRoot ships modules for: ${SUPPORTED.joinToString { it.label }}."
                )
            } else {
                add("Kernel matches DFRoot's ${kmi.label} module")
            }
            if (root) add("Root is already live — no need to run DFRoot again")
            add(
                when {
                    selfContained ->
                        "KernelSU backend (ksud) is bundled — no separate manager app is " +
                            "needed for the module stages, but root reaches GamersHub " +
                            "through DFRoot's uid 0 session, not through su"
                    canBootstrap -> "SU manager found and bootstrap-capable"
                    else ->
                        "No bootstrap-capable SU manager found — install KernelSU, " +
                            "KernelSU-Next or KowSU first. KernelSU's backend (ksud) is " +
                            "bundled here, but its superuser policy only answers its own " +
                            "manager app: GamersHub gets root from DFRoot's uid 0 session"
                }
            )
        }

        return Status(
            installed = installedHere,
            version = version(context),
            hooked = hooked(),
            deviceKmi = kmi,
            kmiSupported = supported,
            supportedList = SUPPORTED.map { it.label },
            rootReady = root,
            manager = manager,
            canBootstrap = canBootstrap,
            notes = notes
        )
    }

    /**
     * Opens DFRoot so the user can run the exploit.
     *
     * It cannot be run from here: the chain needs DFRoot's own `libdfroot.so` in
     * *its* `nativeLibraryDir` and *its* uid, because the IpSec transform and the
     * `splice()` bridge are tied to the process that created them. Starting its
     * activity is the only supported entry point, and pretending otherwise would be a
     * button that fails.
     */
    fun launch(context: Context): Boolean = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(PKG)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    fun isInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getApplicationInfo(PKG, PackageManager.GET_META_DATA)
            true
        }.getOrDefault(false)
}
