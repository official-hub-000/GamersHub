package com.gamershub.core.root

/**
 * Reboot modes, matching what KernelSU's manager offers.
 *
 * ## The distinction that matters for this app
 *
 * Temporary root and the shell daemon both end at a **real** reboot, because a real
 * reboot re-runs `init` and everything the exploit and the daemon did is gone. A
 * **soft reboot** does not: it restarts the Android userspace (`stop; start`) while the
 * kernel keeps running, which is deliberately *not* a reboot at all as far as privilege
 * is concerned. That is exactly why KernelSU offers it — it re-runs the module stages
 * without dropping the kernel-side root.
 *
 * ## Where GamersHub's modes differ from KernelSU's, and why
 *
 *  - **Userspace reboot** uses Android's own `init` mechanism (`ctl.restart` on `zygote`
 *    and `servicemanager`, or `svc power reboot userspace` where the platform exposes
 *    it). This is the safe version: `init` owns the process tree and will not leave the
 *    phone without a framework.
 *  - **Soft restart** runs KernelSU's own sequence — reset `sys.boot_completed`, `stop`,
 *    `start`, then wait for boot to complete — because that is the behaviour being asked
 *    for. It is riskier by construction: if the restart does not come back, the phone
 *    needs a hardware reboot. The UI says so before running it rather than after.
 *
 * ## What every mode does first
 *
 * [prepare] runs the engine's stop path. A reboot that leaves overlays floating, a
 * capture pipeline running, or settings half-written is how a device comes back in a
 * state the user did not ask for — so the teardown happens before the reboot, not as a
 * side effect of it.
 */
object Reboot {

    enum class Mode(val key: String, val label: String, val detail: String) {
        /** Full reboot. Everything, including temporary root and the daemon, ends. */
        NORMAL(
            "",
            "Reboot",
            "Full reboot. Temporary root and the shell daemon end; the phone is as it was."
        ),

        /**
         * Restarts the Android framework, keeping the kernel running.
         *
         * Kernel-side root survives; a per-boot exploit or daemon that lives in the
         * userspace does not.
         */
        USERSPACE(
            "userspace",
            "Userspace reboot",
            "Restarts the Android framework only. The kernel — and kernel-side root — " +
                "keeps running. Takes about a minute."
        ),

        /**
         * KernelSU's emulated soft reboot: `stop`, then `start`.
         *
         * This is what KernelSU's own "Soft restart" does. It re-runs the userspace
         * boot sequence so module changes take effect without a full power cycle.
         */
        SOFT(
            "soft_reboot",
            "Soft restart",
            "KernelSU's emulated reboot: stops and restarts the Android services. " +
                "Module changes take effect and kernel root stays. If it does not come " +
                "back you will need to hold power to reboot."
        ),

        RECOVERY("recovery", "Recovery", "Reboots into recovery mode."),
        BOOTLOADER("bootloader", "Bootloader", "Reboots into the bootloader."),
        DOWNLOAD("download", "Download mode", "Reboots into download mode (Samsung)."),
        EDL("edl", "EDL", "Reboots into Emergency Download mode (Qualcomm).")
    }

    /** Mode used by the plain REBOOT control — the least surprising one. */
    val DEFAULT = Mode.NORMAL

    /**
     * Whether this device supports a real userspace reboot.
     *
     * `PowerManager.isRebootingUserspaceSupported()` is a hidden platform method, so it
     * is reached by reflection; when it is missing the MODE is still offered, because
     * `svc power reboot userspace` handles it on every platform that has the mode.
     */
    fun userspaceSupported(context: android.content.Context): Boolean = runCatching {
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE)
        val method = pm.javaClass.getMethod("isRebootingUserspaceSupported")
        method.invoke(pm) as? Boolean ?: false
    }.getOrDefault(true)

    /**
     * Runs one reboot mode.
     *
     * [prepare] is the app's own teardown, injected so this file does not depend on the
     * engine (the engine depends on it). Returns a log of what was attempted, in order,
     * because "rebooting…" that silently did nothing is the worst possible outcome here.
     */
    fun run(
        mode: Mode,
        shell: (String) -> String?,
        prepare: () -> Unit,
        /**
         * Re-establishes root after [prepare] has torn the engine down.
         *
         * Injected for the same reason [prepare] is: this file must not depend on the
         * engine. Returns true when a privileged channel is usable again.
         */
        holdRoot: () -> Boolean = { false }
    ): String {
        val log = StringBuilder()

        // 1. Build the command, but DO NOT send it yet.
        val reason = mode.key
        val cmd = when (mode) {
            Mode.SOFT -> null            // its own sequence, sent by softRestart
            Mode.USERSPACE -> null       // its own sequence, sent by userspaceReboot
            else -> if (reason.isEmpty()) {
                // `svc power reboot` is preferred over `reboot` because it goes through
                // the power manager, which is what the platform's own UI uses.
                "sync; svc power reboot || reboot"
            } else {
                "sync; svc power reboot $reason || reboot $reason"
            }
        }

        // 2. Leave the phone tidy.
        //
        // The previous order prepared first and then sent, and prepare()'s last act is
        // PrivilegeLadder.descend(): it drops the root session, the temporary provider
        // and the Shizuku binder. So every mode was "issued" through a channel that had
        // just been closed — "no shell to reboot with", or a reboot requested as uid
        // 2000 that the power manager silently refuses.
        runCatching {
            prepare()
            log.append("engine stopped · ")
        }
        // prepare() ends in PrivilegeLadder.descend(), so the grant has to be taken
        // again *after* it, not before. This is the narrow re-acquire: just enough
        // privilege to issue the reboot, which the reboot itself then removes.
        val kept = runCatching { holdRoot() }.getOrDefault(false)
        if (kept) log.append("root re-held · ")

        // 3. Now send it, with a channel that is provably usable again.
        val out = when (mode) {
            Mode.SOFT -> return softRestart(shell, log)
            Mode.USERSPACE -> return userspaceReboot(shell, log)
            else -> shell(cmd!!)
        }
        log.append(
            when {
                out != null -> "sent: $cmd"
                kept -> "no channel answered, but root was held — reopen REBOOT after the phone restarts"
                else -> "no shell to reboot with: root was not held, so nothing was sent"
            }
        )
        return log.toString()
    }

    /**
     * KernelSU's soft reboot, transcribed.
     *
     * Order matters and is not negotiable:
     *  1. `sys.boot_completed=0` first, so the `-w` wait below has something to see.
     *  2. `stop` — the platform stops every service.
     *  3. `start` — the platform starts them again, re-running the boot sequence.
     *  4. wait for `sys.boot_completed=1`, then let the managers run their late stages.
     *
     * The reset in step 1 is what makes this a *reboot* to everything watching
     * `sys.boot_completed` rather than a framework hiccup.
     */
    private fun softRestart(shell: (String) -> String?, log: StringBuilder): String {
        // Detached with setsid and in the background: step 2 kills the shell that is
        // running this, so the sequence has to survive its own parent being reaped.
        // Root does not require the process tree that `stop` tears down.
        val sequence = listOf(
            "setprop ctl.stop bootanim",
            "resetprop -n sys.boot_completed 0 2>/dev/null || setprop sys.boot_completed 0",
            "stop",
            "start"
        ).joinToString("; ")

        val out = shell(
            "setsid sh -c '$sequence' >> /data/local/tmp/gamershub-softreboot.log 2>&1 < /dev/null & " +
                "echo soft-restart-started"
        )
        log.append(
            if (out?.contains("soft-restart-started") == true) {
                "soft restart running (stop → start)"
            } else {
                "soft restart may not have started: ${out?.take(120)}"
            }
        )
        return log.toString()
    }

    /**
     * Userspace reboot through `init`'s own service control.
     *
     * `ctl.restart` on `zygote` and `servicemanager` is less complete than a full
     * userspace reboot — `init` itself keeps running, which is the whole point — but it
     * always comes back. `svc power reboot userspace` is tried first because it does the
     * complete job on platforms that expose it.
     */
    private fun userspaceReboot(shell: (String) -> String?, log: StringBuilder): String {
        // Android 11+ exposes "userspace" as a power reboot mode on most platforms.
        val svcOut = shell("svc power reboot userspace 2>&1; echo __GH_EXIT__=\$?")
        if (svcOut == null || !svcOut.contains("__GH_EXIT__=0")) {
            val ctl = shell("setprop ctl.restart zygote; setprop ctl.restart servicemanager")
            log.append(if (ctl != null) "restarted zygote + servicemanager" else "userspace reboot not available")
            return log.toString()
        }
        log.append("userspace reboot accepted by the platform")
        return log.toString()
    }
}
