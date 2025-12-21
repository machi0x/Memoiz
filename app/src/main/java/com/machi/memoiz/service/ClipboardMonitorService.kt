package com.machi.memoiz.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import com.machi.memoiz.R
import com.machi.memoiz.worker.ClipboardProcessingWorker

/**
 * Service for handling clipboard monitoring.
 * Shows a notification that allows users to tap to save clipboard content.
 * This approach respects Android 10+ clipboard access restrictions.
 */
class ClipboardMonitorService : Service() {
    
    companion object {
        private const val CHANNEL_ID = "clipboard_monitor_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_SAVE_CLIPBOARD = "com.machi.memoiz.SAVE_CLIPBOARD"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SAVE_CLIPBOARD -> {
                processClipboard()
                stopSelf()
            }
            else -> {
                showMonitoringNotification()
            }
        }
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Clipboard Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors clipboard for saving memos"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showMonitoringNotification() {
        val saveIntent = Intent(this, ClipboardMonitorService::class.java).apply {
            action = ACTION_SAVE_CLIPBOARD
        }
        
        val pendingIntent = PendingIntent.getService(
            this,
            0,
            saveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Memoiz Clipboard Monitor")
            .setContentText("Tap to save clipboard content")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun processClipboard() {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboardManager.primaryClip
        
        if (clipData != null && clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            val text = item.text?.toString()
            val uri = item.uri?.toString()
            
            // Queue work for background processing
            val workData = Data.Builder().apply {
                text?.let { putString(ClipboardProcessingWorker.KEY_CLIPBOARD_CONTENT, it) }
                uri?.let { putString(ClipboardProcessingWorker.KEY_IMAGE_URI, it) }
            }.build()
            
            val workRequest = OneTimeWorkRequestBuilder<ClipboardProcessingWorker>()
                .setInputData(workData)
                .build()
            
            WorkManager.getInstance(this).enqueue(workRequest)
        }
    }
}
