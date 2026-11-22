package com.gamershub.core

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.gamershub.BuildConfig
import com.gamershub.GamersHubApp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Issue reporter — builds one complete, sendable report.
 *
 * ## Why it is built here and not in the sheet
 *
 * The same text has to be reachable from four places (auto-popup, Tools, Profile,
 * Settings) and from the crash guard itself. Assembling it once means the report a user
 * sends after a crash is *byte-identical* to the one they copied by hand, so two reports
 * of the same bug are comparable instead of two different summaries.
 *
 * ## What goes in
 *
 * - the failure itself (last exception, with the breadcrumb `steps:` line),
 * - what the app was allowed to do (engine, channel, root, Shizuku, wireless, permissions),
 * - the phone (model, Android version, ABI, SDK),
 * - the build (versionName + versionCode),
 * - whatever the user typed.
 *
 * Nothing is uploaded from here. This object only *builds* the text and tracks which
 * failure has already been offered. Delivery is [com.gamershub.core.SupportMail]'s job,
 * and it hands a mail draft to the user's own email app — see that class for why the
 * report cannot leave the device without the user pressing send.
 */
object IssueReporter {

    private const val PREFS = "gamershub_reports"
    private const val KEY_LAST_SIG = "last_seen_signature"
    private const val KEY_AUTO_OFF = "auto_popup_off"

    private val STAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** A ready-to-send report. [signature] identifies the failure it came from. */
    data class Report(
        val title: String,
        val text: String,
        val signature: String,
        val hasFailure: Boolean,
        val whenMs: Long = System.currentTimeMillis()
    ) {
        val fileName: String
            get() = "gamershub-report-" +
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(whenMs)) + ".txt"
    }

    /* ------------------------------------------------------------------ log file */

    fun logFile(context: Context): File = File(context.filesDir, "crash.log")

    /** Identity of the current failure state: length + last write. */
    fun signature(context: Context): String {
        val f = logFile(context)
        return if (!f.exists()) "" else "${f.length()}:${f.lastModified()}"
    }

    fun readLog(context: Context, maxChars: Int = 12_000): String = runCatching {
        val f = logFile(context)
        if (!f.exists()) "" else f.readText().trim().takeLast(maxChars)
    }.getOrDefault("")

    fun hasRecentFailure(context: Context, windowMs: Long = 30 * 60 * 1000L): Boolean = runCatching {
        val f = logFile(context)
        f.exists() && System.currentTimeMillis() - f.lastModified() < windowMs
    }.getOrDefault(false)

    /* ------------------------------------------------------------------ auto popup */

    /**
     * True when the reporter should open by itself: a failure the user has not seen
     * yet. Checked on launch and then polled, so a failure mid-session pops the sheet
     * instead of waiting for the next cold start.
     */
    fun shouldAutoOpen(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_AUTO_OFF, false)) return false
        val sig = signature(context)
        if (sig.isEmpty()) return false
        if (!hasRecentFailure(context)) return false
        return prefs.getString(KEY_LAST_SIG, "") != sig
    }

    /** Marks the current failure as seen, so the sheet does not reopen for it. */
    fun markSeen(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_SIG, signature(context))
                .apply()
        }
    }

    fun autoPopupEnabled(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_OFF, false)

    fun setAutoPopup(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_OFF, !on).apply()
    }

    /* ------------------------------------------------------------------ building */

    /** Builds the report. Safe to call from the main thread: every probe is cheap and guarded. */
    fun build(context: Context, userNote: String = ""): Report {
        val log = readLog(context)
        val signature = signature(context)
        val hasFailure = log.isNotBlank()
        val steps = log.lineSequence().firstOrNull { it.startsWith("steps:") }
            ?.removePrefix("steps:")?.trim().orEmpty()
        val headline = log.lineSequence()
            .dropWhile { it.startsWith("===") || it.startsWith("steps:") || it.isBlank() }
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.removePrefix("note: ")
            ?.take(120)
            ?: ""

        val title = when {
            headline.isNotBlank() -> "Issue: " + headline.removePrefix("java.lang.").removePrefix("java.")
            else -> "Issue report — what did not work"
        }

        val text = buildString {
            appendLine("=== GamersHub issue report ===")
            appendLine("When: " + STAMP.format(Date()))
            if (userNote.isNotBlank()) {
                appendLine()
                appendLine("What the user says happened:")
                appendLine(userNote.trim())
            }
            appendLine()
            appendLine("--- Build ---")
            appendLine("App: GamersHub " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
            appendLine("Flavour: " + BuildConfig.BUILD_TYPE)
            appendLine("Package: " + context.packageName)
            appendLine("Installed: " + installedAt(context))
            appendLine()
            appendLine("--- Phone ---")
            appendLine("Model: " + Build.MANUFACTURER + " " + Build.MODEL)
            appendLine("Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")")
            appendLine("ABI: " + Build.SUPPORTED_ABIS.joinToString(", "))
            appendLine("Kernel: " + runCatching { System.getProperty("os.version") }.getOrDefault("?"))
            appendLine("Uptime: " + uptime())
            appendLine()
            appendLine("--- What the app was allowed to do ---")
            appendLine("Engine: " + runCatching { if (Engine.isOn) "ON" else "OFF" }.getOrDefault("?"))
            appendLine("Channel: " + runCatching { Channel.kind().name + " (" + Channel.label() + ")" }.getOrDefault("?"))
            appendLine("Root: " + runCatching { if (RootShellCtl.ready()) "ready · " + RootShellCtl.status() else "not held" }.getOrDefault("?"))
            appendLine("Shizuku: " + runCatching { ShizukuCtl.label() }.getOrDefault("?"))
            appendLine("Wireless adb: " + runCatching { if (WirelessCtl.connected()) "connected" else "not connected" }.getOrDefault("?"))
            appendLine()
            appendLine("--- Permissions ---")
            appendLine("Display over other apps: " + yesNo(overlay(context)))
            appendLine("Modify system settings: " + yesNo(writeSettings(context)))
            appendLine("Usage access: " + yesNo(usage(context)))
            appendLine("Notifications: " + yesNo(notifications(context)))
            appendLine()
            appendLine("--- Steps (last screens) ---")
            appendLine(if (steps.isBlank()) "no breadcrumbs" else steps)
            appendLine()
            appendLine("--- Error log ---")
            appendLine(if (hasFailure) log else "No error was caught. The feature simply did not work.")
            appendLine()
            appendLine("--- End of report ---")
        }

        return Report(
            title = title,
            text = text,
            signature = signature,
            hasFailure = hasFailure
        )
    }

    /** Wipes the log after a report has been sent, so the next popup is a *new* failure. */
    fun clearLog(context: Context) {
        runCatching { logFile(context).delete() }
        markSeen(context)
        GamersHubApp.step("report:sent")
    }

    /* ------------------------------------------------------------------ probes */

    private fun yesNo(v: Boolean): String = if (v) "granted" else "not granted"

    private fun overlay(context: Context): Boolean =
        runCatching { android.provider.Settings.canDrawOverlays(context) }.getOrDefault(false)

    private fun writeSettings(context: Context): Boolean =
        runCatching { android.provider.Settings.System.canWrite(context) }.getOrDefault(false)

    private fun notifications(context: Context): Boolean = runCatching {
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private fun usage(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        mode == android.app.AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    private fun installedAt(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        STAMP.format(Date(info.lastUpdateTime))
    }.getOrDefault("?")

    private fun uptime(): String = runCatching {
        val ms = SystemClock.elapsedRealtime()
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        "${h}h ${m}m since boot"
    }.getOrDefault("?")

    /* ------------------------------------------------------------------ indirection */

    /*
     * The report must build even when root/Shizuku plumbing throws (it is a report
     * *about* things being broken). These small wrappers keep every probe in one
     * runCatching and stop this file from importing the whole root stack.
     */
    private object RootShellCtl {
        fun ready(): Boolean = runCatching { com.gamershub.core.root.RootShell.ready() }.getOrDefault(false)
        fun status(): String = runCatching { com.gamershub.core.root.RootShell.statusLabel() }.getOrDefault("?")
    }

    private object ShizukuCtl {
        fun label(): String = runCatching { com.gamershub.core.shizuku.Shell.statusLabel() }.getOrDefault("?")
    }

    private object WirelessCtl {
        fun connected(): Boolean = runCatching { com.gamershub.core.adb.WirelessAdb.connected() }.getOrDefault(false)
    }
}
