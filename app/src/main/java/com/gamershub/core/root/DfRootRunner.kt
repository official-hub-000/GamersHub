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

    /** How much of the exploit output is kept for the report. */
    private const val LOG_TAIL = 4000
    private const val PREF_DISABLE_MODULES = "disable_modules"
    private const val PREF_SOFT_REBOOT = "soft_reboot"

    data class Manager(
        val pkg: String,
        val label: String,
        val icon: Drawable?,
    )

    /**
     * Absolute path of the staged `bootstrap` executable.
     *
     * The kernel module `exec`s this path directly, so it has to be this package's
     * device-protected files dir — not a path under some other package name. It is
     * also the string the native payload is compiled with (see `lkm/dfroot.c`).
     */
    fun bootstrapPath(context: Context): String =
        java.io.File(deContext(context).filesDir.parentFile, "bootstrap").absolutePath

    /** The payload is compiled for a package name; this is the one it must match. */
    private const val PAYLOAD_PACKAGE = "com.gamershub"

    /**
     * True only when this build can actually complete the chain.
     *
     * Checking that `libdfroot.so` exists is not enough, and pretending it is was the
     * bug: the payload was compiled with upstream DFRoot's package name baked into its
     * `insmod` bootstrap path, so every run ended with the module loaded, SELinux
     * permissive, and nothing to exec. Root never appeared and the log looked fine.
     *
     * Both halves are checked here — the payload file, and the staged path the payload
     * will look for — so a mis-built payload is reported up front instead of silently
     * doing nothing.
     */
    fun payloadPresent(context: Context): Boolean = runCatching {
        val bin = java.io.File(context.applicationInfo.nativeLibraryDir, "libdfroot.so")
        val expectedParent = "/data/user_de/0/$PAYLOAD_PACKAGE"
        bin.exists() && context.packageName == PAYLOAD_PACKAGE &&
            bootstrapPath(context).substringBeforeLast('/') == expectedParent
    }.getOrDefault(false)

    /** Human-readable reason the payload cannot run, or "" when it can. */
    fun payloadProblem(context: Context): String = when {
        !java.io.File(context.applicationInfo.nativeLibraryDir, "libdfroot.so").exists() ->
            "This build has no DFRoot payload (it is arm64-only)."
        context.packageName != PAYLOAD_PACKAGE ->
            "The payload is built for $PAYLOAD_PACKAGE, but this install is ${context.packageName}."
        else -> ""
    }

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
     * Writes the two switches the exploit reads but this screen never showed.
     *
     * `disable_modules` matters more than it looks: if a KernelSU module is what breaks
     * root, `ksud late-load` fails and the run reports success with no root — upstream
     * DFRoot's own FAQ says to switch modules off before retrying. Exposing the switch
     * is what turns "it doesn't apply" into something the user can recover from.
     *
     * `soft_reboot` is left OFF by default on purpose: it restarts the UI, and a user who
     * sees the screen flash assumes the app crashed.
     */
    fun setOptions(context: Context, disableModules: Boolean, softReboot: Boolean) {
        deContext(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_DISABLE_MODULES, disableModules)
            .putBoolean(PREF_SOFT_REBOOT, softReboot)
            .apply()
    }

    fun disableModules(context: Context): Boolean =
        deContext(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(PREF_DISABLE_MODULES, false)

    fun softReboot(context: Context): Boolean =
        deContext(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(PREF_SOFT_REBOOT, false)

    /**
     * Runs the exploit. [onLine] is invoked for every log line, already marshalled
     * back to the main thread so Compose state can be updated directly.
     */
    fun run(context: Context, onLine: (String) -> Unit): Int {
        if (!payloadPresent(context)) {
            val why = payloadProblem(context).ifBlank { "payload incomplete" }
            post(onLine, "cannot run: $why\n")
            com.gamershub.GamersHubApp.problem("dfroot", "DFRoot payload cannot run: $why")
            return 2
        }
        if (selected(context) == null) {
            post(onLine, "no SU manager selected — pick one above\n")
            return 2
        }
        val main = Handler(Looper.getMainLooper())
        // Every line goes to the panel *and* to the app's own log. The panel's copy
        // disappears when the sheet closes, and "DFRoot didn't apply" with no log is
        // unanswerable.
        val tailBuf = StringBuilder()
        val tap: (String) -> Unit = { line ->
            main.post { onLine(line) }
            synchronized(tailBuf) { tailBuf.append(line).also { if (it.length > LOG_TAIL) it.delete(0, it.length - LOG_TAIL) } }
        }
        return try {
            val rc = df.root.ExploitRunner.run(deContext(context), object : df.root.IReporter {
                override fun report(msg: String) = tap(msg)
            })
            com.gamershub.GamersHubApp.noteRun(
                "dfroot",
                "exit $rc · " + synchronized(tailBuf) { tailBuf.toString() }
                    .lineSequence().lastOrNull { it.isNotBlank() }.orEmpty()
            )
            rc
        } catch (t: Throwable) {
            tap("exception: $t\n")
            com.gamershub.GamersHubApp.noteRun("dfroot", "exception: $t")
            2
        }
    }

    private fun post(onLine: (String) -> Unit, msg: String) {
        Handler(Looper.getMainLooper()).post { onLine(msg) }
    }
}
