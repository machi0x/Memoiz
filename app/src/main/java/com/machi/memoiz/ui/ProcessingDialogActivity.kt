package com.machi.memoiz.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.machi.memoiz.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProcessingDialogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_processing_dialog)

        lifecycleScope.launch {
            delay(DISPLAY_DURATION_MS)
            finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        finish()
        overridePendingTransition(0, 0)
        return super.onTouchEvent(event)
    }

    companion object {
        private const val DISPLAY_DURATION_MS = 2000L

        fun start(context: Context) {
            context.startActivity(Intent(context, ProcessingDialogActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
