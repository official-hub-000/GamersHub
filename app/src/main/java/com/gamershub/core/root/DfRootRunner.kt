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
    fun bootstrapPath(context: Context): String = DfPaths.bootstrapPath(context)

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
        val dir = context.applicationInfo.nativeLibraryDir
        java.io.File(dir, "libdfroot.so").exists() &&
            java.io.File(dir, "libbootstrap.so").exists() &&
            context.packageName == PAYLOAD_PACKAGE &&
            // The payload declares the directory it was compiled to exec, so this is a
            // real comparison against the build rather than against a second guess made
            // here. It used to hard-code "/data/user_de/0/$PAYLOAD_PACKAGE" and compare it
            // with `createDeviceProtectedStorageContext().filesDir.parentFile`, which is
            // frequently the credential-encrypted dir — so a perfectly good payload was
            // refused, and DFRoot could not run at all.
            DfPaths.layoutMatches(context)
    }.getOrDefault(false)

    /** Human-readable reason the payload cannot run, or "" when it can. */
    fun payloadProblem(context: Context): String {
        val dir = context.applicationInfo.nativeLibraryDir
        // Ask the device first: whether device-protected storage exists here, whether it
        // is writable, and whether it is the directory the payload was compiled for.
        val layout = runCatching { StorageLayoutCheck.inspect(context) }.getOrNull()
        if (layout != null && !layout.usable) return layout.problem
        val compiled = DfPaths.compiledBootstrapPath(context)
        return when {
            !java.io.File(dir, "libdfroot.so").exists() ->
                "This build has no DFRoot payload (it is arm64-only)."
            context.packageName != PAYLOAD_PACKAGE ->
                "The payload is built for $PAYLOAD_PACKAGE, but this install is " +
                    "${context.packageName}."
            compiled == null ->
                "The payload does not declare its staging path (libdfroot-path.so is " +
                    "missing), so the module could load and then exec nothing."
            compiled != DfPaths.bootstrapPath(context) ->
                "The payload stages to $compiled but this install stages to " +
                    "${DfPaths.bootstrapPath(context)} — the module would exec a path that " +
                    "does not exist."
            else -> ""
        }
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
            // Pass the plain context. `df.root.ExploitRunner` must not be handed the
            // device-protected one: it stages executables into DfPaths' directory and
            // reads `nativeLibraryDir`, both of which are properties of the real install.
            // Passing the DE context here is how the staging dir and the compiled-in exec
            // path used to drift apart.
            val rc = df.root.ExploitRunner.run(context.applicationContext, object : df.root.IReporter {
                override fun report(msg: String) = tap(msg)
            })
            // `ksud late-load` returning 0 means the daemon was *started*, not that it is
            // answering. The exploit used to return here, ADOPT ROOT was pressed
            // immediately, `su` was not up yet, and the ladder reported "no rung
            // answered" — which reads exactly like "DFRoot did not work".
            if (rc == 0) awaitSu(context, tap)
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

    /**
     * Waits for the su daemon this run just started to actually answer.
     *
     * Bounded, and it says what it saw on every outcome. A daemon that never comes up is
     * the difference between "run the exploit again" and "the manager did not accept the
     * late load", and the user cannot tell those apart without this line.
     */
    private fun awaitSu(context: Context, tap: (String) -> Unit) {
        val deadline = System.currentTimeMillis() + SU_WAIT_MS
        tap("waiting for the su daemon…\n")
        while (System.currentTimeMillis() < deadline) {
            val answered = runCatching {
                val bin = RootShell.suBinary() ?: "su"
                val p = ProcessBuilder(bin, "-c", "id").redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor()
                out.contains("uid=0")
            }.getOrDefault(false)
            if (answered) {
                tap("su is answering as uid 0.\n")
                // Pick the grant up now rather than making the user find ADOPT ROOT: the
                // whole point of the run was to end up privileged.
                runCatching { RootShell.acquire(context) }
                return
            }
            Thread.sleep(500)
        }
        tap(
            "the su daemon did not answer within ${SU_WAIT_MS / 1000}s. If ksud reported " +
                "success above, the manager may need a moment: press ADOPT ROOT. If it " +
                "reported an error, enable \"Disable KSU modules\" and run again.\n"
        )
    }

    /** How long to wait for `su` after a successful late-load. */
    private const val SU_WAIT_MS = 15_000L

    private fun post(onLine: (String) -> Unit, msg: String) {
        Handler(Looper.getMainLooper()).post { onLine(msg) }
    }
}
