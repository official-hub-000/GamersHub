package com.gamershub.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms Shizuku's user service binding when the framework comes back up.
 *
 * ## Why this exists
 *
 * A soft restart (`stop` → `start`) restarts Android's services. The Shizuku user service
 * is restarted by the Shizuku app itself, so GamersHub just needs to re-bind to it on boot
 * without waiting for the user to open GamersHub again.
 *
 * ## What it deliberately does not do
 *
 *  - **Never turns wireless debugging on.** It has no such power and does not ask for it.
 *  - **Never starts anything the user did not ask for.** The engine is gated on the preferences
 *    written when the user turned it on.
 *  - **Never reboots or changes a setting.** A boot receiver that alters device state is
 *    the sort of thing that should not exist.
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
                if (!prefs.getBoolean("engine_master_on", false)) return@Thread

                com.gamershub.core.shizuku.Shell.ensure(context)

            } catch (_: Throwable) {
                // A boot receiver that throws takes the process down with it.
            } finally {
                runCatching { pending.finish() }
            }
        }.start()
    }
}