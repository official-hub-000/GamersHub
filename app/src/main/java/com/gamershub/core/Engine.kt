package com.gamershub.core

import android.content.Context
import android.content.Intent
import android.os.Build
import com.gamershub.GamersHubApp
import com.gamershub.core.capture.RecorderService
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
 * Every privileged or overlay surface in GamersHub is owned here. When the engine
 * goes OFF, [stopEverything] walks *all* of them — both the root path and the
 * Shizuku path — and tears them down, then clears the temporary root provider and
 * releases the root session. Turning it off therefore leaves nothing running:
 * no overlays, no capture, no upscale mirror, no root shell, no Shizuku user
 * service, and all display/property changes reverted.
 *
 * This is deliberately a single object rather than per-screen flags: a flag per
 * screen is exactly how things end up still running after "off".
 */
object Engine {

    private const val PREFS = "gamershub"
    private const val KEY_ENGINE_ON = "engine_master_on"

    /** True only while the master switch is on. */
    @Volatile
    var isOn: Boolean = false
        private set

    @Volatile
    var lastStopReport: String = ""
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
     * Turns the engine on. Root is optional: with it the tree is deeper, without it
     * the Shizuku path is used exactly as before.
     */
    fun start(context: Context, wantRoot: Boolean): Boolean {
        persist(context, true)
        GamersHubApp.step("engine:start")
        if (wantRoot) RootShell.acquire(context)
        Shell.ensure(context)
        return true
    }

    /**
     * Turns EVERYTHING off. Idempotent, safe to call when nothing is running, and
     * never throws — a wedged overlay must not stop the rest of the teardown.
     */
    fun stopEverything(context: Context): String {
        persist(context, false)
        GamersHubApp.step("engine:stopAll")
        val done = mutableListOf<String>()

        // 1. every declared service, explicitly. A service left out here is
        //    exactly how something keeps running after "off", so the list must
        //    stay in sync with AndroidManifest.xml.
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

        // 2. upscale / draw engine (capture + GL pipeline)
        runCatching {
            UpscaleMirror.stop(context)
            done += "upscale"
        }

        // 3. privileged state that must not survive: display overrides, game mode,
        //    spoofer props. GfxSafe is idempotent and reports what it put back.
        runCatching {
            val repairs = rootOrShell(context) { GfxSafe.repair(context) }
            if (repairs.isNotEmpty()) done += "restore(${repairs.size})"
        }

        // 4. root session + temporary provider
        runCatching {
            RootShell.run("pkill -f gamershub 2>/dev/null; true", 2_000)
            done += "root pkill"
        }
        runCatching {
            RootShell.releaseAll()
            RootShell.removeProvider(context)
            done += "root released"
        }

        // 5. Shizuku user service (the binder), and stop asking for privileges
        runCatching {
            Shell.disconnect()
            Shell.exec("pkill -f GamersHubShellService 2>/dev/null; true")
            done += "shizuku released"
        }

        lastStopReport = "stopped: " + done.joinToString(", ")
        return lastStopReport
    }

    /**
     * Chooses root first, Shizuku second, and reports which one answered. Features
     * that used to be Shizuku-only go through here so root automatically enables
     * them with no further changes.
     */
    fun exec(context: Context, command: String): String? {
        if (!isOn) return null
        if (RootShell.ready()) {
            RootShell.run(command)?.let { return it }
        }
        return Shell.exec(command)
    }

    fun available(context: Context): Boolean =
        isOn && (RootShell.ready() || Shell.available())

    /** Human-readable state for the UI cards. */
    fun statusLine(context: Context): String = when {
        !isOn -> "Engine off — nothing running"
        RootShell.ready() -> "Engine on · root · ${RootShell.method}"
        Shell.available() -> "Engine on · Shizuku"
        else -> "Engine on · no privilege (overlays only)"
    }

    private fun <T> rootOrShell(context: Context, block: () -> T): T = block()
}
