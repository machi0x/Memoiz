package com.machika.memoiz.service

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.machika.memoiz.worker.ClipboardProcessingWorker

/**
 * Quick Settings Tile for quick clipboard capture.
 * Allows users to quickly save clipboard content from quick settings.
 * This respects Android 10+ clipboard restrictions by requiring explicit user action.
 */
@RequiresApi(Build.VERSION_CODES.N)
class ClipboardTileService : TileService() {
    
    override fun onClick() {
        super.onClick()
        
        // Process clipboard when tile is tapped
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboardManager.primaryClip
        
        if (clipData != null && clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            val text = item.text?.toString()
            val uri = item.uri?.toString()
            
            if (!text.isNullOrBlank() || !uri.isNullOrBlank()) {
                // Queue work for background processing
                val workData = Data.Builder().apply {
                    text?.let { putString(ClipboardProcessingWorker.KEY_CLIPBOARD_CONTENT, it) }
                    uri?.let { putString(ClipboardProcessingWorker.KEY_IMAGE_URI, it) }
                }.build()
                
                val workRequest = OneTimeWorkRequestBuilder<ClipboardProcessingWorker>()
                    .setInputData(workData)
                    .build()
                
                WorkManager.getInstance(this).enqueue(workRequest)
                
                // Show feedback
                showDialog(
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Clipboard Saved")
                        .setMessage("Your clipboard content is being processed and categorized.")
                        .setPositiveButton("OK", null)
                        .create()
                )
            }
        }
    }
}
