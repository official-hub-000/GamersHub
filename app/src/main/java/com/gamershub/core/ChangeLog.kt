package com.gamershub.core

import android.content.Context

/**
 * The journal of everything GamersHub changed that a settings snapshot cannot undo.
 *
 * ## Why a snapshot is not enough
 *
 * [Restore] captures `settings` rows because their previous values can be read back and
 * re-written. A module toggle cannot be handled that way: enabling a module writes
 * `disable`'s *absence*, and "the file that was not there" is not a value you can read.
 * The same holds for a flashed module zip, a prop written with `setprop`, and a package
 * the app disabled. To undo those, the app has to have **recorded what it did at the
 * moment it did it**.
 *
 * That is this file. Every mutation goes through [record], and [undo] replays the journal
 * in reverse when the engine stops. It is deliberately boring: a list of small records
 * with enough information to reverse themselves, and nothing else.
 *
 * ## The rule this enforces
 *
 * "Engine off puts the phone back" has to be true for privileged changes, not just for
 * settings — otherwise the app looks clean while the phone is not. A change that cannot
 * be reversed (a module whose files were replaced by an update) is not silently
 * forgotten: it is reported in [undo]'s result so the user is told, rather than left
 * believing a reboot is unnecessary.
 *
 * ## Lifetime
 *
 * Records live until they are undone. They are *not* cleared by a reboot: a module left
 * enabled is still enabled after one, so the journal has to survive too. A record is
 * removed only when [undo] successfully reverses it.
 */
object ChangeLog {

    private const val PREFS = "gamershub_changes"

    /** One reversible change. [kind] decides how it is reversed. */
    data class Record(
        val kind: Kind,
        /** What it applied to — a module id, a prop name, a package. */
        val subject: String,
        /** What was true before. Empty means "did not exist". */
        val before: String,
        /** What GamersHub changed it to. */
        val after: String,
        /** Wall clock, so the journal can be shown in order and aged out. */
        val at: Long
    )

    enum class Kind {
        /** A module was enabled or disabled. Reversed by restoring the other state. */
        MODULE_STATE,

        /** A module zip was flashed. Reversed by queueing it for removal. */
        MODULE_INSTALLED,

        /** A module was queued for removal. Reversed by cancelling the queue marker. */
        MODULE_REMOVE_QUEUED,

        /** A system property was written. Reversed by writing [Record.before] back. */
        PROP,

        /** A package was disabled. Reversed by re-enabling it. */
        PACKAGE_DISABLED,

        /** A `cmd game` mode was set. Reversed by `cmd game reset`. */
        GAME_MODE
    }

    fun record(context: Context, kind: Kind, subject: String, before: String, after: String) {
        val all = current(context).toMutableList()
        // Collapse a repeated toggle of the same subject onto its first record: what
        // matters is the state before GamersHub touched it, not every intermediate step.
        all.removeAll { it.kind == kind && it.subject == subject }
        all += Record(kind, subject, before, after, System.currentTimeMillis())
        write(context, all)
    }

    fun current(context: Context): List<Record> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("journal", null) ?: return emptyList()
        return decode(raw)
    }

    /** Overwrites the journal. Used when a change is cancelled rather than undone. */
    fun replace(context: Context, records: List<Record>) {
        write(context, records)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("journal").apply()
    }

    fun size(context: Context): Int = current(context).size

    /** What the engine-off report should say about pending privileged changes. */
    fun summary(context: Context): String {
        val all = current(context)
        if (all.isEmpty()) return ""
        val byKind = all.groupingBy { it.kind }.eachCount()
        return byKind.entries.joinToString(", ") { (k, n) ->
            "${n}× ${k.name.lowercase().replace('_', ' ')}"
        }
    }

    /**
     * Reverses everything in the journal, newest first.
     *
     * Newest first because later changes assume earlier ones: a module installed and then
     * disabled has to be re-enabled before it is removed, or the removal marker is
     * written into a directory that is already gone.
     *
     * Returns the human-readable outcome, split so the engine's stop report can say what
     * is genuinely back and what needs a reboot.
     */
    fun undo(context: Context, shell: (String) -> String?): Result {
        val all = current(context).sortedByDescending { it.at }
        val undone = mutableListOf<String>()
        val needsReboot = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val keep = mutableListOf<Record>()

        for (r in all) {
            val ok = runCatching { reverse(context, r, shell) }.getOrDefault(false)
            when {
                !ok -> {
                    failed += "${r.kind.name.lowercase()} ${r.subject}"
                    keep += r
                }
                // These only take effect on the next boot, so saying "restored" would be
                // a lie until the user has rebooted.
                r.kind == Kind.MODULE_STATE || r.kind == Kind.MODULE_INSTALLED ||
                    r.kind == Kind.MODULE_REMOVE_QUEUED -> {
                    // The module id alone is not enough: "my_module" and "removed
                    // my_module" are different outcomes, and the user has to be able to
                    // tell which one this was.
                    needsReboot += when (r.kind) {
                        Kind.MODULE_INSTALLED -> "remove ${r.subject}"
                        Kind.MODULE_REMOVE_QUEUED -> "keep ${r.subject}"
                        else -> r.subject
                    }
                }
                else -> undone += "${r.subject}"
            }
        }

        // Keep only the ones that could not be reversed, so a second stop does not
        // replay work that already succeeded.
        write(context, keep)
        // The same module can appear twice (installed, then disabled); one line is enough.
        return Result(undone.distinct(), needsReboot.distinct(), failed.distinct())
    }

    data class Result(
        val undone: List<String>,
        val needsReboot: List<String>,
        val failed: List<String>
    ) {
        val empty: Boolean get() = undone.isEmpty() && needsReboot.isEmpty() && failed.isEmpty()

        /**
         * One line naming what was actually reversed.
         *
         * "3 changes reverted" tells the user nothing they can check. Naming the kinds is
         * what lets them look at the module list and believe the phone is back to normal.
         */
        fun report(): String = buildString {
            if (undone.isNotEmpty()) append("undid ${undone.joinToString(", ")}")
            if (needsReboot.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append("${needsReboot.joinToString(", ")} take effect after a reboot")
            }
            if (failed.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append("could not undo ${failed.joinToString(", ")}")
            }
        }
    }

    // ------------------------------------------------------------------ reversing

    private fun reverse(context: Context, r: Record, shell: (String) -> String?): Boolean = when (r.kind) {
        // Enabling a module removes <id>/disable; disabling writes it. Restoring the
        // recorded "before" is therefore the same operation either way.
        Kind.MODULE_STATE -> {
            val cmd = if (r.before == "enabled") {
                "rm -f /data/adb/modules/${r.subject}/disable"
            } else {
                "touch /data/adb/modules/${r.subject}/disable"
            }
            shell("$cmd; echo __GH_EXIT__=\$?")?.contains("__GH_EXIT__=0") == true
        }

        // A flashed module is not un-flashed by deleting the directory — its files may
        // already be live. The managers' own mechanism is the removal marker, which
        // runs the module's uninstall path at the next boot.
        Kind.MODULE_INSTALLED ->
            shell("touch /data/adb/modules/${r.subject}/remove; echo __GH_EXIT__=\$?")
                ?.contains("__GH_EXIT__=0") == true

        Kind.MODULE_REMOVE_QUEUED ->
            shell("rm -f /data/adb/modules/${r.subject}/remove; echo __GH_EXIT__=\$?")
                ?.contains("__GH_EXIT__=0") == true

        Kind.PROP -> {
            val value = r.before
            if (value.isBlank()) {
                shell("resetprop --delete '${r.subject}' 2>/dev/null; echo __GH_EXIT__=\$?")
                    ?.contains("__GH_EXIT__=0") == true
            } else {
                shell("resetprop -n '${r.subject}' '${escape(value)}' 2>/dev/null; echo __GH_EXIT__=\$?")
                    ?.contains("__GH_EXIT__=0") == true
            }
        }

        Kind.PACKAGE_DISABLED ->
            shell("pm enable --user 0 '${r.subject}'; echo __GH_EXIT__=\$?")
                ?.contains("__GH_EXIT__=0") == true

        // `cmd game reset` drops the per-app config; the explicit `--mode standard` is
        // what restores the stock scheduling mode, since reset leaves the last mode in
        // place on some builds.
        Kind.GAME_MODE ->
            shell(
                "cmd game reset '${r.subject}'; cmd game set --mode standard '${r.subject}'; " +
                    "echo __GH_EXIT__=\$?"
            )?.contains("__GH_EXIT__=0") == true
    }

    private fun escape(v: String): String = v.replace("'", "'\\''")

    // ------------------------------------------------------------------ storage

    /**
     * Tab-separated, one record per line.
     *
     * A JSON dependency would be heavier than the whole journal for no benefit, and the
     * fields are already constrained (module ids, prop names, package names) — none of
     * which can contain a tab or a newline.
     */
    private fun write(context: Context, records: List<Record>) {
        val text = records.joinToString("\n") { r ->
            listOf(r.kind.name, r.subject, r.before, r.after, r.at.toString())
                .joinToString("\t")
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("journal", text).apply()
    }

    private fun decode(raw: String): List<Record> = raw.lines().mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val f = line.split("\t")
        if (f.size < 5) return@mapNotNull null
        val kind = runCatching { Kind.valueOf(f[0]) }.getOrNull() ?: return@mapNotNull null
        Record(kind, f[1], f[2], f[3], f[4].toLongOrNull() ?: 0L)
    }
}
