package com.gamershub.core.root

import android.content.Context
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Root backend.
 *
 * Two things live here:
 *
 *  1. [acquire] — gets a real root shell. `su` is tried directly, then through the
 *     Shizuku shell (some setups only grant su to the shell uid), then through a
 *     bundled `libsu`-style helper binary installed into the app's private dir as a
 *     temporary root provider ("temp root", in the spirit of DF root). The provider
 *     is a thin trampoline that re-execs `su -c`, so it never fakes privilege: if
 *     the device is not rooted it reports NOT_ROOT instead of pretending.
 *
 *  2. [run] — executes a command in a *persistent* root shell (one process, many
 *     commands) so per-frame operations do not pay `su` startup cost. Falls back to
 *     a one-shot `su -c` when the persistent shell cannot be kept alive.
 *
 * Everything is tracked so [releaseAll] can guarantee that turning the master engine
 * off really leaves nothing running.
 */
object RootShell {

    enum class State { UNKNOWN, NOT_ROOT, SHELL_ONLY, READY, DENIED }

    private const val PROVIDER = "ghsu"

    @Volatile
    var state: State = State.UNKNOWN
        private set

    @Volatile
    var lastError: String = ""
        private set

    /** How root was obtained, for the UI. */
    @Volatile
    var method: String = ""
        private set

    /** Absolute path of the su binary that worked, when it was not plain PATH su. */
    @Volatile
    var binPath: String? = null
        private set

    /**
     * True when this root was granted for the current session only.
     *
     * Set when [provisionTemporary] is what got us here. Cleared by [releaseAll], so
     * the next acquire has to earn it again. The privilege ladder reads this to tell
     * "temporary" from "persistent" honestly instead of guessing from the su path.
     */
    @Volatile
    var temporarySession: Boolean = false
        private set

    /** True when root is held via the shell uid (Shizuku / daemon / wireless ADB). */
    @Volatile
    private var shizukuRoot: Boolean = false

    /** True when `su -c` verified uid=0 even if an interactive `su` session could not be kept open. */
    @Volatile
    private var oneShotReady: Boolean = false

    /** Keeps the multiplexed shell open for the engine session. */
    fun hold(context: android.content.Context) {
        if (shizukuRoot) {
            record("session held via shell")
            return
        }
        if (session?.alive != true) startSession(context)
        record(if (session?.alive == true) "session held" else "one-shot root ready")
    }

    /**
     * Adopts a root grant that Shizuku already holds.
     *
     * When `su -c id` works through the Shizuku shell but not from the app, the grant
     * lives in the shell's process. There is no way to move that into our own uid, so
     * we do not pretend to: the shell keeps it, [run] keeps routing through it, and the
     * state becomes READY with an honest method label.
     */
    fun adoptShizukuRoot() {
        if (ready()) return
        shizukuRoot = true
        oneShotReady = false
        state = State.READY
        method = "su via Shizuku"
        lastError = ""
        record("adopted the Shizuku-shell root grant")
    }

    /**
     * Installs the temporary provider and returns true only if it *verified* uid 0.
     *
     * The provider is a shim over the device's su — it is not a root exploit and does
     * not claim to be one. It exists so the ladder has one stable privileged path, and
     * [removeProvider] deletes it whenever the engine stops.
     */
    fun provisionTemporary(context: android.content.Context): Boolean {
        if (!installProvider(context)) {
            record("temp provider could not be written")
            return false
        }
        val out = quick("${providerPath(context)} id", timeoutMs = 20_000) ?: return false
        if (!out.contains("uid=0")) {
            record("temp provider ran but not as root")
            return false
        }
        shizukuRoot = false
        state = State.READY
        method = "temporary provider"
        temporarySession = true
        lastError = ""
        startSession(context)
        oneShotReady = session?.alive != true
        record("temp provider -> uid=0 (session only)")
        return true
    }

    @Volatile
    private var session: Session? = null

    /** Commands we ran, newest last — drives the Root screen's log. */
    private val history = ArrayDeque<String>()

    fun recent(limit: Int = 10): List<String> = synchronized(history) {
        history.toList().takeLast(limit)
    }

    private fun record(line: String) {
        synchronized(history) {
            history.addLast(line)
            while (history.size > 60) history.removeFirst()
        }
    }

    fun ready(): Boolean =
        state == State.READY && (shizukuRoot || oneShotReady || session?.alive == true)

    fun statusLabel(): String = when (state) {
        State.READY -> "Root ready · $method"
        State.SHELL_ONLY -> "Shell only (no root)"
        State.NOT_ROOT -> "No root / Magisk su missing"
        State.DENIED -> "Root denied"
        State.UNKNOWN -> "Not checked"
    }

    // ------------------------------------------------------------------ discovery

    /**
     * Cheap check that does not prompt: is there a usable `su` on the path?
     * Used to decide whether the Root card is worth showing at all.
     */
    /**
     * Every place a root solution may put `su`. Magisk uses /system/bin or the
     * debug_ramdisk overlay (which is not on PATH for app processes), KernelSU
     * and APatch use /system/bin or /data/adb. Probing PATH alone misses most
     * modern installs, so all known locations are checked directly.
     */
    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/debug_ramdisk/su",
        "/data/adb/magisk/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su",
        "/vendor/bin/su",
    )

    /** First su binary that exists, or null. Cheap: one shell, no prompt. */
    fun suBinary(): String? {
        val cmd = SU_PATHS.joinToString("; ") { "test -x $it && echo $it" } + "; command -v su 2>/dev/null"
        val out = quick(cmd, 4_000) ?: return null
        return out.lines().map { it.trim() }.firstOrNull { line ->
            line.startsWith("/") && !line.contains(" ")
        }
    }

    fun suExists(): Boolean = suBinary() != null || runCatching {
        ProcessBuilder("sh", "-c", "command -v su >/dev/null 2>&1").start().waitFor() == 0
    }.getOrDefault(false)

    /**
     * Obtains root. This may show the Magisk/SuperSU prompt, so call it from a
     * background thread and only when the user asks for it.
     */
    fun acquire(context: Context): Boolean {
        if (ready()) return true

        var sawDenied = false

        // 1. su, tried through PATH first and then by absolute path. Magisk's
        //    debug_ramdisk and the KernelSU/APatch bins are not on an app's PATH,
        //    which is why a plain `su -c id` fails on many rooted devices.
        val binary = suBinary()
        val candidates = buildList {
            add("su")
            binary?.let { if (it != "su") add(it) }
        }
        for (bin in candidates) {
            quick("$bin -c id", timeoutMs = 20_000)?.let { out ->
                if (out.contains("uid=0")) {
                    shizukuRoot = false
                    binPath = if (bin == "su") (binary ?: "su") else bin
                    state = State.READY
                    method = if (bin == "su") "su" else "su ($bin)"
                    lastError = ""
                    startSession(context)
                    oneShotReady = session?.alive != true
                    record("$bin -c id → uid=0")
                    return true
                }
                if (out.contains("denied", true) || out.contains("not allowed", true)) {
                    sawDenied = true
                    state = State.DENIED
                    lastError = "Superuser request denied. Open your root manager and allow GamersHub."
                }
            }
        }

        // 2. su through the Shizuku / daemon / wireless shell — some setups only grant root to shell
        if (state != State.READY) {
            com.gamershub.core.shizuku.Shell.execAsShell("su -c id")?.let { out ->
                if (out.contains("uid=0")) {
                    adoptShizukuRoot()
                    record("su via shizuku → uid=0")
                    return true
                }
            }
        }

        // 3. bundled temporary provider
        if (state != State.READY && installProvider(context)) {
            quick("${providerPath(context)} id", timeoutMs = 20_000)?.let { out ->
                if (out.contains("uid=0")) {
                    shizukuRoot = false
                    temporarySession = true
                    state = State.READY
                    method = "bundled provider"
                    lastError = ""
                    startSession(context)
                    oneShotReady = session?.alive != true
                    record("$PROVIDER id → uid=0")
                    return true
                }
            }
        }

        if (sawDenied) {
            state = State.DENIED
            lastError = "Superuser request denied. Open your root manager and allow GamersHub."
        } else {
            state = if (com.gamershub.core.shizuku.Shell.available()) State.SHELL_ONLY else State.NOT_ROOT
            lastError = "No root. Install Magisk (or KernelSU/APatch) and grant GamersHub superuser access."
        }
        record("acquire failed → $lastError")
        return false
    }

    /**
     * Full diagnostic for the Root screen. Every line is a real probe, so a user
     * who cannot get root can see exactly which step failed instead of a generic
     * "no root" message.
     */
    fun diagnose(context: Context): List<String> {
        val lines = mutableListOf<String>()
        lines += "state        ${state.name}"
        lines += "su on PATH   ${runCatching {
            ProcessBuilder("sh", "-c", "command -v su").start()
                .inputStream.bufferedReader().readText().trim().ifBlank { "not found" }
        }.getOrDefault("probe failed")}"
        lines += "su binary    ${suBinary() ?: "not found in any known location"}"
        lines += "method       ${method.ifBlank { "—" }}"
        lines += "session      ${if (session?.alive == true) "alive" else "not running"}"
        lines += "uid now      ${run("id", 4_000)?.lines()?.firstOrNull()?.take(70) ?: "—"}"
        lines += "magisk       ${run("magisk -V 2>/dev/null", 4_000)?.trim()?.take(40).orEmpty().ifBlank { "—" }}"
        lines += "zygisk       ${when (ModuleManager.zygiskEnabled()) {
            true -> "enabled"
            false -> "disabled"
            null -> "unknown / not Magisk"
        }}"
        lines += "modules      ${ModuleManager.list().size} installed"
        lines += "manager      ${ModuleManager.managerName()}"
        if (lastError.isNotBlank()) lines += "last error   $lastError"
        return lines
    }

    /** Drops the persistent shell and marks root unusable; call on engine stop. */
    fun releaseAll() {
        temporarySession = false
        shizukuRoot = false
        oneShotReady = false
        binPath = null
        runCatching { session?.close() }
        session = null
        state = State.SHELL_ONLY
        method = ""
        record("root session released")
    }

    // ------------------------------------------------------------------ execution

    /**
     * Runs [command] as root. Returns combined stdout/stderr, or null when root is
     * unavailable. Never throws and never blocks longer than [timeoutMs].
     */
    fun run(command: String, timeoutMs: Long = 8_000): String? {
        if (state != State.READY) return null
        if (shizukuRoot) {
            val out = com.gamershub.core.shizuku.Shell.execAsShell("su -c ${shellQuote(command)}")
            record("$ " + command.take(70) + if (out != null) " → root (shell)" else " → failed")
            return out
        }
        val s = session
        if (s != null && s.alive) {
            val out = s.exec(command, timeoutMs)
            if (out != null) {
                record("$ " + command.take(70) + " → root")
                return out
            }
        }
        // Session died or could not stay open: retry one-shot with the discovered su binary.
        val out = quickSu(binPath ?: "su", command, timeoutMs)
        if (out != null) {
            record("$ " + command.take(70) + " → root (oneshot)")
            com.gamershub.GamersHubApp.instance?.let { startSession(it) }
            oneShotReady = session?.alive != true
        } else {
            session = null
            record("$ " + command.take(70) + " → failed")
        }
        return out
    }

    private fun shellQuote(cmd: String): String = "'" + cmd.replace("'", "'\\''") + "'"

    fun ok(command: String): Boolean {
        val out = run("$command; echo __GH_EXIT__=$?") ?: return false
        val m = Regex("__GH_EXIT__=(-?\\d+)").find(out) ?: return true
        return m.groupValues[1] == "0"
    }

    /** One-shot exec via `su -c` without relying on `sh -c` double-quote escaping. */
    private fun quickSu(suBin: String, command: String, timeoutMs: Long): String? = runCatching {
        val p = ProcessBuilder(suBin, "-c", command).redirectErrorStream(true).start()
        val sb = StringBuilder()
        val t = Thread { runCatching { p.inputStream.bufferedReader().forEachLine { sb.appendLine(it) } } }
        t.start()
        if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            runCatching { p.destroyForcibly() }
        }
        t.join(800)
        sb.toString()
    }.getOrNull()

    /** One-shot exec with no session reuse; used for probing. */
    private fun quick(cmdline: String, timeoutMs: Long): String? = runCatching {
        val p = ProcessBuilder("sh", "-c", cmdline).redirectErrorStream(true).start()
        val sb = StringBuilder()
        val t = Thread { runCatching { p.inputStream.bufferedReader().forEachLine { sb.appendLine(it) } } }
        t.start()
        if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            runCatching { p.destroyForcibly() }
        }
        t.join(800)
        sb.toString()
    }.getOrNull()

    // ------------------------------------------------------------------ temp provider

    private fun providerPath(context: Context): String =
        java.io.File(context.filesDir, PROVIDER).absolutePath

    /**
     * Installs a tiny trampoline script that re-execs su. This is the "temporary root
     * system": it lives inside the app's private storage, is created only when root is
     * requested, and is deleted by [removeProvider] when the engine is switched off.
     *
     * It deliberately does NOT bundle a su binary — shipping one would be both a
     * security hole and licence-problematic, and it could not work without the kernel
     * already trusting it. It is a convenience shim over the device's real su.
     */
    private fun installProvider(context: Context): Boolean = runCatching {
        val file = java.io.File(context.filesDir, PROVIDER)
        val su = binPath ?: suBinary() ?: "su"
        file.writeText(
            """
            #!/system/bin/sh
            # GamersHub temporary root provider.
            # Exists only while the master engine is on; re-execs the device's su.
            exec $su -c "$*"
            """.trimIndent() + "\n"
        )
        file.setExecutable(true, false)
        file.exists() && file.canExecute()
    }.getOrDefault(false)

    fun removeProvider(context: Context) {
        runCatching { java.io.File(context.filesDir, PROVIDER).delete() }
        record("temp provider removed")
    }

    // ------------------------------------------------------------------ session

    private fun startSession(context: Context) {
        if (session?.alive == true) return
        session = runCatching { Session.start() }.getOrNull()
        record(if (session?.alive == true) "root shell session started" else "root session unavailable")
    }

    /**
     * A single long-lived `su` process multiplexed by a marker line.
     *
     * Commands are written to sh's stdin and each one is followed by an exit marker;
     * the reader thread hands complete blocks back to the waiting caller. This keeps
     * privileged work cheap (no fork per command) which is what makes per-frame and
     * per-touch operations viable.
     */
    private class Session private constructor(
        private val process: Process,
        private val writer: OutputStreamWriter,
        private val reader: BufferedReader,
    ) : AutoCloseable {

        @Volatile
        var alive: Boolean = true
            private set

        private val pending = ArrayDeque<String>()
        private val lock = Object()
        private val execLock = Object()
        private var markerSeq = AtomicInteger(0)
        @Volatile
        private var activeMarker: String = ""
        @Volatile
        private var waiting: CountDownLatch? = null

        fun exec(command: String, timeoutMs: Long): String? = synchronized(execLock) {
            if (!alive) return null
            return try {
                val id = markerSeq.incrementAndGet()
                val marker = "__GH_DONE_${id}__"
                val latch = CountDownLatch(1)
                synchronized(lock) {
                    pending.clear()
                    activeMarker = marker
                    waiting = latch
                }
                writer.write("$command\n")
                writer.write("echo $marker\n")
                writer.flush()
                val finished = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                synchronized(lock) {
                    waiting = null
                    activeMarker = ""
                }
                if (!finished) {
                    // Stream is now out of sync with markers; close so caller falls back cleanly.
                    close()
                    return null
                }
                synchronized(lock) { pending.joinToString("\n") }
            } catch (t: Throwable) {
                alive = false
                null
            }
        }

        private fun pump() {
            try {
                var line: String? = reader.readLine()
                while (line != null) {
                    val cur = activeMarker
                    if (cur.isNotEmpty() && line == cur) {
                        waiting?.countDown()
                    } else {
                        synchronized(lock) { pending.addLast(line!!) }
                    }
                    line = reader.readLine()
                }
            } catch (_: Throwable) {
            } finally {
                alive = false
                waiting?.countDown()
            }
        }

        override fun close() {
            alive = false
            runCatching { writer.close() }
            runCatching { reader.close() }
            runCatching { process.destroy() }
        }

        companion object {
            fun start(): Session {
                // Use the su that actually answered during discovery, so the session
                // does not depend on PATH resolution that already failed once.
                val bin = binPath ?: "su"
                val p = ProcessBuilder(bin).redirectErrorStream(true).start()
                val w = OutputStreamWriter(p.outputStream)
                val r = p.inputStream.bufferedReader()
                val s = Session(p, w, r)
                Thread(s::pump, "gh-root-shell").apply { isDaemon = true }.start()
                val idOut = s.exec("id", 4_000)
                if (idOut == null || !idOut.contains("uid=0")) {
                    s.close()
                    error("su session did not reach uid=0")
                }
                return s
            }
        }
    }
}
