package com.gamershub.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms the shell daemon when the framework comes back up.
 *
 * ## Why this exists
 *
 * A soft restart (`stop` → `start`) and a userspace reboot both restart Android's
 * services while leaving the kernel — and therefore kernel-side root — running. The
 * shell daemon, however, is a process in the *userspace*, so it is gone. Without this
 * receiver, the user's privileged features stay dead after a soft restart until they
 * open GamersHub again, which looks exactly like the daemon having never worked.
 *
 * ## What it deliberately does not do
 *
 *  - **Never turns wireless debugging on.** It has no such power and does not ask for
 *    it. It re-attaches to an identity the user already authorised, nothing more.
 *  - **Never starts anything the user did not ask for.** Both the daemon and the engine
 *    are gated on the preferences written when the user turned them on.
 *  - **Never reboots or changes a setting.** A boot receiver that alters device state is
 *    the sort of thing that should not exist.
 *
 * On a *full* reboot the daemon cannot come back by itself — starting it needs a shell,
 * and the shell it was started with is gone. That is the same lifetime a Shizuku server
 * has when it was started over ADB, and the release notes say so. Rooted devices can
 * start it again from here.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        // goAsync so a slow connect cannot be killed when onReceive returns.
        val pending = goAsync()
        Thread {
            try {
                val prefs = context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
                if (!prefs.getBoolean("daemon_enabled", false)) return@Thread

                com.gamershub.core.adb.ShellDaemon.attached(context)
                // Already running (a soft restart that kept it): nothing to do.
                if (com.gamershub.core.adb.ShellDaemon.ping()) return@Thread

                // Rooted devices can start it again with no user involvement; everything
                // else genuinely needs the user, because the shell it needs is gone.
                if (com.gamershub.core.root.RootShell.ready()) {
                    com.gamershub.core.adb.ShellDaemon.start(context)
                }
            } catch (_: Throwable) {
                // A boot receiver that throws takes the process down with it.
            } finally {
                runCatching { pending.finish() }
            }
        }.start()
    }
}
