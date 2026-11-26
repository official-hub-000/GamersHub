package com.gamershub.core

import android.content.Context
import com.gamershub.core.shizuku.Shell

/**
 * Which shell channel is answering, and what each one can reach.
 *
 * There is no root. GamersHub never acquires uid 0; the strongest channel is
 * the shell user (uid 2000).
 *
 * The rule the app promises:
 *
 *  - **Shell is optional.** With no Shizuku, GamersHub still runs: overlays, macros,
 *    the panel and capture are app-level features.
 *  - **A shell user gets exactly the shell-reachable subset.** Features that
 *    would need uid 0 are not part of the app; nothing is silently faked.
 *
 * [CAPABILITIES] is the single place that encodes what each channel can do,
 * so a screen can ask "can I do this?" instead of hardcoding transport checks.
 */
object Channel {

    /**
     * Which transport is answering. Shizuku is the only transport kept;
     * wireless debugging and the in-app shell daemon are removed.
     */
    enum class Kind { NONE, SHIZUKU }

    /** What a channel can actually reach. */
    enum class Capability {
        /** Run a shell command as the shell user (uid 2000). */
        SHELL,

        /** `pm disable-user` / `pm suspend` on other packages. */
        PACKAGE_CONTROL,

        /** `settings put` into any namespace. */
        SETTINGS_WRITE,
    }

    /** Capabilities per channel. The shell user never reaches uid 0. */
    private val CAPABILITIES: Map<Kind, Set<Capability>> = mapOf(
        Kind.NONE to setOf(),
        // The Shizuku shell runs as uid 2000 (shell).
        Kind.SHIZUKU to setOf(
            Capability.SHELL,
            Capability.SETTINGS_WRITE,
            Capability.PACKAGE_CONTROL,
        ),
    )

    /** Current channel, evaluated against live state rather than a cached flag. */
    fun kind(): Kind = when {
        Shell.available() -> Kind.SHIZUKU
        else -> Kind.NONE
    }

    fun label(): String = when (kind()) {
        Kind.SHIZUKU -> "Shizuku"
        Kind.NONE -> "no privilege"
    }

    /** True when the current channel can reach [cap]. */
    fun can(cap: Capability): Boolean = CAPABILITIES[kind()]?.contains(cap) == true

    /** Which capabilities are blocked right now, with the reason. Drives the UI. */
    fun blocked(): List<Pair<Capability, String>> {
        val k = kind()
        return Capability.entries.filter { !can(it) }.map { cap ->
            cap to when (k) {
                Kind.NONE -> "needs Shizuku"
                Kind.SHIZUKU -> "unavailable"
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

/**
 * Human label for a capability, used when explaining why something is blocked.
 * Top-level so both the engine and the UI can name the same thing identically.
 */
val Channel.Capability.capabilityLabel: String
    get() = when (this) {
        Channel.Capability.SHELL -> "shell commands"
        Channel.Capability.PACKAGE_CONTROL -> "package control"
        Channel.Capability.SETTINGS_WRITE -> "settings writes"
    }
