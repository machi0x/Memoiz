package com.machi.memoiz.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.machi.memoiz.service.GenAiStatusManager
import com.machi.memoiz.ui.dialog.GenAiStatusCheckDialogActivity

object GenAiStatusNotification {
    private const val CHANNEL_ID = "genai_status"
    private const val NOTIFICATION_ID = 42

    fun showUnavailable(context: Context) {
        val manager = GenAiStatusManager(context)
        manager.buildNotificationChannel(CHANNEL_ID)
        val intent = Intent(context, GenAiStatusCheckDialogActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = manager.buildUnavailableNotification(CHANNEL_ID)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
