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
 * ON  → snapshot the device, then allow privileged work through the shell
 *        channels (Shizuku, wireless debugging, the in-app daemon).
 * OFF → *restore to default*: every overlay, capture surface, shell binding,
 *        settings key and display override the app made is undone.
 *
 * There is no root. GamersHub never acquires uid 0; privileged work runs at
 * the shell user (uid 2000) or not at all.
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

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reads the persisted state without starting anything. */
    fun restore(context: Context) {
        isOn = prefs(context).getBoolean(KEY_ENGINE_ON, false)
    }

    private fun persist(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENGINE_ON, on).apply()
        isOn = on
    }

    /**
     * Turns the engine on.
     *
     * A snapshot of the device is taken *first*, so that OFF has something exact
     * to return to. Shell bindings are re-attached; nothing is escalated.
     */
    fun start(context: Context): String {
        GamersHubApp.step("engine:start")

        // Capture before anything is written. This is what makes "restore to
        // default" exact rather than approximate.
        runCatching { Restore.capture(context) }

        persist(context, true)

        runCatching { Shell.ensure(context) }

        // Re-attach the wireless-debugging shell when the user turned it on for this
        // engine before. This is a reconnect, never an enable: GamersHub does not have
        // (and does not ask for) the permission to switch wireless debugging on, so a
        // device where the user turned it off is left exactly as it is.
        runCatching {
            if (context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
                    .getBoolean("wireless_enabled", false)
            ) {
                com.gamershub.core.adb.WirelessAdb.attached(context)
                if (!com.gamershub.core.adb.WirelessAdb.connected()) {
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

        // 3. UNDO PRIVILEGED CHANGES. A snapshot cannot reverse everything —
        //    anything not re-derivable was journaled, and this is the last moment
        //    where a shell still exists to replay it with. Runs before Restore so
        //    the journal's own reports land in the same summary.
        runCatching {
            val undone = com.gamershub.core.ChangeLog.undo(context) { cmd ->
                Channel.exec(context, cmd, 12_000)
            }
            if (!undone.empty) {
                done += "changes: ${undone.report()}"
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

        // 5. Tear down shell transports. One call for all of them so none is forgotten.
        //    The Shizuku user service is killed too; the binder itself is dropped.
        runCatching {
            val released = mutableListOf<String>()
            runCatching {
                Shell.disconnect()
                released += "Shizuku binder"
            }
            runCatching {
                com.gamershub.core.adb.ShellDaemon.stop(context)
                released += "shell daemon"
            }
            // Only the socket is dropped; the device keeps this app authorised, so the
            // next connect needs no pairing code.
            runCatching {
                if (com.gamershub.core.adb.WirelessAdb.connected()) {
                    com.gamershub.core.adb.WirelessAdb.disconnect()
                    released += "wireless debugging link"
                }
            }
            if (released.isNotEmpty()) done += "released ${released.joinToString(", ")}"
        }

        // 6. Shizuku's user service.
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
     * Privileged exec through the best shell channel. Honours the master switch:
     * with the engine off there is no channel, so a stale caller cannot slip work through.
     */
    fun exec(context: Context, command: String): String? = Channel.exec(context, command)

    fun available(context: Context): Boolean =
        isOn && Channel.kind() != Channel.Kind.NONE

    /** One-line state for cards. Says which channel is live and what it can reach. */
    fun statusLine(context: Context): String = when {
        !isOn -> "Engine off — phone restored"
        Channel.kind() == Channel.Kind.DAEMON ->
            "Engine on · GamersHub daemon (uid 2000) · independent of ADB"
        Channel.kind() == Channel.Kind.WIRELESS ->
            "Engine on · wireless debugging (shell subset)"
        Channel.kind() == Channel.Kind.SHIZUKU ->
            "Engine on · Shizuku (shell subset)"
        else -> "Engine on · no privilege — overlays only"
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
        if (Shell.available()) out += "Shizuku connection"
        if (com.gamershub.core.adb.ShellDaemon.alive) out += "shell daemon"
        if (com.gamershub.core.adb.WirelessAdb.connected()) out += "wireless debugging link"
        if (GfxSafe.isUnclean()) out += "leftover graphics properties"
        if (Settings.canDrawOverlays(context)) out += "overlay window permission (kept by Android)"
        return out
    }
}
