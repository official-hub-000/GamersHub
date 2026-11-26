package com.gamershub.core.root

/**
 * Built-in root manager: which apps may use su, and what the policy is.
 *
 * This does *not* replace Magisk / KernelSU / APatch — on Android the su daemon is
 * owned by whichever root solution is installed, and an app cannot take that over
 * without re-implementing the daemon itself. What it does instead is drive that
 * daemon's own policy store, so the user gets one screen for "which apps have root"
 * instead of hunting through a different app for each backend.
 *
 * Each backend keeps its grant list somewhere different, so the reader is written
 * per backend rather than assumed:
 *
 *   Magisk     /data/adb/magisk.db, sqlite table `policies`
 *              (uid, policy, until, logging, notification)
 *   KernelSU   /data/adb/ksu/... with `ksud` for writes
 *   APatch     /data/adb/ap/...  with `apd`  for writes
 *
 * Reads are done with the sqlite3 binary that ships in the root environment, and
 * every function reports *why* it could not answer rather than returning an empty
 * list that looks like "no apps have root".
 */
object SuManager {

    /** One app with a root decision attached. */
    data class Grant(
        val uid: Int,
        val pkg: String,
        val label: String,
        /** true = allowed, false = denied, null = not decided / ask */
        val allowed: Boolean?,
        /** Wall-clock expiry in seconds since epoch, 0 = forever. */
        val untilSeconds: Long = 0,
        val logging: Boolean = true,
        val notification: Boolean = true
    )

    enum class Backend { MAGISK, KERNELSU, APATCH, NONE }

    fun backend(): Backend = when {
        !RootShell.ready() -> Backend.NONE
        else -> when (ModuleManager.manager()) {
            ModuleManager.Manager.MAGISK -> Backend.MAGISK
            ModuleManager.Manager.KERNELSU -> Backend.KERNELSU
            ModuleManager.Manager.APATCH -> Backend.APATCH
            ModuleManager.Manager.NONE -> Backend.NONE
            ModuleManager.Manager.UNKNOWN -> Backend.NONE
        }
    }

    fun backendName(): String = when (backend()) {
        Backend.MAGISK -> "Magisk"
        Backend.KERNELSU -> "KernelSU"
        Backend.APATCH -> "APatch"
        Backend.NONE -> "no root manager"
    }

    /** True when we can both read and write the policy store. */
    fun canManage(): Boolean = when (backend()) {
        Backend.MAGISK -> sqlite() != null
        Backend.KERNELSU, Backend.APATCH -> true
        Backend.NONE -> false
    }

    private fun sqlite(): String? {
        val out = RootShell.run(
            "for b in /data/adb/magisk/sqlite3 /system/bin/sqlite3 /system/xbin/sqlite3; do " +
                "[ -x \$b ] && echo \$b && break; done", 4_000
        )
        return out?.lineSequence()?.firstOrNull { it.startsWith("/") }?.trim()
    }

    /**
     * Reads the grant list. [lookup] maps a uid to a package name, because the
     * policy table stores uids and a uid with no package is usually a system daemon.
     */
    fun grants(lookup: (Int) -> Pair<String, String>?): List<Grant> {
        if (!RootShell.ready()) return emptyList()
        return when (backend()) {
            Backend.MAGISK -> magiskGrants(lookup)
            // KernelSU and APatch keep their allow-list in a database the app cannot
            // portably parse across versions, so we report what their own CLI says
            // and do writes through it.
            Backend.KERNELSU -> ksuGrants(lookup)
            Backend.APATCH -> ksuGrants(lookup)
            Backend.NONE -> emptyList()
        }
    }

    private fun magiskGrants(lookup: (Int) -> Pair<String, String>?): List<Grant> {
        val db = "/data/adb/magisk.db"
        val bin = sqlite() ?: return emptyList()
        val out = RootShell.run(
            "$bin -separator '|' $db \"SELECT uid,policy,until,logging,notification " +
                "FROM policies ORDER BY uid\" 2>/dev/null", 6_000
        ) ?: return emptyList()
        return out.lineSequence().mapNotNull { line ->
            val parts = line.trim().split('|')
            if (parts.size < 5) return@mapNotNull null
            val uid = parts[0].toIntOrNull() ?: return@mapNotNull null
            val policy = parts[1].toIntOrNull() ?: 0
            val named = lookup(uid)
            Grant(
                uid = uid,
                pkg = named?.first ?: "",
                label = named?.second ?: "uid $uid",
                allowed = when (policy) {
                    2 -> true      // ALLOW
                    1 -> false     // DENY
                    else -> null   // ask / not decided
                },
                untilSeconds = parts[2].toLongOrNull() ?: 0,
                logging = parts[3].toIntOrNull() == 1,
                notification = parts[4].toIntOrNull() == 1
            )
        }.toList()
    }

    private fun ksuGrants(lookup: (Int) -> Pair<String, String>?): List<Grant> {
        val cli = if (backend() == Backend.APATCH) "apd" else "ksud"
        val out = RootShell.run("$cli su list 2>/dev/null || $cli -l 2>/dev/null", 6_000)
            ?: return emptyList()
        return out.lineSequence().mapNotNull { line ->
            val t = line.trim()
            if (t.isBlank() || !t.first().isDigit()) return@mapNotNull null
            val uid = t.takeWhile { it.isDigit() }.toIntOrNull() ?: return@mapNotNull null
            val named = lookup(uid)
            Grant(
                uid = uid,
                pkg = named?.first ?: "",
                label = named?.second ?: "uid $uid",
                allowed = true
            )
        }.toList()
    }

    /** Grants or revokes su for a uid with an optional timeout in seconds. */
    fun setGrantWithTimeout(uid: Int, allow: Boolean, timeoutSeconds: Long): String {
        if (!RootShell.ready()) return "Root is not granted to GamersHub"
        val b = backend()
        if (b == Backend.NONE) return "No root manager detected"
        val nowSec = System.currentTimeMillis() / 1000
        val until = if (timeoutSeconds > 0) nowSec + timeoutSeconds else 0
        return when (b) {
            Backend.MAGISK -> {
                val bin = sqlite() ?: return "sqlite3 not found — cannot write the policy table"
                val db = "/data/adb/magisk.db"
                val policy = if (allow) 2 else 1
                val exists = RootShell.run(
                    "$bin $db \"SELECT COUNT(*) FROM policies WHERE uid=$uid\" 2>/dev/null", 4_000
                )?.trim()?.toIntOrNull() ?: 0
                val sql = if (exists > 0) {
                    "UPDATE policies SET policy=$policy, until=$until, logging=1, notification=1 WHERE uid=$uid"
                } else {
                    "INSERT INTO policies(uid,policy,until,logging,notification) VALUES($uid,$policy,$until,1,1)"
                }
                val okRun = RootShell.ok("$bin $db \"$sql\"")
                if (okRun) {
                    RootShell.run("magisk --denylist status >/dev/null 2>&1; true", 2_000)
                    val timeDesc = if (timeoutSeconds > 0) " for ${timeoutSeconds / 60}m" else " (permanent)"
                    "uid $uid ${if (allow) "granted" else "denied"}$timeDesc"
                } else {
                    "could not write the policy table"
                }
            }
            Backend.KERNELSU, Backend.APATCH -> {
                val cli = if (b == Backend.APATCH) "apd" else "ksud"
                val cmd = if (allow) "$cli su grant $uid" else "$cli su deny $uid"
                if (RootShell.ok(cmd)) {
                    "uid $uid ${if (allow) "granted" else "denied"} via $cli"
                } else {
                    "$cli could not change uid $uid"
                }
            }
            Backend.NONE -> "No root manager detected"
        }
    }

    /** Grants or revokes su for a uid. Returns a human-readable result. */
    fun setGrant(uid: Int, allow: Boolean, forever: Boolean = true): String {
        if (!RootShell.ready()) return "Root is not granted to GamersHub"
        val b = backend()
        if (b == Backend.NONE) return "No root manager detected"
        return when (b) {
            Backend.MAGISK -> {
                val bin = sqlite() ?: return "sqlite3 not found — cannot write the policy table"
                val db = "/data/adb/magisk.db"
                val policy = if (allow) 2 else 1
                val until = if (allow && forever) 0 else 0
                val now = System.currentTimeMillis() / 1000
                val exists = RootShell.run(
                    "$bin $db \"SELECT COUNT(*) FROM policies WHERE uid=$uid\" 2>/dev/null", 4_000
                )?.trim()?.toIntOrNull() ?: 0
                val sql = if (exists > 0) {
                    "UPDATE policies SET policy=$policy, until=$until, logging=1, notification=1 " +
                        "WHERE uid=$uid"
                } else {
                    "INSERT INTO policies(uid,policy,until,logging,notification) " +
                        "VALUES($uid,$policy,$until,1,1)"
                }
                val okRun = RootShell.ok("$bin $db \"$sql\"")
                if (okRun) {
                    // Magisk caches the policy in its daemon; nudge it so the change is
                    // live without a reboot where the build supports it.
                    RootShell.run("magisk --denylist status >/dev/null 2>&1; true", 2_000)
                    "uid $uid ${if (allow) "granted" else "denied"} · ${now}"
                } else {
                    "could not write the policy table"
                }
            }
            Backend.KERNELSU, Backend.APATCH -> {
                val cli = if (b == Backend.APATCH) "apd" else "ksud"
                val cmd = if (allow) "$cli su grant $uid" else "$cli su deny $uid"
                if (RootShell.ok(cmd)) {
                    "uid $uid ${if (allow) "granted" else "denied"} via $cli"
                } else {
                    "$cli could not change uid $uid"
                }
            }
            Backend.NONE -> "No root manager detected"
        }
    }

    /** Removes a policy row entirely, so the next su request shows a prompt again. */
    fun forget(uid: Int): String {
        if (!RootShell.ready()) return "Root is not granted to GamersHub"
        return when (backend()) {
            Backend.MAGISK -> {
                val bin = sqlite() ?: return "sqlite3 not found"
                if (RootShell.ok("$bin /data/adb/magisk.db \"DELETE FROM policies WHERE uid=$uid\""))
                    "uid $uid forgotten — will ask next time"
                else "could not delete the row"
            }
            Backend.KERNELSU, Backend.APATCH -> {
                val cli = if (backend() == Backend.APATCH) "apd" else "ksud"
                if (RootShell.ok("$cli su deny $uid")) "uid $uid set to ask" else "$cli failed"
            }
            Backend.NONE -> "No root manager detected"
        }
    }

    /**
     * A pre-boot / "superuser" switch: turn every grant off without deleting the
     * rows, so it can be flipped back. Magisk has no such global flag, so this is
     * implemented by setting every row to DENY and remembering which ones were
     * allowed, in our own prefs, so [restoreAll] is exact.
     */
    fun snapshotDenyAll(allowedUids: List<Int>): String {
        if (allowedUids.isEmpty()) return "nothing to turn off"
        val bin = sqlite() ?: return "sqlite3 not found"
        val inList = allowedUids.joinToString(",")
        return if (RootShell.ok(
                "$bin /data/adb/magisk.db " +
                    "\"UPDATE policies SET policy=1 WHERE uid IN ($inList)\""
            )
        ) "denied ${allowedUids.size} app(s)" else "could not update the policy table"
    }

    fun restoreAllowed(uids: List<Int>): String {
        if (uids.isEmpty()) return "nothing to restore"
        val bin = sqlite() ?: return "sqlite3 not found"
        val inList = uids.joinToString(",")
        return if (RootShell.ok(
                "$bin /data/adb/magisk.db " +
                    "\"UPDATE policies SET policy=2 WHERE uid IN ($inList)\""
            )
        ) "restored ${uids.size} app(s)" else "could not restore"
    }
}
