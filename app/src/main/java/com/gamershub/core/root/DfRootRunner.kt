package com.gamershub.core.root

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper

/**
 * Kotlin bridge to the embedded DFRoot payload (GPL-2.0, vendored verbatim from
 * diabl0w/DFRoot — see THIRD-PARTY.md and licenses/DFRoot-LICENSE.txt).
 *
 * The payload ships inside this APK: `libdfroot.so` and `libbootstrap.so` are
 * *executables* named `.so` so the installer extracts them into `nativeLibraryDir`
 * (which is the one directory with the exec bit set), and they are authenticated
 * per-KMI by the `.ko` modules that are compiled into `libdfroot.so` at build time.
 *
 * This object mirrors DFRoot's own `MainActivity` flow — same prefs file
 * ("dfroot"), same key ("su_manager"), same pre-flight checks — so behaviour is
 * identical to running the DFRoot app, just in-process:
 *
 *  1. scan installed apps for an SU manager carrying `libksud.so` in its
 *     nativeLibraryDir (the exploit stages that file, it cannot invent one);
 *  2. persist the user's choice under GamersHub's device-protected storage, the
 *     same direct-boot-aware context DFRoot uses;
 *  3. hand control to `df.root.ExploitRunner.run` and stream its log lines.
 *
 * Runs on the calling thread — always invoke from a background dispatcher. The
 * returned code is the exploit binary's exit code:
 *  0 = patched (or already hooked), 1 = ksud reported an error, 2 = pre-flight failure.
 */
object DfRootRunner {

    /** Prefs file/key match DFRoot's own constants (they are package-private there). */
    private const val PREFS = "dfroot"
    private const val PREF_SU_MANAGER = "su_manager"

    data class Manager(
        val pkg: String,
        val label: String,
        val icon: Drawable?,
    )

    /** True when this build actually contains the native payload (arm64-only builds). */
    fun payloadPresent(context: Context): Boolean =
        runCatching {
            java.io.File(
                context.applicationInfo.nativeLibraryDir,
                "libdfroot.so"
            ).exists()
        }.getOrDefault(false)

    /**
     * Installed SU managers that carry `libksud.so`, sorted by label.
     * Mirrors DFRoot's own listing: system apps are skipped — a preinstalled
     * KernelSU manager the user actually uses is still non-system, and a system
     * su hides from the exploit either way.
     */
    fun candidates(context: Context): List<Manager> {
        val pm = context.packageManager
        return runCatching {
            pm.getInstalledApplications(0)
                .asSequence()
                .filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                .filter { df.root.ExploitRunner.hasKsud(it) }
                .map { Manager(it.packageName, pm.getApplicationLabel(it).toString(), runCatching { pm.getApplicationIcon(it) }.getOrNull()) }
                .sortedBy { it.label.lowercase() }
                .toList()
        }.getOrDefault(emptyList())
    }

    /** Device-protected context — the same storage DFRoot's prefs live in. */
    private fun deContext(context: Context): Context =
        runCatching { context.createDeviceProtectedStorageContext() }
            .getOrDefault(context)

    /** The manager the exploit will bootstrap, or null when none was chosen/still installed. */
    fun selected(context: Context): String? {
        val saved = deContext(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_SU_MANAGER, null)
            ?: return null
        // Cheap check: the manager must still be installed and still carry libksud.so.
        val info = runCatching {
            context.packageManager.getApplicationInfo(saved, 0)
        }.getOrNull() ?: return null
        return saved.takeIf { df.root.ExploitRunner.hasKsud(info) }
    }

    fun select(context: Context, pkg: String) {
        deContext(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(PREF_SU_MANAGER, pkg).apply()
    }

    /**
     * Runs the exploit. [onLine] is invoked for every log line, already marshalled
     * back to the main thread so Compose state can be updated directly.
     */
    fun run(context: Context, onLine: (String) -> Unit): Int {
        if (!payloadPresent(context)) {
            post(onLine, "embedded payload missing from this build — reinstall the signed release APK\n")
            return 2
        }
        val main = Handler(Looper.getMainLooper())
        val reporter = object : df.root.IReporter {
            override fun report(msg: String) {
                main.post { onLine(msg) }
            }
        }
        return try {
            df.root.ExploitRunner.run(deContext(context), reporter)
        } catch (t: Throwable) {
            post(onLine, "exception: $t\n")
            2
        }
    }

    private fun post(onLine: (String) -> Unit, msg: String) {
        Handler(Looper.getMainLooper()).post { onLine(msg) }
    }
}
