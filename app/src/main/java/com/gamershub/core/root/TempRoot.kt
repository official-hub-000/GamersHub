package com.gamershub.core.root

import android.content.Context

/**
 * Temporary root — what GamersHub can and cannot do about it.
 *
 * ## What "temporary root" means here
 *
 * A *persistent* root (Magisk, KernelSU, APatch) installs a daemon that survives
 * reboots. A *temporary* root gains uid 0 for the current boot only, and it does so by
 * exploiting a kernel bug from inside an unprivileged app.
 *
 * ## Why this app does not ship an exploit
 *
 * The reference project (DFRoot, CVE-2026-43284 "DirtyFrag") is a kernel page-cache
 * write primitive: it decrypts crafted AES-CBC ESP packets straight into the page cache
 * of a `splice()`d file, which lets it patch shellcode into `libc++.so` with no write
 * permission and no copy-on-write. That shellcode fires inside init (uid 0, tid 1) and
 * `insmod`s a kernel module that sets `selinux_state` permissive.
 *
 * There are three concrete reasons it cannot be folded into a Play-style release of
 * this app, and pretending otherwise would be worse than saying so:
 *
 *  1. **It needs a native toolchain and per-kernel offsets.** DFRoot is 59% C plus 13%
 *     assembly and ships a prebuilt `.ko` per kernel family. This repo has no NDK
 *     build, and a wrong offset is not a failed exploit, it is a bricked phone.
 *  2. **It requires the exact kernel.** The exploit is dead on patched kernels and on
 *     any vendor that backported the fix. Shipping it would hand most users a button
 *     that fails — which is exactly the "is it real?" problem this app avoids.
 *  3. **It is a security hazard as a library.** It lands a world-writable page-cache
 *     primitive and a permissive-SELinux module in *your* phone. That is a permanent
 *     hole for every other app on the device, traded for a root shell.
 *
 * ## What this class does instead
 *
 * It detects and reports — honestly — whether a temporary root path is plausible on
 * *this* device, and it manages the root this app actually has:
 *
 *  - **detection**: kernel version, SELinux mode, whether `su` came from a manager or
 *    from a one-shot bootstrap, and whether root survives a reboot
 *  - **session hold**: keeps the current root shell alive for the engine session
 *  - **release**: drops root completely when the master engine goes off, so a
 *    "temporary" root really is temporary and does not outlive the switch
 *
 * If a device already has DFRoot-style root installed, everything in the Root tab works
 * against it: GamersHub only needs a working `su`, not a specific manager.
 */
object TempRoot {

    /** How root, if any, is currently held. */
    enum class Kind(val label: String) {
        /** No root. */
        NONE("none"),
        /** A manager-granted root session that dies with the app process. */
        SESSION("session"),
        /** A manager-granted root that persists across reboots. */
        PERSISTENT("persistent"),
        /** A root that will not survive a reboot — typically an exploit bootstrap. */
        TEMPORARY("temporary")
    }

    /** The environment facts the report is built from. All read from the device. */
    data class Facts(
        val rootReady: Boolean,
        val kind: Kind,
        val kernel: String,
        val selinux: String,
        val suPath: String?,
        val manager: String,
        val uptime: String,
        val temporaryLikely: Boolean,
        val notes: List<String>
    )

    /**
     * Reads the device. Every field is a real query — nothing here is inferred from
     * a stored flag, because a stale flag is how a "root status" panel starts lying.
     */
    fun inspect(context: Context): Facts {
        val ready = RootShell.ready()
        val kernel = RootShell.run("uname -r 2>/dev/null", 3_000)?.trim().orEmpty().ifBlank { "unknown" }
        val selinux = RootShell.run("getenforce 2>/dev/null", 3_000)?.trim().orEmpty().ifBlank { "unknown" }
        val uptimeRaw = RootShell.run("cat /proc/uptime 2>/dev/null", 3_000)?.trim().orEmpty()
        val uptime = formatUptime(uptimeRaw)
        val su = RootShell.suBinary()
        val manager = runCatching { ModuleManager.managerName() }.getOrDefault("none")

        // A manager binary in /data/adb means the root is installed, not bolted on.
        val managerBinary = RootShell.run(
            "if [ -x /data/adb/magisk/magisk ] || [ -x /data/adb/ksu/bin/ksud ] || " +
                "[ -x /data/adb/apd ]; then echo yes; else echo no; fi", 4_000
        )?.trim() == "yes"

        // Temporary root fingerprints: su that is not from a manager, and a module
        // directory that appeared after boot rather than at it.
        val moduleDir = RootShell.run("ls -d /data/adb/modules 2>/dev/null", 3_000)?.trim().orEmpty()
        val temporaryLikely = ready && !managerBinary && moduleDir.isBlank()

        val kind = when {
            !ready -> Kind.NONE
            temporaryLikely -> Kind.TEMPORARY
            managerBinary -> Kind.PERSISTENT
            else -> Kind.SESSION
        }

        val notes = buildList {
            if (!ready) add("No root session. Grant it below, or use Shizuku — the engine works either way.")
            if (ready && managerBinary) add("A root manager owns su, so this root survives a reboot.")
            if (ready && temporaryLikely)
                add("Root is live but no manager owns it — this looks like a per-boot bootstrap.")
            if (selinux == "Permissive")
                add("SELinux is permissive. That is a device-wide security hole; it was not set by GamersHub.")
            if (selinux == "Enforcing" && ready) add("SELinux enforcing — the safer of the two states.")
            add("Kernel $kernel")
        }
        return Facts(
            rootReady = ready,
            kind = kind,
            kernel = kernel,
            selinux = selinux,
            suPath = su,
            manager = manager,
            uptime = uptime,
            temporaryLikely = temporaryLikely,
            notes = notes
        )
    }

    private fun formatUptime(raw: String): String {
        val secs = raw.split(' ').firstOrNull()?.toDoubleOrNull() ?: return "unknown"
        val m = (secs / 60).toLong()
        val h = m / 60
        return when {
            h > 0 -> "${h}h ${m % 60}m"
            m > 0 -> "${m}m"
            else -> "${secs.toLong()}s"
        }
    }

    /**
     * Drops the root session and the temporary provider.
     *
     * This is what makes "temporary root" actually temporary in this app: the master
     * engine calls it on every stop, so a root gained for one session cannot quietly
     * outlive the switch.
     */
    fun release(context: Context): String = runCatching {
        RootShell.releaseAll()
        RootShell.removeProvider(context)
        "temporary root released"
    }.getOrElse { "release failed: ${it.message}" }

    /** True when releasing would actually do something. */
    fun isHolding(): Boolean = RootShell.ready()
}
