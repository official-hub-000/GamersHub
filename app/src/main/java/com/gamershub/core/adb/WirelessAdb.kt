package com.gamershub.core.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import io.github.muntashirakon.adb.AdbAuthenticationFailedException
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Wireless-debugging transport — Shizuku's job, done without Shizuku.
 *
 * ## Why this exists
 *
 * Shizuku is a service that runs as uid 2000 (shell) so an app can borrow that uid.
 * Wireless debugging is the same uid, reached a different way: Android's own `adbd`
 * listens on loopback / Wi-Fi with a TLS connection, and the user authorises this app
 * once by pairing with the six-digit code the Settings screen shows. After that,
 * GamersHub holds a shell connection of its own and no longer needs Shizuku at all.
 *
 * That is why this is not a parallel feature set. It is a third **transport** under
 * the same [com.gamershub.core.shizuku.Shell] surface, which is why every existing
 * call site — Game Space, tools, restore, capture — works through it untouched.
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

    /** Local address advertised by adbd (used as fallback if 127.0.0.1 is refused). */
    @Volatile
    private var discoveredHost: String = "127.0.0.1"

    @Volatile
    private var appContext: Context? = null

    private const val LOOPBACK = "127.0.0.1"

    fun attached(context: Context) {
        appContext = context.applicationContext
    }

    fun connected(): Boolean {
        if (status != Status.CONNECTED) return false
        val ctx = appContext ?: return true
        val alive = runCatching { AdbConnectionManager.get(ctx).isConnected }.getOrDefault(false)
        if (!alive) {
            status = Status.OFF
        }
        return alive
    }

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
    fun discover(context: Context, timeoutMs: Long = 5_000): Boolean {
        attached(context)
        val found = discoverShellPort(context, timeoutMs)
        // Pairing is only advertised while the pairing dialog is open, so a miss here
        // is normal and must not be treated as a failure.
        runCatching { discoverPairingPort(context, 2_000) }
        return found
    }

    private fun discoverShellPort(context: Context, timeoutMs: Long): Boolean {
        val res = NsdFinder(context, "_adb-tls-connect._tcp").find(timeoutMs)
        if (res != null && res.second > 0) {
            discoveredHost = res.first
            shellPort = res.second
            return true
        }
        return false
    }

    private fun discoverPairingPort(context: Context, timeoutMs: Long): Int {
        val res = NsdFinder(context, "_adb-tls-pairing._tcp").find(timeoutMs)
        if (res != null && res.second > 0) {
            discoveredHost = res.first
            pairingPort = res.second
            return res.second
        }
        return 0
    }

    /**
     * Pairs with the code shown in Settings › Wireless debugging › Pair device with
     * pairing code.
     *
     * Must run off the main thread: it opens a TLS socket to adbd, runs SPAKE2, and
     * waits for the daemon. When [port] is 0, mDNS discovery is always run fresh so a
     * stale port from an earlier dialog is never reused.
     */
    fun pair(context: Context, code: String, port: Int = 0): Pair<Boolean, String> {
        attached(context)
        if (!wirelessSupported()) {
            lastError = "Wireless pairing needs Android 10 or newer (TLS 1.3)"
            return false to lastError
        }
        val cleanCode = code.filter { it.isDigit() }
        if (cleanCode.length != 6) {
            return false to "The pairing code is the six digits shown on the phone"
        }

        val targetPort = if (port in 1..65535) {
            pairingPort = port
            port
        } else {
            pairingPort = 0
            discoverPairingPort(context, 6_000)
        }
        if (targetPort <= 0) {
            lastError = "Could not find the pairing service — keep the 6-digit pairing dialog open on screen, or enter its port"
            return false to lastError
        }

        val hosts = buildList {
            add(LOOPBACK)
            if (discoveredHost.isNotBlank() && discoveredHost != LOOPBACK) add(discoveredHost)
        }

        var lastThrowable: Throwable? = null
        val manager = AdbConnectionManager.get(context)
        for (h in hosts) {
            try {
                val ok = manager.pair(h, targetPort, cleanCode)
                if (ok) {
                    lastError = ""
                    pairingPort = 0
                    return true to "Paired"
                }
            } catch (t: Throwable) {
                lastThrowable = t
            }
        }

        pairingPort = 0
        val detail = lastThrowable?.let { describeError(it, duringPair = true) }
            ?: "The pairing code was rejected"
        lastError = detail
        return false to detail
    }

    /**
     * Opens the shell connection.
     *
     * Assumes pairing already happened. When [portOverride] > 0, connects directly to
     * that port; otherwise uses mDNS discovery (and refreshes if a cached port fails).
     */
    fun connect(
        context: Context,
        timeoutMs: Long = 10_000,
        portOverride: Int = 0
    ): Pair<Boolean, String> {
        attached(context)
        if (!wirelessSupported()) {
            status = Status.OFF
            lastError = "Wireless debugging needs Android 10 or newer"
            return false to lastError
        }

        val manager = AdbConnectionManager.get(context)
        if (manager.isConnected) {
            val check = AdbExec.run(manager, "id", 4_000)
            if (check != null && check.contains("uid=")) {
                status = Status.CONNECTED
                lastError = ""
                return true to check.trim()
            }
            runCatching { manager.disconnect() }
        }

        if (portOverride in 1..65535) {
            shellPort = portOverride
        } else if (shellPort <= 0) {
            discoverShellPort(context, 6_000)
        }

        if (shellPort <= 0) {
            status = Status.OFF
            lastError = "Wireless debugging is not on — enable it in Developer options, or enter the port manually"
            return false to lastError
        }

        val firstAttempt = tryConnectPort(context, manager, shellPort)
        if (firstAttempt.first) return firstAttempt

        // If the cached port was stale (wireless debugging was toggled), re-discover once.
        if (portOverride <= 0 && status == Status.OFF) {
            val prevPort = shellPort
            shellPort = 0
            if (discoverShellPort(context, 5_000) && shellPort > 0 && shellPort != prevPort) {
                return tryConnectPort(context, manager, shellPort)
            }
        }
        return firstAttempt
    }

    private fun tryConnectPort(
        context: Context,
        manager: AdbConnectionManager,
        port: Int
    ): Pair<Boolean, String> {
        val hosts = buildList {
            add(LOOPBACK)
            if (discoveredHost.isNotBlank() && discoveredHost != LOOPBACK) add(discoveredHost)
        }
        var lastThrowable: Throwable? = null
        for (h in hosts) {
            try {
                runCatching { if (manager.isConnected) manager.disconnect() }
                val ok = manager.connect(h, port)
                if (ok) {
                    val out = AdbExec.run(manager, "id", 6_000)
                    if (out != null && out.contains("uid=")) {
                        status = Status.CONNECTED
                        lastError = ""
                        runCatching {
                            context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
                                .edit().putBoolean("wireless_enabled", true).apply()
                        }
                        return true to out.trim()
                    } else {
                        runCatching { manager.disconnect() }
                        status = Status.UNAUTHORISED
                        lastError = "Connected but adbd refused the shell"
                        return false to lastError
                    }
                } else {
                    status = Status.NOT_PAIRED
                    lastError = "Connection timed out — check Wireless debugging or re-pair"
                    return false to lastError
                }
            } catch (t: AdbPairingRequiredException) {
                status = Status.NOT_PAIRED
                lastError = "Not paired — enter the 6-digit pairing code from Wireless debugging"
                runCatching { WirelessNotification.showPairingPrompt(context, lastError) }
                return false to lastError
            } catch (t: AdbAuthenticationFailedException) {
                status = Status.UNAUTHORISED
                lastError = "Not authorised on this phone — tap Forget pairing and pair again"
                runCatching { WirelessNotification.showPairingPrompt(context, lastError) }
                return false to lastError
            } catch (t: Throwable) {
                lastThrowable = t
            }
        }

        val err = lastThrowable
        val msg = if (err != null) describeError(err, duringPair = false) else "connection failed"
        status = when {
            err is AdbPairingRequiredException ||
                msg.contains("pair", ignoreCase = true) ||
                msg.contains("certificate", ignoreCase = true) ||
                msg.contains("protocol error", ignoreCase = true) -> Status.NOT_PAIRED
            err is java.net.ConnectException ||
                err is java.net.SocketTimeoutException ||
                msg.contains("ECONNREFUSED", ignoreCase = true) -> {
                shellPort = 0
                Status.OFF
            }
            else -> Status.UNAUTHORISED
        }
        lastError = msg
        if (status == Status.NOT_PAIRED || status == Status.UNAUTHORISED) {
            runCatching { WirelessNotification.showPairingPrompt(context, msg) }
        }
        return false to msg
    }

    private fun describeError(t: Throwable, duringPair: Boolean): String {
        val root = generateSequence(t) { it.cause }.lastOrNull() ?: t
        val raw = root.message ?: t.message ?: ""
        return when {
            raw.contains("ECONNREFUSED", ignoreCase = true) ->
                if (duringPair) "Pairing port refused connection — keep the pairing code dialog open"
                else "Port refused connection — Wireless debugging may have reset to a new port"
            raw.contains("ETIMEDOUT", ignoreCase = true) || root is java.net.SocketTimeoutException ->
                if (duringPair) "Pairing timed out — verify the 6-digit code and keep the dialog open"
                else "Timed out connecting to adbd"
            raw.contains("BAD_DECRYPT", ignoreCase = true) ||
                raw.contains("AEADBadTag", ignoreCase = true) ||
                raw.contains("Exchanging", ignoreCase = true) ||
                raw.contains("peer info", ignoreCase = true) ->
                "Wrong or expired pairing code — check the 6 digits and try again"
            raw.isNotBlank() -> raw
            else -> if (duringPair) "Pairing failed" else "Connection failed"
        }
    }

    /** Runs a command over the live connection. Null when there is no connection. */
    fun exec(context: Context, command: String, timeoutMs: Long = 12_000): String? {
        attached(context)
        if (!connected()) return null
        val out = AdbExec.run(AdbConnectionManager.get(context), command, timeoutMs)
        if (out == null && !runCatching { AdbConnectionManager.get(context).isConnected }.getOrDefault(false)) {
            status = Status.OFF
        }
        return out
    }

    /**
     * Drops the connection. The device keeps its authorisation, so the next connect
     * needs no code — this is only the network half, not a teardown of privilege.
     */
    fun disconnect() {
        runCatching { AdbConnectionManager.get(appContext ?: return).disconnect() }
        status = Status.OFF
    }

    /**
     * Forgets the pairing identity entirely and generates a fresh key and certificate.
     */
    fun forgetIdentity() {
        runCatching { AdbConnectionManager.get(appContext ?: return).resetIdentity() }
        disconnect()
        shellPort = 0
        pairingPort = 0
        lastError = ""
    }

    /**
     * One-shot mDNS lookup for local Wireless Debugging services.
     *
     * Normalises `serviceType` (Android returns a trailing dot like `_adb-tls-connect._tcp.`),
     * serialises `resolveService` calls so multiple LAN devices don't trigger
     * `FAILURE_ALREADY_ACTIVE`, and matches against this device's local interface IPs.
     */
    private class NsdFinder(private val context: Context, private val type: String) {

        private val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        private val targetType = type.trim('.')

        fun find(timeoutMs: Long): Pair<String, Int>? {
            val manager = nsd ?: return null
            val localAddrs = localInterfaceAddresses()
            val best = AtomicReference<Pair<String, Int>?>(null)
            val fallback = AtomicReference<Pair<String, Int>?>(null)
            val latch = CountDownLatch(1)
            val resolveLock = Any()
            var resolving = false
            val pending = ArrayDeque<NsdServiceInfo>()

            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val multicastLock = runCatching {
                wifi?.createMulticastLock("gamershub_mdns")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.getOrNull()

            fun resolveNext() {
                val next: NsdServiceInfo
                synchronized(resolveLock) {
                    if (resolving || best.get() != null) return
                    next = pending.removeFirstOrNull() ?: return
                    resolving = true
                }
                runCatching {
                    manager.resolveService(next, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(i: NsdServiceInfo, errorCode: Int) {
                            synchronized(resolveLock) { resolving = false }
                            resolveNext()
                        }

                        override fun onServiceResolved(i: NsdServiceInfo) {
                            val host = i.host?.hostAddress?.substringBefore('%')
                            val port = i.port
                            if (!host.isNullOrBlank() && port > 0) {
                                val pair = host to port
                                if (host in localAddrs || host == LOOPBACK) {
                                    best.compareAndSet(null, pair)
                                    latch.countDown()
                                } else {
                                    fallback.compareAndSet(null, pair)
                                }
                            }
                            synchronized(resolveLock) { resolving = false }
                            resolveNext()
                        }
                    })
                }.onFailure {
                    synchronized(resolveLock) { resolving = false }
                    resolveNext()
                }
            }

            val listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = latch.countDown()
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onServiceFound(info: NsdServiceInfo) {
                    val foundType = info.serviceType?.trim('.') ?: return
                    if (foundType != targetType) return
                    synchronized(resolveLock) {
                        pending.addLast(info)
                    }
                    resolveNext()
                }

                override fun onServiceLost(info: NsdServiceInfo) = Unit
            }

            return try {
                manager.discoverServices(targetType, NsdManager.PROTOCOL_DNS_SD, listener)
                latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                best.get() ?: fallback.get()
            } catch (_: Throwable) {
                best.get() ?: fallback.get()
            } finally {
                runCatching { manager.stopServiceDiscovery(listener) }
                runCatching { if (multicastLock?.isHeld == true) multicastLock.release() }
            }
        }

        private fun localInterfaceAddresses(): Set<String> = runCatching {
            val set = HashSet<String>()
            for (ni in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (addr in Collections.list(ni.inetAddresses)) {
                    addr.hostAddress?.substringBefore('%')?.let { set.add(it) }
                }
            }
            set
        }.getOrDefault(emptySet())
    }
}
