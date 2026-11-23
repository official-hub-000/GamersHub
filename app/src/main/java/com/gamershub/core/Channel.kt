package com.gamershub.core

import android.content.Context
import com.gamershub.core.root.RootShell
import com.gamershub.core.shizuku.Shell

/**
 * Which privilege channel is answering, and what each one can reach.
 *
 * The rule the app promises:
 *
 *  - **Root is optional.** With neither root nor Shizuku, GamersHub still runs:
 *    overlays, macros, the panel and capture are app-level features.
 *  - **A root user gets everything Shizuku can do, plus more.** Root implies
 *    [Capability.SHELL], so anything written against the shell path keeps working.
 *  - **A Shizuku-only user gets exactly the shell-reachable subset.** Features that
 *    genuinely need uid 0 are reported as unavailable instead of silently failing
 *    or, worse, being faked.
 *
 * [CAPABILITIES] is the single place that encodes that asymmetry, so a screen can
 * ask "can I do this?" instead of hardcoding `if (rooted)`. Anything that only
 * works as root is listed with that requirement and the reason.
 */
object Channel {

    /**
     * Which transport is answering.
     *
     * [WIRELESS] is deliberately not folded into [SHIZUKU]: they carry identical
     * capabilities, but the user needs to see which one is holding the shell, and the
     * fix differs when one drops.
     */
    enum class Kind { NONE, DAEMON, WIRELESS, SHIZUKU, ROOT }

    /** What a channel can actually reach. */
    enum class Capability {
        /** Run a shell command as the shell user (uid 2000) or root. */
        SHELL,

        /** Write system properties, including `ro.*` via resetprop. */
        PROPS,

        /** Read and write /data/adb — module directory, manager database. */
        ADB_DATA,

        /** Mount the module system (official KernelSU needs a metamodule). */
        MOUNT_MODULE,

        /** `pm disable-user` / `pm suspend` on other packages. */
        PACKAGE_CONTROL,

        /** Write kernel tunables: cpufreq, gpu governor, vm.*. */
        KERNEL_TUNABLES,

        /** `settings put` into any namespace. */
        SETTINGS_WRITE,

        /** Access another app's private data dir. */
        APP_DATA,
    }

    /**
     * Capabilities per channel. Root is a strict superset of Shizuku — that is the
     * invariant this table exists to keep true and visible.
     */
    private val CAPABILITIES: Map<Kind, Set<Capability>> = mapOf(
        Kind.NONE to setOf(),
        // The Shizuku shell runs as uid 2000 (shell). It is not root, so it cannot
        // write ro.* props, cannot enter /data/adb, and cannot write kernel nodes.
        Kind.SHIZUKU to setOf(
            Capability.SHELL,
            Capability.SETTINGS_WRITE,
            Capability.PACKAGE_CONTROL,
        ),
        // Wireless debugging runs `adbd` as uid 2000 — the same user, and therefore
        // the same reach, as Shizuku's server. Kept as an explicit copy rather than a
        // reference so that a future change to one cannot silently alter the other
        // without someone deciding to.
        Kind.WIRELESS to setOf(
            Capability.SHELL,
            Capability.SETTINGS_WRITE,
            Capability.PACKAGE_CONTROL,
        ),
        // The in-app daemon is the same uid 2000, so the same capability set — but it
        // is listed separately because it is the one that keeps answering with ADB
        // switched off, and that is worth being able to see in the panel.
        Kind.DAEMON to setOf(
            Capability.SHELL,
            Capability.SETTINGS_WRITE,
            Capability.PACKAGE_CONTROL,
        ),
        // Root reaches everything.
        Kind.ROOT to Capability.entries.toSet(),
    )

    /** Current channel, evaluated against live state rather than a cached flag. */
    fun kind(): Kind = when {
        RootShell.ready() -> Kind.ROOT
        com.gamershub.core.adb.ShellDaemon.alive -> Kind.DAEMON
        com.gamershub.core.adb.WirelessAdb.connected() -> Kind.WIRELESS
        Shell.available() -> Kind.SHIZUKU
        else -> Kind.NONE
    }

    fun label(): String = when (kind()) {
        Kind.ROOT -> "root"
        Kind.DAEMON -> "GamersHub daemon"
        Kind.WIRELESS -> "wireless debugging"
        Kind.SHIZUKU -> "Shizuku"
        Kind.NONE -> "no privilege"
    }

    /** True when the current channel can reach [cap]. */
    fun can(cap: Capability): Boolean = CAPABILITIES[kind()]?.contains(cap) == true

    /** Which features are blocked right now, with the reason. Drives the UI. */
    fun blocked(): List<Pair<Capability, String>> {
        val k = kind()
        return Capability.entries.filter { !can(it) }.map { cap ->
            cap to when (k) {
                Kind.NONE -> "needs the daemon, wireless debugging, Shizuku or root"
                Kind.DAEMON, Kind.WIRELESS, Kind.SHIZUKU ->
                    "needs root — the shell user cannot reach this"
                Kind.ROOT -> "unavailable"
            }
        }
    }

    /**
     * Runs [command] on the best available channel.
     *
     * Returns null when there is no channel at all, and also when the master
     * engine is off — a stopped engine must not be able to run privileged work
     * just because a caller asked nicely.
     */
    fun exec(context: Context, command: String, timeoutMs: Long = 8_000): String? {
        if (!Engine.isOn && !Engine.isStopping) return null
        // The timeout is forwarded on every branch. It used to be discarded three times
        // out of four — a caller asking for 8 s silently got Shell's default on the
        // daemon, wireless and Shizuku paths, and on Shizuku it got a hard-coded 1.5 s,
        // so the same command could succeed on one transport and "fail" on another.
        return when (kind()) {
            Kind.ROOT -> RootShell.run(command, timeoutMs)
            Kind.DAEMON -> Shell.exec(command, timeoutMs)     // routes to the daemon socket
            Kind.WIRELESS -> Shell.exec(command, timeoutMs)   // routes to the wireless link
            Kind.SHIZUKU -> Shell.exec(command, timeoutMs)
            Kind.NONE -> null
        }
    }

    fun ok(context: Context, command: String, timeoutMs: Long = 8_000): Boolean {
        val out = exec(context, "$command; echo __GH_EXIT__=$?", timeoutMs) ?: return false
        // A missing exit code is a failure. Returning true here meant a timed-out
        // command was reported as success to every caller that used ok().
        val m = Regex("__GH_EXIT__=(-?\\d+)").find(out) ?: return false
        return m.groupValues[1] == "0"
    }

    /** Capability-split execution: only runs if the channel can reach [cap]. */
    fun execIf(cap: Capability, context: Context, command: String, timeoutMs: Long = 8_000): String? =
        if (can(cap)) exec(context, command, timeoutMs) else null
}

/**
 * Human label for a capability, used when explaining why something is blocked.
 * Top-level so both the engine and the UI can name the same thing identically.
 */
val Channel.Capability.capabilityLabel: String
    get() = when (this) {
        Channel.Capability.SHELL -> "shell commands"
        Channel.Capability.PROPS -> "system properties"
        Channel.Capability.ADB_DATA -> "/data/adb"
        Channel.Capability.MOUNT_MODULE -> "module mounting"
        Channel.Capability.PACKAGE_CONTROL -> "package control"
        Channel.Capability.KERNEL_TUNABLES -> "kernel tunables"
        Channel.Capability.SETTINGS_WRITE -> "settings writes"
        Channel.Capability.APP_DATA -> "app private data"
    }
