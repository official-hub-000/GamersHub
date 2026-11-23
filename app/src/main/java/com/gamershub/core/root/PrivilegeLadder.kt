package com.gamershub.core.root

import android.content.Context

/**
 * The privilege ladder: how GamersHub gets from "no privilege" to uid 0, and how it
 * gives it back.
 *
 * The user-facing model is the one DFRoot / KernelSU describe, and it is two layers:
 *
 *   **Layer 1 — temporary root.** uid 0 for the current session only. Acquired when
 *   the engine starts, released the moment it stops, never persisted. This is what a
 *   kernel-bug bootstrap gives you (DFRoot), and it is also what a plain `su` grant
 *   gives you if you choose not to keep it.
 *
 *   **Layer 2 — the manager.** Superuser policy for other apps, and module zips.
 *   That is the KernelSU / Magisk / APatch job, and [SuManager] + [ModuleManager]
 *   already drive it.
 *
 * GamersHub does not ship a kernel exploit (see [TempRoot] for why). What it does
 * ship is the *ladder*: it tries every legitimate way to reach uid 0, in order, and
 * reports the exact step that succeeded — so a device with a temporary root from
 * DFRoot, or a persistent one from KernelSU, both land in the same place.
 *
 * Every step here is real. Nothing reports success it did not verify with `id`.
 */
object PrivilegeLadder {

    /** How the current root session was obtained. */
    enum class Rung(val label: String, val detail: String) {
        /** No root and no shell. */
        NONE("none", "no privilege"),

        /** App-level only: overlays, macros, capture. No system writes. */
        APP("app", "app-level features only"),

        /** Shizuku: shell (uid 2000). Everything except genuine uid 0 work. */
        SHIZUKU("shizuku", "shell subset via Shizuku"),

        /**
         * uid 2000 over the phone's own adbd.
         *
         * Ranked beside Shizuku because it *is* the same privilege — same uid, same
         * SELinux domain — reached with no second app installed. Its own rung so the
         * UI can say which shell answered, since the fix differs when one drops.
         */
        WIRELESS("wireless", "wireless debugging — uid 2000, no Shizuku needed"),

        /**
         * The in-app daemon ([com.gamershub.core.adb.ShellDaemon]).
         *
         * Same uid 2000 as Shizuku, in a process of its own rather than a live ADB
         * connection — so it keeps answering after wireless debugging is switched off.
         * This is the rung that behaves like a running Shizuku server.
         */
        DAEMON("daemon", "GamersHub daemon — uid 2000, survives ADB being turned off"),

        /** Root through a manager that persists across reboots. */
        PERSISTENT_ROOT("root", "persistent root"),

        /** Root that was granted for this session and is dropped when the engine stops. */
        TEMP_ROOT("temp-root", "temporary root — released with the engine"),

        /**
         * Root that DFRoot bootstrapped this boot.
         *
         * Functionally identical to TEMP_ROOT — it does not survive a reboot — but it
         * is labelled separately because the user should be able to tell "I got this
         * from the exploit" apart from "I got this from a session hold".
         */
        DFROOT_ROOT("dfroot", "root via DFRoot — per boot"),

        /**
         * uid 0 through a shell that already holds it, usually Shizuku.
         *
         * It *is* root — [RootShell.run] routes commands through that shell — but no su
         * binary exists for this app, so it is not [PERSISTENT_ROOT]. Labelling it
         * persistent would claim a root install the phone does not have; labelling it
         * SHIZUKU would say uid 2000 when the commands really run as uid 0.
         */
        SHELL_ROOT("shell-root", "root held by the shell — not a su install")
    }

    /** One attempt on the ladder, kept so the UI can show what was tried. */
    data class Step(val name: String, val ok: Boolean, val note: String)

    data class Result(
        val rung: Rung,
        val steps: List<Step>,
        val suPath: String?
    ) {
        val reachedRoot: Boolean
            get() = rung == Rung.PERSISTENT_ROOT ||
                rung == Rung.TEMP_ROOT ||
                rung == Rung.DFROOT_ROOT ||
                rung == Rung.SHELL_ROOT
        fun summary(): String = "${
            rung.label
        } · ${steps.count { it.ok }}/${steps.size} steps"
    }

    /**
     * Climb as far as the device allows.
     *
     * [wantRoot] false stops at Shizuku — a user who has not asked for root is never
     * prompted for it, which is the "root is optional" rule this app already keeps.
     */
    fun climb(context: Context, wantRoot: Boolean = true, holdSession: Boolean = true): Result {
        val steps = mutableListOf<Step>()

        // ---- rung 0: is there a shell at all? ----
        // Wireless debugging first: it is the same uid 2000 as Shizuku, with no second
        // app in the chain. Reported as its own step so the panel says which answered.
        val daemonUp = com.gamershub.core.adb.ShellDaemon.alive
        steps += Step(
            "shell daemon",
            daemonUp,
            if (daemonUp) com.gamershub.core.adb.ShellDaemon.statusLine()
            else "not started — start it once over ADB, then it is independent"
        )
        val wirelessUp = com.gamershub.core.adb.WirelessAdb.connected()
        steps += Step(
            "wireless debugging",
            wirelessUp,
            if (wirelessUp) "uid 2000 via adbd on port ${com.gamershub.core.adb.WirelessAdb.shellPort}"
            else "not connected — Shizuku or a pairing code"
        )
        val shizukuUp = daemonUp || wirelessUp ||
            runCatching { com.gamershub.core.shizuku.Shell.available() }.getOrDefault(false)
        steps += Step("Shizuku binder", shizukuUp, if (shizukuUp) "shell reachable" else "not granted")

        if (!wantRoot) {
            return Result(if (shizukuUp) Rung.SHIZUKU else Rung.APP, steps, null)
        }

        // ---- rung 1: an already-live root session ----
        if (RootShell.ready()) {
            steps += Step("live root session", true, RootShell.method.ifBlank { "already held" })
            // ready() means the grant is held, not that the multiplexed shell is up.
            // Asking for a session here is what turns "granted" into "usable".
            if (holdSession) RootShell.hold(context)
            return Result(rungFor(), steps, RootShell.suBinary())
        }

        // ---- rung 2: the device's own su ----
        val acquired = runCatching { RootShell.acquire(context) }.getOrDefault(false)
        steps += Step(
            "device su",
            acquired,
            if (acquired) RootShell.method.ifBlank { "granted" }
            else RootShell.lastError.ifBlank { "no su, or it was denied" }
        )
        if (acquired) {
            if (holdSession) RootShell.hold(context)
            return Result(rungFor(), steps, RootShell.suBinary())
        }

        // ---- rung 3: su through Shizuku ----
        if (shizukuUp) {
            val viaShizuku = runCatching {
                com.gamershub.core.shizuku.Shell.exec("su -c id")?.contains("uid=0") == true
            }.getOrDefault(false)
            steps += Step("su via Shizuku", viaShizuku, if (viaShizuku) "shell holds the grant" else "denied")
            if (viaShizuku) {
                runCatching { RootShell.adoptShizukuRoot() }
                return Result(rungFor(), steps, RootShell.suBinary())
            }
        }

        // ---- rung 4: the uid-0 session DFRoot publishes ----
        // This is the rung that actually carries root after an exploit run. It is checked
        // before the shim below because it is a real uid 0 process, whereas the shim can
        // only re-exec a `su` that may not exist (see RootBridge for why it cannot exist
        // on a KernelSU kernel).
        if (dfrootHooked() || RootBridge.present(context)) {
            val bridged = runCatching { RootBridge.confirmRoot(context) }.getOrDefault(false)
            if (bridged) runCatching { RootShell.acquire(context) }
            steps += Step(
                "DFRoot uid 0 session",
                bridged,
                if (bridged) "answering on the bridge socket"
                else "socket not answering — run DFRoot again and leave the app open"
            )
            if (bridged) return Result(rungFor(), steps, null)
        }

        // ---- rung 5: the bundled temporary provider ----
        // This is a shim over the device's su, not a root exploit: it exists so the
        // app has one stable path to privileged work, and it is deleted with the engine.
        val provider = runCatching { RootShell.provisionTemporary(context) }.getOrDefault(false)
        steps += Step(
            "temporary provider",
            provider,
            if (provider) "session only — dropped when the engine stops"
            else "unavailable: needs a su on the device"
        )
        if (provider) {
            if (holdSession) RootShell.hold(context)
            return Result(Rung.TEMP_ROOT, steps, RootShell.suBinary())
        }

        return Result(if (shizukuUp) Rung.SHIZUKU else Rung.APP, steps, null)
    }

    private fun rungFor(): Rung = when {
        // Before temporarySession: the bridge also sets that flag (it is a per-boot grant),
        // and reporting "temporary root — released with the engine" for a DFRoot session
        // names the wrong mechanism. DFROOT_ROOT says where it came from.
        RootShell.bridgeMode() -> Rung.DFROOT_ROOT
        RootShell.temporarySession -> Rung.TEMP_ROOT
        dfrootHooked() -> Rung.DFROOT_ROOT
        // The grant may be Shizuku's rather than the device's own su. Saying
        // PERSISTENT_ROOT then would claim a root install the phone does not have, and
        // the whole point of the ladder is that each rung is labelled truthfully.
        RootShell.method.contains("Shizuku", ignoreCase = true) -> Rung.SHELL_ROOT
        else -> Rung.PERSISTENT_ROOT
    }

    /** True when this boot was patched by DFRoot, whether or not we started it. */
    private fun dfrootHooked(): Boolean = runCatching { DfRoot.hooked() }.getOrDefault(false)

    /**
     * Gives back everything the ladder climbed: drops the root session, deletes the
     * temporary provider, and disconnects the Shizuku binder.
     *
     * Called by the master engine on every stop. This is the half of "temporary root"
     * that makes the word *temporary* true.
     */
    fun descend(context: Context): String {
        val done = mutableListOf<String>()
        // A DFRoot-bootstrapped su daemon lives outside this app and cannot be stopped
        // by it, so say that rather than implying a full teardown happened.
        val wasDfroot = runCatching { DfRoot.hooked() }.getOrDefault(false)
        runCatching {
            RootShell.releaseAll()
            done += "root session"
        }
        runCatching {
            RootShell.removeProvider(context)
            done += "temp provider"
        }
        runCatching {
            com.gamershub.core.shizuku.Shell.disconnect()
            done += "Shizuku binder"
        }
        // The daemon is a process of ours, so unlike DFRoot's su daemon it really can
        // be stopped — and must be, or "engine off" would leave uid 2000 running.
        runCatching {
            com.gamershub.core.adb.ShellDaemon.stop(context)
            done += "shell daemon"
        }
        // Only the socket is dropped; the device keeps this app authorised, so the
        // next connect needs no pairing code. Tearing down the identity here would
        // mean every engine toggle forces the user to pair again.
        runCatching {
            if (com.gamershub.core.adb.WirelessAdb.connected()) {
                com.gamershub.core.adb.WirelessAdb.disconnect()
                done += "wireless debugging link"
            }
        }
        val released = if (done.isEmpty()) "nothing to release" else "released ${done.joinToString(", ")}"
        // Not a release — a caveat. Kept out of the released list so the engine's
        // stop report never claims to have stopped something it cannot.
        return if (wasDfroot) "$released; DFRoot's su daemon runs until reboot" else released
    }

    /** Current rung without doing anything. */
    fun standing(): Rung = when {
        RootShell.ready() -> rungFor()
        com.gamershub.core.adb.ShellDaemon.alive -> Rung.DAEMON
        com.gamershub.core.adb.WirelessAdb.connected() -> Rung.WIRELESS
        runCatching { com.gamershub.core.shizuku.Shell.available() }.getOrDefault(false) -> Rung.SHIZUKU
        else -> Rung.APP
    }
}
