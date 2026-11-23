package com.gamershub.core.root

import android.content.Context
import java.io.File

/**
 * The one directory this app's privileged native payload can agree on.
 *
 * ## Why this exists at all
 *
 * DFRoot's chain has four independent pieces that must name the *same* absolute path,
 * and three of them are computed in different languages:
 *
 *  1. the Kotlin staging code, which copies `libksud.so` / `libbootstrap.so` out of
 *     `nativeLibraryDir` and makes them executable;
 *  2. the kernel module (`lkm/dfroot.c`), which is compiled with a literal path in it
 *     and `exec`s it as root from `call_usermodehelper`;
 *  3. `bootstrap`, which is compiled with a literal path to the staged `ksud`;
 *  4. the prefs file that `bootstrap` parses by hand as an XML file it opens itself.
 *
 * They used to be computed by three different routes — the app's `filesDir.parentFile`,
 * `createDeviceProtectedStorageContext().filesDir.parentFile`, and hard-coded strings —
 * and those routes can disagree:
 *
 *  - `CREATE_DEVICE_PROTECTED_STORAGE` is optional and is never requested, so on many
 *    devices `createDeviceProtectedStorageContext()` returns the **credential**-encrypted
 *    context, whose `filesDir` is `/data/user/0/<pkg>/files`. Then the app staged the
 *    executables into `/data/user/0/com.gamershub/` while the module built into the APK
 *    `exec`s the hard-coded `/data/user_de/0/com.gamershub/bootstrap` — a path that does
 *    not exist. The module loads, SELinux goes permissive, nothing is exec'd, and the
 *    run finishes with no root and a log that looks healthy.
 *  - The debug build (`applicationIdSuffix = ".debug"`) computes
 *    `/data/user/0/com.gamershub.debug/` (or the `_de` equivalent), which the payload was
 *    never compiled for.
 *
 * So the path is no longer computed by four parties. It is computed **here**, from the
 * app's real data directory, and every party reads it from the same source: this file
 * for Kotlin, `payloadPath()` for the native side (passed as an argument), and the same
 * string for the prefs handshake. `DfRootRunner.payloadPresent()` reads the layout marker
 * written by the native build, so a genuinely mismatched payload is still caught up front.
 */
object DfPaths {

    /**
     * The directory the payload stages into, as a **device-protected** path.
     *
     * ## This must be /data/user_de/0, not /data/user/0
     *
     * The kernel module runs `call_usermodehelper` from init's context and `exec`s an
     * absolute path that was compiled into it. Credential-encrypted storage does not
     * exist until the user unlocks, so that path is device-protected. It is
     * `/data/user_de/0/<pkg>/bootstrap`.
     *
     * `applicationInfo.dataDir` is **credential**-encrypted — `/data/user/0/<pkg>` — even
     * when the package declares a device-protected data dir, unless it has been
     * explicitly migrated. Using it here produced a path that disagreed with the payload's
     * compiled-in path, so `layoutMatches()` returned false and DFRoot refused to run on
     * every device. That was a regression introduced by this file, and it is exactly the
     * class of two-parties-disagree bug this file exists to prevent.
     *
     * `createDeviceProtectedStorageContext().filesDir.parentFile` is the device-protected
     * dir when the platform provides one, which is the layout upstream DFRoot uses. The
     * credential dir is the fallback for an install that genuinely has no DE storage — and
     * on such an install the layout marker will disagree and the app says so up front
     * instead of failing silently.
     */
    fun payloadDir(context: Context): File {
        val de = runCatching {
            context.createDeviceProtectedStorageContext().filesDir.parentFile
        }.getOrNull()
        if (de != null) return de
        return runCatching { File(context.applicationInfo.dataDir) }
            .getOrElse { context.filesDir.parentFile ?: context.filesDir }
    }

    fun bootstrap(context: Context): File = File(payloadDir(context), "bootstrap")

    fun ksud(context: Context): File = File(payloadDir(context), "ksud")

    /** Staging targets, exposed separately so [df.root.ExploitRunner] never re-derives them. */
    fun bootstrapStage(context: Context): File = bootstrap(context)

    fun ksudStage(context: Context): File = ksud(context)

    /** Absolute path handed to the kernel module, which `exec`s it as root. */
    fun bootstrapPath(context: Context): String = bootstrap(context).absolutePath

    fun ksudPath(context: Context): String = ksud(context).absolutePath

    /**
     * The prefs file the native `bootstrap` opens and parses itself.
     *
     * Deliberately a *separate* file from the app's SharedPreferences rather than
     * Android's own `shared_prefs/dfroot.xml`. Two reasons: the native side would have to
     * reproduce SharedPreferences' exact XML quirks (its `<string>` elements are the
     * shape `bootstrap.c` parses), and the app writes this file with a plain
     * `writeText` so that what is on disk is exactly what the parser sees.
     *
     * It is still backed by the same SharedPreferences values, which remain the source of
     * truth for the UI — [syncNativePrefs] rewrites this file from them on every change.
     */
    fun nativePrefs(context: Context): File = File(payloadDir(context), "dfroot-native.xml")

    fun nativePrefsPath(context: Context): String = nativePrefs(context).absolutePath

    /**
     * The layout marker the native build writes into `nativeLibraryDir`.
     *
     * It holds the absolute path the payload was *compiled* to exec. If it is absent the
     * payload predates this handshake and the path cannot be trusted; if it disagrees
     * with [bootstrapPath] the payload is for a different install. Either way the run is
     * refused up front with the real reason instead of silently doing nothing.
     */
    fun layoutMarker(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libdfroot-path.so")

    /**
     * What the payload was compiled for, or null when it does not declare it.
     *
     * The marker is a 64-byte ELF header followed by the path as plain text, so this
     * scans for an absolute path rather than reading the file as text — `readText()` on a
     * binary returns mojibake with the path buried in it, and an earlier version of this
     * compared that whole blob against the expected directory, which could never match.
     */
    fun compiledBootstrapPath(context: Context): String? = runCatching {
        val f = layoutMarker(context)
        if (!f.exists()) return@runCatching null
        val text = String(f.readBytes(), Charsets.ISO_8859_1)
        // Every absolute path that ends in /bootstrap, first one wins.
        Regex("/data/[A-Za-z0-9_./-]+/bootstrap").findAll(text)
            .map { it.value }
            .firstOrNull()
    }.getOrNull()

    /** True when the app and the embedded payload agree on where the payload lives. */
    fun layoutMatches(context: Context): Boolean {
        val compiled = compiledBootstrapPath(context) ?: return false
        return compiled == bootstrapPath(context)
    }

    /**
     * Rewrites the native prefs file from the app's SharedPreferences.
     *
     * Called on every option change and immediately before a run, so the native side can
     * never act on a stale copy. The XML shape is the one `bootstrap.c` parses: a flat
     * `<map>` of `<string name="…">value</string>` elements.
     */
    fun syncNativePrefs(context: Context) {
        runCatching {
            val sp = runCatching { context.createDeviceProtectedStorageContext() }
                .getOrDefault(context)
                .getSharedPreferences("dfroot", Context.MODE_PRIVATE)
            val manager = sp.getString("su_manager", null) ?: return@runCatching
            val disableModules = sp.getBoolean("disable_modules", false)
            val softReboot = sp.getBoolean("soft_reboot", false)

            val xml = buildString {
                append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n")
                append("<map>\n")
                append("    <string name=\"su_manager\">").append(xmlEscape(manager)).append("</string>\n")
                append("    <boolean name=\"disable_modules\" value=\"").append(disableModules)
                    .append("\" />\n")
                append("    <boolean name=\"soft_reboot\" value=\"").append(softReboot)
                    .append("\" />\n")
                append("    <string name=\"bootstrap\">").append(xmlEscape(bootstrapPath(context)))
                    .append("</string>\n")
                append("    <string name=\"ksud\">").append(xmlEscape(ksudPath(context)))
                    .append("</string>\n")
                append("</map>\n")
            }
            val out = nativePrefs(context)
            out.parentFile?.mkdirs()
            out.writeText(xml)
            // Readable by the root context that opens it: the file lives in the app's data
            // dir, which root can reach regardless, but the mode keeps it sane.
            runCatching { out.setReadable(true, false) }
        }
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
