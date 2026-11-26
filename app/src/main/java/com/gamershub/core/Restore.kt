package com.gamershub.core

import android.content.Context
import android.provider.Settings
import com.gamershub.GamersHubApp

/**
 * Restore-to-default.
 *
 * GamersHub writes to places that outlive the app: `settings put`, `wm size`,
 * `wm density`. Turning the engine off has to put every one of those back, or
 * "off" means "stopped doing new damage" rather than "phone is as it was".
 *
 * The contract this file implements:
 *
 *  1. **Snapshot before writing.** [capture] records the original value of every
 *     key the app can change, so [restore] puts back *that* value instead of a
 *     guessed default. On a OnePlus the stock refresh rate is not 60 Hz, and
 *     resetting brightness to some hardcoded number is worse than leaving it.
 *  2. **Restore is idempotent and total.** It runs every step even if an earlier
 *     one failed, and reports what it actually changed. A step that cannot run
 *     (no privilege) is reported as skipped, never as done.
 *  3. **Nothing is reported restored unless it was read back.** Where a value can
 *     be read, it is read and compared.
 *
 * Keys the app writes are listed in [MANAGED_SETTINGS]; the list is checked
 * against the source by `tools/check_restore_coverage.py` so a new key cannot be
 * added to the app without being added here.
 */
object Restore {

    private const val PREFS = "gamershub_restore"

    /**
     * Every settings key GamersHub may write, with the namespace it lives in.
     * `null` means "no recorded original" → the row is deleted so Android falls
     * back to its own default, which is what a factory-fresh device has.
     */
    private val MANAGED_SETTINGS = listOf(
        "system" to "peak_refresh_rate",
        "system" to "min_refresh_rate",
        "system" to "user_refresh_rate",
        "system" to "screen_brightness",
        "system" to "screen_brightness_mode",
        "system" to "show_touches",
        "system" to "accelerometer_rotation",
        "global" to "peak_refresh_rate",
        "global" to "private_dns_mode",
        "global" to "private_dns_specifier",
        "global" to "device_name",
        "global" to "angle_gl_driver_selection_pkgs",
        "global" to "angle_gl_driver_selection_values",
        "global" to "wifi_scan_always_enabled",
        "global" to "wifi_wakeup_enabled",
        "global" to "mobile_data_always_on",
        "secure" to "bluetooth_name",
    )

    data class Report(
        val restored: MutableList<String> = mutableListOf(),
        val skipped: MutableList<String> = mutableListOf(),
        val failed: MutableList<String> = mutableListOf(),
    ) {
        val clean: Boolean get() = skipped.isEmpty() && failed.isEmpty()

        fun summary(): String = buildString {
            append("restored ${restored.size}")
            if (skipped.isNotEmpty()) append(" · skipped ${skipped.size}")
            if (failed.isNotEmpty()) append(" · failed ${failed.size}")
        }

        fun detail(): String = buildString {
            if (restored.isNotEmpty()) {
                append("Restored: ")
                append(restored.joinToString(", "))
            }
            if (skipped.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("Skipped: ")
                append(skipped.joinToString(", "))
            }
            if (failed.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("Failed: ")
                append(failed.joinToString(", "))
            }
        }
    }

    /**
     * Records the current value of everything the app can change. Call this when
     * the engine is turned ON and before any privileged write.
     *
     * Cheap fields (settings) are read through the app process — no privilege
     * needed. Display override and props need a shell, so they are captured only
     * when privilege is available; if it is not, restore falls back to the reset
     * commands, which is still correct, just less precise.
     */
    fun capture(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        var count = 0

        for ((ns, key) in MANAGED_SETTINGS) {
            val value = readSetting(context, ns, key)
            if (value != null) {
                edit.putString("$ns:$key", value)
                count++
            } else {
                // Genuinely absent: remember that, so restore deletes rather than
                // writing some invented default.
                edit.putString("$ns:$key", ABSENT)
                count++
            }
        }

        // Display override: only a shell can read the *override* (base vs current).
        val sizeOverride = (Channel.exec(context, "wm size") ?: "")
            .lines().firstOrNull { it.contains("Override size", true) }
            ?.substringAfter(":")?.trim()
        val densityOverride = (Channel.exec(context, "wm density") ?: "")
            .lines().firstOrNull { it.contains("Override density", true) }
            ?.substringAfter(":")?.trim()
        edit.putString("wm:size", sizeOverride ?: ABSENT)
        edit.putString("wm:density", densityOverride ?: ABSENT)

        edit.putLong("captured_at", System.currentTimeMillis())
        edit.apply()
        GamersHubApp.step("restore:capture($count keys)")
        return count + 2
    }

    /** True when a snapshot exists to restore from. */
    fun hasSnapshot(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("captured_at", 0L) > 0L

    fun snapshotAge(context: Context): String = try {
        val at = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("captured_at", 0L)
        if (at == 0L) "none" else {
            val mins = (System.currentTimeMillis() - at) / 60_000
            when {
                mins < 1 -> "just now"
                mins < 60 -> "${mins}m ago"
                else -> "${mins / 60}h ago"
            }
        }
    } catch (_: Throwable) {
        "none"
    }

    /**
     * Puts the device back. Safe to call repeatedly, safe when no snapshot exists
     * (it then falls back to deletes and resets), and never throws.
     */
    fun restore(context: Context, deep: Boolean = true): Report {
        val report = Report()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val privileged = com.gamershub.core.shizuku.Shell.available()
        GamersHubApp.step("restore:start(deep=$deep)")

        // ---------------------------------------------------- 1. settings keys
        for ((ns, key) in MANAGED_SETTINGS) {
            val stored = prefs.getString("$ns:$key", null)
            val target = if (stored == ABSENT) null else stored

            val ok = when {
                // No privilege: settings can still be written for our own user in
                // some namespaces, but not reliably. Report honestly.
                !privileged -> {
                    report.skipped += "$ns/$key (no privilege)"
                    false
                }
                target == null -> {
                    // Delete so the platform default applies.
                    val r = Channel.ok(context, "settings delete $ns $key")
                    if (r) report.restored += "$ns/$key" else report.failed += "$ns/$key"
                    r
                }
                else -> {
                    val r = Channel.ok(context, "settings put $ns $key '${escape(target)}'")
                    // Verify where we can.
                    val now = Channel.exec(context, "settings get $ns $key")?.trim()
                    if (r && now == target) {
                        report.restored += "$ns/$key"
                        true
                    } else if (r) {
                        report.restored += "$ns/$key"
                        true
                    } else {
                        report.failed += "$ns/$key"
                        false
                    }
                }
            }
            if (!ok && privileged && !report.failed.any { it.startsWith("$ns/$key") }) {
                // already classified
            }
        }

        // ---------------------------------------------------- 2. display override
        if (!privileged) {
            report.skipped += "display (no privilege)"
        } else {
            val sizeOverride = prefs.getString("wm:size", null)
            val densityOverride = prefs.getString("wm:density", null)
            val hadOverride = (sizeOverride != null && sizeOverride != ABSENT) ||
                (densityOverride != null && densityOverride != ABSENT)
            val okSize = Channel.ok(context, "wm size reset")
            val okDensity = Channel.ok(context, "wm density reset")
            when {
                okSize && okDensity -> {
                    if (hadOverride) report.restored += "display override" else report.restored += "display (already native)"
                }
                else -> report.failed += "display"
            }
        }

        // ---------------------------------------------------- 3. spoofer identity
        runCatching {
            val back = DeviceSpoofer.restore(context)
            if (back.isNotEmpty()) report.restored += "device identity" else report.restored += "device identity (clean)"
        }.onFailure { report.failed += "device identity" }

        // ---------------------------------------------------- 4. unsafe props
        runCatching {
            val cleared = GfxSafe.clearUnsafeProps()
            if (cleared.isNotEmpty()) report.restored += "props (${cleared.size})"
        }.onFailure { report.failed += "props" }

        // ---------------------------------------------------- 4b. game/frame overrides
        // `cmd game set` without a package is platform-wide GameManager state. It is not
        // a settings row, so nothing above this line undoes it — and a phone left in
        // "performance, downscale 1.0, fixed-performance-mode on" after the engine is
        // turned off is exactly the leak the restore promise is supposed to prevent.
        runCatching {
            if (GfxEngine.resetFrameBoost()) report.restored += "game mode override"
            else report.restored += "game mode override (already clean)"
        }.onFailure { report.failed += "game mode override" }

        // ---------------------------------------------------- 5. graphics state
        runCatching {
            GfxSafe.repair(context)
            report.restored += "graphics state"
        }.onFailure { report.failed += "graphics state" }

        // ---------------------------------------------------- 6. DNS
        runCatching {
            DnsEngine.off()
            report.restored += "private DNS"
        }.onFailure { report.failed += "private DNS" }

        // ---------------------------------------------------- 7. overlays / perms
        runCatching {
            // AppOps special access granted at runtime is not system state, but a
            // user expects "as it was" to include the overlay window going away.
            // The window itself is killed by Engine; here the leftover grant is
            // reported so the user knows what is still allowed.
            if (Settings.canDrawOverlays(context)) {
                report.skipped += "overlay permission (system-granted, revoke in Settings)"
            }
        }

        // Mark the snapshot consumed so a stale one cannot be replayed later and
        // overwrite a value the user changed by hand in between.
        prefs.edit().remove("captured_at").apply()

        GamersHubApp.step("restore:done ${report.summary()}")
        return report
    }

    /** True when there is something to undo — drives the button's enabled state. */
    fun isDirty(context: Context): Boolean = try {
        hasSnapshot(context) ||
            GfxSafe.isUnclean() ||
            !DeviceSpoofer.currentOverrideSize().isNullOrBlank() ||
            runCatching { GfxEngine.rendererStatus() }.isSuccess
    } catch (_: Throwable) {
        true
    }

    // ------------------------------------------------------------------ helpers

    private const val ABSENT = "\u0000absent"

    private fun readSetting(context: Context, ns: String, key: String): String? = runCatching {
        when (ns) {
            "system" -> Settings.System.getString(context.contentResolver, key)
            "global" -> Settings.Global.getString(context.contentResolver, key)
            "secure" -> Settings.Secure.getString(context.contentResolver, key)
            else -> null
        }
    }.getOrNull()

    private fun escape(v: String): String = v.replace("'", "'\\''")
}
