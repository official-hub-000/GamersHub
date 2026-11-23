package com.gamershub.core.root

import android.content.Context

/**
 * Proves, on the running device, that the privileged payload can actually be executed.
 *
 * ## Why this exists
 *
 * DFRoot's chain spans four parties that must agree on one absolute path — the app that
 * stages the files, the kernel module that `exec`s them as root, the `bootstrap` binary,
 * and the prefs file it parses. Three releases in a row shipped with them disagreeing,
 * and none of the disagreements were visible from the app: the module loaded, SELinux
 * went permissive, the exec hit a path that did not exist, and the run reported success.
 *
 * Checking the paths at build time is not enough either, because the *device* decides
 * where device-protected storage lands and whether it exists at all. So this runs the
 * one check that cannot be fooled: write a file at the exact path the payload is compiled
 * to exec, read it back, and confirm the directory really is the payload's directory.
 *
 * The result is shown verbatim in DIAGNOSE and refused before a run, with the specific
 * path that failed, so a broken install is a sentence on screen rather than a mystery.
 */
object StorageLayoutCheck {

    /** One line per fact, most specific first. */
    data class Report(
        val deviceProtectedDir: String?,
        val credentialDir: String?,
        val stagingDir: String,
        val compiledBootstrap: String?,
        val writable: Boolean,
        /** "" when the layout is usable, else why it is not. */
        val problem: String,
    ) {
        val usable: Boolean get() = problem.isEmpty()

        fun lines(): List<String> = buildList {
            add("payload dir   $stagingDir  ${if (writable) "(writable)" else "(NOT writable)"}")
            add("device-prot   ${deviceProtectedDir ?: "unavailable"}")
            add("credential    ${credentialDir ?: "unavailable"}")
            add("payload wants ${compiledBootstrap ?: "not declared by this build"}")
            if (!usable) add("layout        BROKEN — $problem")
        }
    }

    fun inspect(context: Context): Report {
        val deDir = runCatching {
            context.createDeviceProtectedStorageContext().filesDir.parentFile?.absolutePath
        }.getOrNull()
        val ceDir = runCatching {
            context.filesDir.parentFile?.absolutePath
        }.getOrNull()

        val staging = DfPaths.payloadDir(context).absolutePath
        val compiled = DfPaths.compiledBootstrapPath(context)

        // The only honest test: can we create the exact file the module will exec?
        val writable = runCatching {
            val probe = java.io.File(staging, ".gh-layout-probe")
            java.io.File(staging).mkdirs()
            probe.writeText("ok")
            val read = probe.readText()
            probe.delete()
            read == "ok"
        }.getOrDefault(false)

        val problem = when {
            !writable ->
                "cannot write to $staging — the payload cannot be staged there."
            compiled == null ->
                "this build does not declare where its payload belongs " +
                    "(libdfroot-path.so is missing), so the module would exec a guess."
            compiled != DfPaths.bootstrapPath(context) ->
                "the payload execs $compiled but this app stages into $staging. " +
                    "The module would load, go permissive and exec nothing."
            else -> ""
        }

        return Report(deDir, ceDir, staging, compiled, writable, problem)
    }
}
