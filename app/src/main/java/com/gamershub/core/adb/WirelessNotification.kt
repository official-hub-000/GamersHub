package com.gamershub.core.adb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gamershub.MainActivity

/**
 * Shizuku-style wireless debugging notification prompt.
 *
 * Posts a high-priority system notification when wireless debugging needs pairing
 * or connection, letting the user tap it from anywhere to open the 6-digit code entry
 * sheet immediately.
 */
object WirelessNotification {
    private const val CHANNEL_ID = "gamershub_wireless"
    private const val NOTIF_ID = 7332

    fun showPairingPrompt(context: Context, text: String = "Tap to enter 6-digit pairing code & connect shell") {
        val appContext = context.applicationContext
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wireless Debugging Pairing",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when wireless debugging pairing code input is needed"
            }
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(appContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN, "wireless")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Wireless Debugging (GamersHub)")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            nm.notify(NOTIF_ID, notification)
        }
    }

    fun dismiss(context: Context) {
        val nm = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIF_ID)
    }
}
