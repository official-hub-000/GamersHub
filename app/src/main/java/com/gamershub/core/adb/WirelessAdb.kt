package com.gamershub.core.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wireless-debugging transport — Shizuku's job, done without Shizuku.
 *
 * ## Why this exists
 *
 * Shizuku is a service that runs as uid 2000 (shell) so an app can borrow that uid.
 * Wireless debugging is the same uid, reached a different way: Android's own `adbd`
 * listens on loopback with a TLS connection, and the user authorises this app once by
 * pairing with the six-digit code the Settings screen shows. After that, GamersHub
 * holds a shell connection of its own and no longer needs Shizuku at all.
 *
 * That is why this is not a parallel feature set. It is a third **transport** under
 * the same [com.gamershub.core.shizuku.Shell] surface, which is why every existing
 * call site — Game Space, tools, restore, capture — works through it untouched.
 *
 * ## State
 *
 * [Status.NOT_PAIRED] is the state Shizuku calls "permission needed", and it is
 * reached honestly: the app never claims a connection it does not have. [lastError]
 * carries the real reason a connect failed, because "it didn't work" is the thing
 * that makes a network panel useless.
 *
 * ## Discovery
 *
 * The ports are ephemeral and change whenever wireless debugging is toggled, so they
 * are discovered over mDNS exactly as the platform advertises them:
 * `_adb-tls-connect._tcp` for the shell port, `_adb-tls-pairing._tcp` for pairing.
 * Saved ports are kept as a fast path, but a failed connect re-discovers.
 */
object WirelessAdb {

    enum class Status {
        /** Wireless debugging is off, or the app has not tried yet. */
        OFF,

        /** Wireless debugging is on but this device has never paired GamersHub. */
        NOT_PAIRED,

        /** Connected, but adbd has not authorised this identity yet. */
        UNAUTHORISED,

        /** Socket up, identity accepted, shell usable. */
        CONNECTED,
    }

    /** What the UI shows. Mirrors Shizuku's four states so the two read the same. */
    @Volatile
    var status: Status = Status.OFF
        private set

    @Volatile
    var lastError: String = ""
        private set

    /** Port adbd is listening on for shell connections. */
    @Volatile
    var shellPort: Int = 0
        private set

    /** Port adbd is listening on for pairing. Changes every pairing session. */
    @Volatile
    var pairingPort: Int = 0
        private set

    @Volatile
    private var appContext: Context? = null

    private val host: String = "127.0.0.1"

    /** The fixed port adbd listens on for TLS connects after `adb tcpip`-style setup. */
    private const val KNOWN_TLS_PORT = 5555

    fun attached(context: Context) {
        appContext = context.applicationContext
    }

    fun connected(): Boolean = status == Status.CONNECTED

    /**
     * True when the socket is genuinely still up.
     *
     * [status] is a flag; adbd can drop the connection underneath it (reboot, toggling
     * wireless debugging, the daemon deciding to close an idle stream). Anything that
     * wants to *reuse* a connection has to ask the socket, not the flag.
     */
    fun alive(context: Context): Boolean = runCatching {
        connected() && AdbConnectionManager.get(context).isConnected
    }.getOrDefault(false)

    /** Last port that actually carried a shell, or 0. */
    fun rememberedPort(context: Context): Int = runCatching {
        context.getSharedPreferences("gamershub", Context.MODE_PRIVATE).getInt("wireless_port", 0)
    }.getOrDefault(0)

    fun statusLabel(): String = when (status) {
        Status.CONNECTED -> "Connected"
        Status.NOT_PAIRED -> "Pair needed"
        Status.UNAUTHORISED -> "Not authorised"
        Status.OFF -> "Wireless debugging off"
    }

    /**
     * Discovers the advertised ports.
     *
     * Returns true if at least the shell port was found, which is the signal that
     * wireless debugging is genuinely on — the port only exists while it is.
     */
    fun discover(context: Context, timeoutMs: Long = 6_000): Boolean {
        attached(context)
        // mDNS answers are layer-2 multicast; several chipsets drop them unless a
        // multicast lock is held for the duration of the scan.
        val wifi = context.applicationContext
            .getSystemService(android.content.Context.WIFI_SERVICE)
            as? android.net.wifi.WifiManager
        val lock = runCatching {
            wifi?.createMulticastLock("gh-adb-mdns")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
        return try {
            val found = discoverShellPort(context, timeoutMs)
            // Pairing is only advertised while the pairing dialog is open, so a miss
            // here is normal and must not be treated as a failure.
            runCatching { discoverPairingPort(context, 2_500) }
            found
        } finally {
            runCatching { if (lock?.isHeld == true) lock.release() }
        }
    }

    private fun discoverShellPort(context: Context, timeoutMs: Long): Boolean {
        val port = NsdFinder(context, "_adb-tls-connect._tcp").find(timeoutMs)
        if (port != null) {
            shellPort = port
            return true
        }
        return false
    }

    private fun discoverPairingPort(context: Context, timeoutMs: Long) {
        val port = NsdFinder(context, "_adb-tls-pairing._tcp").find(timeoutMs)
        if (port != null) pairingPort = port
    }

    /**
     * Pairs with the code shown in Settings › Wireless debugging › Pair device with
     * pairing code.
     *
     * Must run off the main thread: it opens a TLS socket to adbd, runs SPAKE2, and
     * waits for the daemon. A wrong or expired code fails here, and the message says
     * which, because "pairing failed" with no reason is what sends people in circles.
     */
    fun pair(context: Context, code: String, port: Int = pairingPort): Pair<Boolean, String> {
        attached(context)
        val manual = port
        // Checked here rather than only in the UI: a caller that skips the screen must
        // not get an opaque TLS failure from three layers down.
        if (!wirelessSupported()) {
            lastError = "Wireless pairing needs Android 10 or newer (TLS 1.3)"
            return false to lastError
        }
        if (manual > 0) {
            // Typed in by hand — trust it and skip discovery entirely.
            pairingPort = manual
        } else if (pairingPort <= 0) {
            discoverPairingPort(context, 5_000)
        }
        val target = if (manual > 0) manual else pairingPort
        if (target <= 0) {
            return false to "Open Settings › Wireless debugging › Pair device with pairing code, then try again"
        }
        if (code.length != 6 || code.any { !it.isDigit() }) {
            return false to "The pairing code is the six digits shown on the phone"
        }
        return try {
            val manager = AdbConnectionManager.get(context)
            val ok = manager.pair(target, code)
            if (ok) {
                lastError = ""
                true to "Paired"
            } else {
                lastError = "The pairing code was rejected"
                false to lastError
            }
        } catch (t: Throwable) {
            lastError = t.message ?: "pairing failed"
            false to lastError
        }
    }

    /**
     * Opens the shell connection.
     *
     * Assumes pairing already happened. When adbd reports that a pairing is required
     * the status becomes [Status.NOT_PAIRED] rather than a generic failure, so the UI
     * can send the user to the right screen.
     */
    fun connect(context: Context, timeoutMs: Long = 10_000, portOverride: Int = 0): Pair<Boolean, String> {
        attached(context)
        if (!wirelessSupported()) {
            status = Status.OFF
            lastError = "Wireless debugging needs Android 10 or newer"
            return false to lastError
        }
        if (portOverride > 0) {
            shellPort = portOverride
        } else if (shellPort <= 0) {
            // The port from last session is the cheapest correct guess: adbd keeps it
            // for the life of a boot, and polling it answers instantly when it is right.
            val remembered = context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
                .getInt("wireless_port", 0)
            if (remembered > 0) shellPort = remembered
            if (shellPort <= 0) discoverShellPort(context, 6_000)
        }
        if (shellPort <= 0) {
            // mDNS is the documented way to find the port, but it is multicast: some
            // chipsets and many routers drop it, and then the app would claim wireless
            // debugging is off while it is plainly on. adbd's TLS connect port is stable
            // for the life of a boot, so trying it costs one socket and rescues every
            // network where discovery is silent.
            val guess = KNOWN_TLS_PORT
            val guessed = try {
                java.net.Socket().use { sock ->
                    sock.connect(java.net.InetSocketAddress(host, guess), 700)
                    true
                }
            } catch (_: Throwable) {
                false
            }
            if (!guessed) {
                status = Status.OFF
                lastError = "No adbd found on 127.0.0.1:$guess and mDNS found nothing. " +
                    "Turn Wireless debugging on, and keep this phone on the same Wi-Fi " +
                    "(not a VPN). You can also type the port from Settings."
                return false to lastError
            }
            shellPort = guess
        }
        return try {
            val manager = AdbConnectionManager.get(context)
            manager.setHostAddress(host)
            val ok = manager.connect(shellPort)
            if (ok) {
                // connected() only proves the TLS/identity handshake; a real command
                // proves the shell is usable. Anything less would be theatre.
                val out = AdbExec.run(manager, "id")
                if (out != null && out.contains("uid=")) {
                    status = Status.CONNECTED
                    lastError = ""
                    // Remembered so the engine re-attaches on the next start. This is a
                    // preference about *our* connection, not a switch over the phone.
                    runCatching {
                        context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("wireless_enabled", true)
                            .putInt("wireless_port", shellPort)
                            .apply()
                    }
                    true to out.trim()
                } else {
                    status = Status.UNAUTHORISED
                    lastError = "Connected but adbd refused the shell"
                    false to lastError
                }
            } else {
                status = Status.NOT_PAIRED
                lastError = "Not paired — use the pairing code from Settings"
                false to lastError
            }
        } catch (t: Throwable) {
            val msg = t.message ?: "connection failed"
            status = when {
                msg.contains("pair", ignoreCase = true) -> Status.NOT_PAIRED
                msg.contains("timed out", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) -> Status.OFF
                else -> Status.UNAUTHORISED
            }
            // Name the remedy with the failure. A bare "connection failed" is what sent
            // people to reinstall the app for a port that had simply changed.
            lastError = when (status) {
                Status.NOT_PAIRED -> "Not paired — use the pairing code from Settings"
                Status.OFF -> "adbd did not answer on port $shellPort — the port changes when " +
                    "wireless debugging is toggled. Tap Already paired · Connect."
                else -> msg
            }
            // A stale remembered port must not win twice.
            runCatching {
                context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
                    .edit().remove("wireless_port").apply()
            }
            false to lastError
        }
    }

    /** Runs a command over the live connection. Null when there is no connection. */
    fun exec(context: Context, command: String, timeoutMs: Long = 12_000): String? {
        attached(context)
        if (!connected()) return null
        return AdbExec.run(AdbConnectionManager.get(context), command, timeoutMs)
    }

    /**
     * Drops the connection. The device keeps its authorisation, so the next connect
     * needs no code — this is only the network half, not a teardown of privilege.
     *
     * Deliberately NOT [AdbConnectionManager.close]: that method destroys the private
     * key, which on a singleton would leave the next connect with a dead identity and
     * no way to recover except re-pairing. The engine stops this every time, so using
     * close() here would silently unpair the user on their first engine toggle.
     */
    fun disconnect() {
        runCatching { AdbConnectionManager.get(appContext ?: return).disconnect() }
        status = Status.OFF
    }

    /**
     * Forgets the pairing identity entirely.
     *
     * The certificate cannot be replaced on adbd's side from here, so this is only
     * useful after the user removed GamersHub from the authorised list. It generates
     * a fresh key and certificate, which means pairing must be done again.
     */
    fun forgetIdentity() {
        runCatching { AdbConnectionManager.get(appContext ?: return).resetIdentity() }
        disconnect()
        lastError = ""
    }

    /**
     * One-shot mDNS lookup.
     *
     * Deliberately not a long-lived listener: the ports are read when the user acts,
     * and a service that stays subscribed leaks (and keeps the radio awake) for no
     * benefit on a screen that is open for a few seconds at a time.
     */
    private class NsdFinder(context: Context, private val type: String) {

        private val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

        fun find(timeoutMs: Long): Int? {
            val manager = nsd ?: return null
            val port = AtomicInteger(-1)
        val latch = Object()
        val wanted = type.trimEnd('.')
        var listener: NsdManager.DiscoveryListener? = null

        listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = signal()
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = signal()
                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onDiscoveryStopped(serviceType: String) = signal()

                override fun onServiceFound(info: NsdServiceInfo) {
                    // Some platforms advertise with a trailing dot, which would make a
                    // strict string compare reject every service and look exactly like
                    // "wireless debugging is broken".
                    if (info.serviceType?.trimEnd('.') != wanted) return
                    runCatching {
                        manager.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(i: NsdServiceInfo, errorCode: Int) = Unit
                            override fun onServiceResolved(i: NsdServiceInfo) {
                                if (i.port > 0) port.compareAndSet(-1, i.port)
                                signal()
                            }
                        })
                    }
                }

                override fun onServiceLost(info: NsdServiceInfo) = Unit

                // Not named notify(): that is java.lang.Object.notify(), and a private method
                // with the same JVM signature is an accidental override.
                private fun signal() = synchronized(latch) { latch.notifyAll() }
            }

            return try {
                manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                synchronized(latch) {
                    if (port.get() <= 0) latch.wait(timeoutMs)
                }
                runCatching { manager.stopServiceDiscovery(listener) }
                port.get().takeIf { it > 0 }
            } catch (t: Throwable) {
                runCatching { runCatching { manager.stopServiceDiscovery(listener) } }
                null
            }
        }
    }
}
