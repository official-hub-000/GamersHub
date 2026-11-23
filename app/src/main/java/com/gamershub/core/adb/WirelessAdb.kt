package com.gamershub.core.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
 * ## The bug that made this look permanently broken
 *
 * The two load-bearing facts live in the vendored libadb, and each is individually
 * reasonable:
 *
 *  1. `AbsAdbConnectionManager.isConnected()` is
 *     `mAdbConnection != null && isConnected() && isConnectionEstablished()`. The
 *     `isConnectionEstablished()` term is the important one, and it lives in the vendored
 *     `AdbConnection` — it is NOT the plain `java.net.Socket.isConnected()` flag, which
 *     would stay true forever once the object had connected once. (This comment used to
 *     claim it was that flag; reading the vendored source shows the library is stricter
 *     than that.) The failure mode it does have is on the other side: once the link is
 *     torn down, `connect()` refuses to dial again until the object is discarded.
 *
 *  2. `AbsAdbConnectionManager.connect(port)` opens with
 *     `if (isConnected()) return false;` — it bails out *before dialing*, and returns
 *     the same `false` it would return for a real failure.
 *
 * Together: after `adbd` drops the link (reboot, toggling wireless debugging, an idle
 * timeout), the dead `AdbConnection` still passes `isConnected()`, so every later
 * `connect()` returned `false` **without opening a socket**, and this class reported
 * that as "Not paired — use the pairing code". The right port, the right certificate,
 * a fresh pairing — none of it could help, because nothing was ever dialed. Only
 * force-stopping the app cleared it, which is why it looked like the feature "just
 * doesn't work".
 *
 * [teardown] below is the fix: the old connection object is destroyed before every
 * attempt, so `connect()` cannot take that early exit, and "connected" is decided by
 * an actual round trip ([verify]) rather than by a socket flag.
 *
 * ## State
 *
 * [Status.CONNECTED] is only ever set after a command has come back from the shell.
 * [lastError] always names the specific failure, because "it didn't work" is the
 * thing that makes a network panel useless.
 *
 * ## Discovery
 *
 * The ports are ephemeral and change whenever wireless debugging is toggled, so they
 * are discovered over mDNS exactly as the platform advertises them:
 * `_adb-tls-connect._tcp` for the shell port, `_adb-tls-pairing._tcp` for pairing.
 * A saved port is tried first because it is free when it is right; when it is wrong
 * the answer is a refused socket in milliseconds, not a six-second mDNS wait.
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

        /**
         * The device is reachable but this app could not complete the TLS pairing
         * handshake — a fault in the bundled crypto, not in the phone.
         *
         * Kept separate from [OFF] on purpose: every remedy for [OFF] (turn wireless
         * debugging off and on, re-pair, reboot) is useless here, and telling the user
         * to try them is what wasted their time in the first place.
         */
        UNSUPPORTED,
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

    /** Guarded: pairing and connecting must not run at the same time. */
    private val guard = Any()

    fun attached(context: Context) {
        appContext = context.applicationContext
    }

    fun connected(): Boolean = status == Status.CONNECTED

    /**
     * True when the socket is genuinely still up.
     *
     * Deliberately *not* `AdbConnectionManager.isConnected` on its own: see the class
     * comment. A dead link whose peer closed it still answers true there, which is
     * exactly what kept the reconnect from ever running.
     */
    fun alive(context: Context): Boolean = runCatching {
        connected() && AdbConnectionManager.get(context).isConnected && verify(AdbConnectionManager.get(context))
    }.getOrDefault(false)

    /**
     * A stage-by-stage account of the last attempt, for the sheet and DIAGNOSE.
     *
     * "Not working" is not a diagnosis, and every failure in this file looks the same on
     * screen: a red row and a sentence. These are the facts that actually distinguish the
     * stages — which ports were tried, who answered, whether the pairing tunnel got as far
     * as the TLS handshake, and which provider supplied the keying material — so one
     * screenshot identifies the broken stage instead of a fourth round of guessing.
     */
    fun diagnostics(context: Context): List<String> = buildList {
        add("status        ${status.name.lowercase()} — ${statusLabel()}")
        add("wireless      ${wirelessSupported()} (needs API 29+; this is ${android.os.Build.VERSION.SDK_INT})")
        // The single fact that explains the most confusing failure this screen has shown.
        add("debug toggle  ${when (wirelessDebuggingEnabled(context)) {
            true -> "on"
            false -> "OFF — no adbd can answer"
            null -> "not readable"
        }}")
        add("nearby perm   ${nearbyGranted(context)}")
        add("settings port ${settingsPort(context) ?: "not readable"}")
        add("remembered    ${rememberedPort(context).takeIf { it > 0 } ?: "none"}")
        add("discovered    ${shellPort.takeIf { it > 0 } ?: "not found"}")
        add("pairing port  ${pairingPort.takeIf { it > 0 } ?: "not found"}")
        add("connected     ${connected()} (daemon ${com.gamershub.core.adb.ShellDaemon.alive})")
        add("tls provider  ${tlsProvider()}")
        add("last error    ${lastError.ifBlank { "—" }}")
    }

    /**
     * Which SSL provider pairing will use.
     *
     * The bundled `org.conscrypt` is the one that can export keying material on recent
     * Android; the platform copy is hidden API and refuses. Reporting which one is in use
     * is the difference between "pairing is broken" and "this build shipped without its
     * Conscrypt".
     */
    private fun tlsProvider(): String = runCatching {
        val bundled = runCatching { Class.forName("org.conscrypt.OpenSSLProvider") }.isSuccess
        val platform = runCatching { Class.forName("com.android.org.conscrypt.OpenSSLProvider") }.isSuccess
        when {
            bundled -> "bundled Conscrypt (can export keying material)"
            platform -> "platform Conscrypt only — pairing will fail on API 36+"
            else -> "none found"
        }
    }.getOrDefault("unknown")

    /** Whether the Android 13+ Nearby-devices permission has actually been granted. */
    fun nearbyGranted(context: Context): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT < 33) true
        else context.checkSelfPermission(android.Manifest.permission.NEARBY_WIFI_DEVICES) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** Last port that actually carried a shell, or 0. */
    fun rememberedPort(context: Context): Int = runCatching {
        context.getSharedPreferences("gamershub", Context.MODE_PRIVATE).getInt("wireless_port", 0)
    }.getOrDefault(0)

    /**
     * What the pill says.
     *
     * `Status.OFF` is the *initial* state as well as a genuine "adbd did not answer", and
     * calling both "Wireless debugging off" was asserting something the app had not
     * checked — on a fresh install it had not tried at all. Now it says which of the two
     * it is: untouched, or tried and nothing answered.
     */
    fun statusLabel(): String = when (status) {
        Status.CONNECTED -> "Connected"
        Status.NOT_PAIRED -> "Pair needed"
        Status.UNAUTHORISED -> "Not authorised"
        Status.OFF -> if (lastError.isBlank()) "Not connected yet" else "No adbd answered"
        Status.UNSUPPORTED -> "Pairing unsupported here"
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
        val mcLock = runCatching {
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
            runCatching { if (mcLock?.isHeld == true) mcLock.release() }
        }
    }

    /**
     * Finds the *connect* port after a successful pairing.
     *
     * Two things make this its own step rather than a reuse of the pre-pair attempt:
     *
     *  1. adbd starts advertising `_adb-tls-connect._tcp` only once the device is paired,
     *     and it does so a moment later — so a discovery that failed before pairing
     *     failing again immediately afterwards means nothing.
     *  2. The connect port is a different number from the pairing port. They are separate
     *     listed ports on the same Settings screen, so a user who types the pairing port
     *     into the shell field gets a connection refused and concludes pairing failed.
     *
     * Returns the port, or 0 when nothing answered. Never throws.
     */
    fun rediscoverShellPort(context: Context): Int {
        // Give adbd a moment to publish the service. This is a local network broadcast;
        // 1.2 s covers the gap between the pairing tunnel closing and the daemon
        // advertising, which is where the earlier attempts were losing it.
        runCatching { Thread.sleep(1_200) }
        if (discoverShellPort(context, 4_000) && shellPort > 0) return shellPort
        // A second, shorter sweep: some stacks drop the first discovery after a pairing
        // tunnel has just closed on the same NsdManager instance.
        if (discoverShellPort(context, 3_000) && shellPort > 0) return shellPort
        return 0
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
        // Forget any previous connection first, for the same reason connect() does:
        // the vendored manager bails out of connect() when it thinks it is already
        // connected, and after pairing it thinks so. Without this, a successful pair was
        // followed by a connect that returned false without dialing, and the sheet
        // reported "not paired" seconds after "Paired".
        teardownQuietly(context)
        if (manual > 0) {
            // Typed in by hand — trust it and skip discovery entirely.
            pairingPort = manual
        } else {
            // ALWAYS rediscover. A pairing port lives only as long as one "Pair device
            // with pairing code" dialog: adbd closes it when the dialog is dismissed or
            // times out, and opens a different number next time. Caching it across
            // attempts meant the second and every later pairing dialled a port from a
            // previous session, got a live listener, and failed inside the SPAKE2
            // exchange — "Could not exchange peer info", which reads as bad crypto and
            // is actually a dead port. That is why re-entering the code never helped and
            // why "turn wireless off and on" sometimes did: it produced a new port.
            pairingPort = 0
            discoverPairingPort(context, 5_000)
        }
        val target = if (manual > 0) manual else pairingPort
        if (target <= 0) {
            return fail(Status.NOT_PAIRED,
                "Open Settings › Wireless debugging › Pair device with pairing code, then try again")
        }
        if (code.length != 6 || code.any { !it.isDigit() }) {
            return fail(Status.NOT_PAIRED, "The pairing code is the six digits shown on the phone")
        }
        return try {
            val manager = AdbConnectionManager.get(context)
            val ok = synchronized(guard) { manager.pair(target, code) }
            if (ok) {
                lastError = ""
                // The identity adbd has just authorised is only useful if the very next
                // connect presents the same certificate. Make that explicit, because a
                // silent regeneration here is indistinguishable from "pairing does not
                // work" — the code is accepted and the shell still refuses.
                runCatching { AdbIdentity.certificate(context) }
                true to "Paired"
            } else {
                lastError = "The pairing code was rejected"
                false to lastError
            }
        } catch (t: Throwable) {
            // Not "pairing failed": either the code was wrong (the user can fix that) or
            // our crypto refused (the user cannot) or the port was not a pairing port at
            // all. remedy() is what tells them apart, and it names the port it used so
            // the three are distinguishable without a debugger.
            lastError = remedy(t) + " (pairing port $target)"
            status = classify(t)
            false to lastError
        }
    }

    /**
     * Opens the shell connection.
     *
     * [portOverride] pins the port (typed by the user, or remembered from last time).
     * Without it the remembered port is tried, then mDNS, then a scan of adbd's usual
     * range — and only if adbd answers on none of them is "wireless debugging is off"
     * reported, which is a claim this app should make only when it is sure.
     */
    fun connect(context: Context, timeoutMs: Long = 10_000, portOverride: Int = 0): Pair<Boolean, String> {
        attached(context)
        if (!wirelessSupported()) {
            status = Status.OFF
            lastError = "Wireless debugging needs Android 10 or newer"
            return false to lastError
        }

        // Say the real reason before dialling anything. With the toggle off the platform
        // still reports the last port it used, so the attempt below would dial a dead
        // port and fail inside the TLS handshake with "Could not exchange peer info" —
        // accurate about the wrong layer.
        if (wirelessDebuggingEnabled(context) == false) {
            return fail(
                Status.OFF,
                "Wireless debugging is switched off, so there is no adbd to talk to. " +
                    "Developer options › Wireless debugging › turn it on, then connect. " +
                    "(The port this app can read is left over from last time and is not " +
                    "live — that is why it looked like something was there.)"
            )
        }

        // The fix for the whole class of "it says Not paired but I am paired": the old
        // connection object must die before connect() is called, or the manager's
        // `if (isConnected()) return false;` swallows the attempt without dialing.
        teardown(context)

        return try {
            if (portOverride > 0) {
                shellPort = portOverride
                val r = tryPort(context, portOverride)
                if (r == null) {
                    fail(Status.OFF, "Nothing answered on 127.0.0.1:$portOverride. Check the port " +
                        "shown in Settings › Wireless debugging, then connect again.")
                } else r.first to r.second
            } else {
                val candidates = candidatePorts(context)
                var result: Pair<Boolean, String>? = null
                var unpaired = false
                var cryptoFailure: String? = null
                for (candidate in candidates) {
                    val r = tryPort(context, candidate)
                    if (r == null) continue
                    // A real answer that is not a success is worth reporting as-is:
                    // re-dialing the next port would not change what adbd said.
                    if (r.first || r.third) {
                        // Through fail() even on the way out, so the status and the
                        // message the sheet shows are always the ones from this attempt
                        // and never a stale pair from an earlier one.
                        result = if (r.first) r.first to r.second
                        else fail(Status.NOT_PAIRED, r.second)
                        break
                    }
                    if (isCryptoFailure(r.second)) {
                        cryptoFailure = remedy(java.io.IOException(r.second))
                    }
                    if (r.second.contains("pair", ignoreCase = true) ||
                        r.second.contains("unauthor", ignoreCase = true)
                    ) unpaired = true
                }
                if (result == null && cryptoFailure != null) {
                    // Retrying the next port cannot fix a TLS provider that is missing
                    // its keying-material export, so this is reported as what it is.
                    return fail(Status.UNSUPPORTED, cryptoFailure)
                }
                result ?: if (unpaired) {
                    fail(Status.NOT_PAIRED, "adbd refused this identity. Use the pairing code from " +
                        "Settings › Wireless debugging › Pair device with pairing code.")
                } else {
                    // The toggle is on (checked above), so this is not "wireless is off".
                    // Either discovery could not see the service or the port never got
                    // recorded. Both are fixed by reading one number off the same Settings
                    // screen, so say that rather than repeating the connection error.
                    fail(
                        Status.OFF,
                        if (shellPort <= 0 && settingsPort(context) == null) {
                            "Wireless debugging is on, but this app cannot see the port. " +
                                "Either the network drops mDNS discovery, or the platform " +
                                "has not recorded adb_wifi_port yet. Open Developer " +
                                "options › Wireless debugging and type the port shown " +
                                "under \"IP address & port\" into the port field — it is " +
                                "a different number from the pairing port."
                        } else {
                            "adbd did not answer on any known port. Read the port under " +
                                "IP address & port in Wireless debugging and type it here."
                        }
                    )
                }
            }
        } catch (t: Throwable) {
            fail(classify(t), remedy(t))
        } finally {
            // Nothing is half-open after a failed attempt: a socket left behind is the
            // next attempt's false "already connected".
            if (status != Status.CONNECTED) teardownQuietly(context)
        }
    }

    /**
     * One attempt against one port.
     *
     * Returns null when adbd did not answer at all (refused / timed out — the port is
     * simply wrong), else the outcome plus whether it is terminal.
     */
    private fun tryPort(context: Context, port: Int): Triple<Boolean, String, Boolean>? {
        val reachable = runCatching {
            Socket().use { it.connect(InetSocketAddress(host, port), 600) }
            true
        }.getOrDefault(false)
        if (!reachable) return null

        val manager = AdbConnectionManager.get(context)
        manager.setHostAddress(host)
        val ok = try {
            manager.connect(port)
        } catch (t: Throwable) {
            // no lastError: this returns the reason to connect(), which records it
            // through fail() once it knows whether another port is worth trying.
            val msg = t.message ?: "connection failed"
            // A TLS-level refusal is definitive; a generic failure is worth another
            // port, because it usually means the port belonged to something else.
            val terminal = msg.contains("pair", true) || msg.contains("unauthor", true) ||
                msg.contains("authentication", true)
            return Triple(false, msg, terminal)
        }
        if (!ok) {
            return Triple(
                false,
                "adbd answered on $port but did not accept the connection",
                true
            )
        }

        // A completed handshake is not a working shell. "id" is the smallest command
        // that proves the identity was authorised and the stream carries data — the
        // connection used to be called CONNECTED on the handshake alone, which is how
        // a half-dead link could look healthy.
        val out = AdbExec.run(manager, "id")
        if (out != null && out.contains("uid=")) {
            shellPort = port
            status = Status.CONNECTED
            lastError = ""
            runCatching {
                context.getSharedPreferences("gamershub", Context.MODE_PRIVATE).edit()
                    .putBoolean("wireless_enabled", true)
                    .putInt("wireless_port", port)
                    .apply()
            }
            ensureDaemon(context)
            return Triple(true, out.trim(), true)
        }
        return Triple(
            false,
            "adbd accepted the handshake on $port but the shell did not answer — " +
                "if a phone prompt is asking to allow USB debugging, accept it",
            true
        )
    }

    /**
     * Ports to try, most authoritative first.
     *
     * 1. `Settings.Global.adb_wifi_port` — the number the Settings app itself stores
     *    for the live wireless-debugging server. When it is readable this is not a
     *    guess, it is the answer, and it survives the port changing on reboot.
     * 2. The port from last time, then whatever mDNS advertises.
     * 3. 5555, for a phone switched to `adb tcpip` rather than wireless debugging.
     *
     * The list is bounded and each entry costs a 600 ms dial at most, so a wrong guess
     * is cheap. Nothing here scans port ranges: that would be a 20-second sweep to
     * rediscover something the platform will tell us for free.
     */
    private fun candidatePorts(context: Context): List<Int> {
        val out = LinkedHashSet<Int>()
        settingsPort(context)?.let { out += it }
        rememberedPort(context).takeIf { it > 0 }?.let { out += it }
        shellPort.takeIf { it > 0 }?.let { out += it }
        if (discoverShellPort(context, 3_500) && shellPort > 0) out += shellPort
        out += 5555
        return out.toList()
    }

    /** The live port as recorded by the platform, or null when it is not readable. */
    private fun settingsPort(context: Context): Int? = runCatching {
        android.provider.Settings.Global.getInt(context.contentResolver, "adb_wifi_port", 0)
            .takeIf { it > 0 }
    }.getOrNull()

    /**
     * Whether Wireless debugging is switched on, as the platform records it.
     *
     * Returns null when the setting is not readable, which is not the same as "off" and
     * must not be reported as such.
     *
     * ## Why this exists
     *
     * This was the missing check behind the most confusing report the app has produced:
     * "adbd did not answer: Could not exchange peer info" while the user was entering a
     * pairing code. Wireless debugging had been switched off, so `adb_wifi_port` was
     * stale — the setting is written when the toggle comes on and is NOT cleared when it
     * goes off. [settingsPort] therefore returned a port nothing was listening on,
     * [candidatePorts] offered it first, and the failure surfaced from inside the TLS
     * handshake as a peer-info error. Every symptom pointed at the code that parses that
     * error, and none of them was the cause.
     *
     * A stale port is indistinguishable from a live one until something answers, so this
     * is checked before dialling rather than inferred afterwards.
     */
    fun wirelessDebuggingEnabled(context: Context): Boolean? = runCatching {
        android.provider.Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
    }.getOrNull()

    private fun verify(manager: AdbConnectionManager): Boolean =
        AdbExec.run(manager, "id", 3_000)?.contains("uid=") == true

    private fun fail(s: Status, message: String): Pair<Boolean, String> {
        status = s
        lastError = message
        return false to message
    }

    private fun classify(t: Throwable): Status {
        val msg = (t.message ?: "") + " " + (t.cause?.toString() ?: "")
        // "Could not exchange peer info" is the SPAKE2 exchange failing. It is NOT proof
        // of a crypto fault: a port that is no longer a pairing port, or a code from a
        // dialog that has since closed, produces exactly this too. Only the TLS-specific
        // failures below are treated as this build's fault.
        val exchangeFailed = msg.contains("exchange peer info", true)
        return when {
            !exchangeFailed && isCryptoFailure(msg) -> Status.UNSUPPORTED
            msg.contains("pair", true) || msg.contains("unauthor", true) ||
                msg.contains("authentication", true) -> Status.NOT_PAIRED
            else -> Status.OFF
        }
    }

    /**
     * True when the failure is in this app's TLS/pairing crypto rather than on the wire.
     *
     * The distinction is the difference between advice that can work and advice that
     * cannot: a crypto failure is not fixed by re-pairing, restarting wireless
     * debugging or rebooting, so it must never be reported as "not paired" or as "off".
     */
    private fun isCryptoFailure(msg: String): Boolean =
        msg.contains("NoSuchMethodException", true) ||
            msg.contains("exportKeyingMaterial", true) ||
            msg.contains("keying material", true) ||
            msg.contains("SSLException", true) ||
            msg.contains("TLSv1.3", true) ||
            msg.contains("Conscrypt", true)

    private fun remedy(t: Throwable): String {
        val msg = (t.message ?: "") + " " + (t.cause?.toString() ?: "")
        if (isCryptoFailure(msg)) {
            // Deliberately says "this build", because that is the truth: the device is
            // fine, the pairing crypto in the APK could not reach a usable TLS provider.
            return "Pairing needs TLS keying material and this Android build refused it " +
                "(${t.cause?.javaClass?.simpleName ?: t.javaClass.simpleName}). Wireless debugging " +
                "itself is fine — this is a GamersHub build fault, not something re-pairing or " +
                "rebooting can fix. Please report it with this line."
        }
        return when (classify(t)) {
            Status.NOT_PAIRED -> "adbd refused this identity — pair again with the code from Settings"
            else -> "adbd did not answer: ${t.message ?: "connection failed"}. Turn Wireless " +
                "debugging off and on, then connect again."
        }
    }

    /**
     * Runs a command over the live connection, reconnecting once if it went away.
     *
     * Shizuku self-heals: when its server restarts, the next call rebinds and the
     * command runs. This transport used to mark itself OFF on the first dead stream and
     * then answer null for the rest of the process, so a single drop — a screen-off
     * idle timeout, a Wi-Fi handover, adbd recycling — silently disabled every
     * privileged feature until the user found the sheet and tapped Connect. Same
     * guarantee now: one transparent reconnect, and only then a failure.
     */
    fun exec(context: Context, command: String, timeoutMs: Long = 12_000): String? {
        attached(context)
        if (!connected() && !reconnect(context)) return null
        val manager = AdbConnectionManager.get(context)
        val out = AdbExec.run(manager, command, timeoutMs)
        if (out != null) return out

        // The stream died underneath us. Drop the dead link and try once to restore it;
        // this is the same recovery Shizuku performs by rebinding its binder.
        status = Status.OFF
        teardownQuietly(context)
        if (!reconnect(context)) {
            lastError = "the connection dropped and could not be restored — open " +
                "Wireless debugging and tap Connect"
            return null
        }
        val retried = AdbExec.run(AdbConnectionManager.get(context), command, timeoutMs)
        if (retried == null) {
            status = Status.OFF
            lastError = "the connection dropped again during the retry — check that " +
                "Wireless debugging is still on"
            teardownQuietly(context)
        }
        return retried
    }

    /**
     * Leaves a shell behind that does not need this connection.
     *
     * This is the one architectural property Shizuku has that a bare ADB link does not:
     * Shizuku's server keeps running after the thing that started it goes away. Pairing
     * over wireless used to give exactly a connection — switch wireless debugging off and
     * every privileged feature stopped, which is the difference users notice.
     *
     * [com.gamershub.core.adb.ShellDaemon] is the same shape: it is started once as uid
     * 2000 via `app_process`, then serves over a local socket with no ADB involved. It is
     * started here, right after the first successful connect, and only if the user has
     * not switched it off — it is their phone, so `daemon_enabled` is respected rather
     * than forced.
     *
     * Best-effort by design: a device that cannot spawn it still has a working wireless
     * connection, and failing the connect over this would be worse.
     */
    private fun ensureDaemon(context: Context) {
        runCatching {
            com.gamershub.core.adb.ShellDaemon.attached(context)
            if (com.gamershub.core.adb.ShellDaemon.alive || com.gamershub.core.adb.ShellDaemon.ping()) {
                return@runCatching
            }
            val prefs = context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
            // Absent means "not decided yet": start it, because that is the behaviour that
            // matches Shizuku. Explicitly false means the user turned it off — leave it.
            if (prefs.contains("daemon_enabled") && !prefs.getBoolean("daemon_enabled", true)) {
                return@runCatching
            }
            com.gamershub.core.adb.ShellDaemon.start(context)
        }
    }

    /** Guards against a stack of callers all reconnecting at once. */
    @Volatile
    private var reconnecting = false

    /** Last time a reconnect was attempted, so a dead network is not hammered. */
    @Volatile
    private var lastReconnect = 0L

    /**
     * Restores the connection if this app was previously authorised.
     *
     * Deliberately silent and cheap when it cannot: the caller reports the failure. It
     * only fires for an identity that has already paired, because pairing needs the code
     * from Settings and this is not the place to ask for one.
     */
    private fun reconnect(context: Context): Boolean {
        val prefs = runCatching {
            context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
        }.getOrNull() ?: return false
        if (!prefs.getBoolean("wireless_enabled", false)) return false
        val now = System.currentTimeMillis()
        if (now - lastReconnect < RECONNECT_BACKOFF_MS) return false
        if (reconnecting) return false
        reconnecting = true
        return try {
            lastReconnect = now
            // connect() tears the old connection down first, which is what makes this
            // safe to call on a link that is only half dead.
            val res = connect(context)
            if (!res.first) lastError = res.second
            res.first
        } catch (t: Throwable) {
            lastError = t.message ?: "reconnect failed"
            false
        } finally {
            reconnecting = false
        }
    }

    /** How long to wait before trying to restore a dropped link again. */
    private const val RECONNECT_BACKOFF_MS = 3_000L

    /**
     * Drops the connection but keeps the identity.
     *
     * The device keeps its authorisation, so the next connect needs no code — this is
     * only the network half, not a teardown of privilege.
     *
     * Deliberately NOT [AdbConnectionManager.close]: that method destroys the private
     * key, which on a singleton would leave the next connect with a dead identity and
     * no way to recover except re-pairing.
     */
    fun disconnect() {
        val ctx = appContext
        if (ctx != null) teardownQuietly(ctx)
        status = Status.OFF
    }

    /**
     * Destroys the in-memory connection so the next [connect] actually dials.
     *
     * This is the whole bug fix: `AbsAdbConnectionManager.connect()` returns `false`
     * without opening a socket when it thinks it is already connected, and it thinks so
     * whenever the socket object has ever connected. Nulling it is the only way to make
     * the manager dial again.
     */
    private fun teardown(context: Context) {
        runCatching { AdbConnectionManager.get(context).disconnect() }
    }

    private fun teardownQuietly(context: Context) {
        runCatching { teardown(context) }
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
            val latch = CountDownLatch(1)
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

                private fun signal() = latch.countDown()
            }

            return try {
                manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                runCatching { manager.stopServiceDiscovery(listener) }
                port.get().takeIf { it > 0 }
            } catch (t: Throwable) {
                // no lastError: a silent discovery is not a failure — connect() tries
                // the platform's recorded port and 5555 as well, and only reports once
                // every candidate has actually been dialed.
                runCatching { manager.stopServiceDiscovery(listener) }
                null
            }
        }
    }
}
