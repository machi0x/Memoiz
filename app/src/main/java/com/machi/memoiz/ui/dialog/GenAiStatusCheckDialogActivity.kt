package com.machi.memoiz.ui.dialog

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.machi.memoiz.R
import com.machi.memoiz.databinding.ActivityGenaiStatusDialogBinding
import com.machi.memoiz.service.GenAiFeatureStates
import com.machi.memoiz.service.GenAiStatusManager
import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Dialog-like Activity that checks GenAI feature status and optionally downloads models. */
class GenAiStatusCheckDialogActivity : ComponentActivity() {

    private lateinit var binding: ActivityGenaiStatusDialogBinding
    private lateinit var manager: GenAiStatusManager
    private var downloadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        manager = GenAiStatusManager(applicationContext)
        binding = ActivityGenaiStatusDialogBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.apply {
            setGravity(android.view.Gravity.CENTER)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        val forceOff = GenAiFeatureStates(
            imageDescription = if (intent.getBooleanExtra(GenAiStatusManager.EXTRA_FORCE_OFF_IMAGE, false)) FeatureStatus.UNAVAILABLE else FeatureStatus.AVAILABLE,
            textGeneration = if (intent.getBooleanExtra(GenAiStatusManager.EXTRA_FORCE_OFF_TEXT, false)) FeatureStatus.UNAVAILABLE else FeatureStatus.AVAILABLE,
            summarization = if (intent.getBooleanExtra(GenAiStatusManager.EXTRA_FORCE_OFF_SUM, false)) FeatureStatus.UNAVAILABLE else FeatureStatus.AVAILABLE
        )

        lifecycleScope.launch {
            val status = manager.checkAll(forceOff)
            render(status)
        }

        binding.buttonClose.setOnClickListener { finish() }
        binding.buttonDownload.setOnClickListener { startDownload() }
    }

    private fun render(status: GenAiFeatureStates) {
        val needsDownload = status.anyDownloadable()
        val anyUnavailable = status.anyUnavailable()

        when {
            needsDownload -> {
                binding.title.text = getString(R.string.genai_status_title_download)
                binding.message.text = getString(R.string.genai_status_message_download)
                binding.illustration.setImageResource(R.drawable.model_download)
                binding.buttonDownload.visibility = View.VISIBLE
                binding.progress.visibility = View.GONE
            }
            anyUnavailable -> {
                binding.title.text = getString(R.string.genai_status_title_unavailable)
                binding.message.text = getString(R.string.genai_status_message_unavailable)
                binding.illustration.setImageResource(R.drawable.model_unavailable)
                binding.buttonDownload.visibility = View.GONE
                binding.progress.visibility = View.GONE
            }
            else -> finish()
        }
    }

    private fun startDownload() {
        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch {
            binding.progress.visibility = View.VISIBLE
            binding.buttonDownload.visibility = View.GONE
            val status = manager.checkAll()
            manager.downloadMissing(status)
            finish()
        }
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        manager.close()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context, forceOff: Triple<Boolean, Boolean, Boolean>) {
            context.startActivity(Intent(context, GenAiStatusCheckDialogActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(GenAiStatusManager.EXTRA_FORCE_OFF_IMAGE, forceOff.first)
                putExtra(GenAiStatusManager.EXTRA_FORCE_OFF_TEXT, forceOff.second)
                putExtra(GenAiStatusManager.EXTRA_FORCE_OFF_SUM, forceOff.third)
            })
        }
    }
}
