package com.gamershub.core.adb

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.security.SecureRandom

/**
 * The app's half of the Shizuku-style daemon ([com.gamershub.daemon.GamersHubDaemon]).
 *
 * ## What this adds over the 6.29.0 transport
 *
 * Wireless debugging is a *connection*. Turn it off and the shell is gone, which is
 * exactly the behaviour that makes a user say "that isn't a system, that's a session".
 * Shizuku's answer is a server process that was started once and keeps running; this is
 * the same thing:
 *
 * ```
 * ADB (once)  →  app_process daemon as uid 2000  →  local socket  →  every feature
 * ```
 *
 * The ADB connection is needed for exactly as long as it takes to spawn the process.
 * After that the daemon is independent of wireless debugging, of the port, and of the
 * app's own process — so privileges survive ADB being switched off, which is the
 * property the user asked for. It ends at reboot, like a Shizuku server started over ADB.
 *
 * ## Starting it
 *
 * `CLASSPATH=<base.apk> app_process /system/bin com.gamershub.daemon.GamersHubDaemon`
 * is sent over the shell transport that is currently available (wireless ADB or Shizuku). `app_process` takes an APK on its classpath, so the daemon ships in the
 * installed APK itself instead of a second dex asset that could drift out of sync.
 *
 * The process is detached with `setsid` and its output goes to a log file, because a
 * daemon whose stdout is a pipe that closes is a daemon that dies of SIGPIPE at the
 * worst possible moment.
 *
 * ## Talking to it
 *
 * An abstract unix socket whose name carries a random token generated here and passed
 * on the command line. Any app can reach an abstract socket if it knows the name, so the
 * token is the access control — it is regenerated on every start and never stored.
 */
object ShellDaemon {

    /** Socket name prefix; the random token is appended. */
    private const val SOCKET_PREFIX = "com.gamershub.daemon."

    private const val LOG_FILE = "gh-daemon.log"

    @Volatile
    var token: String = ""
        private set

    @Volatile
    var lastError: String = ""
        private set

    @Volatile
    private var appContext: Context? = null

    /** True once a daemon answered a PING on the current token. */
    @Volatile
    var alive: Boolean = false
        private set

    fun attached(context: Context) {
        appContext = context.applicationContext
    }

    private fun socketName(): String = SOCKET_PREFIX + token

    private const val TMP_LOG_FILE = "/data/local/tmp/gh-daemon-shell.log"

    /** Builds the shell command that starts the daemon. Exposed for the diagnostics panel. */
    fun startCommand(context: Context): String {
        val apk = context.applicationInfo.sourceDir
        return "setsid sh -c 'CLASSPATH=$apk /system/bin/app_process /system/bin " +
            "com.gamershub.daemon.GamersHubDaemon --token=$token' " +
            ">> $TMP_LOG_FILE 2>&1 < /dev/null &"
    }

    /**
     * Starts the daemon over whichever transport is live and waits for it to answer.
     *
     * Returns true only when the started daemon has actually replied on the socket — a
     * spawned process is not a running server, and reporting success on the `&` would be
     * exactly the kind of lie that makes a privilege panel worthless.
     */
    fun start(context: Context, timeoutMs: Long = 8_000): Pair<Boolean, String> {
        attached(context)

        if (token.isBlank()) token = newToken()

        // Already running from an earlier start: nothing to do.
        if (ping()) return true to statusLine()

        val shell = transportFor(context)
            ?: return false to "Needs a shell to start: pair wireless debugging or run Shizuku"

        val command = startCommand(context)
        val out = shell(command)
        if (out == null) {
            lastError = "The shell refused to start the daemon"
            return false to lastError
        }

        // app_process takes a moment to bind; poll rather than sleeping a fixed time,
        // so a fast device is fast and a slow one still works.
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (ping()) {
                // Remembered so the engine restarts it next time. A preference about our
                // own daemon, not a switch over the phone.
                runCatching {
                    context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
                        .edit().putBoolean("daemon_enabled", true).apply()
                }
                return true to statusLine()
            }
            Thread.sleep(120)
        }

        lastError = tailOfLog(context) ?: "The daemon did not answer on its socket"
        return false to lastError
    }

    /**
     * Chooses the transport that can spawn a process.
     *
     * The wireless ADB shell first, then Shizuku. Both return output or null.
     */
    private fun transportFor(context: Context): ((String) -> String?)? {
        if (WirelessAdb.connected()) {
            return { cmd -> WirelessAdb.exec(context, cmd, 12_000) }
        }
        if (com.gamershub.core.shizuku.Shell.available()) {
            return { cmd -> com.gamershub.core.shizuku.Shell.exec(cmd) }
        }
        return null
    }

    /**
     * Runs a command through the daemon.
     *
     * Returns null when the daemon is not reachable, so callers fall through to the next
     * transport instead of seeing a fabricated empty string.
     */
    fun exec(command: String, timeoutMs: Long = 20_000): String? {
        if (token.isBlank()) return null
        val socket = LocalSocket()
        return try {
            socket.connect(LocalSocketAddress(socketName(), LocalSocketAddress.Namespace.ABSTRACT))
            socket.soTimeout = timeoutMs.toInt()
            val out = DataOutputStream(socket.outputStream)
            val input = DataInputStream(socket.inputStream)

            out.write("HELLO\n".toByteArray()); out.flush()
            val ready = readLine(input) ?: return null
            if (!ready.startsWith("READY")) return null

            val body = command.toByteArray(Charsets.UTF_8)
            out.write("RUN ${body.size}\n".toByteArray()); out.write(body); out.flush()

            val header = readLine(input) ?: return null
            if (!header.startsWith("OUT ")) return null
            val length = header.substring(4).trim().toIntOrNull() ?: return null

            val payload = ByteArray(length)
            input.readFully(payload)
            // Free the socket promptly; the daemon handles one client at a time and the
            // next call would otherwise queue behind this one.
            runCatching { out.write("BYE\n".toByteArray()); out.flush() }
            alive = true
            String(payload, Charsets.UTF_8)
        } catch (t: Throwable) {
            alive = false
            lastError = t.message ?: "daemon call failed"
            null
        } finally {
            runCatching { socket.close() }
        }
    }

    /** Cheap liveness check. Never throws. */
    fun ping(): Boolean {
        if (token.isBlank()) return false
        val socket = LocalSocket()
        return try {
            socket.connect(LocalSocketAddress(socketName(), LocalSocketAddress.Namespace.ABSTRACT))
            socket.soTimeout = 2_000
            val out = DataOutputStream(socket.outputStream)
            val input = DataInputStream(socket.inputStream)
            out.write("HELLO\n".toByteArray()); out.flush()
            val ready = readLine(input)
            if (ready == null || !ready.startsWith("READY")) return false
            out.write("PING\n".toByteArray()); out.flush()
            val pong = readLine(input) == "PONG"
            runCatching { out.write("BYE\n".toByteArray()); out.flush() }
            alive = pong
            pong
        } catch (t: Throwable) {
            alive = false
            false
        } finally {
            runCatching { socket.close() }
        }
    }

    /** Stops the daemon. Called by the engine on every stop, so "off" is really off. */
    fun stop(context: Context): Boolean {
        attached(context)
        if (token.isBlank()) return false
        val shell = transportFor(context)
        var stopped = false
        if (shell != null) {
            // Killed by socket name: precise, and it cannot hit anything else.
            shell("pkill -f 'gamershub.daemon.${token}' 2>/dev/null; true")?.let { stopped = true }
        }
        // Even with no transport the daemon may still be running from an earlier session;
        // it exits on idle, but a stale token must not be reused.
        alive = false
        return stopped
    }

    /** Forgets the token. The daemon keeps running until stopped or idle-exited. */
    fun forget() {
        token = ""
        alive = false
    }

    fun statusLine(): String = when {
        alive -> "daemon running as uid 2000 · independent of ADB"
        token.isNotBlank() -> "daemon not answering"
        else -> "no daemon"
    }

    private fun newToken(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun readLine(input: DataInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString()
            if (c != '\r'.code) sb.append(c.toChar())
            if (sb.length > 4096) return null
        }
    }

    /** Last lines of the daemon log, for a failure message that says something. */
    private fun tailOfLog(context: Context): String? {
        val shellLog = transportFor(context)?.invoke("tail -n 6 $TMP_LOG_FILE 2>/dev/null")
        if (!shellLog.isNullOrBlank()) {
            val lines = shellLog.trim().lines().filter { it.isNotBlank() && !it.startsWith("__GH_EXIT__") }
            if (lines.isNotEmpty()) return lines.joinToString(" · ")
        }
        val log = java.io.File(context.filesDir, LOG_FILE)
        if (!log.exists()) return null
        val text = runCatching { log.readText() }.getOrNull() ?: return null
        val lines = text.trim().lines().takeLast(4).filter { it.isNotBlank() }
        return lines.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
