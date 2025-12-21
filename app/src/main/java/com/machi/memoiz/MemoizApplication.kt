package com.machi.memoiz

import android.app.Application

/**
 * Application class for Memoiz.
 * Used for app-wide initialization.
 */
class MemoizApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Future: Initialize WorkManager configurations here if needed
        // Future: Initialize AI service when Gemini Nano becomes available
    }
}
