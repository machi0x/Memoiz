package com.machi.memoiz.boot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.machi.memoiz.MainActivity
import com.machi.memoiz.R

/**
 * Receiver that notifies the user after device boot to allow starting the ClipboardMonitorService.
 * Starting a foreground service directly from a BroadcastReceiver is restricted on recent Android versions,
 * so we ask the user to open the app and give permission to start the service.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "memoiz_boot_channel"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_START_CLIPBOARD_MONITOR = "com.machi.memoiz.ACTION_START_CLIPBOARD_MONITOR"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Create notification channel
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Memoiz Start Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminder to start Clipboard Monitor"
            }
            notificationManager.createNotificationChannel(channel)

            // Intent to open MainActivity with instruction to start the service
            val startIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_START_CLIPBOARD_MONITOR
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText("Tap to allow Memoiz to start clipboard monitoring after reboot")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
}
