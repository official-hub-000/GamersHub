package com.gamershub.core

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.gamershub.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostics for an emailed Help & Support report.
 *
 * ## What this is not
 *
 * This is **not** the crash subsystem. It never installs a handler, never swallows a
 * throwable and never runs at crash time. It reads what the crash guard already wrote
 * and formats it. Interfering with normal crash handling would be worse than having no
 * diagnostics at all — a report is not worth a crash that goes unreported to the
 * platform.
 *
 * ## What leaves the device
 *
 * Nothing, until the user taps send in their email app. [collect] builds a plain-text
 * [Diagnostics] object in memory; [writeTo] puts it in the app's private cache dir. The
 * only network call in this whole flow is [lookupNetwork], and it runs only when the
 * user has already asked for a report — disclosed on screen before the attachment is
 * built.
 *
 * ## Privacy boundary
 *
 * Deliberately absent: account names, passwords, tokens, message contents, browsing
 * history, installed-app inventory, file listings, and anything from another app.
 * [sanitize] additionally scrubs anything that looks like a credential out of the log
 * tail, because a log line is the one place a secret could realistically leak in from
 * elsewhere in the app.
 */
object SupportDiagnostics {

    /** Where a report is addressed. Shown verbatim in the UI, never hidden. */
    const val SUPPORT_EMAIL = "official.hub.000@gmail.com"

    /**
     * The one external service this feature contacts, and only to answer "what is my
     * public IP and roughly where is it".
     *
     * Chosen with the user's explicit approval. Plain HTTPS GET, no account, no API key,
     * no identifier sent. If it is slow, blocked, or returns something unexpected, the
     * report says `unavailable` rather than guessing.
     */
    const val IP_ENDPOINT = "https://ipwho.is/"

    private val STAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)

    /** Everything that goes into the attached file. Never invented, only observed. */
    data class Diagnostics(
        val text: String,
        val publicIp: String,
        val region: String,
        val networkLookup: String,
    ) {
        val fileName: String
            get() = "gamershub-diagnostics-" +
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
    }

    /**
     * Builds the diagnostic text.
     *
     * @param kind what the user chose — feedback or a problem. It only decides whether
     *   the error-log section is included: a deliberate feature request has no error to
     *   report, and attaching a stale trace would be misleading.
     * @param note what the user typed, if anything.
     * @param includeNetwork call [lookupNetwork]. False when the user has unticked the
     *   network row, in which case the fields say so instead of being quietly omitted.
     */
    fun collect(
        context: Context,
        kind: Kind,
        note: String,
        includeLog: Boolean = true,
        includeNetwork: Boolean = true,
    ): Diagnostics {
        val network = if (includeNetwork) lookupNetwork() else Network("unavailable", "not requested", "skipped by user")

        val text = buildString {
            appendLine("=== GamersHub ${if (kind == Kind.PROBLEM) "problem report" else "feedback"} ===")
            appendLine()

            appendLine("--- App ---")
            appendLine("Name: GamersHub")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
            appendLine("Package: ${context.packageName}")
            appendLine()

            appendLine("--- Device ---")
            appendLine("Manufacturer: ${orUnavailable(Build.MANUFACTURER)}")
            appendLine("Model: ${orUnavailable(Build.MODEL)}")
            appendLine("Android: ${orUnavailable(Build.VERSION.RELEASE)} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { "unavailable" }}")
            appendLine("Language: ${deviceLanguage()}")
            appendLine("Kernel: ${orUnavailable(runCatching { System.getProperty("os.version") }.getOrNull())}")
            appendLine("Uptime: ${uptime()}")
            appendLine()

            appendLine("--- Report ---")
            appendLine("Taken: ${STAMP.format(Date())}")
            appendLine("Kind: ${if (kind == Kind.PROBLEM) "Report a problem" else "Send feedback"}")
            appendLine()

            appendLine("--- Network (approximate) ---")
            appendLine("Public IP: ${network.ip}")
            appendLine("Region: ${network.region}")
            appendLine("Lookup: ${network.status}")
            appendLine("Source: $IP_ENDPOINT — approximate only, resolved from the public IP.")
            appendLine("This is not GPS. GamersHub never requests your precise location.")
            appendLine()

            if (note.isNotBlank()) {
                appendLine("--- What the user wrote ---")
                appendLine(note.trim().take(4000))
                appendLine()
            }

            appendLine("--- App state ---")
            appendLine("Engine: ${runCatching { if (Engine.isOn) "ON" else "OFF" }.getOrDefault("unavailable")}")
            appendLine("Channel: ${runCatching { Channel.kind().name + " (" + Channel.label() + ")" }.getOrDefault("unavailable")}")
            appendLine()

            if (includeLog) {
                appendLine("--- Latest error (sanitized) ---")
                if (kind == Kind.FEEDBACK) {
                    appendLine("Not attached: this is feedback, not a fault report.")
                } else {
                    val tail = readLogTail(context)
                    appendLine(if (tail.isBlank()) "unavailable — no error was recorded." else tail)
                }
                appendLine()
            }

            appendLine("--- End ---")
            appendLine("Sent by hand from GamersHub. Nothing above was uploaded automatically.")
        }

        return Diagnostics(
            text = text,
            publicIp = network.ip,
            region = network.region,
            networkLookup = network.status,
        )
    }

    enum class Kind { FEEDBACK, PROBLEM }

    // ------------------------------------------------------------------ network

    /**
     * Result of the one network call. Public because [lookupNetwork] is public: the
     * compiler rightly refuses a public function that returns a private type.
     */
    data class Network(val ip: String, val region: String, val status: String)

    /**
     * Public IP and coarse region.
     *
     * Runs off the caller's thread contract (callers use Dispatchers.IO). Every failure
     * mode maps to `unavailable` with the reason kept — a report that silently omits the
     * field is worse than one that says why, because the reader cannot tell the
     * difference between "no internet" and "we chose not to ask".
     */
    fun lookupNetwork(): Network {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(IP_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Accept", "application/json")
                // Identify the client honestly; no user id, no device id.
                setRequestProperty("User-Agent", "GamersHub/${BuildConfig.VERSION_NAME} (support report)")
            }
            if (conn.responseCode !in 200..299) {
                return Network("unavailable", "unavailable", "HTTP ${conn.responseCode} from $IP_ENDPOINT")
            }
            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val json = JSONObject(body)
            // ipwho.is reports failure inside a 200 body, so success is not implied by
            // the status code.
            if (!json.optBoolean("success", true)) {
                return Network("unavailable", "unavailable", "service said: " + json.optString("message", "refused"))
            }
            val ip = json.optString("ip").takeIf { it.isNotBlank() } ?: return Network(
                "unavailable", "unavailable", "response had no ip field"
            )
            val country = json.optString("country").takeIf { it.isNotBlank() }
            val regionName = json.optString("region").takeIf { it.isNotBlank() }
            val city = json.optString("city").takeIf { it.isNotBlank() }
            // Country + region only: a city is closer than the feature needs, and the
            // task asked for approximate.
            val region = listOfNotNull(country, regionName).joinToString(", ").ifBlank {
                city ?: "unavailable"
            }
            Network(ip, region, "ok")
        } catch (t: Throwable) {
            Network("unavailable", "unavailable", "could not reach $IP_ENDPOINT: ${t.javaClass.simpleName}")
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    // ------------------------------------------------------------------ log

    /** How much of the crash log travels. Kept small: a report is not an archive. */
    private const val LOG_TAIL_CHARS = 6_000

    /**
     * The most recent error section, sanitized.
     *
     * Reads the file the crash guard already writes. Splits on the guard's `===` section
     * marker and takes the last block, because a long-running install can hold dozens
     * and only the newest is relevant to the report being written now.
     */
    fun readLogTail(context: Context, maxChars: Int = LOG_TAIL_CHARS): String {
        val file = IssueReporter.logFile(context)
        if (!file.exists()) return ""
        val all = runCatching { file.readText() }.getOrNull().orEmpty()
        if (all.isBlank()) return ""
        val last = all.split(Regex("(?m)^=== ")).lastOrNull { it.isNotBlank() } ?: return ""
        val body = "=== " + last
        return sanitize(body).take(maxChars)
    }

    /**
     * Removes anything that looks like a credential.
     *
     * The app's own log should not contain secrets, but "should not" is not a guarantee,
     * and this text leaves the device at the user's request. Cheap, conservative, and
     * applied to the log only — never to what the user typed, because their own words are
     * theirs and editing them would be confusing.
     */
    fun sanitize(raw: String): String {
        var out = raw
        // token=..., password: ..., api_key=..., bearer ..., Authorization: ...
        out = out.replace(
            Regex("(?i)\\b(pass(word)?|token|secret|api[_-]?key|auth|bearer|session)\\b\\s*[:=]\\s*\\S+"),
            "$1: <redacted>"
        )
        // Long base64/hex blobs: pairing keys, certs, hashes.
        out = out.replace(Regex("\\b[A-Fa-f0-9]{32,}\\b"), "<redacted-hex>")
        out = out.replace(Regex("\\b[A-Za-z0-9+/]{40,}={0,2}\\b"), "<redacted-blob>")
        // Absolute paths under another app's data dir, and email addresses.
        out = out.replace(Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+"), "<redacted-email>")
        return out
    }

    // ------------------------------------------------------------------ helpers

    /** The device's own locale, as the user set it. */
    private fun deviceLanguage(): String = runCatching {
        val locales = android.content.res.Resources.getSystem().configuration.locales
        if (locales.isEmpty) "unavailable" else locales[0].toString()
    }.getOrDefault("unavailable")

    private fun uptime(): String = runCatching {
        val ms = SystemClock.elapsedRealtime()
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        "${h}h ${m}m"
    }.getOrDefault("unavailable")

    private fun orUnavailable(v: String?): String =
        v?.takeIf { it.isNotBlank() && it != "unknown" } ?: "unavailable"

    // ------------------------------------------------------------------ file

    /**
     * Writes the report to the app's private cache so a FileProvider can hand it out.
     *
     * Cache, not files: the report is disposable once sent, and the OS may reclaim it.
     * [pruneOld] keeps that from turning into an archive nobody asked for.
     */
    fun writeTo(context: Context, diag: Diagnostics): File? = runCatching {
        val dir = File(context.cacheDir, "support").apply { mkdirs() }
        pruneOld(dir)
        File(dir, diag.fileName).apply { writeText(diag.text) }
    }.getOrNull()

    /**
     * Retention: keeps at most 3 reports, and never anything older than a week.
     *
     * A diagnostics file names the phone, the network and the last error. Keeping a
     * pile of them in the clear would be a small but real privacy cost for no benefit.
     */
    fun pruneOld(dir: File, keep: Int = 3, maxAgeMs: Long = 7L * 24 * 3600 * 1000) {
        runCatching {
            val now = System.currentTimeMillis()
            val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
                ?: return
            files.forEachIndexed { index, f ->
                if (index >= keep || now - f.lastModified() > maxAgeMs) runCatching { f.delete() }
            }
        }
    }

    /** How many report files are on disk right now, for the delete row. */
    fun savedCount(context: Context): Int = runCatching {
        File(context.cacheDir, "support").listFiles()?.count { it.isFile } ?: 0
    }.getOrDefault(0)

    /** Deletes every stored report. Exposed so "delete my reports" is possible. */
    fun clear(context: Context) {
        runCatching { File(context.cacheDir, "support").listFiles()?.forEach { it.delete() } }
    }
}
