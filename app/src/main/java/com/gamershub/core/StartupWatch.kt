package com.gamershub.core

import android.content.Context
import android.content.SharedPreferences
import com.gamershub.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Remembers a crash that could not be offered for sending at the time.
 *
 * ## The problem this solves
 *
 * The crash guard writes `crash.log` and shows a toast, but a crash during `onCreate` —
 * or during the very first composition — kills the process before any screen exists to
 * put a "send this?" button on. The user sees the app disappear, reopens it, and the log
 * is sitting there unmentioned. That is the single most common shape of "your app does
 * not start", and it used to be unreportable.
 *
 * ## What this does not do
 *
 * It does not install a handler, does not catch anything, does not retry, does not
 * suppress the system dialog, and does not claim the app can see every failure. Only
 * what reaches the process-wide uncaught handler is recorded, which is the same set of
 * failures the platform itself logs. A native crash, an OOM kill, or a crash before
 * `onCreate` writes nothing, and the UI says so rather than implying otherwise.
 *
 * ## Store
 *
 * A small prefs file, not a database: three keys, overwritten rather than appended, so
 * it cannot grow. [Pending.atMs] lets the offer expire, so a crash from a month ago is
 * not raised as news.
 */
object StartupWatch {

    private const val PREFS = "gamershub_support"
    private const val KEY_PENDING = "crash_pending"
    private const val KEY_WHEN = "crash_when"
    private const val KEY_WHERE = "crash_where"
    private const val KEY_VERSION = "crash_version"
    private const val KEY_OFFERED_AT = "offered_at"

    /** Crashes older than this are not offered: an old failure is not a current problem. */
    private const val MAX_AGE_MS = 7L * 24 * 3600 * 1000

    /** How many times the same crash may be offered before it stops interrupting. */
    private const val MAX_OFFERS = 2

    private val STAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    data class Pending(
        val whenMs: Long,
        val where: String,
        val versionName: String,
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Marks a crash as unoffered. Cheap and non-throwing by contract — this runs on the
     * way to the process dying, so it must not be the reason the process dies.
     */
    fun markPending(context: Context, error: Throwable) {
        runCatching {
            val p = prefs(context)
            p.edit()
                .putBoolean(KEY_PENDING, true)
                .putLong(KEY_WHEN, System.currentTimeMillis())
                .putString(KEY_WHERE, "${error.javaClass.simpleName}: ${error.message?.take(160) ?: ""}")
                .putString(KEY_VERSION, BuildConfig.VERSION_NAME)
                // A new crash gets a fresh offer count, even if the last one gave up.
                .putInt(KEY_OFFERED_AT, 0)
                .apply()
        }
    }

    /**
     * Called once per launch. Returns a crash that should be offered, or null.
     *
     * Clears the flag when it returns a report, so the offer happens once per crash
     * rather than on every start — up to [MAX_OFFERS] times, for the case where the user
     * dismisses it before deciding.
     */
    fun onLaunch(context: Context): Pending? = runCatching {
        val p = prefs(context)
        if (!p.getBoolean(KEY_PENDING, false)) return null

        val whenMs = p.getLong(KEY_WHEN, 0)
        val version = p.getString(KEY_VERSION, "") ?: ""

        // An update since the crash means the fault may already be gone, and reporting
        // against a build the user no longer runs is not useful.
        if (version.isNotBlank() && version != BuildConfig.VERSION_NAME) {
            p.edit().putBoolean(KEY_PENDING, false).apply()
            return null
        }
        if (System.currentTimeMillis() - whenMs > MAX_AGE_MS) {
            p.edit().putBoolean(KEY_PENDING, false).apply()
            return null
        }

        val offers = p.getInt(KEY_OFFERED_AT, 0)
        if (offers >= MAX_OFFERS) {
            p.edit().putBoolean(KEY_PENDING, false).apply()
            return null
        }
        p.edit().putInt(KEY_OFFERED_AT, offers + 1).apply()

        Pending(
            whenMs = whenMs,
            where = p.getString(KEY_WHERE, "") ?: "",
            versionName = version,
        )
    }.getOrNull()

    /** The user said yes, or no. Either way it is not offered again. */
    fun resolve(context: Context) {
        runCatching { prefs(context).edit().putBoolean(KEY_PENDING, false).apply() }
    }

    /** Human line for the banner. */
    fun describe(pending: Pending): String {
        val stamp = STAMP.format(Date(pending.whenMs))
        val what = pending.where.ifBlank { "a crash" }
        return "GamersHub stopped unexpectedly on $stamp ($what)"
    }
}
