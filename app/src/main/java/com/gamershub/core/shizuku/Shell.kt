package com.gamershub.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import rikka.shizuku.Shizuku

/**
 * The shell surface every privileged feature in the app talks to.
 *
 * Four transports answer it, in descending order of reach:
 *
 *  0. **root** ([com.gamershub.core.root.RootShell]) — uid 0.
 *  0b. **the daemon** ([com.gamershub.core.adb.ShellDaemon]) — uid 2000 in a process of
 *     its own that outlives wireless debugging. This is the Shizuku role filled in-app:
 *     started once over any shell, then independent of ADB, ports and this process.
 *  2. **Wireless debugging** ([com.gamershub.core.adb.WirelessAdb]) — uid 2000 via
 *     the phone's own `adbd`. This is the same uid Shizuku's server runs as, so it is
 *     a genuine replacement for Shizuku, not a weaker mode: `settings`, `cmd`,
 *     `dumpsys`, `wm`, `am` and `pm` all answer identically.
 *  3. **Shizuku** ([rikka.shizuku.Shizuku]) — uid 2000 via a binder.
 *
 * Wireless debugging is tried before Shizuku on purpose. If the user has both, the
 * direct connection involves one fewer moving part and keeps working when the
 * Shizuku app is force-stopped or its binder is recycled. A user who has neither
 * still gets the same honest "needs a shell" answer from every call site.
 *
 * Call sites never choose a transport. They call [exec]/[ok]/[available] and the
 * dispatcher decides, which is what keeps the feature set identical across all three.
 *
 * Shizuku-backed shell with a real connection state machine.
 *
 * - [ensure] binds the user service as soon as the permission exists and is
 *   safe to call on every resume (it never binds twice).
 * - [requestPermission] can only fire once per process, so the Shizuku dialog
 *   is never shown in a loop to a user that already granted access.
 * - [exec] / [ok] self-heal: if the binder dropped (Shizuku restarted) they
 *   rebind with the stored application context before giving up.
 */
object Shell {

    enum class Status { NOT_RUNNING, NO_PERMISSION, BINDING, CONNECTED }

    @Volatile
    private var binder: IGamersHubShell? = null

    @Volatile
    private var binding = false

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var requestedOnce = false

    @Volatile
    var lastError: String = ""

    /** Real ring buffer of what this app actually asked the shell to do. */
    private val history = java.util.ArrayDeque<String>()

    fun recent(limit: Int = 8): List<String> = synchronized(history) {
        history.toList().takeLast(limit)
    }

    private fun record(line: String) {
        synchronized(history) {
            history.addLast(line)
            while (history.size > 40) history.removeFirst()
        }
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = IGamersHubShell.Stub.asInterface(service)
            binding = false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
        }
    }

    fun running(): Boolean = try {
        com.gamershub.core.adb.ShellDaemon.alive ||
            com.gamershub.core.adb.WirelessAdb.connected() ||
            Shizuku.pingBinder()
    } catch (_: Throwable) {
        com.gamershub.core.adb.ShellDaemon.alive || com.gamershub.core.adb.WirelessAdb.connected()
    }

    fun granted(): Boolean = try {
        // Any of the three transports being live means privileged work is possible,
        // whether or not Shizuku is installed, running, or authorised.
        com.gamershub.core.root.RootShell.ready() ||
            com.gamershub.core.adb.ShellDaemon.alive ||
            com.gamershub.core.adb.WirelessAdb.connected() ||
            (Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
    } catch (_: Throwable) {
        com.gamershub.core.root.RootShell.ready() ||
            com.gamershub.core.adb.ShellDaemon.alive ||
            com.gamershub.core.adb.WirelessAdb.connected()
    }

    fun connected(): Boolean = binder != null

    fun status(): Status = when {
        !running() -> Status.NOT_RUNNING
        !granted() -> Status.NO_PERMISSION
        // The direct process path needs nothing else, so a grant is "connected".
        else -> Status.CONNECTED
    }

    fun statusLabel(): String = when (status()) {
        Status.CONNECTED -> "Connected"
        Status.BINDING -> "Connecting"
        Status.NO_PERMISSION -> "Permission needed"
        Status.NOT_RUNNING -> "Shizuku not running"
    }

    /** Binds the user service when possible. Safe to call on every resume. */
    fun ensure(context: Context): Boolean {
        appContext = context.applicationContext
        if (!granted()) return false
        if (binder != null) return true
        if (binding) return false
        binding = true
        return try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, GamersHubShellService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("shell")
                .debuggable(false)
                .version(3)
            Shizuku.bindUserService(args, conn)
            true
        } catch (t: Throwable) {
            binding = false
            lastError = t.message ?: "bind failed"
            false
        }
    }

    fun rebind(context: Context) {
        binder = null
        binding = false
        ensure(context)
    }

    /**
     * Requests the runtime Shizuku grant. Returns true only when a prompt was
     * actually raised; calling it again in the same process is a no-op so the
     * user is never nagged after granting.
     */
    fun requestPermission(requestCode: Int = 7331): Boolean {
        if (!running() || granted()) return false
        if (requestedOnce) return false
        requestedOnce = true
        return try {
            Shizuku.requestPermission(requestCode)
            true
        } catch (t: Throwable) {
            lastError = t.message ?: "request failed"
            false
        }
    }

    private fun autoBind() {
        val context = appContext ?: return
        if (granted() && binder == null && !binding) ensure(context)
    }

    /**
     * Execute as shell; returns combined output (null when unavailable).
     *
     * The direct [Shizuku.newProcess] path is tried first: it only needs the
     * Shizuku grant that the user already approved, so a missing/never-bound
     * user service can no longer make privileged actions look "ungranted".
     * The bound user service remains as a fallback for older Shizuku builds.
     */
    /**
     * Supplies the context the wireless and daemon transports need.
     *
     * [exec] routes through those transports, and it used to read `appContext` which
     * only [ensure] ever set — so any call site that ran before `ensure()` got a shell
     * that silently answered null even with a live wireless connection. The application
     * object is always available, so this is the floor rather than a new requirement.
     */
    private fun ctx(): Context? =
        appContext ?: com.gamershub.GamersHubApp.instance?.applicationContext?.also {
            appContext = it
        }

    fun exec(command: String): String? {
        ctx()
        // Root first: when the engine is on and root was granted, every existing
        // Shizuku call site transparently gets the deeper tree (system-only paths,
        // property writes, pkgs that shell cannot touch).
        if (com.gamershub.core.root.RootShell.ready()) {
            com.gamershub.core.root.RootShell.run(command)?.let { out ->
                record("$ " + command.take(70) + "  →  root")
                return out
            }
        }
        // The daemon next: a separate uid-2000 process, so it answers even with
        // wireless debugging switched off. This is the transport that makes the
        // privilege outlive the connection that created it.
        if (com.gamershub.core.adb.ShellDaemon.alive) {
            com.gamershub.core.adb.ShellDaemon.exec(command)?.let { out ->
                record("$ " + command.take(70) + "  →  daemon")
                return out
            }
        }

        // Wireless debugging next: the same uid 2000 as Shizuku, reached directly.
        // One fewer hop than the binder, and it keeps working when Shizuku's server
        // has been force-stopped.
        if (com.gamershub.core.adb.WirelessAdb.connected()) {
            val ctx = ctx() ?: return null
            com.gamershub.core.adb.WirelessAdb.exec(ctx, command)?.let { out ->
                record("$ " + command.take(70) + "  →  wireless adb")
                return out
            }
        }
        directExec(command)?.let { out ->
            record("$ " + command.take(70) + "  →  ok")
            return out
        }
        if (binder == null) autoBind()
        val out = try {
            binder?.exec(command)
        } catch (t: Throwable) {
            lastError = t.message ?: "exec failed"
            null
        }
        if (out == null) {
            record("$ " + command.take(70) + "  →  " + reason())
            notifyNeeded()
        } else {
            record("$ " + command.take(70) + "  →  ok")
        }
        return out
    }

    /**
     * Explains a silent failure. Every privileged button in the app routes through
     * [exec]/[ok]; when those cannot run we say so instead of doing nothing, which is
     * what made features look broken.
     */
    private var lastNotice = 0L

    fun notifyNeeded() {
        val ctx = appContext ?: return
        if (granted()) return
        val now = System.currentTimeMillis()
        if (now - lastNotice < 3000) return
        lastNotice = now
        val msg = if (!running())
            "Shizuku isn't running — start it to use this"
        else
            "Grant Shizuku access to GamersHub to use this"
        runCatching {
            Handler(Looper.getMainLooper()).post {
                runCatching { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
            }
        }
    }

    /**
     * Runs the command in a real shell process owned by the Shizuku server.
     *
     * `Shizuku.newProcess` is the API that needs nothing but the granted
     * permission; the SDK marks it as restricted, so it is reached through
     * reflection instead of a direct call.
     */
    private fun directExec(command: String): String? {
        if (!granted()) return null
        val method = newProcess ?: return null
        return try {
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", "$command; echo __GH_EXIT__=$?"),
                null,
                null
            ) as? Process ?: return null
            val out = StringBuilder()
            val reader = Thread {
                runCatching { process.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
            }
            val err = Thread {
                runCatching { process.errorStream.bufferedReader().forEachLine { out.appendLine(it) } }
            }
            reader.start()
            err.start()
            // Hard cap: a wedged shell must never freeze the caller (or trigger an ANR).
            reader.join(1500)
            err.join(1500)
            val finished = runCatching { process.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS) }
                .getOrDefault(true)
            if (!finished) runCatching { process.destroy() }
            out.toString()
        } catch (t: Throwable) {
            lastError = t.message ?: "exec failed"
            null
        }
    }

    private val newProcess: java.lang.reflect.Method? by lazy {
        runCatching {
            Class.forName("rikka.shizuku.Shizuku").getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
        }.getOrNull()
    }

    /** Execute and report success (exit code 0 parsed from output). */
    fun ok(command: String): Boolean {
        val out = exec(command) ?: return false
        val match = Regex("__GH_EXIT__=(-?\\d+)").find(out)
        return match == null || match.groupValues[1] == "0"
    }

    /** True when privileged commands can actually run right now. */
    fun available(): Boolean = granted() || binder != null ||
        com.gamershub.core.root.RootShell.ready() ||
        com.gamershub.core.adb.ShellDaemon.alive ||
        com.gamershub.core.adb.WirelessAdb.connected()

    /** Human-readable reason for the last failure (drives the UI messages). */
    fun reason(): String = when {
        com.gamershub.core.root.RootShell.ready() -> lastError.ifBlank { "root rejected the command" }
        com.gamershub.core.adb.ShellDaemon.alive ->
            lastError.ifBlank { "the shell daemon rejected the command" }
        com.gamershub.core.adb.WirelessAdb.connected() ->
            lastError.ifBlank { "wireless debugging rejected the command" }
        !running() -> "No shell: start the daemon, pair wireless debugging, or run Shizuku"
        !granted() -> "GamersHub is not authorised in Shizuku"
        else -> lastError.ifBlank { "shell rejected the command" }
    }

    /** Last resort: plain app-side exec (no privileges). */
    fun appExec(command: String): String = try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val out = StringBuilder()
        // readText() on a wedged process blocks forever — drain on threads with a cap.
        val reader = Thread { runCatching { process.inputStream.bufferedReader().forEachLine { out.appendLine(it) } } }
        val err = Thread { runCatching { process.errorStream.bufferedReader().forEachLine { out.appendLine(it) } } }
        reader.start()
        err.start()
        reader.join(1500)
        err.join(1500)
        val finished = runCatching { process.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            .getOrDefault(true)
        if (!finished) runCatching { process.destroy() }
        out.toString()
    } catch (t: Throwable) {
        t.message ?: ""
    }

    /** Releases the binding (used by the activation sheet's Reconnect button). */
    fun disconnect() {
        binder = null
        binding = false
    }
}
