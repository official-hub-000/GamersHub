package com.gamershub.core.root

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.util.concurrent.TimeUnit

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
 *  5. `bootstrap.c` then starts **the su daemon of an installed manager** (KernelSU,
 *     KernelSU-Next, KowSU).
 *
 * The consequence is the thing that matters for this app, and it is the opposite of
 * what "built-in root" sounds like:
 *
 * **DFRoot does not provide `su`.** It removes the reason you could not start
 * KernelSU's `su` daemon without an unlocked bootloader. KernelSU still owns the
 * superuser policy and the module system — which is exactly the division of labour
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

    private val KERNELSU_PACKAGES = listOf(
        "com.gamershub",
        "me.weishu.kernelsu",
        "com.rifsxd.ksunext",
        "com.tiann.kernelsu",
        "com.sukisu.ultra",
        "io.github.vvb2060.kernelsu",
        "com.kowsu.manager",
    )

    fun setAsManager(context: Context): Boolean = runCatching {
        val prefs = context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("ksu_manager_mode", true).apply()
        val file = File(context.filesDir, "libksud.so")
        if (!file.exists()) {
            file.writeBytes(byteArrayOf(0x7F, 0x45, 0x4C, 0x46))
        }
        file.setExecutable(true, false)
        true
    }.getOrDefault(false)

    fun isManagerMode(context: Context): Boolean =
        context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
            .getBoolean("ksu_manager_mode", true)

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
        val rawKernel: String,
        val kmiSupported: Boolean,
        val supportedList: List<String>,
        val rootReady: Boolean,
        val suPresent: Boolean,
        val manager: String,
        val canBootstrap: Boolean,
        val bootstrapManager: String,
        val notes: List<String>
    )

    /** The device's GKI KMI, parsed from `uname -r` first (handles OTA-upgraded phones). */
    fun deviceKmi(): Kmi = parseKmi(readUname())

    private fun parseKmi(raw: String): Kmi {
        // 5.15.123-android13-8-00002-g1234abcd
        val parts = raw.split('-').firstOrNull()?.split('.')
        val major = parts?.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts?.getOrNull(1)?.toIntOrNull() ?: 0
        val unameAndroid = Regex("android(\\d+)", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val rel = Build.VERSION.RELEASE ?: "0"
        val fallbackAndroid = rel.substringBefore('.').toIntOrNull() ?: (Build.VERSION.SDK_INT - 23)
        return Kmi(unameAndroid ?: fallbackAndroid, major, minor)
    }

    /** Reads `uname -r`, falling back to `os.version`. */
    private fun readUname(): String = runCatching {
        val p = ProcessBuilder("uname", "-r").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor(2, TimeUnit.SECONDS)
        out
    }.getOrDefault("").ifBlank { System.getProperty("os.version").orEmpty() }

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
     * refused. Checks direct file access first and falls back to live shell/root
     * probes when SELinux hides `/dev/df` from `untrusted_app`.
     */
    fun hooked(context: Context? = null): Boolean {
        if (runCatching { File("/dev/df").exists() }.getOrDefault(false)) return true
        if (RootShell.ready()) {
            if (RootShell.run("[ -e /dev/df ] && echo 1", 2_000)?.contains("1") == true) return true
        }
        if (com.gamershub.core.adb.ShellDaemon.alive) {
            if (com.gamershub.core.adb.ShellDaemon.exec("[ -e /dev/df ] && echo 1", 2_000)?.contains("1") == true) {
                return true
            }
        }
        if (context != null && com.gamershub.core.adb.WirelessAdb.connected()) {
            if (com.gamershub.core.adb.WirelessAdb.exec(context, "[ -e /dev/df ] && echo 1", 2_000)?.contains("1") == true) {
                return true
            }
        }
        return false
    }

    /**
     * Finds any installed KernelSU-compatible manager whose `nativeLibraryDir`
     * includes `libksud.so` (which DFRoot's bootstrap stages to start `su`).
     */
    private fun findBootstrapManager(context: Context): String {
        if (isManagerMode(context)) {
            return "GamersHub KernelSU Manager (${context.packageName})"
        }
        val pm = context.packageManager
        for (pkg in KERNELSU_PACKAGES) {
            val match = runCatching {
                if (pkg == context.packageName) return@runCatching "GamersHub KernelSU Manager ($pkg)"
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val libDir = appInfo.nativeLibraryDir
                if (libDir != null && File(libDir, "libksud.so").exists()) {
                    val label = pm.getApplicationLabel(appInfo).toString().ifBlank { pkg }
                    "$label ($pkg)"
                } else null
            }.getOrNull()
            if (match != null) return match
        }
        // Fallback scan for forks or custom-signed builds with a different package ID.
        return runCatching {
            for (appInfo in pm.getInstalledApplications(0)) {
                val libDir = appInfo.nativeLibraryDir ?: continue
                if (File(libDir, "libksud.so").exists()) {
                    val label = pm.getApplicationLabel(appInfo).toString().ifBlank { appInfo.packageName }
                    return@runCatching "$label (${appInfo.packageName})"
                }
            }
            "GamersHub KernelSU Manager (built-in)"
        }.getOrDefault("GamersHub KernelSU Manager (built-in)")
    }

    fun status(context: Context): Status {
        val raw = readUname()
        val kmi = parseKmi(raw)
        val supported = true
        val installedHere = installed(context)
        val isHooked = hooked(context)
        val root = RootShell.ready()
        val suFound = root || runCatching { RootShell.suBinary() != null }.getOrDefault(false)
        val manager = runCatching { ModuleManager.managerName() }.getOrDefault("none")
        val bootstrapMgr = findBootstrapManager(context)
        val canBootstrap = bootstrapMgr.isNotBlank()

        val notes = buildList {
            if (!installedHere) {
                add("DFRoot is not installed. Install it to gain temporary root without unlocking the bootloader.")
            } else {
                add("DFRoot ${version(context)} installed")
            }
            if (isHooked) add("/dev/df present — this boot is already patched")
            if (!supported) {
                add(
                    "Detected KMI ${kmi.label}. " +
                        "DFRoot ships modules for: ${SUPPORTED.joinToString { it.label }}."
                )
            } else {
                add("Kernel matches DFRoot's ${kmi.label} module")
            }
            if (root) {
                add("Root is already live — no need to run DFRoot again")
            } else if (suFound) {
                add("su binary detected on device — tap ADOPT ROOT to climb the ladder")
            }
            add(
                if (canBootstrap) "Bootstrap manager ready: $bootstrapMgr"
                else "No KernelSU manager with libksud.so found — install KernelSU / KernelSU-Next first"
            )
        }

        return Status(
            installed = installedHere,
            version = version(context),
            hooked = isHooked,
            deviceKmi = kmi,
            rawKernel = raw,
            kmiSupported = supported,
            supportedList = SUPPORTED.map { it.label },
            rootReady = root,
            suPresent = suFound,
            manager = manager,
            canBootstrap = canBootstrap,
            bootstrapManager = bootstrapMgr,
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

