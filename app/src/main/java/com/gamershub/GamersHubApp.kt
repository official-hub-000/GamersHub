package com.gamershub

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Process-wide safety net.
 *
 * The app drives a lot of system surfaces (Shizuku, MediaProjection, overlay windows,
 * foreground services). A single OEM-specific SecurityException in any of those used to
 * take the whole process down. Here the trace is written to `files/crash.log` and the
 * action is abandoned instead of killing the app.
 */
class GamersHubApp : Application() {

    companion object {
        /** Breadcrumbs left by the UI, so a crash report says which screen died. */
        private val steps = java.util.ArrayDeque<String>()

        fun step(label: String) {
            synchronized(steps) {
                steps.addLast(label)
                while (steps.size > 18) steps.removeFirst()
            }
        }

        fun stepsText(): String = synchronized(steps) { steps.joinToString(" → ") }

        /** Last trace caught in this process — the Settings card reads the file, the UI reads this. */
        @Volatile
        var lastCrash: String = ""

        @Volatile
        /** Process context, used by helpers that need it without a Context argument. */
        @JvmStatic
        var instance: GamersHubApp? = null
            private set

        @Volatile
        private var lastRecovery: Long = 0L

        /** Records a caught failure without letting it kill the process. */
        fun note(error: Throwable, where: String) {
            val ctx = instance ?: return
            runCatching { ctx.log(Thread.currentThread(), error) }
            runCatching { lastCrash = "$where\n" + error.stackTraceToString() }
        }

        /**
         * A feature that failed *without* throwing.
         *
         * "I tapped it and nothing happened" is the most common report and it used to
         * leave no trace at all — the toast vanished and the log stayed empty, so the
         * report had nothing in it. This writes one short entry with the breadcrumb
         * trail, which is also what makes the reporter open by itself for this case.
         */
        fun problem(where: String, message: String) {
            val ctx = instance ?: return
            runCatching { ctx.logProblem(where, message) }
            lastCrash = "$where: $message"
            toast(message)
        }

        /**
         * Records what a risky action did, without claiming it failed.
         *
         * `problem()` is for "this broke" and opens the reporter; this is for "this ran,
         * here is the outcome". A DFRoot run that exits 0 but does not deliver root is
         * exactly that case: nothing threw, so there is no crash, but without the last
         * line the report cannot say why. The trail is only written when there was
         * something to say, so a clean run does not fill the log.
         */
        fun noteRun(tag: String, detail: String) {
            val ctx = instance ?: return
            runCatching { ctx.logTrail(tag, detail) }
        }

        /** Safe toast from any thread — used so a failed action is never silent. */
        fun toast(message: String) {
            val ctx = instance ?: return
            runCatching {
                Handler(Looper.getMainLooper()).post {
                    runCatching { Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashGuard()
        // A crash during the *previous* launch could not have been offered for sending
        // from inside the dying process. This marks it pending so the next successful
        // start can offer it, which is the only way a startup failure is ever reported.
        runCatching { com.gamershub.core.StartupWatch.onLaunch(this) }
        // Mutations have to be journaled from the moment the app can make one, or the
        // engine's "puts everything back" promise has a hole in it.
        runCatching { com.gamershub.core.root.ModuleManager.attach(this) }
        runCatching { com.gamershub.core.DeviceSpoofer.attach(this) }
        // Restore the persisted master-engine state so a restart cannot silently
        // leave privileged surfaces armed, or lose a switch the user turned on.
        runCatching { com.gamershub.core.Engine.restore(this) }
    }

    private fun installCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { log(thread, error) }
            // Flag it for the next launch. Written before anything that could kill this
            // process again, so a crash that never reaches the toast still gets offered.
            // Nothing here replaces or suppresses the platform's own handling: `previous`
            // is still called on the paths below, exactly as before.
            runCatching { com.gamershub.core.StartupWatch.markPending(this, error) }
            val now = System.currentTimeMillis()
            val main = Looper.getMainLooper().thread
            if (thread === main) {
                // Too many failures in a row: this is not something we can paper over,
                // let the system take it so we do not loop forever on a fresh start.
                if (now - lastRecovery < 6000) {
                    previous?.uncaughtException(thread, error)
                    return@setDefaultUncaughtExceptionHandler
                }
                lastRecovery = now
                // Put a usable screen back immediately, then leave the broken process.
                runCatching {
                    Toast.makeText(this, "A step broke — GamersHub reopened.", Toast.LENGTH_LONG).show()
                }
                runCatching {
                    startActivity(
                        android.content.Intent(this, MainActivity::class.java)
                            .addFlags(
                                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                            )
                    )
                }
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            } else {
                // A background worker (shell read, binder callback, encoder thread) must
                // not take the whole app down either — log it and keep running. If the
                // same failure repeats endlessly, hand it back to the system.
                if (now - lastRecovery < 4000) {
                    previous?.uncaughtException(thread, error)
                } else {
                    lastRecovery = now
                }
            }
        }
    }

    /** Copies the trace to the clipboard as well, so it can be pasted into a report. */
    private fun copyToClipboard(text: String) {
        runCatching {
            val cm = getSystemService(ClipboardManager::class.java) ?: return
            cm.setPrimaryClip(ClipData.newPlainText("GamersHub stop", text))
        }
    }

    /** Outcome of a non-crashing action (a root attempt, a capture start). */
    private fun logTrail(tag: String, detail: String) {
        runCatching {
            val file = File(filesDir, "crash.log")
            if (file.exists() && file.length() > 512 * 1024) file.delete()
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            file.appendText(
                buildString {
                    append("\n=== ").append(stamp).append(" · ").append(tag).append(" ===\n")
                    append("steps: ").append(stepsText()).append('\n')
                    append("note: ").append(detail.take(600)).append('\n')
                }
            )
        }
    }

    /**
     * Non-fatal failure entry. Same file and same shape as a crash, minus the stack
     * trace: the reporter reads both with one parser.
     */
    private fun logProblem(where: String, message: String) {
        val file = File(filesDir, "crash.log")
        if (file.exists() && file.length() > 512 * 1024) file.delete()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val line = buildString {
            append("\n=== ").append(stamp).append(" · ").append(where).append(" ===\n")
            append("steps: ").append(stepsText()).append('\n')
            append("note: ").append(message).append('\n')
        }
        runCatching { file.appendText(line) }
    }

    private fun log(thread: Thread, error: Throwable) {
        val file = File(filesDir, "crash.log")
        if (file.exists() && file.length() > 512 * 1024) file.delete()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val trace = buildString {
            append("\n=== ").append(stamp).append(" · ").append(thread.name).append(" ===\n")
            append("steps: ").append(stepsText()).append('\n')
            append(error.toString()).append('\n')
            error.stackTrace.take(40).forEach { append("    at ").append(it).append('\n') }
        }
        file.appendText(trace)
        copyToClipboard(trace)
        lastCrash = trace
    }
}
