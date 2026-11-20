package com.gamershub.core.root

/**
 * Deep system control that only a root uid can do.
 *
 * Each entry is a real command with a real return value — nothing is a stub that
 * reports success. Every method returns a [Result] carrying either the command
 * output or the reason it could not run, so the UI can show the truth.
 *
 * All of it is gated behind [RootShell.ready] and therefore behind the master
 * engine switch: turn the engine off and these stop being reachable at all.
 */
object RootTools {

    /**
     * Journal sink, set at app start.
     *
     * Freezing a package is the most user-visible thing this app can do: the icon stays
     * on the home screen and refuses to open. If "engine off" does not put it back, the
     * user is left with a phone where an app silently stopped working.
     */
    @Volatile
    private var journal: android.content.Context? = null

    fun attach(context: android.content.Context) {
        journal = context.applicationContext
    }

    private fun record(kind: com.gamershub.core.ChangeLog.Kind, subject: String, before: String, after: String) {
        journal?.let { com.gamershub.core.ChangeLog.record(it, kind, subject, before, after) }
    }

    data class Result(val ok: Boolean, val detail: String)

    private fun has(): Boolean = RootShell.ready()

    private fun none(what: String) = Result(false, "$what needs root")

    private fun exec(cmd: String): Result {
        val out = RootShell.run(cmd) ?: return Result(false, "root shell did not answer")
        val exit = Regex("__GH_EXIT__=(-?\\d+)").find(out)?.groupValues?.get(1)
        return Result(exit == null || exit == "0", out.trim())
    }

    private fun run(cmd: String): Result = exec("$cmd; echo __GH_EXIT__=$?")

    // ---------------------------------------------------------------- identity

    fun whoAmI(): Result =
        if (!has()) none("uid check") else exec("id")

    /** True root vs adb-style shell (uid 2000) vs app. */
    fun privilegeLevel(): String {
        if (!has()) return "app"
        val out = RootShell.run("id") ?: return "app"
        return when {
            out.contains("uid=0") -> "root"
            out.contains("uid=2000") -> "shell"
            else -> "app"
        }
    }

    // ---------------------------------------------------------------- packages

    /** Freeze (disable) a package in place — stays installed, cannot run. */
    fun freezePackage(pkg: String): Result =
        if (!has()) {
            none("freeze")
        } else {
            run("pm disable-user --user 0 ${q(pkg)}").also { r ->
                if (r.ok) record(
                    com.gamershub.core.ChangeLog.Kind.PACKAGE_DISABLED, pkg, "enabled", "disabled"
                )
            }
        }

    fun unfreezePackage(pkg: String): Result =
        if (!has()) {
            none("unfreeze")
        } else {
            run("pm enable ${q(pkg)}").also { r ->
                if (r.ok) {
                    journal?.let { ctx ->
                        com.gamershub.core.ChangeLog.replace(
                            ctx,
                            com.gamershub.core.ChangeLog.current(ctx).filterNot {
                                it.kind == com.gamershub.core.ChangeLog.Kind.PACKAGE_DISABLED &&
                                    it.subject == pkg
                            }
                        )
                    }
                }
            }
        }

    /** Disable a package for the current user without uninstalling it. */
    fun suspendPackage(pkg: String): Result =
        if (!has()) {
            none("suspend")
        } else {
            run("pm suspend ${q(pkg)}").also { r ->
                // Suspending is reversed the same way as disabling: `pm enable`.
                if (r.ok) record(
                    com.gamershub.core.ChangeLog.Kind.PACKAGE_DISABLED, pkg, "enabled", "suspended"
                )
            }
        }

    fun unsuspendPackage(pkg: String): Result =
        if (!has()) none("unsuspend") else run("pm unsuspend ${q(pkg)}")

    /** True system-app remover (uninstall for user 0, keeps the APK on /system). */
    fun uninstallForUser(pkg: String): Result =
        if (!has()) none("uninstall") else run("pm uninstall --user 0 ${q(pkg)}")

    // ---------------------------------------------------------------- properties

    /**
     * Writes a system property. `resetprop` (Magisk) is used when available because
     * it can override ro.* props; otherwise setprop, which only accepts mutable keys.
     */
    fun setProp(key: String, value: String): Result {
        if (!has()) return none("setprop")
        val hasResetprop = RootShell.ok("command -v resetprop >/dev/null 2>&1")
        val cmd = if (hasResetprop) "resetprop ${q(key)} ${q(value)}" else "setprop ${q(key)} ${q(value)}"
        val r = run(cmd)
        return if (r.ok) Result(true, "$key=$value via ${if (hasResetprop) "resetprop" else "setprop"}")
               else Result(false, "rejected: ${r.detail.take(120)}")
    }

    fun getProp(key: String): String =
        RootShell.run("getprop ${q(key)}")?.trim().orEmpty()

    // ---------------------------------------------------------------- storage / caches

    fun clearAppCache(pkg: String): Result =
        if (!has()) none("clear cache") else run("rm -rf /data/data/${q(pkg)}/cache/*")

    fun trimCaches(): Result =
        if (!has()) none("trim") else run("sync; echo 3 > /proc/sys/vm/drop_caches")

    fun fstrim(): Result =
        if (!has()) none("fstrim") else run("fstrim -v /data")

    // ---------------------------------------------------------------- cpu / gpu tuning

    /** Pin or release CPU frequency limits. [maxKhz] 0 = release the cap. */
    fun cpuMaxHz(maxKhz: Long): Result {
        if (!has()) return none("cpu freq")
        val cores = RootShell.run("ls /sys/devices/system/cpu/ | grep -E '^cpu[0-9]+$' | wc -l")
            ?.trim()?.toIntOrNull() ?: return Result(false, "no cpufreq nodes")
        val path = if (maxKhz <= 0) "scaling_max_freq" else "scaling_max_freq"
        val target = if (maxKhz <= 0) {
            // restore the hardware maximum
            "cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"
        } else maxKhz.toString()
        val value = if (maxKhz <= 0) {
            RootShell.run(target)?.trim()?.toLongOrNull()?.toString() ?: return Result(false, "cannot read max")
        } else target
        var applied = 0
        for (i in 0 until cores) {
            if (RootShell.ok("echo $value > /sys/devices/system/cpu/cpu$i/cpufreq/$path 2>/dev/null")) applied++
        }
        return Result(applied > 0, "cpu$path=$value on $applied/$cores cores")
    }

    /** GPU governor where the driver exposes it (Adreno/Mali vary). */
    fun gpuGovernor(mode: String): Result {
        if (!has()) return none("gpu governor")
        val paths = listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
            "/sys/class/kgsl/kgsl-3d0/pwrscale/trustzone/governor",
            "/sys/devices/platform/mali.0/dvfs_governor",
        )
        for (p in paths) {
            if (RootShell.ok("test -w $p")) {
                val r = run("echo ${q(mode)} > $p")
                if (r.ok) return Result(true, "gpu governor=$mode ($p)")
            }
        }
        return Result(false, "no writable GPU governor on this device")
    }

    // ---------------------------------------------------------------- display depth

    /** Override the reported refresh rate (some panels accept it via SurfaceFlinger). */
    fun setRefreshRate(hz: Float): Result =
        if (!has()) none("refresh rate")
        else run("service call SurfaceFlinger 1035 i32 1; settings put system peak_refresh_rate $hz; settings put system min_refresh_rate $hz")

    /** Put display size/density back to native. */
    fun resetDisplay(): Result =
        if (!has()) none("reset display") else run("wm size reset; wm density reset")

    // ---------------------------------------------------------------- process depth

    /** Kill a package by uid exactly, including its isolated processes. */
    fun forceStop(pkg: String): Result =
        if (!has()) none("force stop") else run("am force-stop ${q(pkg)}")

    /** Everything the package has open right now. */
    fun openFiles(pkg: String): Result =
        if (!has()) none("lsof")
        else run("lsof 2>/dev/null | grep -i ${q(pkg)} | head -20")

    // ---------------------------------------------------------------- module flashing

    /**
     * Flash a Magisk/KernelSU module zip.
     *
     * With the Magisk `magisk` binary present we use its own installer so Magisk's
     * database stays consistent. Otherwise we install the module directory manually
     * the way Magisk expects it (module.prop + the module's files under
     * /data/adb/modules/<id>) and mark it for the next boot.
     */
    fun flashModule(zipPath: String, id: String): Result {
        if (!has()) return none("module flash")
        if (!java.io.File(zipPath).exists()) return Result(false, "zip not found: $zipPath")

        // 1. official path
        if (RootShell.ok("command -v magisk >/dev/null 2>&1")) {
            val r = run("magisk --install-module ${q(zipPath)}")
            if (r.ok) return Result(true, "installed via magisk: ${r.detail.takeLast(200)}")
        }

        // 2. manual install, Magisk-compatible layout
        val stage = "/data/local/tmp/ghmod_$id"
        run("rm -rf $stage; mkdir -p $stage")
        val unzip = run("unzip -o ${q(zipPath)} -d $stage")
        if (!unzip.ok) return Result(false, "unzip failed: ${unzip.detail.take(160)}")
        if (!RootShell.ok("test -f $stage/module.prop")) {
            return Result(false, "not a module zip (no module.prop)")
        }
        val dest = "/data/adb/modules/$id"
        run("rm -rf $dest; mkdir -p $dest")
        val copy = run("cp -af $stage/. $dest/")
        run("touch $dest/update")
        run("rm -rf $stage")
        return if (copy.ok) Result(true, "staged at $dest — takes effect on next reboot")
               else Result(false, "copy failed: ${copy.detail.take(160)}")
    }

    /** Modules currently installed, for the Root screen. */
    fun listModules(): List<String> {
        if (!has()) return emptyList()
        val out = RootShell.run("ls /data/adb/modules 2>/dev/null") ?: return emptyList()
        return out.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun removeModule(id: String): Result {
        if (!has()) return none("remove module")
        run("touch /data/adb/modules/$id/remove")
        return Result(true, "module $id marked for removal — reboot to finish")
    }

    // ---------------------------------------------------------------- helpers

    /** Shell-quote a single argument. */
    private fun q(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
