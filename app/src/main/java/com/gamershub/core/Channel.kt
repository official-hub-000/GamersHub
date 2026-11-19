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

    enum class Kind { NONE, SHIZUKU, ROOT }

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
        // Root reaches everything.
        Kind.ROOT to Capability.entries.toSet(),
    )

    /** Human label for the capability, used in the UI when explaining a block. */
    val Capability.label: String
        get() = when (this) {
            Capability.SHELL -> "shell commands"
            Capability.PROPS -> "system properties"
            Capability.ADB_DATA -> "/data/adb"
            Capability.MOUNT_MODULE -> "module mounting"
            Capability.PACKAGE_CONTROL -> "package control"
            Capability.KERNEL_TUNABLES -> "kernel tunables"
            Capability.SETTINGS_WRITE -> "settings writes"
            Capability.APP_DATA -> "app private data"
        }

    /** Current channel, evaluated against live state rather than a cached flag. */
    fun kind(): Kind = when {
        RootShell.ready() -> Kind.ROOT
        Shell.available() -> Kind.SHIZUKU
        else -> Kind.NONE
    }

    fun label(): String = when (kind()) {
        Kind.ROOT -> "root"
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
                Kind.NONE -> "needs Shizuku or root"
                Kind.SHIZUKU -> "needs root — Shizuku runs as the shell user"
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
        return when (kind()) {
            Kind.ROOT -> RootShell.run(command, timeoutMs)
            Kind.SHIZUKU -> Shell.exec(command)
            Kind.NONE -> null
        }
    }

    fun ok(context: Context, command: String): Boolean {
        val out = exec(context, "$command; echo __GH_EXIT__=$?") ?: return false
        val m = Regex("__GH_EXIT__=(-?\\d+)").find(out) ?: return true
        return m.groupValues[1] == "0"
    }

    /** Capability-split execution: only runs if the channel can reach [cap]. */
    fun execIf(cap: Capability, context: Context, command: String, timeoutMs: Long = 8_000): String? =
        if (can(cap)) exec(context, command, timeoutMs) else null
}
