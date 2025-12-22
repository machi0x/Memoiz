package com.machi.memoiz.service

import android.content.Context

object AiWorkerHelper {

    private var aiCategorizationService: AiCategorizationService? = null

    @Synchronized
    fun getService(context: Context): AiCategorizationService {
        if (aiCategorizationService == null) {
            aiCategorizationService = AiCategorizationService(context.applicationContext)
        }
        return aiCategorizationService!!
    }

    @Synchronized
    fun closeService() {
        aiCategorizationService?.close()
        aiCategorizationService = null
    }
}
