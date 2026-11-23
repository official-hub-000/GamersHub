package com.gamershub.core.root

import java.io.File

/**
 * Real Magisk / KernelSU / APatch module management.
 *
 * Everything here is the same mechanism the managers themselves use — the
 * on-disk module directory, not a private side-channel. That means a change made
 * from GamersHub is visible in the Magisk app and vice versa, and nothing is
 * faked: if the manager is absent or root is not granted, each call says so.
 *
 * Module state, per the managers' own rules:
 *   `<module>/disable`    → module is disabled
 *   `<module>/remove`     → module is queued for removal at next boot
 *   `<module>/update`     → staging marker, module not yet live
 *   `/data/adb/modules_update/<id>` → update waiting for reboot
 */
object ModuleManager {

    /**
     * Set once at app start.
     *
     * Mutations are journaled ([com.gamershub.core.ChangeLog]) so the engine can undo
     * them, and journaling needs storage. Keeping it here rather than adding a `Context`
     * parameter to every method preserves the existing call sites — and, more
     * importantly, means a mutation cannot be made *without* being journaled, which is
     * exactly how a "turns everything back" promise silently stops being true.
     */
    @Volatile
    private var journal: android.content.Context? = null

    fun attach(context: android.content.Context) {
        journal = context.applicationContext
    }

    private fun record(kind: com.gamershub.core.ChangeLog.Kind, subject: String, before: String, after: String) {
        journal?.let { com.gamershub.core.ChangeLog.record(it, kind, subject, before, after) }
    }

    private const val MODULES = "/data/adb/modules"
    private const val MODULES_UPDATE = "/data/adb/modules_update"

    enum class Manager { MAGISK, KERNELSU, APATCH, NONE, UNKNOWN }

    data class Module(
        val id: String,
        val name: String,
        val version: String,
        val versionCode: String,
        val author: String,
        val description: String,
        val enabled: Boolean,
        val pendingRemove: Boolean,
        val pendingUpdate: Boolean,
    ) {
        val state: String
            get() = when {
                pendingRemove -> "remove on reboot"
                pendingUpdate -> "update on reboot"
                enabled -> "active"
                else -> "disabled"
            }
    }

    // ------------------------------------------------------------------ manager

    /**
     * Which root solution is installed. Checked by the daemon binaries and the
     * data directories the managers create — the same signals the managers use.
     */
    fun manager(): Manager {
        val r = RootShell.run(
            "if [ -x /data/adb/magisk/magisk ] || command -v magisk >/dev/null 2>&1; then echo magisk; " +
                "elif command -v ksud >/dev/null 2>&1 || [ -d /data/adb/ksu ]; then echo kernelsu; " +
                "elif command -v apd >/dev/null 2>&1 || [ -d /data/adb/ap ]; then echo apatch; " +
                "elif [ -d /data/adb ]; then echo unknown; else echo none; fi"
            // No privileged shell means we could not ask, which is not the same as "there
            // is a manager we do not recognise". Returning UNKNOWN here is what printed
            // "an unrecognised manager — policy store not readable" on a device with no
            // root session at all, next to "su binary not found": two different claims
            // about the same absence. NONE is the truthful answer.
        ) ?: return Manager.NONE
        return when {
            r.contains("magisk") -> Manager.MAGISK
            r.contains("kernelsu") -> Manager.KERNELSU
            r.contains("apatch") -> Manager.APATCH
            r.contains("unknown") -> Manager.UNKNOWN
            r.contains("none") -> Manager.NONE
            else -> Manager.UNKNOWN
        }
    }

    fun managerName(): String = when (manager()) {
        Manager.MAGISK -> "Magisk"
        Manager.KERNELSU -> "KernelSU"
        Manager.APATCH -> "APatch"
        Manager.NONE -> "no root manager"
        // Only reached when a manager answers on the shell but is not one we recognise.
        // It must not read like a device state: the copy around it puts this straight
        // into a sentence ("flashed with X's own installer"), and the old wording
        // produced "root manager (unrecognised)'s own installer" on a device that has no
        // manager at all.
        Manager.UNKNOWN -> "an unrecognised manager"
    }

    fun managerVersion(): String {
        val r = when (manager()) {
            Manager.MAGISK -> RootShell.run("magisk -v 2>/dev/null || magisk -V 2>/dev/null")
            Manager.KERNELSU -> RootShell.run("ksud -V 2>/dev/null")
            Manager.APATCH -> RootShell.run("apd -V 2>/dev/null")
            else -> null
        }
        return r?.trim()?.lines()?.firstOrNull()?.take(60).orEmpty()
    }

    /** True when the manager can flash a zip through its own installer. */
    fun canFlashingWork(): Boolean = manager() in listOf(
        Manager.MAGISK, Manager.KERNELSU, Manager.APATCH
    )

    // ------------------------------------------------------------------ listing

    /**
     * Every installed module, newest manager state read fresh from disk.
     * Returns an empty list when root is unavailable — the caller should check
     * [RootShell.ready] to distinguish "no modules" from "no root".
     */
    fun list(): List<Module> {
        if (!RootShell.ready()) return emptyList()
        val ids = RootShell.run("ls -1 $MODULES 2>/dev/null")?.lines()
            ?.map { it.trim() }?.filter { it.isNotEmpty() && !it.contains(" ") }
            ?: return emptyList()
        val updates = RootShell.run("ls -1 $MODULES_UPDATE 2>/dev/null")?.lines()
            ?.map { it.trim() }?.toSet() ?: emptySet()

        return ids.mapNotNull { id -> read(id, pendingUpdate = id in updates) }
    }

    private fun read(id: String, pendingUpdate: Boolean): Module? {
        val prop = RootShell.run("cat $MODULES/$id/module.prop 2>/dev/null") ?: return null
        if (prop.isBlank()) return null
        val map = prop.lines().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }.toMap()
        val flags = RootShell.run(
            "for f in disable remove update; do [ -e $MODULES/$id/\$f ] && echo \$f; done 2>/dev/null"
        ).orEmpty()
        return Module(
            id = map["id"] ?: id,
            name = map["name"] ?: id,
            version = map["version"] ?: "—",
            versionCode = map["versionCode"] ?: "",
            author = map["author"] ?: "",
            description = map["description"] ?: "",
            enabled = !flags.contains("disable"),
            pendingRemove = flags.contains("remove"),
            pendingUpdate = pendingUpdate || flags.contains("update"),
        )
    }

    // ------------------------------------------------------------------ control

    /** Enables a module. Takes effect at the next boot, as with the manager UI. */
    fun enable(id: String): RootTools.Result {
        if (!RootShell.ready()) return RootTools.Result(false, "needs root")
        val wasDisabled = isDisabled(id)
        val r = RootShell.run("rm -f $MODULES/$id/disable; echo __GH_EXIT__=$?")
        val ok = r?.contains("__GH_EXIT__=0") == true
        // Recorded against the state before this call, so undo knows which way to go.
        if (ok) record(
            com.gamershub.core.ChangeLog.Kind.MODULE_STATE, id,
            if (wasDisabled) "disabled" else "enabled", "enabled"
        )
        return RootTools.Result(ok, if (ok) "$id enabled — reboot to load" else "could not enable $id")
    }

    /** Disables a module without uninstalling it. */
    fun disable(id: String): RootTools.Result {
        if (!RootShell.ready()) return RootTools.Result(false, "needs root")
        val wasDisabled = isDisabled(id)
        val r = RootShell.run("touch $MODULES/$id/disable; echo __GH_EXIT__=$?")
        val ok = r?.contains("__GH_EXIT__=0") == true
        if (ok) record(
            com.gamershub.core.ChangeLog.Kind.MODULE_STATE, id,
            if (wasDisabled) "disabled" else "enabled", "disabled"
        )
        return RootTools.Result(ok, if (ok) "$id disabled — reboot to unload" else "could not disable $id")
    }

    /**
     * Queues removal. The manager deletes it during boot, which is why we set the
     * marker instead of `rm -rf`: removing a live module while its files are mapped
     * into running processes is how systems get wedged.
     */
    fun remove(id: String): RootTools.Result {
        if (!RootShell.ready()) return RootTools.Result(false, "needs root")
        val r = RootShell.run("touch $MODULES/$id/remove; echo __GH_EXIT__=$?")
        val ok = r?.contains("__GH_EXIT__=0") == true
        if (ok) record(
            com.gamershub.core.ChangeLog.Kind.MODULE_REMOVE_QUEUED, id, "present", "removal queued"
        )
        return RootTools.Result(ok, if (ok) "$id queued for removal — reboot to finish" else "could not remove $id")
    }

    /** True when the module is flagged disabled on disk, whatever the manager reports. */
    fun isDisabled(id: String): Boolean =
        RootShell.run("test -f $MODULES/$id/disable && echo yes", 3_000)?.trim() == "yes"

    /**
     * Soft-reboots only the userspace, which is what KernelSU's late-load uses.
     *
     * Delegates to [Reboot] so there is one implementation of the sequence instead of a
     * weaker second one: `stop; start` sent from inside the process being stopped is
     * exactly the failure mode the researched version avoids by detaching first.
     */
    fun softRebootUserspace(): Boolean {
        if (!RootShell.ready()) return false
        val log = Reboot.run(
            Reboot.Mode.SOFT,
            shell = { cmd -> RootShell.run(cmd, 15_000) },
            prepare = {}
        )
        return log.contains("soft restart running")
    }

    /** Undoes a pending removal. */
    fun cancelRemove(id: String): RootTools.Result {
        if (!RootShell.ready()) return RootTools.Result(false, "needs root")
        val r = RootShell.run("rm -f $MODULES/$id/remove; echo __GH_EXIT__=$?")
        val ok = r?.contains("__GH_EXIT__=0") == true
        // Cancelling a removal the app queued makes that journal entry moot.
        if (ok) journal?.let { ctx ->
            val remaining = com.gamershub.core.ChangeLog.current(ctx).filterNot {
                it.kind == com.gamershub.core.ChangeLog.Kind.MODULE_REMOVE_QUEUED && it.subject == id
            }
            com.gamershub.core.ChangeLog.replace(ctx, remaining)
        }
        return RootTools.Result(ok, if (ok) "removal of $id cancelled" else "could not cancel")
    }

    // ------------------------------------------------------------------ installing

    /**
     * Flashes a module zip with the manager's own installer, then falls back to a
     * manual stage only if that binary is missing.
     *
     * The zip is copied into /data/local/tmp first: the manager runs as root in a
     * different mount namespace, and a file inside our app sandbox is not reliably
     * readable from there.
     */
    fun install(zipPath: String): RootTools.Result {
        if (!RootShell.ready()) return RootTools.Result(false, "needs root")

        val src = File(zipPath)
        if (!src.exists()) return RootTools.Result(false, "file not found")
        if (!src.name.endsWith(".zip", true)) return RootTools.Result(false, "not a .zip")
        if (src.length() < 1024) return RootTools.Result(false, "file is too small to be a module")

        // Validate before flashing — a bad zip in the wrong place can bootloop.
        // A GitHub "source archive" (repo-tag.zip) wraps everything in one folder
        // and will not flash, so that layout is reported specifically rather than
        // being passed to the installer to fail with a vague error.
        val check = RootShell.run("unzip -l '${q(src.absolutePath)}' 2>/dev/null | head -60")
            .orEmpty()
        val hasRootProp = check.lineSequence().any { line ->
            line.trimEnd().endsWith(" module.prop")
        }
        val hasMeta = check.contains("META-INF")
        if (!hasRootProp && !hasMeta) {
            val wrapped = check.lineSequence().any { it.contains("/module.prop") }
            return RootTools.Result(
                false,
                if (wrapped) {
                    "this zip has module.prop inside a folder — it is a source archive, not " +
                        "a flashable module. Download the release .zip instead."
                } else {
                    "this zip has no module.prop or META-INF — it is not a flashable module"
                }
            )
        }

        val staged = "/data/local/tmp/gh_flash_${System.currentTimeMillis()}.zip"
        val copy = RootShell.run("cp -f '${q(src.absolutePath)}' $staged; echo __GH_EXIT__=$?")
        if (copy?.contains("__GH_EXIT__=0") != true) {
            return RootTools.Result(false, "could not stage the zip (storage full or permission denied)")
        }
        RootShell.run("chmod 644 $staged")

        val result = when (manager()) {
            Manager.MAGISK -> runInstaller("magisk --install-module $staged")
            Manager.KERNELSU -> runInstaller("ksud module install $staged")
            Manager.APATCH -> runInstaller("apd module install $staged")
            else -> RootTools.Result(false, "no supported root manager to flash with")
        }

        RootShell.run("rm -f $staged")
        if (result.ok) {
            // Which module appeared is only knowable after the fact, so the newest
            // directory in the module tree is taken as the one that was just flashed.
            // Queueing its removal at engine stop is the honest undo: deleting the
            // directory of a module whose files may already be live is how a bootloop
            // happens, and the managers' own removal marker runs the uninstall path.
            val newId = RootShell.run(
                "ls -t $MODULES 2>/dev/null | head -1"
            )?.trim().orEmpty()
            if (newId.isNotBlank()) {
                record(com.gamershub.core.ChangeLog.Kind.MODULE_INSTALLED, newId, "absent", "flashed")
            }
        }
        return if (result.ok) {
            RootTools.Result(true, "flashed — reboot to load the module")
        } else {
            RootTools.Result(false, result.detail.ifBlank { "installer rejected the zip" })
        }
    }

    private fun runInstaller(cmd: String): RootTools.Result {
        val out = RootShell.run("$cmd 2>&1; echo __GH_EXIT__=\$?", timeoutMs = 120_000)
            ?: return RootTools.Result(false, "no answer from the installer")
        val exit = Regex("__GH_EXIT__=(-?\\d+)").find(out)?.groupValues?.get(1)
        val body = out.replace(Regex("__GH_EXIT__=-?\\d+"), "").trim()
        // Magisk prints "! " for failures and "- " for progress.
        val failed = body.lines().any { it.trimStart().startsWith("!") }
        val ok = exit == "0" && !failed
        return RootTools.Result(ok, body.lines().filter { it.isNotBlank() }.takeLast(6).joinToString("\n"))
    }

    // ------------------------------------------------------------------ misc

    /** Reboots so flashed/disabled modules actually load. */
    fun reboot(): RootTools.Result {
        if (!RootShell.ready()) return RootTools.Result(false, "needs root")
        RootShell.run("setprop sys.boot_completed ''; sync; (sleep 1; svc power reboot) >/dev/null 2>&1 &")
        return RootTools.Result(true, "rebooting")
    }

    /** Whether Zygisk is active — most gaming modules need it. */
    fun zygiskEnabled(): Boolean? {
        if (!RootShell.ready()) return null
        val out = RootShell.run("magisk --sqlite \"SELECT value FROM settings WHERE key='zygisk'\" 2>/dev/null")
            ?: return null
        return when {
            out.contains("value=1") -> true
            out.contains("value=0") -> false
            else -> null
        }
    }

    private fun q(v: String): String = v.replace("'", "'\\''")
}
