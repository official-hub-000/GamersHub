package com.gamershub.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.gamershub.GamersHubApp
import com.gamershub.core.capture.Recorder
import com.gamershub.core.capture.RecorderService
import com.gamershub.core.capture.Stream
import com.gamershub.core.capture.StreamService
import com.gamershub.core.root.RootShell
import com.gamershub.core.shizuku.Shell
import com.gamershub.core.upscale.UpscaleMirror
import com.gamershub.core.upscale.UpscaleMirrorService
import com.gamershub.overlay.CrosshairOverlayService
import com.gamershub.overlay.FloatingPanelService
import com.gamershub.overlay.MappingOverlayService
import com.gamershub.overlay.MonitorOverlayService
import com.gamershub.overlay.MouseCursorService

/**
 * The master engine switch.
 *
 * ON  → snapshot the device, then allow privileged work.
 * OFF → *restore to default*: every overlay, capture surface, root session,
 *        Shizuku binder, settings key, display override, property and identity
 *        change the app made is undone.
 *
 * The difference between this and "stop the services" is the point of the whole
 * object. Stopping services makes the app stop doing new things; restoring makes
 * the phone the way it was. A user who turns the engine off expects the second.
 *
 * [stopEverything] therefore ends with [Restore.restore], which is snapshot-based
 * and idempotent. Turning the engine off twice is harmless; turning it off with no
 * snapshot still resets display and settings to platform defaults.
 */
object Engine {

    private const val PREFS = "gamershub"
    private const val KEY_ENGINE_ON = "engine_master_on"
    private const val KEY_ROOT_OPT_IN = "engine_root_opt_in"

    /** True only while the master switch is on. */
    @Volatile
    var isOn: Boolean = false
        private set

    /** True while [stopEverything] is running, so teardown can still use a shell. */
    @Volatile
    var isStopping: Boolean = false
        private set

    @Volatile
    var lastStopReport: String = ""
        private set

    /** Full detail of the last restore, for the report panel. */
    @Volatile
    var lastRestore: Restore.Report? = null
        private set

    /** How the last start reached privilege, for the Root tab and the panel. */
    @Volatile
    var lastLadder: com.gamershub.core.root.PrivilegeLadder.Result? = null
        private set

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reads the persisted state without starting anything. */
    fun restore(context: Context) {
        isOn = prefs(context).getBoolean(KEY_ENGINE_ON, false)
    }

    /** Whether the user asked to use root. Root is an option, never required. */
    fun rootOptIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ROOT_OPT_IN, false)

    fun setRootOptIn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ROOT_OPT_IN, on).apply()
        if (!on) RootShell.releaseAll()
    }

    private fun persist(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENGINE_ON, on).apply()
        isOn = on
    }

    /**
     * Turns the engine on.
     *
     * A snapshot of the device is taken *first*, so that OFF has something exact
     * to return to. Root is attempted only when the user opted in — a Shizuku-only
     * user gets the shell subset and is never nagged for su.
     */
    fun start(context: Context, wantRoot: Boolean = false): String {
        GamersHubApp.step("engine:start")

        // Capture before anything is written. This is what makes "restore to
        // default" exact rather than approximate.
        runCatching { Restore.capture(context) }

        persist(context, true)

        // Root is an option: attempted only when the caller asked for it. A
        // successful grant is remembered so later starts do not re-prompt, and a
        // failed one is remembered too so we stop nagging a user who has no root.
        // Climb the privilege ladder: su from the device, then su via Shizuku, then the
        // bundled temporary provider. Whichever rung answers is reported, so a
        // KernelSU device and a DFRoot-style per-boot device both end up with working
        // root and an honest label.
        if (wantRoot) {
            val ladder = runCatching {
                com.gamershub.core.root.PrivilegeLadder.climb(context, wantRoot = true)
            }.getOrNull()
            if (ladder?.reachedRoot == true) setRootOptIn(context, true)
            lastLadder = ladder
        }
        runCatching { Shell.ensure(context) }

        // Re-attach the wireless-debugging shell when the user turned it on for this
        // engine before. This is a reconnect, never an enable: GamersHub does not have
        // (and does not ask for) the permission to switch wireless debugging on, so a
        // device where the user turned it off is left exactly as it is.
        //
        // Reset first. adbd drops every connection when the phone reboots or wireless
        // debugging is toggled, but our status flag survives that — so the check below
        // used to short-circuit on a connection that was dead, and the reconnect never
        // ran. That single line is why "wireless debugging not working" survived a
        // successful pairing.
        runCatching {
            if (context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
                    .getBoolean("wireless_enabled", false)
            ) {
                com.gamershub.core.adb.WirelessAdb.attached(context)
                val stale = com.gamershub.core.adb.WirelessAdb.rememberedPort(context)
                if (stale > 0) {
                    // Try the last known port before spending seconds on mDNS.
                    com.gamershub.core.adb.WirelessAdb.connect(context, 4_000, stale)
                }
                // alive(), not connected(): connected() is our own flag and survives a
                // reboot or a wireless-debugging toggle, so trusting it here meant the
                // reconnect above was skipped every time the socket had already died.
                if (!com.gamershub.core.adb.WirelessAdb.alive(context)) {
                    com.gamershub.core.adb.WirelessAdb.connect(context)
                }
            }
        }

        // Bring the shell daemon back if the user had one. This is what makes the
        // privilege survive a restart of the app rather than of the phone: the daemon
        // may still be running from the last session, and if it is not, the transport
        // that is live right now can start it again.
        runCatching {
            com.gamershub.core.adb.ShellDaemon.attached(context)
            if (!com.gamershub.core.adb.ShellDaemon.ping() &&
                context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
                    .getBoolean("daemon_enabled", false)
            ) {
                com.gamershub.core.adb.ShellDaemon.start(context)
            }
        }
        return statusLine(context)
    }

    /**
     * Turns EVERYTHING off and puts the phone back.
     *
     * Idempotent, safe when nothing is running, and never throws: a wedged overlay
     * must not stop the rest of the teardown. Every stage is isolated.
     */
    fun stopEverything(context: Context): String {
        isStopping = true
        persist(context, false)
        GamersHubApp.step("engine:stopAll")
        val done = mutableListOf<String>()

        // 1. Every declared service, explicitly. A service missing from this list
        //    is exactly how something keeps running after "off".
        for ((label, cls) in listOf(
            "panel" to FloatingPanelService::class.java,
            "crosshair" to CrosshairOverlayService::class.java,
            "monitor" to MonitorOverlayService::class.java,
            "mapping" to MappingOverlayService::class.java,
            "cursor" to MouseCursorService::class.java,
            "recorder" to RecorderService::class.java,
            "stream" to StreamService::class.java,
            "upscale-service" to UpscaleMirrorService::class.java,
        )) {
            runCatching { context.stopService(Intent(context, cls)); done += label }
        }

        // 2. Capture / draw pipeline.
        runCatching { UpscaleMirror.stop(context); done += "upscale" }

        // 3. Stop producing new privileged work before restoring state.
        runCatching {
            RootShell.run("pkill -f gamershub 2>/dev/null; true", 2_000)
            done += "root procs"
        }

        // 3b. UNDO PRIVILEGED CHANGES. A snapshot cannot reverse a module toggle or a
        //     flashed zip — those have to be replayed from the journal, and this is the
        //     last moment where a shell still exists to replay them with. Runs before
        //     Restore so the journal's own reports land in the same summary.
        runCatching {
            val undone = com.gamershub.core.ChangeLog.undo(context) { cmd ->
                Channel.exec(context, cmd, 12_000) ?: RootShell.run(cmd, 12_000)
            }
            if (!undone.empty) {
                done += "changes: ${undone.report()}"
                // Surfaced to the user because these are the changes that only take
                // effect at the next boot — saying "restored" would be a lie until then.
                if (undone.needsReboot.isNotEmpty()) {
                    done += "reboot to finish: ${undone.needsReboot.take(3).joinToString(", ")}"
                }
            }
        }

        // 4. RESTORE. This is the part that turns "off" into "as it was".
        val report = runCatching { Restore.restore(context) }.getOrNull()
        if (report != null) {
            lastRestore = report
            if (report.restored.isNotEmpty()) done += "restored ${report.restored.size}"
            if (report.skipped.isNotEmpty()) done += "skipped ${report.skipped.size}"
            if (report.failed.isNotEmpty()) done += "failed ${report.failed.size}"
        } else {
            done += "restore error"
        }

        // 5. Down the ladder: root session, temporary provider, Shizuku binder. One
        //    call, because three separate teardowns is how one of them gets forgotten.
        runCatching {
            done += com.gamershub.core.root.PrivilegeLadder.descend(context)
            lastLadder = null
        }

        // 6. Shizuku's user service (the binder itself is dropped in step 5).
        runCatching {
            Shell.exec("pkill -f GamersHubShellService 2>/dev/null; true")
        }

        // 7. Local app state that represents "the engine was running".
        runCatching {
            context.getSharedPreferences("gamershub", Context.MODE_PRIVATE).edit()
                .putBoolean("session", false)
                .apply()
        }

        isStopping = false
        lastStopReport = done.joinToString(", ")
        GamersHubApp.step("engine:off $lastStopReport")
        return lastStopReport
    }

    /**
     * Privileged exec through the best channel. Honours the master switch: with the
     * engine off there is no channel, so a stale caller cannot slip work through.
     */
    fun exec(context: Context, command: String): String? = Channel.exec(context, command)

    fun available(context: Context): Boolean =
        isOn && Channel.kind() != Channel.Kind.NONE

    /** One-line state for cards. Says which channel is live and what it can reach. */
    fun statusLine(context: Context): String = when {
        !isOn -> "Engine off — phone restored"
        Channel.kind() == Channel.Kind.ROOT ->
            "Engine on · root · ${RootShell.method}"
        Channel.kind() == Channel.Kind.DAEMON ->
            "Engine on · GamersHub daemon (uid 2000) · independent of ADB"
        Channel.kind() == Channel.Kind.WIRELESS ->
            "Engine on · wireless debugging (shell subset) · root adds ${missingFromShizuku()}"
        Channel.kind() == Channel.Kind.SHIZUKU ->
            "Engine on · Shizuku (shell subset) · root adds ${missingFromShizuku()}"
        else -> "Engine on · no privilege — overlays only"
    }

    /** Names the capabilities only root would add, for the upgrade prompt. */
    fun missingFromShizuku(): String {
        val blocked = Channel.blocked().map { it.first }
        return when {
            blocked.isEmpty() -> "nothing"
            blocked.size >= 4 -> "props, /data/adb, kernel tuning"
            else -> blocked.joinToString(", ") { it.capabilityLabel }
        }
    }

    /**
     * What turning the engine off will undo, listed for the confirmation sheet.
     * Reading this before stopping is what makes the button honest.
     */
    fun pendingChanges(context: Context): List<String> {
        val out = mutableListOf<String>()
        if (Restore.hasSnapshot(context)) {
            out += "settings changed by GamersHub"
            out += "display size/density override"
        }
        if (FloatingPanelService.isRunning ||
            MonitorOverlayService.isRunning ||
            CrosshairOverlayService.isRunning ||
            MappingOverlayService.isRunning ||
            MouseCursorService.isRunning
        ) out += "running overlays"
        if (Recorder.isRecording) out += "screen recording"
        if (Stream.isStreaming) out += "live stream"
        if (RootShell.ready()) out += "root shell session"
        if (Shell.available()) out += "Shizuku connection"
        if (GfxSafe.isUnclean()) out += "leftover graphics properties"
        if (Settings.canDrawOverlays(context)) out += "overlay window permission (kept by Android)"
        return out
    }

    private fun <T> rootOrShell(context: Context, block: () -> T): T = block()
}
